package dev.baranhan.flashmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.flashmod.FlashMod;
import dev.baranhan.flashmod.client.ClientSpeedsters;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * O karede GERCEKTEN cizilen oyuncu modelinin uzuv matrislerini yakalar (LivingEntityRendererMixin).
 *
 * Model cizilirken PoseStack = Gorus * Oteleme(entity - kamera) * govde donusu * olcek; her ModelPart kendi
 * pivot/donusunu ekler. Her uzuv icin (ters gorus * PoseStack) matrisini sakliyoruz: bu matris uzvun KENDI
 * kupunun icindeki herhangi bir noktayi kameraya goreli dunya koordinatina cevirir. Boylece simsekler kafanin,
 * govdenin, kollarin, bacaklarin yuzeyindeki istedigimiz her noktaya, zirh/kask gibi her karede tam yapisik baglanir.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class BodyPoseCapture {
    public static final int HEAD = 0, BODY = 1, R_ARM = 2, L_ARM = 3, R_LEG = 4, L_LEG = 5, PARTS = 6;

    /** Uzuv kuplari, parca-yerel piksel (min x,y,z, max x,y,z). Vanilla oyuncu modeli (genis kol). */
    public static final float[][] BOX = {
            {-4, -8, -4, 4, 0, 4},     // kafa
            {-4, 0, -2, 4, 12, 2},     // govde
            {-3, -2, -2, 1, 10, 2},    // sag kol
            {-1, -2, -2, 3, 10, 2},    // sol kol
            {-2, 0, -2, 2, 12, 2},     // sag bacak
            {-2, 0, -2, 2, 12, 2},     // sol bacak
    };
    /** Ayakta duruste uzuv pivotlari (model uzayi, piksel). Iz formunun kalici ofsetleri buradan turetilir. */
    public static final float[][] PIVOT = {
            {0, 0, 0}, {0, 0, 0}, {-5, 2, 0}, {5, 2, 0}, {-1.9F, 12, 0}, {1.9F, 12, 0},
    };
    /** Yuzey alanina kabaca orantili secim agirliklari (toplam 1). */
    public static final float[] WEIGHT = {0.14F, 0.30F, 0.14F, 0.14F, 0.14F, 0.14F};

    public static final class Pose {
        final Matrix4f[] part = new Matrix4f[PARTS];
        double camX, camY, camZ;
        double ox, oy, oz;
        long frame = -1;

        Pose() {
            for (int i = 0; i < PARTS; i++) part[i] = new Matrix4f();
        }

        /** Ayaklarin (entity orijini) mutlak dunya konumu, o karede cizildigi yer. */
        public double originX() { return ox; }
        public double originY() { return oy; }
        public double originZ() { return oz; }

        /**
         * Uzvun kendi kupundeki (piksel) noktanin, verilen kameraya goreli dunya konumu.
         * out[0..2] = x, y, z.
         */
        public void point(int part, float px, float py, float pz, Vec3 cam, float[] out) {
            Vector4f v = SCRATCH.set(px / 16F, py / 16F, pz / 16F, 1F);
            this.part[part].transform(v);
            out[0] = (float) (v.x() + camX - cam.x);
            out[1] = (float) (v.y() + camY - cam.y);
            out[2] = (float) (v.z() + camZ - cam.z);
        }
    }

    private static final Map<Integer, Pose> POSES = new HashMap<>();
    private static long frame;
    private static long viewFrame = -1;
    private static float lastRoll;
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f INV_VIEW = new Matrix4f();
    private static final Vector4f TMP = new Vector4f();
    private static final Vector4f SCRATCH = new Vector4f();

    private BodyPoseCapture() {}

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            frame++;
            if (frame % 600 == 0) POSES.entrySet().removeIf(en -> frame - en.getValue().frame > 600);
        }
    }

    /** Bu karenin son kamera roll'u (tum modlar ComputeCameraAngles'i degistirdikten sonra). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        lastRoll = event.getRoll();
        viewFrame = -1;
    }

    /** GameRenderer ile birebir ayni gorus matrisi (roll -> pitch -> yaw+180). Kare basina bir kez hesaplanir. */
    public static Matrix4f view() {
        ensureView();
        return VIEW;
    }

    private static void ensureView() {
        if (viewFrame == frame) return;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        VIEW.identity()
                .rotate(Axis.ZP.rotationDegrees(lastRoll))
                .rotate(Axis.XP.rotationDegrees(camera.getXRot()))
                .rotate(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F));
        VIEW.invert(INV_VIEW);
        viewFrame = frame;
    }

    /** Bu karede yakalanmis govde pozu, yoksa null (birinci sahista kendi modelin cizilmez -> null). */
    @Nullable
    public static Pose get(Player p) {
        Pose pose = POSES.get(p.getId());
        return pose != null && pose.frame == frame ? pose : null;
    }

    /** LivingEntityRendererMixin'den, model cizildikten hemen sonra cagrilir (PoseStack tam model donusumunde). */
    public static void capture(LivingEntity entity, PoseStack ps, EntityModel<?> model) {
        if (!(entity instanceof Player p) || !(model instanceof HumanoidModel<?> hm)) return;
        ClientSpeedsters.Entry e = ClientSpeedsters.get(p.getUUID());
        if (e == null || !(e.active || e.tornado || !e.nodes.isEmpty())) return;
        ensureView();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Pose pose = POSES.computeIfAbsent(p.getId(), k -> new Pose());
        pose.frame = frame;
        pose.camX = cam.x;
        pose.camY = cam.y;
        pose.camZ = cam.z;

        // Orijin: translate(0,-1.501,0) geri alininca model uzayinda (0, 1.501, 0) = ayaklar
        TMP.set(0F, 1.501F, 0F, 1F);
        ps.last().pose().transform(TMP);
        INV_VIEW.transform(TMP);
        pose.ox = TMP.x() + cam.x;
        pose.oy = TMP.y() + cam.y;
        pose.oz = TMP.z() + cam.z;

        store(pose, HEAD, ps, hm.head);
        store(pose, BODY, ps, hm.body);
        store(pose, R_ARM, ps, hm.rightArm);
        store(pose, L_ARM, ps, hm.leftArm);
        store(pose, R_LEG, ps, hm.rightLeg);
        store(pose, L_LEG, ps, hm.leftLeg);
    }

    private static void store(Pose pose, int idx, PoseStack ps, ModelPart part) {
        ps.pushPose();
        part.translateAndRotate(ps);
        pose.part[idx].set(INV_VIEW).mul(ps.last().pose()); // parca-yerel -> kameraya goreli dunya
        ps.popPose();
    }
}
