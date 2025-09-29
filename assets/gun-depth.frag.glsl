// File: assets/gun-depth.frag.glsl
#ifdef GL_ES
#define MED mediump
precision mediump float;
#else
#define MED
#endif

// Packs a float into a 4-component vector
vec4 pack (float depth) {
    const vec4 bitShift = vec4(1.0, 255.0, 255.0 * 255.0, 255.0 * 255.0 * 255.0);
    const vec4 bitMask = vec4(1.0/255.0, 1.0/255.0, 1.0/255.0, 0.0);
    vec4 res = fract(depth * bitShift);
    res -= res.xxyz * bitMask;
    return res;
}

void main() {
    // Pack the depth into RGB, but set Alpha to 0.5 as a special marker
    gl_FragColor = pack(gl_FragCoord.z);
    gl_FragColor.a = 0.5;
}
