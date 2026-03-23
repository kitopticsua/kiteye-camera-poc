#version 300 es
precision mediump float;

in vec2 vTexCoord;
out vec4 fragColor;

// NV21 Y-plane uploaded as GL_LUMINANCE (640x480): each texel = luminance = thermal intensity
uniform sampler2D uTexture;
// 0.0 = White-hot (bright = hot), 1.0 = Black-hot (dark = hot)
uniform float uInvertPalette;

void main() {
    float y = texture(uTexture, vTexCoord).r;

    // Apply palette: White-hot or Black-hot
    float intensity = mix(y, 1.0 - y, uInvertPalette);

    fragColor = vec4(intensity, intensity, intensity, 1.0);
}
