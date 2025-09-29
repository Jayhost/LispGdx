// File: assets/shaders/gbuffer.vert
// This version correctly calculates and passes Tangent, Bitangent, and Normal
// vectors required for normal mapping.
#version 100

// Input attributes from the 3D model's mesh
attribute vec3 a_position;
attribute vec3 a_normal;
attribute vec2 a_texCoord0;
attribute vec4 a_tangent; // ADDED: For normal mapping

// Uniforms
uniform mat4 u_projViewTrans;
uniform mat4 u_worldTrans;

// Varyings (outputs to the fragment shader)
varying vec2 v_texCoords;
varying vec3 v_T; // Tangent
varying vec3 v_B; // Bitangent
varying vec3 v_N; // Normal

void main() {
    // Pass the texture coordinates directly to the fragment shader
    v_texCoords = a_texCoord0;

    // Transform T, B, N vectors to world space
    mat3 normalMatrix = mat3(u_worldTrans);
    v_T = normalize(normalMatrix * a_tangent.xyz);
    v_N = normalize(normalMatrix * a_normal);
    v_B = normalize(cross(v_N, v_T) * a_tangent.w); // Bitangent calculation

    // Calculate the final screen position of the vertex
    gl_Position = u_projViewTrans * u_worldTrans * vec4(a_position, 1.0);
}