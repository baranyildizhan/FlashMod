package dev.baranhan.flashmod.mixin;

import dev.baranhan.flashmod.client.skin.SkinManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    /** Kostum dolabi skin'i (ve onizleme) varsa vanilla skin yerine o doku. */
    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
    private void flashmod$skin(CallbackInfoReturnable<ResourceLocation> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        if (SkinManager.previewActive() && self == Minecraft.getInstance().player) {
            if (SkinManager.previewTexture() != null) cir.setReturnValue(SkinManager.previewTexture());
            return; // onizlemede "varsayilan" secili: vanilla skin
        }
        SkinManager.Skin s = SkinManager.get(self.getUUID());
        if (s != null) cir.setReturnValue(s.texture());
    }

    /** Model tipi (klasik / ince kol): oyuncu renderer'i buna gore secilir. */
    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void flashmod$model(CallbackInfoReturnable<String> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        if (SkinManager.previewActive() && self == Minecraft.getInstance().player) {
            if (SkinManager.previewTexture() != null) cir.setReturnValue(SkinManager.previewSlim() ? "slim" : "default");
            return;
        }
        SkinManager.Skin s = SkinManager.get(self.getUUID());
        if (s != null) cir.setReturnValue(s.slim() ? "slim" : "default");
    }
}
