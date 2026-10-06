package dev.baranhan.flashmod.ultimate;

import dev.baranhan.flashmod.FlashMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Ultimate icin sunucu olaylari: tick, iptal kosullari, dokunulmazlik, stasis'teki hedefin kilitlenmesi. */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class UltimateEvents {
    private UltimateEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) UltimateManager.serverTick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().getServer() != null) UltimateManager.abortInvolving(event.getEntity().getServer(), event.getEntity());
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity().getServer() != null) UltimateManager.abortInvolving(event.getEntity().getServer(), event.getEntity());
    }

    /** Caster olurse iptal. Hedef olurse sinematik devam eder (vurus 2 bosa gider). */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && UltimateManager.sessionOf(sp) != null) {
            UltimateManager.abort(sp.server, UltimateManager.sessionOf(sp));
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        Entity e = event.getEntity();
        if (e.level().isClientSide || !(e instanceof LivingEntity)) return;
        UltimateSession s = UltimateManager.asTarget(e);
        if (s != null && e instanceof net.minecraft.world.entity.Mob mob && s.stasis) {
            // dunyadan kalkiyor (olum, unload): noAi'yi her durumda geri ver
            mob.setNoAi(s.hadNoAi);
            mob.getPersistentData().remove(UltimateManager.NOAI_KEY);
            s.stasis = false;
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide) UltimateManager.restorePersisted(event.getEntity());
    }

    /** Caster sinematik boyunca hasar almaz; stasis'teki hedef saldiramaz. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && UltimateManager.sessionOf(sp) != null) {
            event.setCanceled(true);
            return;
        }
        Entity src = event.getSource().getEntity();
        if (src != null) {
            UltimateSession s = UltimateManager.asTarget(src);
            if (s != null && s.stasis) event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        UltimateSession s = UltimateManager.asTarget(event.getEntity());
        if (s != null && !s.launched) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        UltimateSession s = UltimateManager.asTarget(event.getEntity());
        if ((s != null && s.stasis) || (event.getEntity() instanceof ServerPlayer sp && UltimateManager.sessionOf(sp) != null)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        UltimateSession s = UltimateManager.asTarget(event.getEntity());
        if ((s != null && s.stasis) || UltimateManager.sessionOf(event.getEntity()) != null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        UltimateManager.abortAll(event.getServer());
    }
}
