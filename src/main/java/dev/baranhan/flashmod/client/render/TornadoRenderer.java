package dev.baranhan.flashmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.flashmod.FlashMod;
import dev.baranhan.flashmod.client.ClientSpeedsters;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tornado sirasinda oyuncu modeli:
 *  - Minecraft konumu iki tick arasinda DUZ cizgi (kiris) boyunca enterpole eder; Mach 1'de bu cemberin icinden
 *    gecen anlamsiz bir ziplama olur. Modeli her karede cemberin YAYI uzerindeki dogru noktaya tasiyoruz.
 *  - Govde teget yone doner (kafa/bakis serbest kalir).
 *  - Arkasinda yay boyunca dizilen, giderek solan hayalet kopyalar -> donen bulanik figur halkasi.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class TornadoRenderer {
    private static final float DEG = (float) Math.PI / 180F;
    private static int pushedId = Integer.MIN_VALUE;

    private TornadoRenderer() {}

    /** Hareket yonu (teget) icin Minecraft yaw'i, derece. */
    private static float tangentYaw(float angle, int dir) {
        float fx = -Mth.sin(angle) * dir, fz = Mth.cos(angle) * dir;
        return (float) Math.toDegrees(Math.atan2(-fx, fz));
    }

    @SubscribeEvent
    public static void onPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        ClientSpeedsters.Entry e = ClientSpeedsters.get(p.getUUID());
        if (e == null || !e.tornado) return;
        float pt = event.getPartialTick();
        float a = e.tornadoAngle(pt);
        float R = ClientSpeedsters.TORNADO_RADIUS;
        double ax = e.tcx + Mth.cos(a) * R, az = e.tcz + Mth.sin(a) * R;
        double rx = Mth.lerp(pt, p.xo, p.getX()), rz = Mth.lerp(pt, p.zo, p.getZ());
        float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(ax - rx, 0.0D, az - rz);
        ps.mulPose(Axis.YP.rotationDegrees(bodyYaw - tangentYaw(a, e.tDir)));
        pushedId = p.getId();
    }

    @SubscribeEvent
    public static void onPost(RenderPlayerEvent.Post event) {
        Player p = event.getEntity();
        if (pushedId != p.getId()) return;
        PoseStack ps = event.getPoseStack();
        ps.popPose();
        pushedId = Integer.MIN_VALUE;

        ClientSpeedsters.Entry e = ClientSpeedsters.get(p.getUUID());
        if (e == null || !e.tornado || !(p instanceof AbstractClientPlayer acp)) return;
        float k = Mth.clamp(e.tSpeed / 6F, 0F, 1F);
        int ghosts = Math.min(14, (int) (e.tSpeed * 1.2F));
        if (ghosts <= 0) return;

        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        ResourceLocation skin = event.getRenderer().getTextureLocation(acp);
        VertexConsumer vc = event.getMultiBufferSource().getBuffer(FlashRenderTypes.ghost(skin));

        float pt = event.getPartialTick();
        float a = e.tornadoAngle(pt);
        float R = ClientSpeedsters.TORNADO_RADIUS;
        double rx = Mth.lerp(pt, p.xo, p.getX()), rz = Mth.lerp(pt, p.zo, p.getZ());
        // Bir tick'te katedilen yay kadar (en fazla ~1 tur) arkaya dagit
        float arc = Math.min((float) (Math.PI * 2.0) * 0.95F, e.tSpeed / R * 1.1F);

        float r = Mth.lerp(0.4F, 1F, GlowDraw.cr(e.glow));
        float g = Mth.lerp(0.4F, 1F, GlowDraw.cg(e.glow));
        float b = Mth.lerp(0.4F, 1F, GlowDraw.cb(e.glow));
        for (int i = 1; i <= ghosts; i++) {
            float u = i / (float) (ghosts + 1);
            float ga = a - e.tDir * arc * u;
            double gx = e.tcx + Mth.cos(ga) * R - rx, gz = e.tcz + Mth.sin(ga) * R - rz;
            float alpha = 0.42F * (1F - u) * k;
            if (alpha < 0.01F) continue;
            ps.pushPose();
            ps.translate(gx, 0.0D, gz);
            ps.mulPose(Axis.YP.rotationDegrees(180.0F - tangentYaw(ga, e.tDir)));
            ps.scale(-1.0F, -1.0F, 1.0F);
            ps.scale(0.9375F, 0.9375F, 0.9375F);
            ps.translate(0.0F, -1.501F, 0.0F);
            model.renderToBuffer(ps, vc, event.getPackedLight(), OverlayTexture.NO_OVERLAY, r, g, b, alpha);
            ps.popPose();
        }
    }
}
