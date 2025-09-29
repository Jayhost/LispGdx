#version 100
// File: assets/shaders/skybox.frag
#ifdef GL_ES
precision mediump float;
#endif

uniform samplerCube u_environmentMap;

varying vec3 v_texCoords;

void main() {
    gl_FragColor = textureCube(u_environmentMap, v_texCoords);
}