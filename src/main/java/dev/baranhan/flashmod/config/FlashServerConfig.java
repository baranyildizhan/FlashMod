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
    public static final ForgeConfigSpec.DoubleValue ULT_LAUNCH_H;
    public static final ForgeConfigSpec.DoubleValue ULT_LAUNCH_V;
    public static final ForgeConfigSpec.DoubleValue ULT_CRASH_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue ULT_CRASH_BREAKS;
    public static final ForgeConfigSpec.DoubleValue ULT_CRASH_HARDNESS;
    public static final ForgeConfigSpec.BooleanValue ULT_TARGET_SEES;
    public static final ForgeConfigSpec.BooleanValue ULT_ALLOW_CREATIVE;
    public static final ForgeConfigSpec.BooleanValue ULT_ALLOW_AIRBORNE;
    public static final ForgeConfigSpec.DoubleValue ULT_ENERGY;
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
        ULT_LAUNCH_H = b.comment("Launch speed, blocks/tick.").defineInRange("launchHorizontal", 3.2D, 0.0D, 20.0D);
        ULT_LAUNCH_V = b.defineInRange("launchVertical", 0.55D, 0.0D, 5.0D);
        ULT_CRASH_DAMAGE = b.defineInRange("crashDamage", 3.0D, 0.0D, 1000.0D);
        ULT_CRASH_BREAKS = b.comment("Crash breaks soft blocks (also needs the mobGriefing gamerule).")
                .define("crashBreaksBlocks", false);
        ULT_CRASH_HARDNESS = b.defineInRange("crashMaxHardness", 3.0D, 0.0D, 100.0D);
        ULT_TARGET_SEES = b.comment("A targeted player sees the full cinematic too.").define("targetSeesCinematic", true);
        ULT_ALLOW_CREATIVE = b.define("allowTargetCreative", false);
        ULT_ALLOW_AIRBORNE = b.define("allowAirborne", true);
        ULT_ENERGY = b.comment("Speed Force energy cost (energy bar max 100).").defineInRange("energyCost", 60.0D, 0.0D, 100.0D);
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
