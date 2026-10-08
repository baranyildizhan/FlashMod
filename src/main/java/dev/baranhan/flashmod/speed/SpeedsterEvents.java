package dev.baranhan.flashmod.speed;

import dev.baranhan.flashmod.FlashMod;
import dev.baranhan.flashmod.config.FlashServerConfig;
import dev.baranhan.flashmod.network.FlashNetwork;
import dev.baranhan.flashmod.network.SyncSpeedsterPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class SpeedsterEvents {
    private SpeedsterEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player instanceof ServerPlayer sp) {
            SpeedsterLogic.serverTick(sp);
            TornadoLogic.serverTick(sp);
            BlitzLogic.serverTick(sp);
            AbilityLogic.serverTick(sp);
            SkillLogic.serverTick(sp);
            ChunkPreloader.tick(sp);
            // Phasing / tornado: tick sonunda noPhysics=true. Hareket paketleri tick'ler ARASINDA islenir, boylece
            // vanilla'nin "duvara girdi / yanlis hareket -> geri isinla" kontrolu atlanir (tornado cemberin kirisi
            // boyunca ziplar, aradaki engeller yuzunden geri isinlanmasin). Player.tick bir sonraki tick basinda sifirlar.
            // Geri sarma: istemci kendi gectigi yoldan geri kosar (araya sonradan konan bloklar geri isinlatmasin).
            if (PhaseHelper.isPhasing(sp) || TornadoLogic.isActive(sp) || SkillLogic.isRewinding(sp)) sp.noPhysics = true;
        }
    }

    /** Duvarin icindeyken bogulma hasari yok. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && PhaseHelper.isPhasing(sp)
                && event.getSource().is(DamageTypes.IN_WALL)) {
            event.setCanceled(true);
        }
        // Blitz sinematigi ve geri sarma sirasinda hizci hasar almaz
        if (event.getEntity() instanceof ServerPlayer sp && (BlitzLogic.isActive(sp) || SkillLogic.isRewinding(sp))) {
            event.setCanceled(true);
        }
    }

    /** Kinetik yumruk: biriken yuk oyuncunun bir sonraki dogrudan darbesinde birakilir. */
    @SubscribeEvent
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (!event.getEntity().level().isClientSide) SkillLogic.onHurt(event);
    }

    /** Zaman yavaslatma: her sunucu tick'inin basinda oran guncellenir ve herkese gonderilir. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) TimeControl.serverTick(event.getServer());
        else SkillLogic.endServerTick();
    }

    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        TimeControl.reset();
        SkillLogic.clearAll();
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player p && !p.level().isClientSide
                && SpeedsterData.isActive(p) && FlashServerConfig.NEGATE_FALL_DAMAGE.get()) {
            event.setDamageMultiplier(0F);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            FlashNetwork.syncAllToTrackingAndSelf(sp);
            TimeControl.onLogin(sp);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SpeedsterLogic.forget(event.getEntity());
        TornadoLogic.forget(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer sp) BlitzLogic.forget(sp);
        AbilityLogic.forget(event.getEntity().getUUID());
        SkillLogic.forget(event.getEntity().getUUID());
        ChunkPreloader.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) FlashNetwork.syncAllToTrackingAndSelf(sp);
        SkillLogic.reset(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) FlashNetwork.syncAllToTrackingAndSelf(sp);
        SkillLogic.reset(event.getEntity());
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player target && event.getEntity() instanceof ServerPlayer watcher) {
            FlashNetwork.sendTo(watcher, SyncSpeedsterPacket.of(target));
            FlashNetwork.sendTo(watcher, dev.baranhan.flashmod.network.SkinSyncPacket.of(target));
            TornadoLogic.sendTo(watcher, target);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData().getCompound(SpeedsterData.ROOT);
        event.getEntity().getPersistentData().put(SpeedsterData.ROOT, old.copy());
        if (event.isWasDeath()) SpeedsterData.setActive(event.getEntity(), false);
        TornadoLogic.forget(event.getOriginal().getUUID());
    }
}
