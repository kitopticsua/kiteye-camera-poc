package com.kitoptics.thermalview.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val KITEYE_VID = 0x04b4
const val KITEYE_PID = 0x00c2
const val ACTION_USB_PERMISSION = "com.kitoptics.thermalview.USB_PERMISSION"

class UvcDeviceManager {

    private val _state = MutableStateFlow<UsbCameraState>(UsbCameraState.Disconnected)
    val state: StateFlow<UsbCameraState> = _state.asStateFlow()

    private var currentDevice: UsbDevice? = null

    // Called when USB_DEVICE_ATTACHED broadcast received
    fun onDeviceAttached(vendorId: Int, productId: Int) {
        if (vendorId != KITEYE_VID || productId != KITEYE_PID) return
        _state.value = UsbCameraState.RequestingPermission
    }

    fun onDeviceAttached(device: UsbDevice) {
        onDeviceAttached(device.vendorId, device.productId)
        if (device.vendorId == KITEYE_VID && device.productId == KITEYE_PID) {
            currentDevice = device
        }
    }

    fun onPermissionGranted() {
        if (_state.value == UsbCameraState.RequestingPermission) {
            _state.value = UsbCameraState.Connecting
        }
    }

    fun onPermissionDenied() {
        _state.value = UsbCameraState.Error("USB permission denied")
        currentDevice = null
    }

    fun onDeviceDetached() {
        _state.value = UsbCameraState.Disconnected
        currentDevice = null
    }

    fun onStreamingStarted(fps: Float) {
        _state.value = UsbCameraState.Streaming(fps)
    }

    fun onError(message: String) {
        _state.value = UsbCameraState.Error(message)
    }

    fun getCurrentDevice(): UsbDevice? = currentDevice

    fun createUsbReceiver(): BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra(
                UsbManager.EXTRA_DEVICE, UsbDevice::class.java
            ) ?: return
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> onDeviceAttached(device)
                UsbManager.ACTION_USB_DEVICE_DETACHED -> onDeviceDetached()
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted) onPermissionGranted() else onPermissionDenied()
                }
            }
        }
    }

    fun createIntentFilter(): IntentFilter = IntentFilter().apply {
        addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        addAction(ACTION_USB_PERMISSION)
    }
}
