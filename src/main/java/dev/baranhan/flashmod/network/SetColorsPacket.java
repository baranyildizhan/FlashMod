package dev.baranhan.flashmod.network;

import dev.baranhan.flashmod.speed.SpeedsterData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class SetColorsPacket {
    private final int core;
    private final int glow;
    /** true: giris aninda istemci varsayilani; sunucuda kayitli renk varsa EZMEZ (kayitli olan gecerli). */
    private final boolean initial;

    public SetColorsPacket(int core, int glow) {
        this(core, glow, false);
    }

    public SetColorsPacket(int core, int glow, boolean initial) {
        this.core = core & 0xFFFFFF;
        this.glow = glow & 0xFFFFFF;
        this.initial = initial;
    }

    public static void encode(SetColorsPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.core);
        buf.writeInt(msg.glow);
        buf.writeBoolean(msg.initial);
    }

    public static SetColorsPacket decode(FriendlyByteBuf buf) {
        return new SetColorsPacket(buf.readInt(), buf.readInt(), buf.readBoolean());
    }

    public static void handle(SetColorsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (player == null) return;
        if (msg.initial && SpeedsterData.hasColors(player)) {
            // Kayitli renkler gecerli: istemciye (ve gorenlere) sunucudaki degeri tekrar gonder
            FlashNetwork.syncToTrackingAndSelf(player);
            return;
        }
        SpeedsterData.setColors(player, msg.core, msg.glow); // oyuncu NBT'sine (ForgeData) kaydedilir
        FlashNetwork.syncToTrackingAndSelf(player);
    }
}
