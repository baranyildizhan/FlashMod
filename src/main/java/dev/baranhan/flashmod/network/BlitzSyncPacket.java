package dev.baranhan.flashmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Sunucu -> izleyenler + kendisi: Blitz basladi/bitti ve sahne cercevesi (hedef, yon, hizcinin baslangici). */
public final class BlitzSyncPacket {
    public final UUID player;
    public final boolean active;
    public final int targetId;
    public final long start;
    public final double ax, ay, az;
    public final float fx, fz;
    public final double px, py, pz;

    public BlitzSyncPacket(UUID player, boolean active, int targetId, long start, double ax, double ay, double az,
                           float fx, float fz, double px, double py, double pz) {
        this.player = player;
        this.active = active;
        this.targetId = targetId;
        this.start = start;
        this.ax = ax;
        this.ay = ay;
        this.az = az;
        this.fx = fx;
        this.fz = fz;
        this.px = px;
        this.py = py;
        this.pz = pz;
    }

    public static void encode(BlitzSyncPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.player);
        buf.writeBoolean(m.active);
        buf.writeVarInt(m.targetId);
        buf.writeLong(m.start);
        buf.writeDouble(m.ax);
        buf.writeDouble(m.ay);
        buf.writeDouble(m.az);
        buf.writeFloat(m.fx);
        buf.writeFloat(m.fz);
        buf.writeDouble(m.px);
        buf.writeDouble(m.py);
        buf.writeDouble(m.pz);
    }

    public static BlitzSyncPacket decode(FriendlyByteBuf buf) {
        return new BlitzSyncPacket(buf.readUUID(), buf.readBoolean(), buf.readVarInt(), buf.readLong(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
                buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(BlitzSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.baranhan.flashmod.client.BlitzClient.handleSync(msg));
    }
}
