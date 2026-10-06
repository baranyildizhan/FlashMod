package dev.baranhan.flashmod.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.baranhan.flashmod.client.ClientSpeedsters;
import dev.baranhan.flashmod.client.render.GlowDraw;
import dev.baranhan.flashmod.client.render.Lightning;
import dev.baranhan.flashmod.client.render.Polyline;
import dev.baranhan.flashmod.config.FlashClientConfig;
import dev.baranhan.flashmod.network.FlashNetwork;
import dev.baranhan.flashmod.network.SetColorsPacket;
import dev.baranhan.flashmod.util.ColorUtil;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import javax.annotation.Nullable;

/** Cekirdek + parilti rengi icin HSV ekrani, hazir renkler ve canli simsek onizlemesi. */
public class FlashColorScreen extends Screen {
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final String[] PRESET_KEYS = {"gold", "crimson", "cobalt", "platinum", "violet", "emerald"};
    private static final int[][] PRESETS = {
            {0xFFF3C4, 0xFF9A1A}, // altin (CW Flash)
            {0xFFD6D0, 0xE5161B}, // kizil (Reverse-Flash tarzi)
            {0xE2F4FF, 0x2E7DFF}, // kobalt
            {0xFFFFFF, 0xBFD8FF}, // platin
            {0xF3DEFF, 0x9B32FF}, // mor
            {0xE2FFE9, 0x19D866}, // zumrut
    };

    @Nullable
    private final Screen parent;
    private final float[] coreHsv;
    private final float[] glowHsv;
    private int core, glow;

    private final HsvSlider[] coreSliders = new HsvSlider[3];
    private final HsvSlider[] glowSliders = new HsvSlider[3];
    private int colX1, colX2, colW, top, previewY0, previewY1;

    private final Polyline line = new Polyline();
    private final Lightning.Rng rng = new Lightning.Rng(5);

