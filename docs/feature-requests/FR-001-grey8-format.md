# FR-001: Grey8 (Y800) UVC Format Support

**Date**: 2026-03-24
**From**: Android Team (KitEye Camera PoC)
**To**: Firmware Team (Cypress SX3)
**Priority**: High
**Status**: Open

---

## Summary

Add Grey8 (Y800) as a second UVC uncompressed video format alongside the existing YUYV.
This halves USB bandwidth from ~35 MB/s to ~17.5 MB/s on the USB 2.0 link.

## Motivation

| Metric | YUYV (current) | Grey8 (proposed) |
|---|---|---|
| Bytes per pixel | 2 | 1 |
| Frame size (640×480) | 614,400 B | 307,200 B |
| Bandwidth @ 60 fps | ~35.2 MB/s | ~17.6 MB/s |
| USB 2.0 utilisation | **88%** | **44%** |
| Headroom | 12% | 56% |

The camera is a thermal sensor — U/V chroma bytes in YUYV are not real data.
The Android app already discards them and renders only the Y (luminance) plane.
Transmitting fake chroma wastes half the USB 2.0 bandwidth.

With Grey8 the camera also fits within the USB 2.0 High-Speed **isochronous** limit
(~24 MB/s), enabling guaranteed bandwidth delivery instead of best-effort bulk.

## Evidence

Android logcat from connected device (SM-G556B, Android 16, API 36):

```
AusbcBridge: Supported sizes: {"formats":[{"index":1,"type":4,"default":1,"size":["640x480"]}]}
```

Only one format advertised (type 4 = YUYV). No Grey8 option available for negotiation.

Camera confirmed streaming 640×480 @ ~60 fps YUYV via libUVCCamera.

## Requested Change

### 1. USB Descriptor

Add a second `VS_FORMAT_UNCOMPRESSED` descriptor with a corresponding
`VS_FRAME_UNCOMPRESSED` entry.

**Existing (keep as-is):**

```
VS_FORMAT_UNCOMPRESSED (Format 1)
  bFormatIndex    = 1
  bNumFrameDescriptors = 1
  guidFormat      = YUY2  {59 55 59 32 00 00 10 00 80 00 00 AA 00 38 9B 71}
  bBitsPerPixel   = 16
  bDefaultFrameIndex = 1

VS_FRAME_UNCOMPRESSED (Frame 1 of Format 1)
  bFrameIndex     = 1
  wWidth          = 640
  wHeight         = 480
  dwDefaultFrameInterval = 166666  (60 fps)
```

**New (add):**

```
VS_FORMAT_UNCOMPRESSED (Format 2)
  bFormatIndex    = 2
  bNumFrameDescriptors = 1
  guidFormat      = Y800  {59 38 30 30 00 00 10 00 80 00 00 AA 00 38 9B 71}
  bBitsPerPixel   = 8
  bDefaultFrameIndex = 1

VS_FRAME_UNCOMPRESSED (Frame 1 of Format 2)
  bFrameIndex     = 1
  wWidth          = 640
  wHeight         = 480
  dwDefaultFrameInterval = 166666  (60 fps)
  dwMaxVideoFrameBufferSize = 307200   (640 × 480 × 1)
  dwMaxPayloadTransferSize  = TBD      (see bandwidth notes)
```

### 2. Frame Output

When the host commits `bFormatIndex = 2` via `VS_COMMIT_CONTROL`:

- Camera outputs **1 byte per pixel** (Y only)
- Frame size: 640 × 480 = 307,200 bytes
- No chroma bytes transmitted
- Pixel order: row-major, top-to-bottom, left-to-right

### 3. Default Behaviour

Camera **starts in YUYV** (Format 1) for backward compatibility.
Grey8 is only activated when the host explicitly negotiates Format 2
via the standard `VS_PROBE_CONTROL` / `VS_COMMIT_CONTROL` sequence.

## GUID Reference

Y800 is a standard DirectShow / UVC GUID for 8-bit grayscale:

```
GUID: {59383030-0000-0010-8000-00AA00389B71}
FourCC: Y800
```

Widely recognised by libuvc, V4L2, DirectShow, and Media Foundation.

## Verification

After firmware update, connect camera to any Linux/Windows host:

```bash
# Linux
v4l2-ctl --list-formats-ext
# Expected:
#   [0]: 'YUYV' (YUYV 4:2:2)   640x480  60fps
#   [1]: 'Y800' (8-bit Greyscale) 640x480  60fps

# Windows
ffmpeg -list_options true -f dshow -i video="KitEye Thermal"
```

On Android, after connecting:

```
AusbcBridge: Supported sizes: {"formats":[
  {"index":1,"type":4,"default":1,"size":["640x480"]},
  {"index":2,"type":X,"default":0,"size":["640x480"]}
]}
```

## Dependencies

### Android Side (our responsibility)

The current `libUVCCamera.so` (AUSBC 3.2.7) does **not** include Y800 in its
format lookup table. After firmware delivers Grey8 support, the Android team will:

1. Patch `libUVCCamera.so` to add Y800 GUID to the libuvc format table
2. Negotiate Grey8 via `UVCCamera.setPreviewSize(640, 480, FRAME_FORMAT_GREY8)`
3. Receive raw 307,200-byte frames via `IFrameCallback` + `PIXEL_FORMAT_RAW`

GL renderer and shader require **no changes** — already configured for single-channel
luminance rendering (Y-plane only).

## Bandwidth Notes (Cypress SX3 / USB 2.0)

USB 2.0 High-Speed isochronous limit:
- Max packet size: 1024 bytes × 3 transactions/microframe = 3072 bytes
- 8000 microframes/second → **~24.6 MB/s** theoretical max

| Format | Bandwidth | Fits isochronous? |
|---|---|---|
| YUYV | ~35.2 MB/s | ❌ Exceeds limit → must use bulk |
| Grey8 | ~17.6 MB/s | ✅ Fits with 28% headroom |

Switching to Grey8 opens the option to use **isochronous transfers** for guaranteed
latency, rather than bulk transfers which compete with other USB traffic.

## Timeline

| Step | Owner | Estimate |
|---|---|---|
| Add Y800 descriptor + frame output logic | Firmware | TBD |
| Test on Linux (`v4l2-ctl`) | Firmware | — |
| Deliver updated firmware to Android team | Firmware | — |
| Patch libUVCCamera.so for Y800 | Android | 1-2 days |
| Integration test on XCover7 | Both | — |

## Contact

Questions about this request → Android team lead / this document author.
