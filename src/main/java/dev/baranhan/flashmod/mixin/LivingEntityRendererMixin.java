package dev.baranhan.flashmod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.flashmod.client.render.BodyPoseCapture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    /**
     * Ana model cizildikten hemen sonra PoseStack tam model donusumundedir -> govde noktalarini yakala.
     * require=0: hedef bulunamazsa oyun cokmez, efektler eski (enterpolasyon) yontemine duser.
     */
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V",
                    shift = At.Shift.AFTER),
            require = 0)
    private void flashmod$captureBody(LivingEntity entity, float yaw, float partialTick, PoseStack poseStack,
                                      MultiBufferSource buffers, int light, CallbackInfo ci) {
        dev.baranhan.flashmod.client.render.FlameAnchor.capture(entity, poseStack);
        // GUI'de (envanter, kostum dolabi) cizilen model ortografik projeksiyonla gelir: yakalama, yoksa iz/simsekler
        // o kare GUI koordinatlarina yapisir.
        if (com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix().m33() == 1.0F) return;
        BodyPoseCapture.capture(entity, poseStack, ((LivingEntityRenderer<?, ?>) (Object) this).getModel());
    }
}
