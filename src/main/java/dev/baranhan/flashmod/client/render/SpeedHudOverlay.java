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

    // ---------------------------------------------------------------- yetenek cipleri

    /** Bir yetenek cipi: tus, ad, tur (bas / basili tut / ac-kapa), kullanilabilir mi, su an acik mi, bekleme. */
    private record Chip(net.minecraft.client.KeyMapping key, String name, int kind, boolean usable, boolean on,
                        long readyAt, int total) {}

    private static final int PRESS = 0, HOLD = 1, TOGGLE = 2;
    private static final int GREY = 0xFF7C7C7C, GREY_DARK = 0xFF4A4A4A;

    private static float cfg(java.util.function.Supplier<Double> v, float def) {
        try {
            return v.get().floatValue();
        } catch (IllegalStateException ex) {
            return def;
        }
    }

    private static boolean cfgB(java.util.function.Supplier<Boolean> v, boolean def) {
        try {
            return v.get();
        } catch (IllegalStateException ex) {
            return def;
        }
    }

    private static String keyText(net.minecraft.client.KeyMapping k) {
        String t = k.getTranslatedKeyMessage().getString();
        return t.length() > 3 ? t.substring(0, 3) : t;
    }

    /**
     * Cip: solda tus (renkli), yaninda ad; sagda tur isareti (basili tut: alt cizgili tus kapagi, ac/kapa: yanan /
     * sonuk nokta). Bekleme suresindeyse ad yerine kalan saniye ve altta dolan cizgi. Kullanilamiyorsa gri.
     * Glow (yumusak isik) ikinci gecişte: glows listesine {x0, y0, x1, y1, guc} eklenir.
     */
    /** Adin ve tus kapaginin sigdigi en dar cip genisligi (ac/kapa noktasi dahil). */
    private static int chipWidth(Minecraft mc, Chip c) {
        int kw = Math.max(9, mc.font.width(keyText(c.key())) + 4);
        return 1 + kw + 4 + mc.font.width(c.name()) + (c.kind() == TOGGLE ? 11 : 4);
    }

    private static void chip(GuiGraphics g, Minecraft mc, Chip c, int x0, int y0, int cw, int chH, long now, int glow,
                             int core, float pulse, java.util.List<float[]> glows) {
        long left = Math.max(0L, c.readyAt() - now);
        boolean cooling = left > 0L;
        boolean lit = c.usable() && !cooling;
        g.fill(x0, y0, x0 + cw, y0 + chH, 0x90000000);
        if (c.on()) {
            g.fill(x0, y0, x0 + cw, y0 + chH, (0x48 << 24) | (glow & 0xFFFFFF));
            glows.add(new float[]{x0, y0, x0 + cw, y0 + chH, 0.35F * pulse});
        }
        int keyCol = lit || c.on() ? 0xFF000000 | glow : GREY_DARK | 0xFF000000;
        String key = keyText(c.key());
        int kw = Math.max(9, mc.font.width(key) + 4);
        g.fill(x0 + 1, y0 + 1, x0 + 1 + kw, y0 + chH - 1, lit || c.on() ? 0x60000000 : 0x40000000);
        g.drawString(mc.font, key, x0 + 1 + (kw - mc.font.width(key)) / 2, y0 + 2, keyCol, false);
        if (c.kind() == HOLD) { // basili tut: tus kapaginin altinda kalin cizgi
            g.fill(x0 + 2, y0 + chH - 2, x0 + kw, y0 + chH - 1, lit ? 0xFF000000 | GlowDraw.mixRgb(glow, 0xFFFFFF, 0.3F) : 0xFF555555);
        }
        int tx = x0 + kw + 4;
        int nameCol = c.on() ? 0xFF000000 | GlowDraw.mixRgb(core, 0xFFFFFF, 0.4F) : lit ? 0xFFF0F0F0 : GREY;
        if (cooling) {
            String sec = left >= 200L ? String.format(Locale.ROOT, "%ds", (left + 19L) / 20L)
                    : String.format(Locale.ROOT, "%.1fs", left / 20F);
            g.drawString(mc.font, c.name(), tx, y0 + 2, GREY, false);
            g.drawString(mc.font, sec, x0 + cw - 3 - mc.font.width(sec), y0 + 2, 0xFFB8B8B8, false);
            float ready = 1F - Mth.clamp(left / (float) Math.max(1, c.total()), 0F, 1F);
            g.fill(x0 + 1, y0 + chH - 1, x0 + 1 + Math.round((cw - 2) * ready), y0 + chH, 0xFF000000 | GlowDraw.mixRgb(glow, 0, 0.5F));
        } else {
            g.drawString(mc.font, c.name(), tx, y0 + 2, nameCol, false);
            if (lit) {
                g.fill(x0 + 1, y0 + chH - 1, x0 + cw - 1, y0 + chH, 0xFF000000 | GlowDraw.mixRgb(glow, 0, 0.3F));
                glows.add(new float[]{x0 + 1, y0 + chH - 1, x0 + cw - 1, y0 + chH, 0.4F * pulse});
            }
        }
        if (c.kind() == TOGGLE) { // ac/kapa: sagda nokta
            int dx = x0 + cw - 6, dy = y0 + chH / 2 - 2;
            if (!cooling) g.fill(dx, dy, dx + 3, dy + 3, c.on() ? 0xFF000000 | GlowDraw.mixRgb(core, 0xFFFFFF, 0.5F) : 0xFF5A5A5A);
            if (c.on()) glows.add(new float[]{dx - 1, dy - 1, dx + 4, dy + 4, 0.8F * pulse});
        }
    }

    private static void glowPass(GuiGraphics g, java.util.List<float[]> glows, int glow) {
        if (glows.isEmpty()) return;
        g.flush();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder buf = GlowDraw.beginGui();
        for (float[] r : glows) {
            GlowDraw.softRect(buf, m, r[0], r[1], r[2], r[3], 2.5F, GlowDraw.cr(glow), GlowDraw.cg(glow), GlowDraw.cb(glow), r[4]);
        }
        GlowDraw.endGui();
    }

    public static void renderHud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !FlashClientConfig.SHOW_HUD.get()) return;
        ClientSpeedsters.Entry e = ClientSpeedsters.get(mc.player.getUUID());
        boolean active = e != null && e.active;
        int glowCol = e != null ? e.glow : SpeedsterData.DEFAULT_GLOW, coreCol = e != null ? e.core : SpeedsterData.DEFAULT_CORE;
        float pulse = 0.75F + 0.25F * Mth.sin(Util.getMillis() / 180F);
        long now = mc.level == null ? 0L : mc.level.getGameTime();
        java.util.List<float[]> glows = new java.util.ArrayList<>();

        // --- sag ust: kus bakisi kamera + Speed Force ac/kapa
        {
            Chip aerial = new Chip(dev.baranhan.flashmod.client.FlashKeys.AERIAL, tr("hud.flashmod.aerial"), TOGGLE,
                    active, dev.baranhan.flashmod.client.CameraModes.aerial, 0L, 1);
            Chip sf = new Chip(dev.baranhan.flashmod.client.FlashKeys.TOGGLE, tr("hud.flashmod.speedforce"), TOGGLE,
                    true, active, 0L, 1);
            int cw = Math.max(64, Math.max(chipWidth(mc, aerial), chipWidth(mc, sf))), chH = 12;
            int gx = w - 6 - (cw * 2 + 3), gy = 6;
            chip(g, mc, aerial, gx, gy, cw, chH, now, glowCol, coreCol, pulse, glows);
            chip(g, mc, sf, gx + cw + 3, gy, cw, chH, now, glowCol, coreCol, pulse, glows);
        }
        if (!active) {
            glowPass(g, glows, glowCol);
            return;
        }

        int x = 6, y = 6, pw = 168, ph = 30;
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

        // Speed Force enerjisi
        int ey = y + ph + extra + 3, ew = pw, eh = 4;
        g.fill(x, ey, x + ew, ey + eh, 0x90000000);
        int fillW = Math.round((ew - 2) * Mth.clamp(e.energy / 100F, 0F, 1F));
        g.fill(x + 1, ey + 1, x + 1 + fillW, ey + eh - 1, 0xFF000000 | GlowDraw.mixRgb(e.glow, 0, 0.25F));
        glows.add(new float[]{x + 1, ey + 1, x + 1 + fillW, ey + eh - 1, 0.35F * pulse});

        // Kinetik yuk (+ mizrak sarji: kinetikten elde biriken kisim, ardindan parlak)
        dev.baranhan.flashmod.client.skill.SkillClient.Info si =
                dev.baranhan.flashmod.client.skill.SkillClient.info(mc.player.getUUID());
        float kin = Mth.clamp(si.kinetic / 100F, 0F, 1F);
        boolean kinFull = kin >= 0.999F;
        int ky = ey + eh + 2, kh = 3;
        g.fill(x, ky, x + ew, ky + kh, 0x90000000);
        int kinW = Math.round((ew - 2) * kin);
        int kinCol = kinFull ? GlowDraw.mixRgb(e.core, 0xFFFFFF, 0.35F + 0.35F * pulse) : GlowDraw.mixRgb(e.core, e.glow, 0.35F);
        if (kinW > 0) g.fill(x + 1, ky + 1, x + 1 + kinW, ky + kh - 1, 0xFF000000 | kinCol);
        if (kinW > 0) glows.add(new float[]{x + 1, ky + 1, x + 1 + kinW, ky + kh - 1, (kinFull ? 0.6F : 0.25F) * pulse});
        if (e.charging && e.charge > 0F) {
            int cw0 = Math.min(ew - 2 - kinW, Math.round((ew - 2) * Mth.clamp(e.charge / 100F, 0F, 1F)));
            g.fill(x + 1 + kinW, ky, x + 1 + kinW + cw0, ky + kh, 0xFF000000 | GlowDraw.mixRgb(e.core, 0xFFFFFF, 0.6F));
            glows.add(new float[]{x + 1 + kinW, ky, x + 1 + kinW + cw0, ky + kh, 0.8F * pulse});
        }

        // --- yetenekler: 2 sutun
        boolean locked = e.blitz || dev.baranhan.flashmod.client.ultimate.UltDirector.inputLocked();
        boolean torn = dev.baranhan.flashmod.client.Tornado.isActive(), wall = dev.baranhan.flashmod.client.WallRun.isActive();
        boolean base = !locked;
        float ultCost = cfg(dev.baranhan.flashmod.config.FlashServerConfig.ULT_ENERGY::get, 60F);
        float decoyCost = cfg(dev.baranhan.flashmod.config.FlashServerConfig.DECOY_ENERGY::get, 20F);
        float rewindCost = cfg(dev.baranhan.flashmod.config.FlashServerConfig.REWIND_ENERGY::get, 35F);
        boolean phaseOk = cfgB(dev.baranhan.flashmod.config.FlashServerConfig.PHASING::get, true);
        boolean rewinding = dev.baranhan.flashmod.client.skill.SkillClient.rewinding(mc.player.getUUID());
        Chip[] chips = {
                new Chip(dev.baranhan.flashmod.client.FlashKeys.BLITZ, tr("hud.flashmod.blitz"), PRESS,
                        base && !torn && !wall, e.blitz, si.blitzReadyAt, si.blitzTotal),
                new Chip(dev.baranhan.flashmod.client.FlashKeys.ULTIMATE, tr("hud.flashmod.ultimate"), PRESS,
                        base && !torn && !wall && e.energy + 1.0E-3F >= ultCost, false, si.ultReadyAt, si.ultTotal),
                new Chip(dev.baranhan.flashmod.client.FlashKeys.TORNADO, tr("hud.flashmod.tornado"), HOLD,
                        base && !wall, torn, 0L, 1),
                new Chip(dev.baranhan.flashmod.client.FlashKeys.THROW, tr("hud.flashmod.spear"), HOLD,
                        base && (e.charging || si.kinetic >= dev.baranhan.flashmod.speed.AbilityLogic.MIN_THROW), e.charging, 0L, 1),
                new Chip(dev.baranhan.flashmod.client.FlashKeys.PHASE, tr("hud.flashmod.phase"), HOLD,
                        base && phaseOk && !torn, phasing, 0L, 1),
                new Chip(dev.baranhan.flashmod.client.FlashKeys.SLOWMO, tr("hud.flashmod.slowmo"), TOGGLE,
                        base && (e.slowmo || e.energy > 2F), e.slowmo, 0L, 1),
                new Chip(dev.baranhan.flashmod.client.FlashKeys.DECOY, tr("hud.flashmod.decoy"), PRESS,
                        base && !torn && e.energy + 1.0E-3F >= decoyCost, false, si.decoyReadyAt, si.decoyTotal),
                new Chip(dev.baranhan.flashmod.client.FlashKeys.REWIND, tr("hud.flashmod.rewind_short"), PRESS,
                        base && !torn && !wall && e.energy + 1.0E-3F >= rewindCost, rewinding, si.rewindReadyAt, si.rewindTotal)};
        int chH = 12, cw = (pw - 3) / 2, cy = ky + kh + 3;
        for (int i = 0; i < chips.length; i++) {
            int col = i % 2, row = i / 2;
            chip(g, mc, chips[i], x + col * (cw + 3), cy + row * (chH + 2), cw, chH, now, e.glow, e.core, pulse, glows);
        }

        // seviye segmentlerinin isigi
        g.flush();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder buf = GlowDraw.beginGui();
        int hot = GlowDraw.mixRgb(e.core, 0xFFFFFF, 0.3F);
        for (int i = 0; i < e.level; i++) {
            int x0 = sx + i * (segW + gap);
            GlowDraw.softRect(buf, m, x0, sy, x0 + segW, sy + segH, 3F,
                    GlowDraw.cr(e.glow), GlowDraw.cg(e.glow), GlowDraw.cb(e.glow), 0.45F * pulse);
            GlowDraw.softRect(buf, m, x0 + 1, sy + 1, x0 + segW - 1, sy + segH - 1, 1F,
                    GlowDraw.cr(hot), GlowDraw.cg(hot), GlowDraw.cb(hot), 0.7F);
        }
        GlowDraw.endGui();
        glowPass(g, glows, e.glow);
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
}
