package dev.baranhan.flashmod.client.ultimate;

import dev.baranhan.flashmod.FlashSounds;
import dev.baranhan.flashmod.ultimate.UltimatePhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ultimate sesleri, tamamen istemcide ve goruntuyle AYNI zamandan (UltState.t) calinir: her istemci tick'inde son
 * tick'ten simdikine kadar gecilen olaylar baslatilir (atlama olmaz). Tam sinematikte sesler konumsuz (UI), izleyicilerde
 * olayin dunyadaki yerinde. Kayitli 8 ses (FlashSounds.ULT_*), kosu loop'u (TRAIL_LOOP) ve ruzgar (ULT_WIND) ve agir vuruslarda Blitz
 * finalindeki vanilla katmanlar.
 */
public final class UltSounds {
    private static final int ACTIVATE = 0, CHARGE = 1, HIT = 2, RUN_OUT = 3, BLINK = 4, SLOWMO = 5, HEAVY = 6, FOCUS = 7;
    /** Izleyici icin olayin yeri: caster (yerde/proxy), hedef (betik), havadaki caster; NONE: sadece sinematikte. */
    private static final int AT_CASTER = 0, AT_TARGET = 1, AT_AIR = 2, NONE = -1;

    /** 2. sahnede (VOID) kameranin durup kosucunun ileri uzaklastigi an (UltCamera VOID tablosu, 108). */
    private static final float VOID_RUN_OUT = 108F;
    /** {t, ses, ses seviyesi, perde, yer}. Zamanlar UltimatePhase cizelgesine gore. */
    private static final float[][] EVENTS = {
            {0, ACTIVATE, 1.0F, 1.0F, AT_CASTER},
            {UltimatePhase.WINDUP.start, CHARGE, 1.0F, 1.0F, AT_CASTER},
            {UltimatePhase.HIT1, HIT, 1.0F, 1.0F, AT_TARGET},                 // bastaki carpma
            {UltimatePhase.HIT1 + 1, RUN_OUT, 1.0F, 1.0F, AT_CASTER},         // kosup cikis
            {VOID_RUN_OUT, RUN_OUT, 0.9F, 1.0F, NONE},                        // 2. sahne: kamera durur, kosucu ileri uzaklasir
            {UltOceanScene.DASH_T, RUN_OUT, 1.0F, 0.92F, NONE},               // okyanusta ufka firlayis
            {274, FOCUS, 1.0F, 1.0F, NONE},                                  // tunel: comelme, kamera yuze yaklasir
            {UltimatePhase.HIT2, HEAVY, 1.0F, 1.0F, AT_TARGET},               // aparkat
            {UltimatePhase.LAUNCH_T + 2, SLOWMO, 1.0F, 1.0F, AT_TARGET},      // havada asili, yavas cekim
            {UltimatePhase.AIR_BLINK, BLINK, 1.0F, 1.0F, AT_AIR},
            {UltimatePhase.HIT3, HEAVY, 0.9F, 1.08F, AT_TARGET},              // hava yumrugu
            {UltimatePhase.SLAM_T, HEAVY, 1.3F, 0.82F, AT_TARGET},            // yere carpma patlamasi
            {UltimatePhase.CASTER_LAND, HIT, 0.7F, 0.72F, AT_AIR}};           // Flash'in inisi

    /** Kosu loop'u (TRAIL_LOOP) ve arka plandaki ruzgar: kosup cikistan tunelden donuse kadar. */
    private static final float LOOP_START = UltimatePhase.HIT1 + 1, LOOP_END = UltimatePhase.HIT2;

    private static final Map<UltState, List<SoundInstance>> PLAYING = new HashMap<>();

    private UltSounds() {}

