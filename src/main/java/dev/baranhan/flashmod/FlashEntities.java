package dev.baranhan.flashmod;

import dev.baranhan.flashmod.entity.LightningSpearEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class FlashEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, FlashMod.MODID);

    public static final RegistryObject<EntityType<LightningSpearEntity>> LIGHTNING_SPEAR = ENTITIES.register("lightning_spear",
            () -> EntityType.Builder.<LightningSpearEntity>of(LightningSpearEntity::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("lightning_spear"));

    private FlashEntities() {}
}
