package dev.baranhan.flashmod.mixin;

import dev.baranhan.flashmod.client.WallRun;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerTravelMixin {
    /** Duvarda kosarken vanilla hareket yerine duvar cercevesindeki hareket (yercekimi N'ye dogru, adim yuksekligi). */
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void flashmod$wallTravel(Vec3 input, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer lp && WallRun.isActive()) {
            WallRun.travel(lp);
            ci.cancel();
        }
    }
}
