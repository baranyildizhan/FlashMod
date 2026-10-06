package dev.baranhan.flashmod.speed;

import dev.baranhan.flashmod.config.FlashServerConfig;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hizla kosan hizcinin onundeki chunk'lari onceden yukler/uretir. Gidis yonunde, hiza gore birkac saniyelik
 * mesafe boyunca 3 chunk genisliginde bir seride sureli chunk bileti (ticket) acilir; biletler her tick
 * tazelenir, hizci yon degistirince ya da durunca kendiliginden duser (TTL). Boylece oyuncu oraya vardiginda
 * chunk hazir olur, sunucu sadece gondermekle ugrasir (uretim gecikmesi takilmaya yol acmaz).
 */
public final class ChunkPreloader {
    /** Sureli bilet: 80 tick tazelenmezse duser. */
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("flashmod_run_ahead", Comparator.comparingLong(ChunkPos::toLong), 80);
    /** {x, z, vx, vz} - son konum ve yumusatilmis hiz. */
    private static final Map<UUID, double[]> STATE = new HashMap<>();
    private static final double MIN_SPEED = 0.8D; // blok/tick (~58 km/s): normal kosuda gerek yok

    private ChunkPreloader() {}

    public static void tick(ServerPlayer p) {
        if (!FlashServerConfig.CHUNK_PRELOAD.get() || !SpeedsterData.isActive(p) || p.isSpectator()) {
            STATE.remove(p.getUUID());
            return;
        }
        double[] st = STATE.get(p.getUUID());
        if (st == null) {
            STATE.put(p.getUUID(), new double[]{p.getX(), p.getZ(), 0, 0});
            return;
        }
        double dx = p.getX() - st[0], dz = p.getZ() - st[1];
        st[0] = p.getX();
        st[1] = p.getZ();
        if (dx * dx + dz * dz > 64.0D * 64.0D) { dx = 0; dz = 0; } // isinlanma
        st[2] += (dx - st[2]) * 0.4D;
        st[3] += (dz - st[3]) * 0.4D;
        double sp = Math.sqrt(st[2] * st[2] + st[3] * st[3]);
        if (sp < MIN_SPEED) return;

        double ux = st[2] / sp, uz = st[3] / sp;
        double ahead = Math.min(sp * 20.0D * FlashServerConfig.CHUNK_PRELOAD_SECONDS.get(),
                FlashServerConfig.CHUNK_PRELOAD_MAX_BLOCKS.get());
        ServerChunkCache cache = p.serverLevel().getChunkSource();
        long last = Long.MIN_VALUE;
        for (double d = 8.0D; d <= ahead; d += 12.0D) {
            ChunkPos cp = new ChunkPos(((int) Math.floor(p.getX() + ux * d)) >> 4, ((int) Math.floor(p.getZ() + uz * d)) >> 4);
            long key = cp.toLong();
            if (key == last) continue;
            last = key;
            cache.addRegionTicket(TICKET, cp, 1, cp); // 3x3 chunk tamamen yuklu
        }
    }

    public static void forget(UUID id) {
        STATE.remove(id);
    }
}
