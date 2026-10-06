package dev.baranhan.flashmod.speed;

import dev.baranhan.flashmod.config.FlashServerConfig;
import dev.baranhan.flashmod.network.FlashNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phasing durumu. Ortak (common) sinif: mixin hem istemcide hem sunucuda buradan okur.
 * Kural: tus basiliyken phasing; tus birakilsa bile oyuncu bir blogun icindeyse disari cikana kadar devam eder.
 *
 * Zemin kilidi: phasing sirasinda son "gercek" zemin yuksekligi saklanir. Oyuncu bir blogun icindeyken
 * bu seviyenin altina inemez -> duvarin icinden ayni zemin seviyesinde gecer, yere batmaz.
 */
public final class PhaseHelper {
    private static final Set<UUID> SERVER_PHASING = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SERVER_HELD = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Double> SERVER_FLOOR = new ConcurrentHashMap<>();
    /** Sadece yerel oyuncu icin, istemci kodu tarafindan yazilir. */
    public static volatile boolean clientLocalPhasing;
    private static volatile double clientFloor = Double.NaN;

    private PhaseHelper() {}

    public static boolean isPhasing(Player p) {
        if (p.level().isClientSide) return clientLocalPhasing && p.isLocalPlayer();
        return SERVER_PHASING.contains(p.getUUID());
    }

    /** Hitbox (cok az kucultulmus) herhangi bir carpisma sekliyle kesisiyor mu? */
    public static boolean insideSolid(Player p) {
        return !p.level().noCollision(p, p.getBoundingBox().deflate(1.0E-3D));
    }

    public static double floor(Player p) {
        if (p.level().isClientSide) return clientFloor;
        return SERVER_FLOOR.getOrDefault(p.getUUID(), Double.NaN);
    }

    public static void setFloor(Player p, double y) {
        if (p.level().isClientSide) clientFloor = y;
        else SERVER_FLOOR.put(p.getUUID(), y);
    }

    public static void clearClientFloor() {
        clientFloor = Double.NaN;
    }

    public static void setHeld(ServerPlayer p, boolean held) {
        if (held) SERVER_HELD.add(p.getUUID());
        else SERVER_HELD.remove(p.getUUID());
    }

    static void serverTick(ServerPlayer p, boolean active) {
        UUID id = p.getUUID();
        boolean was = SERVER_PHASING.contains(id);
        boolean now = active && FlashServerConfig.PHASING.get() && !p.isPassenger()
                && (SERVER_HELD.contains(id) || (was && insideSolid(p)));
        if (now == was) return;
        if (now) {
            SERVER_PHASING.add(id);
            if (p.onGround()) SERVER_FLOOR.put(id, p.getY());
        } else {
            SERVER_PHASING.remove(id);
            SERVER_FLOOR.remove(id);
        }
        FlashNetwork.syncToTrackingAndSelf(p);
    }

    public static void forget(UUID id) {
        SERVER_PHASING.remove(id);
        SERVER_HELD.remove(id);
        SERVER_FLOOR.remove(id);
    }
}
