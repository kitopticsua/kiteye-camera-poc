# Android UVC Thermal Camera Viewer — PoC UI/UX Design

**Expert:** UI/UX Designer
**Date:** 2026-03-23
**Design System:** Material 3, Dark Theme
**Orientation:** Landscape-only (locked)

---

## 1. Screen Map

| Screen | Trigger | Purpose |
|---|---|---|
| S1 | App launch, no camera | Splash / USB waiting |
| S2 | USB device detected | Permission dialog |
| S3 | Permission granted + stream | Camera preview (main) |
| S4 | Record pressed | Recording active overlay |
| S5 | Settings tapped | Settings bottom sheet |
| S6 | USB detached | Error — disconnected |
| S7 | Permission denied | Error — permission |
| S8 | UVC open error | Error — stream failed |
| S9 | Recording stopped | Saved confirmation snackbar |

---

## 2. Main Camera Preview (S3)

```
╔══════════════════════════════════════════════════════════════════╗
║                                                                  ║
║  ┌──────────────────────────────────────────────────┐  ┌──────┐ ║
║  │                                                  │  │      │ ║
║  │                                                  │  │  ⚙   │ ║
║  │                                                  │  │ 72dp │ ║
║  │         THERMAL CAMERA FEED (640x480)            │  ├──────┤ ║
║  │         aspect-fit, fullscreen                   │  │      │ ║
║  │                                                  │  │  ●   │ ║
║  │                                                  │  │ REC  │ ║
║  │                                                  │  │ 72dp │ ║
║  │                                                  │  ├──────┤ ║
║  │                                                  │  │      │ ║
║  └──────────────────────────────────────────────────┘  │  W/B │ ║
║                                                        │ 72dp │ ║
║  ┌────────────────────────────────────────────┐        └──────┘ ║
║  │ FPS: 52.3  DROP: 0  USB: 32MB/s  YUV422   │                 ║
║  └────────────────────────────────────────────┘                 ║
╚══════════════════════════════════════════════════════════════════╝

ZONES:
  [A] Camera feed      — 80% width, aspect-ratio locked
  [B] Right action bar — 72dp wide, 3 FABs stacked
  [C] Stats bar        — 32dp tall, monospace, semi-transparent
```

---

## 3. All Screen Wireframes

### S1 — Splash / USB Waiting

```
╔════════════════════════════════════════════╗
║                                            ║
║            [LOGO 96dp]                     ║
║                                            ║
║         KitEye Thermal Viewer              ║
║                                            ║
║           ⊙ ⊙ ⊙  (loading)               ║
║                                            ║
║    Waiting for USB camera connection...    ║
║    Connect camera via USB-C cable          ║
║                                            ║
╚════════════════════════════════════════════╝
Background: #121212
```

### S4 — Recording Active

Changes from S3:
```
Stats bar adds:  ● REC  00:01:23  (● blinks 1Hz, red #FF5252)
REC FAB changes: icon = ■ (stop), bg = #CF6679

Top-right of feed:
┌──────────────────┐
│  ● REC  00:01:23 │   12sp, white on #80000000
└──────────────────┘
```

### S5 — Settings Bottom Sheet

```
┌────────────────────────────────────────────────┐
│  ─────── (drag handle)                          │
│                                                  │
│  Settings                                        │
│  ─────────────────────────────────────────       │
│                                                  │
│  VIDEO FORMAT                                    │
│  ┌──────────────┐  ┌──────────────┐             │
│  │ ✓ YUV422     │  │   GREY8      │             │
│  └──────────────┘  └──────────────┘             │
│                                                  │
│  DISPLAY MODE                                    │
│  ┌──────────────┐  ┌──────────────┐             │
│  │ ✓ WHITE-HOT  │  │  BLACK-HOT   │             │
│  └──────────────┘  └──────────────┘             │
│                                                  │
│  OVERLAYS                                        │
│  Show FPS stats              [●──] ON           │
│  Show crosshair              [──●] OFF          │
│                                                  │
│  SAVE LOCATION                                   │
│  /DCIM/Thermal/                      [CHANGE]   │
└────────────────────────────────────────────────┘
```

