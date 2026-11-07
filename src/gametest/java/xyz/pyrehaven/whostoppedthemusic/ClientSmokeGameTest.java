package xyz.pyrehaven.whostoppedthemusic;

import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.assertWidgetsFit;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.cassette;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.check;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.clickWidget;
import static xyz.pyrehaven.whostoppedthemusic.TransportGameTest.checkTransport;
import static xyz.pyrehaven.whostoppedthemusic.VanillaGameTest.checkCassette;
import static xyz.pyrehaven.whostoppedthemusic.VanillaGameTest.checkReboundKey;
import static xyz.pyrehaven.whostoppedthemusic.VanillaGameTest.checkSharedControls;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.sounds.MusicManager.MusicFrequency;
import org.slf4j.LoggerFactory;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicScreen;

public final class ClientSmokeGameTest implements FabricClientGameTest, ClientModInitializer {
    private static volatile boolean firstRunObserved;
    public static volatile CompletableFuture<Void> streamGate;
    public static volatile CompletableFuture<Void> streamReady;

    private static final Map<String, Consumer<ClientGameTestContext>> FEATURES = Map.ofEntries(
            Map.entry("transportOnly", TransportGameTest::run),
            Map.entry("basicOnly", BasicGameTest::checkBasic),
            Map.entry("basicUiOnly", BasicGameTest::checkBasicUi),
            Map.entry("discoveryOnly", DiscoveryGameTest::run),
            Map.entry("customOnly", CustomPlaybackGameTest::run),
            Map.entry("customUiOnly", CustomUiGameTest::run),
            Map.entry("biomeUiOnly", BiomeUiGameTest::run),
            Map.entry("toggleOnly", ToggleGameTest::run),
            Map.entry("previewOnly", PreviewGameTest::preview),
            Map.entry("controlsOnly", ControlsGameTest::checkUnifiedControls),
            Map.entry("artworkOnly", ControlsGameTest::checkUnifiedControls),
            Map.entry("menuOnly", MenuGameTest::checkMenuPlayers),
            Map.entry("ownSongsOnly", LocalSongsGameTest::checkOwnSongs),
            Map.entry("addSongsOnly", AddSongsGameTest::checkAddSongs));

    private static Consumer<ClientGameTestContext> selectedFeature() {
        var selected = FEATURES.entrySet().stream()
                .filter(entry -> Boolean.getBoolean("whostoppedthemusic." + entry.getKey())).toList();
        if (selected.size() > 1) throw new IllegalArgumentException("Select one client test feature");
        return selected.isEmpty() ? null : selected.getFirst().getValue();
    }

    @Override
    public void onInitializeClient() {
        if (selectedFeature() != null) return;
        // Entrypoint order is not guaranteed. Check after all startup callbacks,
        // before the first tick starts gametests and resets the vanilla options.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> client.schedule(() -> {
            check(client.options.musicFrequency().get() == MusicFrequency.CONSTANT, "fresh launch sets Constant before test defaults");
            firstRunObserved = true;
        }));
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitForScreen(TitleScreen.class);
        context.runOnClient(client -> {
            var location = WhoStoppedTheMusic.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            check(location.endsWith("/whostoppedthemusic-1.2.0.jar"), "client tests exercise the installable jar");
            DiscoveryGameTest.checkLiveNames(client);
        });
        var feature = selectedFeature();
        if (feature != null) {
            feature.accept(context);
            return;
        }
        check(firstRunObserved, "first-launch Constant was observed before gametest resets vanilla options");
        Path settingsFile = FabricLoader.getInstance().getConfigDir().resolve("whostoppedthemusic.json");
        check(Settings.load(settingsFile).firstRunDone(), "startup must finish first-run settings");
        try (var world = context.worldBuilder().create()) {
            context.waitForScreen(null);
            context.getInput().pressKey(InputConstants.KEY_M);
            context.waitForScreen(MusicScreen.class);
            context.takeScreenshot("whostoppedthemusic-vanilla");
            assertWidgetsFit(context);
            checkSharedControls(context, settingsFile);
            checkReboundKey(context);
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitForScreen(PauseScreen.class);
            checkCassette(context);
            checkTransport(context);
            context.getInput().resizeWindow(320, 240);
            context.waitTicks(5);
            assertWidgetsFit(context);
            clickWidget(context, context.computeOnClient(client -> cassette(client.gui.screen())));
            context.waitForScreen(MusicScreen.class);
            assertWidgetsFit(context);
            context.getInput().setCursorPos(0, 0);
            context.waitTicks(2);
            context.takeScreenshot("whostoppedthemusic-vanilla-small");
        }
        LoggerFactory.getLogger("whostoppedthemusic-test").info(
                "WHOSTOPPEDTHEMUSIC CLIENT PASS: first-run Constant, Vanilla UI, M/rebind, options/persistence, top cassette, live song/silence, Play/Next/Pause/Resume, music-only volume, Vanilla/Basic/Custom cycle, 320x240");
    }

}
