package dev.baranhan.flashmod.mixin;

import dev.baranhan.flashmod.speed.WallState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class ProjectileMixin {
    /** Duvarda kosarken ok/trident/kartopu vb. de duvar cercevesinde baktigin yone gider. */
    @Inject(method = "shootFromRotation", at = @At("HEAD"), cancellable = true, require = 0)
    private void flashmod$wallShoot(Entity shooter, float xRot, float yRot, float roll, float velocity, float inaccuracy,
                                    CallbackInfo ci) {
        if (!(shooter instanceof Player p)) return;
        float[] n = WallState.normal(p);
        if (n == null) return;
        Projectile self = (Projectile) (Object) this;
        float d = (float) Math.PI / 180F;
        Vec3 dir = new Vec3(-Mth.sin(yRot * d) * Mth.cos(xRot * d), -Mth.sin((xRot + roll) * d),
                Mth.cos(yRot * d) * Mth.cos(xRot * d));
        dir = WallState.toWorld(dir, n[0], n[1], 1.0D);
        self.shoot(dir.x, dir.y, dir.z, velocity, inaccuracy);
        Vec3 sv = shooter.getDeltaMovement();
        self.setDeltaMovement(self.getDeltaMovement().add(sv.x, shooter.onGround() ? 0.0D : sv.y, sv.z));
        ci.cancel();
    }
}
