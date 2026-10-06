package dev.baranhan.flashmod.client.ultimate;

import dev.baranhan.flashmod.ultimate.UltimatePhase;
import dev.baranhan.flashmod.ultimate.UltimateScript;
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

    /**
     * follow: SCENE'de konum ve bakis kosucuya goreli (director kosucunun (t - lag) anindaki konumunu ekler).
     * lag zamanla dogrusal buyurse kamera yerinde kalir, kosucu kadrajdan uzaklasir.
     */
    public record Key(float t, Vec3 pos, Vec3 look, int lookMode, float fov, float roll, Ease ease, boolean cut,
                      boolean follow, float lag) {}

    public record Track(UltimatePhase.Space space, Key[] keys) {}

    public record State(Vec3 pos, Vec3 look, int lookMode, float fov, float roll, UltimatePhase.Space space,
                        boolean follow, float lag) {}

    private UltCamera() {}

    private static Key k(float t, double x, double y, double z, double lx, double ly, double lz, float fov, float roll,
                         Ease e, boolean cut) {
        return new Key(t, new Vec3(x, y, z), new Vec3(lx, ly, lz), LOOK_FIXED, fov, roll, e, cut, false, 0F);
    }

    /** Kosucuyu takip eden sahne anahtari. */
    private static Key kf(float t, double x, double y, double z, double lx, double ly, double lz, float fov, float roll,
                          Ease e, boolean cut, float lag) {
        return new Key(t, new Vec3(x, y, z), new Vec3(lx, ly, lz), LOOK_FIXED, fov, roll, e, cut, true, lag);
    }

    private static Key kd(float t, double x, double y, double z, int mode, Vec3 offset, float fov, float roll, Ease e,
                          boolean cut) {
        return new Key(t, new Vec3(x, y, z), offset, mode, fov, roll, e, cut, false, 0F);
    }

    /** Oturuma ozel tablolar (D, ilk vurus itmesi ve hedef boyutuna gore). */
    public static Track[] build(float d, float push, float targetWidth) {
        double wx = Math.max(0D, (targetWidth - 0.6D) * 0.5D);
        double di = d + push; // itilmis hedef
        double ha = UltimateScript.RISE + 0.3, za = di + 0.8, zl = di + 0.3; // havadaki hedef, cakildigi yer
        UltimatePhase.Space A = UltimatePhase.Space.ARENA, S = UltimatePhase.Space.SCENE;
        Ease L = Ease.LINEAR;
        return new Track[]{
                // WINDUP: on-sag, alcak orta plan; yavasca yaklasir (yuz kadraja oturur, kafanin icine girmez)
                new Track(A, new Key[]{
                        k(6, 1.95 + wx, 0.85, 2.05, 0, 1.20, 0, 60, 0, Ease.IN_OUT_CUBIC, false),
                        k(18, 1.60 + wx, 0.95, 1.80, 0, 1.30, 0, 56, -3, Ease.OUT_QUAD, false),
                        k(26, 1.30 + wx, 1.10, 1.55, 0, 1.42, 0, 52, -5, Ease.IN_OUT_SINE, false),
                        k(30, 1.20 + wx, 1.15, 1.45, 0, 1.46, 0, 50, -6, L, false)}),
                // DEPART: soldan genis yan cekim (caster hedefin bu tarafindan gecer, arkasinda kalmaz); kosu, vurus,
                // hedefin ileri kaymasi, caster'in uzaklasmasi
                new Track(A, new Key[]{
                        k(30, -3.90 - wx, 1.05, d * 0.45, -0.3, 1.00, d * 0.55, 62, 2, L, true),
                        k(33, -3.80 - wx, 1.08, d * 0.48 + 0.1, -0.3, 1.05, d, 61, 2, Ease.OUT_QUAD, false),
                        k(43, -3.50 - wx, 1.15, d * 0.55 + 0.6, -0.4, 1.05, di + 1.5, 62, 0, Ease.IN_OUT_CUBIC, false),
                        k(52, -3.40 - wx, 1.15, d * 0.60 + 0.8, -0.6, 1.00, d + 12.0, 68, 0, L, false)}),
                // VOID: kosucuyu takip; omuz arkasi -> yan -> on 3/4 (kameraya kosar) -> genis yan -> kosucu uzaklasir
                new Track(S, new Key[]{
                        kf(52, -2.40, 1.25, -1.80, 0.2, 1.00, 1.60, 60, 2, L, true, 0),
                        kf(64, -2.90, 1.05, 0.30, 0.0, 0.95, 1.00, 58, 3, Ease.IN_OUT_SINE, false, 0),
                        kf(74, -3.10, 0.95, 0.60, 0.0, 0.95, 0.80, 57, 2, Ease.IN_OUT_SINE, false, 0),
                        kf(76, -2.60, 0.65, 4.60, 0.0, 1.00, 0.00, 56, -2, L, true, 0),
                        kf(90, -2.20, 0.75, 3.40, 0.0, 1.00, 0.20, 60, -3, Ease.IN_OUT_SINE, false, 0),
                        kf(96, -7.20, 1.60, 1.20, 0.0, 1.00, 2.50, 62, 0, L, true, 0),
                        kf(106, -7.20, 1.60, 2.80, 0.0, 1.00, 5.00, 66, 0, Ease.IN_OUT_SINE, false, 0),
                        kf(108, -2.20, 1.25, -3.20, 0.0, 1.00, 10.0, 60, 0, L, true, 0),
                        kf(122, -2.20, 1.25, -3.20, 0.0, 1.00, 60.0, 72, 0, L, false, 14)}),
                // OCEAN: alcak, kosucunun arkasindan takip. Egim (roll) ve pitch director'da kompozisyondan
                // hesaplanir: ufuk sol alttan sag uste, su alani ~%40.
                new Track(S, new Key[]{
                        // kosucu kadrajin sag-alt kisminda, suyun icinde: ~5 blok geride, 2.6 yukarida, bakis ekseni
                        // kosucunun ~14 derece solunda (pitch/roll kompozisyondan)
                        kf(122, -1.60, 2.60, -5.00, 6.3, 1.00, 7.8, 70, 0, L, true, 0),
                        kf(148, -1.90, 2.70, -5.30, 6.1, 1.00, 8.1, 70, 0, Ease.IN_OUT_SINE, false, 0),
                        kf(164, -2.20, 2.85, -5.70, 5.9, 1.00, 8.5, 70, 0, Ease.IN_OUT_SINE, false, 0),
                        kf(182, -2.20, 2.85, -5.70, 5.9, 1.00, 8.5, 70, 0, L, false, 18)}),
                // ORBIT: Dunya etrafinda yavas kamera yorungesi, sonda isigin dalisina yaklasma
                new Track(S, new Key[]{
                        k(182, 70, 30, -195, 30, 0, 0, 45, 0, L, true),
                        k(212, 10, 40, -205, 15, 0, 0, 45, 2, Ease.IN_OUT_SINE, false),
                        k(242, -60, 34, -185, 0, 0, 0, 44, 3, Ease.IN_OUT_SINE, false),
                        k(262, -45, 22, -125, -6, 4, -40, 40, 3, Ease.IN_CUBIC, false)}),
                // TUNNEL: derinlik -> comelen Flash -> yuz yakin plan
                new Track(S, new Key[]{
                        k(262, 0, 1.20, 0, 0, 1.10, -50, 70, 0, L, true),
                        k(274, 0, 2.00, 0.80, 0, 0.90, -2.40, 60, 0, Ease.OUT_CUBIC, false),
                        k(281, 0.30, 1.25, -0.40, -0.10, 1.45, -2.40, 52, -4, Ease.IN_OUT_CUBIC, false),
                        k(290, 0.25, 1.30, -0.70, -0.10, 1.50, -2.40, 46, -6, Ease.IN_QUAD, false)}),
                // IMPACT: 3/4 alcak yan aci, temasa bakis
                new Track(A, new Key[]{
                        kd(290, -3.10, 0.90, di - 1.60, LOOK_CONTACT, Vec3.ZERO, 66, 4, L, true),
                        kd(294, -3.15, 0.92, di - 1.65, LOOK_CONTACT, Vec3.ZERO, 64, 4, L, false),
                        kd(298, -3.30, 0.95, di - 1.80, LOOK_CONTACT, new Vec3(0, 0, 0.6), 70, 3, Ease.OUT_CUBIC, false)}),
                // LAUNCH: genis yan cekim; yerde izleyen Flash ile havaya kalkan hedefin ortasina yayli bakis
                new Track(A, new Key[]{
                        kd(298, -6.80 - wx, 1.60, di - 1.20, LOOK_MID, Vec3.ZERO, 66, 2, L, true),
                        kd(306, -8.20 - wx, 2.40, di - 1.60, LOOK_MID, Vec3.ZERO, 70, 3, Ease.IN_OUT_SINE, false),
                        kd(310, -8.40 - wx, 2.60, di - 1.70, LOOK_MID, Vec3.ZERO, 70, 3, L, false)}),
                // AIR: Flash'in arkasindan, hemen hemen onun hizasindan (Flash kadrajin ortasinda, hedef asagida-onde);
                // yumruktan sonra cakilan hedefe asagi bakis
                new Track(A, new Key[]{
                        k(310, 1.30, ha + 3.20, za + 7.20, 0, ha + 1.80, za + 0.40, 62, -4, L, true),
                        k(318, 0.90, ha + 3.00, za + 5.90, 0, ha + 1.60, za + 0.50, 58, -3, Ease.OUT_CUBIC, false),
                        k(321, 0.85, ha + 2.95, za + 5.80, 0, ha + 1.55, za + 0.50, 57, -3, L, false),
                        k(327, 1.10, ha + 2.20, za + 6.20, 0, 0.80, za - 0.30, 66, -1, Ease.IN_QUAD, false)}),
                // SLAM: yerden genis cekim; simsek patlamasi ve Flash'in inisi
                new Track(A, new Key[]{
                        k(327, -5.20 - wx, 1.30, zl + 0.50, 0, 0.90, zl, 74, 3, L, true),
                        k(333, -4.80 - wx, 1.60, zl + 1.80, 0, 1.00, zl + 0.90, 68, 2, Ease.OUT_CUBIC, false),
                        k(340, -4.60 - wx, 1.80, zl + 2.20, 0, 1.10, zl + 1.00, 64, 0, Ease.IN_OUT_SINE, false)}),
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
        if (i + 1 >= ks.length || ks[i + 1].cut()) {
            return new State(k1.pos(), k1.look(), k1.lookMode(), k1.fov(), k1.roll(), tr.space(), k1.follow(), k1.lag());
        }
        Key k2 = ks[i + 1];
        float u = (t - k1.t()) / Math.max(1.0E-4F, k2.t() - k1.t());
        float e = k2.ease().apply(u);
        Key k0 = i > 0 && !k1.cut() ? ks[i - 1] : k1;
        Key k3 = i + 2 < ks.length && !ks[i + 2].cut() ? ks[i + 2] : k2;
        Vec3 pos = catmull(k0.pos(), k1.pos(), k2.pos(), k3.pos(), e);
        Vec3 look = k1.look().lerp(k2.look(), e);
        int mode = e < 0.5F ? k1.lookMode() : k2.lookMode();
        // gecikme her zaman dogrusal (kamera sabit kalsin diye easing uygulanmaz)
        float lag = Mth.lerp(u, k1.lag(), k2.lag());
        return new State(pos, look, k1.lookMode() == k2.lookMode() ? k1.lookMode() : mode,
                Mth.lerp(e, k1.fov(), k2.fov()), Mth.lerp(e, k1.roll(), k2.roll()), tr.space(), k1.follow() || k2.follow(), lag);
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
