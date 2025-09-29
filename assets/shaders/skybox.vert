// File: assets/shaders/skybox.vert
// This forces the cube to always be drawn "behind" everything else.
#version 100

attribute vec3 a_position;

uniform mat4 u_projViewTrans;

varying vec3 v_texCoords;

void main() {
    v_texCoords = a_position;
    // Use the combined matrix from Java
    vec4 pos = u_projViewTrans * vec4(a_position, 1.0);
    // This trick ensures the cube is always at the far plane (max depth)
    gl_Position = pos.xyww;
}