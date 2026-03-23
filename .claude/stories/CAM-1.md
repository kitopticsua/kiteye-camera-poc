# CAM-1 — USB State Machine + Device Manager

## Goal
Implement `UsbCameraState` sealed class and `UvcDeviceManager` that detects the KitEye camera (VID=0x04b4, PID=0x00c2), requests USB permission, and handles connect/disconnect events.

## Type
android (Kotlin, USB)

## Priority
P0 — blocks all other stories

## Acceptance Criteria
- [ ] `UsbCameraState` sealed class: `Disconnected`, `RequestingPermission`, `Connecting`, `Streaming(fps)`, `Recording(fps, duration)`, `Error(message)`
- [ ] `UvcDeviceManager` detects USB device attachment with correct VID/PID
- [ ] USB permission request flow: shows system dialog, handles Allow/Deny
- [ ] `BroadcastReceiver` handles `USB_DEVICE_ATTACHED` and `USB_DEVICE_DETACHED`
- [ ] State transitions: Disconnected → RequestingPermission → Connecting → Streaming
- [ ] State transitions: any state → Disconnected on cable unplug
- [ ] Permission denied → `Error("USB permission denied")` state

## Test Plan
### Unit Tests
| ID | Test | Expected |
|---|---|---|
| UT-01 | Initial state is Disconnected | state == Disconnected |
| UT-02 | Device attached → RequestingPermission | state changes |
| UT-03 | Permission granted → Connecting | state changes |
| UT-04 | Permission denied → Error | error message set |
| UT-05 | Device detached from any state → Disconnected | state resets |
| UT-06 | Invalid VID/PID not detected | state stays Disconnected |

## Files to Create
- `usb/UsbCameraState.kt`
- `usb/UvcDeviceManager.kt`
- `usb/test/UvcDeviceManagerTest.kt`

## Definition of Done
- [ ] All 6 unit tests pass
- [ ] Lint clean
- [ ] No allocations in BroadcastReceiver callback
- [ ] State machine exhaustively tested
