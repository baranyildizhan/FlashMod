package dev.baranhan.flashmod.client.ultimate;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.flashmod.client.render.FlashRenderTypes;
import dev.baranhan.flashmod.client.render.GlowDraw;
import dev.baranhan.flashmod.client.render.Lightning;
import dev.baranhan.flashmod.ultimate.UltimatePhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * ORBIT: uzaydan Dunya turu. NASA Blue Marble dokulu Dunya (gunduz + gece sehir isiklari, yumusak terminator),
 * ayri donen bulut katmani, atmosfer halesi, yildizlar. Flash yuzeye yakin buyuk bir cemberde gezegeni tam tur
 * (1.25 tur) kosar: arkasinda bizim iz sistemimizle cizilen simsek izi (iz olcegi 2x), gezegenin arkasina
 * gecince derinlikle gizlenir, diger yandan geri cikar. Sonda yuzeye dogru dalar (beyaz flas -> tunel).
 */
public final class UltOrbitScene implements UltScene.Scene {
    private static final Lightning.Rng R = new Lightning.Rng(1200);
    private static final double ER = 60.0, RO = 63.0;
    private static final float T0 = UltimatePhase.ORBIT.start, T1 = UltimatePhase.ORBIT.end;
    /** Tur suresi (tick) ve baslangic acisi: bas sag kenardan cikip once kameranin onunden gecer. */
    private static final double LAP = 50.0, THETA0 = Math.toRadians(-34.0);
    private static final float DIVE_T = 244F, TRAIL_SCALE = 2F;
    private static final Vec3 SUN = new Vec3(-0.85, 0.30, -0.42).normalize();
    private static final Vec3 N = new Vec3(0.15, 1, 0.1).normalize();
    private static final Vec3 A, B;

    static {
        Vec3 a = new Vec3(-1, 0, 0);
        a = a.subtract(N.scale(a.dot(N))).normalize();
        Vec3 b = new Vec3(0, 0, -1);
        b = b.subtract(N.scale(b.dot(N)));
        b = b.subtract(a.scale(b.dot(a))).normalize();
        A = a;
        B = b;
    }

    /** Kosu acisi: ilk 10 tick'te hizlanarak, sonra sabit hizla. */
    static double theta(float t) {
        double x = t - (T0 + 2F);
        double w = 2 * Math.PI / LAP;
        if (x < 0) return THETA0 + 0.6 * w * x;
        if (x < 10) return THETA0 + w * (0.6 * x + 0.4 * x * x / 20.0);
        return THETA0 + w * (0.6 * 10 + 0.4 * 10 / 2.0) + w * (x - 10);
    }

    /** Yorunge yaricapi: sonda yuzeye dogru dalar. */
    static double radius(float t) {
        if (t < DIVE_T) return RO;
        double u = Math.min(1.0, (t - DIVE_T) / (T1 - 4F - DIVE_T));
        return Mth.lerp(u * u, RO, ER - 6.0);
    }

    static final UltScene.TrailPath PATH = new UltScene.TrailPath() {
        @Override
        public Vec3 at(float t) {
            double th = theta(t), r = radius(t);
            return A.scale(Math.cos(th) * r).add(B.scale(Math.sin(th) * r));
        }

        @Override
        public double odo(float t) {
            return theta(t) * RO;
        }

        @Override
        public Vec3 up(Vec3 p) {
            return p.normalize(); // izin "yukarisi" radyal: iplikler yuzeyden disari kabarir
        }
    };

    @Override
    public void backdrop(UltScene.Ctx c) {
        Matrix4f m = c.view;
        BufferBuilder b = UltDraw.begin(null, UltDraw.Blend.OPAQUE, false, false);
        cube(b, m, 100F, 0.006F, 0.007F, 0.016F);
        UltDraw.end();
        // yildizlar (kameraya merkezli kure, R=90) + soluk samanyolu bandi
        b = UltDraw.begin(UltTextures.GLOW, UltDraw.Blend.ADD, false, false);
        R.seed(c.s.seed ^ 0x57A2L);
        int n = (int) (1800 * c.quality);
        for (int i = 0; i < n; i++) {
            double z = R.signed(), th = R.next() * Math.PI * 2, rr = Math.sqrt(Math.max(0, 1 - z * z));
            double x = rr * Math.cos(th), y = rr * Math.sin(th);
            float size = (0.35F + 0.8F * R.next() * R.next()) * 0.35F, br = 0.25F + 0.75F * R.next();
            float tint = R.next();
            int col = tint < 0.04F ? 0xBFD8FF : tint < 0.08F ? 0xFFE8B0 : 0xFFFFFF;
            UltDraw.billboard(b, m, x * 90, y * 90, z * 90, c.right, c.up, size, size, 0F, col, br);
        }
        R.seed(c.s.seed ^ 0x3A1L);
        for (int i = 0; i < 70; i++) {
            double a = R.next() * Math.PI * 2, off = R.signed() * 0.12;
            Vec3 d = new Vec3(Math.cos(a), off + 0.35 * Math.sin(a), Math.sin(a)).normalize().scale(90);
            float s = 10F + 18F * R.next();
            UltDraw.billboard(b, m, d.x, d.y, d.z, c.right, c.up, s, s * 0.6F, R.next() * 3F, 0x8A90B8, 0.05F);
        }
        UltDraw.end();
    }

