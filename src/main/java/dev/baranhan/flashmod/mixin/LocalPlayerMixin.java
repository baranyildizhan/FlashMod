package dev.baranhan.flashmod.mixin;

import dev.baranhan.flashmod.speed.PhaseHelper;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    /** Vanilla, bloga gomulen oyuncuyu en yakin bosluga iter; phasing sirasinda bu duvardan geri firlatirdi. */
    @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
    private void flashmod$noPushOut(double x, double z, CallbackInfo ci) {
        if (PhaseHelper.clientLocalPhasing) ci.cancel();
    }
}
