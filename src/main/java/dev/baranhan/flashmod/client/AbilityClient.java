package dev.baranhan.flashmod.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.flashmod.client.particle.FlashParticles;
import dev.baranhan.flashmod.client.render.BodyPoseCapture;
import dev.baranhan.flashmod.client.render.GlowDraw;
import dev.baranhan.flashmod.client.render.Lightning;
import dev.baranhan.flashmod.client.render.Polyline;
import dev.baranhan.flashmod.entity.LightningSpearEntity;
import dev.baranhan.flashmod.network.AbilitySyncPacket;
import dev.baranhan.flashmod.speed.AbilityLogic;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/** Istemci tarafi yetenekler: senkron, mizrak sarj gorseli ve kol pozu, mizrak isabet efekti, agir cekim gorseli. */
public final class AbilityClient {
    private static final Polyline LINE = new Polyline();
    private static final Lightning.Rng R = new Lightning.Rng(11);
    private static final float[] PT3 = new float[3];

    private AbilityClient() {}

    public static void handleSync(AbilitySyncPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ClientSpeedsters.Entry e = ClientSpeedsters.getOrCreate(m.player);
        boolean self = mc.player != null && mc.player.getUUID().equals(m.player);
        long now = mc.level.getGameTime();
        if (m.charging && !e.charging) e.chargeStartAt = now;
        if (!m.charging && e.charging && e.charge >= AbilityLogic.MIN_THROW) e.throwAt = now;
        e.energy = m.energy;
        e.charge = m.charge;
        e.charging = m.charging;
        e.slowmo = m.slowmo;
        if (!self) { // yerel oyuncunun duvarda kosmasi istemcide hesaplanir
            boolean was = e.wallRun;
            e.wallRun = m.wallRun;
            if (m.wallRun) {
                e.wallNx = m.nx;
                e.wallNz = m.nz;
            }
            if (was != m.wallRun) { // uzak oyuncunun kutusu da duvar cercevesine gecsin
                dev.baranhan.flashmod.speed.WallState.setClient(m.player, m.wallRun, m.nx, m.nz);
                Player other = mc.level.getPlayerByUUID(m.player);
                if (other != null) other.refreshDimensions();
            }
        }
    }

    // ---------------------------------------------------------------- kol pozu (HumanoidModelMixin uzerinden)

