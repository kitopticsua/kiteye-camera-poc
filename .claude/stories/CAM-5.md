# CAM-5 — CameraViewModel + Full UI Integration

## Goal
Wire all components into working app: `CameraViewModel` manages state via `StateFlow`, `MainActivity` reacts to state, overlay shows FPS + status, palette FAB toggles White-hot/Black-hot. End-to-end: plug camera → see live thermal preview.

## Type
android (Kotlin, MVVM, UI)

## Priority
P1 — Sprint 1 final deliverable

## Dependencies
- CAM-1 (UsbCameraState, UvcDeviceManager)
- CAM-2 (TripleBuffer, FrameDistributor)
- CAM-3 (UvcStreamController, FpsCounter)
- CAM-4 (ThermalSurfaceView, ThermalGLRenderer)

## Acceptance Criteria
- [ ] `CameraViewModel` exposes `StateFlow<UsbCameraState>`
- [ ] `CameraViewModel` handles USB permission result from `MainActivity`
- [ ] `MainActivity` observes state and updates UI accordingly:
  - `Disconnected` → "Connect camera" message, FABs disabled
  - `RequestingPermission` → loading indicator
  - `Connecting` → "Connecting..." message
  - `Streaming(fps)` → live preview visible, FPS overlay shows value
  - `Error(message)` → error snackbar with message
- [ ] Palette FAB toggles White-hot ↔ Black-hot (persists across reconnects)
- [ ] FPS overlay updates every 500ms (not every frame)
- [ ] Screen stays ON while streaming (`FLAG_KEEP_SCREEN_ON`)
- [ ] App locked to landscape orientation
- [ ] App auto-launches when camera plugged in (via `usb_device_filter.xml`)

## Test Plan
### Unit Tests
| ID | Test | Expected |
|---|---|---|
| UT-01 | Initial ViewModel state is Disconnected | state == Disconnected |
| UT-02 | onPermissionGranted() → state progresses | state != Disconnected |
| UT-03 | onPermissionDenied() → Error state | error message set |
| UT-04 | onCameraDisconnected() → Disconnected | state resets |
| UT-05 | togglePalette() flips mode | White-hot ↔ Black-hot |

### Device Tests (manual, XCover7 + camera)
| ID | Scenario | Expected |
|---|---|---|
| DT-01 | Install APK, plug camera → app auto-launches | app opens automatically |
| DT-02 | Permission dialog → Allow | live preview appears |
| DT-03 | FPS overlay visible | shows 55-60 fps |
| DT-04 | Tap palette FAB | image inverts White↔Black |
| DT-05 | Unplug camera mid-stream | shows "Disconnected" UI, no crash |
| DT-06 | Replug camera | auto-reconnects, preview resumes |

## Files to Modify/Create
- `ui/CameraViewModel.kt`
- `app/MainActivity.kt` (update from stub)
- `ui/test/CameraViewModelTest.kt`

## Definition of Done
- [ ] All 5 unit tests pass
- [ ] Lint clean
- [ ] DT-01..DT-06 all passed on real XCover7
- [ ] End-to-end: plug camera → see 55-60fps live preview ✅
