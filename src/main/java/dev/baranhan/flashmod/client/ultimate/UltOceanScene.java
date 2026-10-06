package dev.baranhan.flashmod.client.ultimate;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.baranhan.flashmod.client.render.Lightning;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * OCEAN (86-120): atmosfer kenarinda okyanus. Egik ufuk, turuncu gokyuzu ve firca bulutlari, gunes, egrilmis
 * okyanus diski (gunes yansimasi + kopuk lekeleri), mavi atmosfer kenari ve Flash'in actigi dev V su duvarlari:
 * yukselir, cokup kopuge doner, ufukta sprey sutunlari. Sonda kamera yukari firlar.
 */
public final class UltOceanScene implements UltScene.Scene {
    private static final Lightning.Rng R = new Lightning.Rng(808);
    private static final double RV = 900.0, DISK = 1500.0;
    private static final Vec3 P0 = new Vec3(-20, 0, 30), H = new Vec3(60, 0, 1100);
    private static final Vec3 SUN = new Vec3(0.35, 0.18, 1.0).normalize();
    private static final double EXP45 = Math.exp(4.5) - 1.0;

    private static double curve(double r) {
        return -r * r / (2.0 * RV);
    }

    @Override
    public void backdrop(UltScene.Ctx c) {
        Matrix4f m = c.view;
        BufferBuilder b = UltDraw.begin(null, UltDraw.Blend.OPAQUE, false, false);
        int seg = 32, rings = 16;
        float rad = 100F;
        for (int i = 0; i < rings; i++) {
            float e0 = -10F + 100F * i / rings, e1 = -10F + 100F * (i + 1) / rings;
            for (int j = 0; j < seg; j++) {
                float a0 = 6.2832F * j / seg, a1 = 6.2832F * (j + 1) / seg;
                vtx(b, m, rad, e0, a0);
                vtx(b, m, rad, e0, a1);
                vtx(b, m, rad, e1, a1);
                vtx(b, m, rad, e1, a0);
            }
        }
        UltDraw.end();
        // firca bulut seritleri (sol alttan sag uste ~15 derece egik)
        b = UltDraw.begin(UltTextures.SKY, UltDraw.Blend.ALPHA, false, false);
        R.seed(c.s.seed ^ 0x5C1EL);
        int[] cols = {0xFFB050, 0xE07040, 0xA04030};
        int n = 20 + (int) (10 * c.quality);
        for (int i = 0; i < n; i++) {
            float az = (R.signed() * 70F) * 0.017453292F, el = (3F + R.next() * 28F) * 0.017453292F;
            float len = 30F + R.next() * 50F, th = 3F + R.next() * 5F, a = 0.3F + R.next() * 0.4F;
            int col = cols[(int) (R.next() * 3) % 3];
            float slide = (c.t * 0.3F + R.next() * 10F) % 1F;
            Vec3 d = new Vec3(Math.sin(az) * Math.cos(el), Math.sin(el), Math.cos(az) * Math.cos(el)).scale(90);
            // seridin ekseni: yataydan 15 derece yukari, ekranda sol-alt -> sag-ust (sol = +X)
            Vec3 axis = new Vec3(-Math.cos(az), 0.27, Math.sin(az)).normalize();
            Vec3 upv = d.normalize().cross(axis).normalize();
            quadTex(b, m, d, axis.scale(len * 0.5), upv.scale(th * 0.5), slide, col, a);
        }
        UltDraw.end();
        // gunes
        b = UltDraw.begin(UltTextures.GLOW, UltDraw.Blend.ADD, false, false);
        Vec3 sp = SUN.scale(90);
        UltDraw.billboard(b, m, sp.x, sp.y, sp.z, c.right, c.up, 22F, 22F, 0F, 0xFFC070, 0.8F);
        UltDraw.billboard(b, m, sp.x, sp.y, sp.z, c.right, c.up, 5F, 5F, 0F, 0xFFF4D0, 1F);
        UltDraw.end();
    }

