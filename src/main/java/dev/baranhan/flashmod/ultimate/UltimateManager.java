package dev.baranhan.flashmod.ultimate;

import dev.baranhan.flashmod.FlashSounds;
import dev.baranhan.flashmod.config.FlashServerConfig;
import dev.baranhan.flashmod.network.FlashNetwork;
import dev.baranhan.flashmod.network.UltimateEventPacket;
import dev.baranhan.flashmod.network.UltimateStartPacket;
import dev.baranhan.flashmod.speed.AbilityLogic;
import dev.baranhan.flashmod.speed.BlitzLogic;
import dev.baranhan.flashmod.speed.SpeedsterData;
import dev.baranhan.flashmod.speed.TornadoLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Sunucu otoritesi: aktivasyon, hedef, blink, stasis (UltimateScript betigiyle itme/havaya kalkis/cakilma), hasar,
 * yere carpma, iptal, cooldown.
 * Zaman startGameTime'dan hesaplanir; olaylar "yapildi mi" bayraklariyla >= ile tetiklenir (lag'e dayanikli).
 */
public final class UltimateManager {
    public static final String NOAI_KEY = "flashmod_stasis_prev_noai";
    private static final Map<UUID, UltimateSession> BY_CASTER = new HashMap<>();
    private static int nextId = 1;

    /** Konumlu sunucu sesleri: {tick, ses, nerede (0 caster, 1 hedef, 2 temas)}. */
    private static final Object[][] SOUNDS = {
            {UltimatePhase.BLINK, "ult_blink", 0}, {16, "ult_roar_crackle", 0}, {UltimatePhase.HIDE_BODY, "ult_whoosh_depart", 0},
            {UltimatePhase.HIT1, "ult_hit_light", 1}, {44, "ult_sonic_boom", 1}, {UltimatePhase.HIT2, "ult_impact_huge", 2},
            {UltimatePhase.LAUNCH_T, "ult_release_whoosh", 1}, {UltimatePhase.LAUNCH_T, "ult_launch_wind", 1},
            {UltimatePhase.AIR_BLINK, "ult_blink", 1}, {UltimatePhase.HIT3, "ult_sonic_boom", 1},
            {UltimatePhase.SLAM_T + 4, "ult_thunder_tail", 1}};

    private UltimateManager() {}

    public static boolean anyActive() {
        return !BY_CASTER.isEmpty();
    }

    @Nullable
    public static UltimateSession sessionOf(Player caster) {
        return BY_CASTER.get(caster.getUUID());
    }

    /** Bu varlik bir oturumda caster ya da hedef mi. */
    public static boolean involved(Entity e) {
        for (UltimateSession s : BY_CASTER.values()) {
            if (s.casterId.equals(e.getUUID()) || s.targetUuid.equals(e.getUUID())) return true;
        }
        return false;
    }

    @Nullable
    public static UltimateSession asTarget(Entity e) {
        for (UltimateSession s : BY_CASTER.values()) if (s.targetUuid.equals(e.getUUID())) return s;
        return null;
    }

    private static void deny(ServerPlayer p, String key, Object... args) {
        p.displayClientMessage(Component.translatable(key, args), true);
    }

    // ---------------------------------------------------------------- baslatma

