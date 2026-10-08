#version 150

uniform sampler2D Sampler0;
uniform float Intensity;   // 0..~1 kosu hizi (+ overdrive)
uniform float Phase;       // 0..1 phasing
uniform float Shake;       // 0..~1.5 ses duvari darbesi
uniform float Time;        // saniye
uniform float Cine;        // 0..1 Blitz sinematik renk ayari
uniform float Slow;        // 0..1 zaman yavaslamasi (hafif mavi ton)
uniform float Rewind;      // 0..1 geri sarma (bant geri sarilirken: izler, renk kaymasi, tarama cizgileri)
uniform vec2 ScreenSize;

in vec2 texCoord;
out vec4 fragColor;

float hash(float n) { return fract(sin(n) * 43758.5453123); }

void main() {
    vec2 uv = texCoord;

    // --- Ses duvari: kisa, sonumlu goruntu sarsintisi ---
    float t = Time;
    uv += vec2(sin(t * 91.0) + 0.5 * sin(t * 53.0), cos(t * 77.0) + 0.5 * sin(t * 43.0)) * 0.0035 * Shake;

    // --- Geri sarma: yukari kayan bant izleri yatay kaydirir, goruntu hafifce iceri cekilir ---
    float rb1 = 0.0, rb2 = 0.0;
    if (Rewind > 0.001) {
        rb1 = exp(-pow((fract(uv.y - t * 1.05) - 0.5) * 16.0, 2.0));
        rb2 = exp(-pow((fract(uv.y * 0.6 + 0.37 - t * 0.66) - 0.5) * 28.0, 2.0));
        float line = hash(floor(uv.y * ScreenSize.y * 0.5) + floor(t * 30.0)) - 0.5;
        uv.x += (rb1 * 0.03 + rb2 * 0.018) * (0.6 + line) * Rewind + line * 0.0015 * Rewind;
        uv = 0.5 + (uv - 0.5) * (1.0 - 0.035 * Rewind);
    }

    vec2 c = uv - 0.5;
    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
    float r = length(vec2(c.x * aspect, c.y));      // merkez 0, kose ~0.9
    float edge = smoothstep(0.22, 0.85, r);          // merkez net, kenarlar etkilenir

    // --- Radyal hiz bulanikligi + renk sapmasi ---
    float blur = Intensity * 0.05 * edge;
    float ca = (0.0022 * Intensity + 0.006 * Shake) * (0.25 + edge);
    vec3 acc = vec3(0.0);
    float wsum = 0.0;
    for (int i = 0; i < 8; i++) {
        float k = float(i) / 7.0;
        float w = 1.0 - k * 0.65;
        vec2 suv = uv - c * blur * k;
        acc.r += texture(Sampler0, suv + c * ca).r * w;
        acc.g += texture(Sampler0, suv).g * w;
        acc.b += texture(Sampler0, suv - c * ca).b * w;
        wsum += w;
    }
    vec3 col = acc / wsum;

    // --- Phasing: yatay titreyen ghosting ---
    if (Phase > 0.001) {
        float frame = floor(t * 60.0);
        float j1 = hash(frame) - 0.5;
        float j2 = hash(frame + 17.0) - 0.5;
        float dx = (0.003 + 0.008 * abs(j1)) * Phase;
        vec3 g1 = texture(Sampler0, uv + vec2(dx, 0.0)).rgb;
        vec3 g2 = texture(Sampler0, uv - vec2(dx * 1.4, 0.0)).rgb;
        vec3 g3 = texture(Sampler0, uv + vec2(dx * 3.0 * j2, 0.0)).rgb;
        vec3 ghost = (g1 + g2 + g3) / 3.0;
        col = mix(col, ghost, 0.5 * Phase);
        col.r = mix(col.r, g1.r, 0.35 * Phase);
        col.b = mix(col.b, g2.b, 0.35 * Phase);
    }

    // --- Cok hafif kenar karartma (hiz tuneli hissi) ---
    col *= 1.0 - 0.2 * clamp(Intensity, 0.0, 1.0) * smoothstep(0.5, 1.05, r);

    // --- Zaman yavaslamasi: hafif mavi, biraz soluk, cok hafif kenar karartma ---
    if (Slow > 0.001) {
        float lum = dot(col, vec3(0.299, 0.587, 0.114));
        col = mix(col, vec3(lum), 0.22 * Slow);
        col *= mix(vec3(1.0), vec3(0.86, 0.95, 1.12), Slow);
        col += vec3(0.0, 0.008, 0.03) * Slow;
        col *= 1.0 - 0.18 * Slow * smoothstep(0.45, 1.1, r);
        col = clamp(col, 0.0, 1.0);
    }

    // --- Geri sarma: yatay renk ayrismasi, soluk ve serin ton, tarama cizgileri, bant izlerinde parlama, kar ---
    if (Rewind > 0.001) {
        float sh = 0.006 * Rewind;
        col.r = mix(col.r, texture(Sampler0, uv + vec2(sh, 0.0)).r, 0.8 * Rewind);
        col.b = mix(col.b, texture(Sampler0, uv - vec2(sh, 0.0)).b, 0.8 * Rewind);
        float lum = dot(col, vec3(0.299, 0.587, 0.114));
        col = mix(col, vec3(lum), 0.32 * Rewind);
        col *= mix(vec3(1.0), vec3(0.9, 0.97, 1.1), Rewind);
        col *= 1.0 - 0.09 * Rewind * (0.5 + 0.5 * sin(uv.y * ScreenSize.y * 1.4));
        col += vec3(0.16) * (rb1 + 0.6 * rb2) * Rewind;
        float sn = hash(floor(uv.x * ScreenSize.x * 0.5) * 7.13 + floor(uv.y * ScreenSize.y * 0.5) * 3.71 + floor(t * 24.0));
        col += vec3(step(0.9965, sn) * 0.35 * Rewind);
        col *= 1.0 - 0.22 * Rewind * smoothstep(0.45, 1.1, r);
        col = clamp(col, 0.0, 1.0);
    }

    fragColor = vec4(col, 1.0);
}
