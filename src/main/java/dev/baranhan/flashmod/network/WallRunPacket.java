package dev.baranhan.flashmod.network;

import dev.baranhan.flashmod.speed.AbilityLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Istemci -> sunucu: duvarda kosma basladi/bitti ve duvarin normali (duvardan disari, yatay). */
public final class WallRunPacket {
    private final boolean on;
    private final float nx, nz;

    public WallRunPacket(boolean on, float nx, float nz) {
        this.on = on;
        this.nx = nx;
        this.nz = nz;
    }

    public static void encode(WallRunPacket m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.on);
        buf.writeFloat(m.nx);
        buf.writeFloat(m.nz);
    }

    public static WallRunPacket decode(FriendlyByteBuf buf) {
        return new WallRunPacket(buf.readBoolean(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(WallRunPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (p != null) AbilityLogic.wallRun(p, m.on, m.nx, m.nz);
    }
}
