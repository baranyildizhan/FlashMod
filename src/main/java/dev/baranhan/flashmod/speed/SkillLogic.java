package dev.baranhan.flashmod.speed;

import dev.baranhan.flashmod.FlashSounds;
import dev.baranhan.flashmod.config.FlashServerConfig;
import dev.baranhan.flashmod.entity.AfterimageEntity;
import dev.baranhan.flashmod.network.FlashNetwork;
import dev.baranhan.flashmod.network.SkillFxPacket;
import dev.baranhan.flashmod.network.SkillSyncPacket;
import dev.baranhan.flashmod.ultimate.UltimateManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sunucu tarafi yeni yetenekler:
 *  - Kinetik yuk: kostukca yumrukta birikir (0..100), kosmayinca yavasca soner. Bir sonraki yumruk (oyuncu
 *    saldirisi) yuku birakir: ek hasar, yuke gore savurma, simsek patlamasi.
 *  - Zaman kalintisi: hizcinin olduğu yerde donmus parlak goruntusu kalir (AfterimageEntity, dusmanlari ceker),
 *    hizci girdi yonune atilir ve kisa bir an gorunmez olur.
 *  - Geri sarma: son birkac saniyenin konum/bakis/can kaydi tutulur; kullaninca hizci kendi yolunu tersine kosar
 *    (hareketi istemcide, yol bu paketle gider), sonunda tam o anki yere oturur, can o andakine doner.
 */
public final class SkillLogic {
    public static final int DECOY = 0, REWIND = 1;
    public static final float MAX_KINETIC = 100F;

    private static final class Sample {
        final double x, y, z;
        final float yaw, pitch, health, speed;
        final int air, fire;

        Sample(ServerPlayer p, float speed) {
            this.x = p.getX();
            this.y = p.getY();
            this.z = p.getZ();
            this.yaw = p.getYRot();
            this.pitch = p.getXRot();
            this.health = p.getHealth();
            this.air = p.getAirSupply();
            this.fire = p.getRemainingFireTicks();
            this.speed = speed;
        }
    }

