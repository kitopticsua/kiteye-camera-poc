# Android UVC Thermal Camera Viewer — Experts Board Initiative Brief

**Date:** 2026-03-23
**Status:** Analysis Complete
**Scope:** PoC / Feasibility Testing

---

## Initiative Classification

| Parameter | Value |
|---|---|
| Type | Hardware + Software Integration PoC |
| Complexity | HIGH — real-time video pipeline, USB protocol, performance-critical |
| Platform | Android 13+ (Samsung Galaxy XCover7) |
| Risk Level | MEDIUM-HIGH — UVC driver support varies, 50+ FPS is aggressive target |
| Estimated Duration | 3-4 sprints for PoC delivery |

## Founder Requirements

- **Target Device:** Samsung Galaxy XCover7 (Android 13+)
- **Camera:** Custom-built USB 2.0 UVC thermal camera (тепловiзор)
- **Connection:** USB Type-C (direct or via USB HUB)
- **Resolution:** 640x480
- **Video Output Formats:** YUV422 (primary, implement first), Grey8/Y8 (later as option — camera has it as separate mode)
- **Target FPS:** 50-55 fps
- **Video Recording:** Yes — save video to device
- **Latency:** Minimize, no hard constraint
- **Security:** Not a concern (prototype)
- **Orientation:** Landscape (decision by UX)
- **Palettes:** White-hot and Black-hot only
- **Devices:** Samsung Galaxy XCover7 only for now, multi-device later

## Founder Clarification Answers

| # | Question | Answer |
|---|---|---|
| Q1 | Standalone or companion to KiteyeFlow? | Completely separate project |
| Q2 | Video recording needed? | Yes |
| Q3 | Camera resolution? | 640x480 thermal camera |
| Q4 | Simultaneous YUV422 + Grey8? | YUV422 first, Grey8 as option later (camera has it as separate mode) |
| Q5 | Data privacy / security? | Not a concern, prototype |
| Q6 | Landscape-only? | Agent decides (→ landscape) |
| Q7 | Minimum latency? | Minimize but no hard constraints |
| Q8 | Support other Android devices? | One device for now, may expand later |

## Feasibility: POSITIVE

- **USB 2.0 Bandwidth:** 32.2 MB/s needed, 40-45 MB/s available (bulk mode)
- **GPU utilization:** < 1% for 640x480 YUYV→RGB shader
- **Memory:** ~7 MB pipeline total
- **Battery:** ~9.5 hours preview, ~8.5 hours preview + recording
- **Thermal:** No throttling expected (MIL-STD-810H rated device)

## Critical Risk: USB Transfer Mode

Camera **MUST** support bulk transfer mode for 55 FPS with uncompressed YUV422.
Isochronous mode max = ~24 MB/s → realistic max ~38 FPS.
**Action:** Verify with HW team.

## Problem Decomposition

```
Android UVC Camera Viewer PoC
├── 1. USB Connection Layer
│   ├── 1.1 USB Host Mode detection & permission
│   ├── 1.2 UVC device enumeration
│   ├── 1.3 USB HUB support (passthrough)
│   └── 1.4 Hotplug handling (connect/disconnect)
├── 2. Video Capture Pipeline
│   ├── 2.1 UVC stream negotiation (resolution, FPS, format)
│   ├── 2.2 YUV422 frame acquisition
│   ├── 2.3 Grey8 frame acquisition (Phase 2)
│   └── 2.4 Frame buffer management
├── 3. Video Rendering Pipeline
│   ├── 3.1 YUV422 → RGB conversion (GPU shader)
│   ├── 3.2 GLSurfaceView rendering
│   ├── 3.3 Frame rate control & vsync
│   └── 3.4 Grey8 → displayable conversion (Phase 2)
├── 4. Recording Pipeline
│   ├── 4.1 MediaCodec H.264 encoding
│   ├── 4.2 MediaMuxer MP4 container
│   ├── 4.3 Simultaneous preview + recording
│   └── 4.4 File management
├── 5. User Interface
│   ├── 5.1 Camera preview (fullscreen, landscape)
│   ├── 5.2 FPS counter overlay
│   ├── 5.3 Connection status indicator
│   ├── 5.4 Record/Stop controls
│   └── 5.5 White-hot / Black-hot toggle
├── 6. Performance & Stability
│   ├── 6.1 50-55 FPS sustained rendering
│   ├── 6.2 Memory management (no leaks)
│   ├── 6.3 Thermal throttling handling
│   └── 6.4 USB bandwidth monitoring
└── 7. Testing & Validation
    ├── 7.1 Unit tests (conversion, state machine)
    ├── 7.2 Integration tests (USB mock)
    ├── 7.3 Real device benchmark suite
    └── 7.4 Regression test harness
```

## User Stories

### US-1: USB Camera Detection
> Як оператор, я хочу підключити UVC камеру через USB-C і побачити що додаток її розпізнав.

AC:
- [ ] Додаток запитує USB permission при підключенні UVC пристрою
- [ ] Connection status indicator показує "Connected" / "Disconnected"
- [ ] Працює через прямий USB-C та через USB HUB
- [ ] Hotplug: автоматичне визначення підключення/відключення

### US-2: Live Video Preview (YUV422)
> Як оператор, я хочу бачити live video feed з камери у форматі YUV422.

AC:
- [ ] Відео відображається у fullscreen preview
- [ ] Frame rate ≥ 50 FPS (sustained, not burst)
- [ ] YUV422 → RGB конвертація без видимих артефактів
- [ ] Latency < 50ms (camera → screen)

### US-3: Video Recording
> Як оператор, я хочу записувати термальне відео в MP4.

AC:
- [ ] Record/Stop button
- [ ] MP4 файл зберігається в DCIM/Thermal/
- [ ] Preview FPS не падає під час запису
- [ ] Файл playable стандартним плеєром

### US-4: Performance Monitoring Overlay
> Як розробник, я хочу бачити FPS counter та stats на екрані.

AC:
- [ ] FPS counter overlay (оновлюється кожну секунду)
- [ ] Dropped frames counter
- [ ] USB bandwidth indicator
- [ ] Toggleable overlay (show/hide)

### US-5: Error Handling & Recovery
> Як оператор, я хочу зрозумілі повідомлення при помилках.

AC:
- [ ] USB disconnect → graceful stop + "Reconnect" prompt
- [ ] Permission denied → clear instruction screen
- [ ] UVC negotiation failure → error with technical details
- [ ] Auto-reconnect при повторному підключенні камери

## Success KPIs

| KPI | Target |
|---|---|
| Frame Rate | ≥ 50 FPS sustained (5 min test) |
| Latency | < 50ms camera-to-screen |
| Stability | Zero crashes in 30-min session |
| Memory | No leak > 10MB over 30 min |
| Thermal | No throttle-down in 15 min |
| Connection | Works via direct USB-C AND USB HUB |
| Recording | Playable MP4, correct resolution |

## Experts Dispatched

| Expert | Document | Status |
|---|---|---|
| Tech Director Android | `01-architecture-android.md` | Complete |
| DevOps | `02-ci-cd-devops.md` | Complete |
| QA | `03-test-plan-qa.md` | Complete |
| UI/UX | `04-ui-ux-design.md` | Complete |
