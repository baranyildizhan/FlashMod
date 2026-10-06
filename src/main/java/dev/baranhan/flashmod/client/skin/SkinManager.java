package dev.baranhan.flashmod.client.skin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.baranhan.flashmod.FlashMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Istemci tarafi skin'ler: sunucudan gelen PNG'leri dokuya cevirir (oyuncu basina), kostum dolabi onizlemesini
 * tutar ve .minecraft/flashskins klasorunu yonetir. AbstractClientPlayerMixin dokuyu/model tipini buradan alir.
 */
public final class SkinManager {
    public record Skin(ResourceLocation texture, boolean slim, String name) {}

    private static final Map<UUID, Skin> SKINS = new HashMap<>();
    private static final ResourceLocation PREVIEW_TEX = new ResourceLocation(FlashMod.MODID, "skin_preview");

    // kostum dolabi onizlemesi (sadece yerel oyuncu, ekran acikken)
    private static boolean previewActive;
    @Nullable private static ResourceLocation previewTexture;
    private static boolean previewSlim;

    private SkinManager() {}

    // ---------------------------------------------------------------- klasor

    public static Path folder() {
        Path dir = FMLPaths.GAMEDIR.get().resolve("flashskins");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir;
    }

    public static List<Path> listSkins() {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(folder())) {
            s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()))
                    .forEach(out::add);
        } catch (IOException ignored) {
        }
        return out;
    }

    // ---------------------------------------------------------------- oyuncu skin'leri

    @Nullable
    public static Skin get(UUID id) {
        return SKINS.get(id);
    }

    /** Sunucudan gelen skin (bos dizi = varsayilan skin'e don). */
    public static void apply(UUID id, byte[] png, boolean slim, String name) {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation loc = new ResourceLocation(FlashMod.MODID, "skins/" + id.toString().replace("-", ""));
        Skin old = SKINS.remove(id);
        if (old != null) mc.getTextureManager().release(old.texture());
        if (png == null || png.length == 0) return;
        NativeImage img = decode(png);
        if (img == null) return;
        mc.getTextureManager().register(loc, new DynamicTexture(img));
        SKINS.put(id, new Skin(loc, slim, name));
    }

    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (Skin s : SKINS.values()) mc.getTextureManager().release(s.texture());
        SKINS.clear();
        endPreview();
    }

    // ---------------------------------------------------------------- onizleme

    /** Kostum dolabinda secilen skin'i yerel oyuncuya gecici olarak giydir (null = varsayilan skin). */
    public static void preview(@Nullable NativeImage img, boolean slim) {
        Minecraft mc = Minecraft.getInstance();
        mc.getTextureManager().release(PREVIEW_TEX);
        previewActive = true;
        previewSlim = slim;
        if (img == null) {
            previewTexture = null;
            return;
        }
        mc.getTextureManager().register(PREVIEW_TEX, new DynamicTexture(img));
        previewTexture = PREVIEW_TEX;
    }

    public static void setPreviewSlim(boolean slim) {
        previewSlim = slim;
    }

    public static void endPreview() {
        if (previewActive) Minecraft.getInstance().getTextureManager().release(PREVIEW_TEX);
        previewActive = false;
        previewTexture = null;
    }

    public static boolean previewActive() {
        return previewActive;
    }

    @Nullable
    public static ResourceLocation previewTexture() {
        return previewTexture;
    }

    public static boolean previewSlim() {
        return previewSlim;
    }

    // ---------------------------------------------------------------- goruntu

    /** PNG -> 64x64 (veya kare HD) NativeImage; eski 64x32 skin'ler vanilla gibi 64x64'e cevrilir. */
    @Nullable
    public static NativeImage decode(byte[] png) {
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
            if (img.getWidth() == 64 && img.getHeight() == 32) {
                NativeImage full = new NativeImage(64, 64, true);
                full.copyFrom(img);
                img.close();
                full.fillRect(0, 32, 64, 32, 0);
                full.copyRect(4, 16, 16, 32, 4, 4, true, false);
                full.copyRect(8, 16, 16, 32, 4, 4, true, false);
                full.copyRect(0, 20, 24, 32, 4, 12, true, false);
                full.copyRect(4, 20, 16, 32, 4, 12, true, false);
                full.copyRect(8, 20, 8, 32, 4, 12, true, false);
                full.copyRect(12, 20, 16, 32, 4, 12, true, false);
                full.copyRect(44, 16, -8, 32, 4, 4, true, false);
                full.copyRect(48, 16, -8, 32, 4, 4, true, false);
                full.copyRect(40, 20, 0, 32, 4, 12, true, false);
                full.copyRect(44, 20, -8, 32, 4, 12, true, false);
                full.copyRect(48, 20, -16, 32, 4, 12, true, false);
                full.copyRect(52, 20, -8, 32, 4, 12, true, false);
                return full;
            }
            if (img.getWidth() != img.getHeight()) {
                img.close();
                return null;
            }
            return img;
        } catch (IOException | IllegalArgumentException ex) {
            return null;
        }
    }

    /** Ince kol (Alex) tahmini: sag kolun 4. piksel sutunu seffafsa ince. */
    public static boolean guessSlim(NativeImage img) {
        int s = img.getWidth() / 64;
        if (img.getHeight() < 32 * s) return false;
        return ((img.getPixelRGBA(54 * s, 20 * s) >>> 24) & 0xFF) == 0;
    }
}
