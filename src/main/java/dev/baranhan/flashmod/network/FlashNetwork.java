package dev.baranhan.flashmod.network;

import dev.baranhan.flashmod.FlashMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class FlashNetwork {
    private static final String VERSION = "10";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FlashMod.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private static boolean registered = false;

    private FlashNetwork() {}

    public static void register() {
        if (registered) return;
        registered = true;
        int id = 0;

        CHANNEL.messageBuilder(ToggleSpeedPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToggleSpeedPacket::encode)
                .decoder(ToggleSpeedPacket::decode)
                .consumerMainThread(ToggleSpeedPacket::handle)
                .add();

        CHANNEL.messageBuilder(ChangeLevelPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ChangeLevelPacket::encode)
                .decoder(ChangeLevelPacket::decode)
                .consumerMainThread(ChangeLevelPacket::handle)
                .add();

        CHANNEL.messageBuilder(SetColorsPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetColorsPacket::encode)
                .decoder(SetColorsPacket::decode)
                .consumerMainThread(SetColorsPacket::handle)
                .add();

        CHANNEL.messageBuilder(PhaseInputPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PhaseInputPacket::encode)
                .decoder(PhaseInputPacket::decode)
                .consumerMainThread(PhaseInputPacket::handle)
                .add();

        CHANNEL.messageBuilder(TornadoStatePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TornadoStatePacket::encode)
                .decoder(TornadoStatePacket::decode)
                .consumerMainThread(TornadoStatePacket::handle)
                .add();

        CHANNEL.messageBuilder(BlitzStartPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BlitzStartPacket::encode)
                .decoder(BlitzStartPacket::decode)
                .consumerMainThread(BlitzStartPacket::handle)
                .add();

        CHANNEL.messageBuilder(AbilityInputPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AbilityInputPacket::encode)
                .decoder(AbilityInputPacket::decode)
                .consumerMainThread(AbilityInputPacket::handle)
                .add();

        CHANNEL.messageBuilder(WallRunPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WallRunPacket::encode)
                .decoder(WallRunPacket::decode)
                .consumerMainThread(WallRunPacket::handle)
                .add();

        CHANNEL.messageBuilder(SetSkinPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetSkinPacket::encode)
                .decoder(SetSkinPacket::decode)
                .consumerMainThread(SetSkinPacket::handle)
                .add();

        CHANNEL.messageBuilder(SkinSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkinSyncPacket::encode)
                .decoder(SkinSyncPacket::decode)
                .consumerMainThread(SkinSyncPacket::handle)
                .add();

        CHANNEL.messageBuilder(UltimateRequestPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UltimateRequestPacket::encode)
                .decoder(UltimateRequestPacket::decode)
                .consumerMainThread(UltimateRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(UltimateStartPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UltimateStartPacket::encode)
                .decoder(UltimateStartPacket::decode)
                .consumerMainThread(UltimateStartPacket::handle)
                .add();

        CHANNEL.messageBuilder(UltimateEventPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UltimateEventPacket::encode)
                .decoder(UltimateEventPacket::decode)
                .consumerMainThread(UltimateEventPacket::handle)
                .add();

        CHANNEL.messageBuilder(SyncSpeedsterPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncSpeedsterPacket::encode)
                .decoder(SyncSpeedsterPacket::decode)
                .consumerMainThread(SyncSpeedsterPacket::handle)
                .add();

        CHANNEL.messageBuilder(TornadoSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TornadoSyncPacket::encode)
                .decoder(TornadoSyncPacket::decode)
                .consumerMainThread(TornadoSyncPacket::handle)
                .add();

        CHANNEL.messageBuilder(BlitzSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BlitzSyncPacket::encode)
                .decoder(BlitzSyncPacket::decode)
                .consumerMainThread(BlitzSyncPacket::handle)
                .add();

        CHANNEL.messageBuilder(TimeRatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TimeRatePacket::encode)
                .decoder(TimeRatePacket::decode)
                .consumerMainThread(TimeRatePacket::handle)
                .add();

        CHANNEL.messageBuilder(AbilitySyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AbilitySyncPacket::encode)
                .decoder(AbilitySyncPacket::decode)
                .consumerMainThread(AbilitySyncPacket::handle)
                .add();
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }

    public static void sendTo(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void syncToTrackingAndSelf(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), SyncSpeedsterPacket.of(player));
    }

    /** Renk/durum + skin: giris, yeniden dogma, boyut degisimi icin. */
    public static void syncAllToTrackingAndSelf(ServerPlayer player) {
        syncToTrackingAndSelf(player);
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), SkinSyncPacket.of(player));
    }
}
