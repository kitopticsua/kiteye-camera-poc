# Android UVC Thermal Camera Viewer — QA Test Plan

**Expert:** QA Engineer
**Date:** 2026-03-23
**Device:** Samsung Galaxy XCover7 (Android 13+)
**Camera:** Custom USB 2.0 UVC thermal camera, 640x480

---

## 1. Test Strategy

### Test Pyramid

```
         /\
        /  \  Manual / Device Tests (real HW required)
       /    \
      /------\
     /        \  Integration Tests (USB mock layer)
    /          \
   /------------\
  /              \  Unit Tests (no hardware)
 /                \
/------------------\
```

### Unit Tests (no hardware)

**YUV422 → RGB Conversion:**
| Test | Input | Expected |
|---|---|---|
| Pure red | Y=81, Cb=90, Cr=240 | RGB(255, 0, 0) ±3 |
| Pure green | Y=145, Cb=54, Cr=34 | RGB(0, 255, 0) ±3 |
| Black | Y=16, Cb=128, Cr=128 | RGB(0, 0, 0) ±3 |
| White | Y=235, Cb=128, Cr=128 | RGB(255, 255, 255) ±3 |
| Full frame | Synthetic 640x480 | Matches reference |
| Null buffer | null | IllegalArgumentException |

**USB State Machine:**
| Initial | Event | Expected |
|---|---|---|
| DISCONNECTED | USB_ATTACHED | ATTACHED |
| ATTACHED | requestPermission() | PERMISSION_REQUESTED |
| PERMISSION_REQUESTED | granted | PERMISSION_GRANTED |
| PERMISSION_REQUESTED | denied | DISCONNECTED |
| PERMISSION_GRANTED | startStream() | STREAMING |
| STREAMING | USB_DETACHED | DISCONNECTED |
| STREAMING | error | ERROR |
| ERROR | reconnect() | ATTACHED |

**Recording File Management:**
| Test | Expected |
|---|---|
| Filename generation | `thermal_YYYYMMDD_HHmmss.mp4` |
| Storage full | Recording stops, user notified |
| Cancel mid-recording | Partial file deleted |
| Concurrent start | Error returned |

### Integration Tests (USB mock)

| Test | Pass criteria |
|---|---|
| 100 synthetic frames → pipeline | All delivered, no drops |
| Buffer overflow | Oldest dropped, no crash |
| 5-second mock recording | Valid .mp4 file, size > 0 |
| Start/stop recording cycle | Two valid files |

---

## 2. Test Scenarios

### 2.1 Happy Path (P0)

| ID | Steps | Expected |
|---|---|---|
| HP-01 | Plug camera → grant permission | Live feed within 3 sec |
| HP-02 | Observe feed 60 sec | Continuous, no freeze |
| HP-03 | Read FPS overlay | Shows 50-55 |
| HP-04 | Tap Record | Recording starts, timer counts |
| HP-05 | Tap Stop after 10 sec | File saved notification |
| HP-06 | Open file in player | Plays correctly |
| HP-07 | Unplug camera | "Disconnected", no crash |
| HP-08 | Re-plug camera | Feed resumes within 3 sec |
| HP-10 | Camera via USB HUB | Feed appears, same FPS |

### 2.2 Edge Cases — Hotplug (P0-P1)

| ID | Steps | Expected | Pri |
|---|---|---|---|
| PLUG-01 | Unplug during recording | File saved up to disconnect | P0 |
| PLUG-02 | Unplug → 1 sec → replug | Feed resumes, no ANR | P0 |
| PLUG-03 | Unplug/replug ×10 in 30 sec | App stable, memory stable | P1 |
| PLUG-04 | Unplug USB-C, replug via HUB | Feed resumes | P1 |

### 2.3 Permission (P0-P1)

| ID | Steps | Expected | Pri |
|---|---|---|---|
| PERM-01 | Tap Deny on permission | "Permission required" msg | P0 |
| PERM-02 | Tap "Grant" after deny | Dialog reappears | P0 |
| PERM-03 | Grant, close, replug | Auto-connects | P1 |

### 2.4 Storage (P0)

| ID | Steps | Expected | Pri |
|---|---|---|---|
| STOR-01 | Storage < 10 MB free → Record | Warning shown | P0 |
| STOR-02 | Storage fills during recording | Stops gracefully | P0 |
| STOR-03 | Storage permission denied | Clear error | P0 |

### 2.5 Stress & Endurance (P0-P1)

