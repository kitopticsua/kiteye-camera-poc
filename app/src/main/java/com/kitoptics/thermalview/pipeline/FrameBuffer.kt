package com.kitoptics.thermalview.pipeline

/**
 * Metadata wrapper for a frame — index, timestamp, size.
 * Immutable, allocated once per logical frame slot (not per frame).
 */
data class FrameBuffer(
    val index: Long,
    val timestampNs: Long,
    val size: Int
)
