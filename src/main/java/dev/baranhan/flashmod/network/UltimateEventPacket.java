package dev.baranhan.flashmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sunucu -> istemci: oturum olaylari. ABORT (iptal), CRASH / SLAM (hedef carpti; konum + normal),
 * STASIS_ON / STASIS_OFF (hedef oyuncunun girdisi kilitlensin/acilsin).
 */
public final class UltimateEventPacket {
    public static final byte ABORT = 0, CRASH = 1, SLAM = 2, STASIS_ON = 3, STASIS_OFF = 4;
    public final byte type;
    public final int sessionId;
    public final double x, y, z;
    public final float nx, ny, nz;

    public UltimateEventPacket(byte type, int sessionId, double x, double y, double z, float nx, float ny, float nz) {
        this.type = type;
        this.sessionId = sessionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
    }

    public static UltimateEventPacket simple(byte type, int sessionId) {
        return new UltimateEventPacket(type, sessionId, 0, 0, 0, 0, 1, 0);
    }

    public static void encode(UltimateEventPacket m, FriendlyByteBuf buf) {
        buf.writeByte(m.type);
        buf.writeInt(m.sessionId);
        buf.writeDouble(m.x);
        buf.writeDouble(m.y);
        buf.writeDouble(m.z);
        buf.writeFloat(m.nx);
        buf.writeFloat(m.ny);
        buf.writeFloat(m.nz);
    }

    public static UltimateEventPacket decode(FriendlyByteBuf buf) {
        return new UltimateEventPacket(buf.readByte(), buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(UltimateEventPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.baranhan.flashmod.client.ultimate.UltDirector.onEvent(m));
    }
}
