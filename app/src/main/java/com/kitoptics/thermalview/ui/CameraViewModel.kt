package com.kitoptics.thermalview.ui

import androidx.lifecycle.ViewModel
import com.kitoptics.thermalview.usb.CameraFormat
import com.kitoptics.thermalview.usb.UsbCameraState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for camera UI state.
 * Bridges USB events (from BroadcastReceiver) → UI state (observed by MainActivity).
 */
class CameraViewModel : ViewModel() {

    private val _cameraState = MutableStateFlow<UsbCameraState>(UsbCameraState.Disconnected)
    val cameraState: StateFlow<UsbCameraState> = _cameraState.asStateFlow()

    var isBlackHot: Boolean = false
        private set

    // Called when USB_DEVICE_ATTACHED with correct VID/PID
    fun onDeviceDetected() {
        _cameraState.value = UsbCameraState.RequestingPermission
    }

    // Called when USB permission dialog → Allow
    fun onPermissionGranted() {
        if (_cameraState.value == UsbCameraState.RequestingPermission) {
            _cameraState.value = UsbCameraState.Connecting
        }
    }

    // Called when USB permission dialog → Deny
    fun onPermissionDenied() {
        _cameraState.value = UsbCameraState.Error("USB permission denied")
    }

    // Called when AUSBC successfully opens camera and first frame arrives
    fun onStreamingStarted(fps: Float = 0f, format: CameraFormat = CameraFormat.YUYV) {
        _cameraState.value = UsbCameraState.Streaming(fps, format)
    }

    // Called periodically to update FPS overlay
    fun onFpsUpdate(fps: Float) {
        val current = _cameraState.value
        if (current is UsbCameraState.Streaming) {
            _cameraState.value = UsbCameraState.Streaming(fps, current.format)
        }
    }

    // Called on USB_DEVICE_DETACHED
    fun onCameraDisconnected() {
        _cameraState.value = UsbCameraState.Disconnected
    }

    // Called on any unrecoverable error
    fun onError(message: String) {
        _cameraState.value = UsbCameraState.Error(message)
    }

    // Toggle White-hot / Black-hot palette
    fun togglePalette() {
        isBlackHot = !isBlackHot
    }
}
