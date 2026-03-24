package com.kitoptics.thermalview.usb

import android.util.Log
import com.kitoptics.thermalview.pipeline.FrameDistributor
import com.kitoptics.thermalview.util.FpsCounter

private const val TAG = "UvcStreamController"
const val CAMERA_WIDTH = 640
const val CAMERA_HEIGHT = 480
const val TARGET_FPS = 60

/**
 * Controls UVC camera streaming lifecycle.
 * Receives frames and pushes them into [FrameDistributor] → TripleBuffer.
 *
 * AUSBC integration is done via [AusbcBridge] (device-only, not unit tested).
 * Unit tests use [simulateFrame] to inject frames directly.
 */
class UvcStreamController(
    private val distributor: FrameDistributor,
    private val fpsCounter: FpsCounter = FpsCounter(windowSize = 30)
) {

    private var frameCallback: ((ByteArray) -> Unit)? = null
    private var streaming = false
    @Volatile private var lastFrameSize: Int = 0

    /**
     * Called by AusbcBridge on USB read thread when a frame arrives.
     * Zero allocations — only writes to triple buffer.
     */
    fun onFrameReceived(data: ByteArray) {
        if (!streaming) return
        lastFrameSize = data.size
        frameCallback?.invoke(data)
        distributor.onFrame(data)
        fpsCounter.onFrame(System.nanoTime())
    }

    fun onStreamStarted() {
        streaming = true
        Log.i(TAG, "Stream started: ${CAMERA_WIDTH}x${CAMERA_HEIGHT}@${TARGET_FPS}fps")
    }

    fun stopStream() {
        streaming = false
        frameCallback = null
        fpsCounter.reset()
        Log.i(TAG, "Stream stopped")
    }

    fun getFps(): Float = fpsCounter.getFps()

    /** USB bandwidth in MB/s — uses YUYV wire size (2 bytes/pixel), not NV21 callback size. */
    fun getBandwidthMbps(): Float {
        val fps = fpsCounter.getFps()
        if (fps <= 0f) return 0f
        val wireBytesPerFrame = CAMERA_WIDTH * CAMERA_HEIGHT * 2  // YUYV on USB wire
        return wireBytesPerFrame * fps / 1_000_000f
    }

    /** Average measured inter-frame interval in milliseconds. */
    fun getAvgIntervalMs(): Float = fpsCounter.getAvgIntervalMs()

    fun isStreaming(): Boolean = streaming

    /** For unit testing only — register frame callback */
    fun setFrameCallback(callback: (ByteArray) -> Unit) {
        frameCallback = callback
    }

    /** For unit testing only — inject a frame directly */
    fun simulateFrame(data: ByteArray) {
        frameCallback?.invoke(data)
        distributor.onFrame(data)
        fpsCounter.onFrame(System.nanoTime())
    }
}
