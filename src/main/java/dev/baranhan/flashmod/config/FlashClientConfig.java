package dev.baranhan.flashmod.config;

import dev.baranhan.flashmod.speed.SpeedsterData;
import dev.baranhan.flashmod.util.ColorUtil;
import net.minecraftforge.common.ForgeConfigSpec;

public final class FlashClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<String> CORE_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> GLOW_COLOR;

    public static final ForgeConfigSpec.IntValue STRANDS;
    public static final ForgeConfigSpec.DoubleValue BLOOM;
    public static final ForgeConfigSpec.DoubleValue FOV_BOOST;
    public static final ForgeConfigSpec.BooleanValue SCREEN_EFFECTS;
    public static final ForgeConfigSpec.BooleanValue CAMERA_SHAKE;
    public static final ForgeConfigSpec.BooleanValue SHOW_HUD;
    public static final ForgeConfigSpec.BooleanValue POST_PROCESSING;
    public static final ForgeConfigSpec.DoubleValue POST_STRENGTH;
    public static final ForgeConfigSpec.DoubleValue AERIAL_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue ULT_CINEMATIC;
    public static final ForgeConfigSpec.DoubleValue ULT_SHAKE;
    public static final ForgeConfigSpec.BooleanValue ULT_REDUCE_FLASHES;
    public static final ForgeConfigSpec.BooleanValue ULT_LETTERBOX;
    public static final ForgeConfigSpec.BooleanValue ULT_COMBO_TEXT;
    public static final ForgeConfigSpec.EnumValue<VfxQuality> ULT_QUALITY;
    public static final ForgeConfigSpec.BooleanValue ULT_HIDE_CHAT;
    public static final ForgeConfigSpec.BooleanValue ULT_DEBUG;

    public enum VfxQuality {
        LOW(0.3F), MEDIUM(0.6F), HIGH(1.0F);
        public final float mul;
        VfxQuality(float mul) { this.mul = mul; }
    }

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("colors");
        CORE_COLOR = b.comment("Inner (core) lightning color, hex. In-game: G key.")
                .define("core", ColorUtil.toHex(SpeedsterData.DEFAULT_CORE), ColorUtil::isHex);
        GLOW_COLOR = b.comment("Outer glow (bloom) color, hex.")
                .define("glow", ColorUtil.toHex(SpeedsterData.DEFAULT_GLOW), ColorUtil::isHex);
        b.pop();

        b.push("visuals");
        STRANDS = b.comment("Lightning strands per player (performance vs. looks).")
                .defineInRange("strands", 8, 2, 16);
        BLOOM = b.comment("Fake bloom strength (no shaders needed). 0 = off.")
                .defineInRange("bloom", 1.0D, 0.0D, 2.0D);
        FOV_BOOST = b.comment("FOV boost at max speed (replaces vanilla's FOV explosion).")
                .defineInRange("fovBoost", 0.22D, 0.0D, 0.6D);
        SCREEN_EFFECTS = b.comment("Speed lines and edge glow in first person.")
                .define("screenEffects", true);
        CAMERA_SHAKE = b.comment("Camera shake at high speed and on sonic booms.")
                .define("cameraShake", true);
        SHOW_HUD = b.comment("Level / km/h display in the top-left corner.")
                .define("showHud", true);
        POST_PROCESSING = b.comment("Speed post-processing: edge speed blur, chromatic aberration, sonic boom shake, phasing ghosting.",
                        "Built-in core shader, works without shader packs. Turn off if it conflicts with Oculus/Iris.")
                .define("postProcessing", true);
        POST_STRENGTH = b.comment("Post-processing strength.")
                .defineInRange("postStrength", 1.0D, 0.0D, 2.0D);
        AERIAL_DISTANCE = b.comment("Aerial view camera distance from the player (blocks).")
                .defineInRange("aerialDistance", 48.0D, 10.0D, 160.0D);
        b.pop();

        b.push("ultimate");
        ULT_CINEMATIC = b.comment("Full cinematic for your own ultimate (off = world-only spectator view).")
                .define("cinematicEnabled", true);
        ULT_SHAKE = b.comment("Camera shake multiplier (0 = off).").defineInRange("cameraShakeMultiplier", 1.0D, 0.0D, 3.0D);
        ULT_REDUCE_FLASHES = b.comment("Photosensitivity mode: dimmer flashes, slower flicker, fainter radial lines.")
                .define("reduceFlashes", false);
        ULT_LETTERBOX = b.define("letterbox", false);
        ULT_COMBO_TEXT = b.define("comboText", false);
        ULT_QUALITY = b.defineEnum("vfxQuality", VfxQuality.HIGH);
        ULT_HIDE_CHAT = b.define("hideChatDuringCinematic", false);
        ULT_DEBUG = b.comment("Debug keys (numpad) for the cinematic preview (/flashult preview).").define("debug", false);
        b.pop();

        SPEC = b.build();
    }

    private FlashClientConfig() {}

    public static int core() { return ColorUtil.parseHex(CORE_COLOR.get(), SpeedsterData.DEFAULT_CORE); }
    public static int glow() { return ColorUtil.parseHex(GLOW_COLOR.get(), SpeedsterData.DEFAULT_GLOW); }
}
