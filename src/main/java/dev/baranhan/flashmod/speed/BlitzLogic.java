package dev.baranhan.flashmod.speed;

import dev.baranhan.flashmod.network.BlitzSyncPacket;
import dev.baranhan.flashmod.network.FlashNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sunucu tarafi Blitz. Hedef (bir Mob) sinematik boyunca yerinde donar (NoAI); tum hareket istemcilerde
 * senaryodan cizilir. Sunucu sadece: darbe anlarinda hasar (son darbeye kadar oldurmez -> ceset havada
 * kalmaz), sonda hedefi ve hizciyi senaryodaki son yerlerine isinlar ve son hasari orada uygular.
 */
public final class BlitzLogic {
    private static final double MAX_RANGE = 40.0D;
    private static final int COOLDOWN_TICKS = 40;

    private static final class Session {
        int targetId;
        long start;
        BlitzPath.Frame frame;
        int hitIdx;
        boolean prevNoAi;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    /** Blitz'in tekrar kullanilabilecegi oyun zamani (HUD senkronu). */
    public static long cooldownUntil(Player p) {
        Long cd = COOLDOWN.get(p.getUUID());
        return cd == null ? 0L : cd;
    }

    public static int cooldownTotal() {
        return COOLDOWN_TICKS;
    }

    private BlitzLogic() {}

    /** Herhangi bir Blitz sinematigi oynuyor mu (zaman yavaslatma o sirada normale doner). */
    public static boolean anyActive() {
        return !SESSIONS.isEmpty();
    }

    public static boolean isActive(Player p) {
        return SESSIONS.containsKey(p.getUUID());
    }

    public static void tryStart(ServerPlayer p, int targetId) {
        if (!SpeedsterData.isActive(p) || p.isSpectator() || SESSIONS.containsKey(p.getUUID())
                || TornadoLogic.isActive(p) || p.isPassenger()) return;
        long now = p.level().getGameTime();
        Long cd = COOLDOWN.get(p.getUUID());
        if (cd != null && now < cd) {
            p.displayClientMessage(Component.translatable("msg.flashmod.blitz_cooldown"), true);
            return;
        }
        Entity e = p.level().getEntity(targetId);
        if (!(e instanceof Mob mob) || !mob.isAlive() || mob.distanceToSqr(p) > MAX_RANGE * MAX_RANGE
                || !p.hasLineOfSight(mob)) return;
        for (Session other : SESSIONS.values()) if (other.targetId == targetId) return; // ayni hedefe iki blitz yok

        double dx = mob.getX() - p.getX(), dz = mob.getZ() - p.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3D) {
            Vec3 look = p.getLookAngle();
            dx = look.x;
            dz = look.z;
            len = Math.max(1.0E-3D, Math.sqrt(dx * dx + dz * dz));
        }
        Session s = new Session();
        s.targetId = targetId;
        s.start = now;
        s.frame = new BlitzPath.Frame(mob.getX(), mob.getY(), mob.getZ(), (float) (dx / len), (float) (dz / len),
                p.getX(), p.getY(), p.getZ(), now);
        s.prevNoAi = mob.isNoAi();
        mob.setNoAi(true);
        mob.setDeltaMovement(Vec3.ZERO);
        SESSIONS.put(p.getUUID(), s);
        p.setDeltaMovement(Vec3.ZERO);
        broadcast(p, s, true);
    }

    public static void serverTick(ServerPlayer p) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) return;
        if (!p.isAlive() || !SpeedsterData.isActive(p)) {
            end(p, s, false);
            return;
        }
        long t = p.level().getGameTime() - s.start;
        Entity e = p.level().getEntity(s.targetId);
        Mob mob = e instanceof Mob m && m.isAlive() ? m : null;
        if (mob != null) mob.setDeltaMovement(Vec3.ZERO);

        while (s.hitIdx < BlitzScript.HITS.length && BlitzScript.HITS[s.hitIdx][0] <= t) {
            float[] h = BlitzScript.HITS[s.hitIdx++];
            if (mob == null) continue;
            float dmg = (int) h[1] == BlitzScript.FINISH ? 1F : h[2] * scale(p);
            dmg = Math.min(dmg, Math.max(0F, mob.getHealth() - 1F)); // sona kadar oldurme
            mob.invulnerableTime = 0;
            if (dmg > 0F) mob.hurt(p.damageSources().playerAttack(p), dmg);
            mob.setDeltaMovement(Vec3.ZERO); // hurt()'un geri itmesini iptal (hareket istemcide senaryodan)
        }

        p.setDeltaMovement(Vec3.ZERO);
        p.resetFallDistance();
        if (t >= BlitzScript.DURATION) end(p, s, true);
    }

    private static float scale(ServerPlayer p) {
        return 0.35F + 0.065F * SpeedsterData.getLevel(p); // seviye 10 ~ 1x
    }

    private static void end(ServerPlayer p, Session s, boolean normal) {
        SESSIONS.remove(p.getUUID());
        COOLDOWN.put(p.getUUID(), p.level().getGameTime() + COOLDOWN_TICKS);
        Entity e = p.level().getEntity(s.targetId);
        Mob mob = e instanceof Mob m ? m : null;
        if (mob != null) {
            mob.setNoAi(s.prevNoAi);
            mob.setDeltaMovement(Vec3.ZERO);
        }
        if (normal) {
            double[] tw = new double[6];
            BlitzPath.targetWorld(s.frame, BlitzScript.DURATION, tw);
            if (mob != null && mob.isAlive()) {
                Vec3 d = new Vec3(tw[0] - mob.getX(), tw[1] - mob.getY(), tw[2] - mob.getZ());
                if (mob.level().noCollision(mob, mob.getBoundingBox().move(d))) {
                    mob.teleportTo(tw[0], tw[1], tw[2]);
                }
                mob.setYRot((float) tw[3]);
                mob.setYBodyRot((float) tw[3]);
                mob.invulnerableTime = 0;
                mob.hurt(p.damageSources().playerAttack(p), BlitzScript.HITS[BlitzScript.HITS.length - 1][2] * scale(p));
                mob.setDeltaMovement(Vec3.ZERO);
            }
            double[] sw = new double[3];
            BlitzPath.speedsterWorld(s.frame, BlitzScript.DURATION, sw);
            Vec3 d = new Vec3(sw[0] - p.getX(), sw[1] - p.getY(), sw[2] - p.getZ());
            if (p.level().noCollision(p, p.getBoundingBox().move(d))) {
                // Kamera oyuncuya donerken yere serilen rakibe baksin
                float yaw = (float) Math.toDegrees(Math.atan2(-(tw[0] - sw[0]), tw[2] - sw[2]));
                p.connection.teleport(sw[0], sw[1], sw[2], yaw, 12F);
            }
        }
        broadcast(p, s, false);
    }

    private static void broadcast(ServerPlayer p, Session s, boolean active) {
        BlitzPath.Frame f = s.frame;
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new BlitzSyncPacket(p.getUUID(), active, s.targetId, s.start, f.ax(), f.ay(), f.az(), f.fx(), f.fz(),
                        f.px(), f.py(), f.pz()));
    }

    public static void forget(ServerPlayer p) {
        Session s = SESSIONS.get(p.getUUID());
        if (s != null) end(p, s, false);
        COOLDOWN.remove(p.getUUID());
    }
}
