package dev.baranhan.flashmod.client.skill;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.baranhan.flashmod.client.ClientSpeedsters;
import dev.baranhan.flashmod.client.render.GlowDraw;
import dev.baranhan.flashmod.client.render.Lightning;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.joml.Matrix4f;

import java.util.Locale;

/**
 * Geri sarma ekran ustu (yalnizca geri saran oyuncu): kenarlarda renkli hale, ekrani kaplayan ince ve ters donen
 * saat kadrani (60 cizgi, geriye kosan ibre), merkeze dogru akan (ters) hiz cizgileri, sol ustte yanip sonen
 * "◀◀" bant gostergesi ve sag ustte geriye sayan zaman kodu; bitiste kisa beyaz parlama. Goruntu bozulmasi
 * (bant izleri, renk kaymasi, tarama cizgileri) SpeedPostEffect'te.
 */
public final class SkillOverlay {
    private static final Lightning.Rng R = new Lightning.Rng(404);

    private SkillOverlay() {}

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        float a = SkillClient.rewindAmount(pt);
        float flash = SkillClient.rewindFlash(pt);
        if (a <= 0.003F && flash <= 0.003F) return;
        ClientSpeedsters.Entry e = ClientSpeedsters.get(mc.player.getUUID());
        int core = e != null ? e.core : 0xFFF3C4, glow = e != null ? e.glow : 0xFF9A1A;
        float t = mc.level.getGameTime() + pt;
        float gr = GlowDraw.cr(glow), gg = GlowDraw.cg(glow), gb = GlowDraw.cb(glow);
        int hot = GlowDraw.mixRgb(core, 0xFFFFFF, 0.5F);
        float hr = GlowDraw.cr(hot), hg = GlowDraw.cg(hot), hb = GlowDraw.cb(hot);

        g.flush();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder buf = GlowDraw.beginGui();
        if (a > 0.003F) {
            // kenar halesi
            float edge = Math.min(w, h) * 0.2F, ea = 0.28F * a;
            GlowDraw.gradQuad(buf, m, 0, 0, w, edge, gr, gg, gb, ea, 0F, 0F, ea);
            GlowDraw.gradQuad(buf, m, 0, h - edge, w, h, gr, gg, gb, 0F, ea, ea, 0F);
            GlowDraw.gradQuad(buf, m, 0, 0, edge, h, gr, gg, gb, ea, ea, 0F, 0F);
            GlowDraw.gradQuad(buf, m, w - edge, 0, w, h, gr, gg, gb, 0F, 0F, ea, ea);

            // ters donen saat kadrani
            float cx = w * 0.5F, cy = h * 0.5F, rad = h * 0.36F;
            float rot = -t * 0.12F;
            int segs = 96;
            for (int i = 0; i < segs; i++) {
                float a0 = rot + i * Mth.TWO_PI / segs, a1 = rot + (i + 1) * Mth.TWO_PI / segs;
                GlowDraw.segment(buf, m, cx + Mth.cos(a0) * rad, cy + Mth.sin(a0) * rad, 0F, cx + Mth.cos(a1) * rad,
                        cy + Mth.sin(a1) * rad, 0F, 2.2F, hr, hg, hb, 0.3F * a, 0.3F * a, true);
            }
            for (int i = 0; i < 60; i++) {
                float an = rot + i * Mth.TWO_PI / 60F;
                boolean big = i % 5 == 0;
                float r0 = rad * (big ? 0.9F : 0.95F);
                GlowDraw.segment(buf, m, cx + Mth.cos(an) * r0, cy + Mth.sin(an) * r0, 0F, cx + Mth.cos(an) * rad,
                        cy + Mth.sin(an) * rad, 0F, big ? 1.8F : 1.0F, hr, hg, hb, (big ? 0.5F : 0.3F) * a, 0.2F * a, true);
            }
            float hand = -t * 0.75F - Mth.HALF_PI;     // yelkovan geriye kosar
            GlowDraw.segment(buf, m, cx, cy, 0F, cx + Mth.cos(hand) * rad * 0.82F, cy + Mth.sin(hand) * rad * 0.82F, 0F,
                    2.4F, gr, gg, gb, 0.05F * a, 0.4F * a, true);
            float hour = -t * 0.06F - Mth.HALF_PI;
            GlowDraw.segment(buf, m, cx, cy, 0F, cx + Mth.cos(hour) * rad * 0.5F, cy + Mth.sin(hour) * rad * 0.5F, 0F,
                    3.2F, gr, gg, gb, 0.05F * a, 0.35F * a, true);

            // merkeze dogru akan (ters) hiz cizgileri
            float maxR = (float) Math.sqrt(cx * cx + cy * cy);
            int mid = GlowDraw.mixRgb(core, glow, 0.5F);
            for (int i = 0; i < 46; i++) {
                R.seed(0x5EC0L, i, 7);
                float ang = R.next() * Mth.TWO_PI, off = R.next(), spd = R.range(0.7F, 1.3F);
                float len = R.range(0.08F, 0.22F) * maxR;
                float u = (off + t * 0.05F * spd) % 1F;
                float r0 = Mth.lerp(u, 1.05F, 0.3F) * maxR;          // disaridan iceri
                float la = 0.5F * a * Mth.sin(u * Mth.PI);
                float c = Mth.cos(ang), s = Mth.sin(ang);
                GlowDraw.segment(buf, m, cx + c * (r0 + len), cy + s * (r0 + len), 0F, cx + c * r0, cy + s * r0, 0F,
                        R.range(0.5F, 1.5F), GlowDraw.cr(mid), GlowDraw.cg(mid), GlowDraw.cb(mid), 0F, la, true);
            }
        }
        if (flash > 0.003F) {
            GlowDraw.softRect(buf, m, 0, 0, w, h, 1F, 1F, 1F, 1F, 0.3F * flash);
            float fe = Math.min(w, h) * 0.3F;
            GlowDraw.gradQuad(buf, m, 0, 0, w, fe, gr, gg, gb, 0.35F * flash, 0F, 0F, 0.35F * flash);
            GlowDraw.gradQuad(buf, m, 0, h - fe, w, h, gr, gg, gb, 0F, 0.35F * flash, 0.35F * flash, 0F);
        }
        GlowDraw.endGui();

        if (a > 0.05F) {
            // bant gostergesi ve geriye sayan zaman kodu
            boolean blink = ((int) (t / 6F)) % 2 == 0;
            int col = (Math.min(255, (int) (a * 255)) << 24) | (hot & 0xFFFFFF);
            Component label = Component.translatable("hud.flashmod.rewind");
            if (blink) g.drawString(mc.font, label, (w - mc.font.width(label)) / 2, 10, col, true);
            SkillClient.Rewind r = SkillClient.localRewind();
            if (r != null) {
                float back = (1F - r.progress(t)) * (r.count - 1) / 20F;
                String tc = String.format(Locale.ROOT, "-%d:%05.2f", (int) (back / 60F), back % 60F);
                g.drawString(mc.font, tc, (w - mc.font.width(tc)) / 2, 22, col, true);
            }
        }
    }
}
