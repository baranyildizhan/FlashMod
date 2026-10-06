package dev.baranhan.flashmod.mixin;

import dev.baranhan.flashmod.client.TimeControlClient;
import net.minecraft.client.Timer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Timer.class)
public abstract class TimerMixin {
    @Shadow @Final private float msPerTick;

    /** Istemci tick suresi sunucuyla ayni oranda uzar: dunya, parcaciklar, animasyonlar akici sekilde yavaslar. */
    @Redirect(method = "advanceTime", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Timer;msPerTick:F"))
    private float flashmod$msPerTick(Timer timer) {
        return this.msPerTick / TimeControlClient.rate();
    }
}
