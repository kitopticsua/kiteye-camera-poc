# Android UVC Thermal Camera Viewer — Architecture & Feasibility

**Expert:** Tech Director Android + System Architect
**Date:** 2026-03-23
**Project:** ThermalView PoC (standalone)
**Device:** Samsung Galaxy XCover7 (Dimensity 6100+, Mali-G57 MC2, Android 13+, USB-C)
**Camera:** Custom USB 2.0 UVC thermal camera, 640x480, YUV422 primary / Grey8 secondary

---

## 1. C4 Architecture Model

### 1.1 Context Diagram (Level 1)

```
+-------------------+         USB-C / UVC          +---------------------+
|   USB UVC Thermal |  =========================>  |                     |
|   Camera (640x480)|  YUV422 / Grey8 frames       |   ThermalView App   |
+-------------------+                              |   (Android)         |
                                                   |                     |
                        +------------------------> |                     |
                        |  Touch gestures          +----------+----------+
+-------------------+   |                                     |
|   Operator (User) | --+                                     |
+-------------------+                                         |
                                                              v
                                                   +---------------------+
                                                   |  Device File System |
                                                   |  (MP4 recordings)   |
                                                   +---------------------+
```

### 1.2 Container Diagram (Level 2)

```
+============================================================================+
|                         ThermalView Android App                            |
|                                                                            |
|  +------------------+    +-------------------+    +--------------------+   |
|  |                  |    |                   |    |                    |   |
|  |   USB Layer      |--->|  Video Pipeline   |--->|  Rendering Engine  |   |
|  |  (UVC Control)   |    | (Buffer + Convert)|    |  (OpenGL ES)       |   |
|  |                  |    |                   |    |                    |   |
|  +------------------+    +--------+----------+    +--------------------+   |
|                                   |                                        |
|                                   v                                        |
|                          +-------------------+                             |
|                          |  Recording Engine |                             |
|                          |  (MediaCodec+Muxer)|                            |
|                          +-------------------+                             |
|                                   |                                        |
|  +------------------+             v                                        |
|  |   UI Layer       |    +-------------------+                             |
|  | (Views + Overlay)|    |  File System I/O  |                             |
|  +------------------+    +-------------------+                             |
+============================================================================+
```

### 1.3 Component Diagram (Level 3)

#### USB Layer
| Component | Responsibility |
|---|---|
| `UvcDeviceManager` | USB permission request, device enumeration, hotplug detection |
| `UvcStreamController` | Open UVC stream, negotiate format (YUV422/Grey8), set resolution & FPS |
| `UsbFrameReader` | Read raw frames from USB endpoint into pre-allocated ByteBuffer pool |

#### Video Pipeline
| Component | Responsibility |
|---|---|
| `FrameRingBuffer` | Lock-free triple buffer (3 slots) for producer-consumer decoupling |
| `Yuv422Converter` | YUV422 (YUYV) to RGBA via OpenGL ES shader (GPU path) |
| `Grey8Converter` | Grey8 to RGBA via shader (Phase 2) |
| `FrameDistributor` | Fan-out: sends converted frame to both Renderer and Recorder |

#### Rendering Engine
| Component | Responsibility |
|---|---|
| `ThermalGLRenderer` | GLSurfaceView.Renderer — uploads texture, runs fragment shader, displays |
| `ThermalSurfaceView` | Custom GLSurfaceView, handles lifecycle, aspect ratio |

#### Recording Engine
| Component | Responsibility |
|---|---|
| `VideoEncoder` | Wraps MediaCodec (H.264 HW encoder) with Surface input |
| `RecordingMuxer` | Wraps MediaMuxer, writes MP4 container |
| `RecordingController` | Start/stop recording state machine, manages encoder lifecycle |

#### UI Layer
| Component | Responsibility |
|---|---|
| `MainActivity` | Single-activity, hosts preview + controls |
| `CameraViewModel` | MVVM ViewModel — USB state, recording state, FPS counter |
| `OverlayView` | FPS counter, recording indicator, timestamp overlay |

### 1.4 Data Flow

