package dev.baranhan.flashmod.ultimate;

import dev.baranhan.flashmod.config.FlashServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;

/** Hedef secimi: once isin (kutular 0.75 sisirilmis, bloklar keser), bulamazsa 15 derecelik koni. */
public final class UltimateTargeting {
    private UltimateTargeting() {}

    @Nullable
    public static LivingEntity find(ServerPlayer caster, boolean debug) {
        double range = FlashServerConfig.ULT_RANGE.get();
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        HitResult block = caster.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double maxD = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);
        Vec3 rayEnd = eye.add(look.scale(maxD));

        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        AABB area = caster.getBoundingBox().expandTowards(look.scale(maxD)).inflate(1.5D);
        for (Entity e : caster.level().getEntities(caster, area, en -> en instanceof LivingEntity)) {
            LivingEntity le = (LivingEntity) e;
            if (!valid(caster, le, debug)) continue;
            Optional<Vec3> hit = le.getBoundingBox().inflate(0.75D).clip(eye, rayEnd);
            if (hit.isEmpty()) continue;
            double d = hit.get().distanceToSqr(eye);
            if (d < bestD) { bestD = d; best = le; }
        }
        if (best != null) return best;

        // koni
        double cos = Math.cos(Math.toRadians(FlashServerConfig.ULT_CONE.get()));
        for (Entity e : caster.level().getEntities(caster, caster.getBoundingBox().inflate(range), en -> en instanceof LivingEntity)) {
            LivingEntity le = (LivingEntity) e;
            if (!valid(caster, le, debug)) continue;
            Vec3 to = le.position().add(0, le.getBbHeight() * 0.5D, 0).subtract(eye);
            double d = to.length();
            if (d > range || d < 1.0E-3) continue;
            if (to.scale(1.0D / d).dot(look) < cos || !caster.hasLineOfSight(le)) continue;
            if (d * d < bestD) { bestD = d * d; best = le; }
        }
        return best;
    }

    public static boolean valid(ServerPlayer caster, LivingEntity e, boolean debug) {
        if (!e.isAlive() || e.isSpectator() || e == caster) return false;
        if (e == caster.getVehicle() || e.getVehicle() == caster) return false;
        if (e instanceof ArmorStand) return debug;
        if (e.getType().is(UltimateDamage.IMMUNE)) return false;
        if (e instanceof Player pl) {
            if (pl.isCreative() && !FlashServerConfig.ULT_ALLOW_CREATIVE.get()) return false;
            if (caster.getServer() != null && !caster.getServer().isPvpAllowed()) return false;
        }
        return !UltimateManager.involved(e);
    }
}