    public static boolean tryStart(ServerPlayer caster, @Nullable LivingEntity forced, boolean debug) {
        if (!FlashServerConfig.ULT_ENABLED.get() && !debug) return false;
        if (!debug && !SpeedsterData.isActive(caster)) { deny(caster, "flashmod.ult.need_power"); return false; }
        if (involved(caster)) return false;
        if (!caster.isAlive() || caster.isSpectator() || caster.isSleeping()) return false;
        if (caster.isPassenger()) { deny(caster, "flashmod.ult.dismount"); return false; }
        if (BlitzLogic.isActive(caster) || TornadoLogic.isActive(caster) || AbilityLogic.isWallRunning(caster)) return false;
        long now = caster.level().getGameTime();
        long cd = SpeedsterData.getUltCooldown(caster);
        if (!debug && cd > now) {
            deny(caster, "flashmod.ult.cooldown", (cd - now + 19) / 20);
            return false;
        }
        float cost = FlashServerConfig.ULT_ENERGY.get().floatValue();
        if (!debug && AbilityLogic.energy(caster) + 1.0E-3F < cost) { deny(caster, "flashmod.ult.no_energy"); return false; }

        LivingEntity target = forced != null ? forced : UltimateTargeting.find(caster, debug);
        if (target == null || !UltimateTargeting.valid(caster, target, debug)) { deny(caster, "flashmod.ult.no_target"); return false; }

        Vec3 tp = target.position();
        double fx = tp.x - caster.getX(), fz = tp.z - caster.getZ();
        if (fx * fx + fz * fz < 1.0E-4) { fx = -Math.sin(Math.toRadians(caster.getYRot())); fz = Math.cos(Math.toRadians(caster.getYRot())); }
        double fl = Math.sqrt(fx * fx + fz * fz);
        fx /= fl;
        fz /= fl;
        float w = target.getBbWidth();
        float d0 = w > 1.2F ? 1.5F + w : UltimatePhase.ARENA_DISTANCE;
        Vec3 origin = null;
        float dUsed = d0;
        boolean airborne = !target.onGround() && FlashServerConfig.ULT_ALLOW_AIRBORNE.get();
        for (float d : new float[]{d0, d0 + 0.5F, d0 + 1.0F}) {
            Vec3 o = findStand(caster, new Vec3(tp.x - fx * d, tp.y, tp.z - fz * d), airborne);
            if (o != null) { origin = o; dUsed = d; break; }
        }
        if (origin == null) { deny(caster, "flashmod.ult.no_space"); return false; }
        float push = pushDistance(target, fx, fz, airborne);
        if (!debug && !AbilityLogic.consumeEnergy(caster, cost)) { deny(caster, "flashmod.ult.no_energy"); return false; }

        ServerLevel level = caster.serverLevel();
        ArenaFrame arena = new ArenaFrame(origin.x, origin.y, origin.z, fx, fz, dUsed);
        float scale = FlashServerConfig.ULT_DURATION_SCALE.get().floatValue();
        UltimateSession s = new UltimateSession(nextId++, caster.getUUID(), target.getId(), target.getUUID(), level.dimension(),
                arena, now + 2, scale, level.getRandom().nextLong(), caster.position(), debug);
        s.push = push;
        s.lockPos = origin;
        s.lockYaw = arena.forwardYaw();
        s.lastTargetPos = tp;
        for (ServerPlayer pl : level.players()) {
            if (pl == caster || pl == target || pl.distanceToSqr(origin) < 160 * 160) s.audience.add(pl.getUUID());
        }
        BY_CASTER.put(caster.getUUID(), s);
        startStasis(s, target);
        sendStart(level, s, caster);
        return true;
    }

    /** Kutu sigiyor mu + altinda zemin (en fazla 3 blok asagi tarar). Havadaysa ayni yukseklikte sabit kalir. */
    @Nullable
    private static Vec3 findStand(ServerPlayer caster, Vec3 o, boolean airborne) {
        AABB base = caster.getBoundingBox().move(-caster.getX(), -caster.getY(), -caster.getZ());
        if (airborne) {
            return caster.level().noCollision(caster, base.move(o)) ? o : null;
        }
        for (double y = o.y + 1.0D; y >= o.y - 3.0D; y -= 0.0625D) {
            Vec3 p = new Vec3(o.x, y, o.z);
            if (!caster.level().noCollision(caster, base.move(p))) continue;
            if (!caster.level().noCollision(caster, base.move(p.x, y - 0.0625D, p.z))) return p;
        }
        return null;
    }

