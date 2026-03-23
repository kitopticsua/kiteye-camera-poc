# ThermalView PoC — Engineering Practices

## 1. BMAD Development Cycle

Every feature passes through **Build → Measure → Analyze → Decide**:

```
  FOUNDER REQUEST
       │
       ▼
  ┌─────────┐
  │   BA    │ ← decompose + contract
  └────┬────┘
       │
       ▼
  ┌─────────┐     fail
  │  BUILD  │ ◄────────┐
  │ /android│          │
  └────┬────┘          │
       │               │
       ▼               │
  ┌─────────┐          │
  │ MEASURE │          │
  │   /qa   │          │
  └────┬────┘          │
       │               │
       ▼               │
  ┌─────────┐     ITERATE
  │ ANALYZE │ ─────────┘
  │  orch.  │
  └────┬────┘
       │ GO
       ▼
  ┌─────────┐
  │ DECIDE  │ → merge + next story
  └─────────┘
```

**No story ships without passing all 4 phases.**

---

## 2. TDD: Red → Green → Refactor

### Strict order:
1. **RED** — Write a failing test that describes the desired behavior
2. **GREEN** — Write the minimum code to make the test pass
3. **REFACTOR** — Clean up without changing behavior, tests must still pass

### What to test (test pyramid):

```
         /\
        /  \   Device Tests (real HW)
       / 10% \  - FPS, latency, USB hotplug
      /--------\
     /          \  Integration Tests
    /    20%     \ - Pipeline, encoder, state machine
   /--------------\
  /                \  Unit Tests
 /      70%        \ - Conversion, buffer, ViewModel, FpsCounter
/--------------------\
```

### Unit test rules:
- Every public function has at least one test
- Test edge cases: null, empty, boundary values
- Test state machine transitions exhaustively
- Mock USB/camera dependencies, never the business logic
- Run in <10 seconds total

### What NOT to unit test:
- OpenGL shader output (use visual inspection on device)
- USB driver internals (AUSBC responsibility)
- Android framework lifecycle (use instrumented tests sparingly)

---

## 3. Memory-Safe Real-Time Pipeline

### Zero-Allocation Hot Path

The frame callback runs 60 times per second. **Any GC pause = dropped frames.**

```kotlin
// BAD — allocates on every frame
fun onFrame(data: ByteArray) {
    val buffer = ByteBuffer.allocate(data.size)  // ← GC pressure!
    buffer.put(data)
    process(buffer)
}

// GOOD — pre-allocated, reused
private val tripleBuffer = TripleBuffer(FRAME_SIZE)  // allocated once

fun onFrame(data: ByteArray) {
    tripleBuffer.write(data)  // copy into pre-allocated slot
}
```

### Rules:
- Pre-allocate ALL buffers at stream start
- Never use `ByteBuffer.allocate()` in frame callback
- Never create objects in `onDrawFrame()` (GL thread)
- Use primitive types in hot path, not boxed types
- Profile with Android Studio Memory Profiler weekly

---

## 4. USB State Machine

### Always use sealed class, never booleans

```kotlin
// BAD — multiple booleans = impossible states
var isConnected = false
var isStreaming = false
var isRecording = false
// Bug: isRecording=true && isConnected=false is possible

// GOOD — sealed class prevents impossible states
sealed class UsbCameraState {
    object Disconnected : UsbCameraState()
    object Connecting : UsbCameraState()
    data class Streaming(val fps: Float) : UsbCameraState()
    data class Recording(val fps: Float) : UsbCameraState()
    data class Error(val message: String) : UsbCameraState()
}
```

### Lifecycle integration:
- `onResume` → start streaming (if connected)
- `onPause` → stop streaming, finalize recording
- `onDestroy` → release all USB resources
- USB BroadcastReceiver → handle hotplug

---

## 5. Threading Discipline

### Three threads, strict ownership:

| Thread | Owns | Reads | Never |
|---|---|---|---|
| USB Read | TripleBuffer write slot | USB endpoint | Touch GL, UI, files |
| GL Render | GL context, textures | TripleBuffer read slot | USB, file I/O |
| Encoder Drain | MediaMuxer | MediaCodec output | GL, USB, UI |

