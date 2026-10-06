package dev.baranhan.flashmod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Modun ozel sesleri. Dosyalar: assets/flashmod/sounds/&lt;isim&gt;.ogg (sounds.json bunlari esler).
 * Loop olanlar (run_loop, trail_loop, phase_loop, blitz_drone) kesintisiz donecek sekilde kesilmis olmali.
 */
public final class FlashSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, FlashMod.MODID);

    public static final RegistryObject<SoundEvent> POWER_ON = reg("power_on");
    public static final RegistryObject<SoundEvent> POWER_OFF = reg("power_off");
    /** Kosarken hiza gore yukselen ruzgar/kosu loop'u. */
    public static final RegistryObject<SoundEvent> RUN_LOOP = reg("run_loop");
    /** Iz simseklerinin citirtisi (kosu ve tornado sirasinda). */
    public static final RegistryObject<SoundEvent> TRAIL_LOOP = reg("trail_loop");
    public static final RegistryObject<SoundEvent> PHASE_START = reg("phase_start");
    /** Titresim (phasing) loop'u. */
    public static final RegistryObject<SoundEvent> PHASE_LOOP = reg("phase_loop");
    public static final RegistryObject<SoundEvent> SONIC_BOOM = reg("sonic_boom");
    public static final RegistryObject<SoundEvent> BRAKE = reg("brake");
    public static final RegistryObject<SoundEvent> BLITZ_START = reg("blitz_start");
    /** Blitz sinematigi boyunca "zaman yavasladi" ugultusu (loop). */
    public static final RegistryObject<SoundEvent> BLITZ_DRONE = reg("blitz_drone");
    public static final RegistryObject<SoundEvent> BLITZ_WHOOSH = reg("blitz_whoosh");
    public static final RegistryObject<SoundEvent> BLITZ_PUNCH = reg("blitz_punch");
    public static final RegistryObject<SoundEvent> BLITZ_KICK = reg("blitz_kick");
    public static final RegistryObject<SoundEvent> BLITZ_FINAL = reg("blitz_final");
    /** Simsek mizragi: elde biriktirme (loop), firlatma, isabet. */
    public static final RegistryObject<SoundEvent> THROW_CHARGE = reg("throw_charge");
    public static final RegistryObject<SoundEvent> THROW_RELEASE = reg("throw_release");
    public static final RegistryObject<SoundEvent> SPEAR_IMPACT = reg("spear_impact");
    /** Speed Force agir cekimi: baslangic / bitis. */
    public static final RegistryObject<SoundEvent> SLOWMO_START = reg("slowmo_start");
    public static final RegistryObject<SoundEvent> SLOWMO_END = reg("slowmo_end");
    /** Duvara tutunup kosmaya baslama. */
    public static final RegistryObject<SoundEvent> WALLRUN_START = reg("wallrun_start");

    private FlashSounds() {}

    /**
     * Ultimate sesleri (id -> kayit). sounds.json'da gecici olarak vanilla ses dosyalarina esleniyorlar; kendi
     * seslerini koyunca ilgili girdiyi "flashmod:ult/<id>" yap.
     */
    public static final java.util.Map<String, RegistryObject<SoundEvent>> ULT = new java.util.LinkedHashMap<>();
    public static final String[] ULT_IDS = {"ult_activate", "ult_blink", "ult_charge_loop", "ult_roar_crackle", "ult_charge_peak", "ult_whoosh_depart", "ult_hit_light", "ult_sonic_boom", "ult_flash_swell", "ult_void_ambience", "ult_heartbeat", "ult_pushoff", "ult_footsteps_ramp", "ult_speed_ramp", "ult_boom", "ult_high_wind", "ult_pass_under", "ult_ocean_tear", "ult_spray_wash", "ult_ascend", "ult_space_hum", "ult_orbit_streak", "ult_return_swell", "ult_tunnel_rush", "ult_land_heavy", "ult_eyes_ignite", "ult_charge_final", "ult_inhale_crack", "ult_impact_huge", "ult_impact_sub", "ult_hitstop_ring", "ult_release_whoosh", "ult_launch_wind", "ult_crash", "ult_debris", "ult_thunder_tail", "ult_resolve", "ult_orbit_world"};

    static {
        for (String id : ULT_IDS) ULT.put(id, reg(id));
    }

    public static SoundEvent ult(String id) {
        RegistryObject<SoundEvent> r = ULT.get(id);
        return r == null ? null : r.get();
    }

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FlashMod.MODID, name)));
    }
}
