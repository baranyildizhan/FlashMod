package dev.baranhan.flashmod.client.ultimate;

import com.mojang.blaze3d.platform.InputConstants;
import dev.baranhan.flashmod.config.FlashClientConfig;
import dev.baranhan.flashmod.ultimate.UltimatePhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Debug tuslari (onizleme ya da client config debug=true): Numpad 0 duraklat, 4/6 geri/ileri 1 tick (Shift 10),
 * 1-9 faza atla, 5 bilgi, 8 serbest kamera. Geri sarma yalniz onizlemede (/flashult preview) calisir.
 */
public final class UltDebug {
    private static final boolean[] DOWN = new boolean[GLFW.GLFW_KEY_LAST + 1];
    private static final UltimatePhase[] JUMP = {UltimatePhase.WINDUP, UltimatePhase.DEPART, UltimatePhase.VOID,
            UltimatePhase.OCEAN, UltimatePhase.ORBIT, UltimatePhase.TUNNEL, UltimatePhase.IMPACT, UltimatePhase.LAUNCH,
            UltimatePhase.RECOVER};

    private UltDebug() {}

    private static boolean pressed(int key) {
        long win = Minecraft.getInstance().getWindow().getWindow();
        boolean d = InputConstants.isKeyDown(win, key);
        boolean edge = d && !DOWN[key];
        DOWN[key] = d;
        return edge;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        UltState s = UltDirector.full();
        if (s == null || mc.screen != null || (!s.preview && !FlashClientConfig.ULT_DEBUG.get())) return;
        long win = mc.getWindow().getWindow();
        boolean shift = InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_SHIFT);
        if (pressed(GLFW.GLFW_KEY_KP_0)) s.paused = !s.paused;
        if (pressed(GLFW.GLFW_KEY_KP_5)) UltDirector.showInfo = !UltDirector.showInfo;
        if (pressed(GLFW.GLFW_KEY_KP_8)) UltDirector.freeCam = !UltDirector.freeCam;
        if (!s.preview) return;
        float step = shift ? 10F : 1F;
        if (pressed(GLFW.GLFW_KEY_KP_4)) scrub(s, s.previewT - step);
        if (pressed(GLFW.GLFW_KEY_KP_6)) scrub(s, s.previewT + step);
        for (int i = 0; i < 9; i++) if (pressed(GLFW.GLFW_KEY_KP_1 + i)) scrub(s, JUMP[i].start);
    }

    private static void scrub(UltState s, float t) {
        s.previewT = Mth.clamp(t, -2F, 199F);
        s.lastTickT = s.previewT;   // ses tetikleyicisi atlamasin
        s.lastShakeT = s.previewT;
        s.trauma = 0F;
        s.springPos = null;
        s.collDist = -1;
        UltSounds.stopAll(s);
    }

    public static void renderInfo(GuiGraphics g, float pt) {
        UltState s = UltDirector.full();
        if (s == null || !UltDirector.showInfo) return;
        Minecraft mc = Minecraft.getInstance();
        float t = s.t(pt);
        UltimatePhase ph = UltimatePhase.at(t);
        String[] lines = {
                String.format("t=%.2f  %s  u=%.2f%s", t, ph, ph.local(t), s.paused ? "  [PAUSED]" : ""),
                String.format("cam yaw=%.1f pitch=%.1f roll=%.1f fov=%.1f", s.lastYaw, s.lastPitch, s.lastRoll, s.lastFov),
                String.format("scene cam %.2f %.2f %.2f", s.sceneCam.x, s.sceneCam.y, s.sceneCam.z),
                String.format("trauma=%.2f  flash=%.2f  freecam=%s", s.trauma, UltimatePhase.flash(t), UltDirector.freeCam)};
        int y = 4;
        for (String l : lines) {
            g.drawString(mc.font, l, 4, y, 0xFFFFD27F, true);
            y += 10;
        }
    }
}
