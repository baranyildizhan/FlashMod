package dev.baranhan.flashmod.client.ultimate;

import com.mojang.blaze3d.platform.NativeImage;
import dev.baranhan.flashmod.FlashMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Prosedurel dokular: bulut, cizgi bandi, dikey kopuk, kopuk lekeleri, dalga lekeleri, gokyuzu fircasi, tunel
 * bantlari, yumusak hale. Hepsi beyaz/gri (renk vertex'ten), NativeImage ABGR.
 * Dunya: NASA Blue Marble Next Generation (Temmuz 2004, batimetrili), bulut katmani ve gece isiklari (NASA Visible
 * Earth, kamu malı) -> assets/flashmod/textures/ult/. Uzaktan titremesin diye mipmap'li yuklenir.
 */
public final class UltTextures {
    public static final ResourceLocation CLOUD = id("cloud"), STREAK = id("streak"), FOAM_V = id("foam_v"),
            FOAM = id("foam"), CAPS = id("caps"), SKY = id("sky"), TUNNEL = id("tunnel"), GLOW = id("glow"),
            EARTH = id("earth"), EARTH_CLOUDS = id("earth_clouds"), EARTH_NIGHT = id("earth_night"),
            RIPPLE = id("ripple"), GLITTER = id("glitter"), SPRAY = id("spray");
    private static boolean ready;

    private UltTextures() {}

    private static ResourceLocation id(String n) {
        return new ResourceLocation(FlashMod.MODID, "ult_" + n);
    }

    public static boolean ready() {
        return ready;
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
        reg(RIPPLE, 256, 256, (x, y, w, h) -> { // su yuzeyindeki ince dalga sirtlari (ruzgara dik uzamis)
            float nx = x / (float) w, ny = y / (float) h;
            float n = fbm(nx * 6F, ny * 16F, 4, 6, 101) * 0.65F + fbm(nx * 13F, ny * 5F, 3, 13, 111) * 0.35F;
            return gray(1F, Mth.clamp((n - 0.52F) * 3.2F, 0F, 1F));
        });
        reg(GLITTER, 128, 128, (x, y, w, h) -> { // gunes parlamasi icin seyrek parlak noktalar
            float hv = hash(x, y, 121), hv2 = hash(x / 2, y / 2, 131);
            float a = hv > 0.992F ? 1F : (hv2 > 0.985F ? 0.45F : 0F);
            return gray(1F, a);
        });
        reg(SPRAY, 128, 128, (x, y, w, h) -> { // V su perdesi: dikey su lifleri, yirtik tepe, tepede damlacik serpintisi
            float nx = x / (float) w, ny = y / (float) h;                      // ny = 0 tepe, 1 dip
            float edge = 0.08F + 0.42F * fbm(nx * 5F, 0.37F, 3, 5, 141);       // her sutunda farkli tepe
            float fib = fbm(nx * 22F, ny * 2.2F, 4, 22, 151);                  // dikey lifler
            float fine = fbm(nx * 48F, ny * 6F, 2, 48, 161);
            float body = Mth.clamp((fib - 0.3F) * 2.1F, 0F, 1F) * (0.75F + 0.25F * fine);
            float a;
            if (ny >= edge) {
                float in = Mth.clamp((ny - edge) / 0.18F, 0F, 1F);
                a = (0.45F + 0.55F * body) * (0.35F + 0.65F * in) * (0.75F + 0.25F * ny);
            } else {                                                            // tepenin ustu: seyrek damlalar
                float hv = hash(x, y, 171);
                a = hv > 0.965F ? 0.85F * (ny / Math.max(0.01F, edge)) : 0F;
            }
            return gray(0.82F + 0.18F * body, Mth.clamp(a, 0F, 1F));
        });
        loadMip(EARTH, "earth", false);
        loadMip(EARTH_CLOUDS, "earth_clouds", true);
        loadMip(EARTH_NIGHT, "earth_night", false);
    }

    /**
     * Mod dokusunu mipmap'li yukler. alphaFromLuma: gri tonlamayi beyaz + alfa'ya cevirir (bulut katmani).
     * Dosya okunamazsa duz renkli yedek doku (sahne yine calisir).
     */
    private static void loadMip(ResourceLocation loc, String file, boolean alphaFromLuma) {
        NativeImage img;
        ResourceLocation src = new ResourceLocation(FlashMod.MODID, "textures/ult/" + file + ".png");
        try (java.io.InputStream in = Minecraft.getInstance().getResourceManager().open(src)) {
            img = NativeImage.read(NativeImage.Format.RGBA, in);
        } catch (Exception ex) {
            img = new NativeImage(NativeImage.Format.RGBA, 4, 2, false);
            img.fillRect(0, 0, 4, 2, alphaFromLuma ? 0 : 0xFF804020);
        }
        if (alphaFromLuma) {
            for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
                int c = img.getPixelRGBA(x, y);
                int lum = ((c & 0xFF) * 3 + ((c >> 8) & 0xFF) * 5 + ((c >> 16) & 0xFF) * 2) / 10;
                img.setPixelRGBA(x, y, (lum << 24) | 0xFFFFFF);
            }
        }
        int levels = Math.max(0, Math.min(6, Integer.numberOfTrailingZeros(Math.min(img.getWidth(), img.getHeight())) - 1));
        Minecraft.getInstance().getTextureManager().register(loc, new MipTexture(img, levels));
    }

    /** Mipmap'li, tekrarli (boylam sarmasi icin), yumusak filtreli doku. */
    private static final class MipTexture extends net.minecraft.client.renderer.texture.AbstractTexture {
        MipTexture(NativeImage base, int levels) {
            NativeImage[] mips = net.minecraft.client.renderer.texture.MipmapGenerator.generateMipLevels(new NativeImage[]{base}, levels);
            com.mojang.blaze3d.platform.TextureUtil.prepareImage(getId(), levels, base.getWidth(), base.getHeight());
            for (int i = 0; i <= levels; i++) {
                mips[i].upload(i, 0, 0, 0, 0, mips[i].getWidth(), mips[i].getHeight(), true, false, levels > 0, true);
            }
        }

        @Override
        public void load(net.minecraft.server.packs.resources.ResourceManager rm) {}
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
