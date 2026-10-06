package dev.baranhan.flashmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Sunucu -> kendisi + izleyenler: enerji, mizrak sarji, agir cekim, duvarda kosma. */
public final class AbilitySyncPacket {
    public final UUID player;
    public final float energy, charge;
    public final boolean charging, slowmo, wallRun;
    public final float nx, nz;

    public AbilitySyncPacket(UUID player, float energy, float charge, boolean charging, boolean slowmo, boolean wallRun,
                             float nx, float nz) {
        this.player = player;
        this.energy = energy;
        this.charge = charge;
        this.charging = charging;
        this.slowmo = slowmo;
        this.wallRun = wallRun;
        this.nx = nx;
        this.nz = nz;
    }

    public static void encode(AbilitySyncPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.player);
        buf.writeFloat(m.energy);
        buf.writeFloat(m.charge);
        buf.writeBoolean(m.charging);
        buf.writeBoolean(m.slowmo);
        buf.writeBoolean(m.wallRun);
        buf.writeFloat(m.nx);
        buf.writeFloat(m.nz);
    }

    public static AbilitySyncPacket decode(FriendlyByteBuf buf) {
        return new AbilitySyncPacket(buf.readUUID(), buf.readFloat(), buf.readFloat(), buf.readBoolean(),
                buf.readBoolean(), buf.readBoolean(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(AbilitySyncPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.baranhan.flashmod.client.AbilityClient.handleSync(m));
    }
}
