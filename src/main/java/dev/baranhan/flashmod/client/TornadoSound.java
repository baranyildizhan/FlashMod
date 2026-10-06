package dev.baranhan.flashmod.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.UUID;

/** Tornadonun ruzgar ugultusu: hizla birlikte yukselen ses ve perde, merkezden duyulur. */
public class TornadoSound extends AbstractTickableSoundInstance {
    private final UUID player;

    public TornadoSound(UUID player, double x, double y, double z) {
        super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.player = player;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.05F;
        this.pitch = 0.6F;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        ClientSpeedsters.Entry e = ClientSpeedsters.get(player);
        if (e == null || !e.tornado) {
            stop();
            return;
        }
        float k = e.tornadoPower();
        this.volume = 0.15F + 0.95F * k;
        this.pitch = 0.55F + 0.9F * k;
        this.x = e.tcx;
        this.y = e.tcy + 1.0D;
        this.z = e.tcz;
    }
}
