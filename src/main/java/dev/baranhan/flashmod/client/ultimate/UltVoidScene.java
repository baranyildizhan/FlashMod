package dev.baranhan.flashmod.client.ultimate;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.baranhan.flashmod.client.render.Lightning;
import dev.baranhan.flashmod.ultimate.UltimatePhase;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * VOID: gri-beyaz, sisli Speed Force boslugu. Kosucu sahnenin ilk karesinden itibaren kosar (bekleme/comelme yok),
 * giderek hizlanir, en sonda isik cizgisine donusup ufka firlar. Kamera onu takip eder (UltCamera follow);
 * zemin dokusu, sis bulutlari ve hiz seritleri dunyaya sabittir, hareket hissi gercek konum degisiminden gelir.
 * Arkasinda bizim iz sistemimizle cizilen simsek izi.
 */
public final class UltVoidScene implements UltScene.Scene {
    private static final Lightning.Rng R = new Lightning.Rng(404);
    private static final float T0 = UltimatePhase.VOID.start, T1 = UltimatePhase.VOID.end;
    /** Hiz dugumleri {t, blok/tick}; aralarda dogrusal -> konum analitik (parca parca ikinci derece). */
    private static final float[][] SPEED = {{T0, 0.9F}, {70, 1.3F}, {96, 4.0F}, {108, 14F}, {116, 40F}, {T1, 70F}};
    /** Kosucunun isiga donustugu an. */
    private static final float LIGHT_T = 110F;

    /** Kosucunun z konumu (T0'da 0); T0 oncesi sabit hizla geriye uzar (iz sahne basinda hazir olsun). */
    static double runnerZ(float t) {
        float[][] K = SPEED;
        if (t <= K[0][0]) return K[0][1] * (t - K[0][0]);
        double z = 0;
        for (int i = 0; i + 1 < K.length; i++) {
            float t0 = K[i][0], t1 = K[i + 1][0];
            if (t <= t0) break;
            float te = Math.min(t, t1), u = (te - t0) / (t1 - t0);
            float vEnd = K[i][1] + (K[i + 1][1] - K[i][1]) * u;
            z += (K[i][1] + vEnd) * 0.5 * (te - t0);
        }
        float[] last = K[K.length - 1];
        if (t > last[0]) z += last[1] * (t - last[0]);
        return z;
    }

    static float speed(float t) {
        float[][] K = SPEED;
        if (t <= K[0][0]) return K[0][1];
        for (int i = 0; i + 1 < K.length; i++) {
            if (t < K[i + 1][0]) return Mth.lerp((t - K[i][0]) / (K[i + 1][0] - K[i][0]), K[i][1], K[i + 1][1]);
        }
        return K[K.length - 1][1];
    }

    static final UltScene.TrailPath PATH = new UltScene.TrailPath() {
        @Override
        public Vec3 at(float t) {
            return new Vec3(0, 0, runnerZ(t));
        }

        @Override
        public double odo(float t) {
            return runnerZ(t);
        }
    };

    @Override
    public void backdrop(UltScene.Ctx c) {
        int top = 0x3A3E48, mid = 0xD8D5D6, low = 0x9A9496;
        if (c.t >= 86F) mid = UltDraw.mix(mid, c.glow, 0.18F * Math.min(1F, (c.t - 86F) / 16F));
        BufferBuilder b = UltDraw.begin(null, UltDraw.Blend.OPAQUE, false, false);
        int seg = 24, rings = 12;
        float rad = 50F;
        for (int i = 0; i < rings; i++) {
            float a0 = -90F + 180F * i / rings, a1 = -90F + 180F * (i + 1) / rings;
            float y0 = Mth.sin(a0 * 0.017453292F), y1 = Mth.sin(a1 * 0.017453292F);
            float r0 = Mth.cos(a0 * 0.017453292F), r1 = Mth.cos(a1 * 0.017453292F);
            int c0 = sky(y0, top, mid, low), c1 = sky(y1, top, mid, low);
            for (int j = 0; j < seg; j++) {
                float b0 = 6.2832F * j / seg, b1 = 6.2832F * (j + 1) / seg;
                quadC(b, c.view, rad, r0, y0, r1, y1, b0, b1, c0, c1);
            }
        }
        UltDraw.end();
    }

    static void quadC(BufferBuilder b, Matrix4f m, float rad, float r0, float y0, float r1, float y1, float b0, float b1,
                      int c0, int c1) {
        UltDraw.c(b, m, Mth.cos(b0) * r0 * rad, y0 * rad, Mth.sin(b0) * r0 * rad, UltDraw.r(c0), UltDraw.g(c0), UltDraw.b(c0), 1F);
        UltDraw.c(b, m, Mth.cos(b1) * r0 * rad, y0 * rad, Mth.sin(b1) * r0 * rad, UltDraw.r(c0), UltDraw.g(c0), UltDraw.b(c0), 1F);
        UltDraw.c(b, m, Mth.cos(b1) * r1 * rad, y1 * rad, Mth.sin(b1) * r1 * rad, UltDraw.r(c1), UltDraw.g(c1), UltDraw.b(c1), 1F);
        UltDraw.c(b, m, Mth.cos(b0) * r1 * rad, y1 * rad, Mth.sin(b0) * r1 * rad, UltDraw.r(c1), UltDraw.g(c1), UltDraw.b(c1), 1F);
    }

