package dev.baranhan.flashmod.mixin;

import dev.baranhan.flashmod.client.BlitzAnim;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    /**
     * Vanilla animasyondan SONRA Blitz pozunu uygula (hizci: kosu/yumruk/tekme/fren..., hedef: savrulma/cokme).
     * PlayerModel kol/bacak katmanlarini (sleeve, pants) bu metottan donunce kopyaladigi icin onlar da takip eder.
     */
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"), require = 0)
    private void flashmod$blitzPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                    float netHeadYaw, float headPitch, CallbackInfo ci) {
        BlitzAnim.applyModel(entity, (HumanoidModel<?>) (Object) this);
    }
}