    @Override
    public void geometry(UltScene.Ctx c) {
        Matrix4f m = c.m;
        float t = c.t;
        int lon = Math.max(48, (int) (128 * c.quality)), lat = Math.max(24, (int) (64 * c.quality));
        float spin = (t - T0) / 20F * 1.5F / 360F; // yavas eksen donusu
        // gunduz yuzu (opak, derinlik yazar)
        BufferBuilder b = UltDraw.begin(UltTextures.EARTH, UltDraw.Blend.OPAQUE, true, true);
        sphere(b, m, ER, lon, lat, spin, 0);
        UltDraw.end();
        // gece sehir isiklari (karanlik tarafta, eklemeli)
        b = UltDraw.begin(UltTextures.EARTH_NIGHT, UltDraw.Blend.ADD, true, false);
        sphere(b, m, ER + 0.02, lon, lat, spin, 1);
        UltDraw.end();
        // bulut katmani (biraz daha hizli doner)
        b = UltDraw.begin(UltTextures.EARTH_CLOUDS, UltDraw.Blend.ALPHA, true, false);
        sphere(b, m, ER + 0.45, lon, lat, spin * 1.6F + 0.13F, 2);
        UltDraw.end();
        // atmosfer halesi (kameraya donuk halka, aydinlik tarafta yogun)
        Vec3 toCam = c.cam.normalize();
        float[] u = new float[3], v = new float[3];
        UltWorldFx.basis((float) toCam.x, (float) toCam.y, (float) toCam.z, u, v);
        double dc = c.cam.length();
        // orijinden gecen, kameraya dik duzlemde gezegenin gorunen kenarina oturan yaricap
        double limb = ER / Math.sqrt(Math.max(1.0E-4, 1.0 - (ER * ER) / (dc * dc)));
        b = UltDraw.begin(null, UltDraw.Blend.ADD, true, false);
        int seg = 128;
        for (int i = 0; i < seg; i++) {
            double a0 = 2 * Math.PI * i / seg, a1 = 2 * Math.PI * (i + 1) / seg;
            halo(b, m, u, v, a0, a1, limb);
        }
        UltDraw.end();
        streak(c);
    }

    /** Gezegenin ekrandaki kenari etrafinda ince mavi atmosfer: icte yogun, disa dogru soner. */
    private static void halo(BufferBuilder b, Matrix4f m, float[] u, float[] v, double a0, double a1, double limb) {
        double[] ri = {limb * 0.985, limb * 1.0, limb * 1.06};
        Vec3 d0 = new Vec3(u[0] * Math.cos(a0) + v[0] * Math.sin(a0), u[1] * Math.cos(a0) + v[1] * Math.sin(a0),
                u[2] * Math.cos(a0) + v[2] * Math.sin(a0));
        Vec3 d1 = new Vec3(u[0] * Math.cos(a1) + v[0] * Math.sin(a1), u[1] * Math.cos(a1) + v[1] * Math.sin(a1),
                u[2] * Math.cos(a1) + v[2] * Math.sin(a1));
        float w0 = (float) Mth.lerp(Mth.clamp((d0.dot(SUN) + 0.25) / 0.6, 0, 1), 0.08, 1.0);
        float w1 = (float) Mth.lerp(Mth.clamp((d1.dot(SUN) + 0.25) / 0.6, 0, 1), 0.08, 1.0);
        float r = 0.42F, g = 0.68F, bl = 1F;
        UltDraw.c(b, m, d0.x * ri[0], d0.y * ri[0], d0.z * ri[0], r, g, bl, 0F);
        UltDraw.c(b, m, d1.x * ri[0], d1.y * ri[0], d1.z * ri[0], r, g, bl, 0F);
        UltDraw.c(b, m, d1.x * ri[1], d1.y * ri[1], d1.z * ri[1], r, g, bl, 0.75F * w1);
        UltDraw.c(b, m, d0.x * ri[1], d0.y * ri[1], d0.z * ri[1], r, g, bl, 0.75F * w0);
        UltDraw.c(b, m, d0.x * ri[1], d0.y * ri[1], d0.z * ri[1], r, g, bl, 0.75F * w0);
        UltDraw.c(b, m, d1.x * ri[1], d1.y * ri[1], d1.z * ri[1], r, g, bl, 0.75F * w1);
        UltDraw.c(b, m, d1.x * ri[2], d1.y * ri[2], d1.z * ri[2], r, g, bl, 0F);
        UltDraw.c(b, m, d0.x * ri[2], d0.y * ri[2], d0.z * ri[2], r, g, bl, 0F);
    }

