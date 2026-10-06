package dev.baranhan.flashmod.client.ultimate;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.baranhan.flashmod.client.render.GlowDraw;
import dev.baranhan.flashmod.client.render.Lightning;
import dev.baranhan.flashmod.client.render.Polyline;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * ORBIT (120-140): uzaydan Dunya turu. Siyah uzay, yildizlar, CPU aydinlatmali Dunya (aydinlik taraf ekranin
 * saginda), donen bulut katmani, atmosfer halesi ve gece tarafinda ilerleyen altin isik cizgisi + yatay flare.
 */
public final class UltOrbitScene implements UltScene.Scene {
    private static final Lightning.Rng R = new Lightning.Rng(1200);
    private static final Polyline LINE = new Polyline();
    private static final double ER = 60.0, RO = 61.5;
    private static final Vec3 SUN = new Vec3(-1.0, 0.25, -0.35).normalize();
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

    @Override
    public void backdrop(UltScene.Ctx c) {
        Matrix4f m = c.view;
        BufferBuilder b = UltDraw.begin(null, UltDraw.Blend.OPAQUE, false, false);
        cube(b, m, 100F, 0.008F, 0.008F, 0.02F); // #020205
        UltDraw.end();
        // yildizlar (kameraya merkezli kure, R=90)
        b = UltDraw.begin(UltTextures.GLOW, UltDraw.Blend.ADD, false, false);
        R.seed(c.s.seed ^ 0x57A2L);
        int n = (int) (1500 * c.quality);
        for (int i = 0; i < n; i++) {
            double z = R.signed(), th = R.next() * Math.PI * 2, rr = Math.sqrt(Math.max(0, 1 - z * z));
            double x = rr * Math.cos(th), y = rr * Math.sin(th);
            float size = (0.4F + 0.8F * R.next()) * 0.35F, br = 0.3F + 0.7F * R.next();
            float tint = R.next();
            int col = tint < 0.025F ? 0xBFD8FF : tint < 0.05F ? 0xFFE8B0 : 0xFFFFFF;
            UltDraw.billboard(b, m, x * 90, y * 90, z * 90, c.right, c.up, size, size, 0F, col, br);
        }
        UltDraw.end();
    }

    @Override
    public void geometry(UltScene.Ctx c) {
        Matrix4f m = c.m;
        int lon = Math.max(32, (int) (96 * c.quality)), lat = Math.max(16, (int) (48 * c.quality));
        // Dunya (opak, derinlik yazar)
        BufferBuilder b = UltDraw.begin(UltTextures.EARTH, UltDraw.Blend.OPAQUE, true, true);
        sphere(b, m, ER, lon, lat, 0F, 1F, false);
        UltDraw.end();
        // bulut katmani (2 derece/s doner)
        b = UltDraw.begin(UltTextures.EARTH_CLOUDS, UltDraw.Blend.ALPHA, true, false);
        sphere(b, m, ER + 0.6, lon, lat, (c.t - 120F) / 20F * 2F / 360F, 1F, true);
        UltDraw.end();
        // atmosfer halesi (kameraya donuk halka, aydinlik tarafta yogun)
        Vec3 toCam = c.cam.normalize();
        float[] u = new float[3], v = new float[3];
        UltWorldFx.basis((float) toCam.x, (float) toCam.y, (float) toCam.z, u, v);
        b = UltDraw.begin(null, UltDraw.Blend.ADD, true, false);
        int seg = 96;
        for (int i = 0; i < seg; i++) {
            double a0 = 2 * Math.PI * i / seg, a1 = 2 * Math.PI * (i + 1) / seg;
            halo(b, m, u, v, a0, a1);
        }
        UltDraw.end();
        streak(c);
    }

    private static void halo(BufferBuilder b, Matrix4f m, float[] u, float[] v, double a0, double a1) {
        double[] ri = {59.5, 64.0};
        Vec3 d0 = new Vec3(u[0] * Math.cos(a0) + v[0] * Math.sin(a0), u[1] * Math.cos(a0) + v[1] * Math.sin(a0),
                u[2] * Math.cos(a0) + v[2] * Math.sin(a0));
        Vec3 d1 = new Vec3(u[0] * Math.cos(a1) + v[0] * Math.sin(a1), u[1] * Math.cos(a1) + v[1] * Math.sin(a1),
                u[2] * Math.cos(a1) + v[2] * Math.sin(a1));
        float w0 = (float) Mth.lerp(Mth.clamp((d0.dot(SUN) + 0.2) / 0.6, 0, 1), 0.15, 1.0);
        float w1 = (float) Mth.lerp(Mth.clamp((d1.dot(SUN) + 0.2) / 0.6, 0, 1), 0.15, 1.0);
        float r = 0.486F, g = 0.753F, bl = 1F;
        UltDraw.c(b, m, d0.x * ri[0], d0.y * ri[0], d0.z * ri[0], r, g, bl, 0.7F * w0);
        UltDraw.c(b, m, d1.x * ri[0], d1.y * ri[0], d1.z * ri[0], r, g, bl, 0.7F * w1);
        UltDraw.c(b, m, d1.x * ri[1], d1.y * ri[1], d1.z * ri[1], r, g, bl, 0F);
        UltDraw.c(b, m, d0.x * ri[1], d0.y * ri[1], d0.z * ri[1], r, g, bl, 0F);
    }

