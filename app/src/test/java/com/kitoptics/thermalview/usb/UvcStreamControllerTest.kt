package com.kitoptics.thermalview.usb

import com.kitoptics.thermalview.pipeline.FrameDistributor
import com.kitoptics.thermalview.pipeline.TripleBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UvcStreamControllerTest {

    private lateinit var tripleBuffer: TripleBuffer
    private lateinit var distributor: FrameDistributor
    private lateinit var controller: UvcStreamController

    companion object {
        const val FRAME_SIZE = 640 * 480 * 2
    }

    @Before
    fun setUp() {
        tripleBuffer = TripleBuffer(FRAME_SIZE)
        distributor = FrameDistributor(tripleBuffer)
        controller = UvcStreamController(distributor)
    }

    // UT-04: stopStream() clears frame callback — no callbacks after stop
    @Test
    fun `stopStream clears frame callback`() {
        var callbackCount = 0
        controller.setFrameCallback { callbackCount++ }

        // Simulate 3 frames
        repeat(3) { controller.simulateFrame(ByteArray(FRAME_SIZE)) }
        assertEquals(3, callbackCount)

        controller.stopStream()

        // After stop — no more callbacks
        repeat(3) { controller.simulateFrame(ByteArray(FRAME_SIZE)) }
        assertEquals(3, callbackCount)  // still 3, not 6
    }

    // UT-05: Frame callback executes in <1ms
    @Test
    fun `frame callback executes in under 1ms`() {
        val frame = ByteArray(FRAME_SIZE) { 42 }
        // Warm up
        repeat(5) { controller.simulateFrame(frame) }

        val startNs = System.nanoTime()
        repeat(100) { controller.simulateFrame(frame) }
        val elapsedNs = System.nanoTime() - startNs

        val avgNs = elapsedNs / 100
        assertTrue(
            "Frame callback avg ${avgNs / 1_000}µs should be <1ms",
            avgNs < 1_000_000L  // 1ms in nanoseconds
        )
    }
}
