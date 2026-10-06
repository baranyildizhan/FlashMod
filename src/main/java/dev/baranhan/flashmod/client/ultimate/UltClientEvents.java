package dev.baranhan.flashmod.client.ultimate;

import dev.baranhan.flashmod.FlashMod;
import dev.baranhan.flashmod.config.FlashClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Ultimate istemci baglantilari: tick, roll/FOV, girdi ve HUD kilidi, el gizleme, temizlik. */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class UltClientEvents {
    private UltClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        while (dev.baranhan.flashmod.client.FlashKeys.ULTIMATE_SKIP.consumeClick()) UltDirector.skip();
        UltDirector.clientTick();
    }

    /** Roll'u en son biz yazariz (diger sarsintilardan sonra); BodyPoseCapture LOWEST'te yakalar. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!UltDirector.controlsCamera()) return;
        Minecraft mc = Minecraft.getInstance();
        event.setYaw(mc.gameRenderer.getMainCamera().getYRot());
        event.setPitch(mc.gameRenderer.getMainCamera().getXRot());
        event.setRoll(UltDirector.roll());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (UltDirector.controlsCamera()) event.setFOV(UltDirector.fov());
    }

    @SubscribeEvent
    public static void onMovement(MovementInputUpdateEvent event) {
        if (!UltDirector.inputLocked()) return;
        var in = event.getInput();
        in.forwardImpulse = 0F;
        in.leftImpulse = 0F;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.side.isClient()) return;
        if (event.player != Minecraft.getInstance().player || !UltDirector.inputLocked()) return;
        Vec3 v = event.player.getDeltaMovement();
        event.player.setDeltaMovement(0D, Math.min(0D, v.y), 0D);
        event.player.setSprinting(false);
    }

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (UltDirector.inputLocked()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (UltDirector.inputLocked() && Minecraft.getInstance().screen == null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        if (UltDirector.controlsCamera()) event.setCanceled(true);
    }

    /** Sinematikte HUD gizli (sohbet ve F3 acik kalir; config ile sohbet de gizlenebilir). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHud(RenderGuiOverlayEvent.Pre event) {
        if (!UltDirector.hidesHud()) return;
        String id = event.getOverlay().id().getPath();
        if (id.equals("flash_ultimate") || id.equals("debug_text")) return;
        if (id.equals("chat_panel") && !FlashClientConfig.ULT_HIDE_CHAT.get()) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        UltDirector.clear();
    }
}
