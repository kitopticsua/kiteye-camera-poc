package com.kitoptics.thermalview.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.IPreviewDataCallBack
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.serenegiant.usb.USBMonitor

private const val TAG = "AusbcBridge"

/**
 * Wraps AUSBC MultiCameraClient to open the KitEye UVC camera and stream frames.
 * Call [open] after USB permission is granted; [close] on disconnect or onDestroy.
 *
 * Frame data is delivered in NV21 format by AUSBC.
 */
class AusbcBridge(
    private val context: Context,
    private val onFrame: (ByteArray) -> Unit,
    private val onOpened: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private var client: MultiCameraClient? = null
    private var camera: MultiCameraClient.Camera? = null

    fun open(device: UsbDevice) {
        Log.i(TAG, "Opening UVC device VID=0x${device.vendorId.toString(16)} PID=0x${device.productId.toString(16)}")
        val mc = MultiCameraClient(context, object : MultiCameraClient.IDeviceConnectCallBack {
            override fun onAttachDev(device: UsbDevice?) {
                Log.d(TAG, "onAttachDev: ${device?.deviceName}")
            }

            override fun onDetachDec(device: UsbDevice?) {
                Log.d(TAG, "onDetachDec: ${device?.deviceName}")
            }

            override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                Log.i(TAG, "onConnectDev — opening camera")
                if (device == null || ctrlBlock == null) return
                openCameraInternal(device, ctrlBlock)
            }

            override fun onDisConnectDec(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                Log.d(TAG, "onDisConnectDec")
            }

            override fun onCancelDev(device: UsbDevice?) {
                Log.d(TAG, "onCancelDev")
            }
        })
        mc.register()
        mc.requestPermission(device)
        client = mc
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
        val request = CameraRequest().apply {
            previewWidth = CAMERA_WIDTH
            previewHeight = CAMERA_HEIGHT
        }
        cam.openCamera(null, request)
        camera = cam
    }

    fun close() {
        camera?.closeCamera()
        camera = null
        client?.unRegister()
        client?.destroy()
        client = null
        Log.i(TAG, "AusbcBridge closed")
    }
}