    public static void tick(UltState s, float prev, float t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (float[] e : EVENTS) {
            if (prev < e[0] && t >= e[0]) fire(mc, s, (int) e[1], e[2], e[3], (int) e[4]);
        }
        if (prev < LOOP_START && t >= LOOP_START && t < LOOP_END) {
            for (boolean wind : new boolean[]{false, true}) {
                if (wind && !s.full) continue; // ruzgar kameranin sesi: yalnizca sinematikte
                SoundInstance loop = new RunLoop(s, wind);
                mc.getSoundManager().play(loop);
                PLAYING.computeIfAbsent(s, k -> new ArrayList<>()).add(loop);
            }
        }
    }

    private static void fire(Minecraft mc, UltState s, int kind, float vol, float pitch, int at) {
        Vec3 pos = null;
        if (!s.full) { // izleyici: olayin yerinde
            if (at == NONE) return;
            pos = where(s, at);
            if (pos == null) return;
        }
        if (kind == HEAVY) {
            heavy(mc, s, pos, vol, pitch);
            return;
        }
        SoundEvent ev = switch (kind) {
            case ACTIVATE -> FlashSounds.ULT_ACTIVATE.get();
            case CHARGE -> FlashSounds.ULT_CHARGE.get();
            case HIT -> FlashSounds.ULT_HIT.get();
            case RUN_OUT -> FlashSounds.ULT_RUN_OUT.get();
            case BLINK -> FlashSounds.ULT_BLINK.get();
            case FOCUS -> FlashSounds.ULT_FOCUS.get();
            default -> FlashSounds.ULT_SLOWMO.get();
        };
        play(mc, s, ev, pos, vol, pitch);
    }

    /** Agir vurus: Blitz finalindeki vanilla katmanlar (havai fisek patlamasi + simsek + patlama). */
    private static void heavy(Minecraft mc, UltState s, Vec3 pos, float vol, float pitch) {
        play(mc, s, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, pos, 1.9F * vol, 0.35F * pitch);
        play(mc, s, SoundEvents.LIGHTNING_BOLT_IMPACT, pos, 1.0F * vol, 1.2F * pitch);
        play(mc, s, SoundEvents.GENERIC_EXPLODE, pos, 0.6F * vol, 1.6F * pitch);
    }

    private static void play(Minecraft mc, UltState s, SoundEvent ev, Vec3 pos, float vol, float pitch) {
        SoundInstance si = pos == null ? SimpleSoundInstance.forUI(ev, pitch, Math.min(1F, vol))
                : new SimpleSoundInstance(ev, SoundSource.PLAYERS, vol, pitch, RandomSource.create(), pos.x, pos.y, pos.z);
        mc.getSoundManager().play(si);
        PLAYING.computeIfAbsent(s, k -> new ArrayList<>()).add(si);
    }

    private static Vec3 where(UltState s, int at) {
        float t = s.t(0F);
        switch (at) {
            case AT_TARGET:
                return UltDirector.targetLive(s, 0F);
            case AT_AIR:
                return s.casterAir(t).add(0, 1.0, 0);
            default: {
                Player p = s.caster();
                return p == null ? null : UltRender.casterWorld(s, p, t, 0F, new float[1]).add(0, 1.0, 0);
            }
        }
    }

    public static void stopAll(UltState s) {
        List<SoundInstance> l = PLAYING.remove(s);
        if (l == null) return;
        for (SoundInstance si : l) Minecraft.getInstance().getSoundManager().stop(si);
    }

    /**
     * Kosu sirasinda iki loop. TRAIL_LOOP (Blitz'deki iz citirtisi): sinematikte konumsuz ama ses seviyesi kosucunun
     * kameraya uzakligina gore (yakinken tam, uzaklastikca zayiflar; yorungede sabit uzak, tunelde yakin), perde kosu
     * hizlandikca tizlesir. Izleyicide arenanin ustunde donen isigin yerinde. ULT_WIND: arka planda dusuk ruzgar
     * (kameranin sesi, uzakliktan bagimsiz). Ses hic 0'a inmez (motor sessiz sesi kesebilir): taban ~0.03.
     */
    private static final class RunLoop extends AbstractTickableSoundInstance {
        private static final float FLOOR = 0.03F;
        private final UltState s;
        private final boolean wind;
        private float near = 1F;