### S6/S7/S8 — Error States

```
╔════════════════════════════════════════════╗
║                                            ║
║              ⚠  (56dp icon)               ║
║                                            ║
║         Camera Disconnected                ║
║         USB cable removed                  ║
║                                            ║
║         [  RECONNECT  ]                    ║
║                                            ║
╚════════════════════════════════════════════╝

S7: "USB Permission Denied" → [OPEN SETTINGS]
S8: "Stream Error: [detail]" → [RETRY] + [CHANGE FORMAT]
```

---

## 4. Component Specifications

### Action FABs

| Property | Value |
|---|---|
| Size | 72x72dp (glove-friendly) |
| Shape | RoundedCornerShape(16dp) |
| Gap | 12dp between FABs |
| Margin | 16dp from edge |

**Record FAB states:**
| State | Icon | BG Color |
|---|---|---|
| Idle | circle filled | #1E88E5 (primary) |
| Recording | stop square | #CF6679 (error) |
| Disabled | circle outline | #3C3C3C |

**White/Black-hot FAB:**
| State | Icon | BG |
|---|---|---|
| White-hot | sun icon | #2D2D2D, tint #FFFFFF |
| Black-hot | moon icon | #2D2D2D, tint #424242 |

Single tap toggles between White-hot and Black-hot.

### Stats Bar

| Property | Value |
|---|---|
| Height | 32dp |
| BG | #CC000000 (80% black) |
| Font | Monospace, 12sp, #B3FFFFFF |
| Update | Every 500ms |
| Toggle | Via Settings |

### Crosshair (optional)

| Property | Value |
|---|---|
| Size | 24x24dp lines |
| Thickness | 1.5dp |
| Color | #FFFFFF + 2dp shadow |
| Gap at center | 4dp |

---

## 5. Interaction Flows

### First Launch

```
App Start → [S1 Splash] → USB attach → [S2 Permission]
  → Granted → Init UVC → [S3 Preview]
  → Denied → [S7 Error]
```

### Record

```
[S3 Preview] → tap REC → [S4 Recording] → tap STOP → Snackbar "Saved"
                                         → USB disconnect → save partial → [S6 Error]
```

### Disconnect / Reconnect

```
[S3 Preview] → USB detach → [S6 Error]
  → USB reattach + permission granted → [S3 Preview]
  → USB reattach + no permission → [S2 Permission]
```

### Display Mode Toggle

```
[S3 Preview] → tap W/B FAB → shader switches White-hot ↔ Black-hot
  (instant, no stream restart needed — shader uniform change only)
```

---

## 6. Design Decisions

### Theme

```xml
<style name="Theme.KiteyeThermal" parent="Theme.Material3.Dark">
    <item name="colorSurface">#121212</item>
    <item name="colorPrimary">#FF6D00</item>  <!-- thermal orange -->
    <item name="colorOnPrimary">#1A0A00</item>
    <item name="colorError">#CF6679</item>
</style>
```

### Orientation

```xml
<activity android:screenOrientation="sensorLandscape" />
```
`sensorLandscape` = landscape locked, but flips 180° if inverted.

### Touch for Gloves

| Component | Size | Rationale |
|---|---|---|
| FABs | 72dp | Standard 56dp too small |
| Settings chips | 80x56dp | Extra width |
| Toggle switches | 48dp target | M3 default |

### Screen Always On

```kotlin
window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
```

### Vibration Feedback

- Record start: 1x short vibration
- Record stop: 2x short vibration
- Error: 1x long vibration

---

## 7. PoC Scope

**In scope:**
- Splash + USB wait
- Permission flow
- Live preview
- White-hot / Black-hot display modes
- Record / Stop / Save
- FPS overlay
- Settings sheet
- Error handling

**Out of scope:**
- Playback screen
- Zoom / pan
- Multiple cameras
- Network streaming
- Temperature calibration
- Color palettes beyond White-hot / Black-hot