    private static void sendStart(ServerLevel level, UltimateSession s, ServerPlayer caster) {
        Player tPlayer = level.getEntity(s.targetId) instanceof Player pl ? pl : null;
        int core = SpeedsterData.getCore(caster), glow = SpeedsterData.getGlow(caster);
        for (UUID id : s.audience) {
            ServerPlayer pl = level.getServer().getPlayerList().getPlayer(id);
            if (pl == null) continue;
            boolean isTarget = pl == tPlayer;
            boolean full = pl == caster || (isTarget && FlashServerConfig.ULT_TARGET_SEES.get());
            FlashNetwork.sendTo(pl, new UltimateStartPacket(s.id, caster.getId(), s.targetId, s.startGameTime, s.scale,
                    s.arena, s.startPos.x, s.startPos.y, s.startPos.z, core, glow, s.seed, isTarget, full, false, s.push));
        }
    }

    /** /flashult preview: sunucuda hicbir sey olmadan, sadece o istemcide gorsel onizleme. */
    public static void preview(ServerPlayer p, @Nullable LivingEntity target) {
        double fx = -Math.sin(Math.toRadians(p.getYRot())), fz = Math.cos(Math.toRadians(p.getYRot()));
        float d = UltimatePhase.ARENA_DISTANCE, push = 0F;
        if (target != null) { // hedef varsa arena gercek konumlara hizalanir (onizleme ile gercek kullanim ayni gorunsun)
            double dx = target.getX() - p.getX(), dz = target.getZ() - p.getZ(), l = Math.sqrt(dx * dx + dz * dz);
            if (l > 0.5) { fx = dx / l; fz = dz / l; d = (float) l; }
            push = pushDistance(target, fx, fz, !target.onGround());
        }
        ArenaFrame arena = new ArenaFrame(p.getX(), p.getY(), p.getZ(), fx, fz, d);
        FlashNetwork.sendTo(p, new UltimateStartPacket(-(nextId++), p.getId(), target == null ? -1 : target.getId(),
                p.level().getGameTime() + 2, 1F, arena, p.getX(), p.getY(), p.getZ(), SpeedsterData.getCore(p),
                SpeedsterData.getGlow(p), p.getRandom().nextLong(), false, true, true, push));
    }

    /**
     * Ilk vurusta hedefin ileri kayabilecegi mesafe: yol boyunca carpisma yok ve (havada degilse) altinda zemin var.
     * Ilk engelde durur; en fazla PUSH_MAX.
     */
    private static float pushDistance(LivingEntity target, double fx, double fz, boolean airborne) {
        AABB box = target.getBoundingBox();
        float ok = 0F;
        for (float p = 0.2F; p <= UltimatePhase.PUSH_MAX + 1.0E-3F; p += 0.2F) {
            AABB moved = box.move(fx * p, 0, fz * p);
            if (!target.level().noCollision(target, moved)) break;
            if (!airborne && target.level().noCollision(target, moved.move(0, -0.5, 0))) break; // ucurum
            ok = p;
        }
        return ok;
    }

    // ---------------------------------------------------------------- tick

    public static void serverTick(MinecraftServer server) {
        if (BY_CASTER.isEmpty()) return;
        Iterator<UltimateSession> it = new ArrayList<>(BY_CASTER.values()).iterator();
        while (it.hasNext()) {
            UltimateSession s = it.next();
            ServerLevel level = server.getLevel(s.dimension);
            ServerPlayer caster = server.getPlayerList().getPlayer(s.casterId);
            if (level == null || caster == null || !caster.isAlive() || caster.level() != level || caster.isSpectator()) {
                abort(server, s);
                continue;
            }
            tickSession(level, caster, s);
        }
    }

