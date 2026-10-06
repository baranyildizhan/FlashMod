package dev.baranhan.flashmod.util;

import net.minecraft.util.Mth;

public final class ColorUtil {
    private ColorUtil() {}

    public static boolean isHex(Object o) {
        return o instanceof String s && s.matches("#?[0-9a-fA-F]{6}");
    }

    public static int parseHex(String s, int fallback) {
        if (s == null) return fallback;
        String t = s.startsWith("#") ? s.substring(1) : s;
        try {
            return Integer.parseInt(t, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static String toHex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    public static float r(int c) { return ((c >> 16) & 0xFF) / 255F; }
    public static float g(int c) { return ((c >> 8) & 0xFF) / 255F; }
    public static float b(int c) { return (c & 0xFF) / 255F; }

    public static int mix(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return (r << 16) | (g << 8) | bl;
    }

    /** h, s, v: 0..1 */
    public static int hsv(float h, float s, float v) {
        float hue = h - (float) Math.floor(h);
        return Mth.hsvToRgb(hue, Mth.clamp(s, 0F, 1F), Mth.clamp(v, 0F, 1F)) & 0xFFFFFF;
    }

    public static float[] toHsv(int rgb) {
        float r = r(rgb), g = g(rgb), b = b(rgb);
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d < 1.0E-6F) h = 0F;
        else if (max == r) h = ((g - b) / d) % 6F;
        else if (max == g) h = (b - r) / d + 2F;
        else h = (r - g) / d + 4F;
        h /= 6F;
        if (h < 0F) h += 1F;
        float s = max <= 0F ? 0F : d / max;
        return new float[]{h, s, max};
    }
}
