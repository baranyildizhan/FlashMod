package dev.baranhan.flashmod.skin;

/** Skin PNG dogrulamasi (iki tarafta ortak). */
public final class SkinData {
    /** Istemci -> sunucu paket siniri 32767 bayt; PNG icin pay birakilir. */
    public static final int MAX_BYTES = 30000;
    public static final int MAX_NAME = 64;

    private SkinData() {}

    /** {genislik, yukseklik} ya da gecersizse null. Kabul: 64x64, 64x32 (eski), 128x128, 256x256 (HD). */
    public static int[] validate(byte[] png) {
        if (png == null || png.length < 33 || png.length > MAX_BYTES) return null;
        int[] sig = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        for (int i = 0; i < 8; i++) if ((png[i] & 0xFF) != sig[i]) return null;
        if (png[12] != 'I' || png[13] != 'H' || png[14] != 'D' || png[15] != 'R') return null;
        int w = readInt(png, 16), h = readInt(png, 20);
        boolean ok = (w == 64 && (h == 64 || h == 32)) || (w == h && (w == 128 || w == 256));
        return ok ? new int[]{w, h} : null;
    }

    private static int readInt(byte[] b, int o) {
        return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }
}
