package dev.baranhan.flashmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Sunucu -> izleyen istemciler: bir oyuncunun tornado durumu (gorseller icin). */
public final class TornadoSyncPacket {
    public final UUID player;
    public final boolean active;
    public final double cx, cy, cz;
    public final float speed;
    public final int dir;

    public TornadoSyncPacket(UUID player, boolean active, double cx, double cy, double cz, float speed, int dir) {
        this.player = player;
        this.active = active;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.speed = speed;
        this.dir = dir >= 0 ? 1 : -1;
    }

    public static void encode(TornadoSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.player);
        buf.writeBoolean(msg.active);
        buf.writeDouble(msg.cx);
        buf.writeDouble(msg.cy);
        buf.writeDouble(msg.cz);
        buf.writeFloat(msg.speed);
        buf.writeByte(msg.dir);
    }

    public static TornadoSyncPacket decode(FriendlyByteBuf buf) {
        return new TornadoSyncPacket(buf.readUUID(), buf.readBoolean(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readFloat(), buf.readByte());
    }

    public static void handle(TornadoSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.baranhan.flashmod.client.Tornado.handleSync(msg));
    }
}