    private static final class State {
        float kinetic;
        int idle;
        boolean full;
        double lx, ly, lz;
        boolean hasLast;
        long decoyReady, rewindReady;
        int decoyTotal, rewindTotal;
        @Nullable UUID decoy;
        // gecmis (en yeni sonda)
        final ArrayDeque<Sample> history = new ArrayDeque<>();
        @Nullable ResourceKey<Level> dim;
        // geri sarma
        boolean rewinding;
        long rewindStart;
        int rewindTicks;
        @Nullable Sample rewindTo;
        // savurma (vanilla geri itmesinden sonra uygulanir)
        @Nullable LivingEntity kbTarget;
        Vec3 kb = Vec3.ZERO;
        // son gonderilen
        float sKinetic = -1F;
        long sBlitz = -1, sUlt = -1;
        long sDecoy = -1, sRewind = -1;
        int syncCooldown;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** Savurmasi bekleyen hizcilar (oyuncu tick'i saldiridan once gelebilir; sunucu tick sonunda islenir). */
    private static final List<UUID> PENDING_KB = new ArrayList<>();

    private SkillLogic() {}

    private static State state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    /** Kinetik yukten en fazla 'amount' kadar al (simsek mizragi sarji); alinan miktari doner. */
    public static float takeKinetic(Player p, float amount) {
        State s = state(p);
        float take = Math.max(0F, Math.min(amount, s.kinetic));
        s.kinetic -= take;
        s.idle = 0;
        if (take > 0F) s.full = false;
        return take;
    }

    /** Kullanilmayan sarji kinetik yuke iade et. */
    public static void giveKinetic(Player p, float amount) {
        State s = state(p);
        s.kinetic = Math.min(MAX_KINETIC, s.kinetic + Math.max(0F, amount));
    }

    public static float kinetic(Player p) {
        State s = STATES.get(p.getUUID());
        return s == null ? 0F : s.kinetic;
    }

    public static boolean isRewinding(Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.rewinding;
    }

    // ---------------------------------------------------------------- tick

    public static void serverTick(ServerPlayer p) {
        State s = state(p);
        long now = p.level().getGameTime();
        boolean active = SpeedsterData.isActive(p) && !p.isSpectator() && p.isAlive();

        double h = 0;
        if (s.hasLast) {
            double dx = p.getX() - s.lx, dz = p.getZ() - s.lz;
            h = Math.min(Math.sqrt(dx * dx + dz * dz), 50.0D);
        }
        boolean jumped = s.hasLast && (Math.abs(p.getX() - s.lx) + Math.abs(p.getY() - s.ly) + Math.abs(p.getZ() - s.lz) > 96.0D);
        s.lx = p.getX();
        s.ly = p.getY();
        s.lz = p.getZ();
        s.hasLast = true;

        if (s.rewinding) {
            tickRewind(p, s, now);
            sync(p, s, false);
            return;
        }

        // --- gecmis (geri sarma icin)
        if (s.dim != p.level().dimension() || jumped || !active) s.history.clear();
        s.dim = p.level().dimension();
        boolean busy = BlitzLogic.isActive(p) || UltimateManager.involved(p);
        if (active && !busy) {
            s.history.addLast(new Sample(p, (float) h));
            int max = (int) Math.round(FlashServerConfig.REWIND_SECONDS.get() * 20.0D) + 1;
            while (s.history.size() > max) s.history.removeFirst();
        } else if (busy) {
            s.history.clear();
        }

        // --- kinetik yuk
        if (!active || !FlashServerConfig.KINETIC_ENABLED.get() || busy || TornadoLogic.isActive(p)) {
            if (!active) s.kinetic = 0F;
        } else {
            if (h > 0.15D) {
                s.idle = 0;
                s.kinetic = Math.min(MAX_KINETIC, s.kinetic + (float) (Math.min(h, 6.0D) * FlashServerConfig.KINETIC_GAIN.get()));
            } else if (++s.idle > 40 && !AbilityLogic.isCharging(p)) {
                s.kinetic = Math.max(0F, s.kinetic - 0.8F);
            }
        }
        boolean full = s.kinetic >= MAX_KINETIC - 0.01F;
        if (full && !s.full) sound(p, FlashSounds.KINETIC_READY.get(), 0.7F, 1.0F);
        s.full = full;

        sync(p, s, false);
    }

    /** Sunucu tick sonu: bu tick'te kinetik yumruk yiyen hedeflere savurma (vanilla geri itmesinin ustune). */
    public static void endServerTick() {
        if (PENDING_KB.isEmpty()) return;
        for (UUID id : PENDING_KB) {
            State s = STATES.get(id);
            if (s == null || s.kbTarget == null) continue;
            LivingEntity t = s.kbTarget;
            if (t.isAlive()) {
                Vec3 v = t.getDeltaMovement();
                t.setDeltaMovement(v.x * 0.3D + s.kb.x, Math.max(v.y, 0D) + s.kb.y, v.z * 0.3D + s.kb.z);
                t.hurtMarked = true;
            }
            s.kbTarget = null;
        }
        PENDING_KB.clear();
    }

    // ---------------------------------------------------------------- kinetik yumruk

    /** LivingHurtEvent: oyuncunun dogrudan yumrugu yuku birakir. */
    public static void onHurt(LivingHurtEvent event) {
        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof ServerPlayer sp) || src.getDirectEntity() != sp || !src.is(DamageTypes.PLAYER_ATTACK)) return;
        if (!FlashServerConfig.KINETIC_ENABLED.get() || !SpeedsterData.isActive(sp)) return;
        State s = STATES.get(sp.getUUID());
        if (s == null || s.rewinding || BlitzLogic.isActive(sp) || s.kinetic < FlashServerConfig.KINETIC_MIN.get().floatValue()) return;
        LivingEntity t = event.getEntity();
        if (t == sp) return;
        float k = s.kinetic / MAX_KINETIC;
        s.kinetic = 0F;
        s.full = false;
        event.setAmount(event.getAmount() + FlashServerConfig.KINETIC_DAMAGE.get().floatValue() * k);

