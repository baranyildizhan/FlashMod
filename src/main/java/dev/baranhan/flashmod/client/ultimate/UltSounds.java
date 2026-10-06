package dev.baranhan.flashmod.client.ultimate;

import dev.baranhan.flashmod.FlashSounds;
import dev.baranhan.flashmod.ultimate.UltimatePhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sinematik sesleri (rehber 12.1, "UI" olanlar): her istemci tick'inde son tick'ten simdikine kadar olan olaylar
 * calinir (atlama olmaz). Donguler fade-in/out'lu tickable sesler; yorunge basinda sessizlik icin hepsi kesilir.
 * Konumlu sesler sunucudan (UltimateManager) gelir. Izleyiciler tepede donen ugultuyu duyar.
 */
public final class UltSounds {
    /** {t, id, pitch, volume}. Zamanlar UltimatePhase cizelgesine gore. */
    private static final Object[][] SHOTS = {
            {0F, "ult_activate", 1F, 1F}, {26F, "ult_charge_peak", 1F, 1F}, {48F, "ult_flash_swell", 1F, 1F},
            {53F, "ult_pushoff", 0.8F, 1.2F},
            {56F, "ult_footsteps_ramp", 0.9F, 1F}, {62F, "ult_footsteps_ramp", 0.95F, 1F}, {68F, "ult_footsteps_ramp", 1.0F, 1F},
            {74F, "ult_footsteps_ramp", 1.1F, 1F}, {79F, "ult_footsteps_ramp", 1.25F, 1F}, {84F, "ult_footsteps_ramp", 1.4F, 1F},
            {88F, "ult_footsteps_ramp", 1.6F, 1F},
            {86F, "ult_speed_ramp", 1F, 1F}, {112F, "ult_boom", 0.9F, 1.4F}, {126F, "ult_pass_under", 0.8F, 1.4F},
            {130F, "ult_ocean_tear", 0.7F, 1.4F}, {150F, "ult_spray_wash", 0.8F, 1.2F}, {172F, "ult_ascend", 1F, 1.2F},
            {192F, "ult_orbit_streak", 1.4F, 0.5F}, {230F, "ult_orbit_streak", 1.7F, 0.9F}, {250F, "ult_return_swell", 1F, 1.2F},
            {262F, "ult_tunnel_rush", 1.05F, 1.3F}, {274F, "ult_land_heavy", 0.8F, 1.3F}, {276F, "ult_eyes_ignite", 1.2F, 1F},
            {276F, "ult_charge_final", 1F, 1.2F}, {286F, "ult_inhale_crack", 1F, 1F}, {290F, "ult_impact_sub", 0.7F, 1.5F},
            {290F, "ult_hitstop_ring", 1.6F, 0.8F}, {326F, "ult_resolve", 1F, 1F}};
    /** Donguler: {basla, bitir, id, ses}. */
    private static final Object[][] LOOPS = {
            {6F, 30F, "ult_charge_loop", 0.9F}, {52F, 122F, "ult_void_ambience", 0.8F}, {122F, 182F, "ult_high_wind", 1.0F},
            {182F, 262F, "ult_space_hum", 0.35F}};

    private static final Map<UltState, List<SoundInstance>> PLAYING = new HashMap<>();

    private UltSounds() {}

    public static void tick(UltState s, float prev, float t) {
        Minecraft mc = Minecraft.getInstance();
        if (s.full) {
            float orbit = UltimatePhase.ORBIT.start;
            if (prev < orbit && t >= orbit) stopAll(s); // yorunge: kontrast icin sessizlik
            for (Object[] e : SHOTS) {
                float at = (Float) e[0];
                if (prev < at && t >= at) {
                    SoundEvent ev = FlashSounds.ult((String) e[1]);
                    if (ev == null) continue;
                    SoundInstance si = SimpleSoundInstance.forUI(ev, (Float) e[2], (Float) e[3]);
                    mc.getSoundManager().play(si);
                    PLAYING.computeIfAbsent(s, k -> new ArrayList<>()).add(si);
                }
            }
            for (Object[] l : LOOPS) {
                float a = (Float) l[0];
                if (prev < a && t >= a) {
                    SoundEvent ev = FlashSounds.ult((String) l[2]);
                    if (ev == null) continue;
                    Loop loop = new Loop(ev, s, a, (Float) l[1], (Float) l[3], null);
                    mc.getSoundManager().play(loop);
                    PLAYING.computeIfAbsent(s, k -> new ArrayList<>()).add(loop);
                }
            }
        } else if (prev < UltimatePhase.SCENE_START && t >= UltimatePhase.SCENE_START) {
            SoundEvent ev = FlashSounds.ult("ult_orbit_world");
            if (ev != null) {
                Loop loop = new Loop(ev, s, UltimatePhase.SCENE_START, UltimatePhase.SCENE_END, 1.0F,
                        s.arena.toWorld(0, 14, s.arena.d * 0.5));
                mc.getSoundManager().play(loop);
                PLAYING.computeIfAbsent(s, k -> new ArrayList<>()).add(loop);
            }
        }
    }

    public static void stopAll(UltState s) {
        List<SoundInstance> l = PLAYING.remove(s);
        if (l == null) return;
        for (SoundInstance si : l) Minecraft.getInstance().getSoundManager().stop(si);
    }

    /** Pencere boyunca calan, kenarlarda 3 tick fade'li dongu. pos null ise konumsuz (UI). */
    private static final class Loop extends AbstractTickableSoundInstance {
        private final UltState s;
        private final float a, b, vol;

        Loop(SoundEvent ev, UltState s, float a, float b, float vol, Vec3 pos) {
            super(ev, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.s = s;
            this.a = a;
            this.b = b;
            this.vol = vol;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            if (pos == null) {
                this.relative = true;
                this.attenuation = Attenuation.NONE;
                this.x = 0; this.y = 0; this.z = 0;
            } else {
                this.x = pos.x; this.y = pos.y; this.z = pos.z;
            }
        }

        @Override
        public void tick() {
            float t = s.t(0F);
            if (t > b + 3F || t < a - 1F || s.abortAt >= 0) { stop(); return; }
            float k = Mth.clamp(Math.min(t - a, b - t) / 3F, 0F, 1F);
            this.volume = Math.max(0.001F, vol * k);
        }
    }
}
