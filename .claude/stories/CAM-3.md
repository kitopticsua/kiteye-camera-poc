# CAM-3 — AUSBC Stream Integration

## Goal
Integrate AUSBC library to open the KitEye camera (640x480, YUYV, 60fps), receive frame callbacks, and push frames into TripleBuffer. Implement FpsCounter.

## Type
android (Kotlin, AUSBC, USB)

## Priority
P0

## Dependencies
- CAM-1 (UvcDeviceManager — provides UsbDevice)
- CAM-2 (TripleBuffer — receives frames)

## Acceptance Criteria
- [ ] `UvcStreamController` opens camera with correct config: 640×480, YUYV, 60fps
- [ ] Frame callback pushes `ByteArray` into `TripleBuffer.write()` in <1ms
- [ ] Frame callback does NOT allocate any objects (no new ByteArray, no boxing)
- [ ] `startStream()` transitions state to `Streaming`
- [ ] `stopStream()` stops AUSBC, releases UVC camera cleanly
- [ ] `FpsCounter` rolling average over last 30 frames, updates every 500ms
- [ ] Camera open failure → `Error(message)` state with UVC error code logged

## Test Plan
### Unit Tests
| ID | Test | Expected |
|---|---|---|
| UT-01 | FpsCounter: 30 frames in 1s → 30.0 fps | ±1fps accuracy |
| UT-02 | FpsCounter: 60 frames in 1s → 60.0 fps | ±1fps accuracy |
| UT-03 | FpsCounter reset → 0.0 fps | returns 0 |
| UT-04 | stopStream() clears frame callback | no callbacks after stop |
| UT-05 | Frame callback executes in <1ms | timing test |

### Device Tests (manual, XCover7 + camera)
| ID | Scenario | Expected |
|---|---|---|
| DT-01 | Plug camera → startStream() | logcat shows frame callbacks |
| DT-02 | FPS reading after 5s | 55-60 fps in logcat |
| DT-03 | Unplug during stream | graceful stop, no crash |

## Files to Create
- `usb/UvcStreamController.kt`
- `util/FpsCounter.kt`
- `util/test/FpsCounterTest.kt`
- `usb/test/UvcStreamControllerTest.kt`

## Definition of Done
- [ ] All unit tests pass
- [ ] Lint clean
- [ ] DT-01..DT-03 passed on real XCover7
- [ ] Logcat shows 55-60 fps sustained
