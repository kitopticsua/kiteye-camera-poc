package com.kitoptics.thermalview.pipeline

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lock-free triple buffer for USB→GL frame pipeline.
 *
 * Only one AtomicInteger shared between threads (readyIdx).
 * writeIdx is owned exclusively by the USB write thread.
 * readIdx is owned exclusively by the GL read thread.
 *
 * No allocations after construction.
 */
class TripleBuffer(val slotSize: Int) {

    val slotCount: Int = 3
    val totalBytes: Long = slotCount.toLong() * slotSize

    private val slots: Array<ByteBuffer> = Array(slotCount) {
        ByteBuffer.allocateDirect(slotSize)
    }

    // Only the write thread touches this
    private var writeIdx = 0

    // Only the read thread touches this
    private var readIdx = 1

    // Shared between threads — latest complete frame slot index
    private val readyIdx = AtomicInteger(2)

    // Set to 1 after first write
    private val hasFrame = AtomicInteger(0)

    /**
     * Write frame into write slot, then atomically swap write↔ready.
     * USB thread only. Zero allocations.
     */
    fun write(data: ByteArray) {
        val slot = slots[writeIdx]
        slot.clear()
        slot.put(data, 0, minOf(data.size, slotSize))
        // Atomic swap: give away write slot, take the ready slot
        writeIdx = readyIdx.getAndSet(writeIdx)
        hasFrame.set(1)
    }

    /**
     * Returns ByteBuffer with latest frame, or null if no frame written yet.
     * GL thread only. Atomically swaps read↔ready to get latest.
     */
    fun read(): ByteBuffer? {
        if (hasFrame.get() == 0) return null
        // Atomic swap: give away read slot, take the ready slot
        readIdx = readyIdx.getAndSet(readIdx)
        val slot = slots[readIdx]
        slot.rewind()
        return slot
    }
}
