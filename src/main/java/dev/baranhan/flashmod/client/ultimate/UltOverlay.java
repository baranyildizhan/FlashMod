package dev.baranhan.flashmod.client.ultimate;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.baranhan.flashmod.FlashMod;
import dev.baranhan.flashmod.client.render.BodyPoseCapture;
import dev.baranhan.flashmod.client.render.GlowDraw;
import dev.baranhan.flashmod.client.render.Lightning;
import dev.baranhan.flashmod.config.FlashClientConfig;
import dev.baranhan.flashmod.ultimate.UltimatePhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Tam ekran overlay'ler (tek kayitli GUI overlay icinde, sirayla): aura isinlari (WINDUP), hiz cizgileri (yatay /
 * radyal), letterbox ve combo yazisi (opsiyonel), beyaz flas (en son), debug bilgisi.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class UltOverlay {
    private static final Matrix4f PROJ_VIEW = new Matrix4f();
    private static Vec3 cam = Vec3.ZERO;
    private static final Lightning.Rng R = new Lightning.Rng(2024);
    private static final Vector4f TMP = new Vector4f();

    private UltOverlay() {}

    /** Dunya -> ekran izdusumu icin bu karenin projeksiyon x gorus matrisi. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        PROJ_VIEW.set(event.getProjectionMatrix()).mul(BodyPoseCapture.view());
        cam = event.getCamera().getPosition();
    }

    /** Ekran koordinati (GUI olcekli) ya da kameranin arkasindaysa null. */
    private static float[] project(Vec3 w, int sw, int sh) {
        TMP.set((float) (w.x - cam.x), (float) (w.y - cam.y), (float) (w.z - cam.z), 1F);
        PROJ_VIEW.transform(TMP);
        if (TMP.w() <= 0.01F) return null;
        float nx = TMP.x() / TMP.w(), ny = TMP.y() / TMP.w();
        return new float[]{(nx * 0.5F + 0.5F) * sw, (1F - (ny * 0.5F + 0.5F)) * sh};
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        UltState s = UltDirector.full();
        if (s == null) return;
        float t = s.t(pt);
        boolean reduce = FlashClientConfig.ULT_REDUCE_FLASHES.get();
        if (t >= 0F && t < 200F && s.abortAt < 0) {
            aura(g, s, t, pt, w, h);
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

    private static void aura(GuiGraphics g, UltState s, float t, float pt, int w, int h) {
        if (t < 10F || t >= 22F) return;
        float k = (t - 10F) / 12F;
        Player p = s.caster();
        float cx = w * 0.5F, cy = h * 0.4F;
        if (p != null) {
            float[] sp = project(p.getPosition(pt).add(0, 1.6, 0), w, h);
            if (sp != null) { cx = sp[0]; cy = sp[1]; }
        }
        int col = GlowDraw.mixRgb(0xFFFFFF, s.glow, 0.35F);
        Matrix4f m = g.pose().last().pose();
        g.flush();
        BufferBuilder b = GlowDraw.beginGui();
        GlowDraw.gradQuad(b, m, 0, 0, w, h, 1F, 0.86F, 0.88F, 0.25F * k, 0.25F * k, 0.25F * k, 0.25F * k);
        float rot = (t / 20F) * 8F * 0.017453292F;
        R.seed(s.seed ^ 0xA0A0L, (long) (t / 2), 4);
        float diag = Mth.sqrt(w * w + h * h);
        for (int i = 0; i < 24; i++) {
            float ang = rot + 6.2832F * i / 24F + R.signed() * 0.05F;
            float len = diag * (0.45F + 0.4F * R.next());
            float half = 0.035F;
            float x1 = cx + Mth.cos(ang - half) * len, y1 = cy + Mth.sin(ang - half) * len;
            float x2 = cx + Mth.cos(ang + half) * len, y2 = cy + Mth.sin(ang + half) * len;
            float a = 0.55F * k;
            b.vertex(m, cx, cy, 0F).color(GlowDraw.cr(col), GlowDraw.cg(col), GlowDraw.cb(col), a).endVertex();
            b.vertex(m, x1, y1, 0F).color(GlowDraw.cr(col), GlowDraw.cg(col), GlowDraw.cb(col), 0F).endVertex();
            b.vertex(m, x2, y2, 0F).color(GlowDraw.cr(col), GlowDraw.cg(col), GlowDraw.cb(col), 0F).endVertex();
            b.vertex(m, cx, cy, 0F).color(GlowDraw.cr(col), GlowDraw.cg(col), GlowDraw.cb(col), a).endVertex();
        }
        GlowDraw.endGui();
    }

    private static void speedLines(GuiGraphics g, UltState s, float t, int w, int h, boolean reduce) {
        float horiz = 0F;
        if (t >= 26F && t < 40F) horiz = Mth.clamp((t - 26F) / 8F, 0F, 1F) * 0.8F;
        else if (t >= 66F && t < 86F) horiz = Mth.clamp((t - 66F) / 14F, 0F, 1F);
        float radial = t >= 158F && t < 166F ? 1F - (t - 158F) / 8F : 0F;
        if (horiz <= 0.01F && radial <= 0.01F) return;
        Matrix4f m = g.pose().last().pose();
        g.flush();
        BufferBuilder b = GlowDraw.beginGui();
        if (horiz > 0.01F) {
            R.seed(s.seed ^ 0x11E5L);
            int n = (int) (70 * horiz * FlashClientConfig.ULT_QUALITY.get().mul) + 6;
            for (int i = 0; i < n; i++) {
                float y = R.next() * h, len = w * (0.1F + 0.3F * R.next()), th = 1F + 2F * R.next();
                float speed = w * (1.5F + R.next() * 2F);
                float x = ((R.next() * (w + len) + t / 20F * speed) % (w + len)) - len;
                float a = horiz * (0.2F + 0.4F * R.next());
                GlowDraw.gradQuad(b, m, x, y, x + len, y + th, 1F, 0.95F, 0.85F, 0F, 0F, a, a);
            }
        }
        if (radial > 0.01F) {
            Player p = s.caster();
            float[] c = project(s.contact(), w, h);
            float cx = c != null ? c[0] : w * 0.5F, cy = c != null ? c[1] : h * 0.5F;
            int per = reduce ? 7 : 2;
            R.seed(s.seed ^ 0x9AD1L, (long) (t / per), 6);
            float a0 = radial * (reduce ? 0.4F : 1F);
            int warm = GlowDraw.mixRgb(0xFFFFFF, 0xFFA040, 0.6F);
            for (int i = 0; i < 48; i++) {
                float ang = R.next() * 6.2832F, th = 2F + 4F * R.next();
                float r0 = h * 0.08F, r1 = h * 1.2F;
                float dx = Mth.cos(ang), dy = Mth.sin(ang), px = -dy * th * 0.5F, py = dx * th * 0.5F;
                float x0 = cx + dx * r0, y0 = cy + dy * r0, x1 = cx + dx * r1, y1 = cy + dy * r1;
                b.vertex(m, x0 - px, y0 - py, 0F).color(1F, 1F, 1F, a0).endVertex();
                b.vertex(m, x1 - px * 3, y1 - py * 3, 0F).color(GlowDraw.cr(warm), GlowDraw.cg(warm), GlowDraw.cb(warm), 0F).endVertex();
                b.vertex(m, x1 + px * 3, y1 + py * 3, 0F).color(GlowDraw.cr(warm), GlowDraw.cg(warm), GlowDraw.cb(warm), 0F).endVertex();
                b.vertex(m, x0 + px, y0 + py, 0F).color(1F, 1F, 1F, a0).endVertex();
            }
            if (p == null) R.next();
        }
        GlowDraw.endGui();
        RenderSystem.defaultBlendFunc();
    }

    private static void letterbox(GuiGraphics g, float t, int w, int h) {
        float k = t < 6F ? t / 6F : (t > 192F ? Math.max(0F, (200F - t) / 8F) : 1F);
        int bar = Math.round(h * 0.07F * k);
        if (bar <= 0) return;
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
    }

    private static void combo(GuiGraphics g, float t) {
        int hits = t >= 158F ? 2 : (t >= 26F ? 1 : 0);
        if (hits == 0 || t > 200F) return;
        float a = t > 196F ? Math.max(0F, (200F - t) / 4F) : 1F;
        String txt = hits + (hits == 1 ? " HIT" : " HITS");
        g.pose().pushPose();
        g.pose().scale(2F, 2F, 1F);
        g.drawString(Minecraft.getInstance().font, txt, 6, 20, ((int) (a * 255F) << 24) | 0xFFD27F, true);
        g.pose().popPose();
    }
}
