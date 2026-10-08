package dev.baranhan.flashmod.client.skill;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.flashmod.FlashMod;
import dev.baranhan.flashmod.client.render.BodyPoseCapture;
import dev.baranhan.flashmod.client.render.FlashRenderTypes;
import dev.baranhan.flashmod.client.render.GlowDraw;
import dev.baranhan.flashmod.client.ultimate.UltRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Geri sarmanin hayaletleri (hizcinin modeli ve skin'iyle):
 *  - Yankilar: geri sarilan yol boyunca gecmis hallerin (kosuyorsa kosu, duruyorsa durus karesi), hizci yanlarindan
 *    gectikce icine cekilip kaybolur. Varis noktasinda daha parlak, nabiz atan "gecmis ben".
 *  - Bant titremesi: geri sarilan hizcinin iki yana kaymis, iki renge boyanmis yari saydam kopyalari.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class SkillGhosts {
    private static final double[] S = new double[6];
    /** Yankilarin yol uzerindeki araligi (ornek = tick). */
    private static final int ECHO_STEP = 12;

    private SkillGhosts() {}

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || SkillClient.REWINDS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = event.getPartialTick();
        float t = mc.level.getGameTime() + pt;
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f view = BodyPoseCapture.view();
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            for (SkillClient.Rewind r : SkillClient.REWINDS.values()) {
                if (r.ended()) continue;
                Player p = mc.level.getPlayerByUUID(r.player);
                if (!(p instanceof AbstractClientPlayer acp)) continue;
                EntityRenderer<? super Player> er = mc.getEntityRenderDispatcher().getRenderer(p);
                if (!(er instanceof LivingEntityRenderer lr) || !(lr.getModel() instanceof HumanoidModel<?> model)) continue;
                ResourceLocation skin = lr.getTextureLocation(acp);
                VertexConsumer vc = buffers.getBuffer(FlashRenderTypes.glowGhost(skin));
                PoseStack ps = new PoseStack();
                ps.last().pose().set(view);
                ps.last().normal().set(new Matrix3f(view));
                float idx = r.progress(t) * (r.count - 1);
                float fadeIn = Mth.clamp((t - r.start) / 5F, 0F, 1F);
                int tint = UltRender.mixWhite(r.glow, 0.4F);
                int k = 0;
                for (int i = ECHO_STEP; i < r.count - 1; i += ECHO_STEP, k++) {
                    float d = i - idx;                       // >0: henuz gecilmedi
                    if (d < -3F) continue;
                    float a = 0.4F * fadeIn * Mth.clamp((d + 3F) / 4F, 0F, 1F);    // gecerken icine cekilir
                    if (a < 0.01F) continue;
                    r.sample(i, S);
                    float[] pose = S[5] > 0.3 ? (k % 2 == 0 ? SkillPoses.RUN : SkillPoses.RUN_B) : SkillPoses.STANCE;
                    UltRender.drawModel(model, ps, vc, LightTexture.FULL_BRIGHT, S[0] - cam.x, S[1] - cam.y, S[2] - cam.z,
                            (float) S[3], pose, a, tint);
                }
                // varis: "gecmis ben", nabiz atar, yaklastikca belirginlesir
                r.sample(r.count - 1, S);
                float prog = idx / Math.max(1F, r.count - 1);
                float pulse = 0.85F + 0.15F * Mth.sin(t * 0.6F);
                float[] pose = S[5] > 0.3 ? SkillPoses.RUN : SkillPoses.STANCE;
                UltRender.drawModel(model, ps, vc, LightTexture.FULL_BRIGHT, S[0] - cam.x, S[1] - cam.y, S[2] - cam.z,
                        (float) S[3], pose, (0.45F + 0.4F * prog) * pulse * fadeIn, UltRender.mixWhite(r.core, 0.6F));
                buffers.endBatch();
            }
        } finally {
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /** Bant titremesi: gercek model cizildikten hemen sonra ayni pozla, iki yana kaymis renkli kopyalar. */
    @SubscribeEvent
    public static void onPlayerPost(RenderPlayerEvent.Post event) {
        Player p = event.getEntity();
        if (!SkillClient.rewinding(p.getUUID()) || !(p instanceof AbstractClientPlayer acp)) return;
        SkillClient.Rewind r = SkillClient.REWINDS.get(p.getUUID());
        Minecraft mc = Minecraft.getInstance();
        if (r == null || mc.level == null) return;
        float pt = event.getPartialTick();
        float t = mc.level.getGameTime() + pt;
        float fadeIn = Mth.clamp((t - r.start) / 4F, 0F, 1F);
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        ResourceLocation skin = event.getRenderer().getTextureLocation(acp);
        VertexConsumer vc = event.getMultiBufferSource().getBuffer(FlashRenderTypes.ghost(skin));
        PoseStack ps = event.getPoseStack();
        float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        float yr = bodyYaw * Mth.DEG_TO_RAD;
        float rx = -Mth.cos(yr), rz = -Mth.sin(yr);
        float jitter = 0.07F + 0.05F * Mth.sin(t * 2.7F) + (((int) (t * 3F)) % 5 == 0 ? 0.08F : 0F);
        int[] cols = {r.core, r.glow};
        for (int s = 0; s < 2; s++) {
            float side = s == 0 ? 1F : -1F;
            int c = cols[s];
            ps.pushPose();
            ps.translate(rx * jitter * side, 0.0, rz * jitter * side);
            ps.mulPose(Axis.YP.rotationDegrees(180F - bodyYaw));
            ps.scale(-1.0F, -1.0F, 1.0F);
            ps.scale(0.9375F, 0.9375F, 0.9375F);
            ps.translate(0.0F, -1.501F, 0.0F);
            model.renderToBuffer(ps, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, GlowDraw.cr(c), GlowDraw.cg(c),
                    GlowDraw.cb(c), 0.3F * fadeIn);
            ps.popPose();
        }
    }
}
