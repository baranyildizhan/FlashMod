package dev.baranhan.flashmod.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class FlashServerConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue SPEED_SCALE;
    public static final ForgeConfigSpec.DoubleValue HUNGER_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue NEGATE_FALL_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue STEP_ASSIST;
    public static final ForgeConfigSpec.DoubleValue STEP_HEIGHT_BONUS;

    public static final ForgeConfigSpec.BooleanValue OVERDRIVE;
    public static final ForgeConfigSpec.DoubleValue OVERDRIVE_GROWTH;
    public static final ForgeConfigSpec.DoubleValue OVERDRIVE_DECAY;
    public static final ForgeConfigSpec.DoubleValue OVERDRIVE_MAX;
    public static final ForgeConfigSpec.DoubleValue MULTIPLAYER_SPEED_CAP;

    public static final ForgeConfigSpec.BooleanValue PHASING;
    public static final ForgeConfigSpec.DoubleValue SLOWMO_RATE;
    public static final ForgeConfigSpec.DoubleValue SLOWMO_DRAIN;
    public static final ForgeConfigSpec.BooleanValue ULT_ENABLED;
    public static final ForgeConfigSpec.IntValue ULT_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue ULT_ABORT_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue ULT_RANGE;
    public static final ForgeConfigSpec.DoubleValue ULT_CONE;
    public static final ForgeConfigSpec.DoubleValue ULT_DURATION_SCALE;
    public static final ForgeConfigSpec.DoubleValue ULT_HIT1;
    public static final ForgeConfigSpec.DoubleValue ULT_HIT2_BASE;
    public static final ForgeConfigSpec.DoubleValue ULT_HIT2_PERCENT;
    public static final ForgeConfigSpec.DoubleValue ULT_HIT2_MAX;
    public static final ForgeConfigSpec.DoubleValue ULT_HIT3;
    public static final ForgeConfigSpec.DoubleValue ULT_CRASH_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue ULT_CRASH_BREAKS;
    public static final ForgeConfigSpec.DoubleValue ULT_CRASH_HARDNESS;
    public static final ForgeConfigSpec.BooleanValue ULT_TARGET_SEES;
    public static final ForgeConfigSpec.BooleanValue ULT_ALLOW_CREATIVE;
    public static final ForgeConfigSpec.BooleanValue ULT_ALLOW_AIRBORNE;
    public static final ForgeConfigSpec.DoubleValue ULT_ENERGY;
    public static final ForgeConfigSpec.BooleanValue KINETIC_ENABLED;
    public static final ForgeConfigSpec.DoubleValue KINETIC_GAIN;
    public static final ForgeConfigSpec.DoubleValue KINETIC_MIN;
    public static final ForgeConfigSpec.DoubleValue KINETIC_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue KINETIC_KNOCKBACK;
    public static final ForgeConfigSpec.BooleanValue DECOY_ENABLED;
    public static final ForgeConfigSpec.DoubleValue DECOY_ENERGY;
    public static final ForgeConfigSpec.IntValue DECOY_COOLDOWN;
    public static final ForgeConfigSpec.IntValue DECOY_LIFETIME;
    public static final ForgeConfigSpec.DoubleValue DECOY_DASH;
    public static final ForgeConfigSpec.DoubleValue DECOY_DISCHARGE;
    public static final ForgeConfigSpec.IntValue DECOY_VANISH_TICKS;
    public static final ForgeConfigSpec.BooleanValue REWIND_ENABLED;
    public static final ForgeConfigSpec.DoubleValue REWIND_ENERGY;
    public static final ForgeConfigSpec.IntValue REWIND_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue REWIND_SECONDS;
    public static final ForgeConfigSpec.BooleanValue REWIND_HEAL;
    public static final ForgeConfigSpec.BooleanValue CHUNK_PRELOAD;
    public static final ForgeConfigSpec.DoubleValue CHUNK_PRELOAD_SECONDS;
    public static final ForgeConfigSpec.IntValue CHUNK_PRELOAD_MAX_BLOCKS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("speedster");
        SPEED_SCALE = b.comment("Multiplier for all speed levels.")
                .defineInRange("speedScale", 1.0D, 0.1D, 3.0D);
        HUNGER_MULTIPLIER = b.comment("Fraction of vanilla movement exhaustion applied while the power is active (0.05 = 5%).")
                .defineInRange("hungerMultiplier", 0.05D, 0.0D, 1.0D);
        NEGATE_FALL_DAMAGE = b.comment("No fall damage while the power is active.")
                .define("negateFallDamage", true);
        STEP_ASSIST = b.comment("Auto-step up 1 block while active. Disabled while sneaking.")
                .define("stepAssist", true);
        STEP_HEIGHT_BONUS = b.comment("Added step height (vanilla 0.6 + this).")
                .defineInRange("stepHeightBonus", 0.65D, 0.0D, 1.5D);
        b.pop();

        b.push("overdrive");
        OVERDRIVE = b.comment("Keep sprinting at full speed and you keep accelerating, with no fixed limit.")
                .define("enabled", true);
        OVERDRIVE_GROWTH = b.comment("Speed multiplier growth per tick while sprinting on the ground (0.008 = x2 every ~4.3 s).")
                .defineInRange("growthPerTick", 0.008D, 0.0D, 0.05D);
        OVERDRIVE_DECAY = b.comment("How fast the extra speed bleeds off once you stop sprinting (fraction per tick).")
                .defineInRange("decayPerTick", 0.03D, 0.001D, 0.5D);
        OVERDRIVE_MAX = b.comment("Max overdrive multiplier on top of the level speed. Vanilla clamps movement speed at 1024 anyway.")
                .defineInRange("maxMultiplier", 1000.0D, 1.0D, 7000.0D);
        MULTIPLAYER_SPEED_CAP = b.comment("Ground speed cap in blocks/tick for everyone except the singleplayer/LAN host.",
                        "Vanilla's 'moved too quickly' check rubber-bands players above ~10 blocks/tick. 0 = no cap.")
                .defineInRange("multiplayerSpeedCap", 9.0D, 0.0D, 10000.0D);
        b.pop();

        b.push("slowmo");
        SLOWMO_RATE = b.comment("Game speed while a speedster's slow-mo is on (0.25 = 4x slower: 5 ticks/s for the whole server).",
                        "Every player sees it and everyone, the speedster included, slows down.")
                .defineInRange("rate", 0.25D, 0.05D, 0.9D);
        SLOWMO_DRAIN = b.comment("Speed Force energy drained per real second while slow-mo is on (energy max 100).")
                .defineInRange("drainPerSecond", 11.0D, 0.0D, 100.0D);
        b.pop();

        b.push("ultimate");
        ULT_ENABLED = b.comment("Ultimate (world-tour punch) on/off.").define("enabled", true);
        ULT_COOLDOWN = b.comment("Cooldown in seconds.").defineInRange("cooldownSeconds", 60, 0, 3600);
        ULT_ABORT_COOLDOWN = b.comment("Fraction of the cooldown applied when the ultimate is aborted.")
                .defineInRange("abortCooldownFraction", 0.5D, 0.0D, 1.0D);
        ULT_RANGE = b.comment("Targeting range (blocks).").defineInRange("range", 16.0D, 2.0D, 64.0D);
        ULT_CONE = b.comment("Fallback cone angle (degrees).").defineInRange("coneAngle", 15.0D, 1.0D, 60.0D);
        ULT_DURATION_SCALE = b.comment("Scales the whole timeline (1 = ~17 s).").defineInRange("durationScale", 1.0D, 0.1D, 2.0D);
        ULT_HIT1 = b.defineInRange("hit1Damage", 4.0D, 0.0D, 1000.0D);
        ULT_HIT2_BASE = b.defineInRange("hit2BaseDamage", 16.0D, 0.0D, 1000.0D);
        ULT_HIT2_PERCENT = b.defineInRange("hit2PercentMaxHealth", 0.10D, 0.0D, 1.0D);
        ULT_HIT2_MAX = b.defineInRange("hit2MaxDamage", 32.0D, 0.0D, 10000.0D);
        ULT_HIT3 = b.comment("Damage of the mid-air punch that slams the target down.").defineInRange("hit3Damage", 8.0D, 0.0D, 1000.0D);
        ULT_CRASH_DAMAGE = b.defineInRange("crashDamage", 3.0D, 0.0D, 1000.0D);
        ULT_CRASH_BREAKS = b.comment("The final slam breaks soft blocks under the target (also needs the mobGriefing gamerule).")
                .define("crashBreaksBlocks", false);
        ULT_CRASH_HARDNESS = b.defineInRange("crashMaxHardness", 3.0D, 0.0D, 100.0D);
        ULT_TARGET_SEES = b.comment("A targeted player sees the full cinematic too.").define("targetSeesCinematic", true);
        ULT_ALLOW_CREATIVE = b.define("allowTargetCreative", false);
        ULT_ALLOW_AIRBORNE = b.define("allowAirborne", true);
        ULT_ENERGY = b.comment("Speed Force energy cost (energy bar max 100).").defineInRange("energyCost", 60.0D, 0.0D, 100.0D);
        b.pop();

        b.push("kinetic");
        KINETIC_ENABLED = b.comment("Kinetic charge: running stores energy in your fist, the next punch releases it.")
                .define("enabled", true);
        KINETIC_GAIN = b.comment("Charge gained per block run (charge max 100).").defineInRange("gainPerBlock", 1.6D, 0.0D, 50.0D);
        KINETIC_MIN = b.comment("Minimum charge a punch needs to release it.").defineInRange("minimumCharge", 25.0D, 0.0D, 100.0D);
        KINETIC_DAMAGE = b.comment("Extra punch damage at full charge (scales linearly with charge).")
                .defineInRange("maxBonusDamage", 10.0D, 0.0D, 1000.0D);
        KINETIC_KNOCKBACK = b.comment("Knockback speed at full charge (blocks/tick).").defineInRange("maxKnockback", 2.4D, 0.0D, 10.0D);
        b.pop();

        b.push("decoy");
        DECOY_ENABLED = b.comment("Afterimage decoy: leave a frozen afterimage that draws mobs, dash away.").define("enabled", true);
        DECOY_ENERGY = b.comment("Speed Force energy cost.").defineInRange("energyCost", 20.0D, 0.0D, 100.0D);
        DECOY_COOLDOWN = b.comment("Cooldown in seconds.").defineInRange("cooldownSeconds", 8, 0, 600);
        DECOY_LIFETIME = b.comment("How long the afterimage lasts (seconds).").defineInRange("lifetimeSeconds", 8, 1, 60);
        DECOY_DASH = b.comment("Dash distance (blocks).").defineInRange("dashDistance", 7.0D, 0.0D, 20.0D);
        DECOY_DISCHARGE = b.comment("Damage of the static discharge when the afterimage is struck (3.5 block radius).")
                .defineInRange("dischargeDamage", 4.0D, 0.0D, 1000.0D);
        DECOY_VANISH_TICKS = b.comment("Invisibility after the dash (ticks, 0 = none).").defineInRange("vanishTicks", 30, 0, 200);
        b.pop();

        b.push("rewind");
        REWIND_ENABLED = b.comment("Rewind: run back along your own path to where you were a few seconds ago.").define("enabled", true);
        REWIND_ENERGY = b.comment("Speed Force energy cost.").defineInRange("energyCost", 35.0D, 0.0D, 100.0D);
        REWIND_COOLDOWN = b.comment("Cooldown in seconds.").defineInRange("cooldownSeconds", 25, 0, 600);
        REWIND_SECONDS = b.comment("How far back in time (seconds).").defineInRange("seconds", 5.0D, 1.0D, 10.0D);
        REWIND_HEAL = b.comment("Health returns to what it was back then (never lower than now).").define("restoreHealth", true);
        b.pop();

        b.push("chunks");
        CHUNK_PRELOAD = b.comment("Load/generate chunks ahead of fast-running speedsters so they are ready before you reach them.")
                .define("preloadAhead", true);
        CHUNK_PRELOAD_SECONDS = b.comment("How far ahead to preload, in seconds of running at the current speed.")
                .defineInRange("preloadSeconds", 3.0D, 0.5D, 10.0D);
        CHUNK_PRELOAD_MAX_BLOCKS = b.comment("Upper limit of the preload distance in blocks (protects the server at very high speeds).")
                .defineInRange("preloadMaxBlocks", 384, 32, 2048);
        b.pop();

        b.push("phasing");
        PHASING = b.comment("Hold the phase key to vibrate through walls (floors still hold you).")
                .define("enabled", true);
        b.pop();

        SPEC = b.build();
    }

    private FlashServerConfig() {}
}