    public FlashColorScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.flashmod.colors"));
        this.parent = parent;
        // Baslangic: sunucudan gelen (oyuncu NBT'sindeki) kendi renklerin; yoksa istemci varsayilani
        ClientSpeedsters.Entry own = Minecraft.getInstance().player != null
                ? ClientSpeedsters.get(Minecraft.getInstance().player.getUUID()) : null;
        this.core = own != null && own.synced ? own.core : FlashClientConfig.core();
        this.glow = own != null && own.synced ? own.glow : FlashClientConfig.glow();
        this.coreHsv = ColorUtil.toHsv(core);
        this.glowHsv = ColorUtil.toHsv(glow);
    }

    @Override
    protected void init() {
        colW = Math.min(150, (width - 30) / 2);
        colX1 = width / 2 - colW - 6;
        colX2 = width / 2 + 6;
        top = 30;
        String[] keys = {"screen.flashmod.hue", "screen.flashmod.saturation", "screen.flashmod.brightness"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            coreSliders[i] = addRenderableWidget(new HsvSlider(colX1, top + 12 + i * 26, colW, keys[i], i == 0,
                    coreHsv[i], v -> { coreHsv[idx] = v; recompute(); }));
            glowSliders[i] = addRenderableWidget(new HsvSlider(colX2, top + 12 + i * 26, colW, keys[i], i == 0,
                    glowHsv[i], v -> { glowHsv[idx] = v; recompute(); }));
        }

        int py = top + 12 + 3 * 26 + 4;
        int totalW = colW * 2 + 12;
        int bw = (totalW - 5 * 4) / 6;
        for (int i = 0; i < PRESETS.length; i++) {
            final int[] preset = PRESETS[i];
            addRenderableWidget(Button.builder(Component.translatable("preset.flashmod." + PRESET_KEYS[i]),
                            b -> applyPreset(preset[0], preset[1]))
                    .bounds(colX1 + i * (bw + 4), py, bw, 20).build());
        }

        previewY0 = py + 30;
        previewY1 = Math.max(previewY0 + 30, height - 34);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 50, height - 26, 100, 20).build());
    }

    private void applyPreset(int c, int g) {
        float[] ch = ColorUtil.toHsv(c), gh = ColorUtil.toHsv(g);
        System.arraycopy(ch, 0, coreHsv, 0, 3);
        System.arraycopy(gh, 0, glowHsv, 0, 3);
        for (int i = 0; i < 3; i++) {
            coreSliders[i].setSilently(coreHsv[i]);
            glowSliders[i].setSilently(glowHsv[i]);
        }
        recompute();
    }

    private void recompute() {
        core = ColorUtil.hsv(coreHsv[0], coreHsv[1], coreHsv[2]);
        glow = ColorUtil.hsv(glowHsv[0], glowHsv[1], glowHsv[2]);
        // Kendi izini aninda guncelle (sunucuya kapaninca gonderilir)
        if (minecraft != null && minecraft.player != null) {
            ClientSpeedsters.Entry e = ClientSpeedsters.get(minecraft.player.getUUID());
            if (e != null) {
                e.core = core;
                e.glow = glow;
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);

        drawColumnHeader(g, colX1, "screen.flashmod.core", core);
        drawColumnHeader(g, colX2, "screen.flashmod.glow", glow);

        super.render(g, mouseX, mouseY, pt);

        drawStrips(g, coreSliders, coreHsv);
        drawStrips(g, glowSliders, glowHsv);
        drawPreview(g);
    }

    private void drawColumnHeader(GuiGraphics g, int x, String key, int color) {
        g.drawString(font, Component.translatable(key), x, top, 0xE0E0E0, true);
        String hex = ColorUtil.toHex(color);
        int hw = font.width(hex);
        g.drawString(font, hex, x + colW - hw - 14, top, 0xA0A0A0, false);
        g.fill(x + colW - 11, top - 1, x + colW, top + 9, 0xFF000000);
        g.fill(x + colW - 10, top, x + colW - 1, top + 8, 0xFF000000 | color);
    }

    /** Her slider'in altinda o kanalin gradyani (H: tum tonlar, S/V: mevcut renge gore). */
    private void drawStrips(GuiGraphics g, HsvSlider[] sliders, float[] hsv) {
        g.flush();
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < 3; i++) {
            HsvSlider s = sliders[i];
            float x0 = s.getX() + 1, x1 = s.getX() + s.getWidth() - 1, y0 = s.getY() + 21, y1 = y0 + 3;
            if (i == 0) {
                for (int k = 0; k < 6; k++) {
                    float a = k / 6F, b = (k + 1) / 6F;
                    hGrad(buf, m, Mth.lerp(a, x0, x1), y0, Mth.lerp(b, x0, x1), y1,
                            ColorUtil.hsv(a, Math.max(hsv[1], 0.6F), Math.max(hsv[2], 0.8F)),
                            ColorUtil.hsv(b, Math.max(hsv[1], 0.6F), Math.max(hsv[2], 0.8F)));
                }
            } else if (i == 1) {
                hGrad(buf, m, x0, y0, x1, y1, ColorUtil.hsv(hsv[0], 0F, hsv[2]), ColorUtil.hsv(hsv[0], 1F, hsv[2]));
            } else {
                hGrad(buf, m, x0, y0, x1, y1, 0x000000, ColorUtil.hsv(hsv[0], hsv[1], 1F));
            }
        }
        Tesselator.getInstance().end();
        RenderSystem.disableBlend();
    }

    private static void hGrad(BufferBuilder buf, Matrix4f m, float x0, float y0, float x1, float y1, int cl, int cr) {
        buf.vertex(m, x0, y0, 0F).color(ColorUtil.r(cl), ColorUtil.g(cl), ColorUtil.b(cl), 1F).endVertex();
        buf.vertex(m, x0, y1, 0F).color(ColorUtil.r(cl), ColorUtil.g(cl), ColorUtil.b(cl), 1F).endVertex();
        buf.vertex(m, x1, y1, 0F).color(ColorUtil.r(cr), ColorUtil.g(cr), ColorUtil.b(cr), 1F).endVertex();
        buf.vertex(m, x1, y0, 0F).color(ColorUtil.r(cr), ColorUtil.g(cr), ColorUtil.b(cr), 1F).endVertex();
    }

    /** Oyun ici izle ayni katman mantigiyla cizilen 2D onizleme. */
    private void drawPreview(GuiGraphics g) {
        int x0 = colX1, x1 = colX2 + colW, y0 = previewY0, y1 = previewY1;
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF2A2A2A);
        g.fill(x0, y0, x1, y1, 0xF0050608);
        g.drawString(font, Component.translatable("screen.flashmod.preview"), x0 + 4, y0 + 3, 0x707070, false);
        g.flush();

        g.enableScissor(x0, y0, x1, y1);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder buf = GlowDraw.beginGui();
        float bloom = FlashClientConfig.BLOOM.get().floatValue();
        float time = Util.getMillis() / 50F;
        float boxH = y1 - y0, boxW = x1 - x0;
        float rx = x1 - boxW * 0.16F, ry = y0 + boxH * 0.5F;
        float scale = Mth.clamp(boxH / 3.2F, 12F, 34F);
        int strands = Math.min(FlashClientConfig.STRANDS.get(), 10);
        long frame = (long) (time * 0.5F);

        // Atmosfer sisi
        line.clear();
        for (int i = 0; i <= 40; i++) {
            float u = i / 40F;
            line.add(rx - u * (rx - x0 + 20), ry, 0F, (1F - u) * (1F - u), 1F + u);
        }
        GlowDraw.ribbon(buf, m, line, boxH * 0.38F * bloom, GlowDraw.cr(glow), GlowDraw.cg(glow), GlowDraw.cb(glow),
                0.12F * bloom, true);

        for (int s = 0; s < strands; s++) {
            rng.seed(77, s, 0);
            float anchor = rng.signed() * 0.32F, spread = rng.signed() * 0.45F;
            float amp = rng.range(0.05F, 0.14F), freq = rng.range(4F, 9F), ph = rng.next() * TAU;
            float lenFrac = rng.range(0.6F, 1F);
            long ns = 1000L + s * 31L, ts = ns ^ (frame * 0x9E3779B9L);
            line.clear();
            for (int i = 0; i <= 70; i++) {
                float u = i / 70F;
                if (u > lenFrac) break;
                float a = u / lenFrac;
                float yy = ry + boxH * (anchor + spread * a
                        + amp * (0.4F + a) * Mth.sin(u * freq + time * 0.35F + ph)
                        + Lightning.noise(ns, u * 14F + time * 0.3F) * 0.04F
                        + Lightning.noise(ts, u * 22F) * 0.025F * (0.4F + a));
                float xx = rx - u * (rx - x0 + 20);
                line.add(xx, yy, 0F, (1F - a) * (1F - a), 1F + a * 0.7F);
            }
            GlowDraw.layered(buf, m, line, scale, core, glow, 1F, bloom, true);
        }

        // Kosucu noktasi: parlayan cekirdek
        float pulse = 0.85F + 0.15F * Mth.sin(time * 0.8F);
        GlowDraw.disc(buf, m, rx, ry, 0F, 1, 0, 0, 0, 1, 0, boxH * 0.45F * Math.max(bloom, 0.3F),
                GlowDraw.cr(glow), GlowDraw.cg(glow), GlowDraw.cb(glow), 0.25F * pulse, 20);
        int hot = GlowDraw.mixRgb(core, 0xFFFFFF, 0.5F);
        GlowDraw.disc(buf, m, rx, ry, 0F, 1, 0, 0, 0, 1, 0, boxH * 0.1F,
                GlowDraw.cr(hot), GlowDraw.cg(hot), GlowDraw.cb(hot), 0.9F, 16);

        // Kosucu etrafinda citirdayan arklar
        rng.seed(91, frame, 1);
        for (int i = 0; i < 3; i++) {
            float a1 = rng.next() * TAU, a2 = a1 + rng.range(0.6F, 1.6F);
            float r = boxH * 0.16F;
            line.clear();
            Lightning.jag(line, rng, rx + Mth.cos(a1) * r, ry + Mth.sin(a1) * r, 0F,
                    rx + Mth.cos(a2) * r, ry + Mth.sin(a2) * r, 0F, 0.3F, 3, true, 1F, 0.6F);
            GlowDraw.layered(buf, m, line, scale * 0.55F, core, glow, 0.9F, bloom, true);
        }
        GlowDraw.endGui();
        g.disableScissor();
    }

    @Override
    public void onClose() {
        FlashClientConfig.CORE_COLOR.set(ColorUtil.toHex(core));
        FlashClientConfig.GLOW_COLOR.set(ColorUtil.toHex(glow));
        FlashClientConfig.SPEC.save();
        if (minecraft != null && minecraft.player != null && minecraft.getConnection() != null) {
            FlashNetwork.sendToServer(new SetColorsPacket(core, glow));
        }
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private interface FloatSink {
        void accept(float v);
    }

    private static final class HsvSlider extends AbstractSliderButton {
        private final String key;
        private final boolean hue;
        private final FloatSink sink;

        HsvSlider(int x, int y, int w, String key, boolean hue, double value, FloatSink sink) {
            super(x, y, w, 20, Component.empty(), Mth.clamp(value, 0D, 1D));
            this.key = key;
            this.hue = hue;
            this.sink = sink;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            if (key == null) return;
            String v = hue ? Math.round(value * 360) + "\u00B0" : Math.round(value * 100) + "%";
            setMessage(Component.translatable(key).append(": " + v));
        }

        @Override
        protected void applyValue() {
            sink.accept((float) value);
        }

        void setSilently(double v) {
            this.value = Mth.clamp(v, 0D, 1D);
            updateMessage();
        }
    }
}