    /** UV kure; vertex renkleri CPU aydinlatmasi (yumusak terminator, gece sehir isigi yok). */
    private static void sphere(BufferBuilder b, Matrix4f m, double rad, int lon, int lat, float uOff, float alpha, boolean clouds) {
        for (int j = 0; j < lat; j++) {
            double p0 = Math.PI * j / lat - Math.PI / 2, p1 = Math.PI * (j + 1) / lat - Math.PI / 2;
            for (int i = 0; i < lon; i++) {
                double l0 = 2 * Math.PI * i / lon, l1 = 2 * Math.PI * (i + 1) / lon;
                sv(b, m, rad, l0, p0, (float) i / lon + uOff, 1F - (float) j / lat, alpha, clouds);
                sv(b, m, rad, l1, p0, (float) (i + 1) / lon + uOff, 1F - (float) j / lat, alpha, clouds);
                sv(b, m, rad, l1, p1, (float) (i + 1) / lon + uOff, 1F - (float) (j + 1) / lat, alpha, clouds);
                sv(b, m, rad, l0, p1, (float) i / lon + uOff, 1F - (float) (j + 1) / lat, alpha, clouds);
            }
        }
    }

    private static void sv(BufferBuilder b, Matrix4f m, double rad, double lo, double la, float u, float v, float a, boolean clouds) {
        double x = Math.cos(la) * Math.cos(lo), y = Math.sin(la), z = Math.cos(la) * Math.sin(lo);
        double d = x * SUN.x + y * SUN.y + z * SUN.z;
        float light = (float) Mth.clamp((d + 0.08) / 0.33, 0, 1);
        light = light * light * (3 - 2 * light);
        float r = Mth.lerp(light, 0.02F, 1F), g = Mth.lerp(light, 0.027F, 1F), bl = Mth.lerp(light, 0.05F, 1F);
        UltDraw.v(b, m, x * rad, y * rad, z * rad, u, v, r, g, bl, clouds ? a * Mth.lerp(light, 0.15F, 1F) : a);
    }

    /** Altin cizgi: yorunge duzleminde bas + 35 derecelik kuyruk + yatay flare. Derinlik test eder, yazmaz. */
    private void streak(UltScene.Ctx c) {
        float t = c.t;
        float theta;
        float swell = 1F;
        if (t < 136F) theta = 70F + 80F * UltCamera.Ease.IN_OUT_SINE.apply((t - 120F) / 16F);
        else {
            float u = Math.min(1F, (t - 136F) / 4F);
            theta = 150F + 28F * u;
            swell = 1F + 5F * u * u;
        }
        int warm = GlowDraw.mixRgb(0xFFB040, c.glow, 0.5F);
        BufferBuilder b = UltDraw.begin(null, UltDraw.Blend.ADD, true, false);
        LINE.clear();
        int seg = 40;
        for (int i = 0; i <= seg; i++) {
            double th = Math.toRadians(theta - 35.0 * (seg - i) / seg);
            Vec3 p = A.scale(Math.cos(th) * RO).add(B.scale(Math.sin(th) * RO));
            LINE.add((float) (p.x - c.cam.x), (float) (p.y - c.cam.y), (float) (p.z - c.cam.z), (float) i / seg, (float) i / seg);
        }
        GlowDraw.ribbon(b, c.view, LINE, 1.4F, GlowDraw.cr(warm), GlowDraw.cg(warm), GlowDraw.cb(warm), 0.9F, false);
        GlowDraw.ribbon(b, c.view, LINE, 0.5F, 1F, 0.965F, 0.85F, 1F, false);
        int h = LINE.size - 1;
        float hx = LINE.x[h], hy = LINE.y[h], hz = LINE.z[h];
        // yatay flare: kameranin sag vektoru boyunca
        float fl = 7F * swell;
        GlowDraw.segment(b, c.view, hx - c.right[0] * fl, hy - c.right[1] * fl, hz - c.right[2] * fl,
                hx, hy, hz, 0.25F * swell, GlowDraw.cr(warm), GlowDraw.cg(warm), GlowDraw.cb(warm), 0F, 0.9F, false);
        GlowDraw.segment(b, c.view, hx, hy, hz, hx + c.right[0] * fl, hy + c.right[1] * fl, hz + c.right[2] * fl,
                0.25F * swell, GlowDraw.cr(warm), GlowDraw.cg(warm), GlowDraw.cb(warm), 0.9F, 0F, false);
        GlowDraw.orb(b, c.view, hx, hy, hz, 1.25F * swell, GlowDraw.cr(warm), GlowDraw.cg(warm), GlowDraw.cb(warm), 0.9F);
        GlowDraw.orb(b, c.view, hx, hy, hz, 0.45F * swell, 1F, 1F, 1F, 1F);
        UltDraw.end();
    }

    /** Kameraya merkezli dolu kup (backdrop). */
    static void cube(BufferBuilder b, Matrix4f m, float s, float r, float g, float bl) {
        float[][] v = {{-s, -s, -s}, {s, -s, -s}, {s, s, -s}, {-s, s, -s}, {-s, -s, s}, {s, -s, s}, {s, s, s}, {-s, s, s}};
        int[][] f = {{0, 1, 2, 3}, {5, 4, 7, 6}, {4, 0, 3, 7}, {1, 5, 6, 2}, {3, 2, 6, 7}, {4, 5, 1, 0}};
        for (int[] q : f) for (int i : q) UltDraw.c(b, m, v[i][0], v[i][1], v[i][2], r, g, bl, 1F);
    }
}
