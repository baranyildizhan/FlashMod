package dev.baranhan.flashmod.network;

import dev.baranhan.flashmod.speed.PhaseHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Phasing tusu basildi/birakildi (sadece degisimde gonderilir). */
public final class PhaseInputPacket {
    private final boolean held;

    public PhaseInputPacket(boolean held) {
        this.held = held;
    }

    public static void encode(PhaseInputPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.held);
    }

    public static PhaseInputPacket decode(FriendlyByteBuf buf) {
        return new PhaseInputPacket(buf.readBoolean());
    }

    public static void handle(PhaseInputPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (player != null) PhaseHelper.setHeld(player, msg.held);
    }
}