        RunLoop(UltState s, boolean wind) {
            super(wind ? FlashSounds.ULT_WIND.get() : FlashSounds.TRAIL_LOOP.get(), SoundSource.PLAYERS,
                    SoundInstance.createUnseededRandom());
            this.s = s;
            this.wind = wind;
            this.looping = true;
            this.delay = 0;
            this.volume = FLOOR;
            if (s.full) {
                this.relative = true;
                this.attenuation = Attenuation.NONE;
                this.x = 0; this.y = 0; this.z = 0;
            } else {
                Vec3 c = s.arena.toWorld(0, 14, s.arena.d * 0.5);
                this.x = c.x; this.y = c.y; this.z = c.z;
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            float t = s.t(0F);
            if (t > LOOP_END + 3F || t < LOOP_START - 1F || s.abortAt >= 0) { stop(); return; }
            float edge = Mth.clamp(Math.min(t - LOOP_START, LOOP_END - t) / 3F, 0F, 1F);
            float want;
            if (wind) {
                want = windLevel(t);
            } else {
                near += (proximity(t) - near) * 0.35F; // yumusak: kesmelerde ani sicrama olmasin
                want = (s.full ? trailLevel(t) : 0.8F) * near;
            }
            this.volume = Math.max(FLOOR, want * edge);
            this.pitch = wind ? 0.9F + 0.15F * Mth.clamp((t - UltimatePhase.VOID.start) / 60F, 0F, 1F) : pitch(t);
        }

        /** Kosucunun kameraya uzakligina gore 0..1 (3 blokta tam, ~11 blokta yari, 20 blokta ~0.15). */
        private float proximity(float t) {
            if (!s.full) return 1F;
            UltimatePhase ph = UltimatePhase.at(t);
            double d;
            if (ph == UltimatePhase.VOID || ph == UltimatePhase.OCEAN) {
                d = s.sceneCam.distanceTo(UltScene.runner(t, t));
            } else if (t < UltimatePhase.SCENE_START) {
                Player p = s.caster();
                if (p == null) return 1F;
                d = s.lastCamPos.distanceTo(UltRender.casterWorld(s, p, t, 0F, new float[1]).add(0, 1.0, 0));
            } else {
                return 1F; // yorunge ve tunel: seviye trailLevel'da
            }
            double x = Math.max(0.0, d - 3.0);
            return (float) (1.0 / (1.0 + x * x / 60.0));
        }

        private static float trailLevel(float t) {
            if (t < UltimatePhase.VOID.start) return 0.8F;                    // arenadan kosup cikis
            if (t < UltimatePhase.OCEAN.start) return 0.9F;
            if (t < UltimatePhase.ORBIT.start) return 1.0F;
            if (t < UltimatePhase.TUNNEL.start) return 0.3F;                   // yorunge: uzaktan
            return 1.0F;                                                       // tunel
        }

        private static float windLevel(float t) {
            if (t < UltimatePhase.VOID.start) return 0.2F;
            if (t < UltimatePhase.OCEAN.start) return 0.3F;
            if (t < UltimatePhase.ORBIT.start) return 0.38F;                   // acik deniz
            if (t < UltimatePhase.TUNNEL.start) return 0.12F;                  // uzay: neredeyse sessiz
            return 0.35F;
        }

        private static float pitch(float t) {
            if (t < UltimatePhase.VOID.start) return 0.95F;
            if (t < 96F) return Mth.lerp((t - UltimatePhase.VOID.start) / (96F - UltimatePhase.VOID.start), 0.95F, 1.15F);
            if (t < UltimatePhase.TUNNEL.start) return 1.1F;
            return Mth.lerp(UltimatePhase.TUNNEL.local(t), 1.1F, 1.25F);
        }
    }
}
