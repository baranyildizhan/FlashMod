package dev.baranhan.flashmod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.flashmod.client.BlitzClient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    /** Model senaryodaki yerde cizilirken golge varligin gercek (donmus) yerinde kalmasin. */
    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true, require = 0)
    private static void flashmod$hideShadow(PoseStack poseStack, MultiBufferSource buffers, Entity entity, float weight,
                                            float partialTick, LevelReader level, float size, CallbackInfo ci) {
        if (BlitzClient.hideShadow(entity)) ci.cancel();
    }
}
