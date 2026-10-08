package dev.baranhan.flashmod;

import dev.baranhan.flashmod.entity.AfterimageEntity;
import dev.baranhan.flashmod.entity.LightningSpearEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = FlashMod.MODID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class FlashEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, FlashMod.MODID);

    public static final RegistryObject<EntityType<LightningSpearEntity>> LIGHTNING_SPEAR = ENTITIES.register("lightning_spear",
            () -> EntityType.Builder.<LightningSpearEntity>of(LightningSpearEntity::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("lightning_spear"));

    /** Zaman kalintisi (decoy): dusmanlarin yoneldigi donmus goruntu. */
    public static final RegistryObject<EntityType<AfterimageEntity>> AFTERIMAGE = ENTITIES.register("afterimage",
            () -> EntityType.Builder.<AfterimageEntity>of(AfterimageEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .updateInterval(10)
                    .fireImmune()
                    .noSummon()
                    .build("afterimage"));

    private FlashEntities() {}

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onAttributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(AFTERIMAGE.get(), AfterimageEntity.createAttributes().build());
    }
}
