package dev.baranhan.flashmod.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.baranhan.flashmod.FlashEntities;
import dev.baranhan.flashmod.FlashMod;
import dev.baranhan.flashmod.client.render.LightningSpearRenderer;
import dev.baranhan.flashmod.client.render.SpeedHudOverlay;
import dev.baranhan.flashmod.client.render.SpeedPostEffect;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = FlashMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(FlashKeys.TOGGLE);
        event.register(FlashKeys.LEVEL_UP);
        event.register(FlashKeys.LEVEL_DOWN);
        event.register(FlashKeys.COLORS);
        event.register(FlashKeys.PHASE);
        event.register(FlashKeys.TORNADO);
        event.register(FlashKeys.AERIAL);
        event.register(FlashKeys.BLITZ);
        event.register(FlashKeys.THROW);
        event.register(FlashKeys.WARDROBE);
        event.register(FlashKeys.ULTIMATE);
        event.register(FlashKeys.ULTIMATE_SKIP);
        event.register(FlashKeys.SLOWMO);
        event.register(FlashKeys.DECOY);
        event.register(FlashKeys.REWIND);
        event.register(FlashKeys.FISTS);
    }

    @SubscribeEvent
    public static void onOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.VIGNETTE.id(), "speed_effects", SpeedHudOverlay::renderEffects);
        event.registerAbove(VanillaGuiOverlay.VIGNETTE.id(), "skill_rewind", dev.baranhan.flashmod.client.skill.SkillOverlay::render);
        event.registerAboveAll("speed_hud", SpeedHudOverlay::renderHud);
        event.registerAboveAll("blitz_bars", BlitzClient::renderBars);
        event.registerAboveAll("flash_ultimate", dev.baranhan.flashmod.client.ultimate.UltOverlay::render);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FlashEntities.LIGHTNING_SPEAR.get(), LightningSpearRenderer::new);
        event.registerEntityRenderer(FlashEntities.AFTERIMAGE.get(), dev.baranhan.flashmod.client.render.AfterimageRenderer::new);
    }

    /** Post-processing icin core shader (assets/flashmod/shaders/core/speed_post.*). Shader pack gerektirmez. */
    @SubscribeEvent
    public static void onShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        new ResourceLocation(FlashMod.MODID, "speed_post"), DefaultVertexFormat.POSITION_TEX),
                SpeedPostEffect::setShader);
    }
}
