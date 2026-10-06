package dev.baranhan.flashmod.ultimate;

import dev.baranhan.flashmod.FlashMod;
import dev.baranhan.flashmod.config.FlashServerConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;

/** Ultimate hasar turu (data-driven, data/flashmod/damage_type/speed_force_punch.json) ve hesaplar. */
public final class UltimateDamage {
    public static final ResourceKey<DamageType> SPEED_FORCE_PUNCH =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(FlashMod.MODID, "speed_force_punch"));
    public static final TagKey<EntityType<?>> IMMUNE =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(FlashMod.MODID, "ultimate_immune"));
    public static final TagKey<Block> UNBREAKABLE =
            TagKey.create(Registries.BLOCK, new ResourceLocation(FlashMod.MODID, "ultimate_unbreakable"));

    private UltimateDamage() {}

    public static DamageSource source(ServerLevel level, Entity caster) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(SPEED_FORCE_PUNCH), caster);
    }

    public static float hit2(LivingEntity target) {
        double base = FlashServerConfig.ULT_HIT2_BASE.get();
        double pct = FlashServerConfig.ULT_HIT2_PERCENT.get();
        double max = FlashServerConfig.ULT_HIT2_MAX.get();
        return (float) Math.min(base + target.getMaxHealth() * pct, max);
    }
}
