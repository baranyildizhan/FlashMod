package dev.baranhan.flashmod.speed;

import dev.baranhan.flashmod.config.FlashServerConfig;
import dev.baranhan.flashmod.network.FlashNetwork;
import dev.baranhan.flashmod.network.TimeRatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * Gercek zaman yavaslatma (sunucu geneli). Bir hizci agir cekimi acinca sunucunun tick hizi dusurulur
 * (MinecraftServerMixin: 50 ms tick araligi -> 50 / rate) ve oran tum istemcilere gonderilir; istemciler de
 * kendi zamanlayicilarini ayni orana ceker (TimerMixin). Boylece moblar, animasyonlar, parcaciklar, mermiler,
 * dusen bloklar, gun dongusu... her sey ayni oranda ve akici yavaslar; herkes ayni seyi gorur.
 * Hizci dahil herkes ayni oranda yavaslar.
 */
public final class TimeControl {
    /** Sunucunun su an uyguladigi oran (1 = normal). Sunucu thread'i yazar, mixin okur. */
    private static volatile float rate = 1F;
    private static float sentRate = 1F;
    private static long lastNanos;

    private TimeControl() {}

    public static float rate() {
        return rate;
    }

    /** MinecraftServerMixin: tick araligi (ms). */
    public static long tickMillis(long vanilla) {
        float r = rate;
        if (r >= 0.999F) return vanilla;
        return Math.max(vanilla, Math.round(vanilla / (double) r));
    }

    /** Her sunucu tick'inin basinda: hedef orana gercek zamanda yumusakca yaklas, degisirse herkese bildir. */
    public static void serverTick(MinecraftServer server) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.05F : Math.min((now - lastNanos) / 1.0E9F, 0.5F);
        lastNanos = now;
        float target = AbilityLogic.anySlowActive() ? FlashServerConfig.SLOWMO_RATE.get().floatValue() : 1F;
        float r = rate;
        if (BlitzLogic.anyActive() || dev.baranhan.flashmod.ultimate.UltimateManager.anyActive()) {
            r = 1F; // Blitz/ultimate sinematigi tick zamanlamali: hep normal hizda oynar
        } else {
            r += (target - r) * (1F - (float) Math.exp(-dt * 5.0F));
            if (Math.abs(target - r) < 0.004F) r = target;
        }
        rate = r;
        if (Math.abs(r - sentRate) > 0.002F || (r == target && r != sentRate)) {
            sentRate = r;
            FlashNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new TimeRatePacket(r));
        }
    }

    public static void onLogin(ServerPlayer p) {
        FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new TimeRatePacket(rate));
    }

    public static void reset() {
        rate = 1F;
        sentRate = 1F;
        lastNanos = 0;
    }
}
