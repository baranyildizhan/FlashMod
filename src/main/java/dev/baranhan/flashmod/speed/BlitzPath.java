package dev.baranhan.flashmod.speed;

/**
 * BlitzScript'i degerlendirir. Ortak kod (sunucu son konumlari, istemci her kare her sey icin kullanir).
 * Butun egriler kubik Hermite: STOP anahtarlarinda hiz 0 (yumusak varis/kalkis), FLOW'da Catmull-Rom tegeti,
 * IMP'de darbeyle hizli cikis -> hicbir yerde ani kirilma/snap yok.
 */
public final class BlitzPath {
    /** Sahne cercevesi: orijin, ileri birim vektor (sag = (-fz, fx)), hizcinin baslangic konumu. */
    public record Frame(double ax, double ay, double az, float fx, float fz, double px, double py, double pz, long seed) {
        public double wx(double sx, double sz) { return ax - fz * sx + fx * sz; }
        public double wy(double sy) { return ay + sy; }
        public double wz(double sx, double sz) { return az + fx * sx + fz * sz; }
        /** Hizcinin baslangicinin sahne koordinatlari. */
        public double psx() { return (px - ax) * -fz + (pz - az) * fx; }
        public double psz() { return (px - ax) * fx + (pz - az) * fz; }
        /** Sahne yaw'i (0 = -z'ye bak, + saga) -> Minecraft yaw'i (derece). */
        public float worldYaw(float stageYawDeg) {
            double a = Math.toRadians(stageYawDeg);
            double dx = Math.sin(a), dz = -Math.cos(a);
            double wx = -fz * dx + fx * dz, wz = fx * dx + fz * dz;
            return (float) Math.toDegrees(Math.atan2(-wx, wz));
        }
    }

    private BlitzPath() {}

    // ---------------------------------------------------------------- hedef

    /** Hedefin sahne durumu: out = {x, y, z, yaw, egilme, yatma}. */
    public static void target(float t, double[] out) {
        hermite(BlitzScript.TARGET, t, 6, out);
    }

    // ---------------------------------------------------------------- hizci