        Vec3 dir = new Vec3(t.getX() - sp.getX(), 0, t.getZ() - sp.getZ());
        if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(sp.getLookAngle().x, 0, sp.getLookAngle().z);
        dir = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
        double kb = FlashServerConfig.KINETIC_KNOCKBACK.get() * k * (1.0D - t.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) * 0.7D);
        s.kbTarget = t;
        s.kb = new Vec3(dir.x * kb, 0.18D + 0.4D * k, dir.z * kb);
        if (!PENDING_KB.contains(sp.getUUID())) PENDING_KB.add(sp.getUUID());

        Vec3 hit = t.position().add(-dir.x * t.getBbWidth() * 0.5D, t.getBbHeight() * 0.62D, -dir.z * t.getBbWidth() * 0.5D);
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> sp),
                SkillFxPacket.simple(SkillFxPacket.KINETIC_HIT, sp.getUUID(), t.getId(), hit.x, hit.y, hit.z,
                        (float) dir.x, 0F, (float) dir.z, k, SpeedsterData.getCore(sp), SpeedsterData.getGlow(sp)));
        Level lv = sp.level();
        lv.playSound(null, hit.x, hit.y, hit.z, FlashSounds.KINETIC_PUNCH.get(), SoundSource.PLAYERS, 0.7F + 0.7F * k,
                1.15F - 0.25F * k);
        if (k >= 0.95F) { // tam yuk: Blitz finalindeki vanilla carpma katmanlari
            lv.playSound(null, hit.x, hit.y, hit.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.6F, 0.35F);
            lv.playSound(null, hit.x, hit.y, hit.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.9F, 1.2F);
            lv.playSound(null, hit.x, hit.y, hit.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5F, 1.6F);
        }
        sync(sp, s, true);
    }

    // ---------------------------------------------------------------- kullanim

    public static void cast(ServerPlayer p, int skill, float dx, float dz) {
        State s = state(p);
        if (!SpeedsterData.isActive(p) || p.isSpectator() || !p.isAlive() || s.rewinding) return;
        if (UltimateManager.involved(p) || BlitzLogic.isActive(p) || TornadoLogic.isActive(p)) return;
        long now = p.level().getGameTime();
        if (skill == DECOY) castDecoy(p, s, now, dx, dz);
        else if (skill == REWIND) castRewind(p, s, now);
    }

    private static boolean ready(ServerPlayer p, long readyAt, long now, float cost) {
        if (readyAt > now) {
            p.displayClientMessage(Component.translatable("msg.flashmod.skill_cooldown", (readyAt - now + 19) / 20), true);
            return false;
        }
        if (AbilityLogic.energy(p) + 1.0E-3F < cost) {
            p.displayClientMessage(Component.translatable("msg.flashmod.skill_energy"), true);
            return false;
        }
        return true;
    }

    private static void castDecoy(ServerPlayer p, State s, long now, float dx, float dz) {
        if (!FlashServerConfig.DECOY_ENABLED.get()) return;
        float cost = FlashServerConfig.DECOY_ENERGY.get().floatValue();
        if (!ready(p, s.decoyReady, now, cost) || !AbilityLogic.consumeEnergy(p, cost)) return;
        s.decoyTotal = FlashServerConfig.DECOY_COOLDOWN.get() * 20;
        s.decoyReady = now + s.decoyTotal;

        ServerLevel level = p.serverLevel();
        if (s.decoy != null && level.getEntity(s.decoy) instanceof AfterimageEntity old) old.dissolve();
        boolean running = Math.sqrt(p.getDeltaMovement().horizontalDistanceSqr()) > 0.2D
                || (s.history.size() > 1 && s.history.peekLast().speed > 0.25F);
        AfterimageEntity img = AfterimageEntity.create(p, running);
        level.addFreshEntity(img);
        s.decoy = img.getUUID();

        // atilma yonu: istemcinin girdisi (yatay, normalize); yoksa bakisin sagi
        double l = Math.sqrt(dx * dx + dz * dz);
        double ux, uz;
        if (l > 1.0E-3 && Double.isFinite(l)) { ux = dx / l; uz = dz / l; }
        else {
            double yaw = Math.toRadians(p.getYRot());
            ux = -Math.cos(yaw);
            uz = -Math.sin(yaw);
        }
        float dist = FlashServerConfig.DECOY_DASH.get().floatValue();
        int vanish = FlashServerConfig.DECOY_VANISH_TICKS.get();
        if (vanish > 0) p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, vanish, 0, false, false, false));
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                SkillFxPacket.simple(SkillFxPacket.DECOY_CAST, p.getUUID(), img.getId(), p.getX(), p.getY(), p.getZ(),
                        (float) ux, 0F, (float) uz, dist, SpeedsterData.getCore(p), SpeedsterData.getGlow(p)));
        sound(p, FlashSounds.DECOY_CAST.get(), 1.0F, 1.0F);
        sync(p, s, true);
    }

    private static void castRewind(ServerPlayer p, State s, long now) {
        if (!FlashServerConfig.REWIND_ENABLED.get() || AbilityLogic.isWallRunning(p)) return;
        if (s.history.size() < 20) {
            p.displayClientMessage(Component.translatable("msg.flashmod.rewind_short"), true);
            return;
        }
        float cost = FlashServerConfig.REWIND_ENERGY.get().floatValue();
        if (!ready(p, s.rewindReady, now, cost) || !AbilityLogic.consumeEnergy(p, cost)) return;

        // yol: yeniden eskiye
        Sample[] path = s.history.toArray(new Sample[0]);
        int n = path.length;
        double len = 0;
        for (int i = 1; i < n; i++) {
            len += Math.sqrt(sq(path[i].x - path[i - 1].x) + sq(path[i].y - path[i - 1].y) + sq(path[i].z - path[i - 1].z));
        }
        // sure: yol uzunluguna gore; tick basina <= 7.5 blok (cok oyunculuda vanilla "cok hizli hareket" kontrolu
        // ~10 blok/paket), 0.9 - 6 sn. Tek oyunculuda sahip bu kontrolden muaf, cok uzun yollar 6 sn'ye sigar.
        int ticks = (int) Math.max(18, Math.min(120, Math.ceil(len / 7.5D)));
        float[] data = new float[n * 6];
        for (int i = 0; i < n; i++) {
            Sample q = path[n - 1 - i];
            data[i * 6] = (float) (q.x - p.getX());
            data[i * 6 + 1] = (float) (q.y - p.getY());
            data[i * 6 + 2] = (float) (q.z - p.getZ());
            data[i * 6 + 3] = q.yaw;
            data[i * 6 + 4] = q.pitch;
            data[i * 6 + 5] = q.speed;
        }
        s.rewinding = true;
        s.rewindStart = now;
        s.rewindTicks = ticks;
        s.rewindTo = path[0];
        s.history.clear();
        s.kinetic = 0F;
        s.rewindTotal = FlashServerConfig.REWIND_COOLDOWN.get() * 20;
        s.rewindReady = now + ticks + s.rewindTotal;
        p.fallDistance = 0F;
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new SkillFxPacket(SkillFxPacket.REWIND_START, p.getUUID(), p.getId(), p.getX(), p.getY(), p.getZ(),
                        0F, 0F, 0F, (float) len, SpeedsterData.getCore(p), SpeedsterData.getGlow(p), ticks, data));
        sound(p, FlashSounds.REWIND.get(), 1.0F, 1.0F);
        sync(p, s, true);
    }

    private static void tickRewind(ServerPlayer p, State s, long now) {
        p.fallDistance = 0F;
        p.setDeltaMovement(Vec3.ZERO);
        p.clearFire();
        if (now - s.rewindStart < s.rewindTicks) return;
        // bitis: tam kayitli yere ve bakisa otur, can/hava/ates o ana doner
        Sample to = s.rewindTo;
        s.rewinding = false;
        s.rewindTo = null;
        s.hasLast = false;
        if (to == null) return;
        p.connection.teleport(to.x, to.y, to.z, to.yaw, to.pitch);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0F;
        if (FlashServerConfig.REWIND_HEAL.get()) p.setHealth(Math.max(p.getHealth(), Math.min(p.getMaxHealth(), to.health)));
        p.setAirSupply(Math.max(p.getAirSupply(), to.air));
        if (to.fire <= 0) p.clearFire();
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                SkillFxPacket.simple(SkillFxPacket.REWIND_END, p.getUUID(), p.getId(), to.x, to.y, to.z, 0F, 1F, 0F, 1F,
                        SpeedsterData.getCore(p), SpeedsterData.getGlow(p)));
        p.level().playSound(null, to.x, to.y + 1.0D, to.z, FlashSounds.REWIND_END.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static double sq(double v) {
        return v * v;
    }

    private static void sound(ServerPlayer p, SoundEvent ev, float vol, float pitch) {
        p.level().playSound(null, p.getX(), p.getY() + 1.0D, p.getZ(), ev, SoundSource.PLAYERS, vol, pitch);
    }

    // ---------------------------------------------------------------- senkron / temizlik

    private static void sync(ServerPlayer p, State s, boolean force) {
        if (s.syncCooldown > 0) s.syncCooldown--;
        long blitz = BlitzLogic.cooldownUntil(p), ult = SpeedsterData.getUltCooldown(p);
        boolean changed = s.sDecoy != s.decoyReady || s.sRewind != s.rewindReady || s.sBlitz != blitz || s.sUlt != ult
                || (s.kinetic == 0F) != (s.sKinetic == 0F) || (s.kinetic >= MAX_KINETIC) != (s.sKinetic >= MAX_KINETIC);
        boolean valueChanged = Math.abs(s.sKinetic - s.kinetic) >= 1F;
        if (!force && !changed && !(valueChanged && s.syncCooldown == 0)) return;
        s.sKinetic = s.kinetic;
        s.sDecoy = s.decoyReady;
        s.sRewind = s.rewindReady;
        s.sBlitz = blitz;
        s.sUlt = ult;
        s.syncCooldown = 3;
        long now = p.level().getGameTime();
        int ultLeft = (int) Math.max(0, Math.min(Integer.MAX_VALUE, ult - now));
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new SkillSyncPacket(p.getUUID(), s.kinetic, (int) Math.max(0, s.decoyReady - now), s.decoyTotal,
                        (int) Math.max(0, s.rewindReady - now), s.rewindTotal,
                        (int) Math.max(0, blitz - now), BlitzLogic.cooldownTotal(),
                        ultLeft, Math.max(ultLeft, FlashServerConfig.ULT_COOLDOWN.get() * 20)));
    }

    /** Boyut degisimi / olum: gecmis gecersiz, yarim kalan geri sarma iptal. */
    public static void reset(Player p) {
        State s = STATES.get(p.getUUID());
        if (s == null) return;
        s.history.clear();
        s.rewinding = false;
        s.rewindTo = null;
        s.hasLast = false;
        s.kinetic = 0F;
    }

    public static void forget(UUID id) {
        STATES.remove(id);
        PENDING_KB.remove(id);
    }

    /** Sunucu kapanirken. */
    public static void clearAll() {
        STATES.clear();
        PENDING_KB.clear();
    }
}