    private static void vtx(BufferBuilder b, Matrix4f m, float rad, float elev, float az) {
        float e = elev * 0.017453292F;
        double x = Math.sin(az) * Math.cos(e), y = Math.sin(e), z = Math.cos(az) * Math.cos(e);
        int col;
        if (elev < 0F) col = 0x13304D;
        else if (elev < 3F) col = UltDraw.mix(0xE8C0A0, 0xFF9A4A, elev / 3F);
        else if (elev < 18F) col = UltDraw.mix(0xFF9A4A, 0xC0603A, (elev - 3F) / 15F);
        else col = UltDraw.mix(0xC0603A, 0x4A3A48, Math.min(1F, (elev - 18F) / 40F + (float) Math.max(0, x) * 0.3F));
        UltDraw.c(b, m, x * rad, y * rad, z * rad, UltDraw.r(col), UltDraw.g(col), UltDraw.b(col), 1F);
    }

    private static void quadTex(BufferBuilder b, Matrix4f m, Vec3 c, Vec3 ax, Vec3 up, float u0, int col, float a) {
        float r = UltDraw.r(col), g = UltDraw.g(col), bl = UltDraw.b(col);
        UltDraw.v(b, m, c.x - ax.x - up.x, c.y - ax.y - up.y, c.z - ax.z - up.z, u0, 1, r, g, bl, a);
        UltDraw.v(b, m, c.x + ax.x - up.x, c.y + ax.y - up.y, c.z + ax.z - up.z, u0 + 1, 1, r, g, bl, a);
        UltDraw.v(b, m, c.x + ax.x + up.x, c.y + ax.y + up.y, c.z + ax.z + up.z, u0 + 1, 0, r, g, bl, a);
        UltDraw.v(b, m, c.x - ax.x + up.x, c.y - ax.y + up.y, c.z - ax.z + up.z, u0, 0, r, g, bl, a);
    }

    @Override
    public void geometry(UltScene.Ctx c) {
        Matrix4f m = c.m;
        double cx = c.cam.x, cz = c.cam.z;
        // okyanus diski (kutupsal grid, egrilmis)
        int rings = Math.max(16, (int) (64 * c.quality)), slices = Math.max(24, (int) (96 * c.quality));
        BufferBuilder b = UltDraw.begin(null, UltDraw.Blend.OPAQUE, true, true);
        for (int i = 0; i < rings; i++) {
            double r0 = DISK * Math.pow((double) i / rings, 2.0), r1 = DISK * Math.pow((double) (i + 1) / rings, 2.0);
            for (int j = 0; j < slices; j++) {
                double a0 = 2 * Math.PI * j / slices, a1 = 2 * Math.PI * (j + 1) / slices;
                ocean(b, m, c, cx + Math.cos(a0) * r0, cz + Math.sin(a0) * r0, r0);
                ocean(b, m, c, cx + Math.cos(a1) * r0, cz + Math.sin(a1) * r0, r0);
                ocean(b, m, c, cx + Math.cos(a1) * r1, cz + Math.sin(a1) * r1, r1);
                ocean(b, m, c, cx + Math.cos(a0) * r1, cz + Math.sin(a0) * r1, r1);
            }
        }
        UltDraw.end();
        // dalga lekeleri (kayan doku)
        b = UltDraw.begin(UltTextures.CAPS, UltDraw.Blend.ALPHA, true, false);
        float scroll = c.t * 0.02F;
        for (int i = 0; i < 10; i++) {
            double r0 = 600.0 * i / 10, r1 = 600.0 * (i + 1) / 10;
            for (int j = 0; j < 32; j++) {
                double a0 = 2 * Math.PI * j / 32, a1 = 2 * Math.PI * (j + 1) / 32;
                capsV(b, m, cx + Math.cos(a0) * r0, cz + Math.sin(a0) * r0, r0, scroll, cx, cz);
                capsV(b, m, cx + Math.cos(a1) * r0, cz + Math.sin(a1) * r0, r0, scroll, cx, cz);
                capsV(b, m, cx + Math.cos(a1) * r1, cz + Math.sin(a1) * r1, r1, scroll, cx, cz);
                capsV(b, m, cx + Math.cos(a0) * r1, cz + Math.sin(a0) * r1, r1, scroll, cx, cz);
            }
        }
        UltDraw.end();
        // atmosfer kenari
        b = UltDraw.begin(null, UltDraw.Blend.ADD, true, false);
        double ry = curve(DISK);
        for (int j = 0; j < 96; j++) {
            double a0 = 2 * Math.PI * j / 96, a1 = 2 * Math.PI * (j + 1) / 96;
            UltDraw.c(b, m, cx + Math.cos(a0) * DISK, ry, cz + Math.sin(a0) * DISK, 0.44F, 0.71F, 1F, 0.9F);
            UltDraw.c(b, m, cx + Math.cos(a1) * DISK, ry, cz + Math.sin(a1) * DISK, 0.44F, 0.71F, 1F, 0.9F);
            UltDraw.c(b, m, cx + Math.cos(a1) * DISK, ry + 25, cz + Math.sin(a1) * DISK, 0.44F, 0.71F, 1F, 0F);
            UltDraw.c(b, m, cx + Math.cos(a0) * DISK, ry + 25, cz + Math.sin(a0) * DISK, 0.44F, 0.71F, 1F, 0F);
        }
        UltDraw.end();
        wake(c);
    }

