#ifdef GL_ES
#define LOWP lowp
#define MED mediump
precision lowp float;
#else
#define LOWP
#define MED
#endif

varying MED vec2 v_texCoords0;
varying MED vec2 v_texCoords1;
varying MED vec2 v_texCoords2;
varying MED vec2 v_texCoords3;
varying MED vec2 v_texCoords4;

uniform sampler2D u_texture;


float unpackDepth(vec4 color) {
    const vec4 bitShift = vec4(1.0, 1.0/255.0, 1.0/(255.0*255.0), 1.0/(255.0*255.0*255.0));
    return dot(color, bitShift);
}

void main() {
    // Your Laplacian filter logic is correct, it just needs the right input data
    float depth0 = unpackDepth(texture2D(u_texture, v_texCoords0));
    float depth1 = unpackDepth(texture2D(u_texture, v_texCoords1));
    float depth2 = unpackDepth(texture2D(u_texture, v_texCoords2));
    float depth3 = unpackDepth(texture2D(u_texture, v_texCoords3));
    float depth4 = unpackDepth(texture2D(u_texture, v_texCoords4));
    
    float edgeValue = abs(depth0 + depth1 - (4.0 * depth2) + depth3 + depth4);

    if (edgeValue > 0.004)
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0); // Black outline
    else
        gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0); // Transparent
}