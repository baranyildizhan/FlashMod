package dev.baranhan.flashmod.network;

import dev.baranhan.flashmod.speed.TornadoLogic;
import dev.baranhan.flashmod.speed.AbilityLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Istemci -> sunucu: tornado durumu (hareket istemci-otoriter, sunucu dogrular ve cevreye uygular). */
public final class TornadoStatePacket {
    final boolean active;
    final double cx, cy, cz;
    final float speed;
    final byte dir;

    public TornadoStatePacket(boolean active, double cx, double cy, double cz, float speed, int dir) {
        this.active = active;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.speed = speed;
        this.dir = (byte) (dir >= 0 ? 1 : -1);
    }

    public static void encode(TornadoStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
        buf.writeDouble(msg.cx);
        buf.writeDouble(msg.cy);
        buf.writeDouble(msg.cz);
        buf.writeFloat(msg.speed);
        buf.writeByte(msg.dir);
    }

    public static TornadoStatePacket decode(FriendlyByteBuf buf) {
        return new TornadoStatePacket(buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readByte());
    }

    public static void handle(TornadoStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (player == null) return;
        boolean on = msg.active && !AbilityLogic.isWallRunning(player) // duvarda tornado yok
                && !dev.baranhan.flashmod.ultimate.UltimateManager.involved(player);
        TornadoLogic.setState(player, on, msg.cx, msg.cy, msg.cz, msg.speed, msg.dir);
    }
}
