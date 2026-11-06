package xyz.pyrehaven.whostoppedthemusic;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.sounds.MusicManager.MusicFrequency;
import net.minecraft.resources.Identifier;

import org.slf4j.LoggerFactory;
import xyz.pyrehaven.whostoppedthemusic.ui.CassetteWidget;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicScreen;

public final class WhoStoppedTheMusic implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Settings settings = Settings.load(FabricLoader.getInstance().getConfigDir().resolve("whostoppedthemusic.json"));
        KeyMapping open = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.whostoppedthemusic.open", InputConstants.KEY_M,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("whostoppedthemusic", "music"))));
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> settings.completeFirstRun(() -> {
            client.options.musicFrequency().set(MusicFrequency.CONSTANT);
            client.options.save();
        }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (open.consumeClick()) {
                if (client.level != null && client.gui.screen() == null) {
                    client.gui.setScreen(new MusicScreen(null, settings));
                }
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof PauseScreen pause && pause.showsPauseMenu()) {
                Screens.getWidgets(screen).add(new CassetteWidget(client, pause, settings));
            }
        });
        LoggerFactory.getLogger("whostoppedthemusic").info("Who Stopped The Music 1.2.0 loaded");
    }
}
