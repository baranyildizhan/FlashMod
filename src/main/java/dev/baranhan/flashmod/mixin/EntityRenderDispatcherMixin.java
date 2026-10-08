package dev.baranhan.flashmod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.flashmod.client.BlitzClient;
import dev.baranhan.flashmod.client.render.FlameAnchor;
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

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private <E extends Entity> void flashmod$flameBegin(E entity, double x, double y, double z, float yaw, float partialTick,
                                                        PoseStack poseStack, MultiBufferSource buffers, int light,
                                                        CallbackInfo ci) {
        FlameAnchor.begin(entity);
    }

    /** Alev, modelin tasindigi yerde (Blitz/ultimate/tornado); model gizliyse alev de yok. */
    @Inject(method = "renderFlame", at = @At("HEAD"), cancellable = true, require = 0)
    private void flashmod$flameMove(PoseStack poseStack, MultiBufferSource buffers, Entity entity, CallbackInfo ci) {
        if (!FlameAnchor.beforeFlame(poseStack, entity)) ci.cancel();
    }

    @Inject(method = "renderFlame", at = @At("RETURN"), require = 0)
    private void flashmod$flameEnd(PoseStack poseStack, MultiBufferSource buffers, Entity entity, CallbackInfo ci) {
        FlameAnchor.afterFlame(poseStack);
    }
}
