package dev.baranhan.flashmod.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.baranhan.flashmod.client.skin.SkinManager;
import dev.baranhan.flashmod.network.FlashNetwork;
import dev.baranhan.flashmod.network.SetSkinPacket;
import dev.baranhan.flashmod.skin.SkinData;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Kostum dolabi: .minecraft/flashskins klasorundeki PNG skin'leri listeler, secileni karakterin uzerinde canli
 * onizler (surukleyerek dondur, tekerlekle yakinlastir), klasik/ince kol secimi, uygula (sunucuya gider, oyuncu
 * NBT'sine kaydedilir, herkes gorur), varsayilana don, klasoru ac, yenile.
 */
public class SkinWardrobeScreen extends Screen {
    private static final int ROW = 14;
    @Nullable private final Screen parent;
    private List<Path> files = List.of();
    /** -1 = varsayilan skin, 0.. = dosyalar. */
    private int selected = -1;
    private int scroll;
    private boolean slim;
    @Nullable private byte[] selectedBytes;
    private String info = "";
    private Component status = Component.empty();
    private int statusColor = 0xA0A0A0;
    private long statusUntil;

    private int listX, listY, listW, listH, prevX0, prevY0, prevX1, prevY1;
    private float yaw = 25F, pitch = -5F, zoom = 1F;
    private boolean dragging;
    private Button slimButton;

    public SkinWardrobeScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.flashmod.wardrobe"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        listW = Math.min(150, width / 3);
        listX = 12;
        listY = 30;
        listH = height - listY - 60;
        prevX0 = listX + listW + 14;
        prevX1 = width - 12;
        prevY0 = 30;
        prevY1 = height - 60;

        int by = height - 50;
        int pw = prevX1 - prevX0;
        int bw = Math.min(110, (pw - 8) / 2);
        int pcx = (prevX0 + prevX1) / 2;
        slimButton = addRenderableWidget(Button.builder(slimLabel(), b -> {
            slim = !slim;
            SkinManager.setPreviewSlim(slim);
            b.setMessage(slimLabel());
        }).bounds(pcx - bw - 4, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.flashmod.wardrobe.apply"), b -> applySelected())
                .bounds(pcx + 4, by, bw, 20).build());

        int y2 = height - 26;
        int w3 = Math.min(100, (width - 40) / 3);
        int x3 = width / 2 - (w3 * 3 + 8) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.flashmod.wardrobe.open_folder"),
                b -> Util.getPlatform().openFile(SkinManager.folder().toFile())).bounds(x3, y2, w3, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.flashmod.wardrobe.refresh"), b -> refresh())
                .bounds(x3 + w3 + 4, y2, w3, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(x3 + (w3 + 4) * 2, y2, w3, 20).build());
        refresh();
    }

    private Component slimLabel() {
        return Component.translatable(slim ? "screen.flashmod.wardrobe.slim" : "screen.flashmod.wardrobe.classic");
    }

    private void refresh() {
        String keep = selected >= 0 && selected < files.size() ? files.get(selected).getFileName().toString() : null;
        files = SkinManager.listSkins();
        int idx = -1;
        if (keep != null) {
            for (int i = 0; i < files.size(); i++) if (files.get(i).getFileName().toString().equals(keep)) idx = i;
        } else if (selected == -1 && minecraft != null && minecraft.player != null) {
            // acilista su an giyilen skin secili gelsin (isimden)
            SkinManager.Skin cur = SkinManager.get(minecraft.player.getUUID());
            if (cur != null) {
                for (int i = 0; i < files.size(); i++) if (files.get(i).getFileName().toString().equals(cur.name())) idx = i;
            }
        }
        select(idx);
        scroll = Mth.clamp(scroll, 0, Math.max(0, files.size() + 1 - listH / ROW));
    }

    private void select(int idx) {
        selected = idx;
        selectedBytes = null;
        info = "";
        if (idx < 0 || idx >= files.size()) {
            selected = -1;
            SkinManager.preview(null, false);
            info = Component.translatable("screen.flashmod.wardrobe.default_info").getString();
            return;
        }
        Path f = files.get(idx);
        try {
            byte[] bytes = Files.readAllBytes(f);
            NativeImage img = SkinManager.decode(bytes);
            if (img == null) {
                SkinManager.preview(null, false);
                setStatus(Component.translatable("screen.flashmod.wardrobe.bad_image"), 0xFF6060);
                return;
            }
            String size = img.getWidth() + "x" + img.getHeight();
            slim = SkinManager.guessSlim(img);
            if (slimButton != null) slimButton.setMessage(slimLabel());
            SkinManager.preview(img, slim);
            selectedBytes = bytes;
            info = size + "  \u2022  " + (bytes.length / 1024F > 1 ? String.format("%.1f KB", bytes.length / 1024F) : bytes.length + " B");
            if (SkinData.validate(bytes) == null) {
                selectedBytes = null;
                setStatus(Component.translatable(bytes.length > SkinData.MAX_BYTES
                        ? "screen.flashmod.wardrobe.too_big" : "screen.flashmod.wardrobe.bad_size"), 0xFF6060);
            }
        } catch (IOException ex) {
            SkinManager.preview(null, false);
            setStatus(Component.translatable("screen.flashmod.wardrobe.bad_image"), 0xFF6060);
        }
    }

    private void applySelected() {
        if (selected == -1) {
            FlashNetwork.sendToServer(new SetSkinPacket(new byte[0], false, ""));
            setStatus(Component.translatable("screen.flashmod.wardrobe.applied_default"), 0x7CFF7C);
            return;
        }
        if (selectedBytes == null) {
            setStatus(Component.translatable("screen.flashmod.wardrobe.cannot_apply"), 0xFF6060);
            return;
        }
        FlashNetwork.sendToServer(new SetSkinPacket(selectedBytes, slim, files.get(selected).getFileName().toString()));
        setStatus(Component.translatable("screen.flashmod.wardrobe.applied"), 0x7CFF7C);
    }

    private void setStatus(Component c, int color) {
        status = c;
        statusColor = color;
        statusUntil = Util.getMillis() + 4000L;
    }

    // ---------------------------------------------------------------- cizim

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);

        // liste
        panel(g, listX, listY, listX + listW, listY + listH);
        int rows = listH / ROW;
        int total = files.size() + 1;
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int r = 0; r < rows + 1; r++) {
            int i = r + scroll; // 0 = varsayilan
            if (i >= total) break;
            int y = listY + r * ROW;
            boolean sel = (i == 0 && selected == -1) || (i - 1 == selected && selected >= 0);
            boolean hov = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + ROW;
            if (sel) g.fill(listX + 1, y, listX + listW - 1, y + ROW, 0x60FFB020);
            else if (hov) g.fill(listX + 1, y, listX + listW - 1, y + ROW, 0x30FFFFFF);
            String name = i == 0 ? Component.translatable("screen.flashmod.wardrobe.default").getString()
                    : files.get(i - 1).getFileName().toString();
            if (name.toLowerCase().endsWith(".png") && i > 0) name = name.substring(0, name.length() - 4);
            g.drawString(font, font.plainSubstrByWidth(name, listW - 10), listX + 5, y + 3, i == 0 ? 0xFFD27F : 0xE8E8E8, false);
        }
        g.disableScissor();
        if (files.isEmpty()) {
            int y = listY + ROW + 6;
            for (var line : font.split(Component.translatable("screen.flashmod.wardrobe.empty"), listW - 10)) {
                g.drawString(font, line, listX + 5, y, 0x808080, false);
                y += 10;
            }
        }

        // onizleme
        panel(g, prevX0, prevY0, prevX1, prevY1);
        LocalPlayer p = minecraft == null ? null : minecraft.player;
        if (p != null) {
            int cx = (prevX0 + prevX1) / 2;
            int ph = prevY1 - prevY0;
            int scale = (int) (ph * 0.36F * zoom);
            int cy = prevY0 + (int) (ph * 0.5F + scale * 0.95F);
            g.enableScissor(prevX0 + 1, prevY0 + 1, prevX1 - 1, prevY1 - 1);
            renderPlayer(g, cx, cy, scale, p);
            g.disableScissor();
        }
        g.drawString(font, Component.translatable("screen.flashmod.wardrobe.drag_hint"), prevX0 + 5, prevY0 + 4, 0x707070, false);
        g.drawCenteredString(font, info, (prevX0 + prevX1) / 2, prevY1 - 12, 0xB0B0B0);

        super.render(g, mouseX, mouseY, pt);
        if (Util.getMillis() < statusUntil) {
            g.drawCenteredString(font, status, (prevX0 + prevX1) / 2, height - 62, statusColor);
        }
    }

    private void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF2A2A2A);
        g.fill(x0, y0, x1, y1, 0xF0050608);
    }

    private void renderPlayer(GuiGraphics g, int x, int y, int scale, LocalPlayer p) {
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf cam = new Quaternionf().rotateX(pitch * ((float) Math.PI / 180F));
        pose.mul(cam);
        float by = p.yBodyRot, byo = p.yBodyRotO, yr = p.getYRot(), xr = p.getXRot(), hy = p.yHeadRot, hyo = p.yHeadRotO;
        float body = 180F + yaw;
        p.yBodyRot = body;
        p.yBodyRotO = body;
        p.setYRot(body);
        p.setXRot(0F);
        p.yHeadRot = body;
        p.yHeadRotO = body;
        InventoryScreen.renderEntityInInventory(g, x, y, scale, pose, cam, p);
        p.yBodyRot = by;
        p.yBodyRotO = byo;
        p.setYRot(yr);
        p.setXRot(xr);
        p.yHeadRot = hy;
        p.yHeadRotO = hyo;
    }

    // ---------------------------------------------------------------- girdi

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int i = (int) ((my - listY) / ROW) + scroll;
            if (i <= files.size()) select(i - 1);
            return true;
        }
        if (button == 0 && mx >= prevX0 && mx < prevX1 && my >= prevY0 && my < prevY1) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            yaw -= (float) dx * 1.2F;
            pitch = Mth.clamp(pitch - (float) dy * 0.8F, -40F, 40F);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= listX && mx < listX + listW) {
            scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, files.size() + 1 - listH / ROW));
            return true;
        }
        if (mx >= prevX0 && mx < prevX1) {
            zoom = Mth.clamp(zoom + (float) delta * 0.1F, 0.6F, 1.8F);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public void onClose() {
        SkinManager.endPreview();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        SkinManager.endPreview();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
