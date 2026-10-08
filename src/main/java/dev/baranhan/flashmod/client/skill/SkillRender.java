package dev.baranhan.flashmod.client.skill;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.flashmod.FlashMod;
import dev.baranhan.flashmod.client.ClientSpeedsters;
import dev.baranhan.flashmod.client.render.BodyPoseCapture;
import dev.baranhan.flashmod.client.render.FlashRenderTypes;
import dev.baranhan.flashmod.client.render.GlowDraw;
import dev.baranhan.flashmod.client.render.Lightning;
import dev.baranhan.flashmod.client.render.Polyline;
import dev.baranhan.flashmod.client.ultimate.UltWorldFx;
import dev.baranhan.flashmod.config.FlashClientConfig;
import dev.baranhan.flashmod.network.SkillFxPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Yeteneklerin dunya ici efektleri (herkes gorur), bizim simsek altyapimizla (Lightning + GlowDraw):
 *  - Kinetik yuk: yumruk kolunda yuke gore artan, kol boyunca gezinen simsekler ve yumrukta biriken isik;
 *    tam yukte yumruktan yere sizan arklar. Birinci sahista ekrandaki elde.
 *  - Kinetik isabet: darbe noktasinda simsek patlamasi + darbe yonunde ilerleyen sok halkalari.
 *  - Zaman kalintisi: kalintidan hizciya uzanan kopma simsekleri; parcalanma (statik bosalma) patlamasi ve halkasi.
 *  - Geri sarma: kalan yol boyunca titreyen simsek seridi, varis noktasinda ters donen saat kadrani ve isik sutunu,
 *    hizcinin belinde ters donen saat halkasi; bitiste zamanin yerine oturdugu patlama.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class SkillRender {
    private static final Polyline LINE = new Polyline();
    private static final Lightning.Rng R = new Lightning.Rng(91), B = new Lightning.Rng(92);
    private static final float[] P = new float[3], Q = new float[3];
    private static final double[] S = new double[6], S2 = new double[6];

    private SkillRender() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        boolean any = !SkillClient.HITS.isEmpty() || !SkillClient.BURSTS.isEmpty() || !SkillClient.REWINDS.isEmpty();
        if (!any) {
            for (Player p : mc.level.players()) {
                SkillClient.Info i = SkillClient.infoOrNull(p.getUUID());
                if (i != null && i.kinetic > 3F) { any = true; break; }
            }
        }
        if (!any) return;
        float pt = event.getPartialTick();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Matrix4f m = new Matrix4f(BodyPoseCapture.view());
        long now = mc.level.getGameTime();
        float t = now + pt;
        float bloom = Math.max(0.6F, FlashClientConfig.BLOOM.get().floatValue());
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            VertexConsumer vc = buffers.getBuffer(FlashRenderTypes.ADDITIVE_GLOW);
            for (Player p : mc.level.players()) {
                SkillClient.Info i = SkillClient.infoOrNull(p.getUUID());
                ClientSpeedsters.Entry e = ClientSpeedsters.get(p.getUUID());
                if (i == null || e == null || !e.active || i.kinetic <= 3F || p.isInvisibleTo(mc.player)) continue;
                boolean selfFp = p == mc.player && !camera.isDetached();
                kinetic(vc, m, cam, p, e, i, t, now, bloom, selfFp);
            }
            for (SkillClient.Hit h : SkillClient.HITS) hit(vc, m, cam, h, t, now);
            for (SkillClient.Burst b : SkillClient.BURSTS) burst(vc, m, cam, b, t, now, bloom, mc, pt);
            for (SkillClient.Rewind r : SkillClient.REWINDS.values()) rewind(vc, m, cam, r, t, now, bloom, mc, pt);
            buffers.endBatch(FlashRenderTypes.ADDITIVE_GLOW);
        } finally {
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static float rel(double w, double c) {
        return (float) (w - c);
    }

    // ---------------------------------------------------------------- kinetik yuk

    private static void kinetic(VertexConsumer vc, Matrix4f m, Vec3 cam, Player p, ClientSpeedsters.Entry e,
                                SkillClient.Info i, float t, long now, float bloom, boolean selfFp) {
        float k = Mth.clamp(i.kinetic / 100F, 0F, 1F);
        boolean full = k >= 0.999F;
        boolean left = p.getMainArm() == HumanoidArm.LEFT;
        int core = e.core, glow = e.glow;
        int hot = GlowDraw.mixRgb(core, 0xFFFFFF, 0.6F);
        long frame = (long) (t / 2F);
        float pulse = 0.82F + 0.18F * Mth.sin(t * (full ? 0.9F : 0.45F));
        BodyPoseCapture.Pose body = selfFp ? null : BodyPoseCapture.get(p);
        float fx, fy, fz, size;
        if (body != null) {
            int part = left ? BodyPoseCapture.L_ARM : BodyPoseCapture.R_ARM;
            float[] box = BodyPoseCapture.BOX[part];
            float cx = (box[0] + box[3]) * 0.5F;
            body.point(part, cx, 10.6F, 0F, cam, P);
            fx = P[0]; fy = P[1]; fz = P[2];
            size = 1F;
            // kol boyunca gezinen simsekler (omuzdan yumruga), yuk arttikca cogalir ve yumruga yigilir
            // kol boyunca enerji kilifi: omuzdan yumruga parlayan cekirdek cizgisi (yuk arttikca yumruga dogru dolar)
            body.point(part, cx, -1.5F, 0F, cam, Q);
            LINE.clear();
            int segs = 8;
            for (int s = 0; s <= segs; s++) {
                float u = s / (float) segs;
                float yy = Mth.lerp(u, -1.5F, 10.6F);
                body.point(part, cx, yy, 0F, cam, P);
                float fill = Mth.clamp((u - (1F - k)) * 4F, 0F, 1F);
                LINE.add(P[0], P[1], P[2], (0.15F + 0.85F * fill) * k);
            }
            GlowDraw.layered(vc, m, LINE, 1.1F + 0.6F * k, core, glow, 0.55F * pulse, bloom, false);
            R.seed(e.seed ^ 0x4B1EL, frame, 1);
            int arcs = 2 + (int) (k * 6F);
            for (int a = 0; a < arcs; a++) {
                float y0 = Mth.lerp(R.next(), -1.5F + 6F * (1F - k), 10F), y1 = Mth.clamp(y0 + R.range(1.5F, 4.5F), -2F, 10.4F);
                surface(box, R, y0, P);
                body.point(part, P[0], P[1], P[2], cam, P);
                surface(box, R, y1, Q);
                body.point(part, Q[0], Q[1], Q[2], cam, Q);
                LINE.clear();
                Lightning.jag(LINE, R, P[0], P[1], P[2], Q[0], Q[1], Q[2], 0.35F, 3, false, 0.9F, 0.6F);
                GlowDraw.layered(vc, m, LINE, 0.36F + 0.34F * k, core, glow, 0.7F + 0.3F * k, bloom, false);
            }
        } else if (selfFp) { // birinci sahis: ekranin altindaki elde, kucuk
            Camera c = Minecraft.getInstance().gameRenderer.getMainCamera();
            Vector3f look = c.getLookVector(), up = c.getUpVector(), lf = c.getLeftVector();
            float side = left ? -1F : 1F;
            fx = look.x() * 0.62F - up.x() * 0.32F - lf.x() * 0.36F * side;
            fy = look.y() * 0.62F - up.y() * 0.32F - lf.y() * 0.36F * side;
            fz = look.z() * 0.62F - up.z() * 0.32F - lf.z() * 0.36F * side;
            size = 0.32F;
            R.seed(e.seed ^ 0x4B1FL, frame, 2);
            int arcs = (int) (k * 3.5F);
            for (int a = 0; a < arcs; a++) {
                float dx = R.signed(), dy = R.signed(), dz = R.signed();
                float l = Mth.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-4F, len = (0.08F + 0.16F * k * R.next());
                LINE.clear();
                Lightning.jag(LINE, R, fx, fy, fz, fx + dx / l * len, fy + dy / l * len, fz + dz / l * len, 0.3F, 2, false, 0.8F, 0.1F);
                GlowDraw.layered(vc, m, LINE, 0.16F, core, glow, 0.6F, bloom, false);
            }
        } else {
            return;
        }
        // yumrukta biriken isik
        GlowDraw.orb(vc, m, fx, fy, fz, (0.22F + 0.55F * k) * size * pulse, GlowDraw.cr(glow), GlowDraw.cg(glow),
                GlowDraw.cb(glow), (0.18F + 0.35F * k) * bloom);
        GlowDraw.orb(vc, m, fx, fy, fz, (0.06F + 0.14F * k) * size, GlowDraw.cr(hot), GlowDraw.cg(hot), GlowDraw.cb(hot),
                0.6F + 0.4F * k);
        if (full && body != null) {
            // tam yuk: yumruktan yere sizan statik arklar (her ~10 tick bir, kisa)
            long hop = (long) (t / 10F);
            float age = t - hop * 10F;
            if (age < 3F) {
                B.seed(e.seed ^ 0x51FFL, hop, 3);
                float gx = rel(p.getX(), cam.x) + B.signed() * 0.9F, gz = rel(p.getZ(), cam.z) + B.signed() * 0.9F;
                float gy = rel(p.getY(), cam.y) + 0.02F;
                LINE.clear();
                Lightning.jag(LINE, B, fx, fy, fz, gx, gy, gz, 0.3F, 4, false, 1F - age / 3F, 0.2F);
                GlowDraw.layered(vc, m, LINE, 0.45F, core, glow, 1F - age / 3F, bloom, false);
            }
            // yumruk etrafinda nabiz halkasi
            float ring = (t % 12F) / 12F;
            GlowDraw.ring(vc, m, fx, fy, fz, 1, 0, 0, 0, 1, 0, 0.12F + ring * 0.45F, 0.025F, GlowDraw.cr(glow),
                    GlowDraw.cg(glow), GlowDraw.cb(glow), 0.5F * (1F - ring), 20);
        }
    }

    /** Kol kupunun yuzeyinde, verilen yukseklikte rastgele nokta (piksel). */
    private static void surface(float[] box, Lightning.Rng rng, float y, float[] out) {
        out[0] = Mth.lerp(rng.next(), box[0], box[3]);
        out[1] = y;
        out[2] = Mth.lerp(rng.next(), box[2], box[5]);
        if (rng.next() < 0.5F) out[0] = rng.next() < 0.5F ? box[0] - 0.3F : box[3] + 0.3F;
        else out[2] = rng.next() < 0.5F ? box[2] - 0.3F : box[5] + 0.3F;
    }

    // ---------------------------------------------------------------- kinetik isabet

    private static void hit(VertexConsumer vc, Matrix4f m, Vec3 cam, SkillClient.Hit h, float t, long now) {
        float age = (t - h.at) / 15F;
        if (age < 0F || age >= 1F) return;
        float k = h.power;
        long frame = (long) (t / UltWorldFx.regenTicks());
        Vec3 c = new Vec3(h.x, h.y, h.z);
        UltWorldFx.burst(vc, m, cam, c, Double.NaN, 1.1F + 2.4F * k, age, 5 + (int) (11 * k), h.seed, frame, h.core, h.glow, 1F);
        // tam yuke yakin: kisa beyaz parlama ve yerde genisleyen halka
        if (k > 0.6F) {
            float fl = 1F - Mth.clamp((t - h.at) / 5F, 0F, 1F);
            float cx = rel(h.x, cam.x), cy = rel(h.y, cam.y), cz = rel(h.z, cam.z);
            if (fl > 0F) GlowDraw.orb(vc, m, cx, cy, cz, 0.6F + 1.2F * (1F - fl), 1F, 1F, 1F, 0.45F * fl * fl * k);
            float gu = Mth.clamp((t - h.at) / 14F, 0F, 1F);
            float rr = Mth.lerp(1F - (1F - gu) * (1F - gu), 0.4F, 1.8F + 2.6F * k), ga = (1F - gu) * (1F - gu) * k;
            float groundY = cy - 1.1F;
            GlowDraw.ring(vc, m, cx, groundY, cz, 1, 0, 0, 0, 0, 1, rr, 0.12F + rr * 0.05F, GlowDraw.cr(h.glow),
                    GlowDraw.cg(h.glow), GlowDraw.cb(h.glow), 0.45F * ga, 40);
            GlowDraw.ring(vc, m, cx, groundY, cz, 1, 0, 0, 0, 0, 1, rr, 0.03F, 1F, 0.95F, 0.85F, 0.8F * ga, 40);
        }
        // darbe yonunde ilerleyen sok halkalari (normal = darbe yonu)
        float ux = -h.dz, uz = h.dx;
        int hot = GlowDraw.mixRgb(h.core, 0xFFFFFF, 0.5F);
        for (int i = 0; i < 2 + (k > 0.6F ? 1 : 0); i++) {
            float u = (t - h.at - i * 1.6F) / 10F;
            if (u < 0F || u > 1F) continue;
            float adv = u * (1.2F + 1.8F * k) + i * 0.3F;
            float r = (0.25F + u * (0.9F + 1.4F * k)) * (1F - i * 0.18F);
            float x = rel(h.x + h.dx * adv, cam.x), y = rel(h.y, cam.y), z = rel(h.z + h.dz * adv, cam.z);
            float a = (1F - u) * (1F - u);
            GlowDraw.ring(vc, m, x, y, z, ux, 0, uz, 0, 1, 0, r, 0.06F + r * 0.1F, GlowDraw.cr(h.glow), GlowDraw.cg(h.glow),
                    GlowDraw.cb(h.glow), 0.5F * a, 32);
            GlowDraw.ring(vc, m, x, y, z, ux, 0, uz, 0, 1, 0, r, 0.025F, GlowDraw.cr(hot), GlowDraw.cg(hot), GlowDraw.cb(hot),
                    0.9F * a, 32);
        }
    }

    // ---------------------------------------------------------------- kalinti ve geri sarma bitisi

    private static void burst(VertexConsumer vc, Matrix4f m, Vec3 cam, SkillClient.Burst b, float t, long now,
                              float bloom, Minecraft mc, float pt) {
        float age = t - b.at;
        long frame = (long) (t / UltWorldFx.regenTicks());
        int hot = GlowDraw.mixRgb(b.core, 0xFFFFFF, 0.55F);
        switch (b.type) {
            case SkillFxPacket.DECOY_CAST -> {
                // kalintidan hizciya uzanan kopma simsekleri (hizcinin canli konumuna)
                if (age > 9F) return;
                Player p = mc.level.getPlayerByUUID(b.player);
                if (p == null) return;
                Vec3 to = p.getPosition(pt).add(0, 1.0, 0);
                float a = 1F - age / 9F;
                R.seed(b.seed, frame, 4);
                for (int i = 0; i < 3; i++) {
                    LINE.clear();
                    Lightning.jag(LINE, R, rel(b.x, cam.x), rel(b.y + 0.4 + i * 0.45, cam.y), rel(b.z, cam.z),
                            rel(to.x, cam.x), rel(to.y + R.signed() * 0.4, cam.y), rel(to.z, cam.z), 0.16F, 5, false, a, a * 0.5F);
                    GlowDraw.layered(vc, m, LINE, 0.9F, b.core, b.glow, a, bloom, false);
                }
                // donma anindaki dikey isik
                if (age < 4F) {
                    float f = 1F - age / 4F;
                    float x = rel(b.x, cam.x), y = rel(b.y, cam.y), z = rel(b.z, cam.z);
                    GlowDraw.segment(vc, m, x, y, z, x, y + 2.1F, z, 0.5F * f, GlowDraw.cr(b.glow), GlowDraw.cg(b.glow),
                            GlowDraw.cb(b.glow), 0.5F * f, 0.2F * f, false);
                    GlowDraw.orb(vc, m, x, y + 1.0F, z, 1.3F * f + 0.3F, GlowDraw.cr(hot), GlowDraw.cg(hot), GlowDraw.cb(hot), 0.45F * f);
                }
            }
            case SkillFxPacket.DECOY_SHATTER -> {
                float u = age / 16F;
                if (u >= 1F) return;
                Vec3 c = new Vec3(b.x, b.y + 1.0, b.z);
                UltWorldFx.burst(vc, m, cam, c, b.y, 3.2F, u, 14, b.seed, frame, b.core, b.glow, 1F);
                // statik bosalmanin menzili: yerde genisleyen halka
                float x = rel(b.x, cam.x), y = rel(b.y + 0.05, cam.y), z = rel(b.z, cam.z);
                float rr = Mth.lerp(1F - (1F - u) * (1F - u), 0.4F, 3.6F), a = (1F - u) * (1F - u);
                GlowDraw.ring(vc, m, x, y, z, 1, 0, 0, 0, 0, 1, rr, 0.18F + rr * 0.05F, GlowDraw.cr(b.glow),
                        GlowDraw.cg(b.glow), GlowDraw.cb(b.glow), 0.55F * a * bloom, 48);
                GlowDraw.ring(vc, m, x, y, z, 1, 0, 0, 0, 0, 1, rr, 0.04F, GlowDraw.cr(hot), GlowDraw.cg(hot), GlowDraw.cb(hot),
                        0.9F * a, 48);
            }
            case SkillFxPacket.DECOY_FADE -> {
                float u = age / 12F;
                if (u >= 1F) return;
                // goruntu cozulurken kisa kisa citirtilar
                R.seed(b.seed, frame, 5);
                float a = 1F - u;
                for (int i = 0; i < 4; i++) {
                    float x = rel(b.x, cam.x), y = rel(b.y + 0.2 + R.next() * 1.6F, cam.y), z = rel(b.z, cam.z);
                    LINE.clear();
                    Lightning.jag(LINE, R, x + R.signed() * 0.3F, y, z + R.signed() * 0.3F, x + R.signed() * 0.6F,
                            y + R.range(0.2F, 0.7F), z + R.signed() * 0.6F, 0.3F, 3, false, a, 0F);
                    GlowDraw.layered(vc, m, LINE, 0.4F, b.core, b.glow, a, bloom, false);
                }
            }
            case SkillFxPacket.REWIND_END -> {
                float u = age / 16F;
                if (u >= 1F) return;
                Vec3 c = new Vec3(b.x, b.y + 1.0, b.z);
                UltWorldFx.burst(vc, m, cam, c, b.y, 2.4F, u, 12, b.seed, frame, b.core, b.glow, 1F);
                float x = rel(b.x, cam.x), y = rel(b.y + 0.05, cam.y), z = rel(b.z, cam.z);
                // saat kadrani son kez doner ve dagilir
                clockFace(vc, m, x, y, z, Mth.lerp(u, 1.1F, 2.6F), -t * 0.6F, b.core, b.glow, (1F - u) * 0.9F);
            }
            default -> {}
        }
    }

    // ---------------------------------------------------------------- geri sarma

    private static void rewind(VertexConsumer vc, Matrix4f m, Vec3 cam, SkillClient.Rewind r, float t, long now,
                               float bloom, Minecraft mc, float pt) {
        if (r.ended()) return;
        float prog = r.progress(t);
        float idx = prog * (r.count - 1);
        float fadeIn = Mth.clamp((t - r.start) / 4F, 0F, 1F);
        int hot = GlowDraw.mixRgb(r.core, 0xFFFFFF, 0.55F);

        // kalan yol: hizcidan varis noktasina titreyen simsek seridi
        LINE.clear();
        int step = Math.max(1, r.count / 48);
        for (float i = idx; i < r.count - 1 + 0.001F; i += step) {
            r.sample(i, S);
            float n = Lightning.noise(r.player.getLeastSignificantBits() ^ (long) (i * 13), t * 0.35 + i * 0.21) * 0.12F;
            float far = (i - idx) / Math.max(1F, r.count - 1 - idx);
            LINE.add(rel(S[0], cam.x) + n, rel(S[1] + 0.95, cam.y) + n * 0.6F, rel(S[2], cam.z) - n, (0.85F - 0.45F * far) * fadeIn);
        }
        r.sample(r.count - 1, S);
        LINE.add(rel(S[0], cam.x), rel(S[1] + 0.95, cam.y), rel(S[2], cam.z), 0.4F * fadeIn);
        if (LINE.size >= 2) GlowDraw.layered(vc, m, LINE, 0.55F, r.core, r.glow, 0.9F, bloom, false);

        // varis noktasi: ters donen saat kadrani + isik sutunu (yaklastikca parlar)
        float near = 0.45F + 0.55F * prog;
        float x = rel(S[0], cam.x), y = rel(S[1] + 0.04, cam.y), z = rel(S[2], cam.z);
        clockFace(vc, m, x, y, z, 1.15F, -t * 0.22F, r.core, r.glow, near * fadeIn);
        GlowDraw.segment(vc, m, x, y, z, x, y + 2.4F, z, 0.42F, GlowDraw.cr(r.glow), GlowDraw.cg(r.glow), GlowDraw.cb(r.glow),
                0.28F * near * fadeIn, 0F, false);

        // hizcinin belinde ters donen saat halkasi ve uzerinde gezinen arklar
        Player p = mc.level.getPlayerByUUID(r.player);
        if (p == null) return;
        Vec3 at = p.getPosition(pt);
        boolean selfFp = p == mc.player && !mc.gameRenderer.getMainCamera().isDetached();
        if (selfFp) return; // kendi ekraninda shader + ekran ustu efekti var
        float px = rel(at.x, cam.x), py = rel(at.y + 0.95, cam.y), pz = rel(at.z, cam.z);
        clockFace(vc, m, px, py, pz, 0.85F, -t * 0.9F, r.core, r.glow, 0.9F * fadeIn);
        long frame = (long) (t / 2F);
        R.seed(r.player.getMostSignificantBits(), frame, 6);
        for (int i = 0; i < 3; i++) {
            float a0 = R.next() * Mth.TWO_PI, a1 = a0 + R.range(0.6F, 1.6F);
            float h0 = R.signed() * 0.8F, h1 = R.signed() * 0.8F;
            LINE.clear();
            Lightning.jag(LINE, R, px + Mth.cos(a0) * 0.45F, py + h0, pz + Mth.sin(a0) * 0.45F,
                    px + Mth.cos(a1) * 0.45F, py + h1, pz + Mth.sin(a1) * 0.45F, 0.3F, 3, false, 0.8F, 0.4F);
            GlowDraw.layered(vc, m, LINE, 0.38F, r.core, r.glow, 0.8F * fadeIn, bloom, false);
        }
    }

    /**
     * Yatay saat kadrani: yumusak halka + 12 cizgi (3/6/9/12 uzun) + geriye donen iki ibre. rot: radyan.
     */
    static void clockFace(VertexConsumer vc, Matrix4f m, float x, float y, float z, float radius, float rot, int core,
                          int glow, float alpha) {
        if (alpha <= 0.01F) return;
        float gr = GlowDraw.cr(glow), gg = GlowDraw.cg(glow), gb = GlowDraw.cb(glow);
        int hot = GlowDraw.mixRgb(core, 0xFFFFFF, 0.5F);
        float hr = GlowDraw.cr(hot), hg = GlowDraw.cg(hot), hb = GlowDraw.cb(hot);
        GlowDraw.ring(vc, m, x, y, z, 1, 0, 0, 0, 0, 1, radius, 0.08F + radius * 0.04F, gr, gg, gb, 0.45F * alpha, 48);
        GlowDraw.ring(vc, m, x, y, z, 1, 0, 0, 0, 0, 1, radius, 0.018F, hr, hg, hb, 0.85F * alpha, 48);
        for (int i = 0; i < 12; i++) {
            float a = rot + i * (Mth.TWO_PI / 12F);
            float len = i % 3 == 0 ? 0.22F : 0.11F;
            float c = Mth.cos(a), s = Mth.sin(a);
            GlowDraw.segment(vc, m, x + c * radius, y, z + s * radius, x + c * (radius - len * radius), y,
                    z + s * (radius - len * radius), 0.02F, hr, hg, hb, 0.9F * alpha, 0.5F * alpha, false);
        }
        // ibreler (akrep yavas, yelkovan hizli; ikisi de geriye)
        float ah = rot * 0.35F, am = rot * 2.4F;
        GlowDraw.segment(vc, m, x, y, z, x + Mth.cos(ah) * radius * 0.5F, y, z + Mth.sin(ah) * radius * 0.5F, 0.035F,
                hr, hg, hb, 0.9F * alpha, 0.7F * alpha, false);
        GlowDraw.segment(vc, m, x, y, z, x + Mth.cos(am) * radius * 0.82F, y, z + Mth.sin(am) * radius * 0.82F, 0.025F,
                hr, hg, hb, 0.9F * alpha, 0.5F * alpha, false);
        GlowDraw.orb(vc, m, x, y, z, 0.12F, hr, hg, hb, 0.8F * alpha);
    }
}
