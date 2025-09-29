#version 330 core
precision highp float;

in vec2 v_uv;
layout(location = 0) out vec4 o_color;

// your G‑Buffer attachments
uniform sampler2D u_tex0;     // albedo (RGBA8)
uniform sampler2D u_tex1;     // normals (RGBA16F, packed 0→1)
uniform sampler2D u_tex2;     // specular (RGBA8, .r holds strength)
// uniform sampler2D u_tex3;     // specular (RGBA8, .r holds strength)


// lighting uniforms
uniform vec3 u_lightDir;      // *directional* light direction (view‑space), normalized
uniform vec3 u_lightColor;    // e.g. vec3(1.0)
uniform vec3 u_ambientColor;  // e.g. vec3(0.1)

void main() {
    // fetch G‑Buffer
    vec3 albedo  = texture(u_tex0, v_uv).rgb;
    vec3 normal  = texture(u_tex1, v_uv).rgb * 2.0 - 1.0;
    float spec   = texture(u_tex2, v_uv).r;

    // lambertian diffuse
    float NdotL = max(dot(normal, -u_lightDir), 0.0);
    vec3 diffuse = albedo * NdotL * u_lightColor;

    // ambient
    vec3 ambient = albedo * u_ambientColor;

    // simple Blinn‑Phong around view direction (0,0,1) in view‑space
    vec3 viewDir = vec3(0.0, 0.0, 1.0);
    vec3 halfway = normalize(-u_lightDir + viewDir);
    float NdotH = max(dot(normal, halfway), 0.0);
    float shininess = 16.0;
    vec3 specular = u_lightColor * spec * pow(NdotH, shininess);

    // combine
    o_color = vec4(ambient + diffuse + specular, 1.0);
//     o_color = vec4(diffuse + (ambient * 0.001) + (specular * 0.00001),1.0); //test only diffuse
}
