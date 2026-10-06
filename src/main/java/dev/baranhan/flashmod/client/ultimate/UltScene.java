package dev.baranhan.flashmod.client.ultimate;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.baranhan.flashmod.FlashMod;
import dev.baranhan.flashmod.client.ClientSpeedsters;
import dev.baranhan.flashmod.client.render.BodyPoseCapture;
import dev.baranhan.flashmod.client.render.SpeedTrailRenderer;
import dev.baranhan.flashmod.client.render.FlashRenderTypes;
import dev.baranhan.flashmod.config.FlashClientConfig;
import dev.baranhan.flashmod.ultimate.UltimatePhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * SCENE fazlari icin overlay tekniği (rehber 9.3): yeni boyut yok; dunya cizildikten sonra (AFTER_LEVEL) once
 * kameraya merkezli backdrop (derinliksiz), sonra derinlik temizlenip sahne geometrisi sahne kamerasina goreli
 * cizilir. Uzak sahneler icin genis far plane'li kendi projeksiyonumuz. Caster modeli bizim poz/kok yolumuzla.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class UltScene {
    public interface Scene {
        void backdrop(Ctx c);
        void geometry(Ctx c);
        /** Caster modeli + afterimage + damar (yoksa bos). */
        default void model(Ctx c) {}
    }

    /** Cizim baglami. view: sadece donus (roll dahil); m: view * (-sahneKamerasi). right/up: kamera eksenleri. */
    public static final class Ctx {
        public UltState s;
        public float t, vt, pt, quality;
        public Matrix4f view = new Matrix4f(), m = new Matrix4f();
        public Vec3 cam = Vec3.ZERO;
        public final float[] right = new float[3], up = new float[3], fwd = new float[3];
        public int core, glow;
        public boolean reduceFlashes;
    }

    private static final Ctx CTX = new Ctx();
    private static final Scene VOID = new UltVoidScene(), OCEAN = new UltOceanScene(), ORBIT = new UltOrbitScene(),
            TUNNEL = new UltTunnelScene();

    /** Takip kamerasi icin: t'deki fazin kosucusunun sahne konumu, 'at' aninda. */
    public static Vec3 runner(float t, float at) {
        UltimatePhase ph = UltimatePhase.at(t);
        if (ph == UltimatePhase.VOID) return UltVoidScene.PATH.at(at);
        if (ph == UltimatePhase.OCEAN) return UltOceanScene.PATH.at(at);
        return Vec3.ZERO;
    }

    private UltScene() {}

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        UltState s = UltDirector.scene(event.getPartialTick());
        if (s == null) return;
        Minecraft mc = Minecraft.getInstance();
        float pt = event.getPartialTick();
        float t = s.t(pt);
        UltimatePhase ph = UltimatePhase.at(t);
        Scene scene = ph == UltimatePhase.VOID ? VOID : ph == UltimatePhase.OCEAN ? OCEAN : ph == UltimatePhase.ORBIT ? ORBIT
                : ph == UltimatePhase.TUNNEL ? TUNNEL : null;
        if (scene == null) return;
        UltTextures.ensure();

        Ctx c = CTX;
        c.s = s;
        c.t = t;
        c.vt = UltimatePhase.visual(t);
        c.pt = pt;
        c.quality = FlashClientConfig.ULT_QUALITY.get().mul;
        c.core = s.core;
        c.glow = s.glow;
        c.reduceFlashes = FlashClientConfig.ULT_REDUCE_FLASHES.get();
        c.cam = s.sceneCam;
        c.view.set(BodyPoseCapture.view());
        c.m.set(c.view).translate((float) -c.cam.x, (float) -c.cam.y, (float) -c.cam.z);
        c.right[0] = c.view.m00(); c.right[1] = c.view.m10(); c.right[2] = c.view.m20();
        c.up[0] = c.view.m01(); c.up[1] = c.view.m11(); c.up[2] = c.view.m21();
        c.fwd[0] = -c.view.m02(); c.fwd[1] = -c.view.m12(); c.fwd[2] = -c.view.m22();

        Matrix4f oldProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting oldSort = RenderSystem.getVertexSorting();
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(UltDirector.fov()), aspect, 0.05F, 6000F);
        RenderSystem.setProjectionMatrix(proj, VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        FogRenderer.setupNoFog();
        try {
            scene.backdrop(c);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            scene.geometry(c);
            scene.model(c);
        } finally {
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(oldProj, oldSort);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
        }
    }

    // ---------------------------------------------------------------- caster modeli (sahne uzayinda)

    private static final float[] POSE = new float[UltPoses.N], GP = new float[UltPoses.N];

    public interface Path { Vec3 at(float t); }

    /** Iz yolu: konum + o ana kadar katedilen mesafe (iz dalgalari odometreye sabit, kareden kareye kaymaz). */
    public interface TrailPath extends Path {
        double odo(float t);

        /** Yerel yukari (izin "yukseklik" ekseni). Varsayilan dunya yukarisi. */
        default Vec3 up(Vec3 p) { return UP; }
    }

    private static final Vec3 UP = new Vec3(0, 1, 0);

    /**
     * Iz yolunu bizim iz node'larina cevirir (yeni -> eski, her tick bir node). Node tick'i k-1: buildPath'in yas
     * hesabi (now - tick - 1 + pt) / life boylece tam olarak (vt - k) / life olur. scale: iz birimi.
     */
    public static void fillNodes(ClientSpeedsters.Entry e, TrailPath path, float vt, float minT, int life, float scale,
                                 float alpha) {
        e.trailLifeOverride = life;
        e.nodes.clear();
        long now = (long) Math.floor(vt);
        double inv = 1.0 / scale;
        for (long k = now; k >= now - life - 1 && k >= (long) Math.ceil(minT); k--) {
            Vec3 p = path.at(k), a = path.at(k + 0.5F), b = path.at(k - 0.5F);
            Vec3 dir = a.subtract(b);
            Vec3 up = path.up(p);
            Vec3 side = dir.cross(up);
            double sl = side.length();
            side = sl < 1.0E-6 ? new Vec3(1, 0, 0) : side.scale(1.0 / sl);
            e.nodes.addLast(new ClientSpeedsters.TrailNode(p.x * inv, p.y * inv, p.z * inv, (float) side.x, (float) side.y,
                    (float) side.z, (float) up.x, (float) up.y, (float) up.z, path.odo(k) * inv, alpha, k - 1, 1F));
        }
        if (e.nodes.isEmpty()) return;
        ClientSpeedsters.TrailNode first = e.nodes.peekFirst(); // kafa noktasi ilk node'un cercevesini kullanir
        e.sideX = first.sideX(); e.sideY = first.sideY(); e.sideZ = first.sideZ();
        e.upX = first.upX(); e.upY = first.upY(); e.upZ = first.upZ();
    }

    /**
     * Sahnedeki kosucunun izi bizim iz sistemimizle (SpeedTrailRenderer.drawSynthetic): yol her tick'te orneklenir,
     * node'lar normal oyuncu iziyle ayni bicimde uretilir. scale > 1: iz sahnede o kadar buyuk cizilir (yorunge).
     * withBody: govde pozu (drawCaster'da yakalanan) varsa iplikler govdeden cikar.
     */
    public static void drawTrail(Ctx c, TrailPath path, float minT, int life, float scale, boolean withBody, float alpha) {
        ClientSpeedsters.Entry e = c.s.sceneTrail;
        float vt = c.vt;
        long now = (long) Math.floor(vt);
        float pt = vt - now;
        double inv = 1.0 / scale;
        fillNodes(e, path, vt, minT, life, scale, alpha);
        if (e.nodes.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 head = path.at(vt);
        Vec3 cam = c.cam.scale(inv);
        BodyPoseCapture.Pose body = null;
        Player p = c.s.caster();
        if (withBody && scale == 1F && p != null) body = BodyPoseCapture.get(p);
        Matrix4f m = new Matrix4f(c.view).scale(scale);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(FlashRenderTypes.ADDITIVE_GLOW);
        float bloom = Math.max(0.6F, FlashClientConfig.BLOOM.get().floatValue());
        SpeedTrailRenderer.drawSynthetic(vc, m, e, cam, (float) (head.x * inv - cam.x), (float) (head.y * inv - cam.y),
                (float) (head.z * inv - cam.z), 1F, body, mc.gameRenderer.getMainCamera().getPosition(), now, pt, bloom,
                Math.max(6, FlashClientConfig.STRANDS.get()), withBody, true);
        buffers.endBatch(FlashRenderTypes.ADDITIVE_GLOW);
    }

    /**
     * Caster'i sahne konumunda pozla cizer (entity renderer -> RenderPlayerEvent -> UltRender override kok),
     * sonra afterimage'lar ve simsek damarlari. drawMain=false: ana model cizilmez (sadece hayaletler).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void drawCaster(Ctx c, Path path, float yaw, boolean drawMain, int ghosts, float spacing, float ghostA0,
                                  float veinIntensity, float eyeGlow) {
        Player p = c.s.caster();
        if (!(p instanceof AbstractClientPlayer acp)) return;
        Minecraft mc = Minecraft.getInstance();
        EntityRenderer<? super Player> r = mc.getEntityRenderDispatcher().getRenderer(p);
        if (!(r instanceof LivingEntityRenderer lr) || !(lr.getModel() instanceof HumanoidModel<?> model)) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 at = path.at(c.vt);
        float w = UltPoses.pose(c.t, POSE, false);
        if (w <= 0F) UltPoses.pose(Math.max(0F, c.vt), POSE, false);
        PoseStack ps = new PoseStack();
        ps.last().pose().set(c.view);
        ps.last().normal().set(new org.joml.Matrix3f(c.view));
        ps.translate(at.x - c.cam.x, at.y - c.cam.y, at.z - c.cam.z);
        if (drawMain) {
            UltRender.overridePose = POSE;
            UltRender.overrideEntity = p.getId();
            UltRender.overrideYaw = yaw;
            try {
                r.render(p, yaw, c.pt, ps, buffers, LightTexture.FULL_BRIGHT);
            } finally {
                UltRender.overridePose = null;
                UltRender.overrideEntity = Integer.MIN_VALUE;
            }
            buffers.endBatch();
        }
        if (ghosts > 0 && ghostA0 > 0.001F) {
            ResourceLocation skin = lr.getTextureLocation(acp);
            VertexConsumer vc = buffers.getBuffer(FlashRenderTypes.ghost(skin));
            PoseStack gs = new PoseStack();
            gs.last().pose().set(c.view);
            gs.last().normal().set(new org.joml.Matrix3f(c.view));
            for (int i = 1; i <= ghosts; i++) {
                float tk = c.vt - i * spacing;
                UltPoses.pose(tk, GP, false);
                Vec3 g = path.at(tk);
                float a = ghostA0 * (float) Math.pow(1F - i / (float) (ghosts + 1), 1.5);
                UltRender.drawModel(model, gs, vc, LightTexture.FULL_BRIGHT, g.x - c.cam.x, g.y - c.cam.y, g.z - c.cam.z,
                        yaw, GP, a, UltRender.mixWhite(c.glow, 0.25F + 0.5F * i / ghosts));
            }
            buffers.endBatch();
        }
        if (drawMain && (veinIntensity > 0.01F || eyeGlow > 0.01F)) {
            BodyPoseCapture.Pose body = BodyPoseCapture.get(p);
            if (body != null) {
                VertexConsumer vc = buffers.getBuffer(FlashRenderTypes.ADDITIVE_GLOW);
                Vec3 real = mc.gameRenderer.getMainCamera().getPosition();
                if (veinIntensity > 0.01F) UltWorldFx.veins(vc, c.view, real, body, c.s, c.vt, veinIntensity, c.core, c.glow);
                if (eyeGlow > 0.01F) UltWorldFx.eyes(vc, c.view, real, body, eyeGlow, c.core, c.glow);
                buffers.endBatch(FlashRenderTypes.ADDITIVE_GLOW);
            }
        }
    }
}
