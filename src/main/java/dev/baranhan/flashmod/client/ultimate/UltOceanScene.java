package dev.baranhan.flashmod.client.ultimate;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.baranhan.flashmod.client.render.Lightning;
import dev.baranhan.flashmod.ultimate.UltimatePhase;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * OCEAN: gun batiminda acik denizde su ustunde kosu. Su duz bir duzlem (kure degil); kamera alcaktan takip eder,
 * director ufku sol alttan sag uste egik ve su alani ekranin ~%40'i olacak sekilde pitch/roll'u hesaplar.
 * Arka plan tam kure (gercek dunya hicbir aciyla gorunmez). Su: dunyaya sabit dalgalar, Fresnel'li gok yansimasi,
 * gunes parlamasi. Kosucunun ayaklarindan iki yana acilan buyuk V su perdeleri (yat dalgasi gibi), arkasinda
 * kopuk izi, perde tepelerinde su sisi, adim halkalari ve bizim simsek izimiz. Kamera sona kadar kosucuyla gider
 * (kosucu ufka dogru kayip yukari tirmanmis gibi gorunmez).
 */
public final class UltOceanScene implements UltScene.Scene {
    private static final Lightning.Rng R = new Lightning.Rng(808);
    private static final float T0 = UltimatePhase.OCEAN.start, T1 = UltimatePhase.OCEAN.end;
    /** Gunes: ekranin sag ust tarafinda (kosu yonu +Z, sol = +X), ufka yakin. */
    private static final Vec3 SUN = new Vec3(-0.42, 0.10, 1.0).normalize();
    private static final float RUN = 2.4F, ACCEL_T = 160F;
    private static final double FAR = 3500.0;
    private static final int DEEP = 0x07202F, SHALLOW = 0x134A5E, HORIZON = 0xF2C9A0, HAZE = 0xC89A86;
    /** Ufka yakin suyun rengi (pus): gok kubbenin ufuk alti da bu renkle baslar. */
    private static final int FAR_WATER = UltDraw.mix(HORIZON, HAZE, 0.35F);
    /** Yakin su ustune serilen ince dalga ve gunes parlamasi katmanlarinin yaricapi. */
    private static final double DETAIL_R = 140.0;

    // ---------------------------------------------------------------- kosucu

    static double runnerZ(float t) {
        if (t <= ACCEL_T) return RUN * (t - T0);
        double base = RUN * (ACCEL_T - T0), u = t - ACCEL_T;
        // ACCEL_T'den sonra ivmelenme: v = RUN + 0.35 u^2 (blok/tick)
        return base + RUN * u + 0.35 * u * u * u / 3.0;
    }

    /** Kosucu hep su ustunde kalir (ufka dogru yukselmez). */
    static double runnerY(float t) {
        return 0.0;
    }

    static final UltScene.TrailPath PATH = new UltScene.TrailPath() {
        @Override
        public Vec3 at(float t) {
            return new Vec3(0, runnerY(t), runnerZ(t));
        }

        @Override
        public double odo(float t) {
            return runnerZ(t) + runnerY(t);
        }
    };

    // ---------------------------------------------------------------- gokyuzu

    /** Gok rengi: yukseklik (dy, -1..1) ve gunese yakinlik. */
    private static int sky(double dx, double dy, double dz) {
        double el = Math.toDegrees(Math.asin(Mth.clamp(dy, -1, 1)));
        if (el < 0) return UltDraw.mix(FAR_WATER, SHALLOW, (float) Mth.clamp(-el / 8.0, 0, 1)); // ufkun alti: uzak su rengi (ek yeri gorunmez)
        int c;
        if (el < 4) c = UltDraw.mix(HORIZON, 0xF6A766, (float) (el / 4.0));
        else if (el < 22) c = UltDraw.mix(0xF6A766, 0xC8645A, (float) ((el - 4) / 18.0));
        else c = UltDraw.mix(0xC8645A, 0x2C3560, (float) Math.min(1.0, (el - 22) / 45.0));
        double sun = Math.max(0, dx * SUN.x + dy * SUN.y + dz * SUN.z);
        return UltDraw.mix(c, 0xFFE2B8, (float) (Math.pow(sun, 6) * 0.55));
    }

