# KitEye Camera PoC — Sprint Tracker

## Current Sprint: S1

### Sprint Goal
Phase 1: USB camera connection + basic live preview on Samsung Galaxy XCover7

### Wave Plan
- **Wave 1** (sequential — each blocks next): CAM-1 → CAM-2 → CAM-3 → CAM-4 → CAM-5

### Stories

| ID | Title | Type | Status | Agent | Priority |
|---|---|---|---|---|---|
| CAM-1 | USB State Machine + Device Manager | android | 🔲 TODO | /android | P0 |
| CAM-2 | Frame Pipeline (Triple Buffer) | android | 🔲 TODO | /android | P0 |
| CAM-3 | AUSBC Stream Integration | android | 🔲 TODO | /android | P0 |
| CAM-4 | OpenGL Preview (YUYV→RGB Shader) | android | 🔲 TODO | /android | P1 |
| CAM-5 | CameraViewModel + Full UI Integration | android | 🔲 TODO | /android | P1 |

### Last Story ID Used: CAM-5

### DoR Check
| ID | AC defined | Test Plan | Blockers |
|---|---|---|---|
| CAM-1 | ✅ | ✅ 6 unit tests | none |
| CAM-2 | ✅ | ✅ 6 unit tests | CAM-1 |
| CAM-3 | ✅ | ✅ 5 UT + 3 DT | CAM-1, CAM-2 |
| CAM-4 | ✅ | ✅ 5 UT + 3 DT | CAM-2 |
| CAM-5 | ✅ | ✅ 5 UT + 6 DT | CAM-1..4 |

### Sprint History
(none yet)
