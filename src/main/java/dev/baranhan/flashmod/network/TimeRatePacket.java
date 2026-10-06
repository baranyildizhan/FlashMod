package dev.baranhan.flashmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Sunucu -> herkes: oyun hizi orani (1 = normal, 0.25 = 4 kat yavas). */
public final class TimeRatePacket {
    public final float rate;

    public TimeRatePacket(float rate) {
        this.rate = rate;
    }

    public static void encode(TimeRatePacket m, FriendlyByteBuf buf) {
        buf.writeFloat(m.rate);
    }

    public static TimeRatePacket decode(FriendlyByteBuf buf) {
        return new TimeRatePacket(buf.readFloat());
    }

    public static void handle(TimeRatePacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.baranhan.flashmod.client.TimeControlClient.setServerRate(m.rate));
    }
}
