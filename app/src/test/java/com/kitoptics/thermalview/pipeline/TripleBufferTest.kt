package com.kitoptics.thermalview.pipeline

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class TripleBufferTest {

    private lateinit var buffer: TripleBuffer

    companion object {
        const val FRAME_SIZE = 640 * 480 * 2  // 614400 bytes YUYV
    }

    @Before
    fun setUp() {
        buffer = TripleBuffer(FRAME_SIZE)
    }

    // UT-01: Buffer allocated 3 × 614400 bytes
    @Test
    fun `buffer pre-allocates 3 slots of correct size`() {
        assertEquals(3, buffer.slotCount)
        assertEquals(FRAME_SIZE, buffer.slotSize)
        assertEquals(3L * FRAME_SIZE, buffer.totalBytes)
    }

    // UT-02: write() then read() returns same data
    @Test
    fun `write then read returns same data`() {
        val frame = ByteArray(FRAME_SIZE) { i -> (i % 256).toByte() }
        buffer.write(frame)
        val result = buffer.read()
        assertNotNull(result)
        val resultBytes = ByteArray(FRAME_SIZE)
        result!!.get(resultBytes)
        assertArrayEquals(frame, resultBytes)
    }

    // UT-03: Two writes, read returns latest
    @Test
    fun `two writes read returns latest`() {
        val frame1 = ByteArray(FRAME_SIZE) { 1 }
        val frame2 = ByteArray(FRAME_SIZE) { 2 }
        buffer.write(frame1)
        buffer.write(frame2)
        val result = buffer.read()
        assertNotNull(result)
        val resultBytes = ByteArray(FRAME_SIZE)
        result!!.get(resultBytes)
        assertEquals(2.toByte(), resultBytes[0])
    }

    // UT-04: read() before any write returns null
    @Test
    fun `read before write returns null`() {
        assertNull(buffer.read())
    }

    // UT-05: write() does not allocate new objects (100 calls)
    @Test
    fun `write does not allocate new objects in hot path`() {
        val frame = ByteArray(FRAME_SIZE) { 42 }
        // Warm up — first call may trigger JIT
        repeat(10) { buffer.write(frame) }

        val runtime = Runtime.getRuntime()
        runtime.gc()
        val memBefore = runtime.totalMemory() - runtime.freeMemory()

        repeat(100) { buffer.write(frame) }

        runtime.gc()
        val memAfter = runtime.totalMemory() - runtime.freeMemory()
        // Allow small variance (< 1KB) — no large allocations expected
        val delta = memAfter - memBefore
        assert(delta < 1024) { "Unexpected allocation in hot path: ${delta} bytes" }
    }

    // UT-06: Concurrent write + read — no data corruption
    @Test
    fun `concurrent write and read no data corruption`() {
        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)
        val errors = mutableListOf<String>()

        // Writer: 500 frames
        executor.submit {
            try {
                repeat(500) { i ->
                    val frame = ByteArray(FRAME_SIZE) { (i % 256).toByte() }
                    buffer.write(frame)
                }
            } finally {
                latch.countDown()
            }
        }

        // Reader: reads as fast as possible
        executor.submit {
            try {
                repeat(500) {
                    val result = buffer.read()
                    if (result != null) {
                        val bytes = ByteArray(FRAME_SIZE)
                        result.get(bytes)
                        // All bytes in a frame should be identical (we write uniform frames)
                        val first = bytes[0]
                        for (i in 1 until minOf(100, FRAME_SIZE)) {
                            if (bytes[i] != first) {
                                errors.add("Data corruption at index $i: expected $first got ${bytes[i]}")
                                return@repeat
                            }
                        }
                    }
                }
            } finally {
                latch.countDown()
            }
        }

        latch.await()
        executor.shutdown()
        assert(errors.isEmpty()) { "Corruption: ${errors.first()}" }
    }
}