    private static void ocean(BufferBuilder b, Matrix4f m, UltScene.Ctx c, double x, double z, double r) {
        double y = curve(r);
        float k = (float) Math.min(1.0, r / DISK);
        int base = UltDraw.mix(0x13304D, 0x24507A, (float) Math.sqrt(k));
        // gunes yansimasi: reflect(viewDir, up) . sun
        Vec3 v = new Vec3(x - c.cam.x, y - c.cam.y, z - c.cam.z).normalize();
        double refl = Math.max(0.0, v.x * SUN.x + (-v.y) * SUN.y + v.z * SUN.z);
        float glint = (float) (Math.pow(refl, 24.0) * 0.6);
        base = UltDraw.mix(base, 0xFFD0A0, glint);
        base = UltDraw.mix(base, 0xC08070, k * 0.85F);
        UltDraw.c(b, m, x, y, z, UltDraw.r(base), UltDraw.g(base), UltDraw.b(base), 1F);
    }

    private static void capsV(BufferBuilder b, Matrix4f m, double x, double z, double r, float scroll, double cx, double cz) {
        float a = (float) Math.max(0.0, 1.0 - r / 600.0) * 0.5F;
        UltDraw.v(b, m, x, curve(r) + 0.1, z, (float) (x / 40.0), (float) (z / 40.0) + scroll, 1F, 1F, 1F, a);
    }

    // ---------------------------------------------------------------- V su duvarlari

    private static double tPass(double s) {
        return 93.0 + Math.log(1.0 + s * EXP45) / 0.45;
    }