    @Override
    public void backdrop(UltScene.Ctx c) {
        Matrix4f m = c.view;
        BufferBuilder b = UltDraw.begin(null, UltDraw.Blend.OPAQUE, false, false);
        int seg = 48, rings = 36;
        float rad = 100F;
        for (int i = 0; i < rings; i++) { // tam kure: -90..90
            double e0 = Math.toRadians(-90 + 180.0 * i / rings), e1 = Math.toRadians(-90 + 180.0 * (i + 1) / rings);
            for (int j = 0; j < seg; j++) {
                double a0 = 2 * Math.PI * j / seg, a1 = 2 * Math.PI * (j + 1) / seg;
                skyV(b, m, rad, e0, a0);
                skyV(b, m, rad, e0, a1);
                skyV(b, m, rad, e1, a1);
                skyV(b, m, rad, e1, a0);
            }
        }
        UltDraw.end();
        // firca bulutlar: ufka paralel uzun seritler
        b = UltDraw.begin(UltTextures.SKY, UltDraw.Blend.ALPHA, false, false);
        R.seed(c.s.seed ^ 0x5C1EL);
        int[] cols = {0xFFC27A, 0xF08A5A, 0xB85A4A, 0x7A4A5E};
        int n = 22 + (int) (12 * c.quality);
        for (int i = 0; i < n; i++) {
            double az = Math.toRadians(R.signed() * 80F), el = Math.toRadians(2.5F + R.next() * 26F);
            float len = 35F + R.next() * 55F, th = 2F + R.next() * 5F, a = 0.25F + R.next() * 0.45F;
            int col = cols[Math.min(3, (int) (el / Math.toRadians(8)))];
            float slide = (c.t * 0.004F + R.next()) % 1F;
            Vec3 d = new Vec3(Math.sin(az) * Math.cos(el), Math.sin(el), Math.cos(az) * Math.cos(el)).scale(92);
            Vec3 axis = new Vec3(Math.cos(az), 0, -Math.sin(az)).normalize();   // ufka paralel
            Vec3 upv = d.normalize().cross(axis).normalize();
            quadTex(b, m, d, axis.scale(len * 0.5), upv.scale(th * 0.5), slide, col, a);
        }
        UltDraw.end();
        // gunes
        b = UltDraw.begin(UltTextures.GLOW, UltDraw.Blend.ADD, false, false);
        Vec3 sp = SUN.scale(90);
        UltDraw.billboard(b, m, sp.x, sp.y, sp.z, c.right, c.up, 40F, 40F, 0F, 0xFF9A50, 0.55F);
        UltDraw.billboard(b, m, sp.x, sp.y, sp.z, c.right, c.up, 12F, 12F, 0F, 0xFFD090, 0.9F);
        UltDraw.billboard(b, m, sp.x, sp.y, sp.z, c.right, c.up, 4.5F, 4.5F, 0F, 0xFFFFF0, 1F);
        UltDraw.end();
    }

    private static void skyV(BufferBuilder b, Matrix4f m, float rad, double el, double az) {
        double x = Math.sin(az) * Math.cos(el), y = Math.sin(el), z = Math.cos(az) * Math.cos(el);
        int col = sky(x, y, z);
        UltDraw.c(b, m, x * rad, y * rad, z * rad, UltDraw.r(col), UltDraw.g(col), UltDraw.b(col), 1F);
    }

    private static void quadTex(BufferBuilder b, Matrix4f m, Vec3 c, Vec3 ax, Vec3 up, float u0, int col, float a) {
        float r = UltDraw.r(col), g = UltDraw.g(col), bl = UltDraw.b(col);
        UltDraw.v(b, m, c.x - ax.x - up.x, c.y - ax.y - up.y, c.z - ax.z - up.z, u0, 1, r, g, bl, a);
        UltDraw.v(b, m, c.x + ax.x - up.x, c.y + ax.y - up.y, c.z + ax.z - up.z, u0 + 1, 1, r, g, bl, a);
        UltDraw.v(b, m, c.x + ax.x + up.x, c.y + ax.y + up.y, c.z + ax.z + up.z, u0 + 1, 0, r, g, bl, a);
        UltDraw.v(b, m, c.x - ax.x + up.x, c.y - ax.y + up.y, c.z - ax.z + up.z, u0, 0, r, g, bl, a);
    }

