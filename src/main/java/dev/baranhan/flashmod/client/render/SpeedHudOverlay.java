package dev.baranhan.flashmod.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.baranhan.flashmod.client.ClientSpeedsters;
import dev.baranhan.flashmod.config.FlashClientConfig;
import dev.baranhan.flashmod.speed.PhaseHelper;
import dev.baranhan.flashmod.speed.SpeedsterData;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.joml.Matrix4f;

import java.util.Locale;

/** Birinci sahis hiz efektleri (kenar parlamasi + radyal hiz cizgileri) ve sol ust HUD. */
public final class SpeedHudOverlay {
    private static final Lightning.Rng R = new Lightning.Rng(3);
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float MACH_KMH = 1235F;

    private SpeedHudOverlay() {}

    public static void renderEffects(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !FlashClientConfig.SCREEN_EFFECTS.get()) return;
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON || mc.gameRenderer.getMainCamera().isDetached()) return;
        ClientSpeedsters.Entry e = ClientSpeedsters.get(mc.player.getUUID());
        if (e == null) return;
        float k = e.intensity(pt);
        if (k < 0.12F) return;
        float s = (k - 0.12F) / 0.88F;
        float od = e.overdrive(pt);

        g.flush();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder buf = GlowDraw.beginGui();

        float edge = Math.min(w, h) * (0.12F + 0.1F * s);
        float a = (0.2F * s + 0.1F * od) * (0.5F + 0.5F * FlashClientConfig.BLOOM.get().floatValue());
        float gr = GlowDraw.cr(e.glow), gg = GlowDraw.cg(e.glow), gb = GlowDraw.cb(e.glow);
        GlowDraw.gradQuad(buf, m, 0, 0, w, edge, gr, gg, gb, a, 0F, 0F, a);
        GlowDraw.gradQuad(buf, m, 0, h - edge, w, h, gr, gg, gb, 0F, a, a, 0F);
        GlowDraw.gradQuad(buf, m, 0, 0, edge, h, gr, gg, gb, a, a, 0F, 0F);
        GlowDraw.gradQuad(buf, m, w - edge, 0, w, h, gr, gg, gb, 0F, 0F, a, a);

        float cx = w * 0.5F, cy = h * 0.5F;
        float maxR = (float) Math.sqrt(cx * cx + cy * cy);
        float time = Util.getMillis() / 50F;
        int lines = 12 + (int) (44 * s) + (int) (40 * od);
        int mid = GlowDraw.mixRgb(e.core, e.glow, 0.5F);
        float mr = GlowDraw.cr(mid), mg = GlowDraw.cg(mid), mb = GlowDraw.cb(mid);
        for (int i = 0; i < lines; i++) {
            R.seed(e.seed, i, 0x11E5);
            float ang = R.next() * TAU;
            float off = R.next();
            float spd = R.range(0.6F, 1.4F);
            float len = R.range(0.06F, 0.2F) * maxR * (0.4F + s + od);
            float width = R.range(0.5F, 1.6F);
            float t = off + time * 0.06F * spd * (0.6F + s + od * 2F);
            t -= (float) Math.floor(t);
            float r0 = Mth.lerp(t, 0.32F, 1.05F) * maxR;
            float la = s * 0.55F * Mth.sin(t * (float) Math.PI);
            float c = Mth.cos(ang), sn = Mth.sin(ang);
            GlowDraw.segment(buf, m, cx + c * r0, cy + sn * r0, 0F, cx + c * (r0 + len), cy + sn * (r0 + len), 0F,
                    width, mr, mg, mb, 0F, la, true);
        }
        GlowDraw.endGui();
    }

    /** Hiz yazisi: km/h, ses hizinin ustunde Mach, cok ustunde kisaltilmis. */
    private static String speedText(float bpt) {
        float kmh = bpt * 20F * 3.6F;
        if (kmh < MACH_KMH) return Math.round(kmh) + " km/h";
        float mach = kmh / MACH_KMH;
        if (mach < 100F) return String.format(Locale.ROOT, "Mach %.1f", mach);
        if (kmh < 1.0E6F) return String.format(Locale.ROOT, "%,d km/h", Math.round(kmh));
        return String.format(Locale.ROOT, "%.2fM km/h", kmh / 1.0E6F);
    }

    public static void renderHud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !FlashClientConfig.SHOW_HUD.get()) return;
        ClientSpeedsters.Entry e = ClientSpeedsters.get(mc.player.getUUID());
        if (e == null || !e.active) return;

        int x = 6, y = 6, pw = 128, ph = 30;
        float odf = e.overdriveFactor();
        boolean overdrive = odf > 1.15F && mc.player.isSprinting();
        boolean phasing = PhaseHelper.clientLocalPhasing;
        int extra = (overdrive || phasing) ? 11 : 0;
        g.fill(x, y, x + pw, y + ph + extra, 0x90000000);
        g.fill(x, y + ph + extra - 1, x + pw, y + ph + extra, 0xFF000000 | e.glow);

        float spd = Mth.lerp(pt, e.prevSpeed, e.speed);
        g.drawString(mc.font, Component.translatable("hud.flashmod.level", e.level), x + 5, y + 5,
                0xFF000000 | e.core, true);
        String speed = speedText(spd);
        g.drawString(mc.font, speed, x + pw - 5 - mc.font.width(speed), y + 5, 0xFFFFFFFF, true);

        int segW = 9, segH = 5, gap = 2, sx = x + 5, sy = y + 18;
        for (int i = 0; i < SpeedsterData.MAX_LEVEL; i++) {
            int x0 = sx + i * (segW + gap);
            g.fill(x0, sy, x0 + segW, sy + segH, i < e.level ? 0xFF000000 | GlowDraw.mixRgb(e.glow, 0, 0.35F) : 0x40FFFFFF);
        }

        float pulse = 0.75F + 0.25F * Mth.sin(Util.getMillis() / 180F);
        if (overdrive || phasing) {
            int ty = y + ph - 1;
            if (overdrive) {
                String txt = String.format(Locale.ROOT, "OVERDRIVE x%s",
                        odf < 100F ? String.format(Locale.ROOT, "%.1f", odf) : String.valueOf(Math.round(odf)));
                int col = GlowDraw.mixRgb(e.glow, 0xFFFFFF, 0.25F + 0.25F * pulse);
                g.drawString(mc.font, txt, x + 5, ty, 0xFF000000 | col, true);
            }
            if (phasing) {
                Component txt = Component.translatable("hud.flashmod.phasing");
                int jx = Math.round(PhaseRenderer.jitter(mc.player.getId(), 99) * 1.5F);
                g.drawString(mc.font, txt, x + pw - 5 - mc.font.width(txt) + jx, ty, 0xFFE8F4FF, true);
            }
        }

        // Speed Force enerjisi + mizrak sarji
        int ey = y + ph + extra + 3, ew = pw, eh = 4;
        g.fill(x, ey, x + ew, ey + eh, 0x90000000);
        int fillW = Math.round((ew - 2) * Mth.clamp(e.energy / 100F, 0F, 1F));
        g.fill(x + 1, ey + 1, x + 1 + fillW, ey + eh - 1, 0xFF000000 | GlowDraw.mixRgb(e.glow, 0, 0.25F));
        if (e.charging) {
            int cw = Math.round((ew - 2) * Mth.clamp(e.charge / 100F, 0F, 1F));
            g.fill(x + 1, ey + 1, x + 1 + cw, ey + eh - 1, 0xFF000000 | GlowDraw.mixRgb(e.core, 0xFFFFFF, 0.3F));
        }

        g.flush();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder buf = GlowDraw.beginGui();
        int hot = GlowDraw.mixRgb(e.core, 0xFFFFFF, 0.3F);
        GlowDraw.softRect(buf, m, x + 1, ey + 1, x + 1 + fillW, ey + eh - 1, 2F,
                GlowDraw.cr(e.glow), GlowDraw.cg(e.glow), GlowDraw.cb(e.glow), 0.35F * pulse);
        for (int i = 0; i < e.level; i++) {
            int x0 = sx + i * (segW + gap);
            GlowDraw.softRect(buf, m, x0, sy, x0 + segW, sy + segH, 3F,
                    GlowDraw.cr(e.glow), GlowDraw.cg(e.glow), GlowDraw.cb(e.glow), 0.45F * pulse);
            GlowDraw.softRect(buf, m, x0 + 1, sy + 1, x0 + segW - 1, sy + segH - 1, 1F,
                    GlowDraw.cr(hot), GlowDraw.cg(hot), GlowDraw.cb(hot), 0.7F);
        }
        GlowDraw.endGui();
    }
}
