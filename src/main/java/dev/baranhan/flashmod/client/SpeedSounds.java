package dev.baranhan.flashmod.client;

import dev.baranhan.flashmod.FlashSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Her hizci icin surekli (loop) sesler: kosu ruzgari, iz simseklerinin citirtisi (kosu + tornado + blitz),
 * phasing titresimi. Ses yumusakca acilir/kapanir; kosul bitince kendini durdurur, gerekirse yeniden baslar.
 */
public final class SpeedSounds {
    private static final int RUN = 0, TRAIL = 1, PHASE = 2, CHARGE = 3;
    private static final Map<UUID, Loop[]> LOOPS = new HashMap<>();

    private SpeedSounds() {}

    /** SpeedFx her tick her oyuncu icin cagirir. */
    public static void tick(Minecraft mc, UUID id, ClientSpeedsters.Entry e) {
        Loop[] arr = LOOPS.computeIfAbsent(id, k -> new Loop[4]);
        ensure(mc, arr, RUN, id, e, want(RUN, e) > 0.02F, FlashSounds.RUN_LOOP.get());
        ensure(mc, arr, TRAIL, id, e, want(TRAIL, e) > 0.02F, FlashSounds.TRAIL_LOOP.get());
        ensure(mc, arr, PHASE, id, e, want(PHASE, e) > 0.02F, FlashSounds.PHASE_LOOP.get());
        ensure(mc, arr, CHARGE, id, e, want(CHARGE, e) > 0.02F, FlashSounds.THROW_CHARGE.get());
    }

    private static void ensure(Minecraft mc, Loop[] arr, int kind, UUID id, ClientSpeedsters.Entry e, boolean want, SoundEvent ev) {
        Loop l = arr[kind];
        if (want && (l == null || l.isStopped())) {
            l = new Loop(id, kind, ev, e);
            arr[kind] = l;
            mc.getSoundManager().play(l);
        }
    }

    /** Hedef ses seviyesi 0..1. */
    static float want(int kind, ClientSpeedsters.Entry e) {
        if (!e.active) return 0F;
        return switch (kind) {
            case RUN -> e.tornado || e.phasing ? 0F : Mth.clamp((e.speed - 0.3F) / 2.2F, 0F, 1F);
            case TRAIL -> e.tornado ? Math.max(0.35F, e.tornadoPower())
                    : Mth.clamp((e.speed - 0.3F) / 2.0F, 0F, 1F) * (e.nodes.isEmpty() ? 0F : 1F);
            case PHASE -> e.phasing ? 1F : 0F;
            case CHARGE -> e.charging ? 0.35F + 0.65F * Mth.clamp(e.charge / 100F, 0F, 1F) : 0F;
            default -> 0F;
        };
    }

    public static void clear() {
        LOOPS.clear();
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final UUID id;
        private final int kind;
        private int quiet;

        Loop(UUID id, int kind, SoundEvent ev, ClientSpeedsters.Entry e) {
            super(ev, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.id = id;
            this.kind = kind;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001F;
            this.x = e.soundX;
            this.y = e.soundY;
            this.z = e.soundZ;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            ClientSpeedsters.Entry e = ClientSpeedsters.get(id);
            if (e == null) {
                stop();
                return;
            }
            float target = want(kind, e);
            float maxVol = kind == PHASE ? 0.8F : kind == TRAIL ? 0.9F : 1.0F;
            this.volume += (target * maxVol - this.volume) * 0.2F;
            this.pitch = switch (kind) {
                case RUN -> 0.85F + 0.4F * Mth.clamp(e.speed / 3F, 0F, 1F);
                case TRAIL -> 0.9F + 0.25F * target;
                case CHARGE -> 0.8F + 0.5F * target;
                default -> 1.0F;
            };
            this.x = e.soundX;
            this.y = e.soundY + 0.9D;
            this.z = e.soundZ;
            if (target <= 0.02F && this.volume < 0.01F) {
                if (++quiet > 10) stop();
            } else {
                quiet = 0;
            }
        }
    }
}
