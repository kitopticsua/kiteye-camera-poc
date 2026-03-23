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
 * Wraps AUSBC 3.2.7 MultiCameraClient.Camera to open the KitEye UVC camera.
 *
 * - Uses USBMonitor.openDevice() directly (no register()) → avoids
 *   registerReceiver() SecurityException on Android 13+ (API 33+).
 * - AUSBC delivers NV21 via IPreviewDataCallBack; we pass bytes as-is.
 *   ThermalGLRenderer uploads the NV21 Y-plane (first 307,200 bytes) as
 *   GL_LUMINANCE 640×480 for the thermal palette shader.
 *
 * Grey8: camera only advertises type:4 (YUYV) — no Grey8 in USB descriptors.
 * Bandwidth reduction requires different camera firmware.
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

        val monitor = USBMonitor(context, object : USBMonitor.OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice?) {}
            override fun onDetach(device: UsbDevice?) {}
            override fun onConnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?, createNew: Boolean) {}
            override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {}
            override fun onCancel(device: UsbDevice?) {}
        })
        usbMonitor = monitor

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
                                    Log.i(TAG, "First frame: ${data.size} bytes format=$format")
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
        val request = CameraRequest::class.java
            .getDeclaredConstructor(kotlin.jvm.internal.DefaultConstructorMarker::class.java)
            .also { it.isAccessible = true }
            .newInstance(null as kotlin.jvm.internal.DefaultConstructorMarker?)
            .let { it as CameraRequest }
            .also {
                it.previewWidth = CAMERA_WIDTH
                it.previewHeight = CAMERA_HEIGHT
            }
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
