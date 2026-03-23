# Legacy App Analysis — DrabeniukYurii/Android (KitEye28)

**Date:** 2026-03-23
**Repo:** https://github.com/DrabeniukYurii/Android (private)
**Path:** `my_camera/`

---

## Overview

Production thermal camera app supporting 3 devices:
- Pulsar Termion XP/XL — MJPEG, VID `0x2D46` PID `0x6503`
- **KitEye640SL28** — YUYV uncompressed, VID `0x04b4` PID `0x00c2`
- Defender — MJPEG, VID `0x2207` PID `0x1005`

Features: live preview, video/photo capture, reticle overlay, zoom (optical + software), compass, gyroscope, OpenCV auto-detection, ballistic calculations.

## Tech Stack

| Component | Technology |
|---|---|
| Language | Java |
| UVC Library | `libuvccamera` (herohan/UVCAndroid fork of saki4510t), embedded as local module |
| Rendering | TextureView via CameraHelper |
| CV | OpenCV 4.11 |
| Recording | Built-in CameraHelper VideoCapture |
| Architecture | Single God Activity (~2500 lines) |
| State | SharedPreferences + Global static objects |
| Tests | None |

## Key Findings for PoC

### 1. Camera USB Identifiers (CRITICAL)

```java
// KitEye camera
VID = 0x04b4
PID = 0x00c2
Format = UVC_VS_FORMAT_UNCOMPRESSED (YUYV)
Resolution = 640x480
FPS = 60 (confirmed working)
```

Use in `res/xml/usb_device_filter.xml`:
```xml
<usb-device vendor-id="1204" product-id="194" />
<!-- 0x04b4 = 1204 decimal, 0x00c2 = 194 decimal -->
```

### 2. UVC Control API Used

```java
// KitEye-specific
mCameraHelper.getUVCControl().setAutoExposureMode(0);  // manual exposure
mCameraHelper.getUVCControl().setFocusAuto(false);      // trigger calibration
mCameraHelper.getUVCControl().setZoomAbsolute(value);   // zoom control
mCameraHelper.getUVCControl().setContrast(value);       // for Defender

// Zoom range
int[] limits = mCameraHelper.getUVCControl().updateZoomAbsoluteLimit();
// [min=2000, max=16000] — software zoom via UVC extension
```

### 3. Camera Initialization Pattern

```java
onDeviceOpen(UsbDevice device) {
    // Detect device by VID/PID
    Size customSize = new Size(UVC_VS_FORMAT_UNCOMPRESSED, 640, 480, 60, fpsList);
    mCameraHelper.openCamera(customSize);
}

onCameraOpen(UsbDevice device) {
    mCameraHelper.setPreviewSize(selected);
    mCameraHelper.startPreview();
    mCameraHelper.addSurface(surfaceTexture, false);
}
```

### 4. Reticle Overlay System

Custom reticle rendered as byte array pushed to native layer:
```java
Global_Retticle.setByte_array(byte_array);
mCameraHelper.update_rett();  // native call to update overlay
```

This is a custom modification to libuvccamera — not available in stock libraries.

### 5. Optical Calculations

```java
// Angle per pixel for KitEye28 (focal length 28.5mm, pixel pitch 17μm)
angle_pixel_rett = (2 * atan(640.0 * 17.0 / 1000.0 / 2 / 28.5) * 1000) / 640.0;
```

## What to Reuse

| Item | Action |
|---|---|
| VID/PID `0x04b4:0x00c2` | Copy to usb_device_filter.xml |
| Format = YUYV, 640x480@60fps | Use as camera config |
| UVC Control API patterns | Reference for exposure/zoom |
| Reticle byte array approach | Future reference (not for PoC) |

## What NOT to Reuse

| Item | Why |
|---|---|
| Code architecture | God Activity, no separation of concerns |
| Java source | We use Kotlin |
| libuvccamera module | We use AUSBC (cleaner, maintained) |
| OpenCV dependency | +50MB, only for auto-detection, not needed |
| SharedPreferences state | We use StateFlow/ViewModel |
| Global mutable state | Anti-pattern, use DI |
| Zero tests | We follow TDD |

## Architecture Comparison

```
LEGACY (KitEye28):
  MainActivity (2500 lines)
    → CameraHelper (libuvccamera)
    → TextureView
    → SharedPreferences
    → Global static objects
    → OpenCV for auto-detection

NEW (ThermalView PoC):
  MainActivity
    → CameraViewModel (StateFlow)
    → UvcDeviceManager (AUSBC)
    → TripleBuffer (lock-free)
    → ThermalGLRenderer (OpenGL ES shader)
    → VideoEncoder (MediaCodec)
    → RecordingMuxer (MediaMuxer)
```