    /** Sarj ederken sag kol arkaya-yukari kalkar; birakinca one savrulur. Vanilla pozun ustune karisir. */
    public static void applyArm(ClientSpeedsters.Entry e, HumanoidModel<?> model, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float now = mc.level.getGameTime() + pt;
        float target = 0F, w = 0F;
        if (e.charging) {
            w = Mth.clamp((now - e.chargeStartAt) / 4F, 0F, 1F);
            w = w * w * (3F - 2F * w);
            float shake = (e.charge / 100F) * 0.06F * Mth.sin(now * 3.1F);
            target = -2.85F + shake;
        } else {
            float s = now - e.throwAt;
            if (s >= 0F && s < 7F) {
                if (s < 1.5F) { // savurma
                    float k = s / 1.5F;
                    target = Mth.lerp(k * k, -2.85F, -0.9F);
                    w = 1F;
                } else {          // vanilla'ya don
                    target = -0.9F;
                    float k = (s - 1.5F) / 5.5F;
                    w = 1F - k * k * (3F - 2F * k);
                }
            }
        }
        if (w <= 0F) return;
        model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, target);
        model.rightArm.yRot = Mth.lerp(w, model.rightArm.yRot, -0.15F);
        model.rightArm.zRot = Mth.lerp(w, model.rightArm.zRot, 0.12F);
    }

    // ---------------------------------------------------------------- el gorseli (SpeedTrailRenderer'dan)

    /**
     * Sarj sirasinda sag elde buyuyen simsek kuresi ve etrafa savrulan arklar.
     * Govde cizildiyse gercek el noktasi; birinci sahista ekranin sag altindaki el konumu.
     */
    public static void drawCharge(VertexConsumer vc, Matrix4f m, Player p, ClientSpeedsters.Entry e,
                                  @Nullable BodyPoseCapture.Pose body, Vec3 cam, boolean selfFp, float bloom) {
        if (!e.charging || e.charge <= 0.5F) return;
        float k = Mth.clamp(e.charge / 100F, 0F, 1F);
        float hx, hy, hz;
        if (body != null) {
            body.point(BodyPoseCapture.R_ARM, -1F, 10.5F, 0F, cam, PT3);
            hx = PT3[0]; hy = PT3[1]; hz = PT3[2];
        } else if (selfFp) {
            Camera c = Minecraft.getInstance().gameRenderer.getMainCamera();
            Vector3f look = c.getLookVector(), up = c.getUpVector(), left = c.getLeftVector();
            hx = look.x() * 0.6F - up.x() * 0.3F - left.x() * 0.36F;
            hy = look.y() * 0.6F - up.y() * 0.3F - left.y() * 0.36F;
            hz = look.z() * 0.6F - up.z() * 0.3F - left.z() * 0.36F;
        } else {
            return;
        }
        float gr = GlowDraw.cr(e.glow), gg = GlowDraw.cg(e.glow), gb = GlowDraw.cb(e.glow);
        int hot = GlowDraw.mixRgb(e.core, 0xFFFFFF, 0.6F);
        float size = selfFp ? 0.35F : 1F;
        float pulse = 0.85F + 0.15F * Mth.sin(Util.getMillis() / 45F);
        GlowDraw.orb(vc, m, hx, hy, hz, (0.25F + 0.55F * k) * size * pulse, gr, gg, gb, (0.25F + 0.3F * k) * Math.max(bloom, 0.4F));
        GlowDraw.orb(vc, m, hx, hy, hz, (0.06F + 0.14F * k) * size, GlowDraw.cr(hot), GlowDraw.cg(hot), GlowDraw.cb(hot), 0.95F);
        long frame = Util.getMillis() / 40L;
        R.seed(e.seed ^ 0xC4A2L, frame, 1);
        int arcs = 2 + (int) (k * 6);
        for (int i = 0; i < arcs; i++) {
            float len = (0.25F + 0.75F * k * R.next()) * size;
            float dx = R.signed(), dy = R.signed(), dz = R.signed();
            float l = Mth.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-4F;
            LINE.clear();
            Lightning.jag(LINE, R, hx, hy, hz, hx + dx / l * len, hy + dy / l * len, hz + dz / l * len, 0.3F, 3, false, 1F, 0.3F);
            GlowDraw.layered(vc, m, LINE, 0.45F * size, e.core, e.glow, 0.9F, bloom, false);
        }
    }

    // ---------------------------------------------------------------- mizrak isabeti

    public static void spearImpact(LightningSpearEntity spear) {
        if (!(spear.level() instanceof ClientLevel level)) return;
        Vec3 at = spear.position();
        float k = Mth.clamp(spear.getCharge() / 100F, 0F, 1F);
        int core = 0xFFF3C4, glow = 0xFF9A1A;
        Entity owner = spear.getOwner();
        if (owner instanceof Player op) {
            ClientSpeedsters.Entry e = ClientSpeedsters.get(op.getUUID());
            if (e != null) {
                core = e.core;
                glow = e.glow;
                e.lastBoom = level.getGameTime();
                e.boomPower = 0.6F + 0.9F * k; // halka + (guclu ise) parlama, SpeedTrailRenderer.drawBoom
                e.boomX = at.x;
                e.boomY = at.y;
                e.boomZ = at.z;
                Vec3 v = spear.getDeltaMovement();
                double hl = Math.sqrt(v.x * v.x + v.z * v.z);
                e.boomDirX = hl > 1.0E-3 ? (float) (v.x / hl) : 1F;
                e.boomDirZ = hl > 1.0E-3 ? (float) (v.z / hl) : 0F;
            }
        }
        RandomSource r = level.random;
        int n = (int) ((40 + 80 * k) * Math.max(FlashParticles.factor(), 0.35F));
        for (int i = 0; i < n; i++) {
            double sp = 0.3D + r.nextDouble() * (0.6D + 0.8D * k);
            double a = r.nextDouble() * Math.PI * 2.0D, b = (r.nextDouble() - 0.3D) * Math.PI * 0.5D;
            FlashParticles.spark(level, at.x, at.y, at.z, Math.cos(a) * Math.cos(b) * sp, Math.sin(b) * sp + 0.1D,
                    Math.sin(a) * Math.cos(b) * sp, core, glow, 8 + r.nextInt(12), 1.0F + 0.6F * k);
        }
        for (int i = 0; i < 6 + 10 * k; i++) {
            level.addParticle(ParticleTypes.LARGE_SMOKE, at.x + r.nextGaussian() * 0.6D, at.y + r.nextDouble(),
                    at.z + r.nextGaussian() * 0.6D, 0, 0.04D, 0);
        }
        level.addParticle(ParticleTypes.FLASH, at.x, at.y, at.z, 0, 0, 0);
    }

    /** Mizrak ucarken arkasindan sacilan kivilcimlar (renderer her yeni tick'te cagirir). */
    public static void spearTrailFx(LightningSpearEntity spear, int core, int glow) {
        if (!(spear.level() instanceof ClientLevel level)) return;
        RandomSource r = level.random;
        float k = Mth.clamp(spear.getCharge() / 100F, 0F, 1F);
        Vec3 v = spear.getDeltaMovement();
        int n = 1 + (int) (3 * k * FlashParticles.factor());
        for (int i = 0; i < n; i++) {
            double t = r.nextDouble();
            FlashParticles.spark(level, spear.getX() - v.x * t, spear.getY() - v.y * t, spear.getZ() - v.z * t,
                    (r.nextDouble() - 0.5D) * 0.2D, (r.nextDouble() - 0.5D) * 0.2D, (r.nextDouble() - 0.5D) * 0.2D,
                    core, glow, 5 + r.nextInt(6), 0.8F);
        }
    }

}
