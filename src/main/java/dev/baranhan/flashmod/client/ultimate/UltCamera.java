package dev.baranhan.flashmod.client.ultimate;

import dev.baranhan.flashmod.ultimate.UltimatePhase;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Sinematik kamera: easing, keyframe, Catmull-Rom (ayni kesme grubu icinde) ve faz basina tablolar.
 * Konumlar ARENA ya da SCENE uzayinda; dinamik bakislar (canli hedef, orta nokta, temas) director'da cozulur.
 */
public final class UltCamera {
    public enum Ease {
        LINEAR, IN_QUAD, OUT_QUAD, IN_OUT_SINE, IN_CUBIC, OUT_CUBIC, IN_OUT_CUBIC, IN_EXPO, OUT_EXPO, OUT_BACK, SMOOTHSTEP;

        public float apply(float u) {
            u = Mth.clamp(u, 0F, 1F);
            switch (this) {
                case IN_QUAD: return u * u;
                case OUT_QUAD: return 1F - (1F - u) * (1F - u);
                case IN_OUT_SINE: return (float) (-(Math.cos(Math.PI * u) - 1D) / 2D);
                case IN_CUBIC: return u * u * u;
                case OUT_CUBIC: { float v = 1F - u; return 1F - v * v * v; }
                case IN_OUT_CUBIC: return u < 0.5F ? 4F * u * u * u : 1F - (float) Math.pow(-2F * u + 2F, 3) / 2F;
                case IN_EXPO: return u == 0F ? 0F : (float) Math.pow(2D, 10D * u - 10D);
                case OUT_EXPO: return u == 1F ? 1F : 1F - (float) Math.pow(2D, -10D * u);
                case OUT_BACK: { float c1 = 1.70158F, c3 = c1 + 1F, v = u - 1F; return 1F + c3 * v * v * v + c1 * v * v; }
                case SMOOTHSTEP: return u * u * (3F - 2F * u);
                default: return u;
            }
        }
    }

    /** Bakis modu: sabit nokta ya da dinamik. */
    public static final int LOOK_FIXED = 0, LOOK_TARGET = 1, LOOK_MID = 2, LOOK_CONTACT = 3;

    public record Key(float t, Vec3 pos, Vec3 look, int lookMode, float fov, float roll, Ease ease, boolean cut) {}

    public record Track(UltimatePhase.Space space, Key[] keys) {}

    public record State(Vec3 pos, Vec3 look, int lookMode, float fov, float roll, UltimatePhase.Space space) {}

    private UltCamera() {}

    private static Key k(float t, double x, double y, double z, double lx, double ly, double lz, float fov, float roll,
                         Ease e, boolean cut) {
        return new Key(t, new Vec3(x, y, z), new Vec3(lx, ly, lz), LOOK_FIXED, fov, roll, e, cut);
    }

    private static Key kd(float t, double x, double y, double z, int mode, Vec3 offset, float fov, float roll, Ease e,
                          boolean cut) {
        return new Key(t, new Vec3(x, y, z), offset, mode, fov, roll, e, cut);
    }

