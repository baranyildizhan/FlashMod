package dev.baranhan.flashmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.flashmod.FlashMod;
import dev.baranhan.flashmod.client.ClientSpeedsters;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

/**
 * Duvarda kosma: model, ayaklari duvarda (gogus hizasinda) olacak sekilde duvar ekseni etrafinda 90 derece doner;
 * govde duvardan disari, yuz yonu oyuncunun yerel bakisi. Donus, pivot kaymasi ve yuz yonu hepsi ayni yumusak
 * karisimla (wallBlend) ilerler -> ani donus yok. BodyPoseCapture bunu yakaladigi icin simsekler de dogru yere yapisir.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class AbilityRenderer {
    private static int pushed = Integer.MIN_VALUE;

    private AbilityRenderer() {}

    @SubscribeEvent
    public static void onPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        ClientSpeedsters.Entry e = ClientSpeedsters.get(p.getUUID());
        if (e == null || e.blitz || e.tornado) return;
        float pt = event.getPartialTick();
        float b = e.wallBlend(pt);
        if (b <= 0.001F) return;
        float nx = e.wallNx, nz = e.wallNz;
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        // pivot: ayaklar duvarda (oyuncunun duvara bakan yuzu, gogus hizasi)
        ps.translate(-nx * 0.3D * b, 0.9D * b, -nz * 0.3D * b);
        // donus: yerel yukari -> duvardan disari (N); eksen = cross(yukari, N) = (nz, 0, -nx).
        // Govde/kafa yonu vanilla'nin kendi yaw'i: yerel cercevede yerdeki gibi.
        ps.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(90.0D * b), nz, 0F, -nx));
        pushed = p.getId();
    }

    @SubscribeEvent
    public static void onPost(RenderPlayerEvent.Post event) {
        if (pushed != event.getEntity().getId()) return;
        event.getPoseStack().popPose();
        pushed = Integer.MIN_VALUE;
    }
}
