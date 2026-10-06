package dev.baranhan.flashmod.network;

import dev.baranhan.flashmod.FlashSounds;
import dev.baranhan.flashmod.speed.SpeedsterData;
import dev.baranhan.flashmod.speed.SpeedsterLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class ToggleSpeedPacket {
    public ToggleSpeedPacket() {}

    public static void encode(ToggleSpeedPacket msg, FriendlyByteBuf buf) {}

    public static ToggleSpeedPacket decode(FriendlyByteBuf buf) {
        return new ToggleSpeedPacket();
    }

    public static void handle(ToggleSpeedPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (player == null || player.isSpectator()) return;

        boolean on = !SpeedsterData.isActive(player);
        SpeedsterData.setActive(player, on);

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                on ? FlashSounds.POWER_ON.get() : FlashSounds.POWER_OFF.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable(on ? "msg.flashmod.on" : "msg.flashmod.off"), true);

        SpeedsterLogic.serverTick(player);
        FlashNetwork.syncToTrackingAndSelf(player);
    }
}