    /**
     * UV kure (boylam dogu yonunde artar; z = -cos(enlem) sin(boylam) -> doku aynalanmaz). Vertex renkleri CPU
     * aydinlatmasi: mode 0 gunduz (yumusak terminator, hafif okyanus parlamasi), 1 gece isiklari (sadece karanlikta),
     * 2 bulut (aydinlikta beyaz, karanlikta kaybolur).
     */
    private static void sphere(BufferBuilder b, Matrix4f m, double rad, int lon, int lat, float uOff, int mode) {
        for (int j = 0; j < lat; j++) {
            double p0 = Math.PI * j / lat - Math.PI / 2, p1 = Math.PI * (j + 1) / lat - Math.PI / 2;
            for (int i = 0; i < lon; i++) {
                double l0 = 2 * Math.PI * i / lon, l1 = 2 * Math.PI * (i + 1) / lon;
                sv(b, m, rad, l0, p0, (float) i / lon + uOff, 1F - (float) j / lat, mode);
                sv(b, m, rad, l1, p0, (float) (i + 1) / lon + uOff, 1F - (float) j / lat, mode);
                sv(b, m, rad, l1, p1, (float) (i + 1) / lon + uOff, 1F - (float) (j + 1) / lat, mode);
                sv(b, m, rad, l0, p1, (float) i / lon + uOff, 1F - (float) (j + 1) / lat, mode);
            }
        }
    }

    private static void sv(BufferBuilder b, Matrix4f m, double rad, double lo, double la, float u, float v, int mode) {
        double x = Math.cos(la) * Math.cos(lo), y = Math.sin(la), z = -Math.cos(la) * Math.sin(lo);
        double d = x * SUN.x + y * SUN.y + z * SUN.z;
        float light = (float) Mth.clamp((d + 0.10) / 0.35, 0, 1);
        light = light * light * (3 - 2 * light);
        if (mode == 0) {
            float k = Mth.lerp(light, 0.035F, 1F); // >1 renk bayti tasar (siyah kitalar)
            UltDraw.v(b, m, x * rad, y * rad, z * rad, u, v, k, k, k, 1F);
        } else if (mode == 1) {
            float night = (1F - light) * (1F - light);
            UltDraw.v(b, m, x * rad, y * rad, z * rad, u, v, 1F, 0.78F, 0.42F, 0.9F * night);
        } else {
            UltDraw.v(b, m, x * rad, y * rad, z * rad, u, v, 1F, 1F, 1F, 0.9F * Mth.lerp(light, 0.04F, 1F));
        }
    }

    /** Kosucunun dev simsek izi (bizim iz sistemi, olcekli) + basindaki isik ve yatay flare. */
    private void streak(UltScene.Ctx c) {
        float t = c.vt;
        UltScene.drawTrail(c, PATH, T0 - 20F, 14, TRAIL_SCALE, false, 1F);
        float swell = t < DIVE_T ? 1F : 1F + 4F * (float) Math.pow(Math.min(1F, (t - DIVE_T) / (T1 - DIVE_T)), 2);
        Vec3 h = PATH.at(t).subtract(c.cam);
        // gezegenin arkasindayken isik da gizlenir (ADDITIVE_GLOW derinlik testi yapar)
        int warm = GlowDraw.mixRgb(0xFFC060, c.glow, 0.5F);
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(FlashRenderTypes.ADDITIVE_GLOW);
        float hx = (float) h.x, hy = (float) h.y, hz = (float) h.z;
        float fl = 3F * swell;
        GlowDraw.segment(vc, c.view, hx - c.right[0] * fl, hy - c.right[1] * fl, hz - c.right[2] * fl,
                hx, hy, hz, 0.1F * swell, GlowDraw.cr(warm), GlowDraw.cg(warm), GlowDraw.cb(warm), 0F, 0.8F, false);
        GlowDraw.segment(vc, c.view, hx, hy, hz, hx + c.right[0] * fl, hy + c.right[1] * fl, hz + c.right[2] * fl,
                0.1F * swell, GlowDraw.cr(warm), GlowDraw.cg(warm), GlowDraw.cb(warm), 0.8F, 0F, false);
        GlowDraw.orb(vc, c.view, hx, hy, hz, 2F * swell, GlowDraw.cr(warm), GlowDraw.cg(warm), GlowDraw.cb(warm), 0.35F);
        GlowDraw.orb(vc, c.view, hx, hy, hz, 0.87F * swell, GlowDraw.cr(warm), GlowDraw.cg(warm), GlowDraw.cb(warm), 0.75F);
        GlowDraw.orb(vc, c.view, hx, hy, hz, 0.33F * swell, 1F, 1F, 1F, 1F);
        buffers.endBatch(FlashRenderTypes.ADDITIVE_GLOW);
    }

    /** Kameraya merkezli dolu kup (backdrop). */
    static void cube(BufferBuilder b, Matrix4f m, float s, float r, float g, float bl) {
        float[][] v = {{-s, -s, -s}, {s, -s, -s}, {s, s, -s}, {-s, s, -s}, {-s, -s, s}, {s, -s, s}, {s, s, s}, {-s, s, s}};
        int[][] f = {{0, 1, 2, 3}, {5, 4, 7, 6}, {4, 0, 3, 7}, {1, 5, 6, 2}, {3, 2, 6, 7}, {4, 5, 1, 0}};
        for (int[] q : f) for (int i : q) UltDraw.c(b, m, v[i][0], v[i][1], v[i][2], r, g, bl, 1F);
    }
}
