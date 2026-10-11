#version 330
#define BLURRED_FOLIAGE
#define BLURRED_WET
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec3 blurredWorld;
out vec3 blurredView;

vec4 minecraft_sample_lightmap(sampler2D lightMap, ivec2 uv) {
    return texture(lightMap, clamp((uv / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}

void main() {
    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    vec3 world = Position + vec3(ChunkPosition);

    // Leaves, vines and plants are marked with an alpha just under opaque:
    // 254 minus how far the vertex may move (see FoliageMarks). Everything
    // else, including grass blocks and water, is left alone.
    vec4 color = Color;
    float mark = 254.0 - floor(Color.a * 255.0 + 0.5);
    bool marked = mark > -0.5 && mark < 10.5;
    if (marked) {
        color.a = 1.0;
    }
#ifdef BLURRED_FOLIAGE
    if (marked && mark > 0.5) {
        // Displacement depends only on world position and time, so leaves
        // next to each other move together and no gaps open.
        float weight = mark / 10.0;
        float t = GameTime * 1800.0;
        float gust = 0.6 + 0.4 * sin(GameTime * 260.0 + world.x * 0.05 + world.z * 0.03);
        vec2 sway = vec2(sin(t + world.x * 0.7 + world.z * 0.4), cos(t * 0.8 + world.z * 0.6 + world.x * 0.3));
        pos.xz += sway * 0.1 * gust * weight;
        pos.y += sin(t * 1.3 + world.x + world.z) * 0.015 * weight;
    }
#endif

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
    blurredWorld = world;
    blurredView = pos;
}
