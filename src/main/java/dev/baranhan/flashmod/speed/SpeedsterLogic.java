package dev.baranhan.flashmod.speed;

import dev.baranhan.flashmod.config.FlashServerConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraftforge.common.ForgeMod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hiz modeli:
 *  - Guc aciksa seviye hizi HER YONDE gecerli (geri, yan, yurume). Shift = hassas/normal yurume.
 *  - Vanilla sprint "gaz pedali": tam hizda sprint etmeye devam ettikce Overdrive carpani ustel olarak buyur
 *    (varsayilan her ~4.3 sn'de 2 kat). Sprint birakilinca carpan yavasca 1'e iner -> seviyenin normal hizi.
 *    Havadayken carpan korunur, duvara carpinca yariya duser.
 *  - Ekstra tus yok: sadece vanilla sprint.
 */
public final class SpeedsterLogic {
    private static final UUID SPEED_ID = UUID.fromString("7d3c1f2e-5b8a-4e61-9a0c-2f6b1e8d4c11");
    private static final UUID STEP_ID = UUID.fromString("a1e4b7c2-3d9f-4a85-b6e0-9c2d7f1a3e52");
    /** Vanilla movement_speed attribute'u 1024'te kirpilir; 0.1 taban * 1.3 sprint -> carpan ust siniri. */
    private static final double ATTRIBUTE_MULT_LIMIT = 7800.0D;
    /** Zemin hizi ~= attribute * 2.2 blok/tick (blok surtunmesi 0.6 icin). Sprint tabani 0.13 -> 0.286. */
    private static final double SPRINT_BLOCKS_PER_TICK = 0.286D;

    private static final Map<UUID, Float> LAST_EXHAUSTION = new HashMap<>();
    private static final Map<UUID, Double> OVERDRIVE = new HashMap<>();

    private SpeedsterLogic() {}

    public static void serverTick(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean active = SpeedsterData.isActive(player) && !player.isSpectator();
        boolean precise = player.isShiftKeyDown();
        double levelMul = 1.0D + SpeedsterData.speedBonus(SpeedsterData.getLevel(player)) * FlashServerConfig.SPEED_SCALE.get();

        double od = updateOverdrive(player, active && !precise, levelMul);

        double totalMul = levelMul * od;
        double cap = speedCap(player);
        if (cap > 0.0D) totalMul = Math.min(totalMul, Math.max(1.0D, cap / SPRINT_BLOCKS_PER_TICK));
        totalMul = Math.min(totalMul, ATTRIBUTE_MULT_LIMIT);

        double bonus = active && !precise ? totalMul - 1.0D : 0.0D;
        applyModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID, "Flash speed",
                bonus, AttributeModifier.Operation.MULTIPLY_TOTAL);

        // Step assist: sadece aktifken, egilmiyorken, binekte/elytrada degilken.
        boolean step = active && FlashServerConfig.STEP_ASSIST.get() && !precise
                && !player.isPassenger() && !player.isFallFlying();
        applyModifier(player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get()), STEP_ID, "Flash step assist",
                step ? FlashServerConfig.STEP_HEIGHT_BONUS.get() : 0.0D, AttributeModifier.Operation.ADDITION);

        PhaseHelper.serverTick(player, active);
        handleHunger(player, active && !precise);
    }

    private static double updateOverdrive(ServerPlayer player, boolean canRun, double levelMul) {
        UUID id = player.getUUID();
        double od = OVERDRIVE.getOrDefault(id, 1.0D);
        if (!canRun || !FlashServerConfig.OVERDRIVE.get()) {
            OVERDRIVE.remove(id);
            return 1.0D;
        }
        boolean pushing = player.isSprinting() && !player.isPassenger() && !player.isFallFlying();
        if (pushing && player.horizontalCollision && !PhaseHelper.isPhasing(player)) {
            od = 1.0D + (od - 1.0D) * 0.5D;                                   // duvara carpti
        } else if (pushing && (player.onGround() || player.isInWater())) {
            od *= 1.0D + FlashServerConfig.OVERDRIVE_GROWTH.get();            // gaz
        } else if (!pushing) {
            od = 1.0D + (od - 1.0D) * (1.0D - FlashServerConfig.OVERDRIVE_DECAY.get()); // yavasca seviye hizina don
        }                                                                     // havada: korunur
        double max = Math.min(FlashServerConfig.OVERDRIVE_MAX.get(), ATTRIBUTE_MULT_LIMIT / levelMul);
        od = Mth.clamp(od, 1.0D, Math.max(1.0D, max));
        if (od < 1.0005D) {
            OVERDRIVE.remove(id);
            return 1.0D;
        }
        OVERDRIVE.put(id, od);
        return od;
    }

    /** Singleplayer/LAN sahibi disindaki herkes icin vanilla anti-cheat'e takilmayan hiz siniri (blok/tick). */
    private static double speedCap(ServerPlayer player) {
        double cap = FlashServerConfig.MULTIPLAYER_SPEED_CAP.get();
        if (cap <= 0.0D) return 0.0D;
        MinecraftServer server = player.getServer();
        if (server != null && server.isSingleplayerOwner(player.getGameProfile())) return 0.0D;
        return cap;
    }

    private static void applyModifier(@Nullable AttributeInstance inst, UUID id, String name, double amount,
                                      AttributeModifier.Operation op) {
        if (inst == null) return;
        AttributeModifier current = inst.getModifier(id);
        if (amount == 0.0D) {
            if (current != null) inst.removeModifier(id);
            return;
        }
        if (current != null && Math.abs(current.getAmount() - amount) < 1.0E-6D * Math.max(1.0D, amount)) return;
        if (current != null) inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    /**
     * Vanilla exhaustion mesafeyle orantili -> astronomik hizda saniyeler icinde aclik.
     * Eklenen exhaustion'in sadece bir kismini birakiyoruz ve tick basina bir ust sinir koyuyoruz.
     */
    private static void handleHunger(ServerPlayer player, boolean running) {
        FoodData food = player.getFoodData();
        float now = food.getExhaustionLevel();
        Float prev = LAST_EXHAUSTION.get(player.getUUID());
        if (running && prev != null && now > prev) {
            float mult = FlashServerConfig.HUNGER_MULTIPLIER.get().floatValue();
            float kept = prev + Math.min((now - prev) * mult, 0.012F);
            setExhaustion(food, kept);
            now = kept;
        }
        LAST_EXHAUSTION.put(player.getUUID(), now);
    }

    /** FoodData'da public setter yok; AT kullanmadan NBT uzerinden ayarliyoruz. */
    private static void setExhaustion(FoodData food, float value) {
        CompoundTag tag = new CompoundTag();
        food.addAdditionalSaveData(tag);
        tag.putFloat("foodExhaustionLevel", value);
        food.readAdditionalSaveData(tag);
    }

    public static void forget(Player player) {
        LAST_EXHAUSTION.remove(player.getUUID());
        OVERDRIVE.remove(player.getUUID());
        PhaseHelper.forget(player.getUUID());
    }
}