    // ---------------------------------------------------------------- su

    /** Dalgalar: {genlik, dalga boyu, yon x, yon z, hiz}. Dunyaya sabit. */
    private static final double[][] WAVES = {
            {0.14, 17.0, 0.30, 1.00, 0.9}, {0.09, 8.5, 1.00, 0.35, 0.7}, {0.05, 4.2, -0.65, 1.00, 0.55},
            {0.025, 2.1, 0.90, -0.70, 0.4}};
    private static final double[] HN = new double[3];

    /** Dalga yuksekligi; HN'ye normal yazar. fade: uzakta duzlesir (detay ornekleme frekansini asmasin). */
    private static double wave(double x, double z, float t, double fade) {
        double h = 0, dx = 0, dz = 0;
        for (double[] w : WAVES) {
            double l = Math.sqrt(w[2] * w[2] + w[3] * w[3]);
            double kx = w[2] / l * 2 * Math.PI / w[1], kz = w[3] / l * 2 * Math.PI / w[1];
            double ph = kx * x + kz * z - w[4] * t * 0.3;
            double a = w[0] * fade;
            h += a * Math.sin(ph);
            double c = a * Math.cos(ph);
            dx += c * kx;
            dz += c * kz;
        }
        double nl = Math.sqrt(dx * dx + 1 + dz * dz);
        HN[0] = -dx / nl;
        HN[1] = 1 / nl;
        HN[2] = -dz / nl;
        return h;
    }

    private static float[] GX = new float[0], GY = new float[0], GZ = new float[0], GS = new float[0], GF = new float[0];
    private static int[] GC = new int[0];

    /** Bir su kosesi: konum + renk (Fresnel'li gok yansimasi, derin su rengi, gunes parlamasi, uzak pus). */
    private static void waterVertex(UltScene.Ctx c, double x, double z, int idx) {
        double dist = Math.hypot(x - c.cam.x, z - c.cam.z);
        double fade = Mth.clamp(1.0 - (dist - 20.0) / 90.0, 0.0, 1.0); // uzakta sakin ve parlak (yansima)
        double y = wave(x, z, c.t, fade);
        double nx = HN[0], ny = HN[1], nz = HN[2];
        double vx = x - c.cam.x, vy = y - c.cam.y, vz = z - c.cam.z, vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
        vx /= vl; vy /= vl; vz /= vl;
        double dn = vx * nx + vy * ny + vz * nz;
        double cos = Math.max(0.0, -dn);
        double f1 = 1.0 - cos, f2 = f1 * f1;
        double fres = 0.02 + 0.98 * f2 * f2 * f1;
        double rx = vx - 2 * dn * nx, ry = Math.abs(vy - 2 * dn * ny), rz = vz - 2 * dn * nz;
        int refl = sky(rx, ry, rz);
        int body = UltDraw.mix(SHALLOW, DEEP, (float) Mth.clamp(cos * 1.4, 0, 1));
        int col = UltDraw.mix(body, refl, (float) Mth.clamp(fres * 1.15, 0, 1));
        double sd = Math.max(0.0, rx * SUN.x + ry * SUN.y + rz * SUN.z);
        double s2 = sd * sd, s4 = s2 * s2, s16 = s4 * s4 * s4 * s4;
        double spec = Math.pow(sd, 180.0) * 1.6 + s16 * s2 * 0.18;
        col = UltDraw.mix(col, 0xFFE6C0, (float) Mth.clamp(spec, 0, 1));
        col = UltDraw.mix(col, FAR_WATER, (float) Mth.clamp((dist - 300.0) / 2200.0, 0, 0.9)); // ufukta goge karisir
        // gunes yolu (duz yuzeyden yansima): parlama katmaninin yogunlugu; yakin detay solmasi
        double fs = Math.max(0.0, vx * SUN.x - vy * SUN.y + vz * SUN.z);
        double f4 = fs * fs * fs * fs;
        GS[idx] = (float) (f4 * f4 * f4 * f4 * f4 * f4 * f4); // ~^28
        GF[idx] = (float) Mth.clamp(1.0 - dist / DETAIL_R, 0.0, 1.0);
        GX[idx] = (float) x;
        GY[idx] = (float) y;
        GZ[idx] = (float) z;
        GC[idx] = col;
    }

