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

    // ---------------------------------------------------------------- ultimate
    // Dosyalar: assets/flashmod/sounds/ult/<ad>.ogg (sounds.json). Zamanlama ve ses seviyeleri UltSounds'ta; agir
    // vuruslar (aparkat, hava yumrugu, yere carpma) Blitz finalindeki vanilla katmanlari kullanir, kosu loop'u TRAIL_LOOP.
    /** Aktivasyon: simsek patlamasi ve kilitlenme (~1 sn). */
    public static final RegistryObject<SoundEvent> ULT_ACTIVATE = reg("ult_activate");
    /** Sarj: tek seferlik yukselen ses, bas noktasi kosuya cikista (~1.2 sn). */
    public static final RegistryObject<SoundEvent> ULT_CHARGE = reg("ult_charge");
    /** Bastaki carpma (ilk vurus); inisde kalin perdeden tekrar. */
    public static final RegistryObject<SoundEvent> ULT_HIT = reg("ult_hit");
    /** Run out: vurustan sonra kosup cikis ve okyanusta ufka firlayis (~1-1.5 sn). */
    public static final RegistryObject<SoundEvent> ULT_RUN_OUT = reg("ult_run_out");
    /** Havada belirme (isinlanma citirtisi, kisa). */
    public static final RegistryObject<SoundEvent> ULT_BLINK = reg("ult_blink");
    /** Son sahneden once tunelde Flash comelip kamera yuzune yaklasirken (eskiden ors sesi; ~1 sn, agir vurgu). */
    public static final RegistryObject<SoundEvent> ULT_FOCUS = reg("ult_focus");
    /** Kosarken arka planda dusuk ruzgar (loop; kesintisiz donecek sekilde kesilmis olmali). */
    public static final RegistryObject<SoundEvent> ULT_WIND = reg("ult_wind");
    /** Yavas cekim: hedef havada asili kalirken (~1 sn, uzayan/derinlesen ugultu). */
    public static final RegistryObject<SoundEvent> ULT_SLOWMO = reg("ult_slowmo");

    // ---------------------------------------------------------------- yetenekler
    // Dosyalar: assets/flashmod/sounds/skill/<ad>.ogg (sounds.json; liste ve sureler README'de).
    /** Kinetik yuk doldu (kisa citirti / "hazir"). */
    public static final RegistryObject<SoundEvent> KINETIC_READY = reg("kinetic_ready");
    /** Kinetik yumruk (yuk birakildi); tam yukte ustune Blitz finalindeki vanilla katmanlar. */
    public static final RegistryObject<SoundEvent> KINETIC_PUNCH = reg("kinetic_punch");
    /** Zaman kalintisi birakildi + yana atilma. */
    public static final RegistryObject<SoundEvent> DECOY_CAST = reg("decoy_cast");
    /** Zaman kalintisi parcalandi (statik bosalma) ya da soldu. */
    public static final RegistryObject<SoundEvent> DECOY_SHATTER = reg("decoy_shatter");
    /** Geri sarma (ters akan zaman, tum geri sarma boyunca). */
    public static final RegistryObject<SoundEvent> REWIND = reg("rewind");
    /** Geri sarma bitti: zaman yerine oturur. */
    public static final RegistryObject<SoundEvent> REWIND_END = reg("rewind_end");

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FlashMod.MODID, name)));
    }
}
