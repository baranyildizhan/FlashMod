package dev.baranhan.flashmod;

import dev.baranhan.flashmod.client.ClientInit;
import dev.baranhan.flashmod.config.FlashClientConfig;
import dev.baranhan.flashmod.config.FlashServerConfig;
import dev.baranhan.flashmod.network.FlashNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(FlashMod.MODID)
public final class FlashMod {
    public static final String MODID = "flashmod";

    public FlashMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);
        FlashSounds.SOUNDS.register(modBus);
        FlashEntities.ENTITIES.register(modBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, FlashClientConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, FlashServerConfig.SPEC);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientInit::init);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(FlashNetwork::register);
    }
}
