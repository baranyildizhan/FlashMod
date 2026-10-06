package dev.baranhan.flashmod.network;

import dev.baranhan.flashmod.ultimate.ArenaFrame;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Sunucu -> istemci: ultimate basladi (alici basina youAreTarget / fullCinematic). preview: sadece gorsel. */
public final class UltimateStartPacket {
    public final int sessionId, casterId, targetId;
    public final long startGameTime, seed;
    public final float scale;
    public final ArenaFrame arena;
    public final double sx, sy, sz;
    public final int core, glow;
    public final boolean youAreTarget, fullCinematic, preview;

    public UltimateStartPacket(int sessionId, int casterId, int targetId, long startGameTime, float scale, ArenaFrame arena,
                               double sx, double sy, double sz, int core, int glow, long seed, boolean youAreTarget,
                               boolean fullCinematic, boolean preview) {
        this.sessionId = sessionId;
        this.casterId = casterId;
        this.targetId = targetId;
        this.startGameTime = startGameTime;
        this.scale = scale;
        this.arena = arena;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.core = core;
        this.glow = glow;
        this.seed = seed;
        this.youAreTarget = youAreTarget;
        this.fullCinematic = fullCinematic;
        this.preview = preview;
    }

    public static void encode(UltimateStartPacket m, FriendlyByteBuf buf) {
        buf.writeInt(m.sessionId);
        buf.writeInt(m.casterId);
        buf.writeInt(m.targetId);
        buf.writeLong(m.startGameTime);
        buf.writeFloat(m.scale);
        m.arena.write(buf);
        buf.writeDouble(m.sx);
        buf.writeDouble(m.sy);
        buf.writeDouble(m.sz);
        buf.writeInt(m.core);
        buf.writeInt(m.glow);
        buf.writeLong(m.seed);
        buf.writeBoolean(m.youAreTarget);
        buf.writeBoolean(m.fullCinematic);
        buf.writeBoolean(m.preview);
    }

    public static UltimateStartPacket decode(FriendlyByteBuf buf) {
        return new UltimateStartPacket(buf.readInt(), buf.readInt(), buf.readInt(), buf.readLong(), buf.readFloat(),
                ArenaFrame.read(buf), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt(), buf.readInt(),
                buf.readLong(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(UltimateStartPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.baranhan.flashmod.client.ultimate.UltDirector.onStart(m));
    }
}
