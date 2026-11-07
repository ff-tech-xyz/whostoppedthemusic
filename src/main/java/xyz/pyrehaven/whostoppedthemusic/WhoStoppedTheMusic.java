package xyz.pyrehaven.whostoppedthemusic;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.sounds.MusicManager.MusicFrequency;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.TrackLibrary;
import xyz.pyrehaven.whostoppedthemusic.ui.CassetteWidget;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicScreen;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;
import xyz.pyrehaven.whostoppedthemusic.ui.TransportButton;

public final class WhoStoppedTheMusic implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("whostoppedthemusic");
    private static Settings settings;
    public static final TrackLibrary LIBRARY =
            new TrackLibrary();

    public static Settings settings() { return settings; }

    @Override
    public void onInitializeClient() {
        settings = Settings.load(FabricLoader.getInstance().getConfigDir().resolve("whostoppedthemusic.json"));
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
            if (screen instanceof TitleScreen) {
                ScreenKeyboardEvents.allowKeyPress(screen).register((current, event) -> {
                    if (!open.matches(event)) return true;
                    client.gui.setScreen(new MusicScreen(current, settings));
                    return false;
                });
            }
            if (screen instanceof TitleScreen || screen instanceof PauseScreen pause && pause.showsPauseMenu()) {
                var cassette = new CassetteWidget(client, screen, settings);
                Screens.getWidgets(screen).add(cassette);
                var next = new TransportButton(cassette.getRight() - Theme.PAUSE_NEXT_WIDTH,
                        cassette.getY() - Theme.CONTROL_HEIGHT - Theme.PAUSE_NEXT_GAP,
                        Theme.PAUSE_NEXT_WIDTH, TransportButton.Action.NEXT,
                        () -> MusicDirector.get(client).next());
                Screens.getWidgets(screen).add(next);
                ScreenEvents.beforeExtract(screen).register((current, graphics, mouseX, mouseY, delta) ->
                        next.active = MusicDirector.get(client).canNext());
            }
        });
        LOGGER.info("Who Stopped The Music 1.2.0 loaded");
    }
}