```
USB Endpoint
    |  (raw YUV422 bytes, ~614 KB/frame)
    v
UsbFrameReader  -->  FrameRingBuffer (3 slots, pre-allocated ByteBuffers)
                          |
                    [Consumer Thread]
                          |
                    +-----+------+
                    |            |
                    v            v
           ThermalGLRenderer  VideoEncoder
           (GL texture upload  (Surface input from
            + YUYV->RGB shader) shared EGLContext)
                    |            |
                    v            v
              GLSurfaceView   MediaMuxer -> MP4 file
              (display)
```

### 1.5 Key Interfaces

```kotlin
interface FrameSource {
    fun start(config: StreamConfig)
    fun stop()
    val frameFlow: SharedFlow<FrameBuffer>
}

interface FrameConverter {
    fun convert(input: FrameBuffer, output: Surface)
}

interface RecordingSession {
    fun start(outputFile: File, config: RecordingConfig)
    fun stop(): File
    val state: StateFlow<RecordingState>
}

data class StreamConfig(
    val width: Int = 640,
    val height: Int = 480,
    val fps: Int = 55,
    val format: PixelFormat = PixelFormat.YUV422
)
```

### 1.6 Package Structure

```
com.kitoptics.thermalview/
  +-- app/
  |     MainActivity.kt
  |     ThermalViewApp.kt
  +-- usb/
  |     UvcDeviceManager.kt
  |     UvcStreamController.kt
  |     UsbFrameReader.kt
  |     UsbCameraState.kt
  +-- pipeline/
  |     FrameBuffer.kt
  |     TripleBuffer.kt
  |     FrameDistributor.kt
  +-- rendering/
  |     ThermalGLRenderer.kt
  |     ThermalSurfaceView.kt
  |     ShaderProgram.kt
  |     Yuv422Shader.kt
  |     Grey8Shader.kt              (Phase 2)
  +-- recording/
  |     VideoEncoder.kt
  |     RecordingMuxer.kt
  |     RecordingController.kt
  |     RecordingConfig.kt
  +-- ui/
  |     CameraViewModel.kt
  |     OverlayView.kt
  |     theme/
  +-- util/
        PermissionHelper.kt
        FpsCounter.kt
```

---

## 2. Architecture Decision Records (ADRs)

### ADR-001: UVC Library Selection

**Status:** ACCEPTED

**Context:** Need a library for USB UVC thermal camera on Android without root. Camera outputs YUV422 at 640x480@55fps. Need raw frame access.

**Options:**

| Option | Pros | Cons |
|---|---|---|
| **AUSBC 3.x (AndroidUSBCamera)** | Kotlin rewrite, active, YUV/RGBA callbacks, recording, JitPack | Chinese docs, JNI complexity |
| alexey-pelykh/UVCCamera | Maven Central, modern build | Early version (0.0.x), Flutter-focused |
| saki4510t/UVCCamera (original) | Battle-tested | Unmaintained since ~2018, Java only |
| Android native USB API | No native deps, official | No UVC protocol support |
| libusb + custom | Maximum control | Months of work |

**Decision:** **AUSBC 3.3.x (AndroidUSBCamera)**

**Rationale:**
1. Kotlin-native (v3.0+ rewrite)
2. Frame callback API — raw YUV/RGBA ByteBuffer access
3. Built-in recording as fallback
4. Active maintenance (v3.3.x)
5. Most widely used Android UVC library

**Risk:** JNI layer may need patching for Grey8. Mitigation: open source, Grey8 is Phase 2.

---

### ADR-002: Rendering Pipeline

**Status:** ACCEPTED

**Surface:** GLSurfaceView — dedicated GL thread, double buffering, proven for video.

**YUV Conversion:** OpenGL ES 3.2 fragment shader — zero-copy, GPU-native, sub-0.5ms at 640x480.

```glsl
// YUYV -> RGB fragment shader
precision mediump float;
uniform sampler2D uYuyvTexture;  // YUYV packed as RGBA (width/2 x height)
varying vec2 vTexCoord;

void main() {
    vec2 packedCoord = vec2(vTexCoord.x * 0.5, vTexCoord.y);
    vec4 yuyv = texture2D(uYuyvTexture, packedCoord);

    float y = (mod(gl_FragCoord.x, 2.0) < 1.0) ? yuyv.r : yuyv.b;
    float u = yuyv.g - 0.5;
    float v = yuyv.a - 0.5;

    // BT.601 conversion
    float r = y + 1.402 * v;
    float g = y - 0.344 * u - 0.714 * v;
    float b = y + 1.772 * u;

    gl_FragColor = vec4(r, g, b, 1.0);
}
```

