package dev.baranhan.flashmod.network;

import dev.baranhan.flashmod.speed.BlitzLogic;
import dev.baranhan.flashmod.speed.AbilityLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Istemci -> sunucu: baktigim hedefe Blitz baslat. */
public final class BlitzStartPacket {
    private final int targetId;

    public BlitzStartPacket(int targetId) {
        this.targetId = targetId;
    }

    public static void encode(BlitzStartPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.targetId);
    }

    public static BlitzStartPacket decode(FriendlyByteBuf buf) {
        return new BlitzStartPacket(buf.readVarInt());
    }

    public static void handle(BlitzStartPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (player != null && !AbilityLogic.isWallRunning(player)
                && !dev.baranhan.flashmod.ultimate.UltimateManager.involved(player)) BlitzLogic.tryStart(player, msg.targetId); // duvarda yok
    }
}
