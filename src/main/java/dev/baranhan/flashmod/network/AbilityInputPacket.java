package dev.baranhan.flashmod.network;

import dev.baranhan.flashmod.speed.AbilityLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Istemci -> sunucu: yetenek tusu basildi/birakildi (THROW, SLOWMO). */
public final class AbilityInputPacket {
    private final byte ability;
    private final boolean held;

    public AbilityInputPacket(int ability, boolean held) {
        this.ability = (byte) ability;
        this.held = held;
    }

    public static void encode(AbilityInputPacket m, FriendlyByteBuf buf) {
        buf.writeByte(m.ability);
        buf.writeBoolean(m.held);
    }

    public static AbilityInputPacket decode(FriendlyByteBuf buf) {
        return new AbilityInputPacket(buf.readByte(), buf.readBoolean());
    }

    public static void handle(AbilityInputPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (p != null) AbilityLogic.input(p, m.ability, m.held);
    }
}
