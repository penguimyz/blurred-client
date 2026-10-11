#version 330

// Blurred liquid glass: a rounded slab of glass over the world. The quad's
// UV0 is the position inside the slab (GUI units), UV2 its size (in quarter
// units) and the colour's alpha its corner radius / 64; RGB tints the glass.
// UV1 is where the latest click splashed (quarter units) and Normal holds how
// far through it is as (t, 1 - t, 0), or (0, 0, 1) for none.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec2 local;
out vec4 tint;
flat out vec2 size;
flat out vec3 splash;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    local = UV0;
    tint = Color;
    size = vec2(UV2) / 4.0;
    float t = Normal.z > 0.5 ? -1.0 : Normal.x / max(Normal.x + Normal.y, 1e-4);
    splash = vec3(vec2(UV1) / 4.0, t);
}
