package dev.baranhan.flashmod.network;

import dev.baranhan.flashmod.ultimate.UltimateManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Istemci -> sunucu: ultimate istegi (karar sunucunun). */
public final class UltimateRequestPacket {
    public static void encode(UltimateRequestPacket m, FriendlyByteBuf buf) {}

    public static UltimateRequestPacket decode(FriendlyByteBuf buf) {
        return new UltimateRequestPacket();
    }

    public static void handle(UltimateRequestPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (p != null) UltimateManager.tryStart(p, null, false);
    }
}