**Grey8 rendering (Phase 2):** Upload as `GL_LUMINANCE`, White-hot = display as-is, Black-hot = invert in shader.

---

### ADR-003: Recording Strategy

**Status:** ACCEPTED

**Decision:** MediaCodec (hardware H.264 encoder) with Surface input + MediaMuxer → MP4

**Architecture:**
```
FrameRingBuffer --> ThermalGLRenderer
                    |
                    +-- GLSurfaceView (display)
                    +-- EGL shared context --> Encoder Surface (MediaCodec)
                                                      |
                                                MediaMuxer --> MP4 file
```

- Container: MP4
- Codec: H.264 (AVC), H.265 optional later
- Bitrate: 8-12 Mbps for 640x480@55fps
- Surface input: zero-copy GPU path

---

### ADR-004: Threading & Buffer Model

**Status:** ACCEPTED

**Threads:**
```
Thread 1: USB Read Thread (highest priority)
    - Reads from AUSBC callback → writes to TripleBuffer

Thread 2: GL Render Thread (managed by GLSurfaceView)
    - Reads latest frame → uploads texture → renders + encodes

Thread 3: Encoder Drain Thread (normal priority)
    - Polls MediaCodec output → writes to MediaMuxer
```

**Buffer:** Triple buffer (3 slots, lock-free, atomic index swap)

```
Slot 0: [Being written by USB thread]    -- "write" slot
Slot 1: [Ready, latest complete frame]   -- "ready" slot
Slot 2: [Being read by GL thread]        -- "read" slot
```

Memory: 3 × 614,400 bytes = **1.8 MB** total.

---

### ADR-005: App Architecture Pattern

**Status:** ACCEPTED

**Decision:** MVVM with Kotlin Coroutines + StateFlow

**USB Hotplug State Machine:**
```
[Disconnected] --USB_ATTACHED--> [Requesting Permission]
[Requesting Permission] --GRANTED--> [Connecting]
[Requesting Permission] --DENIED--> [Permission Denied]
[Connecting] --STREAM_STARTED--> [Streaming]
[Streaming] --USB_DETACHED--> [Disconnected]
[Streaming] --ERROR--> [Error]
[Error] --RETRY/USB_ATTACHED--> [Connecting]
```

```kotlin
sealed class UsbCameraState {
    object Disconnected : UsbCameraState()
    object RequestingPermission : UsbCameraState()
    object Connecting : UsbCameraState()
    data class Streaming(val fps: Float) : UsbCameraState()
    data class Recording(val fps: Float, val duration: Duration) : UsbCameraState()
    data class Error(val message: String) : UsbCameraState()
}
```

**Orientation:** Landscape locked (`sensorLandscape`).

---

## 3. Performance Feasibility

### 3.1 USB 2.0 Bandwidth

```
Frame size:     640 x 480 x 2 bytes (YUV422) = 614,400 bytes = 600 KB
Target FPS:     55
Raw throughput: 614,400 x 55 = 33,792,000 bytes/sec = 32.2 MB/s

USB 2.0 isochronous practical max:     ~24 MB/s ← NOT ENOUGH
USB 2.0 bulk transfer practical max:   ~40-45 MB/s ← OK
```

**Camera MUST support bulk transfer for 55 FPS.**
If isochronous only → max ~38 FPS. Fallback: 30 FPS safe mode.

### 3.2 GPU

Mali-G57 MC2 fill rate ~3.6 Gpixels/s. At 640x480@55fps = 17 Mpixels/s = **0.47% GPU utilization.**

### 3.3 Memory Budget

