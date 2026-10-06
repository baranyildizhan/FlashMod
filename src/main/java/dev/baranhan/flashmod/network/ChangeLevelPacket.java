package dev.baranhan.flashmod.network;

import dev.baranhan.flashmod.speed.SpeedsterData;
import dev.baranhan.flashmod.speed.SpeedsterLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class ChangeLevelPacket {
    private final int delta;

    public ChangeLevelPacket(int delta) {
        this.delta = delta;
    }

    public static void encode(ChangeLevelPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.delta);
    }

    public static ChangeLevelPacket decode(FriendlyByteBuf buf) {
        return new ChangeLevelPacket(buf.readByte());
    }

    public static void handle(ChangeLevelPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (player == null) return;

        int old = SpeedsterData.getLevel(player);
        int level = Mth.clamp(old + Mth.clamp(msg.delta, -1, 1), SpeedsterData.MIN_LEVEL, SpeedsterData.MAX_LEVEL);
        if (level == old) return;
        SpeedsterData.setLevel(player, level);

        player.displayClientMessage(Component.translatable("msg.flashmod.level", level), true);
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F,
                0.6F + level * 0.11F);

        SpeedsterLogic.serverTick(player);
        FlashNetwork.syncToTrackingAndSelf(player);
    }
}
