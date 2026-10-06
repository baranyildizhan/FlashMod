package dev.baranhan.flashmod.client;

import dev.baranhan.flashmod.config.FlashClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Ozel kamera modlari (CameraMixin, Camera.setup'in sonunda buradan okur):
 *  - Aerial (toggle): cok yukaridan, hafif egik, oyuncuyu ortada tutan kus bakisi. Yon oyuncunun bakisini yumusakca izler.
 *  - Tornado: tornado sirasinda kamera girdabin merkezine bakan serbest yorunge kamerasi; fare ile etrafinda dolasirsin.
 * Gecisler yumusak (ease), aerial acikken tornado da yukaridan izlenir.
 */
public final class CameraModes {
    public static boolean aerial;
    private static float aerialBlend, tornadoBlend, blitzBlend;
    @Nullable
    private static Result lastBlitz;
    private static float smoothYaw;
    private static boolean yawInit;
    private static long lastNanos;

    private CameraModes() {}

    /** detached=false: kamera konum/aci degisir ama birinci sahis kalir (el gorunur, govde cizilmez). */
    public record Result(double x, double y, double z, float yaw, float pitch, boolean detached) {
        public Result(double x, double y, double z, float yaw, float pitch) {
            this(x, y, z, yaw, pitch, true);
        }
    }

    public static boolean detachedByUs() {
        return aerialBlend > 0.001F || tornadoBlend > 0.001F || blitzBlend > 0.001F;
    }

    /** 0..1 sinematik kamera karisimi (sinema seritleri / renk ayari icin). */
    public static float blitzAmount() {
        return ease(blitzBlend);
    }

    public static boolean aerialVisible() {
        return aerialBlend > 0.5F;
    }

    private static float ease(float t) {
        return t * t * (3F - 2F * t);
    }

    @Nullable
    public static Result compute(Entity entity, float pt, Vec3 vanillaPos, float vanillaYaw, float vanillaPitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entity != mc.player || mc.level == null) {
            aerialBlend = 0F;
            tornadoBlend = 0F;
            blitzBlend = 0F;
            return null;
        }
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016F : Mth.clamp((now - lastNanos) / 1.0E9F, 0F, 0.1F);
        lastNanos = now;
        Result ult = dev.baranhan.flashmod.client.ultimate.UltDirector.camera(entity, pt); // ultimate sinematigi: en yuksek oncelik
        if (ult != null) return ult;

        boolean tornado = Tornado.isActive();
        aerialBlend = approach(aerialBlend, aerial ? 1F : 0F, dt * 1.6F);
        tornadoBlend = approach(tornadoBlend, tornado ? 1F : 0F, dt * (tornado ? 2.5F : 1.4F));
        Result blitz = BlitzClient.camera(pt);
        if (blitz != null) lastBlitz = blitz;
        blitzBlend = approach(blitzBlend, blitz != null ? 1F : 0F, dt * (blitz != null ? 6.0F : 2.2F));
        if (aerialBlend <= 0.001F && tornadoBlend <= 0.001F && blitzBlend <= 0.001F) {
            yawInit = false;
            return WallRun.camera(entity, pt, vanillaPos, vanillaYaw, vanillaPitch); // duvarda kosma (1. sahis)
        }

        double x = vanillaPos.x, y = vanillaPos.y, z = vanillaPos.z;
        float yaw = vanillaYaw, pitch = vanillaPitch;
        Vec3 body = entity.getPosition(pt);

        // --- Tornado yorunge kamerasi (fare ile serbest) ---
        if (tornadoBlend > 0.001F) {
            Vec3 c = Tornado.isActive() ? Tornado.center() : body;
            Vec3 target = c.add(0.0D, 1.4D, 0.0D);
            float ty = vanillaYaw, tp = Mth.clamp(vanillaPitch, -10F, 75F);
            Vec3 look = Vec3.directionFromRotation(tp, ty);
            Vec3 cam = clip(mc, entity, target, target.subtract(look.scale(9.0D)));
            float t = ease(tornadoBlend);
            x = Mth.lerp(t, x, cam.x);
            y = Mth.lerp(t, y, cam.y);
            z = Mth.lerp(t, z, cam.z);
            yaw = Mth.rotLerp(t, yaw, ty);
            pitch = Mth.lerp(t, pitch, tp);
        }

        // --- Aerial ---
        if (aerialBlend > 0.001F) {
            float viewYaw = entity.getViewYRot(pt);
            if (!yawInit) {
                smoothYaw = viewYaw;
                yawInit = true;
            }
            smoothYaw = Mth.rotLerp(1F - (float) Math.exp(-dt * 4.0F), smoothYaw, viewYaw);
            float ap = 72F;
            double dist = FlashClientConfig.AERIAL_DISTANCE.get();
            Vec3 focus = (Tornado.isActive() ? Tornado.center() : body).add(0.0D, 1.0D, 0.0D);
            Vec3 look = Vec3.directionFromRotation(ap, smoothYaw);
            Vec3 cam = focus.subtract(look.scale(dist));
            float t = ease(aerialBlend);
            x = Mth.lerp(t, x, cam.x);
            y = Mth.lerp(t, y, cam.y);
            z = Mth.lerp(t, z, cam.z);
            yaw = Mth.rotLerp(t, yaw, smoothYaw);
            pitch = Mth.lerp(t, pitch, ap);
        }
        // --- Blitz sinematik kamerasi (en ust oncelik; cekimler arasi kesme aninda) ---
        if (blitzBlend > 0.001F && lastBlitz != null) {
            float t = ease(blitzBlend);
            Result b = lastBlitz;
            x = Mth.lerp(t, x, b.x());
            y = Mth.lerp(t, y, b.y());
            z = Mth.lerp(t, z, b.z());
            yaw = Mth.rotLerp(t, yaw, b.yaw());
            pitch = Mth.lerp(t, pitch, b.pitch());
        }
        return new Result(x, y, z, yaw, pitch);
    }

    private static Vec3 clip(Minecraft mc, Entity entity, Vec3 from, Vec3 to) {
        BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
        if (hit.getType() == HitResult.Type.MISS) return to;
        Vec3 d = to.subtract(from);
        double len = d.length();
        double back = Math.max(0.0D, hit.getLocation().distanceTo(from) - 0.3D);
        return from.add(d.scale(back / Math.max(len, 1.0E-4D)));
    }

    private static float approach(float v, float target, float step) {
        if (v < target) return Math.min(target, v + step);
        return Math.max(target, v - step);
    }
}
