package dev.baranhan.flashmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sunucu -> izleyenler + kendisi: yetenek gorsel olaylari. Konum/yon/guc genel alanlar; REWIND_START ek olarak geri
 * sarilacak yolu (yeniden eskiye) ve suresini tasir.
 */
public final class SkillFxPacket {
    public static final byte KINETIC_HIT = 0, DECOY_CAST = 1, DECOY_SHATTER = 2, DECOY_FADE = 3, REWIND_START = 4,
            REWIND_END = 5;

    public final byte type;
    public final UUID player;
    public final int entityId;
    public final double x, y, z;
    public final float dx, dy, dz, power;
    public final int core, glow;
    /** REWIND_START: sure (tick) ve yol (yeniden eskiye): x, y, z, yaw, pitch, hiz. */
    public final int ticks;
    public final float[] path;

    public SkillFxPacket(byte type, UUID player, int entityId, double x, double y, double z, float dx, float dy, float dz,
                         float power, int core, int glow, int ticks, float[] path) {
        this.type = type;
        this.player = player;
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.power = power;
        this.core = core;
        this.glow = glow;
        this.ticks = ticks;
        this.path = path == null ? new float[0] : path;
    }

    public static SkillFxPacket simple(byte type, UUID player, int entityId, double x, double y, double z, float dx, float dy,
                                       float dz, float power, int core, int glow) {
        return new SkillFxPacket(type, player, entityId, x, y, z, dx, dy, dz, power, core, glow, 0, null);
    }

    public static void encode(SkillFxPacket m, FriendlyByteBuf buf) {
        buf.writeByte(m.type);
        buf.writeUUID(m.player);
        buf.writeVarInt(m.entityId);
        buf.writeDouble(m.x);
        buf.writeDouble(m.y);
        buf.writeDouble(m.z);
        buf.writeFloat(m.dx);
        buf.writeFloat(m.dy);
        buf.writeFloat(m.dz);
        buf.writeFloat(m.power);
        buf.writeInt(m.core);
        buf.writeInt(m.glow);
        buf.writeVarInt(m.ticks);
        buf.writeVarInt(m.path.length);
        for (float f : m.path) buf.writeFloat(f);
    }

    public static SkillFxPacket decode(FriendlyByteBuf buf) {
        byte type = buf.readByte();
        UUID player = buf.readUUID();
        int id = buf.readVarInt();
        double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
        float dx = buf.readFloat(), dy = buf.readFloat(), dz = buf.readFloat(), power = buf.readFloat();
        int core = buf.readInt(), glow = buf.readInt();
        int ticks = buf.readVarInt();
        int n = Math.min(buf.readVarInt(), 6 * 512);
        float[] path = new float[n];
        for (int i = 0; i < n; i++) path[i] = buf.readFloat();
        return new SkillFxPacket(type, player, id, x, y, z, dx, dy, dz, power, core, glow, ticks, path);
    }

    public static void handle(SkillFxPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.baranhan.flashmod.client.skill.SkillClient.handleFx(m));
    }
}