    private static int sky(float y, int top, int mid, int low) {
        if (y >= 0F) return UltDraw.mix(mid, top, Mth.clamp(y / 0.7F, 0F, 1F));
        return UltDraw.mix(mid, low, Mth.clamp(-y / 0.4F, 0F, 1F));
    }

    /** Kameraya goreli sarmalanan dunya-sabit konum: period'luk tekrar eden dizilim, kameranin etrafinda. */
    private static double wrap(double base, double cam, double period) {
        return cam - period * 0.5 + Math.floorMod((long) ((base - cam + period * 0.5) * 100), (long) (period * 100)) / 100.0;
    }

    @Override
    public void geometry(UltScene.Ctx c) {
        Matrix4f m = c.m;
        float t = c.vt;
        double cx = c.cam.x, cz = c.cam.z;
        float sp = speed(t);
        // zemin: radyal solan disk (kameranin altinda)
        BufferBuilder b = UltDraw.begin(null, UltDraw.Blend.ALPHA, true, true, VertexFormat.Mode.TRIANGLES);
        int seg = 48;
        int gc = 0xA49EA0;
        float gr = UltDraw.r(gc), gg = UltDraw.g(gc), gb = UltDraw.b(gc);
        for (int ring = 0; ring < 6; ring++) {
            float r0 = ring * 18F, r1 = (ring + 1) * 18F;
            float a0 = 1F - ring / 6F, a1 = 1F - (ring + 1) / 6F;
            for (int j = 0; j < seg; j++) {
                float b0 = 6.2832F * j / seg, b1 = 6.2832F * (j + 1) / seg;
                UltDraw.c(b, m, cx + Mth.cos(b0) * r0, 0, cz + Mth.sin(b0) * r0, gr, gg, gb, a0);
                UltDraw.c(b, m, cx + Mth.cos(b1) * r0, 0, cz + Mth.sin(b1) * r0, gr, gg, gb, a0);
                UltDraw.c(b, m, cx + Mth.cos(b1) * r1, 0, cz + Mth.sin(b1) * r1, gr, gg, gb, a1);
                UltDraw.c(b, m, cx + Mth.cos(b0) * r0, 0, cz + Mth.sin(b0) * r0, gr, gg, gb, a0);
                UltDraw.c(b, m, cx + Mth.cos(b1) * r1, 0, cz + Mth.sin(b1) * r1, gr, gg, gb, a1);
                UltDraw.c(b, m, cx + Mth.cos(b0) * r1, 0, cz + Mth.sin(b0) * r1, gr, gg, gb, a1);
            }
        }
        UltDraw.end();
        // zemin dokusu: dunyaya sabit (UV = dunya konumu), hizla gercekten akar
        b = UltDraw.begin(UltTextures.CLOUD, UltDraw.Blend.ALPHA, true, false);
        double tile = 16.0;
        double gx0 = Math.floor(cx / tile) * tile, gz0 = Math.floor(cz / tile) * tile;
        for (int i = -4; i <= 4; i++) for (int j = -4; j <= 5; j++) {
            double x0 = gx0 + i * tile, z0 = gz0 + j * tile;
            float a = (float) Math.max(0.0, 1.0 - Math.hypot(x0 + tile * 0.5 - cx, z0 + tile * 0.5 - cz) / 70.0) * 0.22F;
            if (a <= 0.01F) continue;
            UltDraw.v(b, m, x0, 0.02, z0, 0, 0, 1, 1, 1, a);
            UltDraw.v(b, m, x0 + tile, 0.02, z0, 1, 0, 1, 1, 1, a);
            UltDraw.v(b, m, x0 + tile, 0.02, z0 + tile, 1, 1, 1, 1, 1, a);
            UltDraw.v(b, m, x0, 0.02, z0 + tile, 0, 1, 1, 1, 1, a);
        }
        UltDraw.end();

        // sis bulutlari (dunyaya sabit, kameranin etrafinda sarmalanir)
        b = UltDraw.begin(UltTextures.CLOUD, UltDraw.Blend.ALPHA, true, false);
        R.seed(c.s.seed ^ 0xC10DL);
        int clouds = 14 + (int) (8 * c.quality);
        for (int i = 0; i < clouds; i++) {
            float x = R.signed() * 40F, y = 1F + R.next() * 12F, zb = R.next() * 160F;
            float w = 15F + R.next() * 25F, h = 6F + R.next() * 8F, a = 0.2F + R.next() * 0.3F;
            double z = wrap(zb, cz, 160.0);
            float edge = (float) Mth.clamp(1.0 - Math.abs(z - cz) / 80.0, 0.0, 1.0);
            UltDraw.billboard(b, m, cx + x, y, z, c.right, c.up, w, h, 0F, 0xE4E0E2, a * Math.min(1F, edge * 3F));
        }
        UltDraw.end();

        // yatay hiz seritleri: dunyaya sabit cizgiler; uzunluklari hizla buyur (hareket bulanikligi)
        float k = Mth.clamp((sp - 1.0F) / 3F, 0F, 1F);
        if (k > 0.01F) {
            int n = (int) (220 * c.quality * k);
            boolean warm = t >= 86F;
            int col = warm ? UltDraw.mix(0xFFE2B0, c.glow, 0.35F) : 0xF2F2F2;
            b = UltDraw.begin(null, warm ? UltDraw.Blend.ADD : UltDraw.Blend.ALPHA, true, false);
            R.seed(c.s.seed ^ 0x57A7L);
            float len0 = Math.min(60F, 2F + sp * 2.5F);
            for (int i = 0; i < n; i++) {
                float y = 0.08F + R.next() * 3.0F, x = (R.next() < 0.5F ? -1 : 1) * (0.8F + R.next() * 14F);
                float hh = 0.015F + R.next() * 0.06F, len = len0 * (0.5F + R.next());
                float a = (warm ? 0.25F + 0.45F * R.next() : 0.12F + 0.1F * R.next()) * k;
                double z = wrap(R.next() * 240F, cz + 40, 240.0);
                float cr = UltDraw.r(col), cg = UltDraw.g(col), cb = UltDraw.b(col);
                UltDraw.c(b, m, cx + x, y, z, cr, cg, cb, 0F);
                UltDraw.c(b, m, cx + x, y + hh, z, cr, cg, cb, 0F);
                UltDraw.c(b, m, cx + x, y + hh, z + len, cr, cg, cb, a);
                UltDraw.c(b, m, cx + x, y, z + len, cr, cg, cb, a);
            }
            UltDraw.end();
        }

        // isik bandi: kosucu isiga donusunce ufka uzanan parlak serit
        if (t >= LIGHT_T - 4F) {
            float kk = Mth.clamp((t - (LIGHT_T - 4F)) / 8F, 0F, 1F);
            double hz = runnerZ(t);
            b = UltDraw.begin(null, UltDraw.Blend.ADD, true, false);
            float[][] layers = {{2.0F, 0.3F}, {0.8F, 0.55F}, {0.2F, 0.9F}};
            int[] cols = {UltDraw.mix(0xFFB070, c.glow, 0.5F), UltDraw.mix(0xFFE0B0, c.glow, 0.25F), 0xFFFFFF};
            double z0 = hz - 160, z1 = hz + 3;
            for (int i = 0; i < 3; i++) {
                float hh = layers[i][0] * 0.5F, a = layers[i][1] * kk;
                int col = cols[i];
                float r = UltDraw.r(col), g = UltDraw.g(col), bl = UltDraw.b(col);
                UltDraw.c(b, m, 0, 1.0 - hh, z0, r, g, bl, 0F);
                UltDraw.c(b, m, 0, 1.0, z0, r, g, bl, 0F);
                UltDraw.c(b, m, 0, 1.0, z1, r, g, bl, a);
                UltDraw.c(b, m, 0, 1.0 - hh, z1, r, g, bl, 0F);
                UltDraw.c(b, m, 0, 1.0, z0, r, g, bl, 0F);
                UltDraw.c(b, m, 0, 1.0 + hh, z0, r, g, bl, 0F);
                UltDraw.c(b, m, 0, 1.0 + hh, z1, r, g, bl, 0F);
                UltDraw.c(b, m, 0, 1.0, z1, r, g, bl, a);
            }
            UltDraw.end();
            b = UltDraw.begin(UltTextures.GLOW, UltDraw.Blend.ADD, true, false);
            UltDraw.billboard(b, m, 0, 1.0, hz, c.right, c.up, 5F, 5F, 0F, UltDraw.mix(0xFFE0B0, c.glow, 0.3F), kk);
            UltDraw.billboard(b, m, 0, 1.0, hz, c.right, c.up, 1.4F, 1.4F, 0F, 0xFFFFFF, kk);
            UltDraw.end();
        }
    }

    @Override
    public void model(UltScene.Ctx c) {
        float t = c.vt;
        if (t < LIGHT_T + 4F) {
            float sp = speed(t);
            int ghosts = sp < 1.6F ? 2 : sp < 5F ? 4 : 6;
            ghosts = Math.max(2, (int) (ghosts * Math.max(0.5F, c.quality)));
            float spacing = Mth.clamp(0.9F / sp, 0.12F, 0.6F);
            float a0 = t < LIGHT_T - 6F ? 0.25F : 0.5F * Math.max(0F, 1F - (t - (LIGHT_T - 6F)) / 10F);
            UltScene.drawCaster(c, PATH, 0F, t < LIGHT_T, ghosts, spacing, a0, 0.9F, 0F);
        }
        // iz: sahne basindan once de kosuyor, iz hazir; isiga donusunce kalinlasir
        int life = t < LIGHT_T ? 16 : 22;
        UltScene.drawTrail(c, PATH, T0 - 30F, life, 1F, t < LIGHT_T, 1F);
    }
}
