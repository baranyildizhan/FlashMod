package dev.baranhan.flashmod.mixin;

import dev.baranhan.flashmod.speed.TimeControl;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    /** Sunucu dongusundeki 50 ms tick araligi -> agir cekimde 50 / oran (orn. 200 ms = 5 tick/sn). */
    @ModifyConstant(method = "runServer", constant = @Constant(longValue = 50L))
    private long flashmod$tickLength(long vanilla) {
        return TimeControl.tickMillis(vanilla);
    }
}