| ID | Steps | Expected | Pri |
|---|---|---|---|
| STR-01 | Stream 30 min | FPS ≥ 48 throughout | P0 |
| STR-02 | Record 30 min | File saved, FPS ≥ 48 | P0 |
| STR-03 | Thermal throttle → cool | FPS recovers to ≥ 50 | P1 |
| STR-04 | Monitor memory 10 min | Heap growth < 20 MB | P0 |
| STR-05 | Battery drain 60 min | < 25% per hour | P1 |

---

## 3. Performance Benchmarks

### FPS Measurement

```kotlin
// Software frame counter
private var frameCount = 0
private var lastFpsTimestamp = System.nanoTime()

fun onFrameArrived(frame: ByteArray) {
    frameCount++
    val now = System.nanoTime()
    if (now - lastFpsTimestamp >= 1_000_000_000L) {
        val fps = frameCount * 1_000_000_000.0 / (now - lastFpsTimestamp)
        reportFps(fps)
        frameCount = 0
        lastFpsTimestamp = now
    }
}
```

Measure at: UVC callback, converter output, SurfaceTexture render.

### Latency Measurement

LED + camera method:
1. Arduino blinks LED at 10 Hz
2. Secondary camera films phone screen showing thermal feed
3. Latency = display frame timestamp - LED blink timestamp
4. Repeat 30 times → min/avg/max/p95
5. Target: < 100ms end-to-end

### Memory Profiling

Tool: Android Studio Profiler + LeakCanary

| Step | Action | Measurement |
|---|---|---|
| Baseline | App launched, no camera | Record heap |
| T+0 | Camera connected | Record heap |
| T+5min | Streaming | Record heap |
| T+10min | Post-GC | Record heap |
| T+20min | Still streaming | Record heap |

Pass: Native heap growth < 50 MB over 20 min.

### Thermal Monitoring

```bash
#!/bin/bash
while true; do
  TEMPS=$(adb shell "cat /sys/class/thermal/thermal_zone{0,1,2,3}/temp" | tr '\n' ',')
  echo "$(date +%s),$TEMPS"
  sleep 5
done
```

---

## 4. Go/No-Go Criteria

| # | Criterion | GO | NO-GO |
|---|---|---|---|
| 1 | Live feed established | Streams within 5 sec | Cannot stream |
| 2 | FPS sustained | ≥ 50 fps over 5 min | < 45 fps |
| 3 | FPS under load | ≥ 48 fps over 30 min | < 40 for > 30 sec |
| 4 | Recording | Playable MP4, correct resolution | Crashes or unplayable |
| 5 | Recording FPS | ≥ 48 fps during recording | < 45 while recording |
| 6 | Hotplug | Survives 5 cycles | Crashes or requires restart |
| 7 | Permission | Grant/deny without crash | Crash on deny |
| 8 | Storage full | User notified, no crash | Crash |
| 9 | Memory | Heap growth < 50 MB / 20 min | OOM or > 100 MB growth |
| 10 | USB HUB | Works on 1+ powered HUB | All HUBs fail |
| 11 | Latency | < 150ms (p95) | > 300ms consistently |
| 12 | Endurance | No crash in 30 min | Crash or ANR |

### Exit Decision

| P0 failures | P1 failures | Decision |
|---|---|---|
| 0 | 0-2 | **GO** — proceed to Alpha |
| 0 | 3+ | **GO with conditions** |
| 1 | any | **NO-GO** — fix blocker |
| 2+ | any | **NO-GO** — architecture review |

---

## 5. Bug Severity

**BLOCKER** — App crashes on USB connect, black screen, corrupt recordings, ANR on P0 flow.

**CRITICAL** — FPS < 30 in 5 min, crash after 3 hotplugs, OOM after 15 min, color artifacts > 10% frames.

**MAJOR** — USB HUB fails, Grey8 crashes, FPS 45-49 during recording, battery > 40%/hr.

**MINOR** — FPS overlay hard to read, timer format wrong, missing app icon, status bar visible.

---

## 6. Pre-Test Checklist

- [ ] Samsung Galaxy XCover7, Android 13+
- [ ] Developer options + USB debugging ON
- [ ] Screen timeout: Never
- [ ] Storage: minimum 10 GB free
- [ ] Battery: 100% (battery tests), 80%+ (others)
- [ ] ADB connected: `adb devices`
- [ ] Logcat filter: `adb logcat -s KitEyeCamera,FpsMonitor,UvcDriver`
- [ ] Profiler session open (memory tests)

### Regression Order (before any release)

1. HP-01..HP-08 (happy path)
2. PERM-01, PERM-02
3. PLUG-01, PLUG-02
4. STOR-01..STOR-03
5. STR-01, STR-04 (endurance + memory)
6. Performance benchmarks

**Total: ~3.5 hours** (dominated by endurance runs).
