package com.kitoptics.thermalview.usb

import kotlin.time.Duration

sealed class UsbCameraState {
    object Disconnected : UsbCameraState()
    object RequestingPermission : UsbCameraState()
    object Connecting : UsbCameraState()
    data class Streaming(
        val fps: Float,
        val format: CameraFormat = CameraFormat.YUYV,
        val bandwidthMbps: Float = 0f,
        val avgIntervalMs: Float = 0f,
    ) : UsbCameraState()
    data class Recording(val fps: Float, val duration: Duration) : UsbCameraState()
    data class Error(val message: String) : UsbCameraState()
}
