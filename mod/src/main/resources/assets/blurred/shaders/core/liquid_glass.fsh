#version 330

// Blurred liquid glass. Sampler0 is a copy of the world behind the menu.
// The slab bends what's behind it like a thick lens: strongest at the rim,
// with a little colour fringing, a faint frost, and a bright specular edge
// lit from the top left.

uniform sampler2D Sampler0;

in vec2 local;
in vec4 tint;
flat in vec2 size;
flat in vec3 splash;

out vec4 fragColor;

float roundedBox(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    vec2 halfSize = size * 0.5;
    float r = min(tint.a * 64.0, min(halfSize.x, halfSize.y));
    vec2 p = local - halfSize;
    float d = roundedBox(p, halfSize, r);

    // GUI units per screen pixel, for anti-aliasing and converting offsets.
    float unit = max(1e-4, length(vec2(dFdx(local.x), dFdy(local.y))) * 0.7071);
    float coverage = clamp(0.5 - d / unit, 0.0, 1.0);
    if (coverage <= 0.0) {
        discard;
    }

    // Outward normal of the edge (GUI space, y down).
    vec2 e = vec2(0.35, 0.0);
    vec2 n = vec2(roundedBox(p + e.xy, halfSize, r) - roundedBox(p - e.xy, halfSize, r),
                  roundedBox(p + e.yx, halfSize, r) - roundedBox(p - e.yx, halfSize, r));
    n = n / max(length(n), 1e-4);

    // How far into the rim we are: 0 in the flat middle, 1 at the very edge.
    float bezel = min(14.0, min(halfSize.x, halfSize.y) * 0.9);
    float rim = clamp(1.0 + d / bezel, 0.0, 1.0);
    float bend = rim * rim * rim;

    // Refraction: near the rim, look further in (the edge magnifies what's
    // inside); across the slab, a gentle lens towards the middle.
    // Thick glass isn't perfectly flat: a gentle liquid warp, fixed to the
    // glass (not moving), plus the rim lens and an overall magnification.
    vec2 q = (gl_FragCoord.xy * unit) * 0.06;
    vec2 warp = vec2(sin(q.y * 1.3 + sin(q.x * 0.7) * 2.2), cos(q.x * 1.1 + sin(q.y * 0.9) * 2.0)) * 4.5
              + vec2(sin(q.y * 3.1 + q.x), cos(q.x * 2.7 - q.y)) * 1.2;
    vec2 offsetUnits = -n * bend * bezel * 1.6 - p * 0.10 + warp * (0.4 + 0.6 * (1.0 - rim));
    // A click is a drop landing on water: the surface dips under it, then a
    // wave runs outwards and dies away. The slope of that surface bends the
    // light like the rest of the lens, and catches the light at the crest.
    float glint = 0.0;
    if (splash.z >= 0.0) {
        float t = splash.z;
        vec2 fromDrop = local - splash.xy;
        float dist = length(fromDrop);
        vec2 dir = fromDrop / max(dist, 1e-3);
        float fade = (1.0 - t) * (1.0 - t);
        // The dip where it landed, sinking and filling back in.
        float dip = exp(-dist * dist / (30.0 + 420.0 * t)) * sin(min(t * 7.0, 3.14159)) * 14.0;
        // The ring: a few crests travelling out, spreading and fading.
        float front = 4.0 + t * 95.0;
        float band = exp(-pow((dist - front) / (7.0 + 18.0 * t), 2.0));
        float wave = sin((dist - front) * 0.45) * band * 15.0 * fade;
        offsetUnits += dir * (wave - dip);
        glint = max(0.0, cos((dist - front) * 0.45)) * band * fade
              + exp(-dist * dist / 40.0) * max(0.0, 1.0 - t * 4.0);
    }

    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    vec2 offset = vec2(offsetUnits.x, -offsetUnits.y) / unit * texel; // screen y is up
    vec2 uv = gl_FragCoord.xy * texel;

    // Colour fringing at the rim, and a slight frost everywhere.
    vec3 c;
    c.r = texture(Sampler0, uv + offset * 1.06).r;
    c.g = texture(Sampler0, uv + offset).g;
    c.b = texture(Sampler0, uv + offset * 0.94).b;
    vec3 soft = (texture(Sampler0, uv + offset + vec2(texel.x * 2.0, 0.0)).rgb
               + texture(Sampler0, uv + offset - vec2(texel.x * 2.0, 0.0)).rgb
               + texture(Sampler0, uv + offset + vec2(0.0, texel.y * 2.0)).rgb
               + texture(Sampler0, uv + offset - vec2(0.0, texel.y * 2.0)).rgb) * 0.25;
    c = mix(c, soft, 0.25);

    // Tint (white = clear glass), a touch of haze, and a slight darkening so text reads.
    float tintAmount = 0.75 * (1.0 - min(min(tint.r, tint.g), tint.b));
    c = mix(c, c * 0.55 + tint.rgb * 0.55, tintAmount);
    c = mix(c, vec3(1.0), 0.06) * 0.86;

    // Light inside the rim band, and a crisp specular edge lit from the top left.
    c += vec3(1.0) * pow(rim, 4.0) * 0.10;
    float edge = clamp(1.0 + d / (1.4 * unit), 0.0, 1.0);
    float facing = dot(n, normalize(vec2(-1.0, -1.2)));
    float spec = edge * (0.28 + 0.72 * max(facing, 0.0) + 0.35 * max(-facing, 0.0));
    c += vec3(spec * 0.9);
    c += vec3(glint * 0.35);

    fragColor = vec4(clamp(c, 0.0, 1.0), coverage);
}
