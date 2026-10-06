package dev.baranhan.flashmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.flashmod.FlashMod;
import dev.baranhan.flashmod.client.BlitzAnim;
import dev.baranhan.flashmod.client.BlitzClient;
import dev.baranhan.flashmod.client.ClientSpeedsters;
import dev.baranhan.flashmod.speed.BlitzPath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Blitz cizimi:
 *  - Hizci: gercek model senaryodaki konumda; kok donusumu (yon + egilme + alcalma + burulma + yatma) BlitzAnim'den,
 *    uzuv pozlari HumanoidModelMixin'den. Hizli hamlelerde yol boyunca MESAFEYE gore dizilen yogun hayaletler
 *    -> gercek hareket bulanikligi gibi surekli bir iz.
 *  - Hedef: sunucuda donmus varlik, burada senaryodaki yerinde/aciyla cizilir (savrulma, cokme, ucus).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class BlitzRenderer {
    private static final float[] POSE = new float[BlitzAnim.N];
    private static int pushedPlayer = Integer.MIN_VALUE, pushedTarget = Integer.MIN_VALUE;

    private BlitzRenderer() {}

    /** Gercek render yerine gore kok donusum; ardindan gelen renderer'in kendi govde donusunu iptal eder. */
    private static void root(PoseStack ps, Vec3 render, Vec3 at, float worldYaw, float bodyYaw,
                             float lean, float roll, float lower) {
        ps.translate(at.x - render.x, at.y - render.y - lower, at.z - render.z);
        ps.mulPose(Axis.YP.rotationDegrees(180F - worldYaw));
        ps.mulPose(Axis.XP.rotationDegrees(-lean));
        ps.mulPose(Axis.ZP.rotationDegrees(-roll));
        ps.mulPose(Axis.YP.rotationDegrees(-(180F - bodyYaw)));
    }

    // ---------------------------------------------------------------- hizci

    @SubscribeEvent
    public static void onPlayerPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        ClientSpeedsters.Entry e = ClientSpeedsters.get(p.getUUID());
        if (e == null || !e.blitz || e.blitzFrame == null || Minecraft.getInstance().level == null) return;
        float pt = event.getPartialTick();
        float bt = BlitzClient.renderTime(e, pt);
        BlitzAnim.speedsterPose(bt, POSE);
        Vec3 at = BlitzClient.puppet(p.level(), e, bt, pt);
        float yaw = e.blitzFrame.worldYaw(BlitzAnim.speedsterStageYaw(e.blitzFrame, bt) + POSE[BlitzAnim.TWIST]);
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        root(ps, p.getPosition(pt), at, yaw, Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot),
                POSE[BlitzAnim.LEAN], POSE[BlitzAnim.ROLL], POSE[BlitzAnim.LOWER]);
        pushedPlayer = p.getId();
    }

    @SubscribeEvent
    public static void onPlayerPost(RenderPlayerEvent.Post event) {
        Player p = event.getEntity();
        if (pushedPlayer != p.getId()) return;
        PoseStack ps = event.getPoseStack();
        ps.popPose();
        pushedPlayer = Integer.MIN_VALUE;

        ClientSpeedsters.Entry e = ClientSpeedsters.get(p.getUUID());
        if (e == null || !e.blitz || e.blitzFrame == null || !(p instanceof AbstractClientPlayer acp)) return;
        float pt = event.getPartialTick();
        float bt = BlitzClient.renderTime(e, pt);
        Vec3 render = p.getPosition(pt);

        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        ResourceLocation skin = event.getRenderer().getTextureLocation(acp);
        VertexConsumer vc = event.getMultiBufferSource().getBuffer(FlashRenderTypes.ghost(skin));
        float r = Mth.lerp(0.45F, 1F, GlowDraw.cr(e.glow));
        float g = Mth.lerp(0.45F, 1F, GlowDraw.cg(e.glow));
        float b = Mth.lerp(0.45F, 1F, GlowDraw.cb(e.glow));

        // Mesafe tabanli hareket bulanikligi: yol boyunca geriye dogru her ~0.2 blokta bir kopya
        final double spacing = 0.2D, maxLen = 2.8D;
        Vec3 prev = BlitzClient.puppet(p.level(), e, bt, pt);
        double acc = 0D, next = spacing;
        int made = 0;
        float[] gp = new float[BlitzAnim.N];
        for (int step = 1; step <= 48 && made < 14; step++) {
            float tk = bt - step * 0.05F;
            if (tk < 0F) break;
            Vec3 q = BlitzClient.puppet(p.level(), e, tk, pt);
            acc += q.distanceTo(prev);
            prev = q;
            if (acc > maxLen) break;
            if (acc < next) continue;
            next += spacing;
            made++;
            float alpha = 0.42F * (float) (1D - acc / maxLen);
            BlitzAnim.speedsterPose(tk, gp);
            float yaw = e.blitzFrame.worldYaw(BlitzAnim.speedsterStageYaw(e.blitzFrame, tk) + gp[BlitzAnim.TWIST]);
            ps.pushPose();
            ps.translate(q.x - render.x, q.y - render.y - gp[BlitzAnim.LOWER], q.z - render.z);
            ps.mulPose(Axis.YP.rotationDegrees(180F - yaw));
            ps.mulPose(Axis.XP.rotationDegrees(-gp[BlitzAnim.LEAN]));
            ps.mulPose(Axis.ZP.rotationDegrees(-gp[BlitzAnim.ROLL]));
            ps.scale(-1.0F, -1.0F, 1.0F);
            ps.scale(0.9375F, 0.9375F, 0.9375F);
            ps.translate(0.0F, -1.501F, 0.0F);
            model.renderToBuffer(ps, vc, event.getPackedLight(), OverlayTexture.NO_OVERLAY, r, g, b, alpha);
            ps.popPose();
        }
    }

    // ---------------------------------------------------------------- hedef

    @SubscribeEvent
    public static void onLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity ent = event.getEntity();
        if (ent instanceof Player) return;
        ClientSpeedsters.Entry e = BlitzClient.entryForTarget(ent.getId());
        if (e == null || e.blitzFrame == null) return;
        float pt = event.getPartialTick();
        float bt = BlitzClient.renderTime(e, pt);
        double[] tw = new double[6];
        BlitzPath.targetWorld(e.blitzFrame, bt, tw);
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        root(ps, ent.getPosition(pt), new Vec3(tw[0], tw[1], tw[2]), (float) tw[3],
                Mth.rotLerp(pt, ent.yBodyRotO, ent.yBodyRot), (float) tw[4], (float) tw[5], 0F);
        pushedTarget = ent.getId();
    }

    @SubscribeEvent
    public static void onLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (pushedTarget != event.getEntity().getId()) return;
        event.getPoseStack().popPose();
        pushedTarget = Integer.MIN_VALUE;
    }
}