    /** Tohumlu [0,1) (her blitz'de farkli ama sunucu/istemci ayni). */
    private static double hash(long seed, int i) {
        long z = seed * 0x9E3779B97F4A7C15L + i * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 31)) * 0x94D049BB133111EBL;
        z ^= z >>> 29;
        return (z >>> 11) * 0x1.0p-53;
    }

    /** Bir rota anahtarinin sahne konumu, "simdi" (now) anina gore (REL anahtarlar hedefi canli takip eder). */
    private static void keyPos(Frame f, float[] k, float now, double[] out) {
        keyPos(f, k, -1, now, out);
    }

    private static void keyPos(Frame f, float[] k, int index, float now, double[] out) {
        int mode = (int) k[4];
        double bx = 0, by = 0, bz = 0;
        if (mode == BlitzScript.REL || mode == BlitzScript.FIN) {
            double[] tg = new double[6]; // yerel: sunucu ve istemci ayni JVM'de paralel calisabilir
            hermite(BlitzScript.TARGET, mode == BlitzScript.FIN ? BlitzScript.FINAL_T : now, 6, tg);
            bx = tg[0]; bz = tg[2]; // yukseklik zemine gore kalir (hedef cokunce hizci yere gomulmesin)
        } else if (mode == BlitzScript.PLR && f != null) {
            bx = f.psx(); by = f.py() - f.ay(); bz = f.psz();
        }
        out[0] = bx + k[1];
        out[1] = by + k[2];
        out[2] = bz + k[3];
        // Ekran disi bekleme noktalarina tohumlu derinlik: her blitz'de giris/cikis acilari degisir
        if (mode == BlitzScript.ABS && Math.abs(k[1]) >= 12F && index >= 0 && f != null) {
            out[2] += (hash(f.seed(), index) - 0.5D) * 5.0D;
        }
    }

    /** Hizcinin sahne konumu t aninda. */
    public static void speedster(Frame f, float t, double[] out) {
        float[][] K = BlitzScript.RUN;
        if (t <= K[0][0]) { keyPos(f, K[0], 0, t, out); return; }
        int n = K.length;
        if (t >= K[n - 1][0]) { keyPos(f, K[n - 1], n - 1, t, out); return; }
        int i = 0;
        while (i + 1 < n && t >= K[i + 1][0]) i++;
        float t0 = K[i][0], t1 = K[i + 1][0];
        float dt = Math.max(1.0E-3F, t1 - t0);
        double u = (t - t0) / dt;
        double[] PA = new double[3], PB = new double[3];
        keyPos(f, K[i], i, t, PA);       // uc noktalar: simdiki ana gore
        keyPos(f, K[i + 1], i + 1, t, PB);
        double[] m0 = new double[3], m1 = new double[3];
        tangent(f, K, i, m0);
        tangent(f, K, i + 1, m1);
        double u2 = u * u, u3 = u2 * u;
        double h00 = 2 * u3 - 3 * u2 + 1, h10 = u3 - 2 * u2 + u, h01 = -2 * u3 + 3 * u2, h11 = u3 - u2;
        for (int a = 0; a < 3; a++) {
            out[a] = h00 * PA[a] + h10 * dt * m0[a] + h01 * PB[a] + h11 * dt * m1[a];
        }
    }

    private static void tangent(Frame f, float[][] K, int i, double[] m) {
        if ((int) K[i][5] != BlitzScript.FLOW || i == 0 || i == K.length - 1) {
            m[0] = m[1] = m[2] = 0;
            return;
        }
        double[] PC = new double[3], PD = new double[3];
        keyPos(f, K[i - 1], i - 1, K[i - 1][0], PC);
        keyPos(f, K[i + 1], i + 1, K[i + 1][0], PD);
        double span = Math.max(1.0E-3, K[i + 1][0] - K[i - 1][0]);
        for (int a = 0; a < 3; a++) m[a] = (PD[a] - PC[a]) / span;
    }

    /** Hizcinin dunya konumu. */
    public static void speedsterWorld(Frame f, float t, double[] out) {
        double[] s = new double[3];
        speedster(f, t, s);
        out[0] = f.wx(s[0], s[2]);
        out[1] = f.wy(s[1]);
        out[2] = f.wz(s[0], s[2]);
    }

    /** Hedefin dunya konumu + dunya yaw'i: out = {x, y, z, yaw, egilme, yatma}. */
    public static void targetWorld(Frame f, float t, double[] out) {
        double[] s = new double[6];
        target(t, s);
        out[0] = f.wx(s[0], s[2]);
        out[1] = f.wy(s[1]);
        out[2] = f.wz(s[0], s[2]);
        out[3] = f.worldYaw((float) s[3]);
        out[4] = s[4];
        out[5] = s[5];
    }

    // ---------------------------------------------------------------- genel Hermite (sabit sutunlu tablolar)

    /** table satiri: {t, v1..vN, tip}. N deger icin Hermite. */
    static void hermite(float[][] K, float t, int nv, double[] out) {
        int n = K.length;
        if (t <= K[0][0]) { for (int a = 0; a < nv; a++) out[a] = K[0][1 + a]; return; }
        if (t >= K[n - 1][0]) { for (int a = 0; a < nv; a++) out[a] = K[n - 1][1 + a]; return; }
        int i = 0;
        while (i + 1 < n && t >= K[i + 1][0]) i++;
        float t0 = K[i][0], t1 = K[i + 1][0];
        float dt = Math.max(1.0E-3F, t1 - t0);
        double u = (t - t0) / dt, u2 = u * u, u3 = u2 * u;
        double h00 = 2 * u3 - 3 * u2 + 1, h10 = u3 - 2 * u2 + u, h01 = -2 * u3 + 3 * u2, h11 = u3 - u2;
        int typ0 = (int) K[i][1 + nv], typ1 = (int) K[i + 1][1 + nv];
        for (int a = 0; a < nv; a++) {
            double p0 = K[i][1 + a], p1 = K[i + 1][1 + a];
            double m0, m1;
            // cikis tegeti (i)
            if (typ0 == BlitzScript.IMP) m0 = 2.4 * (p1 - p0) / dt;          // darbe: hizli baslar
            else if (typ0 == BlitzScript.FLOW && i > 0) m0 = (p1 - K[i - 1][1 + a]) / Math.max(1.0E-3, t1 - K[i - 1][0]);
            else m0 = 0;
            // giris tegeti (i+1)
            if (typ1 == BlitzScript.FLOW && i + 2 < n) m1 = (K[i + 2][1 + a] - p0) / Math.max(1.0E-3, K[i + 2][0] - t0);
            else m1 = 0;
            out[a] = h00 * p0 + h10 * dt * m0 + h01 * p1 + h11 * dt * m1;
        }
    }
}