    /** Oturuma ozel tablolar (D ve hedef boyutuna gore). */
    public static Track[] build(float d, float targetWidth) {
        double wx = Math.max(0D, (targetWidth - 0.6D) * 0.5D);
        UltimatePhase.Space A = UltimatePhase.Space.ARENA, S = UltimatePhase.Space.SCENE;
        Ease L = Ease.LINEAR;
        return new Track[]{
                // WINDUP: on-sag-alcak, yuze bakis
                new Track(A, new Key[]{
                        k(4, 0.90 + wx, 1.15, 1.70, 0, 1.50, 0, 62, 0, Ease.IN_OUT_CUBIC, false),
                        k(12, 0.62 + wx, 1.30, 1.35, 0, 1.55, 0, 55, -4, Ease.OUT_QUAD, false),
                        k(18, 0.48 + wx, 1.40, 1.12, 0, 1.60, 0, 49, -7, Ease.IN_OUT_SINE, false),
                        k(22, 0.42 + wx, 1.44, 1.02, 0, 1.62, 0, 46, -8, L, false)}),
                // DEPART: hedefin sagi, alcak; caster arkadan gecip ekranin soluna uzaklasir
                new Track(A, new Key[]{
                        k(22, 3.20, 1.00, d - 0.60, 0, 1.10, d, 60, -3, L, true),
                        k(26, 3.00, 1.05, d - 0.20, -0.40, 1.10, d + 0.20, 64, -2, Ease.OUT_QUAD, false),
                        k(34, 2.80, 1.10, d + 0.30, -0.60, 1.00, d + 3.50, 70, 0, Ease.IN_OUT_CUBIC, false),
                        k(40, 2.70, 1.10, d + 0.40, -0.60, 1.00, d + 8.00, 74, 0, L, false)}),
                // VOID: omuz arkasi -> genis yan cekim
                new Track(S, new Key[]{
                        k(40, -1.50, 1.05, -1.00, 0, 0.85, 0.50, 58, 2, L, true),
                        k(52, -1.30, 1.00, -0.70, 0, 0.80, 0.50, 55, 3, Ease.IN_OUT_SINE, false),
                        k(56, -2.00, 1.00, -1.60, 0, 0.90, 0.80, 62, 1, Ease.OUT_CUBIC, false),
                        k(61, -2.30, 1.00, -1.90, 0, 0.90, 1.00, 63, 1, L, false),
                        k(62, -6.50, 1.15, 1.20, 0, 1.00, 2.60, 50, 0, L, true),
                        k(66, -6.50, 1.15, 1.60, 0, 1.00, 3.00, 52, 0, L, false),
                        k(74, -6.80, 1.10, 4.50, 0, 1.00, 7.00, 56, 0, Ease.IN_CUBIC, false),
                        k(80, -6.80, 1.10, 6.00, 0, 1.00, 30.0, 60, 0, Ease.IN_EXPO, false),
                        k(86, -6.80, 1.10, 6.20, 0, 1.00, 30.0, 70, 0, Ease.IN_QUAD, false)}),
                // OCEAN: atmosfer kenari, egik ufuk
                new Track(S, new Key[]{
                        k(86, 0, 60, 0, 0, 35, 800, 70, -16, L, true),
                        k(100, 0, 58, 25, 8, 30, 800, 72, -17, L, false),
                        k(112, 0, 62, 40, 10, 30, 800, 74, -18, L, false),
                        k(120, 0, 380, 10, 0, 100, 800, 95, -10, Ease.IN_EXPO, false)}),
                // ORBIT: Dunya sag yarida, yavas yaklasma
                new Track(S, new Key[]{
                        k(120, 30, 10, -160, 55, 0, 0, 45, 0, L, true),
                        k(132, 20, 12, -145, 42, 0, 0, 44, 2, Ease.IN_OUT_SINE, false),
                        k(140, 8, 14, -128, 30, 0, 0, 42, 3, Ease.IN_OUT_SINE, false)}),
                // TUNNEL: derinlik -> cömelen Flash -> yuz yakin plan
                new Track(S, new Key[]{
                        k(140, 0, 1.20, 0, 0, 1.10, -50, 70, 0, L, true),
                        k(148, 0, 2.00, 0.80, 0, 0.90, -2.40, 60, 0, Ease.OUT_CUBIC, false),
                        k(152, 0.30, 1.25, -0.40, -0.10, 1.45, -2.40, 52, -4, Ease.IN_OUT_CUBIC, false),
                        k(158, 0.25, 1.30, -0.70, -0.10, 1.50, -2.40, 46, -6, Ease.IN_QUAD, false)}),
                // IMPACT: 3/4 alcak yan aci, temasa bakis
                new Track(A, new Key[]{
                        kd(158, -3.10, 0.90, d - 1.60, LOOK_CONTACT, Vec3.ZERO, 66, 4, L, true),
                        kd(162, -3.15, 0.92, d - 1.65, LOOK_CONTACT, Vec3.ZERO, 64, 4, L, false),
                        kd(166, -3.30, 0.95, d - 1.80, LOOK_CONTACT, new Vec3(0, 0, 0.6), 70, 3, Ease.OUT_CUBIC, false)}),
                // LAUNCH: omuz ustu, yayli takip -> genis cekim
                new Track(A, new Key[]{
                        kd(166, 0.90, 1.70, d - 2.60, LOOK_TARGET, Vec3.ZERO, 62, 0, L, true),
                        kd(176, 0.90, 1.90, d - 2.80, LOOK_TARGET, Vec3.ZERO, 66, 0, L, false),
                        kd(182, -1.20, 2.60, d - 5.50, LOOK_MID, Vec3.ZERO, 76, 0, Ease.IN_OUT_CUBIC, false),
                        kd(188, -1.20, 2.60, d - 5.50, LOOK_MID, Vec3.ZERO, 76, 0, L, false)}),
        };
    }

    /** t aninda tablodan kamera durumu (RECOVER/ACTIVATE harmanlamasi director'da). */
    public static State evaluate(Track[] tracks, float t) {
        Track tr = tracks[0];
        for (Track x : tracks) if (t >= x.keys()[0].t()) tr = x;
        Key[] ks = tr.keys();
        int i = 0;
        while (i + 1 < ks.length && t >= ks[i + 1].t()) i++;
        Key k1 = ks[i];
        if (i + 1 >= ks.length) return new State(k1.pos(), k1.look(), k1.lookMode(), k1.fov(), k1.roll(), tr.space());
        Key k2 = ks[i + 1];
        if (k2.cut()) return new State(k1.pos(), k1.look(), k1.lookMode(), k1.fov(), k1.roll(), tr.space());
        float u = (t - k1.t()) / Math.max(1.0E-4F, k2.t() - k1.t());
        float e = k2.ease().apply(u);
        Key k0 = i > 0 && !k1.cut() ? ks[i - 1] : k1;
        Key k3 = i + 2 < ks.length && !ks[i + 2].cut() ? ks[i + 2] : k2;
        Vec3 pos = catmull(k0.pos(), k1.pos(), k2.pos(), k3.pos(), e);
        Vec3 look = k1.look().lerp(k2.look(), e);
        int mode = e < 0.5F ? k1.lookMode() : k2.lookMode();
        return new State(pos, look, k1.lookMode() == k2.lookMode() ? k1.lookMode() : mode,
                Mth.lerp(e, k1.fov(), k2.fov()), Mth.lerp(e, k1.roll(), k2.roll()), tr.space());
    }

    /** Merkezcil degil, klasik (uniform) Catmull-Rom; u ∈ [0,1] p1 -> p2. */
    public static Vec3 catmull(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float u) {
        double u2 = u * u, u3 = u2 * u;
        return new Vec3(
                0.5 * (2 * p1.x + (-p0.x + p2.x) * u + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * u2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * u3),
                0.5 * (2 * p1.y + (-p0.y + p2.y) * u + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * u2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * u3),
                0.5 * (2 * p1.z + (-p0.z + p2.z) * u + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * u2 + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * u3));
    }

    public static float yawTo(Vec3 d) {
        return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
    }

    public static float pitchTo(Vec3 d) {
        return (float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
    }

    /** Kamera sarsintisi gurultusu (uc sinus). */
    public static float noise(float x) {
        return Mth.sin(x) * 0.5F + Mth.sin(x * 2.3F + 1.7F) * 0.3F + Mth.sin(x * 4.7F + 0.4F) * 0.2F;
    }
}
