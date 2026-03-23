package com.kitoptics.thermalview.usb

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.Surface
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.IPreviewDataCallBack
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.serenegiant.usb.USBMonitor

private const val TAG = "AusbcBridge"

/**
 * Wraps AUSBC 3.2.7 to open the KitEye UVC camera and stream frames.
 *
 * Design: avoids MultiCameraClient.register() which calls registerReceiver() without
 * the RECEIVER_EXPORTED flag required on Android 13+ (API 33+). Instead we call
 * USBMonitor.openDevice() directly after USB permission is already granted.
 *
 * Frame data is delivered in NV21 format by AUSBC.
 */
class AusbcBridge(
    private val context: Context,
    private val onFrame: (ByteArray) -> Unit,
    private val onOpened: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private var usbMonitor: USBMonitor? = null
    private var camera: MultiCameraClient.Camera? = null
    private var dummySurfaceTexture: SurfaceTexture? = null
    private var dummySurface: Surface? = null

    fun open(device: UsbDevice) {
        Log.i(TAG, "Opening UVC VID=0x${device.vendorId.toString(16)} PID=0x${device.productId.toString(16)}")

        // Create USBMonitor with a no-op listener — we do NOT call register() to avoid
        // the Android 13+ SecurityException (registerReceiver without RECEIVER_EXPORTED flag).
        // Permission is already granted by MainActivity before calling open().
        val monitor = USBMonitor(context, object : USBMonitor.OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice?) {}
            override fun onDetach(device: UsbDevice?) {}
            override fun onConnect(
                device: UsbDevice?,
                ctrlBlock: USBMonitor.UsbControlBlock?,
                createNew: Boolean
            ) {}
            override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {}
            override fun onCancel(device: UsbDevice?) {}
        })
        usbMonitor = monitor

        // openDevice() opens the USB connection directly — no BroadcastReceiver needed.
        val ctrlBlock = try {
            monitor.openDevice(device)
        } catch (e: SecurityException) {
            Log.e(TAG, "openDevice SecurityException: ${e.message}")
            onError("USB permission denied: ${e.message}")
            return
        } catch (e: Exception) {
            Log.e(TAG, "openDevice failed: ${e.message}")
            onError("USB open failed: ${e.message}")
            return
        }

        if (ctrlBlock == null) {
            Log.e(TAG, "openDevice returned null ctrlBlock")
            onError("Failed to open USB device")
            return
        }

        Log.i(TAG, "Got UsbControlBlock, opening AUSBC camera")
        openCameraInternal(device, ctrlBlock)
    }

    private fun openCameraInternal(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock) {
        val cam = MultiCameraClient.Camera(context, device)
        cam.setUsbControlBlock(ctrlBlock)
        cam.setCameraStateCallBack(object : ICameraStateCallBack {
            override fun onCameraState(
                self: MultiCameraClient.Camera,
                code: ICameraStateCallBack.State,
                msg: String?
            ) {
                when (code) {
                    ICameraStateCallBack.State.OPENED -> {
                        Log.i(TAG, "Camera OPENED — adding preview callback")
                        self.addPreviewDataCallBack(object : IPreviewDataCallBack {
                            private var firstFrame = true
                            override fun onPreviewData(
                                data: ByteArray?,
                                format: IPreviewDataCallBack.DataFormat
                            ) {
                                if (data == null) return
                                onFrame(data)
                                if (firstFrame) {
                                    firstFrame = false
                                    onOpened()
                                }
                            }
                        })
                    }
                    ICameraStateCallBack.State.CLOSED -> Log.i(TAG, "Camera CLOSED")
                    ICameraStateCallBack.State.ERROR -> {
                        Log.e(TAG, "Camera ERROR: $msg")
                        onError(msg ?: "Camera error")
                    }
                }
            }
        })
        // Build CameraRequest via DefaultConstructorMarker (AUSBC 3.2.7 has private constructor)
        val request = CameraRequest::class.java
            .getDeclaredConstructor(kotlin.jvm.internal.DefaultConstructorMarker::class.java)
            .also { it.isAccessible = true }
            .newInstance(null as kotlin.jvm.internal.DefaultConstructorMarker?)
            .let { it as CameraRequest }
            .also {
                it.previewWidth = CAMERA_WIDTH
                it.previewHeight = CAMERA_HEIGHT
            }
        // AUSBC requires a Surface/SurfaceView — pass a dummy offscreen SurfaceTexture.
        // We don't use AUSBC's rendering path; raw NV21 frames arrive via IPreviewDataCallBack.
        val st = SurfaceTexture(0).also { dummySurfaceTexture = it }
        val surface = Surface(st).also { dummySurface = it }
        cam.openCamera(surface, request)
        camera = cam
    }

    fun close() {
        camera?.closeCamera()
        camera = null
        dummySurface?.release()
        dummySurface = null
        dummySurfaceTexture?.release()
        dummySurfaceTexture = null
        usbMonitor?.destroy()
        usbMonitor = null
        Log.i(TAG, "AusbcBridge closed")
    }
}
