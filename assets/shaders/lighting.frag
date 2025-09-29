// File: assets/shaders/lighting.frag
// This final version contains the robust check for two-sided lighting.
#version 100

#ifdef GL_ES
precision highp float;
#endif

varying vec2 v_texCoords;

// G-Buffer inputs
uniform sampler2D u_albedoMetallic;
uniform sampler2D u_normalRoughness;
uniform sampler2D u_aoEmissive;
uniform sampler2D u_depth;

// Environment inputs
uniform samplerCube u_diffuseEnv;
uniform samplerCube u_specularEnv;
uniform sampler2D u_brdfLUT;

// Other uniforms
uniform vec3 u_cameraPos;
uniform mat4 u_invProjView;

const float PI = 3.14159265359;

vec3 getWorldPos(float depth, vec2 texCoords) {
    vec2 ndc = texCoords * 2.0 - 1.0;
    vec4 clipPos = vec4(ndc, depth * 2.0 - 1.0, 1.0);
    vec4 worldPos = u_invProjView * clipPos;
    return worldPos.xyz / worldPos.w;
}

vec3 fresnelSchlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

void main() {
    // 1. Sample from G-Buffer
    vec4 texAlbedoMetallic = texture2D(u_albedoMetallic, v_texCoords);
    vec3 albedo = texAlbedoMetallic.rgb;
    float metallic = texAlbedoMetallic.a;

    vec4 texNormalRoughness = texture2D(u_normalRoughness, v_texCoords);
    vec3 N = normalize(texNormalRoughness.rgb);
    float roughness = texNormalRoughness.a;

    vec4 texAoEmissive = texture2D(u_aoEmissive, v_texCoords);
    float ao = texAoEmissive.r;
    vec3 emissive = texAoEmissive.gba;

    float depth = texture2D(u_depth, v_texCoords).r;
    if (depth == 1.0) { discard; }

    vec3 P = getWorldPos(depth, v_texCoords);
    vec3 V = normalize(u_cameraPos - P);

    // =================================================================
    // THE NEW, ROBUST FIX FOR TWO-SIDED LIGHTING
    // If the normal is pointing away from the camera, flip it.
    // This correctly lights both front and back faces.
    // =================================================================
    if (dot(N, V) < 0.0) {
        N = -N;
    }

    vec3 R = reflect(-V, N);

    // 2. Calculate PBR parameters
    vec3 F0 = vec3(0.04);
    F0 = mix(F0, albedo, metallic);

    // 3. Calculate lighting
    vec3 prefilteredColor = textureCube(u_specularEnv, R).rgb;
    vec2 brdf = texture2D(u_brdfLUT, vec2(max(dot(N, V), 0.0), roughness)).rg;
    vec3 specular = prefilteredColor * (F0 * brdf.x + brdf.y);

    vec3 kS = fresnelSchlick(max(dot(N, V), 0.0), F0);
    vec3 kD = 1.0 - kS;
    kD *= 1.0 - metallic;
    
    vec3 irradiance = textureCube(u_diffuseEnv, N).rgb;
    vec3 diffuse = irradiance * albedo;

    // 4. Combine and output final color
    vec3 ambient = (kD * diffuse + specular) * ao;
    vec3 color = ambient + emissive;

    color = color / (color + vec3(1.0));
    color = pow(color, vec3(1.0/2.2));

    gl_FragColor = vec4(color, 1.0);
}