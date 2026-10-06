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

    /** Oturuma ozel tablolar (D, final betigi ve hedef boyutuna gore). */
    public static Track[] build(float d, UltimateScript.Path fp, float targetWidth) {
        double wx = Math.max(0D, (targetWidth - 0.6D) * 0.5D);
        float push = fp.push;
        double di = d + push;                       // itilmis hedef
        double hz = d + fp.hitA, hy = fp.hitY;      // Flash'in havadaki yumruk konumu (ayak)
        double cz = d + fp.cockA, cy = fp.cockY;    // kurulu bekledigi konum
        double tc = fp.py + fp.h * 0.5;              // asili hedefin merkez yuksekligi
        double gz = d + fp.ga, lz = d + fp.landA;   // cakilma ve inis noktalari
        double mz = (gz + lz) * 0.5;
        UltimatePhase.Space A = UltimatePhase.Space.ARENA, S = UltimatePhase.Space.SCENE;
        Ease L = Ease.LINEAR;
        return new Track[]{
                // WINDUP: on-sag, alcak orta plan; giristen itibaren hic durmadan yuze dogru yavas yaklasir
                new Track(A, new Key[]{
                        k(0, 2.15 + wx, 0.80, 2.25, 0, 1.15, 0, 62, 0, L, false),
                        k(14, 1.72 + wx, 0.95, 1.86, 0, 1.30, 0, 56, -3, L, false),
                        k(30, 1.22 + wx, 1.13, 1.48, 0, 1.46, 0, 50, -6, L, false)}),
                // DEPART: soldan genis yan cekim (caster hedefin bu tarafindan gecer); kosu, vurus, hedefin ileri
                // kaymasi, caster'in uzaklasmasi. Kamera ve bakis surekli kayar (anahtarlarda durmaz).
                new Track(A, new Key[]{
                        k(30, -3.90 - wx, 1.05, d * 0.45, -0.3, 1.00, d * 0.55, 62, 2, L, true),
                        k(34, -3.80 - wx, 1.08, d * 0.48 + 0.15, -0.3, 1.05, d + 0.10, 61, 2, L, false),
                        k(44, -3.50 - wx, 1.15, d * 0.55 + 0.60, -0.4, 1.05, di + 1.20, 62, 0, L, false),
                        k(52, -3.40 - wx, 1.15, d * 0.60 + 0.80, -0.6, 1.00, di + 5.00, 66, 0, L, false)}),
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
                // OCEAN: kosucunun ~10 blok arkasindan, V su perdelerinin icinden sabit mesafede takip. DASH_T'de kamera
                // yumusakca durur (gecikme 0 -> 1 egimli artar), kosucu bir anda hizlanip ufka firlar. Egim (roll) ve
                // pitch director'da kompozisyondan: ufuk sol alttan sag uste, su alani ~%40.
                new Track(S, new Key[]{
                        kf(122, -1.10, 2.70, -10.0, 5.6, 1.00, 9.0, 70, 0, L, true, 0),
                        kf(150, -1.20, 2.75, -10.3, 5.5, 1.00, 9.2, 70, 0, L, false, 0),
                        kf(UltOceanScene.DASH_T, -1.25, 2.78, -10.4, 5.45, 1.00, 9.3, 70, 0, L, false, 0),
                        kf(UltOceanScene.DASH_T + 4, -1.25, 2.78, -10.4, 5.45, 1.00, 9.3, 71, 0, L, false, 1.5F),
                        kf(182, -1.25, 2.78, -10.4, 5.45, 1.00, 9.3, 72, 0, L, false, 182 - UltOceanScene.DASH_T - 2.5F)}),
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
                // IMPACT: 3/4 alcak yan aci, temasa bakis; hit-stop boyunca neredeyse sabit
                new Track(A, new Key[]{
                        kd(290, -3.10, 0.90, di - 1.60, LOOK_CONTACT, Vec3.ZERO, 66, 4, L, true),
                        kd(294, -3.18, 0.92, di - 1.66, LOOK_CONTACT, new Vec3(0, 0.1, 0), 64, 4, L, false)}),
                // LAUNCH: Flash'in arkasindan-solundan alcak aci: aparkatla yukari-ileri ucan hedef ve bakakalan Flash
                new Track(A, new Key[]{
                        kd(294, -3.40 - wx, 0.55, di - 3.20, LOOK_MID, new Vec3(0, 0.2, 0), 70, -3, L, true),
                        kd(302, -3.80 - wx, 0.50, di - 3.70, LOOK_MID, new Vec3(0, 0.5, 0.3), 72, -4, L, false)}),
                // HANG: yavas cekim: hedefin yaninda, onunla ayni yukseklikte; asili kalip yavasca donen hedefe yaklasma
                new Track(A, new Key[]{
                        kd(302, -3.70, tc + 0.45, d + fp.pa - 1.30, LOOK_TARGET, Vec3.ZERO, 54, 2, L, true),
                        kd(310, -3.00, tc + 0.30, d + fp.pa - 0.85, LOOK_TARGET, Vec3.ZERO, 48, 3, L, false)}),
                // AIR: Flash'in ARKASINDAN, sag omzu hizasindan (yumruk dogrusuna yandan-arkadan, hedef Flash'in
                // govdesinin arkasinda kalmaz): kurulma, yumruk, cakilan hedefi takip, patlamayi yukaridan gorur,
                // Flash patlamaya dogru duser
                new Track(A, new Key[]{
                        kd(310, -2.25, cy + 0.95, cz + 2.55, LOOK_TARGET, new Vec3(0, 0.35, 0.45), 64, -3, L, true),
                        kd(316, -2.15, cy + 0.90, cz + 2.40, LOOK_TARGET, new Vec3(0, 0.35, 0.40), 62, -3, L, false),
                        kd(318, -2.05, hy + 0.90, hz + 2.30, LOOK_TARGET, new Vec3(0, 0.30, 0.35), 60, -2, L, false),
                        kd(321, -2.02, hy + 0.88, hz + 2.27, LOOK_TARGET, new Vec3(0, 0.30, 0.30), 59, -2, L, false),
                        kd(326, -1.75, hy + 1.40, hz + 1.85, LOOK_TARGET, new Vec3(0, 0.0, 0.0), 64, -1, L, false),
                        kd(331, -1.55, hy + 1.60, hz + 1.55, LOOK_TARGET, new Vec3(0, 0.3, 0.6), 68, 0, L, false)}),
                // LAND: yerden genis yan cekim: patlama, krater ve Flash'in inisi; sonra yavas yaklasma
                new Track(A, new Key[]{
                        k(331, -4.90 - wx, 1.00, mz + 0.30, 0, 1.10, mz, 72, 3, L, true),
                        k(340, -4.30 - wx, 1.15, mz + 0.60, 0, 0.95, mz + 0.30, 66, 1, L, false),
                        k(354, -3.70 - wx, 1.30, mz + 0.90, 0, 1.05, mz + 0.50, 60, 0, L, false)}),
        };
    }

    /**
     * t aninda tablodan kamera durumu (RECOVER/ACTIVATE harmanlamasi director'da). Ayni kesme grubundaki anahtarlar
     * arasinda hiz SUREKLI (zamana gore kubik Hermite, egimler komsu anahtarlardan): kamera hicbir anahtarda durup
     * yeniden kalkmaz. Easing yalnizca grup uclarinda ipucudur: ilk segmentte IN_* durgun baslar, son anahtarda OUT_*
     * durgun biter. Gecikme (lag) her zaman dogrusal.
     */
    public static State evaluate(Track[] tracks, float t) {
        Track tr = tracks[0];
        for (Track x : tracks) if (t >= x.keys()[0].t()) tr = x;
        Key[] ks = tr.keys();
        int i = 0;
        while (i + 1 < ks.length && t >= ks[i + 1].t()) i++;
        Key k1 = ks[i];
        if (i + 1 >= ks.length || ks[i + 1].cut() || t <= k1.t()) {
            return new State(k1.pos(), k1.look(), k1.lookMode(), k1.fov(), k1.roll(), tr.space(), k1.follow(), k1.lag());
        }
        Key k2 = ks[i + 1];
        float dt = Math.max(1.0E-4F, k2.t() - k1.t());
        float u = Mth.clamp((t - k1.t()) / dt, 0F, 1F);
        Vec3 pos = hermite(k1.pos(), slope(ks, i, 0), k2.pos(), slope(ks, i + 1, 0), u, dt);
        Vec3 look = hermite(k1.look(), slope(ks, i, 1), k2.look(), slope(ks, i + 1, 1), u, dt);
        Vec3 fr = hermite(new Vec3(k1.fov(), k1.roll(), 0), slope(ks, i, 2), new Vec3(k2.fov(), k2.roll(), 0),
                slope(ks, i + 1, 2), u, dt);
        int mode = u < 0.5F ? k1.lookMode() : k2.lookMode();
        float lag = Mth.lerp(u, k1.lag(), k2.lag());
        return new State(pos, look, k1.lookMode() == k2.lookMode() ? k1.lookMode() : mode, (float) fr.x, (float) fr.y,
                tr.space(), k1.follow() || k2.follow(), lag);
    }

    private static Vec3 channel(Key k, int ch) {
        return ch == 0 ? k.pos() : ch == 1 ? k.look() : new Vec3(k.fov(), k.roll(), 0);
    }

    private static boolean easeIn(Ease e) {
        return e == Ease.IN_QUAD || e == Ease.IN_CUBIC || e == Ease.IN_EXPO;
    }

    private static boolean easeOut(Ease e) {
        return e == Ease.OUT_QUAD || e == Ease.OUT_CUBIC || e == Ease.OUT_EXPO || e == Ease.OUT_BACK;
    }

    /** j anahtarindaki egim (birim/tick). */
    private static Vec3 slope(Key[] ks, int j, int ch) {
        boolean first = j == 0 || ks[j].cut(), last = j + 1 >= ks.length || ks[j + 1].cut();
        if (first && last) return Vec3.ZERO;
        if (first) {
            if (easeIn(ks[j + 1].ease())) return Vec3.ZERO;
            return diff(ks[j], ks[j + 1], ch);
        }
        if (last) {
            if (easeOut(ks[j].ease())) return Vec3.ZERO;
            return diff(ks[j - 1], ks[j], ch);
        }
        return diff(ks[j - 1], ks[j + 1], ch);
    }

    private static Vec3 diff(Key a, Key b, int ch) {
        return channel(b, ch).subtract(channel(a, ch)).scale(1.0 / Math.max(1.0E-4F, b.t() - a.t()));
    }

    /** Kubik Hermite: p0 -> p1, egimler birim/tick, dt segment suresi. */
    public static Vec3 hermite(Vec3 p0, Vec3 m0, Vec3 p1, Vec3 m1, float u, float dt) {
        double u2 = u * u, u3 = u2 * u;
        double h00 = 2 * u3 - 3 * u2 + 1, h10 = (u3 - 2 * u2 + u) * dt, h01 = -2 * u3 + 3 * u2, h11 = (u3 - u2) * dt;
        return new Vec3(p0.x * h00 + m0.x * h10 + p1.x * h01 + m1.x * h11,
                p0.y * h00 + m0.y * h10 + p1.y * h01 + m1.y * h11,
                p0.z * h00 + m0.z * h10 + p1.z * h01 + m1.z * h11);
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
