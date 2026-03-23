# KitEye Camera PoC — Claude Code Project Context

## Mission

Build **KitEye Camera PoC** — a standalone Android application for real-time display and recording of thermal video from a custom USB UVC camera (640x480) on Samsung Galaxy XCover7.

**This is a SEPARATE project from KiteyeFlow.**

## Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0+ |
| Min SDK | API 33 (Android 13) |
| Target SDK | API 35 (Android 15) |
| UVC Library | AUSBC 3.3.x (AndroidUSBCamera) |
| Rendering | GLSurfaceView + OpenGL ES 3.2 |
| YUV Conversion | GLSL fragment shader (GPU) |
| Recording | MediaCodec (HW H.264) + MediaMuxer → MP4 |
| Architecture | MVVM + Kotlin Coroutines + StateFlow |
| UI | XML Views + Material 3 Dark Theme |
| Build | Gradle KTS + AGP 8.5+ |
| CI/CD | GitHub Actions + Self-hosted runner (real device) |
| Crash Reporting | Sentry Android SDK |

## Repository

```
https://github.com/kitopticsua/kiteye-camera-poc
```

## Hardware

| Component | Spec |
|---|---|
| Target Device | Samsung Galaxy XCover7 (Dimensity 6100+, Mali-G57 MC2, Android 13+, USB-C) |
| Camera | Custom USB 2.0 UVC thermal camera (tепловiзор) |
| Resolution | 640x480 |
| Formats | YUV422 (primary), Grey8 / Y8 (Phase 2) |
| Target FPS | 50-55 fps |
| Connection | USB-C direct or via powered USB HUB |

## Key Technical Decisions

| Decision | Choice | ADR |
|---|---|---|
| UVC library | AUSBC 3.3.x | ADR-001 |
| Surface type | GLSurfaceView | ADR-002 |
| YUV→RGB | OpenGL ES shader | ADR-002 |
| Buffer strategy | Triple buffer (lock-free) | ADR-004 |
| Recording | MediaCodec + Surface + MediaMuxer | ADR-003 |
| App architecture | MVVM + Coroutines | ADR-005 |
| USB transfer | Bulk (must verify with HW team) | ADR-001 |
| Palettes | White-hot, Black-hot only | Founder decision |

## Development Rules

### Commits (Conventional)
```
feat: [CAM-N] short description
fix:  [CAM-N] short description
test: [CAM-N] add tests for X
docs: [CAM-N] update Y
```

### TDD Workflow (Red → Green → Refactor)
1. Write failing test first
2. Write minimal code to pass
3. Refactor
4. Run: `./gradlew testDebugUnitTest`

### Definition of Done (DoD)
- All tests green
- Coverage gate passed
- PR review approved
- APK builds successfully
- Tested on real XCover7 with thermal camera
- Sentry — no new errors

## Agent Team

| Agent | Role | Model | Command |
|---|---|---|---|
| orchestrator | Sprint planning, routing | sonnet | `/orchestrate` |
| ba | AC refinement, contracts | sonnet | `/ba` |
| android | Kotlin, UVC, GL, TDD — main dev agent | sonnet | `/android` |
| qa | Test plans, device testing | sonnet | `/qa` |
| devops | CI/CD, signing, runner | sonnet | `/devops` |

## Source of Truth

| File | Written by | Read by |
|---|---|---|
| `.claude/stories/CAM-N.md` | orchestrator | ALL agents |
| `.claude/contracts/CAM-N-ba.md` | `/ba` | `/android` |
| `.claude/sprint.md` | `/orchestrate` | orchestrator |
| `docs/experts-board/*.md` | Experts Board (initial) | ALL agents |

## Implementation Phases

| Phase | What | Week |
|---|---|---|
| Phase 1 | USB + basic preview via AUSBC | 1-2 |
| Phase 2 | Custom GL pipeline + YUYV shader | 2-3 |
| Phase 3 | Video recording (MediaCodec + MP4) | 3-4 |
| Phase 4 | Polish + Grey8/Y8 mode | 4+ |

## Known Risks

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| USB bandwidth insufficient (isochronous only) | HIGH | HIGH | Verify camera supports bulk transfer |
| AUSBC incompatible with custom camera | MEDIUM | HIGH | Test Day 1, fallback: fork AUSBC |
| Thermal throttling drops FPS | HIGH | MEDIUM | Monitor CPU temp, optimize GL path |
| Grey8/Y8 not supported by AUSBC | MEDIUM | LOW | Phase 2, JNI patch if needed |

## Local Dev

```bash
# Build
./gradlew assembleDebug

# Unit tests
./gradlew testDebugUnitTest

# Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Logcat (filtered)
adb logcat -s KitEyeCamera:V UvcCamera:V AndroidRuntime:E

# Benchmark
./scripts/benchmark_run.sh 60
```

## Key URLs

| Service | URL |
|---|---|
| GitHub Repo | https://github.com/kitopticsua/kiteye-camera-poc |
| Sentry | TBD |

## When in doubt

- Check `.claude/sprint.md` for current sprint state
- Check `.claude/stories/CAM-N.md` for story AC
- Check `docs/experts-board/` for architecture decisions
- Run tests before any commit: `./gradlew testDebugUnitTest`