### Communication:
- USB → GL: via TripleBuffer (lock-free atomic swap)
- GL → Encoder: via shared EGLContext (GPU-side)
- Any → UI: via `StateFlow` on Main dispatcher

### Rules:
- Never hold locks in frame callback
- Never do I/O on GL thread
- Never touch GL from USB thread
- Use `Dispatchers.IO` for file operations
- Use `Dispatchers.Main` for UI updates only

---

## 6. Performance Budgets

### Per-Frame Budget (at 60 FPS = 16.6ms per frame)

| Stage | Budget | Typical |
|---|---|---|
| USB read + buffer copy | < 2ms | ~1ms |
| GL texture upload | < 1ms | ~0.5ms |
| YUYV→RGB shader | < 1ms | ~0.3ms |
| Display swap | < 2ms | ~1ms |
| Encoder surface draw | < 1ms | ~0.5ms |
| **Total** | **< 7ms** | **~3.3ms** |
| **Headroom** | **9.6ms** | — |

### System Budgets

| Metric | Budget | Alarm |
|---|---|---|
| App memory (PSS) | < 100 MB | > 150 MB |
| Pipeline buffers | ~7 MB | > 15 MB |
| Battery drain | < 25%/hr | > 35%/hr |
| CPU usage | < 15% | > 30% |
| GPU usage | < 5% | > 15% |

---

## 7. Error Recovery Patterns

### Graceful Degradation

```
Camera disconnect during preview:
  → Stop render loop (don't crash)
  → Show "Disconnected" UI
  → Listen for USB_DEVICE_ATTACHED
  → Auto-reconnect when plugged back in

Camera disconnect during recording:
  → Call MediaMuxer.stop() (finalize MP4)
  → Save partial file (it will be playable!)
  → Show "Recording saved (partial)"
  → Transition to Disconnected state

Format negotiation failure:
  → Log exact UVC error code
  → Try fallback resolution (320x240)
  → If still fails → show Error with tech details
  → Never crash, never ANR
```

### Never-crash rules:
- Wrap all USB operations in try/catch
- Wrap all GL operations in try/catch
- Wrap all MediaCodec operations in try/catch
- Log exceptions to Sentry, show user-friendly error
- USB permission denied = UI state, not exception

---

## 8. Code Review Checklist

Before merging any PR, verify:

### Correctness
- [ ] All unit tests pass
- [ ] Lint clean
- [ ] BA contract fully satisfied
- [ ] State machine transitions are valid

### Performance
- [ ] No allocations in frame callback
- [ ] No allocations in `onDrawFrame()`
- [ ] No I/O on GL thread
- [ ] No locks in hot path
- [ ] FPS target met (if device-tested)

### Robustness
- [ ] USB disconnect handled gracefully
- [ ] Recording stops cleanly on disconnect
- [ ] Permission denied handled
- [ ] No resource leaks (buffers, GL objects, MediaCodec)

### Style
- [ ] Follows package structure
- [ ] Uses constants, not magic numbers
- [ ] Sealed class for states, not booleans
- [ ] Coroutines for async, not callbacks
- [ ] Meaningful variable names

---

## 9. Git Workflow

### Branches
```
main     ← always releasable, tags = releases
develop  ← integration branch
feature/CAM-N-description  ← short-lived (<2 days)
fix/CAM-N-description      ← bugfix
```

### Commit Convention
```
feat: [CAM-N] implement UVC frame capture
fix:  [CAM-N] handle USB disconnect during recording
test: [CAM-N] add unit tests for YUV422 converter
docs: [CAM-N] document USB device filter
perf: [CAM-N] optimize triple buffer swap
```

### PR Rules
- Every PR must reference a CAM-N story
- Every PR must pass CI (lint + unit tests)
- Every PR must have BMAD status in description
- Squash merge to develop, merge commit to main

---

## 10. Definition of Done (DoD)

A story is DONE when:
- [ ] All AC from story file are met
- [ ] All unit tests pass (`./gradlew testDebugUnitTest`)
- [ ] Lint clean (`./gradlew lintDebug`)
- [ ] BMAD cycle completed (BUILD + MEASURE + ANALYZE + DECIDE=GO)
- [ ] Code follows engineering practices (this doc)
- [ ] PR merged to develop
- [ ] Device test passed on real XCover7 (if applicable)
- [ ] No new Sentry errors after deploy
