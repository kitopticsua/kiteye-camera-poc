package com.kitoptics.thermalview.pipeline

import java.nio.ByteBuffer

/**
 * Wires USB frame callback → TripleBuffer → GL renderer.
 *
 * USB thread calls onFrame() — zero allocations, just a buffer copy.
 * GL thread calls getLatestFrame() to get the most recent frame.
 */
class FrameDistributor(private val tripleBuffer: TripleBuffer) {

    private var frameIndex: Long = 0L

    /**
     * Called from USB read thread on every frame arrival.
     * Must return in <1ms — only copies data into triple buffer.
     */
    fun onFrame(data: ByteArray) {
        tripleBuffer.write(data)
        frameIndex++
    }

    /**
     * Called from GL render thread.
     * Returns latest frame ByteBuffer or null if no frame yet.
     */
    fun getLatestFrame(): ByteBuffer? = tripleBuffer.read()

    fun getFrameIndex(): Long = frameIndex
}
