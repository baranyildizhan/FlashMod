package dev.baranhan.flashmod.client.ultimate;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.baranhan.flashmod.client.render.GlowDraw;
import dev.baranhan.flashmod.client.render.Lightning;
import dev.baranhan.flashmod.config.FlashClientConfig;
import dev.baranhan.flashmod.ultimate.UltimatePhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.joml.Matrix4f;

/**
 * Tam ekran overlay'ler (tek kayitli GUI overlay icinde, sirayla): yatay hiz cizgileri, letterbox ve combo yazisi
 * (opsiyonel), beyaz flas (en son), debug bilgisi. Sarj ve darbe efektleri dunyada, bizim simseklerle (UltWorldFx).
 */
public final class UltOverlay {
    private static final Lightning.Rng R = new Lightning.Rng(2024);

    private UltOverlay() {}

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        UltState s = UltDirector.full();
        if (s == null) return;
        float t = s.t(pt);
        boolean reduce = FlashClientConfig.ULT_REDUCE_FLASHES.get();
        if (t >= 0F && t < UltimatePhase.DURATION && s.abortAt < 0) {
            speedLines(g, s, t, w, h, reduce);
            if (FlashClientConfig.ULT_LETTERBOX.get()) letterbox(g, t, w, h);
            if (FlashClientConfig.ULT_COMBO_TEXT.get()) combo(g, t);
            float f = UltimatePhase.flash(t);
            if (reduce) f = Math.min(f, 0.35F);
            if (f > 0.002F) g.fill(0, 0, w, h, ((int) (f * 255F) << 24) | 0xFFFFFF);
        }
        if (s.abortAt >= 0 && Minecraft.getInstance().level != null) { // iptal: beyaza 0 -> 0.8 -> 0
            float u = (Minecraft.getInstance().level.getGameTime() - s.abortAt + pt) / 10F;
            float a = u < 0.4F ? u / 0.4F * 0.8F : Math.max(0F, 0.8F * (1F - (u - 0.4F) / 0.6F));
            if (a > 0.002F) g.fill(0, 0, w, h, ((int) (a * 255F) << 24) | 0xFFFFFF);
        }
        UltDebug.renderInfo(g, pt);
    }

    /** Yatay hiz cizgileri (kacis ve bosluktaki son hizlanma). */
    private static void speedLines(GuiGraphics g, UltState s, float t, int w, int h, boolean reduce) {
        float horiz = 0F;
        if (t >= 35F && t < UltimatePhase.DEPART.end) horiz = Mth.clamp((t - 35F) / 6F, 0F, 1F) * 0.35F;
        else if (t >= 96F && t < UltimatePhase.VOID.end) horiz = Mth.clamp((t - 96F) / 14F, 0F, 1F) * 0.8F;
        if (reduce) horiz *= 0.5F;
        if (horiz <= 0.01F) return;
        Matrix4f m = g.pose().last().pose();
        g.flush();
        BufferBuilder b = GlowDraw.beginGui();
        R.seed(s.seed ^ 0x11E5L);
        int n = (int) (70 * horiz * FlashClientConfig.ULT_QUALITY.get().mul) + 6;
        for (int i = 0; i < n; i++) {
            float y = R.next() * h, len = w * (0.1F + 0.3F * R.next()), th = 1F + 2F * R.next();
            float speed = w * (1.5F + R.next() * 2F);
            float x = ((R.next() * (w + len) + t / 20F * speed) % (w + len)) - len;
            float a = horiz * (0.2F + 0.4F * R.next());
            GlowDraw.gradQuad(b, m, x, y, x + len, y + th, 1F, 0.95F, 0.85F, 0F, 0F, a, a);
        }
        GlowDraw.endGui();
        RenderSystem.defaultBlendFunc();
    }

    private static void letterbox(GuiGraphics g, float t, int w, int h) {
        float end = UltimatePhase.DURATION;
        float k = t < 6F ? t / 6F : (t > end - 8F ? Math.max(0F, (end - t) / 8F) : 1F);
        int bar = Math.round(h * 0.07F * k);
        if (bar <= 0) return;
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
    }

    private static void combo(GuiGraphics g, float t) {
        float end = UltimatePhase.DURATION;
        int hits = t >= UltimatePhase.HIT3 ? 3 : t >= UltimatePhase.HIT2 ? 2 : (t >= UltimatePhase.HIT1 ? 1 : 0);
        if (hits == 0 || t > end) return;
        float a = t > end - 4F ? Math.max(0F, (end - t) / 4F) : 1F;
        String txt = hits + (hits == 1 ? " HIT" : " HITS");
        g.pose().pushPose();
        g.pose().scale(2F, 2F, 1F);
        g.drawString(Minecraft.getInstance().font, txt, 6, 20, ((int) (a * 255F) << 24) | 0xFFD27F, true);
        g.pose().popPose();
    }
}
