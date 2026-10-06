package dev.baranhan.flashmod.client.render;

/** Deterministik rastgelelik + midpoint-displacement simsek uretimi. */
public final class Lightning {
    private Lightning() {}

    /** Hizli, tohumlanabilir RNG. Ayni tohum -> ayni simsek (kare titremesi olmadan). */
    public static final class Rng {
        private long s;

        public Rng(long seed) {
            seed(seed);
        }

        public Rng seed(long seed) {
            long z = seed + 0x9E3779B97F4A7C15L;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            s = (z ^ (z >>> 31)) | 1L;
            return this;
        }

        public Rng seed(long a, long b, long c) {
            return seed(a * 0x632BE59BD9B4E019L ^ b * 0x85157AF5L ^ c * 0x9E3779B97F4A7C15L);
        }

        /** 0..1 */
        public float next() {
            s ^= s << 13;
            s ^= s >>> 7;
            s ^= s << 17;
            return (s >>> 40) / (float) (1L << 24);
        }

        /** -1..1 */
        public float signed() {
            return next() * 2F - 1F;
        }

        public float range(float min, float max) {
            return min + (max - min) * next();
        }
    }

    /** Sureklilik icin [0,1) hash -> yumusak 1D gurultu (odometreye sabitlenmis dalgalar icin). */
    public static float hash(long seed, long i) {
        long z = seed * 0x9E3779B97F4A7C15L + i * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 31)) * 0x94D049BB133111EBL;
        z ^= z >>> 29;
        return (z >>> 40) / (float) (1L << 24);
    }

    /** Deger gurultusu, -1..1, smoothstep enterpolasyonlu. double: buyuk odometre degerlerinde hassasiyet korunur. */
    public static float noise(long seed, double t) {
        double fl = Math.floor(t);
        long i = (long) fl;
        float f = (float) (t - fl);
        f = f * f * (3F - 2F * f);
        float a = hash(seed, i), b = hash(seed, i + 1);
        return (a + (b - a) * f) * 2F - 1F;
    }

    private static final float[] TX = new float[257], TY = new float[257], TZ = new float[257];

    /**
     * Iki nokta arasinda zikzak simsek. levels=4 -> 17 nokta. flat=true iken sadece XY'de kirilir (GUI).
     * Sonucu out'a ekler (alpha = a0 -> a1 lineer).
     */
    public static void jag(Polyline out, Rng rng, float x0, float y0, float z0, float x1, float y1, float z1,
                           float roughness, int levels, boolean flat, float a0, float a1) {
        levels = Math.max(1, Math.min(8, levels));
        int n = 1 << levels;
        TX[0] = x0; TY[0] = y0; TZ[0] = z0;
        TX[n] = x1; TY[n] = y1; TZ[n] = z1;
        float len = (float) Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
        float off = len * roughness;
        for (int step = n; step > 1; step >>= 1) {
            int half = step >> 1;
            for (int i = 0; i < n; i += step) {
                int m = i + half;
                TX[m] = (TX[i] + TX[i + step]) * 0.5F + rng.signed() * off;
                TY[m] = (TY[i] + TY[i + step]) * 0.5F + rng.signed() * off;
                TZ[m] = flat ? 0F : (TZ[i] + TZ[i + step]) * 0.5F + rng.signed() * off;
            }
            off *= 0.55F;
        }
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            out.add(TX[i], TY[i], flat ? z0 : TZ[i], a0 + (a1 - a0) * t, 1F - 0.6F * t);
        }
    }
}
