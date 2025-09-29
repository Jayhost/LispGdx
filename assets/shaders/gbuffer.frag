// File: assets/shaders/gbuffer.frag
// This version removes the gl_FrontFacing check.
#version 100
#extension GL_EXT_draw_buffers : require

#ifdef GL_ES
precision mediump float;
#endif

// Input varyings from vertex shader
varying vec2 v_texCoords;
varying vec3 v_T; // Tangent
varying vec3 v_B; // Bitangent
varying vec3 v_N; // Normal

// Material Textures
uniform sampler2D u_albedoTexture;
uniform sampler2D u_normalTexture;
uniform sampler2D u_metallicRoughnessTexture;
uniform sampler2D u_occlusionTexture;

// Flags for texture presence
uniform float u_hasAlbedoTexture;
uniform float u_hasNormalTexture;
uniform float u_hasMetallicRoughnessTexture;
uniform float u_hasOcclusionTexture;

vec3 unpackNormal(vec4 normal) {
    return normalize(normal.xyz * 2.0 - 1.0);
}

void main() {
    // Albedo and Metallic
    vec4 albedo = vec4(1.0);
    float metallic = 0.0;
    if (u_hasAlbedoTexture > 0.5) { albedo = texture2D(u_albedoTexture, v_texCoords); }
    if (u_hasMetallicRoughnessTexture > 0.5) { metallic = texture2D(u_metallicRoughnessTexture, v_texCoords).b; }
    gl_FragData[0] = vec4(albedo.rgb, metallic);

    // Normal and Roughness
    vec3 worldNormal;
    if (u_hasNormalTexture > 0.5) {
        mat3 tbn = mat3(normalize(v_T), normalize(v_B), normalize(v_N));
        vec3 tangentNormal = unpackNormal(texture2D(u_normalTexture, v_texCoords));
        worldNormal = normalize(tbn * tangentNormal);
    } else {
        worldNormal = normalize(v_N);
    }
    // The incorrect gl_FrontFacing check is now gone.

    float roughness = 1.0;
    if (u_hasMetallicRoughnessTexture > 0.5) { roughness = texture2D(u_metallicRoughnessTexture, v_texCoords).g; }
    gl_FragData[1] = vec4(worldNormal, roughness);

    // AO and Emissive
    float ao = 1.0;
    if (u_hasOcclusionTexture > 0.5) { ao = texture2D(u_occlusionTexture, v_texCoords).r; }
    vec3 emissive = vec3(0.0);
    gl_FragData[2] = vec4(ao, emissive);
}