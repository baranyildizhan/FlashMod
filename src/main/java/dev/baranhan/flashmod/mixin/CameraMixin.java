package dev.baranhan.flashmod.mixin;

import dev.baranhan.flashmod.client.CameraModes;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private boolean detached;

    @Shadow protected abstract void setPosition(double x, double y, double z);

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Shadow public abstract Vec3 getPosition();

    @Shadow public abstract float getXRot();

    @Shadow public abstract float getYRot();

    /** Aerial / tornado kamerasi. detached=true -> oyuncunun kendi modeli cizilir. */
    @Inject(method = "setup", at = @At("TAIL"))
    private void flashmod$cameraModes(BlockGetter level, Entity entity, boolean detachedIn, boolean mirror,
                                      float partialTick, CallbackInfo ci) {
        CameraModes.Result r = CameraModes.compute(entity, partialTick, getPosition(), getYRot(), getXRot());
        if (r == null) return;
        if (r.detached()) this.detached = true;
        setRotation(r.yaw(), r.pitch());
        setPosition(r.x(), r.y(), r.z());
    }
}