    private static void tickSession(ServerLevel level, ServerPlayer caster, UltimateSession s) {
        long gt = level.getGameTime();
        float t = s.t(gt);
        Entity te = level.getEntity(s.targetId);
        LivingEntity target = te instanceof LivingEntity le && le.isAlive() ? le : null;
        if (target != null) s.lastTargetPos = target.position();
        if (te == null && t < UltimatePhase.HIT2 && s.lastTargetPos != null) {
            // hedef dunyadan kalkti (olum degil): oyuncuysa cikti -> iptal
            if (level.getServer().getPlayerList().getPlayer(s.targetUuid) == null && !s.mob) {
                abort(level.getServer(), s);
                return;
            }
        }

        // caster kilidi
        if (t >= UltimatePhase.BLINK && !s.blinked) {
            s.blinked = true;
            teleport(caster, s.lockPos, s.lockYaw);
        }
        if (t >= UltimatePhase.RETURN_TP && !s.returned) {
            s.returned = true;
            Vec3 r = s.arena.toWorld(-0.15D, 0D, s.arena.d + s.push - 1.1D);
            Vec3 stand = findStand(caster, r, false);
            s.lockPos = stand != null ? stand : s.lockPos;
            teleport(caster, s.lockPos, s.lockYaw);
        }
        if (t >= UltimatePhase.CASTER_LAND && !s.landed) { // havadaki yumruktan sonra hedefin arkasina iner
            s.landed = true;
            Vec3 l = s.freezePos.add(UltimateScript.casterOffset(s.arena.fx, s.arena.fz, s.push, UltimatePhase.CASTER_LAND));
            Vec3 stand = findStand(caster, l, false);
            if (stand != null) {
                s.lockPos = stand;
                teleport(caster, s.lockPos, s.arena.forwardYaw() + 180F);
            }
        }
        if (s.blinked) {
            caster.setDeltaMovement(Vec3.ZERO);
            caster.fallDistance = 0F;
            if (caster.position().distanceToSqr(s.lockPos) > 0.25D * 0.25D) teleport(caster, s.lockPos, caster.getYRot());
        }

        // stasis
        if (s.stasis && target != null) {
            target.setDeltaMovement(Vec3.ZERO);
            // betik: ilk vurusta ileri kayma, ikinci vurustan sonra havaya kalkis, asili kalma, cakilma
            // (istemci ayni egrileri yumusak cizer)
            Vec3 want = s.freezePos.add(UltimateScript.targetOffset(s.arena.fx, s.arena.fz, s.push, t));
            if (target.position().distanceToSqr(want) > 0.05D * 0.05D) {
                if (target instanceof ServerPlayer sp) sp.connection.teleport(want.x, want.y, want.z, sp.getYRot(), sp.getXRot());
                else target.setPos(want.x, want.y, want.z);
            }
            target.hurtMarked = true;
            target.fallDistance = 0F;
            if ((gt & 3) == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getBbHeight() * 0.6D,
                        target.getZ(), 2, target.getBbWidth() * 0.5D, target.getBbHeight() * 0.4D, target.getBbWidth() * 0.5D, 0.02D);
            }
        }

        // hasar
        if (t >= UltimatePhase.HIT1 && !s.hit1Done) {
            s.hit1Done = true;
            if (target != null) {
                target.invulnerableTime = 0;
                target.hurt(UltimateDamage.source(level, caster), FlashServerConfig.ULT_HIT1.get().floatValue());
            }
        }
        if (t >= UltimatePhase.HIT2 && !s.hit2Done) {
            s.hit2Done = true;
            if (target != null) {
                target.invulnerableTime = 0;
                target.hurt(UltimateDamage.source(level, caster), UltimateDamage.hit2(target));
            }
        }
        if (t >= UltimatePhase.HIT3 && !s.hit3Done) { // havadaki yumruk
            s.hit3Done = true;
            if (target != null) {
                target.invulnerableTime = 0;
                target.hurt(UltimateDamage.source(level, caster), FlashServerConfig.ULT_HIT3.get().floatValue());
            }
        }
        if (t >= UltimatePhase.SLAM_T && !s.launched) { // yere carpma: patlama, stasis biter
            s.launched = true;
            if (target != null) {
                endStasis(s, target);
                if (target.isAlive()) crash(level, caster, s, target, true);
            }
        }

