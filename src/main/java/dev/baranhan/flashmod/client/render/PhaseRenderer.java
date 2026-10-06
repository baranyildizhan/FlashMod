package dev.baranhan.flashmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.flashmod.FlashMod;
import dev.baranhan.flashmod.client.ClientSpeedsters;
import dev.baranhan.flashmod.speed.PhaseHelper;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Phasing animasyonu (Reverse-Flash'in titreyen yuzu gibi):
 *  - Gercek model her karede govdenin sag-sol ekseninde rastgele kayar (60 Hz titreme).
 *  - Ustune derinlik yazmayan, yari saydam, hafif renk tonlu hayalet kopyalar -> yatay "blur" yigini.
 *  - Ince beyaz yatay cizgiler SpeedTrailRenderer'da, ekran ghosting'i SpeedPostEffect'te.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class PhaseRenderer {
    private static final Lightning.Rng R = new Lightning.Rng(7);
    private static final int GHOSTS = 6;
    private static int pushedId = Integer.MIN_VALUE;

    private PhaseRenderer() {}

    /** Kare basina degisen -1..1 titreme degeri (her oyuncu/kanal icin farkli). */
    public static float jitter(int entityId, int channel) {
        long frame = Util.getMillis() / 16L;
        R.seed(entityId, frame, channel);
        float a = R.signed();
        return a * Math.abs(a) * 0.6F + R.signed() * 0.4F;
    }

    static boolean isPhasing(Player p) {
        Minecraft mc = Minecraft.getInstance();
        ClientSpeedsters.Entry e = ClientSpeedsters.get(p.getUUID());
        if (e != null && (e.tornado || e.blitz)) return false; // tornado/blitz kendi model donusumunu yapiyor
        if (p == mc.player) return PhaseHelper.clientLocalPhasing;
        return e != null && e.active && e.phasing;
    }

    private static boolean supportedPose(Player p) {
        Pose pose = p.getPose();
        return pose == Pose.STANDING || pose == Pose.CROUCHING;
    }

    @SubscribeEvent
    public static void onPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        if (!isPhasing(p) || !supportedPose(p)) return;
        float yaw = Mth.rotLerp(event.getPartialTick(), p.yBodyRotO, p.yBodyRot) * ((float) Math.PI / 180F);
        float off = jitter(p.getId(), 0) * 0.075F;
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(Mth.cos(yaw) * off, 0.0F, Mth.sin(yaw) * off);
        pushedId = p.getId();
    }

    @SubscribeEvent
    public static void onPost(RenderPlayerEvent.Post event) {
        Player p = event.getEntity();
        PoseStack ps = event.getPoseStack();
        if (pushedId != p.getId()) return;
        ps.popPose();
        pushedId = Integer.MIN_VALUE;
        if (!(p instanceof AbstractClientPlayer acp)) return;

        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        ResourceLocation skin = event.getRenderer().getTextureLocation(acp);
        VertexConsumer vc = event.getMultiBufferSource().getBuffer(FlashRenderTypes.ghost(skin));

        float pt = event.getPartialTick();
        float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        float yaw = bodyYaw * ((float) Math.PI / 180F);
        float sideX = Mth.cos(yaw), sideZ = Mth.sin(yaw);

        ClientSpeedsters.Entry e = ClientSpeedsters.get(p.getUUID());
        int glow = e != null ? e.glow : 0xFF9A1A;
        float r = Mth.lerp(0.35F, 1F, GlowDraw.cr(glow));
        float g = Mth.lerp(0.35F, 1F, GlowDraw.cg(glow));
        float b = Mth.lerp(0.35F, 1F, GlowDraw.cb(glow));

        for (int i = 0; i < GHOSTS; i++) {
            float spread = 0.08F + 0.22F * (i + 1) / GHOSTS;
            float off = jitter(p.getId(), i + 1) * spread;
            float alpha = 0.26F - 0.03F * i;
            ps.pushPose();
            ps.translate(sideX * off, 0.0F, sideZ * off);
            // LivingEntityRenderer.render ile ayni temel donusum (ayakta/egilmis pozlar icin)
            ps.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
            ps.scale(-1.0F, -1.0F, 1.0F);
            ps.scale(0.9375F, 0.9375F, 0.9375F);
            ps.translate(0.0F, -1.501F, 0.0F);
            model.renderToBuffer(ps, vc, event.getPackedLight(), OverlayTexture.NO_OVERLAY, r, g, b, alpha);
            ps.popPose();
        }
    }
}
