package dev.baranhan.flashmod.mixin;

import dev.baranhan.flashmod.speed.PhaseHelper;
import dev.baranhan.flashmod.speed.WallState;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * canEnterPose bazi surumlerde Player'da, bazilarinda Entity'de tanimli; ikisine de require=0 ile baglaniyoruz,
 * hangisi varsa o calisir. Duvarin icindeyken poz egilme/yuzmeye donmesin.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "canEnterPose", at = @At("HEAD"), cancellable = true, require = 0)
    private void flashmod$phasePose(Pose pose, CallbackInfoReturnable<Boolean> cir) {
        Player p = (Player) (Object) this;
        if (PhaseHelper.isPhasing(p) || WallState.is(p)) cir.setReturnValue(true);
    }
}
