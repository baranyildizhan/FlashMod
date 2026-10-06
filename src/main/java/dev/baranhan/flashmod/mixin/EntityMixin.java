package dev.baranhan.flashmod.mixin;

import dev.baranhan.flashmod.speed.BlitzLock;
import dev.baranhan.flashmod.speed.PhaseHelper;
import dev.baranhan.flashmod.speed.WallState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Entity.class)
public abstract class EntityMixin {
    /**
     * Phasing: yatay hareket hicbir seye carpmaz, dikey hareket normal carpisir.
     * Ek olarak zemin kilidi: oyuncu bir blogun icindeyken son gercek zemin seviyesinin altina inemez.
     */
    @Inject(method = "collide", at = @At("HEAD"), cancellable = true)
    private void flashmod$phaseCollide(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Player p) || !PhaseHelper.isPhasing(p)) return;

        boolean inside = PhaseHelper.insideSolid(p);
        if (!inside && p.onGround()) PhaseHelper.setFloor(p, p.getY());

        AABB box = self.getBoundingBox();
        double vy = Entity.collideBoundingBox(self, new Vec3(0.0D, movement.y, 0.0D), box, self.level(), List.of()).y;

        double floor = PhaseHelper.floor(p);
        if (!Double.isNaN(floor) && vy < 0.0D && p.getY() + vy < floor - 1.0E-4D) {
            AABB moved = box.move(movement.x, vy, movement.z).deflate(1.0E-3D);
            if (inside || !self.level().noCollision(self, moved)) {
                vy = Math.max(vy, floor - p.getY()); // dikey carpisma sayilir -> onGround
            }
        }
        cir.setReturnValue(new Vec3(movement.x, vy, movement.z));
    }

    /** Duvarin icindeyken vanilla "ayakta sigmiyor" diye emekleme/egilme pozuna gecirmesin (kamera yere inerdi). */
    @Inject(method = "canEnterPose", at = @At("HEAD"), cancellable = true, require = 0)
    private void flashmod$phasePose(Pose pose, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player p && (PhaseHelper.isPhasing(p) || WallState.is(p))) cir.setReturnValue(true);
    }

    /** Ayni korumanin metot adindan bagimsiz hali: hangi yoldan gelirse gelsin zorla emekleme pozunu engelle. */
    @ModifyVariable(method = "setPose", at = @At("HEAD"), argsOnly = true)
    private Pose flashmod$phasePoseFix(Pose pose) {
        if ((Object) this instanceof Player p && (PhaseHelper.isPhasing(p) || WallState.is(p))
                && (pose == Pose.SWIMMING || pose == Pose.CROUCHING) && !p.isSwimming() && !p.isFallFlying()) {
            return p.isShiftKeyDown() ? Pose.CROUCHING : Pose.STANDING;
        }
        return pose;
    }

    /** Blitz sinematigi sirasinda fare yerel oyuncuyu dondurmesin (kafa sabit, kamera zaten sabit). */
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true, require = 0)
    private void flashmod$blitzMouseLock(double yRot, double xRot, CallbackInfo ci) {
        if ((BlitzLock.clientLocked || BlitzLock.ultimateLocked) && (Object) this instanceof Player p && p.level().isClientSide && p.isLocalPlayer()) {
            ci.cancel();
        }
    }

    // ------------------------------------------------------------------ duvar = zemin

    /** Duvarda kosarken carpisma kutusu duvar cercevesinde: 0.6 kesit, N boyunca boy kadar. */
    @Inject(method = "makeBoundingBox", at = @At("HEAD"), cancellable = true)
    private void flashmod$wallBox(CallbackInfoReturnable<AABB> cir) {
        if (!((Object) this instanceof Player p)) return;
        float[] n = WallState.normal(p);
        if (n == null) return;
        cir.setReturnValue(WallState.box(p.position(), n[0], n[1], p.getBbWidth(), p.getBbHeight()));
    }

    /** Bakis vektoru duvar cercevesinde doner: nisan, blok/varlik secimi, mizrak hep kameranin baktigi yere. */
    @Inject(method = "calculateViewVector", at = @At("RETURN"), cancellable = true)
    private void flashmod$wallView(float xRot, float yRot, CallbackInfoReturnable<Vec3> cir) {
        if (!((Object) this instanceof Player p)) return;
        float[] n = WallState.normal(p);
        if (n == null) return;
        cir.setReturnValue(WallState.toWorld(cir.getReturnValue(), n[0], n[1], 1.0D));
    }

    /** Goz konumu donmus kafada (duvardan disari). */
    @Inject(method = "getEyePosition()Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), cancellable = true)
    private void flashmod$wallEye(CallbackInfoReturnable<Vec3> cir) {
        if (!((Object) this instanceof Player p)) return;
        float[] n = WallState.normal(p);
        if (n == null) return;
        cir.setReturnValue(WallState.eye(p.position(), n[0], n[1], p.getEyeHeight()));
    }

    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), cancellable = true)
    private void flashmod$wallEyePt(float pt, CallbackInfoReturnable<Vec3> cir) {
        if (!((Object) this instanceof Player p)) return;
        float[] n = WallState.normal(p);
        if (n == null) return;
        cir.setReturnValue(WallState.eye(p.getPosition(pt), n[0], n[1], p.getEyeHeight()));
    }
}
