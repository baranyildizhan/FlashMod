package dev.baranhan.flashmod.client.ultimate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.flashmod.FlashMod;
import dev.baranhan.flashmod.client.render.FlashRenderTypes;
import dev.baranhan.flashmod.client.render.GlowDraw;
import dev.baranhan.flashmod.ultimate.ArenaFrame;
import dev.baranhan.flashmod.ultimate.UltimatePhase;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Caster/hedef cizimi (bizim Blitz yaklasimimiz): uzuvlar HumanoidModelMixin'den, govdenin konumu/yonu/egilmesi
 * RenderPlayerEvent.Pre'de PoseStack kok donusumuyle. DEPART'ta gercek govde proxy yolunda cizilir ve arkasinda
 * afterimage'lar birakir; 22-158 arasi dunyada gizli. Sahne cizimi (UltScene) override poz/kok ile ayni yolu kullanir.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class UltRender {
    /** Sahne cizimi sirasinda: bu varlik icin poz ve kok (sahne uzayinda dogrudan kullanilir). */
    @Nullable public static float[] overridePose;
    public static int overrideEntity = Integer.MIN_VALUE;
    public static float overrideYaw;
    private static int pushedPlayer = Integer.MIN_VALUE, pushedTarget = Integer.MIN_VALUE;
    private static final float[] POSE = new float[UltPoses.N], GP = new float[UltPoses.N];

    private UltRender() {}

    // ---------------------------------------------------------------- yardimcilar

    /** Kok donusumu: kalca pivotu (0.75) etrafinda egilme/bukulme/yuvarlanma, alcalma, ileri kayma. */
    public static void root(PoseStack ps, double dx, double dy, double dz, float worldYaw, float bodyYaw, float[] pz) {
        ps.translate(dx, dy + pz[UltPoses.RY], dz);
        ps.mulPose(Axis.YP.rotationDegrees(180F - worldYaw));
        ps.translate(0D, 0D, -pz[UltPoses.RZ]);
        ps.translate(0D, 0.75D, 0D);
        ps.mulPose(Axis.YP.rotationDegrees(-pz[UltPoses.RYAW]));
        ps.mulPose(Axis.XP.rotationDegrees(-pz[UltPoses.RP]));
        ps.mulPose(Axis.ZP.rotationDegrees(-pz[UltPoses.RROLL]));
        ps.translate(0D, -0.75D, 0D);
        ps.mulPose(Axis.YP.rotationDegrees(-(180F - bodyYaw))); // renderer'in kendi govde donusunu iptal
    }

    /** DEPART proxy yolu (arena): dugum indeksi zamanla carpitilir (22-26 IN_QUAD, 26-36 IN_EXPO). */
    public static Vec3 proxyArena(float t, float d) {
        Vec3[] k = {new Vec3(0, 0, 0), new Vec3(-0.45, 0, 1.40), new Vec3(-0.95, 0, d), new Vec3(-0.80, 0, d + 4.0),
                new Vec3(-0.60, 0, d + 14.0), new Vec3(-0.60, 0, d + 40.0)};
        float idx;
        if (t < 26F) idx = 2F * UltCamera.Ease.IN_QUAD.apply((t - 22F) / 4F);
        else idx = 2F + 3F * UltCamera.Ease.IN_EXPO.apply((t - 26F) / 10F);
        idx = Mth.clamp(idx, 0F, 5F);
        int i = Math.min(4, (int) idx);
        float u = idx - i;
        return UltCamera.catmull(k[Math.max(0, i - 1)], k[i], k[i + 1], k[Math.min(5, i + 2)], u);
    }

    /** Caster'in dunya konumu ve yaw'i (bu t'de, gercek govde ya da proxy). */
    public static Vec3 casterWorld(UltState s, Player p, float t, float pt, float[] yawOut) {
        if (t >= 22F && t < 40F) {
            Vec3 a = proxyArena(t, s.arena.d), b = proxyArena(t + 0.05F, s.arena.d);
            Vec3 dw = s.arena.toWorld(b).subtract(s.arena.toWorld(a));
            yawOut[0] = dw.lengthSqr() > 1.0E-8 ? UltCamera.yawTo(dw) : s.arena.forwardYaw();
            return s.arena.toWorld(a);
        }
        yawOut[0] = s.arena.forwardYaw();
        return p.getPosition(pt);
    }

    // ---------------------------------------------------------------- caster

    @SubscribeEvent
    public static void onPlayerPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        float pt = event.getPartialTick();
        PoseStack ps = event.getPoseStack();
        float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        if (overridePose != null && overrideEntity == p.getId()) { // sahne cizimi
            ps.pushPose();
            root(ps, 0, 0, 0, overrideYaw, bodyYaw, overridePose);
            pushedPlayer = p.getId();
            return;
        }
        UltState s = UltDirector.forCaster(p.getId());
        if (s == null || s.abortAt >= 0) return;
        float t = s.t(pt);
        if (t < 0F || t >= 200F) return;
        if (t >= 40F && t < UltimatePhase.HIT2) { // dunyada gizli (sahne / yorunge devralir)
            event.setCanceled(true);
            return;
        }
        float w = UltPoses.pose(t, POSE, s.smallTarget());
        for (int i = UltPoses.RP; i <= UltPoses.RZ; i++) POSE[i] *= w; // 196-200: koku de vanilla'ya birak
        float[] yaw = new float[1];
        Vec3 at = casterWorld(s, p, t, pt, yaw);
        float k = t < 4F ? t / 4F : w;                                // yon: giriste/cikista govde yonuyle karisim
        float useYaw = Mth.rotLerp(Mth.clamp(k, 0F, 1F), bodyYaw, yaw[0]);
        Vec3 render = p.getPosition(pt);
        ps.pushPose();
        root(ps, at.x - render.x, at.y - render.y, at.z - render.z, useYaw, bodyYaw, POSE);
        pushedPlayer = p.getId();
    }

    @SubscribeEvent
    public static void onPlayerPost(RenderPlayerEvent.Post event) {
        Player p = event.getEntity();
        if (pushedPlayer != p.getId()) return;
        event.getPoseStack().popPose();
        pushedPlayer = Integer.MIN_VALUE;
        if (overridePose != null && overrideEntity == p.getId()) return;
        UltState s = UltDirector.forCaster(p.getId());
        if (s == null || !(p instanceof AbstractClientPlayer acp)) return;
        float pt = event.getPartialTick();
        float t = s.t(pt);
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        ResourceLocation skin = event.getRenderer().getTextureLocation(acp);
        Vec3 render = p.getPosition(pt);
        PoseStack ps = event.getPoseStack();
        MultiBufferSource buf = event.getMultiBufferSource();
        int light = event.getPackedLight();
        if (t >= 22F && t < 40F) { // proxy afterimage: 8 kopya, 0.5 tick arayla
            float vt = UltimatePhase.visual(t);
            for (int i = 1; i <= 8; i++) {
                float tk = vt - i * 0.5F;
                if (tk < 22F) break;
                float a = 0.6F * (float) Math.pow(1F - i / 9F, 1.5);
                ghost(s, p, model, skin, ps, buf, light, tk, pt, render, a, mixWhite(s.glow, 0.25F + 0.5F * i / 8F), 0F);
            }
        } else if (t >= 14F && t < 22F) { // kromatik kayma taklidi: kirmizi saga, camgobegi sola
            ghost(s, p, model, skin, ps, buf, light, t, pt, render, 0.25F, 0xFF5050, 0.04F);
            ghost(s, p, model, skin, ps, buf, light, t, pt, render, 0.25F, 0x50FFFF, -0.04F);
        }
    }

    private static void ghost(UltState s, Player p, HumanoidModel<?> model, ResourceLocation skin, PoseStack ps,
                              MultiBufferSource buf, int light, float tk, float pt, Vec3 render, float alpha, int rgb,
                              float sideShift) {
        UltPoses.pose(tk, GP, s.smallTarget());
        float[] yaw = new float[1];
        Vec3 at = casterWorld(s, p, tk, pt, yaw);
        ArenaFrame a = s.arena;
        at = at.add(a.rx() * sideShift, 0, a.rz() * sideShift);
        VertexConsumer vc = buf.getBuffer(FlashRenderTypes.ghost(skin));
        drawModel(model, ps, vc, light, at.x - render.x, at.y - render.y, at.z - render.z, yaw[0], GP, alpha, rgb);
    }

    /** Pozlu modeli (afterimage) dogrudan cizer: kok + uzuvlar. PoseStack orijini 'render' konumudur. */
    public static void drawModel(HumanoidModel<?> model, PoseStack ps, VertexConsumer vc, int light, double dx, double dy,
                                 double dz, float worldYaw, float[] pz, float alpha, int rgb) {
        UltPoses.apply(model, pz, 1F);
        ps.pushPose();
        ps.translate(dx, dy + pz[UltPoses.RY], dz);
        ps.mulPose(Axis.YP.rotationDegrees(180F - worldYaw));
        ps.translate(0D, 0.75D, -pz[UltPoses.RZ]);
        ps.mulPose(Axis.YP.rotationDegrees(-pz[UltPoses.RYAW]));
        ps.mulPose(Axis.XP.rotationDegrees(-pz[UltPoses.RP]));
        ps.mulPose(Axis.ZP.rotationDegrees(-pz[UltPoses.RROLL]));
        ps.translate(0D, -0.75D, 0D);
        ps.scale(-1.0F, -1.0F, 1.0F);
        ps.scale(0.9375F, 0.9375F, 0.9375F);
        ps.translate(0.0F, -1.501F, 0.0F);
        model.renderToBuffer(ps, vc, light, OverlayTexture.NO_OVERLAY, GlowDraw.cr(rgb), GlowDraw.cg(rgb), GlowDraw.cb(rgb), alpha);
        ps.popPose();
    }

    public static int mixWhite(int c, float k) {
        return GlowDraw.mixRgb(0xFFFFFF, c, Mth.clamp(k, 0F, 1F));
    }

    // ---------------------------------------------------------------- hedef

    /** 158-162 darbe pozu: hafif geriye bukulmus (oyuncu/humanoid ise kok egilmesi). */
    @SubscribeEvent
    public static void onLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity ent = event.getEntity();
        UltState s = UltDirector.forTarget(ent.getId());
        if (s == null || s.abortAt >= 0) return;
        float t = s.t(event.getPartialTick());
        if (t < UltimatePhase.HIT2 || t >= UltimatePhase.HITSTOP_END + 2F) return;
        float k = t < UltimatePhase.HITSTOP_END ? Math.min(1F, (t - UltimatePhase.HIT2) / 0.6F)
                : 1F - (t - UltimatePhase.HITSTOP_END) / 2F;
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        double back = 0.15D * k;
        ps.translate(s.arena.fx * back, 0D, s.arena.fz * back);
        ps.translate(0D, ent.getBbHeight() * 0.3D, 0D);
        // geriye (forward yonunde) egil: eksen = right
        ps.mulPose(new org.joml.Quaternionf().rotationAxis((float) Math.toRadians(-15.0 * k), (float) s.arena.rx(), 0F,
                (float) s.arena.rz()));
        ps.translate(0D, -ent.getBbHeight() * 0.3D, 0D);
        pushedTarget = ent.getId();
    }

    @SubscribeEvent
    public static void onLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (pushedTarget != event.getEntity().getId()) return;
        event.getPoseStack().popPose();
        pushedTarget = Integer.MIN_VALUE;
    }

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (overridePose != null && overrideEntity == event.getEntity().getId()) event.setResult(Event.Result.DENY);
        else if (UltDirector.forCaster(event.getEntity().getId()) != null) event.setResult(Event.Result.DENY);
    }
}
