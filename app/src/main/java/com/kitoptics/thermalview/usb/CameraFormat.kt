package com.kitoptics.thermalview.usb

/**
 * UVC preview format used for frame negotiation.
 *
 * [formatId] maps to the libuvc frame-format constant passed to UVCCamera.setPreviewSize():
 *   UVCCamera.FRAME_FORMAT_YUYV = 0  (confirmed from libusbc-3.2.7 bytecode)
 *   GREY8 = 2 — placeholder; update to the actual constant once libUVCCamera.so
 *               is patched for Y800 support (see FR-001-grey8-format.md).
 *
 * [label] is shown in the stats bar overlay.
 */
enum class CameraFormat(val formatId: Int, val label: String) {
    YUYV(formatId = 0, label = "YUV422"),
    GREY8(formatId = 2, label = "Grey8"),  // TODO: verify constant after AUSBC Y800 patch
}