    private void wake(UltScene.Ctx c) {
        Matrix4f m = c.m;
        float t = c.vt;
        if (t < 93F) return;
        Vec3 dir = H.subtract(P0);
        double len = dir.length();
        dir = dir.scale(1.0 / len);
        Vec3 side = new Vec3(-dir.z, 0, dir.x); // dir x up
        double s = Math.min(1.0, (Math.exp(0.45 * (t - 93.0)) - 1.0) / EXP45);
        Vec3 tip = P0.add(dir.scale(len * s));
        int samples = (int) (len / 6.0);
        int stride = c.quality < 0.5F ? 2 : 1;

        // duvarlar
        BufferBuilder b = UltDraw.begin(UltTextures.FOAM_V, UltDraw.Blend.ALPHA, true, false);
        double tan25 = Math.tan(Math.toRadians(25));
        for (int side2 = -1; side2 <= 1; side2 += 2) {
            int col = side2 < 0 ? UltDraw.mix(0xD8E8F0, 0xFFC890, 0.6F) : 0xD8E8F0;
            float cr = UltDraw.r(col), cg = UltDraw.g(col), cb = UltDraw.b(col);
            for (int i = 0; i + stride <= samples; i += stride) {
                double s0 = (double) i / samples, s1 = (double) (i + stride) / samples;
                if (s1 > s) break;
                double age0 = t - tPass(s0), age1 = t - tPass(s1);
                double w0 = width(age0), w1 = width(age1), h0 = height(age0), h1 = height(age1);
                if (h0 < 0.5 && h1 < 0.5) continue;
                Vec3 c0 = P0.add(dir.scale(len * s0)), c1 = P0.add(dir.scale(len * s1));
                double y0 = curve(Math.hypot(c0.x - c.cam.x, c0.z - c.cam.z)), y1 = curve(Math.hypot(c1.x - c.cam.x, c1.z - c.cam.z));
                Vec3 b0 = c0.add(side.scale(side2 * w0)), b1 = c1.add(side.scale(side2 * w1));
                Vec3 t0 = b0.add(side.scale(side2 * h0 * tan25)), t1 = b1.add(side.scale(side2 * h1 * tan25));
                float vs = c.t * 0.12F;
                UltDraw.v(b, m, b0.x, y0, b0.z, (float) s0 * 30, 1 + vs, cr, cg, cb, 0.95F);
                UltDraw.v(b, m, b1.x, y1, b1.z, (float) s1 * 30, 1 + vs, cr, cg, cb, 0.95F);
                UltDraw.v(b, m, t1.x, y1 + h1, t1.z, (float) s1 * 30, vs, cr, cg, cb, 0F);
                UltDraw.v(b, m, t0.x, y0 + h0, t0.z, (float) s0 * 30, vs, cr, cg, cb, 0F);
            }
        }
        UltDraw.end();

        // merkez kanal (koyu) + kopuk ortusu
        BufferBuilder ch = UltDraw.begin(null, UltDraw.Blend.ALPHA, true, false);
        for (int i = 0; i + stride <= samples; i += stride) {
            double s0 = (double) i / samples, s1 = (double) (i + stride) / samples;
            if (s1 > s) break;
            double age = t - tPass(s0);
            if (age > 10) continue;
            Vec3 c0 = P0.add(dir.scale(len * s0)), c1 = P0.add(dir.scale(len * s1));
            double w = width(age) * 0.7, y0 = curve(Math.hypot(c0.x - c.cam.x, c0.z - c.cam.z)) + 0.2;
            UltDraw.c(ch, m, c0.x - side.x * w, y0, c0.z - side.z * w, 0.04F, 0.1F, 0.18F, 0.5F);
            UltDraw.c(ch, m, c1.x - side.x * w, y0, c1.z - side.z * w, 0.04F, 0.1F, 0.18F, 0.5F);
            UltDraw.c(ch, m, c1.x + side.x * w, y0, c1.z + side.z * w, 0.04F, 0.1F, 0.18F, 0.5F);
            UltDraw.c(ch, m, c0.x + side.x * w, y0, c0.z + side.z * w, 0.04F, 0.1F, 0.18F, 0.5F);
        }
        UltDraw.end();
        BufferBuilder fo = UltDraw.begin(UltTextures.FOAM, UltDraw.Blend.ALPHA, true, false);
        for (int i = 0; i < samples; i += stride) {
            double s0 = (double) i / samples;
            if (s0 > s) break;
            double age = t - tPass(s0);
            if (age <= 10 || age > 40) continue;
            float a = (float) (0.8 * (1.0 - (age - 10) / 30.0));
            double grow = 1.6 + 0.8 * Math.min(1.0, (age - 10) / 20.0);
            double w = width(age) * grow;
            Vec3 c0 = P0.add(dir.scale(len * s0));
            double y0 = curve(Math.hypot(c0.x - c.cam.x, c0.z - c.cam.z)) + 0.3;
            Vec3 f = dir.scale(4);
            UltDraw.v(fo, m, c0.x - side.x * w - f.x, y0, c0.z - side.z * w - f.z, 0, 0, 1, 1, 1, a);
            UltDraw.v(fo, m, c0.x + side.x * w - f.x, y0, c0.z + side.z * w - f.z, 1, 0, 1, 1, 1, a);
            UltDraw.v(fo, m, c0.x + side.x * w + f.x, y0, c0.z + side.z * w + f.z, 1, 1, 1, 1, 1, a);
            UltDraw.v(fo, m, c0.x - side.x * w + f.x, y0, c0.z - side.z * w + f.z, 0, 1, 1, 1, 1, a);
        }
        UltDraw.end();

        // sprey damlalari (analitik balistik), kamera alti patlamasi, ufuk sutunlari, uc parlamasi
        BufferBuilder sp = UltDraw.begin(UltTextures.GLOW, UltDraw.Blend.ALPHA, true, false);
        R.seed(c.s.seed ^ 0x5B4AL);
        int sStride = Math.max(1, (int) (3 / Math.max(0.3F, c.quality)));
        for (int i = 0; i < samples; i += sStride) {
            double s0 = (double) i / samples;
            if (s0 > s) break;
            double tp = tPass(s0), age = t - tp;
            if (age < 0 || age > 22) continue;
            Vec3 c0 = P0.add(dir.scale(len * s0));
            double y0 = curve(Math.hypot(c0.x - c.cam.x, c0.z - c.cam.z));
            for (int k = 0; k < 4; k++) {
                double born = k * 2.0, life = age - born;
                if (life < 0 || life > 14) continue;
                int sd = k % 2 == 0 ? 1 : -1;
                double wb = width(born), hb = height(born);
                double vx = sd * (0.8 + 0.6 * Math.abs(Math.sin(i * 7.1 + k))), vy = 2.2 + Math.abs(Math.cos(i * 3.3 + k));
                double px = c0.x + side.x * sd * wb + side.x * vx * life, pz = c0.z + side.z * sd * wb + side.z * vx * life;
                double py = y0 + hb + vy * life - 0.2 * life * life;
                float a = (float) (0.7 * (1.0 - life / 14.0));
                float size = (float) (4.0 + 6.0 * (life / 14.0));
                UltDraw.billboard(sp, m, px, py, pz, c.right, c.up, size, size, 0F, 0xF0F8FF, a);
            }
        }
        if (t >= 93F && t < 107F) {
            float u = (t - 93F) / 14F, grow = Math.min(1F, (t - 93F) / 4F);
            for (int i = 0; i < 4; i++) {
                double ox = (i - 1.5) * 18, oz = 10 + i * 6;
                float sz = 40F + 40F * grow;
                UltDraw.billboard(sp, m, P0.x + ox, curve(30) + sz * 0.3, P0.z + oz, c.right, c.up, sz, sz, i,
                        0xFFFFFF, 0.8F * (1F - u));
            }
        }
        UltDraw.end();
        BufferBuilder add = UltDraw.begin(UltTextures.GLOW, UltDraw.Blend.ADD, true, false);
        for (int i = 0; i < samples; i += 4) {
            double s0 = (double) i / samples;
            if (s0 < 0.85 || s0 > s) continue;
            double age = t - tPass(s0);
            if (age < 10 || age > 16) continue;
            Vec3 c0 = P0.add(dir.scale(len * s0));
            double y0 = curve(Math.hypot(c0.x - c.cam.x, c0.z - c.cam.z));
            double hgt = 60 + 140 * Math.abs(Math.sin(i * 1.7));
            float a = (float) (1.0 - (age - 10) / 6.0) * 0.6F;
            Vec3 axis = new Vec3(c.right[0], c.right[1], c.right[2]);
            UltDraw.v(add, m, c0.x - axis.x, y0, c0.z - axis.z, 0, 1, 0.8F, 0.9F, 1F, a);
            UltDraw.v(add, m, c0.x + axis.x, y0, c0.z + axis.z, 1, 1, 0.8F, 0.9F, 1F, a);
            UltDraw.v(add, m, c0.x + axis.x, y0 + hgt, c0.z + axis.z, 1, 0, 0.8F, 0.9F, 1F, 0F);
            UltDraw.v(add, m, c0.x - axis.x, y0 + hgt, c0.z - axis.z, 0, 0, 0.8F, 0.9F, 1F, 0F);
        }
        if (s < 1.0) {
            double ty = curve(Math.hypot(tip.x - c.cam.x, tip.z - c.cam.z)) + 2;
            UltDraw.billboard(add, m, tip.x, ty, tip.z, c.right, c.up, 16F, 16F, 0F, UltDraw.mix(0xBFE0FF, c.glow, 0.4F), 0.9F);
            UltDraw.billboard(add, m, tip.x, ty, tip.z, c.right, c.up, 3F, 3F, 0F, 0xFFFFFF, 1F);
        }
        UltDraw.end();
    }

    private static double width(double age) {
        return age < 0 ? 0 : 4.0 + 30.0 * (1.0 - Math.exp(-age / 3.0));
    }

    private static double height(double age) {
        if (age < 0) return 0;
        double collapse = age < 10 ? 1.0 : Math.max(0.0, 1.0 - (age - 10) / 8.0);
        return 75.0 * (1.0 - Math.exp(-age / 2.2)) * collapse;
    }
}
