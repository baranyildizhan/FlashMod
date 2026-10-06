package dev.baranhan.flashmod.network;

import dev.baranhan.flashmod.skin.SkinData;
import dev.baranhan.flashmod.speed.SpeedsterData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Sunucu -> istemci: bir oyuncunun skin'i (bos = varsayilan Mojang skin'i). */
public final class SkinSyncPacket {
    public final UUID player;
    public final byte[] png;
    public final boolean slim;
    public final String name;

    public SkinSyncPacket(UUID player, byte[] png, boolean slim, String name) {
        this.player = player;
        this.png = png;
        this.slim = slim;
        this.name = name;
    }

    public static SkinSyncPacket of(Player p) {
        return new SkinSyncPacket(p.getUUID(), SpeedsterData.getSkin(p), SpeedsterData.getSkinSlim(p), SpeedsterData.getSkinName(p));
    }

    public static void encode(SkinSyncPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.player);
        buf.writeByteArray(m.png);
        buf.writeBoolean(m.slim);
        buf.writeUtf(m.name, SkinData.MAX_NAME);
    }

    public static SkinSyncPacket decode(FriendlyByteBuf buf) {
        return new SkinSyncPacket(buf.readUUID(), buf.readByteArray(SkinData.MAX_BYTES), buf.readBoolean(),
                buf.readUtf(SkinData.MAX_NAME));
    }

    public static void handle(SkinSyncPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                dev.baranhan.flashmod.client.skin.SkinManager.apply(m.player, m.png, m.slim, m.name));
    }
}