| Buffer | Size | Count | Total |
|---|---|---|---|
| USB frame triple buffer | 600 KB | 3 | 1.8 MB |
| GL texture | 600 KB | 1 | 0.6 MB |
| GL framebuffer (RGBA) | 1.2 MB | 1 | 1.2 MB |
| Encoder input surface | ~2 MB | 1 | 2 MB |
| Encoder output buffers | 64 KB | 4 | 0.25 MB |
| MediaMuxer write buffer | 1 MB | 1 | 1 MB |
| **Total** | | | **~7 MB** |

### 3.4 Battery

| Mode | Power Draw | Runtime (4050 mAh) |
|---|---|---|
| Preview only | ~1.65 W | ~9.5 hours |
| Preview + recording | ~1.85 W | ~8.5 hours |

### 3.5 Thermal

No throttling expected. Dimensity 6100+ is 6nm, workload is minimal (GPU < 1%, CPU ~5-10%).

---

## 4. Risk Register

| ID | Risk | Prob | Impact | Mitigation |
|---|---|---|---|---|
| R1 | USB bandwidth insufficient (isochronous) | HIGH | HIGH | Verify camera supports bulk transfer |
| R2 | AUSBC incompatible with custom camera | MEDIUM | HIGH | Test Week 1. Fallback: fork AUSBC |
| R3 | YUV422 format not recognized by AUSBC | MEDIUM | MEDIUM | Verify USB descriptor reports standard YUYV GUID |
| R4 | Samsung USB host controller limits throughput | LOW | HIGH | Benchmark actual throughput Week 1 |
| R5 | MediaCodec drops frames at 55 FPS | LOW | MEDIUM | 640x480 is well within HW encoder capacity |
| R6 | Grey8 not supported by AUSBC | MEDIUM | LOW | Phase 2, JNI patch |
| R7 | USB permission denied on reattach | LOW | LOW | Register USB_DEVICE_ATTACHED intent filter with VID/PID |
| R8 | Recording + preview frame drops | MEDIUM | MEDIUM | Encoder uses Surface (GPU path), no CPU contention |

---

## 5. Recommended Tech Stack

| Component | Technology | Version | Why |
|---|---|---|---|
| Language | Kotlin | 2.0+ | First-class Android |
| Min SDK | API 33 | Android 13 | XCover7 ships with 13 |
| Target SDK | API 35 | Android 15 | Latest stable |
| UVC Library | AUSBC | 3.3.3 | Kotlin, frame callbacks, active |
| Rendering | GLSurfaceView + OpenGL ES 3.2 | — | Direct texture, managed GL thread |
| YUV Conversion | GLSL fragment shader | — | Zero-copy GPU |
| Video Encoder | MediaCodec (HW H.264) | — | Hardware-accelerated |
| Container | MP4 via MediaMuxer | — | Universal |
| Architecture | MVVM + StateFlow | — | Simple, lifecycle-aware |
| Async | Kotlin Coroutines | 1.8+ | Structured concurrency |
| Build | Gradle KTS + AGP | 8.5+ | Standard |
| NDK (optional) | libyuv | latest | Fallback CPU path |
| UI | XML Views | — | Better GLSurfaceView integration |

---

## 6. Implementation Phases

### Phase 1: USB + Preview (Week 1-2)
1. Set up project with AUSBC dependency
2. Implement UvcDeviceManager with hotplug
3. Basic YUV422 preview via AUSBC built-in renderer
4. Measure actual USB throughput and FPS
5. **Gate:** Camera connects, frames display. Measure FPS.

### Phase 2: Custom GL Pipeline (Week 2-3)
1. Replace AUSBC renderer with custom GLSurfaceView
2. Implement YUYV→RGB shader
3. Implement TripleBuffer
4. Optimize: measure frame-to-display latency
5. **Gate:** 50+ FPS with custom shader

### Phase 3: Recording (Week 3-4)
1. MediaCodec + MediaMuxer pipeline
2. Shared EGLContext for simultaneous preview + recording
3. Test recording at 55 FPS, verify MP4
4. Start/stop UI controls
5. **Gate:** MP4 plays correctly, preview maintains FPS during recording

### Phase 4: Polish + Grey8 (Week 4+)
1. USB state machine robustness
2. FPS overlay, recording duration
3. Grey8/Y8 mode (if camera firmware ready)
4. White-hot / Black-hot toggle
5. Performance profiling pass
