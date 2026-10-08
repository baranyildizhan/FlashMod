package dev.baranhan.flashmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sunucu -> kendisi + izleyenler: yuklu yumruklar acik mi ve yeteneklerin bekleme sureleri (kalan / toplam tick):
 * zaman kalintisi, geri sarma, Blitz, ultimate.
 * Bekleme sureleri sadece HUD icin (yalnizca kendi istemcinde anlamli).
 */
public final class SkillSyncPacket {
    public final UUID player;
    public final boolean fists;
    public final int decoyLeft, decoyTotal, rewindLeft, rewindTotal, blitzLeft, blitzTotal, ultLeft, ultTotal;

    public SkillSyncPacket(UUID player, boolean fists, int decoyLeft, int decoyTotal, int rewindLeft, int rewindTotal,
                           int blitzLeft, int blitzTotal, int ultLeft, int ultTotal) {
        this.player = player;
        this.fists = fists;
        this.decoyLeft = decoyLeft;
        this.decoyTotal = decoyTotal;
        this.rewindLeft = rewindLeft;
        this.rewindTotal = rewindTotal;
        this.blitzLeft = blitzLeft;
        this.blitzTotal = blitzTotal;
        this.ultLeft = ultLeft;
        this.ultTotal = ultTotal;
    }

    public static void encode(SkillSyncPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.player);
        buf.writeBoolean(m.fists);
        buf.writeVarInt(m.decoyLeft);
        buf.writeVarInt(m.decoyTotal);
        buf.writeVarInt(m.rewindLeft);
        buf.writeVarInt(m.rewindTotal);
        buf.writeVarInt(m.blitzLeft);
        buf.writeVarInt(m.blitzTotal);
        buf.writeVarInt(m.ultLeft);
        buf.writeVarInt(m.ultTotal);
    }

    public static SkillSyncPacket decode(FriendlyByteBuf buf) {
        return new SkillSyncPacket(buf.readUUID(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(SkillSyncPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.baranhan.flashmod.client.skill.SkillClient.handleSync(m));
    }
}
