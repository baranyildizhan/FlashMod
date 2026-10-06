package dev.baranhan.flashmod.speed;

import dev.baranhan.flashmod.network.FlashNetwork;
import dev.baranhan.flashmod.network.TornadoSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sunucu tarafi tornado: istemcinin bildirdigi durumu dogrular, izleyenlere yayar ve cevredeki
 * entity'leri girdaba ceker (teget donus + merkeze cekim + yukari kaldirma).
 */
public final class TornadoLogic {
    public static final float RADIUS = 3F;
    public static final float MACH1 = 17.15F;
    private static final double PULL_RADIUS = 12.0D;
    /** Yakalanan entity'lerin dondugu yorunge (kosucu halkasinin icinde). */
    private static final double ORBIT_R = 1.8D;

    private static final class State {
        double cx, cy, cz;
        float speed;
        int dir;
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private TornadoLogic() {}

    public static boolean isActive(Player p) {
        return STATES.containsKey(p.getUUID());
    }

    public static void setState(ServerPlayer p, boolean active, double cx, double cy, double cz, float speed, int dir) {
        UUID id = p.getUUID();
        boolean valid = active && SpeedsterData.isActive(p) && !p.isSpectator()
                && Math.abs(cy - p.getY()) < 3.0D
                && Mth.square(cx - p.getX()) + Mth.square(cz - p.getZ()) < Mth.square(RADIUS + 2.5D)
                && Float.isFinite(speed);
        if (!valid) {
            if (STATES.remove(id) != null) broadcast(p, null);
            return;
        }
        State s = STATES.computeIfAbsent(id, k -> new State());
        s.cx = cx;
        s.cy = cy;
        s.cz = cz;
        s.speed = Mth.clamp(speed, 0F, MACH1);
        s.dir = dir >= 0 ? 1 : -1;
        broadcast(p, s);
    }

    private static void broadcast(ServerPlayer p, State s) {
        TornadoSyncPacket msg = s == null
                ? new TornadoSyncPacket(p.getUUID(), false, 0, 0, 0, 0, 1)
                : new TornadoSyncPacket(p.getUUID(), true, s.cx, s.cy, s.cz, s.speed, s.dir);
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> p), msg);
    }

    /** Yeni izlemeye baslayan oyuncuya mevcut tornadoyu gonder. */
    public static void sendTo(ServerPlayer watcher, Player target) {
        State s = STATES.get(target.getUUID());
        if (s != null) {
            FlashNetwork.sendTo(watcher, new TornadoSyncPacket(target.getUUID(), true, s.cx, s.cy, s.cz, s.speed, s.dir));
        }
    }

    public static void serverTick(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s == null) return;
        if (!SpeedsterData.isActive(p) || p.isSpectator()) {
            STATES.remove(p.getUUID());
            broadcast(p, null);
            return;
        }
        float k = s.speed / MACH1;
        if (k < 0.05F) return;

        AABB area = new AABB(s.cx - PULL_RADIUS, s.cy - 3.0D, s.cz - PULL_RADIUS,
                s.cx + PULL_RADIUS, s.cy + 16.0D, s.cz + PULL_RADIUS);
        List<Entity> list = p.level().getEntities(p, area, e -> {
            if (e instanceof Player pl) return !pl.isCreative() && !pl.isSpectator();
            return e.isPushable() || e instanceof ItemEntity || e instanceof ExperienceOrb;
        });
        float damage = 1.0F + 5.0F * k;
        for (Entity e : list) {
            if (e.isPassenger()) continue;
            double dx = s.cx - e.getX(), dz = s.cz - e.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > PULL_RADIUS) continue;
            double nx, nz;
            if (d < 1.0E-3D) { nx = 1.0D; nz = 0.0D; } else { nx = dx / d; nz = dz / d; } // merkeze dogru
            double tx = -nz * s.dir, tz = nx * s.dir;                                        // donus yonu

            // 0..1: merkeze yaklastikca ve hiz arttikca artan kavrama
            double f = k * Mth.clamp(1.0D - (d - RADIUS) / (PULL_RADIUS - RADIUS), 0.0D, 1.0D);
            if (f <= 0.01D) continue;

            // Hedef hiz: ORBIT_R yaricapli yorungede donerek yukselmek. Mevcut hizi hedefe dogru "kavrayarak" cek
            // (eklemek yerine) -> yer surtunmesi ve mob yapay zekasi kacmayi engelleyemez.
            double swirl = (0.45D + 1.25D * k) * Mth.clamp(d / ORBIT_R, 0.3D, 1.3D);
            double radial = Mth.clamp((d - ORBIT_R) * 0.35D, -0.35D, 1.3D) * (0.7D + 0.6D * k);
            double edge = Mth.clamp((d - ORBIT_R) / 8.0D, 0.0D, 1.0D);
            double targetY = s.cy + 1.2D + (2.0D + 7.0D * k) * (1.0D - edge);
            double vy = Mth.clamp((targetY - e.getY()) * 0.22D, -0.35D, 0.65D) + 0.08D; // +yercekimi telafisi

            Vec3 desired = new Vec3(nx * radial + tx * swirl, vy, nz * radial + tz * swirl);
            double grip = Mth.clamp(0.12D + 0.8D * f, 0.0D, 0.92D);
            Vec3 cur = e.getDeltaMovement();
            Vec3 v = cur.add(desired.subtract(cur).scale(grip));

            // Kosucunun yoluna (cembere) veya gozune yakin olan canlilar hasar alir.
            // hurt() geri itme uygular; hizi hurt'ten SONRA yazdigimiz icin girdaptan kacamazlar.
            if (k > 0.2F && d < RADIUS + 1.6D && e instanceof LivingEntity living && living.isAlive()) {
                living.hurt(p.damageSources().playerAttack(p), damage);
            }

            e.setDeltaMovement(v);
            e.setOnGround(false);
            e.hurtMarked = true;
            e.resetFallDistance(); // birakilinca bulunduklari yukseklikten duserler
        }
    }

    public static void forget(UUID id) {
        STATES.remove(id);
    }
}
