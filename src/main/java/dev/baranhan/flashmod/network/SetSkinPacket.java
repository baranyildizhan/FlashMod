package dev.baranhan.flashmod.network;

import dev.baranhan.flashmod.skin.SkinData;
import dev.baranhan.flashmod.speed.SpeedsterData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Istemci -> sunucu: skin sec (bos bayt dizisi = varsayilan skin'e don). Sunucu dogrular, NBT'ye yazar, yayar. */
public final class SetSkinPacket {
    private final byte[] png;
    private final boolean slim;
    private final String name;

    public SetSkinPacket(byte[] png, boolean slim, String name) {
        this.png = png == null ? new byte[0] : png;
        this.slim = slim;
        this.name = name == null ? "" : (name.length() > SkinData.MAX_NAME ? name.substring(0, SkinData.MAX_NAME) : name);
    }

    public static void encode(SetSkinPacket m, FriendlyByteBuf buf) {
        buf.writeByteArray(m.png);
        buf.writeBoolean(m.slim);
        buf.writeUtf(m.name, SkinData.MAX_NAME);
    }

    public static SetSkinPacket decode(FriendlyByteBuf buf) {
        return new SetSkinPacket(buf.readByteArray(SkinData.MAX_BYTES), buf.readBoolean(), buf.readUtf(SkinData.MAX_NAME));
    }

    public static void handle(SetSkinPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (p == null) return;
        if (m.png.length > 0 && SkinData.validate(m.png) == null) return; // gecersiz: yok say
        SpeedsterData.setSkin(p, m.png, m.slim, m.name);
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), SkinSyncPacket.of(p));
    }
}
