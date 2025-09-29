#version 100
// File: shaders/lighting.vert
attribute vec3 a_position;
attribute vec2 a_texCoord0;

varying vec2 v_texCoords;

void main() {
    v_texCoords = a_texCoord0;
    gl_Position = vec4(a_position, 1.0);
}