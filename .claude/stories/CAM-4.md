# CAM-4 — OpenGL Preview (YUYV→RGB Shader)

## Goal
Implement `ThermalSurfaceView` (GLSurfaceView) and `ThermalGLRenderer` with GLSL fragment shader for GPU YUYV→RGB conversion. Support White-hot and Black-hot display modes via shader uniform.

## Type
android (Kotlin, OpenGL ES 3.2, GLSL)

## Priority
P1 — Phase 1 core deliverable

## Dependencies
- CAM-2 (TripleBuffer — GL thread reads frames)

## Acceptance Criteria
- [ ] `ThermalSurfaceView` extends `GLSurfaceView`, sets EGL 3.2 context
- [ ] `ThermalGLRenderer` implements `GLSurfaceView.Renderer`
- [ ] GLSL fragment shader converts YUYV→RGB correctly (ITU-R BT.601)
- [ ] Shader uniform `uInvertPalette`: 0=White-hot, 1=Black-hot
- [ ] `glTexSubImage2D` used for texture upload (zero-copy, no new ByteBuffer)
- [ ] `onDrawFrame()` allocates zero objects
- [ ] Full screen display, maintains 640:480 aspect ratio in landscape
- [ ] Smooth rendering at 60fps (target <7ms per frame total pipeline)

## GLSL Shader (reference from ADR-002)
```glsl
// Fragment shader: YUYV packed as RGBA texture
// Y0 = r, U = g, Y1 = b, V = a (2 pixels per texel)
uniform sampler2D uTexture;
uniform float uInvertPalette;  // 0=white-hot, 1=black-hot

void main() {
    vec2 uv = vTexCoord;
    vec4 yuyv = texture(uTexture, uv);
    float y = (mod(gl_FragCoord.x, 2.0) < 1.0) ? yuyv.r : yuyv.b;
    float u = yuyv.g - 0.5;
    float v = yuyv.a - 0.5;
    float r = y + 1.402 * v;
    float g = y - 0.344 * u - 0.714 * v;
    float b = y + 1.772 * u;
    vec3 rgb = mix(vec3(r,g,b), vec3(1.0)-vec3(r,g,b), uInvertPalette);
    fragColor = vec4(clamp(rgb, 0.0, 1.0), 1.0);
}
```

## Test Plan
### Unit Tests
| ID | Test | Expected |
|---|---|---|
| UT-01 | ShaderProgram compiles without error | no GL errors |
| UT-02 | Pure white YUYV (Y=255,U=128,V=128) → white pixel | RGB(1,1,1) |
| UT-03 | Pure black YUYV (Y=0,U=128,V=128) → black pixel | RGB(0,0,0) |
| UT-04 | White-hot: high Y → bright | luminance positive |
| UT-05 | Black-hot: high Y → dark | luminance inverted |

### Device Tests (manual)
| ID | Scenario | Expected |
|---|---|---|
| DT-01 | Camera connected → live preview on screen | smooth image, no tearing |
| DT-02 | Toggle White-hot/Black-hot | colours invert immediately |
| DT-03 | 5 min continuous preview | no FPS drop, no memory leak |

## Files to Create
- `rendering/ThermalSurfaceView.kt`
- `rendering/ThermalGLRenderer.kt`
- `rendering/ShaderProgram.kt`
- `rendering/test/ShaderProgramTest.kt`
- `app/src/main/res/raw/thermal_fragment.glsl`
- `app/src/main/res/raw/thermal_vertex.glsl`

## Definition of Done
- [ ] All unit tests pass
- [ ] Lint clean
- [ ] DT-01..DT-03 passed on XCover7
- [ ] GPU usage <5% in Android Studio GPU profiler
- [ ] No allocations in onDrawFrame()
