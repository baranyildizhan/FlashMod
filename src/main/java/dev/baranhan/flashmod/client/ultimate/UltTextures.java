package dev.baranhan.flashmod.client.ultimate;

import com.mojang.blaze3d.platform.NativeImage;
import dev.baranhan.flashmod.FlashMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Prosedurel dokular (dosya gerekmez): bulut, cizgi bandi, dikey kopuk, kopuk lekeleri, dalga lekeleri, gokyuzu
 * fircasi, tunel bantlari, yumusak hale, Dunya ve bulut katmani. Hepsi beyaz/gri (renk vertex'ten), NativeImage ABGR.
 * Dunya dokusu da prosedurel (telifli/fotografik asset yok); istenirse NASA Blue Marble ile degistirilebilir.
 */
public final class UltTextures {
    public static final ResourceLocation CLOUD = id("cloud"), STREAK = id("streak"), FOAM_V = id("foam_v"),
            FOAM = id("foam"), CAPS = id("caps"), SKY = id("sky"), TUNNEL = id("tunnel"), GLOW = id("glow"),
            EARTH = id("earth"), EARTH_CLOUDS = id("earth_clouds");
    private static boolean ready;

    private UltTextures() {}

    private static ResourceLocation id(String n) {
        return new ResourceLocation(FlashMod.MODID, "ult_" + n);
    }

    public static void ensure() {
        if (ready) return;
        ready = true;
        reg(CLOUD, 128, 128, (x, y, w, h) -> {
            float nx = x / (float) w, ny = y / (float) h;
            float r = Mth.sqrt((nx - 0.5F) * (nx - 0.5F) + (ny - 0.5F) * (ny - 0.5F)) * 2F;
            float mask = Mth.clamp(1F - r, 0F, 1F);
            float n = fbm(nx * 4F, ny * 4F, 5, 4, 11);
            return gray(1F, Mth.clamp((n * 1.4F - 0.25F) * mask * mask * 1.8F, 0F, 1F));
        });
        reg(STREAK, 256, 32, (x, y, w, h) -> {
            float ny = y / (float) h, nx = x / (float) w;
            float line = (float) Math.pow(Math.max(0F, Mth.sin((ny * 9F + hash(y * 7, 3, 5) * 0.3F) * (float) Math.PI)), 6);
            float ends = Mth.clamp(Math.min(nx, 1F - nx) * 5F, 0F, 1F);
            float band = 1F - Math.abs(ny - 0.5F) * 2F;
            return gray(1F, line * ends * band);
        });
        reg(FOAM_V, 64, 128, (x, y, w, h) -> {
            float nx = x / (float) w, ny = y / (float) h;
            float streak = fbm(nx * 16F, ny * 2F, 4, 16, 21);
            float top = Mth.clamp((1F - ny) * 1.3F, 0F, 1F); // y=0 ust: seffaf
            top = 1F - top * top;
            return gray(1F, Mth.clamp(streak * 1.5F - 0.2F, 0F, 1F) * Mth.clamp(ny * 1.2F, 0F, 1F) * (0.4F + 0.6F * top));
        });
        reg(FOAM, 128, 128, (x, y, w, h) -> {
            float n = fbm(x / 128F * 6F, y / 128F * 6F, 5, 6, 31);
            return gray(0.95F, Mth.clamp((n - 0.48F) * 4F, 0F, 1F));
        });
        reg(CAPS, 128, 128, (x, y, w, h) -> {
            float n = fbm(x / 128F * 12F, y / 128F * 12F, 3, 12, 41);
            return gray(1F, Mth.clamp((n - 0.62F) * 6F, 0F, 1F));
        });
        reg(SKY, 256, 32, (x, y, w, h) -> {
            float nx = x / (float) w, ny = y / (float) h;
            float n = fbm(nx * 3F, ny * 12F, 4, 3, 51);
            float edge = Mth.clamp(Math.min(nx, 1F - nx) * 4F, 0F, 1F) * (1F - Math.abs(ny - 0.5F) * 2F);
            return gray(1F, Mth.clamp(n * 1.6F - 0.3F, 0F, 1F) * edge);
        });
        reg(TUNNEL, 32, 256, (x, y, w, h) -> {
            float nx = x / (float) w, ny = y / (float) h;
            float b = fbm(nx * 8F, ny * 1F, 4, 8, 61);
            float stripe = 0.5F + 0.5F * Mth.sin(nx * (float) Math.PI * 2F * 6F + b * 4F);
            return gray(0.35F + 0.65F * stripe * b, 1F);
        });
        reg(GLOW, 64, 64, (x, y, w, h) -> {
            float nx = x / (float) (w - 1) - 0.5F, ny = y / (float) (h - 1) - 0.5F;
            float r = Mth.sqrt(nx * nx + ny * ny) * 2F;
            float a = Mth.clamp(1F / (1F + r * r * 18F) - 0.05F, 0F, 1F) * Mth.clamp((1F - r) * 3F, 0F, 1F);
            return gray(1F, a);
        });
        reg(EARTH, 512, 256, (x, y, w, h) -> {
            float lon = x / (float) w, lat = y / (float) h;
            float n = fbm(lon * 6F, lat * 3F, 6, 6, 71);
            float polar = Math.abs(lat - 0.5F) * 2F;
            if (polar > 0.86F) return rgb(0.92F, 0.95F, 0.98F, 1F);
            if (n > 0.53F) { // kara
                float d = Mth.clamp((n - 0.53F) * 5F, 0F, 1F);
                float dry = fbm(lon * 10F, lat * 6F, 3, 10, 81);
                return rgb(Mth.lerp(dry, 0.20F, 0.55F) * (1F - d * 0.2F), Mth.lerp(dry, 0.42F, 0.45F), Mth.lerp(dry, 0.16F, 0.28F), 1F);
            }
            float depth = Mth.clamp((0.53F - n) * 4F, 0F, 1F);
            return rgb(Mth.lerp(depth, 0.10F, 0.03F), Mth.lerp(depth, 0.30F, 0.12F), Mth.lerp(depth, 0.55F, 0.35F), 1F);
        });
        reg(EARTH_CLOUDS, 512, 256, (x, y, w, h) -> {
            float n = fbm(x / 512F * 8F, y / 256F * 4F, 6, 8, 91);
            return gray(1F, Mth.clamp((n - 0.5F) * 3F, 0F, 0.9F));
        });
    }

    private interface Px { int at(int x, int y, int w, int h); }

    private static void reg(ResourceLocation loc, int w, int h, Px f) {
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setPixelRGBA(x, y, f.at(x, y, w, h));
        DynamicTexture tex = new DynamicTexture(img);
        Minecraft.getInstance().getTextureManager().register(loc, tex);
        tex.bind();
        img.upload(0, 0, 0, 0, 0, w, h, true, false, false, false); // yumusak (linear), tekrarli
    }

    private static int gray(float v, float a) {
        return rgb(v, v, v, a);
    }

    /** ABGR. */
    private static int rgb(float r, float g, float b, float a) {
        int ri = (int) (Mth.clamp(r, 0F, 1F) * 255F), gi = (int) (Mth.clamp(g, 0F, 1F) * 255F),
                bi = (int) (Mth.clamp(b, 0F, 1F) * 255F), ai = (int) (Mth.clamp(a, 0F, 1F) * 255F);
        return (ai << 24) | (bi << 16) | (gi << 8) | ri;
    }

    private static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    /** Periyodik (tile'lanabilir) deger gurultusu. */
    private static float vnoise(float x, float y, int period, int seed) {
        int xi = Mth.floor(x), yi = Mth.floor(y);
        float fx = x - xi, fy = y - yi;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        int p = Math.max(1, period);
        int x0 = Math.floorMod(xi, p), x1 = Math.floorMod(xi + 1, p), y0 = Math.floorMod(yi, p), y1 = Math.floorMod(yi + 1, p);
        float a = hash(x0, y0, seed), b = hash(x1, y0, seed), c = hash(x0, y1, seed), d = hash(x1, y1, seed);
        return Mth.lerp(fy, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
    }

    private static float fbm(float x, float y, int oct, int period, int seed) {
        float sum = 0F, amp = 0.5F, norm = 0F;
        for (int i = 0; i < oct; i++) {
            sum += vnoise(x, y, period, seed + i * 17) * amp;
            norm += amp;
            x *= 2F;
            y *= 2F;
            period *= 2;
            amp *= 0.5F;
        }
        return sum / norm;
    }
}