    private static void emit(BufferBuilder b, Matrix4f m, int i) {
        int col = GC[i];
        UltDraw.c(b, m, GX[i], GY[i], GZ[i], UltDraw.r(col), UltDraw.g(col), UltDraw.b(col), 1F);
    }

    @Override
    public void geometry(UltScene.Ctx c) {
        Matrix4f m = c.m;
        double cx = c.cam.x, cz = c.cam.z;
        // su: kameraya merkezli kutupsal izgara (yakinda sik, uzakta seyrek). Her kose bir kez hesaplanir.
        int rings = Math.max(28, (int) (52 * c.quality)), slices = Math.max(64, (int) (128 * c.quality));
        double r0 = 0.4, grow = Math.pow(FAR / r0, 1.0 / rings);
        int nv = (rings + 1) * slices;
        if (GX.length < nv) {
            GX = new float[nv]; GY = new float[nv]; GZ = new float[nv]; GS = new float[nv]; GF = new float[nv]; GC = new int[nv];
        }
        for (int i = 0; i <= rings; i++) {
            double r = i == 0 ? 0.0 : r0 * Math.pow(grow, i);
            for (int j = 0; j < slices; j++) {
                double a = 2 * Math.PI * j / slices;
                waterVertex(c, cx + Math.cos(a) * r, cz + Math.sin(a) * r, i * slices + j);
            }
        }
        BufferBuilder b = UltDraw.begin(null, UltDraw.Blend.OPAQUE, true, true);
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < slices; j++) {
                int j1 = (j + 1) % slices;
                emit(b, m, i * slices + j);
                emit(b, m, i * slices + j1);
                emit(b, m, (i + 1) * slices + j1);
                emit(b, m, (i + 1) * slices + j);
            }
        }
        UltDraw.end();
        // yakin detay: ayni izgara koselerinde ince dalga sirtlari (dunyaya sabit UV) ve gunes yolunda parlamalar
        int near = 0;
        while (near < rings && r0 * Math.pow(grow, near + 1) < DETAIL_R) near++;
        float scroll = c.t * 0.004F;
        b = UltDraw.begin(UltTextures.RIPPLE, UltDraw.Blend.ALPHA, true, false);
        for (int i = 0; i < near; i++) {
            for (int j = 0; j < slices; j++) {
                int j1 = (j + 1) % slices;
                detail(b, m, i * slices + j, scroll, 0.42F, false);
                detail(b, m, i * slices + j1, scroll, 0.42F, false);
                detail(b, m, (i + 1) * slices + j1, scroll, 0.42F, false);
                detail(b, m, (i + 1) * slices + j, scroll, 0.42F, false);
            }
        }
        UltDraw.end();
        b = UltDraw.begin(UltTextures.GLITTER, UltDraw.Blend.ADD, true, false);
        float flick = c.t * 0.011F;
        for (int i = 0; i < near; i++) {
            for (int j = 0; j < slices; j++) {
                int j1 = (j + 1) % slices;
                detail(b, m, i * slices + j, flick, 1F, true);
                detail(b, m, i * slices + j1, flick, 1F, true);
                detail(b, m, (i + 1) * slices + j1, flick, 1F, true);
                detail(b, m, (i + 1) * slices + j, flick, 1F, true);
            }
        }
        UltDraw.end();
        wake(c);
    }

    /** Detay katmani kosesi (su izgarasinin ayni noktasi, biraz yukarida). glitter: gunes yolunda eklemeli parilti. */
    private static void detail(BufferBuilder b, Matrix4f m, int i, float scroll, float a, boolean glitter) {
        float u = GX[i] / 3.5F, v = GZ[i] / 3.5F + scroll;
        if (glitter) {
            float k = Math.min(1F, GS[i] * 2.2F) * GF[i];
            UltDraw.v(b, m, GX[i], GY[i] + 0.02, GZ[i], u * 0.6F + scroll, v * 0.6F, 1F, 0.92F, 0.75F, k);
        } else {
            UltDraw.v(b, m, GX[i], GY[i] + 0.015, GZ[i], u, v, 0.78F, 0.88F, 0.95F, a * GF[i] * GF[i]);
        }
    }

    // ---------------------------------------------------------------- iz: V su duvarlari, kopuk, damlalar

    private void wake(UltScene.Ctx c) {
        Matrix4f m = c.m;
        float t = c.vt;
        double hz = runnerZ(t), hy = runnerY(t);
        float lift = (float) Mth.clamp(1.0 - hy / 3.0, 0.0, 1.0); // havalaninca su efektleri soner
        int stride = c.quality < 0.5F ? 2 : 1;
        // kopuk izi: kosucunun arkasinda genisleyen beyaz serit (dunyaya sabit doku)
        BufferBuilder fo = UltDraw.begin(UltTextures.FOAM, UltDraw.Blend.ALPHA, false, false); // dalga tepeleri kesmesin
        double prevS = -1;
        for (int i = 0; i <= 60; i += stride) {
            double s = i * 1.5;
            if (prevS >= 0) {
                double z0 = hz - prevS, z1 = hz - s;
                double w0 = 0.35 + prevS * 0.09, w1 = 0.35 + s * 0.09;
                float a0 = (float) (0.85 * Math.exp(-prevS / 45.0)) * lift, a1 = (float) (0.85 * Math.exp(-s / 45.0)) * lift;
                UltDraw.v(fo, m, -w0, 0.06, z0, (float) (-w0 / 4.0), (float) (z0 / 6.0), 1F, 1F, 1F, a0);
                UltDraw.v(fo, m, w0, 0.06, z0, (float) (w0 / 4.0), (float) (z0 / 6.0), 1F, 1F, 1F, a0);
                UltDraw.v(fo, m, w1, 0.06, z1, (float) (w1 / 4.0), (float) (z1 / 6.0), 1F, 1F, 1F, a1);
                UltDraw.v(fo, m, -w1, 0.06, z1, (float) (-w1 / 4.0), (float) (z1 / 6.0), 1F, 1F, 1F, a1);
            }
            prevS = s;
        }
        UltDraw.end();
        // V su duvarlari: ayaklardan iki yana acilan, kosucunun arkasinda yukselip genisleyen buyuk su perdeleri
        // (yat dalgasi gibi). Dokusu dunyaya sabit: perde kosucuyla gider, icindeki su akar.
        int segs = Math.max(20, (int) (44 * c.quality));
        BufferBuilder b = UltDraw.begin(UltTextures.SPRAY, UltDraw.Blend.ALPHA, false, false);
        for (int layer = 0; layer < 3; layer++) { // arkadan one: dis/yuksek, orta, ic/alcak
            for (int side = -1; side <= 1; side += 2) {
                // gunes sag tarafta (side -1): o perde sicak ve parlak, oteki serin beyaz
                int col = side < 0 ? UltDraw.mix(0xFFFFFF, 0xFFD6A8, 0.45F - layer * 0.12F)
                        : UltDraw.mix(0xF6FAFF, 0xFFE2C4, 0.3F - layer * 0.1F);
                wallStrip(b, m, side, layer, hz, t, segs, UltDraw.r(col), UltDraw.g(col), UltDraw.b(col), lift, 1F);
            }
        }
        UltDraw.end();
        // gunes tarafindaki perdenin parlayan kenari (eklemeli)
        b = UltDraw.begin(UltTextures.SPRAY, UltDraw.Blend.ADD, false, false);
        wallStrip(b, m, -1, 1, hz, t, segs, 1F, 0.78F, 0.55F, lift, 0.35F);
        wallStrip(b, m, 1, 1, hz, t, segs, 0.85F, 0.75F, 0.7F, lift, 0.18F);
        UltDraw.end();
        // perdelerin dibinde kopuk seridi (perde ile su arasindaki keskin cizgiyi yumusatir)
        b = UltDraw.begin(UltTextures.FOAM, UltDraw.Blend.ALPHA, false, false);
        for (int side = -1; side <= 1; side += 2) {
            double ps = -1;
            for (int i = 0; i <= segs; i++) {
                double s = 0.12 + (WALL_LEN - 0.12) * Math.pow(i / (double) segs, 1.7);
                if (ps >= 0) {
                    double x0 = side * (0.22 + ps * V_TAN), x1 = side * (0.22 + s * V_TAN);
                    double w0 = 0.3 + ps * 0.06, w1 = 0.3 + s * 0.06;
                    float a0 = (float) (0.8 * fadeAlong(ps)) * lift, a1 = (float) (0.8 * fadeAlong(s)) * lift;
                    double z0 = hz - ps, z1 = hz - s;
                    UltDraw.v(b, m, x0 - side * w0, 0.05, z0, (float) (x0 / 5), (float) (z0 / 5), 1F, 1F, 1F, 0F);
                    UltDraw.v(b, m, x1 - side * w1, 0.05, z1, (float) (x1 / 5), (float) (z1 / 5), 1F, 1F, 1F, 0F);
                    UltDraw.v(b, m, x1, 0.05, z1, (float) ((x1 + 1) / 5), (float) (z1 / 5), 1F, 1F, 1F, a1);
                    UltDraw.v(b, m, x0, 0.05, z0, (float) ((x0 + 1) / 5), (float) (z0 / 5), 1F, 1F, 1F, a0);
                }
                ps = s;
            }
        }
        UltDraw.end();
        // perde tepelerinden disari savrulan ince serpinti (cok kucuk, yogun: sis gibi okunur)
        b = UltDraw.begin(UltTextures.GLOW, UltDraw.Blend.ALPHA, false, false);
        int mistN = Math.max(60, (int) (220 * c.quality));
        for (int i = 0; i < mistN; i++) {
            R.seed(c.s.seed ^ 0x5B4AL, i, 9);
            int side = (i & 1) == 0 ? -1 : 1;
            float life = 8F + 6F * R.next(), age = (t + R.next() * life) % life;
            double s = 0.8 + R.next() * (WALL_LEN * 0.65);
            double h = wallH(s, t, side);
            double up = h * (0.75 + 0.3 * R.next()) + age * 0.05, out = h * 0.5 + age * (0.06 + 0.08 * R.next());
            double x = side * (0.22 + s * V_TAN + out), z = hz - s;
            float u = age / life, size = (float) (0.08 + 0.12 * R.next() + 0.004 * s);
            UltDraw.billboard(b, m, x, up - age * age * 0.004, z, c.right, c.up, size, size, 0F,
                    side < 0 ? 0xFFF0DC : 0xF4F8FF, (float) (0.75 * (1F - u) * fadeAlong(s)) * lift);
        }
        UltDraw.end();
        // perdelerin tepesinde ve dibinde savrulan su sisi
        BufferBuilder mist = UltDraw.begin(UltTextures.CLOUD, UltDraw.Blend.ALPHA, false, false);
        int puffs = Math.max(6, (int) (12 * c.quality));
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < puffs; i++) {
                R.seed(c.s.seed ^ 0x3157L, i, side + 5);
                double s = 1.0 + (WALL_LEN - 4.0) * (i + R.next() * 0.8) / puffs;
                double h = wallH(s, t, side);
                double x = side * (0.25 + s * V_TAN + h * 0.55), z = hz - s;
                float size = (float) (1.2 + h * 0.55 + R.next());
                float a = (float) (0.13 * fadeAlong(s)) * lift;
                UltDraw.billboard(mist, m, x, h * (0.8 + 0.2 * R.next()), z, c.right, c.up, size * 1.4F, size, R.next() * 6F,
                        side < 0 ? 0xFFE8D0 : 0xEAF2F8, a);
            }
        }
        double ph = UltPoses.runPhase(t) / Math.PI; // her yarim dongu bir adim
        long step = (long) Math.floor(ph);
        for (long st = step; st > step - 6; st--) { // ayak dibinde kabaran su
            float born = t - (float) ((ph - st) * Math.PI / (2 * Math.PI * 9.0 / 20.0));
            float life = t - born;
            if (life < 0F || life > 12F) continue;
            R.seed(c.s.seed ^ 0x3158L, st, 2);
            double px = (st % 2 == 0 ? 0.2 : -0.2) + R.signed() * 0.3, pz = runnerZ(born) - 0.3 - life * 0.15;
            float u = life / 12F, size = 0.9F + 2.4F * u;
            UltDraw.billboard(mist, m, px, 0.25 + 0.5 * u, pz, c.right, c.up, size, size * 0.7F, R.next() * 3F, 0xF4F8FF,
                    0.35F * (1F - u) * lift);
        }
        UltDraw.end();
        // suya basilan yerde halkalar
        BufferBuilder rg = UltDraw.begin(UltTextures.GLOW, UltDraw.Blend.ALPHA, false, false);
        for (long st = step; st > step - 6; st--) {
            float born = t - (float) ((ph - st) * Math.PI / (2 * Math.PI * 9.0 / 20.0));
            float life = t - born;
            if (life < 0F || life > 10F) continue;
            float u = life / 10F;
            ringOnWater(rg, m, st % 2 == 0 ? 0.12 : -0.12, runnerZ(born), 0.2 + 1.4 * u, (1F - u) * 0.6F * lift);
        }
        UltDraw.end();
    }

    /** V perdelerinin yari acisinin tanjanti, perde boyu ve en yuksek yeri. */
    private static final double V_TAN = Math.tan(Math.toRadians(25.0)), WALL_LEN = 44.0, WALL_H = 6.2;

    /** Perde yuksekligi: ayagin dibinde sifir, birkac blokta tepe yuksekligine cikar, en geride alcalir; dalgalanir. */
    private static double wallH(double s, float t, int side) {
        double rise = 1.0 - Math.exp(-s / 4.5);
        double tail = 1.0 - smooth01((s - 26.0) / (WALL_LEN - 26.0));
        double wob = 0.8 + 0.1 * Math.sin(s * 0.45 - t * 0.3 + side) + 0.07 * Math.sin(s * 1.3 + t * 0.5 - side)
                + 0.05 * Math.sin(s * 3.1 - t * 0.9);
        return WALL_H * rise * tail * wob;
    }

    /** Perdenin boyunca opaklik: ayakta yumusak baslar, sonda soner. */
    private static double fadeAlong(double s) {
        return smooth01(s / 0.9) * (1.0 - smooth01((s - 28.0) / (WALL_LEN - 28.0)));
    }

    private static double smooth01(double u) {
        u = Mth.clamp(u, 0.0, 1.0);
        return u * u * (3 - 2 * u);
    }

    /**
     * Bir V perdesi: taban, govde ve tepe siralari; kesitte disari kivrilir (su disari savrulur). layer 0 dis/arka
     * (genis, yuksek, seffaf), 1 orta, 2 ic/on (alcak, yogun). Tepe yari seffaf: yirtik kenari doku verir.
     */
    private static void wallStrip(BufferBuilder b, Matrix4f m, int side, int layer, double hz, float t, int segs, float cr,
                                  float cg, float cb, float lift, float alphaScale) {
        double wide = layer == 0 ? 1.12 : (layer == 1 ? 1.0 : 0.92), tall = layer == 0 ? 1.22 : (layer == 1 ? 0.95 : 0.62);
        float alpha = (layer == 0 ? 0.5F : (layer == 1 ? 0.85F : 0.95F)) * lift * alphaScale;
        float uOff = layer * 0.37F + (side > 0 ? 0.5F : 0F) + t * 0.006F; // perde boyunca hafif akis
        double[] prev = null;
        for (int i = 0; i <= segs; i++) {
            double u = i / (double) segs;
            double s = 0.12 + (WALL_LEN - 0.12) * Math.pow(u, 1.7);   // ayaga yakin sik
            double h = wallH(s + layer * 1.7, t + layer * 9F, side) * tall;
            double xb = side * (0.22 + s * V_TAN * wide), z = hz - s;
            float a = (float) (alpha * fadeAlong(s));
            float tu = (float) (z / (7.0 + layer * 2.0)) + uOff;
            double[] cur = {xb, z, h, a, tu};
            if (prev != null) {
                for (int row = 0; row < 3; row++) {
                    vtx(b, m, prev, side, ROW_Y[row], ROW_O[row], ROW_V[row], ROW_A[row], cr, cg, cb);
                    vtx(b, m, cur, side, ROW_Y[row], ROW_O[row], ROW_V[row], ROW_A[row], cr, cg, cb);
                    vtx(b, m, cur, side, ROW_Y[row + 1], ROW_O[row + 1], ROW_V[row + 1], ROW_A[row + 1], cr, cg, cb);
                    vtx(b, m, prev, side, ROW_Y[row + 1], ROW_O[row + 1], ROW_V[row + 1], ROW_A[row + 1], cr, cg, cb);
                }
            }
            prev = cur;
        }
    }

    /** Perde kesiti: yukseklik orani, disari kivrilma (h orani), doku v, opaklik. */
    private static final double[] ROW_Y = {0.0, 0.22, 0.62, 1.0}, ROW_O = {0.0, 0.03, 0.2, 0.6};
    private static final float[] ROW_V = {1F, 0.8F, 0.42F, 0F}, ROW_A = {0.45F, 1F, 0.85F, 0.25F};

    private static void vtx(BufferBuilder b, Matrix4f m, double[] p, int side, double yk, double outK, float v, float ak,
                            float cr, float cg, float cb) {
        double h = p[2];
        float shade = (float) (0.9 + 0.1 * yk); // dipte biraz koyu (su govdesi), tepeye dogru isik alan beyaz
        UltDraw.v(b, m, p[0] + side * h * outK, 0.02 + h * yk, p[1], (float) p[4], v, cr * shade, cg * shade, cb * shade,
                (float) p[3] * ak);
    }

    private static void ringOnWater(BufferBuilder b, Matrix4f m, double x, double z, double r, float a) {
        if (a <= 0.01F) return;
        int seg = 16;
        double w = 0.12;
        for (int i = 0; i < seg; i++) {
            double a0 = 2 * Math.PI * i / seg, a1 = 2 * Math.PI * (i + 1) / seg;
            UltDraw.v(b, m, x + Math.cos(a0) * (r - w), 0.05, z + Math.sin(a0) * (r - w), 0.5F, 0.5F, 1F, 1F, 1F, a);
            UltDraw.v(b, m, x + Math.cos(a1) * (r - w), 0.05, z + Math.sin(a1) * (r - w), 0.5F, 0.5F, 1F, 1F, 1F, a);
            UltDraw.v(b, m, x + Math.cos(a1) * (r + w), 0.05, z + Math.sin(a1) * (r + w), 0F, 0F, 1F, 1F, 1F, 0F);
            UltDraw.v(b, m, x + Math.cos(a0) * (r + w), 0.05, z + Math.sin(a0) * (r + w), 0F, 0F, 1F, 1F, 1F, 0F);
        }
    }

    // ---------------------------------------------------------------- kosucu modeli + iz

    @Override
    public void model(UltScene.Ctx c) {
        float t = c.vt;
        float sp = (float) (runnerZ(t + 0.5F) - runnerZ(t - 0.5F));
        float spacing = Mth.clamp(0.8F / sp, 0.1F, 0.4F);
        UltScene.drawCaster(c, PATH, 0F, true, Math.max(2, (int) (4 * c.quality)), spacing, 0.3F, 0.9F, 0F);
        UltScene.drawTrail(c, PATH, T0 - 30F, 16, 1F, true, 1F);
    }
}
