#version 300 es
precision mediump float;

in vec2 vTexCoord;
out vec4 fragColor;

// YUYV packed as RGBA8 texture: Y0=r, U=g, Y1=b, V=a (2 pixels per texel)
uniform sampler2D uTexture;
// 0.0 = White-hot (bright = hot), 1.0 = Black-hot (dark = hot)
uniform float uInvertPalette;

void main() {
    vec4 yuyv = texture(uTexture, vTexCoord);

    // Select Y for this pixel based on horizontal position
    float y = (mod(gl_FragCoord.x, 2.0) < 1.0) ? yuyv.r : yuyv.b;
    float u = yuyv.g - 0.5;
    float v = yuyv.a - 0.5;

    // ITU-R BT.601 YUV→RGB
    float r = y + 1.402 * v;
    float g = y - 0.344 * u - 0.714 * v;
    float b = y + 1.772 * u;

    // Apply palette inversion (Black-hot)
    vec3 rgb = mix(vec3(r, g, b), vec3(1.0) - vec3(r, g, b), uInvertPalette);

    fragColor = vec4(clamp(rgb, 0.0, 1.0), 1.0);
}
