package dev.baranhan.flashmod.network;

import dev.baranhan.flashmod.speed.SkillLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Istemci -> sunucu: yetenek kullan (DECOY: atilma yonu dx,dz dunya yatayinda; REWIND). */
public final class SkillCastPacket {
    private final byte skill;
    private final float dx, dz;

    public SkillCastPacket(int skill, float dx, float dz) {
        this.skill = (byte) skill;
        this.dx = dx;
        this.dz = dz;
    }

    public static void encode(SkillCastPacket m, FriendlyByteBuf buf) {
        buf.writeByte(m.skill);
        buf.writeFloat(m.dx);
        buf.writeFloat(m.dz);
    }

    public static SkillCastPacket decode(FriendlyByteBuf buf) {
        return new SkillCastPacket(buf.readByte(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(SkillCastPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (p == null) return;
        SkillLogic.cast(p, m.skill, m.dx, m.dz);
    }
}
