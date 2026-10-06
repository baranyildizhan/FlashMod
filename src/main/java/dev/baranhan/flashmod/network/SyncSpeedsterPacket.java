package dev.baranhan.flashmod.network;

import dev.baranhan.flashmod.speed.PhaseHelper;
import dev.baranhan.flashmod.speed.SpeedsterData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public final class SyncSpeedsterPacket {
    public final UUID player;
    public final boolean active;
    public final int level;
    public final int core;
    public final int glow;
    public final boolean phasing;

    public SyncSpeedsterPacket(UUID player, boolean active, int level, int core, int glow, boolean phasing) {
        this.player = player;
        this.active = active;
        this.level = level;
        this.core = core;
        this.glow = glow;
        this.phasing = phasing;
    }

    public static SyncSpeedsterPacket of(Player p) {
        return new SyncSpeedsterPacket(p.getUUID(), SpeedsterData.isActive(p), SpeedsterData.getLevel(p),
                SpeedsterData.getCore(p), SpeedsterData.getGlow(p), PhaseHelper.isPhasing(p));
    }

    public static void encode(SyncSpeedsterPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.player);
        buf.writeBoolean(msg.active);
        buf.writeByte(msg.level);
        buf.writeInt(msg.core);
        buf.writeInt(msg.glow);
        buf.writeBoolean(msg.phasing);
    }

    public static SyncSpeedsterPacket decode(FriendlyByteBuf buf) {
        return new SyncSpeedsterPacket(buf.readUUID(), buf.readBoolean(), buf.readByte(), buf.readInt(), buf.readInt(),
                buf.readBoolean());
    }

    public static void handle(SyncSpeedsterPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.baranhan.flashmod.client.ClientPacketHandler.handleSync(msg));
    }
}
