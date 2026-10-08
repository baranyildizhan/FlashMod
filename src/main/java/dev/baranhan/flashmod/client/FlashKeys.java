package dev.baranhan.flashmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class FlashKeys {
    public static final String CATEGORY = "key.categories.flashmod";

    public static final KeyMapping TOGGLE = key("key.flashmod.toggle", GLFW.GLFW_KEY_R);
    public static final KeyMapping LEVEL_UP = key("key.flashmod.level_up", GLFW.GLFW_KEY_UP);
    public static final KeyMapping LEVEL_DOWN = key("key.flashmod.level_down", GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping COLORS = key("key.flashmod.colors", GLFW.GLFW_KEY_G);
    /** Basili tut: titres ve duvarlarin icinden gec. */
    public static final KeyMapping PHASE = key("key.flashmod.phase", GLFW.GLFW_KEY_V);
    /** Basili tut: 3 blok yaricapli cemberde Mach 1'e kadar kos, girdap olustur. */
    public static final KeyMapping TORNADO = key("key.flashmod.tornado", GLFW.GLFW_KEY_Z);
    /** Ac/kapa: kus bakisi kamera. */
    public static final KeyMapping AERIAL = key("key.flashmod.aerial", GLFW.GLFW_KEY_H);
    /** Baktigin canliya sinematik vur-kac kombosu. */
    public static final KeyMapping BLITZ = key("key.flashmod.blitz", GLFW.GLFW_KEY_B);
    /** Ultimate: Dunya Turu Yumrugu (rehberdeki V bizde phasing oldugu icin N). */
    public static final KeyMapping ULTIMATE = key("key.flashmod.ultimate", GLFW.GLFW_KEY_N);
    /** Ultimate sinematigini (sadece gorsel) atla; varsayilan atanmamis. */
    public static final KeyMapping ULTIMATE_SKIP = key("key.flashmod.ultimate_skip", GLFW.GLFW_KEY_UNKNOWN);
    /** Kostum dolabi (flashskins klasorundeki skin'ler). */
    public static final KeyMapping WARDROBE = key("key.flashmod.wardrobe", GLFW.GLFW_KEY_J);
    /** Basili tut: elde simsek biriktir; birak: firlat. */
    public static final KeyMapping THROW = key("key.flashmod.throw", GLFW.GLFW_KEY_Y);
    /** Ac/kapa: Speed Force agir cekimi (butun oyun yavaslar, enerji harcar). */
    public static final KeyMapping SLOWMO = key("key.flashmod.slowmo", GLFW.GLFW_KEY_U);

    /** Zaman kalintisi: olduğun yerde donmus goruntun kalir, girdi yonune (yoksa saga) atilirsin. */
    public static final KeyMapping DECOY = key("key.flashmod.decoy", GLFW.GLFW_KEY_K);
    /** Geri sarma: son birkac saniyelik yolunu tersine kosarak o ana don. */
    public static final KeyMapping REWIND = key("key.flashmod.rewind", GLFW.GLFW_KEY_O);

    private FlashKeys() {}

    private static KeyMapping key(String name, int code) {
        return new KeyMapping(name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, CATEGORY);
    }
}
