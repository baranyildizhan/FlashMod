package dev.baranhan.flashmod.client;

import dev.baranhan.flashmod.network.SyncSpeedsterPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void handleSync(SyncSpeedsterPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ClientSpeedsters.Entry e = ClientSpeedsters.getOrCreate(msg.player);
        boolean first = !e.synced;
        boolean was = e.active;
        boolean self = mc.player != null && mc.player.getUUID().equals(msg.player);

        e.active = msg.active;
        e.level = msg.level;
        e.core = msg.core;
        e.glow = msg.glow;
        // Yerel oyuncunun phasing'i istemcide hesaplanir (gecikmesiz); sunucu degeri sadece digerleri icin.
        if (!self) e.phasing = msg.phasing;
        e.synced = true;

        // Ilk senkron (giris / gorus alanina girme) -> efekt patlatma, sadece durumu al.
        if (first) return;

        Player p = mc.level.getPlayerByUUID(msg.player);
        long now = mc.level.getGameTime();
        if (!was && e.active) {
            e.activatedAt = now;
            if (p != null) SpeedFx.activationBurst(p, e);
        } else if (was && !e.active) {
            e.deactivatedAt = now;
            if (p != null) SpeedFx.fizzle(p, e);
        }
    }
}
