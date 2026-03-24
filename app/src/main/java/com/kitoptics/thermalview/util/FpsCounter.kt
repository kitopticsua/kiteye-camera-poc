package com.kitoptics.thermalview.util

/**
 * Rolling-average FPS counter over the last [windowSize] frames.
 * No allocations after construction — uses circular LongArray.
 */
class FpsCounter(private val windowSize: Int = 30) {

    private val timestamps = LongArray(windowSize)
    private var head = 0
    private var count = 0

    /**
     * Record a frame arrival at [timestampNs] (nanoseconds).
     * Called from USB thread — zero allocations.
     */
    fun onFrame(timestampNs: Long) {
        timestamps[head] = timestampNs
        head = (head + 1) % windowSize
        if (count < windowSize) count++
    }

    /**
     * Returns current FPS estimate, or 0 if fewer than 2 frames recorded.
     */
    fun getFps(): Float {
        if (count < 2) return 0f
        val newest = timestamps[(head - 1 + windowSize) % windowSize]
        val oldest = timestamps[(head - count + windowSize) % windowSize]
        val durationNs = newest - oldest
        if (durationNs <= 0) return 0f
        return (count - 1).toFloat() / (durationNs / 1_000_000_000f)
    }

    /**
     * Returns average measured inter-frame interval in milliseconds,
     * or 0 if fewer than 2 frames recorded.
     */
    fun getAvgIntervalMs(): Float {
        if (count < 2) return 0f
        val newest = timestamps[(head - 1 + windowSize) % windowSize]
        val oldest = timestamps[(head - count + windowSize) % windowSize]
        val durationNs = newest - oldest
        if (durationNs <= 0) return 0f
        return durationNs / 1_000_000f / (count - 1)
    }

    fun reset() {
        head = 0
        count = 0
    }
}