        // konumlu sesler
        for (int i = 0; i < SOUNDS.length; i++) {
            if ((s.soundMask & (1 << i)) != 0 || t < (Integer) SOUNDS[i][0]) continue;
            s.soundMask |= 1 << i;
            SoundEvent ev = FlashSounds.ult((String) SOUNDS[i][1]);
            int where = (Integer) SOUNDS[i][2];
            Vec3 at = where == 0 ? caster.position() : where == 1 ? (target != null ? target.position() : s.lastTargetPos)
                    : s.arena.toWorld(0D, 1.35D, s.arena.d + s.push - 0.25D);
            if (ev != null && at != null) level.playSound(null, at.x, at.y, at.z, ev, SoundSource.PLAYERS, 1.6F, 1.0F);
        }

        if (s.debrisAt >= 0 && gt >= s.debrisAt) {
            s.debrisAt = -1;
            SoundEvent ev = FlashSounds.ult("ult_debris");
            if (ev != null) level.playSound(null, s.debrisPos.x, s.debrisPos.y, s.debrisPos.z, ev, SoundSource.PLAYERS, 1.2F, 1F);
        }

        if (t >= UltimatePhase.DURATION) finish(level, caster, s, 1.0D);
    }

    private static void teleport(ServerPlayer p, Vec3 pos, float yaw) {
        p.connection.teleport(pos.x, pos.y, pos.z, yaw, 0F);
        p.setDeltaMovement(Vec3.ZERO);
        p.setOnGround(true);
        p.fallDistance = 0F;
    }

    private static void crash(ServerLevel level, ServerPlayer caster, UltimateSession s, LivingEntity target, boolean slam) {
        s.crashed = true;
        Vec3 p = target.position().add(0, target.getBbHeight() * 0.5D, 0);
        SoundEvent ev = FlashSounds.ult("ult_crash");
        if (ev != null) level.playSound(null, p.x, p.y, p.z, ev, SoundSource.PLAYERS, slam ? 1.1F : 1.6F, 0.9F);
        s.debrisAt = level.getGameTime() + 3;
        s.debrisPos = p;
        BlockPos ahead = BlockPos.containing(p.x + s.arena.fx * (target.getBbWidth() * 0.5D + 0.4D), p.y,
                p.z + s.arena.fz * (target.getBbWidth() * 0.5D + 0.4D));
        if (slam) ahead = target.blockPosition().below();
        BlockState st = level.getBlockState(ahead);
        if (st.isAir()) st = level.getBlockState(target.blockPosition().below());
        if (!st.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), p.x, p.y, p.z, 40, 0.5D, 0.5D, 0.5D, 0.15D);
        }
        if (FlashServerConfig.ULT_CRASH_BREAKS.get() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            float maxH = FlashServerConfig.ULT_CRASH_HARDNESS.get().floatValue();
            for (BlockPos bp : BlockPos.betweenClosed(ahead.offset(-1, -1, -1), ahead.offset(1, 1, 1))) {
                BlockState b = level.getBlockState(bp);
                float h = b.getDestroySpeed(level, bp);
                if (b.isAir() || h < 0F || h > maxH || b.is(UltimateDamage.UNBREAKABLE)) continue;
                level.destroyBlock(bp, true, caster);
            }
        }
        target.invulnerableTime = 0;
        target.hurt(UltimateDamage.source(level, caster), FlashServerConfig.ULT_CRASH_DAMAGE.get().floatValue());
        float nx = slam ? 0F : (float) -s.arena.fx, ny = slam ? 1F : 0F, nz = slam ? 0F : (float) -s.arena.fz;
        broadcast(level.getServer(), s, new UltimateEventPacket(slam ? UltimateEventPacket.SLAM : UltimateEventPacket.CRASH,
                s.id, p.x, p.y, p.z, nx, ny, nz));
    }

    // ---------------------------------------------------------------- stasis

    private static void startStasis(UltimateSession s, LivingEntity target) {
        s.stasis = true;
        s.freezePos = target.position();
        if (target instanceof Mob mob) {
            s.mob = true;
            s.hadNoAi = mob.isNoAi();
            mob.getPersistentData().putBoolean(NOAI_KEY, s.hadNoAi);
            mob.setNoAi(true);
            mob.getNavigation().stop();
        }
        if (target instanceof ServerPlayer sp) {
            FlashNetwork.sendTo(sp, UltimateEventPacket.simple(UltimateEventPacket.STASIS_ON, s.id));
        }
    }

    private static void endStasis(UltimateSession s, @Nullable Entity target) {
        if (!s.stasis) return;
        s.stasis = false;
        if (target instanceof Mob mob) {
            mob.setNoAi(s.hadNoAi);
            mob.getPersistentData().remove(NOAI_KEY);
        }
        if (target instanceof ServerPlayer sp) {
            FlashNetwork.sendTo(sp, UltimateEventPacket.simple(UltimateEventPacket.STASIS_OFF, s.id));
        }
    }

    /** Dunya yuklenince eski ultimate'ten kalmis noAi'yi geri yukle (sunucu cokmus/kaydedilmis olabilir). */
    public static void restorePersisted(Entity e) {
        if (!(e instanceof Mob mob) || involved(e)) return;
        CompoundTag pd = mob.getPersistentData();
        if (!pd.contains(NOAI_KEY)) return;
        mob.setNoAi(pd.getBoolean(NOAI_KEY));
        pd.remove(NOAI_KEY);
    }

    // ---------------------------------------------------------------- bitis / iptal

    private static void finish(ServerLevel level, ServerPlayer caster, UltimateSession s, double cdFraction) {
        if (s.ended) return;
        s.ended = true;
        Entity te = level.getEntity(s.targetId);
        endStasis(s, te);
        BY_CASTER.remove(s.casterId);
        if (!s.debug) {
            long cd = (long) (FlashServerConfig.ULT_COOLDOWN.get() * 20L * cdFraction);
            SpeedsterData.setUltCooldown(caster, level.getGameTime() + cd);
        }
    }

    public static void abort(MinecraftServer server, UltimateSession s) {
        if (s.ended) return;
        ServerLevel level = server.getLevel(s.dimension);
        ServerPlayer caster = server.getPlayerList().getPlayer(s.casterId);
        broadcast(server, s, UltimateEventPacket.simple(UltimateEventPacket.ABORT, s.id));
        if (level != null && caster != null) {
            finish(level, caster, s, FlashServerConfig.ULT_ABORT_COOLDOWN.get());
        } else {
            s.ended = true;
            if (level != null) endStasis(s, level.getEntity(s.targetId));
            BY_CASTER.remove(s.casterId);
        }
    }

    /** Bu oyuncunun karistigi (caster ya da hedef) oturumlari iptal et. */
    public static void abortInvolving(MinecraftServer server, Entity e) {
        for (UltimateSession s : new ArrayList<>(BY_CASTER.values())) {
            if (s.casterId.equals(e.getUUID()) || s.targetUuid.equals(e.getUUID())) abort(server, s);
        }
    }

    public static void abortAll(MinecraftServer server) {
        for (UltimateSession s : new ArrayList<>(BY_CASTER.values())) abort(server, s);
    }

    private static void broadcast(MinecraftServer server, UltimateSession s, Object msg) {
        for (UUID id : s.audience) {
            ServerPlayer pl = server.getPlayerList().getPlayer(id);
            if (pl != null) FlashNetwork.sendTo(pl, msg);
        }
    }
}
