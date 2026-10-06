package dev.baranhan.flashmod.client;

import dev.baranhan.flashmod.client.gui.FlashColorScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

public final class ClientInit {
    private ClientInit() {}

    public static void init() {
        // Mods menusu -> Config butonu renk ekranini acar
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new FlashColorScreen(parent)));
    }
}
