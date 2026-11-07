package xyz.pyrehaven.whostoppedthemusic;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.MusicToastDisplayState;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.sounds.MusicManager.MusicFrequency;
import net.minecraft.sounds.Musics;
import net.minecraft.sounds.SoundSource;
import org.slf4j.LoggerFactory;
import xyz.pyrehaven.whostoppedthemusic.mixin.MusicManagerAccessor;
import xyz.pyrehaven.whostoppedthemusic.ui.CassetteWidget;
import xyz.pyrehaven.whostoppedthemusic.ui.CassetteArtwork;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicScreen;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicVolumeSlider;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;
import net.minecraft.client.gui.components.toasts.NowPlayingToast;
import xyz.pyrehaven.whostoppedthemusic.ui.TransportButton;
import net.minecraft.client.gui.components.toasts.Toast;
import xyz.pyrehaven.whostoppedthemusic.ui.widget.TrackList;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.Track;
import xyz.pyrehaven.whostoppedthemusic.music.TrackNames;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.mixin.SoundManagerAccessor;
import xyz.pyrehaven.whostoppedthemusic.mixin.SoundEngineAccessor;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.CompletableFuture;

public final class ClientSmokeGameTest implements FabricClientGameTest, ClientModInitializer {
    private static volatile boolean firstRunObserved;
    public static volatile CompletableFuture<Void> streamGate;
    public static volatile CompletableFuture<Void> streamReady;

    @Override
    public void onInitializeClient() {
        if (Boolean.getBoolean("whostoppedthemusic.transportOnly") || Boolean.getBoolean("whostoppedthemusic.basicOnly")
                || Boolean.getBoolean("whostoppedthemusic.basicUiOnly") || Boolean.getBoolean("whostoppedthemusic.discoveryOnly")
                || Boolean.getBoolean("whostoppedthemusic.controlsOnly") || Boolean.getBoolean("whostoppedthemusic.artworkOnly")
                || Boolean.getBoolean("whostoppedthemusic.ownSongsOnly") || Boolean.getBoolean("whostoppedthemusic.addSongsOnly")
                || Boolean.getBoolean("whostoppedthemusic.customOnly") || Boolean.getBoolean("whostoppedthemusic.customUiOnly")
                || Boolean.getBoolean("whostoppedthemusic.previewOnly") || Boolean.getBoolean("whostoppedthemusic.menuOnly")
                || Boolean.getBoolean("whostoppedthemusic.biomeUiOnly")) {
            return;
        }
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
        context.runOnClient(DiscoveryGameTest::checkLiveNames);
        if (Boolean.getBoolean("whostoppedthemusic.biomeUiOnly")) {
            BiomeUiGameTest.run(context);
            return;
        }
        if (Boolean.getBoolean("whostoppedthemusic.menuOnly")) {
            checkMenuPlayers(context);
            return;
        }
        if (Boolean.getBoolean("whostoppedthemusic.previewOnly")) {
            CustomUiGameTest.preview(context);
            return;
        }
        if (Boolean.getBoolean("whostoppedthemusic.customUiOnly")) {
            CustomUiGameTest.run(context);
            return;
        }
        if (Boolean.getBoolean("whostoppedthemusic.customOnly")) {
            CustomPlaybackGameTest.run(context);
            return;
        }
        if (Boolean.getBoolean("whostoppedthemusic.addSongsOnly")) {
            checkAddSongs(context);
            return;
        }
        if (Boolean.getBoolean("whostoppedthemusic.ownSongsOnly")) {
            checkOwnSongs(context);
            return;
        }
        if (Boolean.getBoolean("whostoppedthemusic.controlsOnly") || Boolean.getBoolean("whostoppedthemusic.artworkOnly")) {
            checkUnifiedControls(context);
            return;
        }
        if (Boolean.getBoolean("whostoppedthemusic.discoveryOnly")) {
            DiscoveryGameTest.run(context);
            return;
        }
        if (Boolean.getBoolean("whostoppedthemusic.basicUiOnly")) {
            checkBasicUi(context);
            return;
        }
        if (Boolean.getBoolean("whostoppedthemusic.basicOnly")) {
            checkBasic(context);
            return;
        }
        if (Boolean.getBoolean("whostoppedthemusic.transportOnly")) {
            try (var world = context.worldBuilder().create()) {
                context.waitForScreen(null);
                context.runOnClient(client -> {
                    client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.7);
                    client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.7);
                    client.getMusicManager().stopPlaying();
                });
                context.getInput().pressKey(InputConstants.KEY_M);
                context.waitForScreen(MusicScreen.class);
                context.runOnClient(client -> {
                    var controls = Screens.getWidgets(client.gui.screen());
                    var play = controls.get(4);
                    check(play.active, "Play must be available when vanilla is silent");
                    check(controls.stream().noneMatch(w -> w.getMessage().getString().equals("<")),
                            "Vanilla has no previous-track button");
                });
                clickWidget(context, widget(context, 4));
                context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
                context.runOnClient(client -> check(MusicDirector.get(client).active(), "Play starts vanilla music"));
                context.clickScreenButton("gui.done");
                context.waitForScreen(null);
                context.getInput().pressKey(InputConstants.KEY_ESCAPE);
                context.waitForScreen(PauseScreen.class);
                checkTransport(context);
            }
            LoggerFactory.getLogger("whostoppedthemusic-test").info("VANILLA TRANSPORT PASS");
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

    private static void checkMenuPlayers(ClientGameTestContext context) {
        var title = context.computeOnClient(client -> client.gui.screen());
        for (int width : new int[]{44, 94, 124, 187, 190, 300}) {
            var size = CassetteArtwork.size(width, 100);
            check(size.equals(CassetteArtwork.size(size.width(), size.height())), "fitting a tape never shrinks it twice");
        }
        context.runOnClient(client -> {
            WhoStoppedTheMusic.settings().setMode(Mode.BASIC);
            MusicDirector.get(client).syncMode();
            client.options.musicToast().set(MusicToastDisplayState.NEVER);
            client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.7);
            client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.7);
        });
        context.getInput().pressKey(InputConstants.KEY_M);
        context.waitForScreen(MusicScreen.class);
        context.clickScreenButton("gui.done");
        context.runOnClient(client -> check(client.gui.screen() == title, "title M Done preserves exact parent"));
        context.runOnClient(client -> {
            KeyMapping.get("key.whostoppedthemusic.open").setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_N));
            KeyMapping.resetMapping();
        });
        context.getInput().pressKey(InputConstants.KEY_M);
        context.runOnClient(client -> check(client.gui.screen() == title, "old title binding ignored"));
        context.getInput().pressKey(InputConstants.KEY_N);
        context.waitForScreen(MusicScreen.class);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.runOnClient(client -> {
            check(client.gui.screen() == title, "rebound title key Escape preserves exact parent");
            KeyMapping.get("key.whostoppedthemusic.open").setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_M));
            KeyMapping.resetMapping();
        });
        for (int[] window : new int[][]{{960, 720, 2}, {320, 240, 1}, {1280, 960, 4}}) {
            context.getInput().resizeWindow(window[0], window[1]);
            context.runOnClient(client -> client.options.guiScale().set(window[2]));
            context.waitTicks(3);
            for (boolean pause : new boolean[]{false, true}) {
                String name = "menu-" + (pause ? "pause-" : "title-") + window[0];
                try (var world = pause ? context.worldBuilder().create() : null) {
                    if (pause) context.waitForScreen(null);
                    context.runOnClient(client -> {
                        client.gui.setScreen(pause ? new PauseScreen(true) : title);
                        var director = MusicDirector.get(client);
                        director.play(WhoStoppedTheMusic.LIBRARY.tracks().stream()
                                .filter(t -> t.id().equals("minecraft:records/pigstep")).findFirst().orElseThrow());
                    });
                    context.waitFor(client -> MusicDirector.get(client).advancing());
                    context.waitTicks(3);
                    assertMenuFit(context);
                    checkMenuNotes(context, name + "-playing", true);
                    checkPlayerReels(context, name + "-reels", true, true);
                    context.runOnClient(client -> MusicDirector.get(client).togglePlayback());
                    context.waitFor(client -> MusicDirector.get(client).paused() && !MusicDirector.get(client).advancing());
                    checkMenuNotes(context, name + "-paused", false);
                    checkPlayerReels(context, name + "-frozen", false, true);
                    var parent = context.computeOnClient(client -> client.gui.screen());
                    clickWidget(context, context.computeOnClient(client -> cassette(client.gui.screen())));
                    context.waitForScreen(MusicScreen.class);
                    assertWidgetsFit(context);
                    context.clickScreenButton("gui.done");
                    context.runOnClient(client -> check(client.gui.screen() == parent, "cassette Done returns to exact menu"));
                    context.runOnClient(client -> MusicDirector.get(client).stop());
                    context.waitFor(client -> !MusicDirector.get(client).active());
                    checkMenuNotes(context, name + "-stopped", false);
                    checkEmptyPlayer(context, name + "-empty", true);
                }
            }
            for (Mode mode : Mode.values()) {
                context.runOnClient(client -> {
                    WhoStoppedTheMusic.settings().setMode(mode);
                    MusicDirector.get(client).syncMode();
                    client.gui.setScreen(new MusicScreen(title, WhoStoppedTheMusic.settings()));
                    MusicDirector.get(client).next();
                });
                context.waitFor(client -> MusicDirector.get(client).advancing());
                context.waitTicks(2);
                assertWidgetsFit(context);
                var tape = playerTape(context, false);
                check(Math.abs(tape.getWidth() * Theme.CASSETTE_HEIGHT - tape.getHeight() * Theme.CASSETTE_WIDTH)
                        <= Theme.CASSETTE_WIDTH, "shared aspect ratio in " + mode);
                context.takeScreenshot("menu-player-" + mode + "-" + window[0]);
            }
            context.runOnClient(client -> {
                WhoStoppedTheMusic.settings().setMode(Mode.BASIC);
                MusicDirector.get(client).syncMode();
                client.gui.setScreen(title);
            });
        }
        LoggerFactory.getLogger("whostoppedthemusic-test").info(
                "MENU PLAYERS PASS: title M/rebind/Done/Escape, same-parent tape navigation, bottom-left nonoverlap, shared aspect, actual-playing notes/reels and paused/stopped suppression at scales 1/2/4");
    }

    private static void assertMenuFit(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var screen = client.gui.screen();
            var tape = cassette(screen);
            check(tape.getX() == Theme.MARGIN && tape.getBottom() == screen.height - Theme.MENU_FOOTER,
                    "bottom-left menu placement");
            check(tape.getHeight() > 34 || screen.width <= 320, "menu tape enlarged when space permits");
            for (var widget : Screens.getWidgets(screen)) {
                if (widget == tape || !widget.visible) continue;
                check(widget.getRight() <= tape.getX() || widget.getX() >= tape.getRight()
                                || widget.getBottom() <= tape.getY() || widget.getY() >= tape.getBottom(),
                        "menu cassette does not overlap " + widget.getMessage().getString());
            }
        });
    }

    static void checkMenuNotes(ClientGameTestContext context, String name, boolean playing) {
        context.getInput().setCursorPos(0, 0);
        context.waitTicks(3);
        var tape = context.computeOnClient(client -> cassette(client.gui.screen()));
        int scale = context.computeOnClient(client -> (int) client.getWindow().getGuiScale());
        Path first = context.takeScreenshot(name + "-notes-a");
        context.waitTicks(5);
        Path second = context.takeScreenshot(name + "-notes-b");
        try {
            var a = javax.imageio.ImageIO.read(first.toFile());
            var b = javax.imageio.ImageIO.read(second.toFile());
            int colored = 0;
            int moved = 0;
            for (int y = (tape.getY() - Theme.NOTE_RISE - Theme.GAP * 2) * scale; y < tape.getY() * scale; y++) {
                for (int x = tape.getX() * scale; x < (tape.getRight() - Theme.PAUSE_NEXT_WIDTH - Theme.GAP) * scale; x++) {
                    int ca = a.getRGB(x, y);
                    int cb = b.getRGB(x, y);
                    if (ca == Theme.GLOW || ca == Theme.ORANGE) {
                        colored++;
                        if (ca != cb) moved++;
                    }
                }
            }
            check(playing ? colored > 0 && moved > 0 : colored == 0,
                    name + " note pixels=" + colored + " moved=" + moved);
        } catch (java.io.IOException exception) { throw new AssertionError(exception); }
    }

    private static void checkAddSongs(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 960);
        context.runOnClient(client -> client.options.guiScale().set(4));
        var parent = context.computeOnClient(client -> client.gui.screen());
        Path game = context.computeOnClient(client -> client.gameDirectory.toPath());
        Path source = game.resolve("add-songs-inputs");
        Path folder = game.resolve("music");
        try {
            java.nio.file.Files.createDirectories(source);
            java.nio.file.Files.createDirectories(folder);
            try (var files = java.nio.file.Files.list(folder)) {
                for (var file : files.filter(p -> p.getFileName().toString().startsWith("Add-check")).toList()) java.nio.file.Files.delete(file);
            }
            for (var entry : java.util.Map.of("stereo.mp3", "Add-check.mp3", "mono.ogg", "Add-check.ogg").entrySet()) {
                try (var input = ClientSmokeGameTest.class.getResourceAsStream("/audio/" + entry.getKey())) {
                    java.nio.file.Files.copy(input, source.resolve(entry.getValue()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            java.nio.file.Files.writeString(source.resolve("Add-check.png"), "not music");
        } catch (java.io.IOException error) { throw new AssertionError(error); }
        context.runOnClient(client -> {
            WhoStoppedTheMusic.settings().setMode(Mode.BASIC);
            MusicDirector.get(client).syncMode();
            MusicDirector.get(client).stop();
            client.gui.setScreen(new MusicScreen(parent, WhoStoppedTheMusic.settings()));
            Screens.getWidgets(client.gui.screen()).stream().filter(EditBox.class::isInstance)
                    .map(EditBox.class::cast).findFirst().orElseThrow().setValue("Add-check");
        });
        var basic = context.computeOnClient(client -> client.gui.screen());
        context.clickScreenButton("whostoppedthemusic.add_music");
        context.waitForScreen(xyz.pyrehaven.whostoppedthemusic.ui.AddSongsScreen.class);
        context.waitFor(client -> namedWidgetOnClient(client.gui.screen(), "Done").active);
        context.runOnClient(client -> {
            var location = xyz.pyrehaven.whostoppedthemusic.ui.AddSongsScreen.class.getProtectionDomain().getCodeSource().getLocation().toString();
            check(location.endsWith("whostoppedthemusic-1.2.0.jar"), "Add music screen comes from installable jar");
            client.gui.screen().onFilesDrop(java.util.List.of(source.resolve("Add-check.mp3"), source.resolve("Add-check.ogg")));
        });
        context.waitForScreen(net.minecraft.client.gui.screens.ConfirmScreen.class);
        check(!java.nio.file.Files.exists(folder.resolve("Add-check.mp3")), "drop waits for confirmation");
        assertWidgetsFit(context);
        context.runOnClient(client -> check(client.getWindow().getGuiScale() == 4, "actual import GUI scale4"));
        context.takeScreenshot("add-songs-confirm");
        context.clickScreenButton("gui.no");
        context.waitForScreen(xyz.pyrehaven.whostoppedthemusic.ui.AddSongsScreen.class);
        check(!java.nio.file.Files.exists(folder.resolve("Add-check.mp3")), "No leaves folder unchanged");
        context.runOnClient(client -> client.gui.screen().onFilesDrop(java.util.List.of(source.resolve("Add-check.png"))));
        context.waitForScreen(net.minecraft.client.gui.screens.AlertScreen.class);
        context.runOnClient(client -> check(client.gui.screen().getNarrationMessage().getString().contains("Only regular MP3"), "invalid extension explains rejection"));
        assertWidgetsFit(context);
        context.takeScreenshot("add-songs-invalid-scale4");
        context.clickScreenButton("gui.back");
        context.waitForScreen(xyz.pyrehaven.whostoppedthemusic.ui.AddSongsScreen.class);
        context.runOnClient(client -> client.gui.screen().onFilesDrop(java.util.List.of(source.resolve("Add-check.mp3"), source.resolve("Add-check.ogg"))));
        context.waitForScreen(net.minecraft.client.gui.screens.ConfirmScreen.class);
        context.clickScreenButton("gui.yes");
        context.waitForScreen(net.minecraft.client.gui.screens.AlertScreen.class);
        try {
            check(java.nio.file.Files.mismatch(source.resolve("Add-check.mp3"), folder.resolve("Add-check.mp3")) == -1,
                    "confirmed MP3 copy matches original bytes");
            check(java.nio.file.Files.mismatch(source.resolve("Add-check.ogg"), folder.resolve("Add-check.ogg")) == -1,
                    "confirmed OGG copy matches original bytes");
        } catch (java.io.IOException error) { throw new AssertionError(error); }
        var imported = context.computeOnClient(client -> WhoStoppedTheMusic.LIBRARY.tracks().stream()
                .filter(t -> t.id().startsWith("custom:Add-check")).toList());
        check(imported.size() == 2 && imported.stream().allMatch(Track::playable), "import refresh probes both files");
        assertWidgetsFit(context);
        context.takeScreenshot("add-songs-success-scale4");
        for (var track : imported) {
            var sound = context.computeOnClient(client -> {
                MusicDirector.get(client).play(track);
                return MusicDirector.get(client).current();
            });
            context.waitFor(client -> channelState(client, sound, true).join());
            context.runOnClient(client -> MusicDirector.get(client).stop());
        }
        context.clickScreenButton("gui.back");
        context.waitForScreen(xyz.pyrehaven.whostoppedthemusic.ui.AddSongsScreen.class);
        context.runOnClient(client -> client.gui.screen().onFilesDrop(java.util.List.of(source.resolve("Add-check.mp3"))));
        context.waitForScreen(net.minecraft.client.gui.screens.ConfirmScreen.class);
        context.clickScreenButton("gui.yes");
        context.waitForScreen(net.minecraft.client.gui.screens.AlertScreen.class);
        context.runOnClient(client -> check(client.gui.screen().getNarrationMessage().getString().contains("Add-check (2).mp3"), "numbered duplicate reported"));
        try {
            check(java.nio.file.Files.mismatch(source.resolve("Add-check.mp3"), folder.resolve("Add-check (2).mp3")) == -1, "duplicate gets independent exact copy");
        } catch (java.io.IOException error) { throw new AssertionError(error); }
        context.clickScreenButton("gui.back");
        context.waitForScreen(xyz.pyrehaven.whostoppedthemusic.ui.AddSongsScreen.class);
        context.runOnClient(client -> Screens.getWidgets(client.gui.screen()).stream().filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).findFirst().orElseThrow().setValue("Add-check"));
        context.waitTicks(3);
        assertWidgetsFit(context);
        context.takeScreenshot("add-songs-list");
        context.runOnClient(client -> searchBox(client.gui.screen()).setValue("Add-check (2)"));
        context.waitTicks(3);
        clickWidget(context, context.computeOnClient(client -> (AbstractWidget) trackList(client.gui.screen()).children().getFirst().children().getLast()));
        context.waitForScreen(net.minecraft.client.gui.screens.ConfirmScreen.class);
        context.runOnClient(client -> check(client.gui.screen().getNarrationMessage().getString().contains("Add-check (2).mp3"), "Remove names exact file before deletion"));
        assertWidgetsFit(context);
        context.takeScreenshot("add-songs-remove-scale4");
        context.clickScreenButton("gui.cancel");
        context.waitForScreen(xyz.pyrehaven.whostoppedthemusic.ui.AddSongsScreen.class);
        check(java.nio.file.Files.exists(folder.resolve("Add-check (2).mp3")), "cancel keeps duplicate");
        context.waitTicks(3);
        clickWidget(context, context.computeOnClient(client -> (AbstractWidget) trackList(client.gui.screen()).children().getFirst().children().getLast()));
        context.waitForScreen(net.minecraft.client.gui.screens.ConfirmScreen.class);
        context.clickScreenButton("whostoppedthemusic.remove");
        context.waitForScreen(net.minecraft.client.gui.screens.AlertScreen.class);
        check(!java.nio.file.Files.exists(folder.resolve("Add-check (2).mp3")), "confirmed Remove deletes the named duplicate");
        check(java.nio.file.Files.exists(source.resolve("Add-check.mp3")) && java.nio.file.Files.exists(folder.resolve("Add-check.mp3")), "source and first copy survive removal");
        context.runOnClient(client -> check(WhoStoppedTheMusic.LIBRARY.tracks().stream().noneMatch(t -> t.id().equals("custom:Add-check (2).mp3")), "removal refreshes library"));
        context.clickScreenButton("gui.back");
        context.waitForScreen(xyz.pyrehaven.whostoppedthemusic.ui.AddSongsScreen.class);
        context.clickScreenButton("whostoppedthemusic.open_music_folder");
        context.runOnClient(client -> searchBox(client.gui.screen()).setValue("Add-check"));
        context.getInput().resizeWindow(320, 240);
        context.waitTicks(3);
        assertWidgetsFit(context);
        context.takeScreenshot("add-songs-small");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(MusicScreen.class);
        context.runOnClient(client -> {
            check(client.gui.screen() == basic, "returns to same Basic screen");
            var query = Screens.getWidgets(client.gui.screen()).stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
            check(query.getValue().equals("Add-check"), "parent search retained");
            check(trackList(client.gui.screen()).children().size() == 2, "parent library view refreshed after file operations");
        });
        context.clickScreenButton("whostoppedthemusic.add_music");
        context.waitForScreen(xyz.pyrehaven.whostoppedthemusic.ui.AddSongsScreen.class);
        context.waitFor(client -> namedWidgetOnClient(client.gui.screen(), "Done").active);
        context.clickScreenButton("gui.done");
        context.waitForScreen(MusicScreen.class);
        context.clickScreenButton("gui.done");
        context.waitForScreen(TitleScreen.class);
        LoggerFactory.getLogger("whostoppedthemusic-test").info("ADD SONGS PASS: packaged shared screen, import No/Yes, exact MP3/OGG copies, bad extension, numbered duplicate, cancel/delete, source retention, library refresh, search/return/Done/Escape and 320x240");
    }

    private static AbstractWidget namedWidgetOnClient(net.minecraft.client.gui.screens.Screen screen, String name) {
        return Screens.getWidgets(screen).stream().filter(w -> w.getMessage().getString().equals(name)).findFirst().orElseThrow();
    }

    private static void checkOwnSongs(ClientGameTestContext context) {
        context.getInput().resizeWindow(960, 720);
        Path folder = context.computeOnClient(client -> client.gameDirectory.toPath().resolve("music"));
        var parent = context.computeOnClient(client -> client.gui.screen());
        try {
            java.nio.file.Files.createDirectories(folder);
            for (var entry : java.util.Map.of("stereo.mp3", "Artist - Signal.MP3", "mono.ogg", "Plain.ogg").entrySet()) {
                try (var input = ClientSmokeGameTest.class.getResourceAsStream("/audio/" + entry.getKey())) {
                    java.nio.file.Files.copy(input, folder.resolve(entry.getValue()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            java.nio.file.Files.writeString(folder.resolve("Broken.mp3"), "This is a text file, not an MP3");
            java.nio.file.Files.writeString(folder.resolve("Broken.ogg"), "This is not OGG either");
        } catch (java.io.IOException error) { throw new AssertionError(error); }
        var refresh = context.computeOnClient(client -> WhoStoppedTheMusic.LIBRARY.refreshSongs());
        context.waitFor(client -> refresh.isDone());
        refresh.join();
        var songs = context.computeOnClient(client -> WhoStoppedTheMusic.LIBRARY.tracks().stream().filter(t -> t.local() != null).toList());
        var mp3 = songs.stream().filter(t -> t.id().equals("custom:Artist - Signal.MP3")).findFirst().orElseThrow();
        var ogg = songs.stream().filter(t -> t.id().equals("custom:Plain.ogg")).findFirst().orElseThrow();
        var broken = songs.stream().filter(t -> t.id().equals("custom:Broken.mp3")).findFirst().orElseThrow();
        check(mp3.playable() && ogg.playable() && !broken.playable(), "probe distinguishes real MP3/OGG from renamed text");
        check(mp3.metadata().title().getString().equals("Artist - Signal") && mp3.metadata().artist().getString().isEmpty(),
                "file-name title remains literal, unknown artist blank");
        context.runOnClient(client -> {
            client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.7);
            client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.7);
            client.options.musicFrequency().set(MusicFrequency.CONSTANT);
            WhoStoppedTheMusic.settings().setMode(Mode.BASIC);
            WhoStoppedTheMusic.settings().setShuffle(false);
            WhoStoppedTheMusic.settings().setJukebox(JukeboxSetting.PLAY_OVER);
            WhoStoppedTheMusic.settings().setAllTicked(WhoStoppedTheMusic.LIBRARY.tracks().stream().map(Track::id).toList(), false);
            WhoStoppedTheMusic.settings().setTicked(mp3.id(), true);
            WhoStoppedTheMusic.settings().setTicked(ogg.id(), true);
            WhoStoppedTheMusic.settings().setTicked(broken.id(), true);
            var director = MusicDirector.get(client);
            director.syncMode();
            check(director.selected().size() == 2, "unplayable selections are retained in settings but skipped by deck");
            director.play(mp3);
        });
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        var first = context.computeOnClient(client -> MusicDirector.get(client).current());
        context.runOnClient(client -> {
            var director = MusicDirector.get(client);
            check(first.getSource() == SoundSource.MUSIC && director.playing(mp3), "MP3 uses manager-owned Music channel and source identity");
            check(director.metadata().title().getString().equals("Artist - Signal"), "cassette receives file metadata");
            director.togglePlayback();
        });
        context.waitFor(client -> channelState(client, first, false).join());
        context.waitTicks(5);
        context.runOnClient(client -> MusicDirector.get(client).togglePlayback());
        context.waitFor(client -> channelState(client, first, true).join());
        context.runOnClient(client -> {
            check(MusicDirector.get(client).current() == first, "MP3 resume retains stream and native source");
            client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0);
        });
        context.waitFor(client -> channelGain(client, first).join() == 0.0f);
        context.runOnClient(client -> client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.7));
        context.waitFor(client -> channelGain(client, first).join() > 0.0f);
        context.waitFor(client -> MusicDirector.get(client).playing(ogg)
                && channelStopped(client, first) && channelState(client, MusicDirector.get(client).current(), true).join());
        var second = context.computeOnClient(client -> MusicDirector.get(client).current());
        context.runOnClient(client -> check(second.getSource() == SoundSource.MUSIC, "OGG uses same Music audio path"));
        context.waitFor(client -> MusicDirector.get(client).playing(mp3) && channelStopped(client, second)
                && channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> {
            MusicDirector.get(client).stop();
            client.gui.setScreen(new MusicScreen(parent, WhoStoppedTheMusic.settings()));
        });
        context.waitTicks(5);
        context.runOnClient(client -> Screens.getWidgets(client.gui.screen()).stream().filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).findFirst().orElseThrow().setValue("Broken"));
        context.waitTicks(3);
        context.runOnClient(client -> {
            var list = trackList(client.gui.screen());
            var invalidRows = list.children().stream().filter(row -> ((AbstractWidget) row.children().get(1)).getMessage()
                    .getString().lines().findFirst().orElse("").equals("Broken")).toList();
            check(invalidRows.size() == 2, "both invalid formats remain visible beside valid matching soundtrack songs");
            for (var row : invalidRows) {
                var play = (AbstractWidget) row.children().get(2);
                check(!play.active && play.getMessage().getString().startsWith("Can't play:"), "invalid row truthfully disabled/narrated");
            }
        });
        context.takeScreenshot("own-songs-cant-play");
        context.runOnClient(client -> Screens.getWidgets(client.gui.screen()).stream().filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).findFirst().orElseThrow().setValue("Artist - Signal"));
        context.waitTicks(3);
        context.takeScreenshot("own-songs-filename");
        context.getInput().resizeWindow(320, 240);
        context.waitTicks(3);
        assertWidgetsFit(context);
        context.runOnClient(client -> Screens.getWidgets(client.gui.screen()).stream().filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).findFirst().orElseThrow().setValue("Broken"));
        context.waitTicks(3);
        context.takeScreenshot("own-songs-cant-play-small");
        // A file can disappear between discovery and asynchronous playback open.
        try { java.nio.file.Files.delete(mp3.local().song().file()); }
        catch (java.io.IOException error) { throw new AssertionError(error); }
        var latest = context.computeOnClient(client -> WhoStoppedTheMusic.LIBRARY.tracks().stream().filter(t -> t.id().equals(mp3.id())).findFirst().orElseThrow());
        var missing = context.computeOnClient(client -> {
            MusicDirector.get(client).play(latest);
            return MusicDirector.get(client).current();
        });
        context.waitFor(client -> !latest.playable() && channelStopped(client, missing));
        context.runOnClient(client -> MusicDirector.get(client).stop());
        var removed = context.computeOnClient(client -> WhoStoppedTheMusic.LIBRARY.refreshSongs());
        context.waitFor(client -> removed.isDone());
        removed.join();
        context.runOnClient(client -> check(WhoStoppedTheMusic.LIBRARY.tracks().stream().noneMatch(t -> t.id().equals(mp3.id())),
                "explicit rescan removes missing files without erasing saved selections"));
        var rowSource = context.computeOnClient(client -> {
            WhoStoppedTheMusic.settings().setAllTicked(WhoStoppedTheMusic.LIBRARY.tracks().stream().map(Track::id).toList(), false);
            MusicDirector.get(client).play(ogg);
            return MusicDirector.get(client).current();
        });
        context.waitFor(client -> channelState(client, rowSource, true).join());
        context.runOnClient(client -> MusicDirector.get(client).togglePlayback());
        var rescan = context.computeOnClient(client -> WhoStoppedTheMusic.LIBRARY.refreshSongs());
        context.waitFor(client -> rescan.isDone());
        rescan.join();
        context.runOnClient(client -> {
            check(MusicDirector.get(client).current() == rowSource && MusicDirector.get(client).paused(),
                    "rescan retains the same paused row-play source even when every selection is unticked");
            check(MusicDirector.get(client).playing(ogg), "file identity survives track object replacement on rescan");
            MusicDirector.get(client).stop();
        });
        context.clickScreenButton("gui.done");
        context.waitForScreen(TitleScreen.class);
        LoggerFactory.getLogger("whostoppedthemusic-test").info("OWN SONGS PASS: full MP3/OGG native playback and repeat, Music gain, pause/same-source resume, literal file metadata, invalid formats skipped/disabled, missing-file cleanup");
    }

    private static void checkUnifiedControls(ClientGameTestContext context) {
        context.getInput().resizeWindow(960, 720);
        try (var world = context.worldBuilder().create()) {
            context.waitForScreen(null);
            context.runOnClient(client -> {
                client.options.guiScale().set(2);
                client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.7);
                client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.7);
                client.options.musicFrequency().set(MusicFrequency.CONSTANT);
                WhoStoppedTheMusic.settings().setJukebox(JukeboxSetting.PLAY_OVER);
                WhoStoppedTheMusic.settings().setAllTicked(WhoStoppedTheMusic.LIBRARY.tracks().stream().map(t -> t.id()).toList(), true);
            });
            if (Boolean.getBoolean("whostoppedthemusic.artworkOnly")) {
                CustomUiGameTest.artwork(context);
                return;
            }
            var record = context.computeOnClient(client -> {
                var sound = SimpleSoundInstance.forJukeboxSong(SoundEvent.createVariableRangeEvent(
                        Identifier.withDefaultNamespace("music_disc.cat")), Vec3.ZERO);
                client.getSoundManager().play(sound);
                return sound;
            });
            var effect = context.computeOnClient(client -> {
                var sound = new SimpleSoundInstance(Identifier.withDefaultNamespace("music_disc.cat"), SoundSource.AMBIENT,
                        0.5f, 1, SoundInstance.createUnseededRandom(), true, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
                client.getSoundManager().play(sound);
                return sound;
            });
            context.waitFor(client -> channelState(client, record, true).join() && channelState(client, effect, true).join());
            for (Mode mode : Mode.values()) {
                checkModeControls(context, mode, record, effect);
                context.runOnClient(client -> check(channelState(client, record, true).join() && channelState(client, effect, true).join(),
                        mode + " controls leave Records and effects advancing"));
            }
            context.runOnClient(client -> {
                client.getSoundManager().stop(record);
                client.getSoundManager().stop(effect);
            });
            checkCassetteLabels(context);
        }
        LoggerFactory.getLogger("whostoppedthemusic-test").info(
                "SHARED CONTROLS PASS: Vanilla/Basic Play/Pause/same-source Resume/paused Next, pause-menu Next, row Play, held Basic Stop, automatic transitions, single reused toast, duration multiplier, Never, Records/effects isolation, browser state and 320x240");
    }

    private static void checkCassetteLabels(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> WhoStoppedTheMusic.LIBRARY.tracks());
        var translated = Component.translatable("music.game.sweden");
        var shortName = Track.metadata(translated);
        check(shortName.title().getString().equals("Sweden") && shortName.artist().getString().equals("C418"),
                "translated component supplies separate metadata");
        var styled = Track.metadata(Component.literal("Artist-Name - ").append(Component.literal("Night-Song - Reprise")
                .withStyle(net.minecraft.ChatFormatting.BOLD)));
        check(styled.title().getString().equals("Night-Song - Reprise")
                && styled.title().toFlatList(net.minecraft.network.chat.Style.EMPTY).getFirst().getStyle().isBold(),
                "hyphens and component sibling styles survive naming");
        var longName = new Track.Metadata(Component.literal("Night-Song - Reprise across the very distant mountains"),
                Component.literal("Artist-Name and the extraordinarily long orchestra credit"));
        var unknown = Track.metadata(Component.translatableWithFallback("test.missing.modded.name",
                TrackNames.prettify("other:music/forest_theme")));
        check(unknown.title().getString().equals("Forest Theme") && unknown.artist().getString().isEmpty(),
                "modded fallback keeps artist genuinely blank");
        var fixtures = java.util.List.of(shortName, longName, unknown);
        try {
            for (int size : new int[]{960, 320}) {
                context.getInput().resizeWindow(size, size == 320 ? 240 : 720);
                for (int index = 0; index < fixtures.size(); index++) {
                    var fixture = fixtures.get(index);
                    context.runOnClient(client -> {
                        var tracks = original.stream().map(track -> new Track(track.id(), track.kind(), track.title(),
                                track.sound(), fixture)).toList();
                        setTestTracks(tracks);
                        check(tracks.getFirst().metadata() == fixture, "explicit metadata takes precedence over display convention");
                    });
                    for (Mode mode : Mode.values()) {
                        String capture = "labels-" + size + "-" + index + "-" + mode.name().toLowerCase(java.util.Locale.ROOT);
                        context.runOnClient(client -> {
                            WhoStoppedTheMusic.settings().setMode(mode);
                            var director = MusicDirector.get(client);
                            director.syncMode();
                            client.gui.setScreen(null);
                            client.options.musicToast().set(MusicToastDisplayState.PAUSE_AND_TOAST);
                            client.options.notificationDisplayTime().set(2.0);
                            client.gui.toastManager().clear();
                            director.next();
                        });
                        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
                        context.runOnClient(client -> {
                            check(MusicDirector.get(client).metadata().equals(fixture), "director exposes resolved library metadata");
                            checkSingleSongToast(client);
                        });
                        long toastStart = net.minecraft.util.Util.getMillis();
                        context.waitFor(client -> net.minecraft.util.Util.getMillis() - toastStart >= 700);
                        context.takeScreenshot(capture + "-toast");
                        context.runOnClient(client -> {
                            client.gui.toastManager().clear();
                            client.gui.setScreen(new MusicScreen(null, WhoStoppedTheMusic.settings()));
                        });
                        context.waitFor(client -> Screens.getWidgets(client.gui.screen()).stream().anyMatch(widget ->
                                widget.getMessage().getString().equals(fixture.description().getString())));
                        assertWidgetsFit(context);
                        context.runOnClient(client -> {
                            var description = fixture.description().getString();
                            check(Screens.getWidgets(client.gui.screen()).stream().anyMatch(widget ->
                                    widget.getMessage().getString().equals(description)),
                                    "settings tape exposes full metadata to narration");
                            if (mode == Mode.BASIC) {
                                var list = trackList(client.gui.screen());
                                check(list.children().get(1).children().size() == 3, "browser tape participates in focus/narration");
                            }
                        });
                        context.getInput().setCursorPos(0, 0);
                        context.takeScreenshot(capture + "-settings");
                        if (index == 1) {
                            context.waitTicks(30);
                            context.takeScreenshot(capture + "-moving");
                        }
                        context.runOnClient(client -> client.gui.setScreen(new PauseScreen(true)));
                        context.waitFor(client -> cassette(client.gui.screen()).getMessage().getString()
                                .equals(fixture.description().getString()));
                        assertWidgetsFit(context);
                        context.runOnClient(client -> check(cassette(client.gui.screen()).getMessage().getString()
                                .equals(fixture.description().getString()), "pause tape keeps title/artist distinct"));
                        context.takeScreenshot(capture + "-pause");
                        if (index == 1) {
                            context.runOnClient(client -> MusicDirector.get(client).togglePlayback());
                            context.waitTicks(2);
                            context.takeScreenshot(capture + "-paused");
                            var tape = context.computeOnClient(client -> cassette(client.gui.screen()));
                            double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
                            context.getInput().setCursorPos((tape.getX() + tape.getWidth() / 2.0) * scale,
                                    (tape.getY() + tape.getHeight() / 2.0) * scale);
                            context.waitTicks(3);
                            context.takeScreenshot(capture + "-tooltip");
                            context.getInput().setCursorPos(0, 0);
                            context.runOnClient(client -> {
                                check(MusicDirector.get(client).metadata().equals(fixture), "status never contaminates metadata");
                                MusicDirector.get(client).togglePlayback();
                            });
                        }
                    }
                }
            }
        } finally {
            context.runOnClient(client -> setTestTracks(original));
        }
        LoggerFactory.getLogger("whostoppedthemusic-test").info(
                "CASSETTE LABELS PASS: translated/styled/explicit/hyphenated/modded/unknown metadata, changed tracks, both modes, pause/settings/browser/toast at normal and 320x240");
    }

    private static void setTestTracks(java.util.List<Track> tracks) {
        try {
            var field = xyz.pyrehaven.whostoppedthemusic.music.TrackLibrary.class.getDeclaredField("tracks");
            field.setAccessible(true);
            field.set(WhoStoppedTheMusic.LIBRARY, tracks);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }


    private static AbstractWidget playerTape(ClientGameTestContext context, boolean compact) {
        return context.computeOnClient(client -> compact ? cassette(client.gui.screen())
                : Screens.getWidgets(client.gui.screen()).stream().filter(widget -> widget instanceof Button
                        && !(widget instanceof TransportButton) && widget.getHeight() > Theme.CONTROL_HEIGHT).findFirst().orElseThrow());
    }

    private static CassetteArtwork.Geometry tapeGeometry(ClientGameTestContext context, AbstractWidget tape, boolean compact) {
        return context.computeOnClient(client -> CassetteArtwork.geometry(tape.getX(), tape.getY(), tape.getWidth(),
                tape.getHeight()));
    }

    private static void checkPlayerReels(ClientGameTestContext context, String name, boolean moves, boolean compact) {
        var tape = playerTape(context, compact);
        var geometry = tapeGeometry(context, tape, compact);
        checkReelPixels(context, name, geometry, moves, Theme.SPINDLE);
    }


    static void checkReelPixels(ClientGameTestContext context, String name, CassetteArtwork.Geometry geometry,
                                        boolean moves, int centerColor) {
        context.getInput().setCursorPos(0, 0);
        context.waitTicks(2);
        Path first = context.takeScreenshot(name + "-a");
        long start = System.nanoTime();
        context.waitFor(client -> System.nanoTime() - start >= 160_000_000L);
        Path second = context.takeScreenshot(name + "-b");
        int scale = context.computeOnClient(client -> (int) client.getWindow().getGuiScale());
        try {
            var a = javax.imageio.ImageIO.read(first.toFile());
            var b = javax.imageio.ImageIO.read(second.toFile());
            int changed = 0;
            for (int reelX : new int[]{geometry.left(), geometry.right()}) {
                for (int y = geometry.reelY() * scale; y < (geometry.reelY() + geometry.reelSize()) * scale; y++) {
                    for (int x = reelX * scale; x < (reelX + geometry.reelSize()) * scale; x++) {
                        if (a.getRGB(x, y) != b.getRGB(x, y)) changed++;
                    }
                }
            }
            check(moves ? changed > 0 : changed == 0, name + " rendered spokes " + (moves ? "move" : "freeze") + ": " + changed);
            for (int centerX : new int[]{geometry.leftSpindleX(), geometry.rightSpindleX()}) {
                for (int y = geometry.spindleY() * scale; y < (geometry.spindleY() + 2) * scale; y++) {
                    for (int x = centerX * scale; x < (centerX + 2) * scale; x++) {
                        check(a.getRGB(x, y) == centerColor && b.getRGB(x, y) == centerColor,
                                name + " spindle rods/holes retain exact 2x2 geometry while turning");
                    }
                }
            }
        } catch (java.io.IOException exception) { throw new AssertionError(exception); }
    }

    private static void checkEmptyPlayer(ClientGameTestContext context, String name, boolean compact) {
        var tape = playerTape(context, compact);
        var geometry = tapeGeometry(context, tape, compact);
        checkReelPixels(context, name, geometry, false, Theme.SPINDLE);
        Path capture = context.takeScreenshot(name + "-outline");
        int scale = context.computeOnClient(client -> (int) client.getWindow().getGuiScale());
        try {
            var image = javax.imageio.ImageIO.read(capture.toFile());
            int rods = 0;
            for (int y = (tape.getY() + 1) * scale; y < (tape.getBottom() - 1) * scale; y++) {
                for (int x = (tape.getX() + 1) * scale; x < (tape.getRight() - 1) * scale; x++) {
                    int color = image.getRGB(x, y);
                    if (color == Theme.SPINDLE) rods++;
                    check(color != Theme.ORANGE && color != Theme.BLUE && color != Theme.GLOW,
                            name + " contains no cassette shell, label or reels");
                }
            }
            check(rods == 8 * scale * scale, name + " contains only two exact 2x2 red rods");
        } catch (java.io.IOException exception) { throw new AssertionError(exception); }
    }

    private static void checkModeControls(ClientGameTestContext context, Mode mode, SoundInstance record, SoundInstance effect) {
        context.runOnClient(client -> {
            var settings = WhoStoppedTheMusic.settings();
            settings.setMode(mode);
            MusicDirector.get(client).syncMode();
            client.getMusicManager().stopPlaying();
            client.options.musicToast().set(MusicToastDisplayState.PAUSE_AND_TOAST);
            client.options.notificationDisplayTime().set(2.0);
            client.gui.toastManager().clear();
            client.gui.setScreen(new MusicScreen(null, settings));
        });
        context.waitForScreen(MusicScreen.class);
        clickWidget(context, namedWidget(context, "Play"));
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        var sound = context.computeOnClient(client -> MusicDirector.get(client).current());
        context.runOnClient(client -> {
            checkSingleSongToast(client);
            var manager = client.gui.toastManager();
            var toast = nowPlayingToast(client);
            manager.showNowPlayingToast();
            check(nowPlayingToast(client) == toast, "vanilla callback reuses its dedicated toast");
            var timed = new NowPlayingToast();
            timed.showToast(client.options);
            timed.update(manager, 9999);
            check(timed.getWantedVisibility() == Toast.Visibility.SHOW, "2x notification lasts almost ten seconds");
            timed.update(manager, 10001);
            check(timed.getWantedVisibility() == Toast.Visibility.HIDE, "vanilla notification duration expires");
        });
        clickWidget(context, namedWidget(context, "Pause"));
        context.waitFor(client -> channelState(client, sound, false).join());
        context.runOnClient(client -> client.getSoundManager().resume());
        context.waitTicks(30);
        context.runOnClient(client -> {
            check(MusicDirector.get(client).paused() && MusicDirector.get(client).current() == sound
                    && channelState(client, sound, false).join(), mode + " held Pause survives ticks/global resume");
            check(channelState(client, record, true).join() && channelState(client, effect, true).join(),
                    "Records/effects keep advancing during held Pause");
        });
        clickWidget(context, namedWidget(context, "Resume"));
        context.waitFor(client -> channelState(client, sound, true).join());
        context.runOnClient(client -> check(MusicDirector.get(client).current() == sound, "Resume preserves exact source"));
        var toast = context.computeOnClient(ClientSmokeGameTest::nowPlayingToast);
        clickWidget(context, namedWidget(context, "Pause"));
        clickWidget(context, namedWidget(context, "Next"));
        context.waitFor(client -> channelStopped(client, sound) && channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> {
            check(!MusicDirector.get(client).paused(), "Next clears pause");
            checkSingleSongToast(client);
            check(nowPlayingToast(client) == toast, "transition updates same vanilla notification");
        });
        if (mode == Mode.BASIC) checkBasicStopAndRow(context, record, effect);
        var outgoing = context.computeOnClient(client -> MusicDirector.get(client).current());
        context.runOnClient(client -> client.getSoundManager().stop(outgoing));
        context.waitFor(client -> MusicDirector.get(client).current() != outgoing
                && channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> checkSingleSongToast(client));
        context.takeScreenshot("shared-controls-" + mode.name().toLowerCase(java.util.Locale.ROOT));
        context.runOnClient(client -> client.options.musicToast().set(MusicToastDisplayState.NEVER));
        context.waitTicks(2);
        clickWidget(context, namedWidget(context, "Next"));
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> {
            var hidden = nowPlayingToast(client);
            check(hidden == null || hidden.getWantedVisibility() == Toast.Visibility.HIDE, "Never hides vanilla toast");
        });
        context.clickScreenButton("gui.done");
        context.waitForScreen(null);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(PauseScreen.class);
        var beforeTop = context.computeOnClient(client -> MusicDirector.get(client).current());
        clickWidget(context, namedWidget(context, "Next"));
        context.waitFor(client -> channelStopped(client, beforeTop) && channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> client.options.notificationDisplayTime().set(1.0));
    }

    private static void checkBasicStopAndRow(ClientGameTestContext context, SoundInstance record, SoundInstance effect) {
        clickWidget(context, namedWidget(context, "Stop"));
        context.waitTicks(40);
        context.runOnClient(client -> {
            var director = MusicDirector.get(client);
            check(!director.active() && !director.canPrevious(), "Basic Stop holds through Constant ticks");
            director.previous();
            check(!director.active(), "Previous cannot escape held Stop");
            check(channelState(client, record, true).join() && channelState(client, effect, true).join(),
                    "Records/effects keep advancing during held Stop");
        });
        clickWidget(context, namedWidget(context, "Play"));
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> {
            searchBox(client.gui.screen()).setValue("Pigstep");
            check(trackList(client.gui.screen()).children().getFirst().children().getLast() instanceof TransportButton,
                    "row Play shares drawn transport widget");
        });
        context.waitTicks(2);
        clickWidget(context, context.computeOnClient(client -> (AbstractWidget) trackList(client.gui.screen()).children().getFirst().children().getLast()));
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> check(MusicDirector.get(client).status().getString().equals("Lena Raine - Pigstep"), "row Play starts selected track"));
        clickWidget(context, namedWidget(context, "<", Theme.BASIC_TOP));
        clickWidget(context, namedWidget(context, ">", Theme.BASIC_TOP));
        context.getInput().resizeWindow(320, 240);
        context.waitTicks(3);
        assertWidgetsFit(context);
        context.runOnClient(client -> check(searchBox(client.gui.screen()).getValue().equals("Pigstep"), "query survives collapse and resize"));
        context.takeScreenshot("shared-controls-basic-small");
        context.getInput().resizeWindow(960, 720);
        context.waitTicks(3);
    }

    private static void checkBasicUi(ClientGameTestContext context) {
        context.getInput().resizeWindow(960, 720);
        context.runOnClient(client -> {
            var settings = WhoStoppedTheMusic.settings();
            settings.setMode(Mode.BASIC);
            settings.setAllTicked(WhoStoppedTheMusic.LIBRARY.tracks().stream().map(t -> t.id()).toList(), true);
            client.options.guiScale().set(2);
            client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.7);
            client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.7);
            MusicDirector.get(client).syncMode();
            client.gui.setScreen(new MusicScreen(client.gui.screen(), settings));
        });
        context.waitForScreen(MusicScreen.class);
        context.waitTicks(5);
        assertWidgetsFit(context);
        var list = context.computeOnClient(client -> trackList(client.gui.screen()));
        context.runOnClient(client -> {
            check(list.children().stream().filter(row -> row.children().isEmpty()).count() == 2, "empty search has two sections");
            list.setScrollAmount(Theme.TRACK_HEADER_HEIGHT + list.children().get(1).getHeight() * 0.75);
        });
        context.waitTicks(2);
        context.runOnClient(client -> {
            var rows = list.children().stream().filter(row -> !row.children().isEmpty()).toList();
            check(rows.get(0).getY() < list.getY() && rows.get(0).getY() + rows.get(0).getHeight() > list.getY(), "previous cassette clipped above");
            check(rows.get(1).getY() >= list.getY() && rows.get(1).getY() + rows.get(1).getHeight() <= list.getBottom(), "main cassette fully visible");
            check(rows.get(2).getY() < list.getBottom() && rows.get(2).getY() + rows.get(2).getHeight() > list.getBottom(), "next cassette clipped below");
        });
        context.getInput().setCursorPos(0, 0);
        context.takeScreenshot("basic-ui-expanded");
        double start = context.computeOnClient(client -> list.scrollAmount());
        context.runOnClient(client -> {
            list.mouseScrolled(list.getX() + 1, list.getY() + 1, 0, -1);
            check(list.scrollAmount() == start, "wheel sets a smooth target, not an immediate jump");
        });
        context.waitTicks(8);
        context.runOnClient(client -> check(list.scrollAmount() > start, "smooth wheel scrolling advances the viewport"));
        var all = namedWidget(context, "Select All");
        var none = namedWidget(context, "Deselect All");
        context.runOnClient(client -> {
            searchBox(client.gui.screen()).setValue("  C418  ");
            check(!all.visible && !all.active && !none.visible && !none.active, "search hides and disables both bulk controls");
            int count = MusicDirector.get(client).selected().size();
            ((Button) none).onPress(null);
            check(MusicDirector.get(client).selected().size() == count, "bulk action guarded while searching");
            check(list.children().stream().allMatch(row -> row.children().size() == 3), "ranked search has no headers");
            list.setScrollAmount(list.children().getFirst().getHeight());
        });
        double savedScroll = context.computeOnClient(client -> list.scrollAmount());
        clickWidget(context, namedWidget(context, "<", Theme.BASIC_TOP));
        context.waitTicks(2);
        assertWidgetsFit(context);
        context.runOnClient(client -> {
            check(Screens.getWidgets(client.gui.screen()).stream().noneMatch(TrackList.class::isInstance), "collapsed browser removed from input and render tree");
            var mode = Screens.getWidgets(client.gui.screen()).stream().filter(w -> w.getMessage().getString().equals("Basic")).findFirst().orElseThrow();
            check(Math.abs(mode.getX() + mode.getWidth() / 2.0 - client.gui.screen().width / 2.0) <= 1, "collapsed player centered");
        });
        context.getInput().setCursorPos(0, 0);
        context.takeScreenshot("basic-ui-collapsed");
        clickWidget(context, namedWidget(context, ">", Theme.BASIC_TOP));
        context.runOnClient(client -> {
            check(searchBox(client.gui.screen()).getValue().equals("  C418  "), "collapse restores exact query");
            check(trackList(client.gui.screen()) == list && Math.abs(list.scrollAmount() - savedScroll) < 1, "collapse restores same browser and scroll");
            searchBox(client.gui.screen()).setValue("cat");
            var cat = WhoStoppedTheMusic.LIBRARY.tracks().stream().filter(track -> track.id().equals("minecraft:records/cat")).findFirst().orElseThrow();
            check(list.children().getFirst().children().size() == 3, "first ranked result is a song");
            ((Button) list.children().getFirst().children().getLast()).onPress(null);
            check(MusicDirector.get(client).current().getSound().getLocation().equals(cat.sound().getLocation()), "exact Cat disc ranks first across sections");
        });
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        context.getInput().setCursorPos(0, 0);
        context.waitTicks(2);
        context.takeScreenshot("basic-ui-search");
        context.runOnClient(client -> searchBox(client.gui.screen()).setValue("swedn"));
        context.waitTicks(2);
        var row = context.computeOnClient(client -> list.children().getFirst());
        boolean ticked = context.computeOnClient(client -> WhoStoppedTheMusic.settings().ticked("minecraft:music/game/sweden"));
        clickWidget(context, (AbstractWidget) row.children().getFirst());
        context.runOnClient(client -> check(WhoStoppedTheMusic.settings().ticked("minecraft:music/game/sweden") != ticked, "approximate result tick works"));
        context.runOnClient(client -> {
            client.gui.screen().setFocused(list);
            list.setFocused(row);
            row.setFocused(row.children().getFirst());
        });
        context.getInput().pressKey(InputConstants.KEY_SPACE);
        context.runOnClient(client -> check(WhoStoppedTheMusic.settings().ticked("minecraft:music/game/sweden") == ticked, "row selection works by keyboard"));
        context.getInput().pressKey(InputConstants.KEY_SPACE);
        clickWidget(context, (AbstractWidget) row.children().getLast());
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> {
            check(MusicDirector.get(client).status().getString().equals("C418 - Sweden"), "row Play starts exact ranked song on a real channel");
            check(Screens.getWidgets(client.gui.screen()).stream().anyMatch(w -> w.getY() > Theme.BASIC_TOP + Theme.ROW * 4
                    && w.getMessage().getString().equals("Sweden\nC418")), "current-song cassette below transport updates live");
            searchBox(client.gui.screen()).setValue("C418");
            list.setScrollAmount(list.children().getFirst().getHeight());
        });
        context.getInput().resizeWindow(320, 240);
        context.waitTicks(5);
        assertWidgetsFit(context);
        context.runOnClient(client -> {
            check(searchBox(client.gui.screen()).getValue().equals("C418"), "resize preserves query");
            check(Math.abs(list.scrollAmount() / list.children().getFirst().getHeight() - 1) < 0.05, "resize preserves relative song position");
            check(WhoStoppedTheMusic.settings().ticked("minecraft:music/game/sweden") != ticked, "resize preserves selection");
            check(!all.visible && !all.active && !none.visible && !none.active, "resized search bulk controls stay disabled");
        });
        context.getInput().setCursorPos(0, 0);
        context.takeScreenshot("basic-ui-small-search");
        context.runOnClient(client -> searchBox(client.gui.screen()).setValue("   "));
        context.waitTicks(2);
        context.runOnClient(client -> check(all.visible && all.active && none.visible && none.active, "blank query restores bulk controls below list"));
        clickWidget(context, none);
        context.runOnClient(client -> check(MusicDirector.get(client).selected().isEmpty(), "Deselect All works with blank query"));
        clickWidget(context, all);
        context.runOnClient(client -> check(MusicDirector.get(client).selected().size() == WhoStoppedTheMusic.LIBRARY.tracks().size(), "Select All restores library"));
        context.getInput().setCursorPos(0, 0);
        context.takeScreenshot("basic-ui-small-expanded");
        clickWidget(context, namedWidget(context, "<", Theme.BASIC_TOP));
        context.waitTicks(2);
        assertWidgetsFit(context);
        context.takeScreenshot("basic-ui-small-collapsed");
        context.clickScreenButton("gui.done");
        context.waitForScreen(TitleScreen.class);
        LoggerFactory.getLogger("whostoppedthemusic-test").info("BASIC UI PASS: expanded/clipped tapes, smooth wheel, ranked ungrouped search, hidden/disabled bulk, collapse/centering/restore, row tick/real-channel play, live cassette, resize/query/scroll/selection and 320x240");
    }

    private static TrackList trackList(net.minecraft.client.gui.screens.Screen screen) {
        return Screens.getWidgets(screen).stream().filter(TrackList.class::isInstance).map(TrackList.class::cast).findFirst().orElseThrow();
    }

    private static EditBox searchBox(net.minecraft.client.gui.screens.Screen screen) {
        return Screens.getWidgets(screen).stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
    }

    private static AbstractWidget namedWidget(ClientGameTestContext context, String prefix) {
        return context.computeOnClient(client -> Screens.getWidgets(client.gui.screen()).stream()
                .filter(w -> w.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow());
    }

    private static AbstractWidget namedWidget(ClientGameTestContext context, String label, int y) {
        return context.computeOnClient(client -> Screens.getWidgets(client.gui.screen()).stream()
                .filter(w -> w.getY() == y && w.getMessage().getString().equals(label)).findFirst().orElseThrow());
    }

    private static AbstractWidget basicNext(ClientGameTestContext context) {
        return context.computeOnClient(client -> Screens.getWidgets(client.gui.screen()).stream()
                .filter(w -> w.getY() == Theme.BASIC_TOP + Theme.ROW * 3 && w.getMessage().getString().equals("Next"))
                .max(java.util.Comparator.comparingInt(AbstractWidget::getX)).orElseThrow());
    }

    private static void checkBasic(ClientGameTestContext context) {
        SoundInstance carried;
        try (var world = context.worldBuilder().create()) {
            context.waitForScreen(null);
            context.runOnClient(client -> {
                var settings = WhoStoppedTheMusic.settings();
                settings.setMode(Mode.VANILLA);
                settings.setShuffle(false);
                settings.setJukebox(JukeboxSetting.PLAY_OVER);
                settings.setAllTicked(WhoStoppedTheMusic.LIBRARY.tracks().stream().map(t -> t.id()).toList(), true);
                MusicDirector.get(client).syncMode();
                client.options.musicFrequency().set(MusicFrequency.CONSTANT);
                client.options.musicToast().set(MusicToastDisplayState.PAUSE_AND_TOAST);
                client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.7);
                client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.7);
            });
            context.getInput().pressKey(InputConstants.KEY_M);
            context.waitForScreen(MusicScreen.class);
            clickWidget(context, widget(context, 1));
            context.runOnClient(client -> {
                var tracks = WhoStoppedTheMusic.LIBRARY.tracks();
                check(tracks.size() > 50, "loaded soundtrack/disc library");
                check(tracks.stream().map(t -> t.id()).distinct().count() == tracks.size(), "file ids deduplicated");
                check(tracks.stream().anyMatch(t -> t.title().getString().equals("C418 - Sweden")), "real soundtrack title");
                check(tracks.stream().anyMatch(t -> t.title().getString().equals("Lena Raine - Pigstep")), "real disc title: "
                        + tracks.stream().filter(t -> t.id().contains("pigstep")).map(t -> t.id() + "=" + t.title()).toList());
            });
            checkBasicControls(context);
            checkJukebox(context, world.getServer());
            carried = context.computeOnClient(client -> MusicDirector.get(client).current());
        }
        context.waitForScreen(TitleScreen.class);
        context.runOnClient(client -> check(MusicDirector.get(client).current() == carried, "same Basic song across world exit"));
        context.waitFor(client -> channelState(client, carried, true).join());
        try (var world = context.worldBuilder().create()) {
            context.waitForScreen(null);
            context.runOnClient(client -> check(MusicDirector.get(client).current() == carried, "same Basic source in another world"));
            context.getInput().pressKey(InputConstants.KEY_M);
            context.waitForScreen(MusicScreen.class);
            context.clickScreenButton("whostoppedthemusic.mode.basic");
            context.clickScreenButton("whostoppedthemusic.mode.custom");
            context.runOnClient(client -> MusicDirector.get(client).next());
            context.waitFor(client -> MusicDirector.get(client).active() && !(MusicDirector.get(client).current()
                    instanceof xyz.pyrehaven.whostoppedthemusic.music.TrackSoundInstance));
            var vanilla = context.computeOnClient(client -> MusicDirector.get(client).current());
            context.waitFor(client -> channelState(client, vanilla, true).join());
            context.runOnClient(client -> {
                check(vanilla.getIdentifier().equals(client.getSituationalMusic().sound().value().location()), "Vanilla pool restored");
                MusicDirector.get(client).togglePlayback();
            });
            context.waitFor(client -> channelState(client, vanilla, false).join());
            context.runOnClient(client -> MusicDirector.get(client).togglePlayback());
            context.waitFor(client -> channelState(client, vanilla, true).join());
            checkJukebox(context, world.getServer());
        }
        LoggerFactory.getLogger("whostoppedthemusic-test").info("BASIC JUKEBOX PASS: library/names, UI/search/ticks, deck/transport, one toast, real Records isolation, fade/hold/range/end, world continuity, Vanilla switchback");
    }

    private static void checkBasicControls(ClientGameTestContext context) {
        context.getInput().resizeWindow(320, 240);
        context.waitTicks(5);
        assertWidgetsFit(context);
        context.takeScreenshot("whostoppedthemusic-basic-small");
        clickWidget(context, namedWidget(context, "Shuffle:"));
        context.runOnClient(client -> check(WhoStoppedTheMusic.settings().shuffle(), "Shuffle toggles on"));
        clickWidget(context, namedWidget(context, "Shuffle:"));
        context.runOnClient(client -> {
            var search = Screens.getWidgets(client.gui.screen()).stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                    .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow();
            search.setValue("cat");
            var list = Screens.getWidgets(client.gui.screen()).stream().filter(xyz.pyrehaven.whostoppedthemusic.ui.widget.TrackList.class::isInstance)
                    .map(xyz.pyrehaven.whostoppedthemusic.ui.widget.TrackList.class::cast).findFirst().orElseThrow();
            check(!list.children().isEmpty() && list.children().size() < 10
                    && list.children().stream().allMatch(row -> row.children().size() == 3), "search filters without section headers");
            search.setValue("");
        });
        context.clickScreenButton("whostoppedthemusic.none");
        context.waitTicks(25);
        context.runOnClient(client -> check(!MusicDirector.get(client).active() && !MusicDirector.get(client).canNext(), "empty selection is silent"));
        context.clickScreenButton("whostoppedthemusic.all");
        context.runOnClient(client -> {
            var settings = WhoStoppedTheMusic.settings();
            check(MusicDirector.get(client).selected().size() == WhoStoppedTheMusic.LIBRARY.tracks().size(), "All ticks every track with empty search");
            settings.setAllTicked(WhoStoppedTheMusic.LIBRARY.tracks().stream().map(t -> t.id()).toList(), false);
            for (var track : WhoStoppedTheMusic.LIBRARY.tracks()) {
                if (track.title().getString().equals("C418 - Sweden") || track.title().getString().equals("Lena Raine - Pigstep")) settings.setTicked(track.id(), true);
            }
            MusicDirector.get(client).playlistChanged();
            var loaded = Settings.load(FabricLoader.getInstance().getConfigDir().resolve("whostoppedthemusic.json"));
            check(loaded.mode() == Mode.BASIC && !loaded.shuffle(), "Basic settings reload");
            check(WhoStoppedTheMusic.LIBRARY.tracks().stream().filter(t -> loaded.ticked(t.id())).count() == 2, "only two saved ticks");
        });
        clickWidget(context, namedWidget(context, "Play", Theme.BASIC_TOP + Theme.ROW * 3));
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        var first = context.computeOnClient(client -> MusicDirector.get(client).current().getSound().getLocation());
        clickWidget(context, basicNext(context));
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> check(!MusicDirector.get(client).current().getSound().getLocation().equals(first), "ordered Next uses other ticked track"));
        clickWidget(context, namedWidget(context, "Previous", Theme.BASIC_TOP + Theme.ROW * 3));
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> check(MusicDirector.get(client).current().getSound().getLocation().equals(first), "Previous restores exact historical track"));
        clickWidget(context, namedWidget(context, "Stop", Theme.BASIC_TOP + Theme.ROW * 4));
        context.waitTicks(40);
        context.runOnClient(client -> {
            var director = MusicDirector.get(client);
            check(!director.active() && !director.canPrevious(), "Stop holds through Constant ticks and disables Previous");
            director.previous();
            check(!director.active(), "Previous cannot release Stop");
        });
        context.runOnClient(client -> Screens.getWidgets(client.gui.screen()).stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow().setValue("Pigstep"));
        context.waitTicks(3);
        var row = context.computeOnClient(client -> Screens.getWidgets(client.gui.screen()).stream()
                .filter(xyz.pyrehaven.whostoppedthemusic.ui.widget.TrackList.class::isInstance)
                .map(xyz.pyrehaven.whostoppedthemusic.ui.widget.TrackList.class::cast).findFirst().orElseThrow().children().getLast());
        clickWidget(context, (AbstractWidget) row.children().getFirst());
        context.runOnClient(client -> check(!Settings.load(FabricLoader.getInstance().getConfigDir().resolve("whostoppedthemusic.json"))
                .ticked("minecraft:records/pigstep"), "row tick writes saved selection"));
        clickWidget(context, (AbstractWidget) row.children().getFirst());
        clickWidget(context, (AbstractWidget) row.children().getLast());
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> {
            check(MusicDirector.get(client).status().getString().equals("Lena Raine - Pigstep"), "row Play uses disc title on Music source");
            check(MusicDirector.get(client).current().getSource() == SoundSource.MUSIC, "background disc uses Music");
            client.getMusicManager().showNowPlayingToastIfNeeded();
            checkSingleSongToast(client);
        });
        context.takeScreenshot("whostoppedthemusic-basic-disc");
        var outgoing = context.computeOnClient(client -> MusicDirector.get(client).current());
        context.runOnClient(client -> client.getSoundManager().stop(outgoing));
        context.waitFor(client -> MusicDirector.get(client).current() != outgoing
                && channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> {
            check(MusicDirector.get(client).selected().stream().anyMatch(t -> t.sound().getLocation()
                    .equals(MusicDirector.get(client).current().getSound().getLocation())), "automatic deck progression stays in selection");
            client.options.musicToast().set(MusicToastDisplayState.NEVER);
        });
        context.waitTicks(2);
        context.runOnClient(client -> {
            var toast = nowPlayingToast(client);
            check(toast == null || toast.getWantedVisibility() == net.minecraft.client.gui.components.toasts.Toast.Visibility.HIDE,
                    "Music Toast Never hides vanilla notification");
            client.options.musicToast().set(MusicToastDisplayState.PAUSE_AND_TOAST);
        });
    }

    private static void checkJukebox(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        context.runOnClient(client -> WhoStoppedTheMusic.settings().setJukebox(JukeboxSetting.PLAY_OVER));
        var effect = context.computeOnClient(client -> {
            var sound = new SimpleSoundInstance(Identifier.withDefaultNamespace("music_disc.cat"), SoundSource.AMBIENT,
                    0.5f, 1, SoundInstance.createUnseededRandom(), true, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
            client.getSoundManager().play(sound);
            return sound;
        });
        context.waitFor(client -> channelState(client, effect, true).join());
        var pos = context.computeOnClient(client -> client.gameRenderer.mainCamera().blockPosition().immutable());
        context.runOnClient(client -> {
            var registry = client.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.JUKEBOX_SONG);
            client.level.levelEvent(null, 1010, pos, registry.getId(registry.getValueOrThrow(net.minecraft.world.item.JukeboxSongs.PIGSTEP)));
        });
        var records = context.computeOnClient(client -> ((xyz.pyrehaven.whostoppedthemusic.mixin.ClientJukeboxMixin)
                ((xyz.pyrehaven.whostoppedthemusic.mixin.ClientLevelAccessor) client.level).whostoppedthemusic$events())
                .whostoppedthemusic$jukeboxes().get(pos));
        context.waitFor(client -> channelState(client, records, true).join());
        var background = context.computeOnClient(client -> MusicDirector.get(client).current());
        context.waitTicks(25);
        context.runOnClient(client -> check(MusicDirector.get(client).current() == background
                && channelState(client, background, true).join(), "Play over retains background"));
        context.runOnClient(client -> MusicDirector.get(client).next());
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> MusicDirector.get(client).togglePlayback());
        context.waitTicks(25);
        context.runOnClient(client -> check(channelState(client, records, true).join() && channelState(client, effect, true).join(),
                "transport leaves real Records and effects playing"));
        context.runOnClient(client -> MusicDirector.get(client).togglePlayback());
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        var fading = context.computeOnClient(client -> MusicDirector.get(client).current());
        float gain = context.computeOnClient(client -> channelGain(client, fading).join());
        context.runOnClient(client -> WhoStoppedTheMusic.settings().setJukebox(JukeboxSetting.PAUSE_MUSIC));
        context.waitTicks(25);
        context.runOnClient(client -> check(channelGain(client, fading).join() < gain, "nearby jukebox fades actual Music gain"));
        context.waitTicks(310);
        context.runOnClient(client -> {
            check(!MusicDirector.get(client).active() && channelState(client, records, true).join()
                    && channelState(client, effect, true).join(), "jukebox holds silence, Records/effects unchanged");
            MusicDirector.get(client).next();
            check(!MusicDirector.get(client).active(), "manual Next cannot escape jukebox hold");
        });
        context.setScreen(() -> null);
        server.runCommand("execute as @a at @s run tp @s ~80 ~ ~");
        context.waitFor(client -> !xyz.pyrehaven.whostoppedthemusic.music.JukeboxWatcher.nearby(client));
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        server.runCommand("execute as @a at @s run tp @s ~-80 ~ ~");
        context.waitFor(client -> xyz.pyrehaven.whostoppedthemusic.music.JukeboxWatcher.nearby(client));
        context.waitTicks(5);
        context.runOnClient(client -> client.level.levelEvent(null, 1011, pos, 0));
        context.waitFor(client -> !xyz.pyrehaven.whostoppedthemusic.music.JukeboxWatcher.nearby(client)
                && channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> {
            // Audio completion can precede the server's stop event; stale map entries must not hold music.
            var registry = client.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.JUKEBOX_SONG);
            client.level.levelEvent(null, 1010, pos, registry.getId(registry.getValueOrThrow(net.minecraft.world.item.JukeboxSongs.PIGSTEP)));
        });
        var ending = context.computeOnClient(client -> ((xyz.pyrehaven.whostoppedthemusic.mixin.ClientJukeboxMixin)
                ((xyz.pyrehaven.whostoppedthemusic.mixin.ClientLevelAccessor) client.level).whostoppedthemusic$events()).whostoppedthemusic$jukeboxes().get(pos));
        context.waitFor(client -> channelState(client, ending, true).join());
        context.runOnClient(client -> client.getSoundManager().stop(ending));
        context.waitTicks(25);
        context.runOnClient(client -> check(!xyz.pyrehaven.whostoppedthemusic.music.JukeboxWatcher.nearby(client), "ended channel releases hold even before stop event"));
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
    }

    static void checkSingleSongToast(net.minecraft.client.Minecraft client) {
        var toast = nowPlayingToast(client);
        check(toast != null && toast.getWantedVisibility() == Toast.Visibility.SHOW, "vanilla notification is visible");
        check(toast.xPos(client.getWindow().getGuiScaledWidth(), 1) == 0 && toast.yPos(0) == 0,
                "song notification uses vanilla's top-left placement");
    }

    static NowPlayingToast nowPlayingToast(net.minecraft.client.Minecraft client) {
        try {
            var manager = client.gui.toastManager();
            var vanilla = manager.getClass().getDeclaredField("nowPlayingToast");
            vanilla.setAccessible(true);
            var instance = vanilla.get(manager);
            if (instance == null) return null;
            var field = instance.getClass().getDeclaredField("toast");
            field.setAccessible(true);
            return (NowPlayingToast) field.get(instance);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void checkSharedControls(ClientGameTestContext context, Path settingsFile) {
        double master = context.computeOnClient(client -> client.options.getSoundSourceOptionInstance(SoundSource.MASTER).get());
        var toast = context.computeOnClient(client -> client.options.musicToast().get());
        context.clickScreenButton("whostoppedthemusic.mode.vanilla");
        context.runOnClient(client -> {
            check(WhoStoppedTheMusic.settings().mode() == Mode.BASIC, "mode cycles to Basic");
        });
        context.clickScreenButton("whostoppedthemusic.mode.basic");
        context.runOnClient(client -> check(WhoStoppedTheMusic.settings().mode() == Mode.CUSTOM, "mode cycles to Custom"));
        context.clickScreenButton("whostoppedthemusic.mode.custom");
        context.runOnClient(client -> {
            check(WhoStoppedTheMusic.settings().mode() == Mode.VANILLA, "mode cycles back to Vanilla");
            check(Screens.getWidgets(client.gui.screen()).size() == 8, "only tape, mode, frequency, jukebox, transports, music volume and Done");
        });
        var frequency = context.computeOnClient(client -> client.options.musicFrequency().get());
        clickWidget(context, widget(context, 2));
        context.runOnClient(client -> check(client.options.musicFrequency().get() != frequency, "frequency widget updates vanilla"));
        double music = context.computeOnClient(client -> client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get());
        clickWidget(context, widget(context, 6));
        context.waitTicks(25);
        context.runOnClient(client -> check(client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get() != music,
                "music slider updates vanilla"));
        context.runOnClient(client -> {
            check(client.options.getSoundSourceOptionInstance(SoundSource.MASTER).get() == master, "music slider leaves Master unchanged");
            check(client.options.musicToast().get() == toast, "screen leaves toast preference unchanged");
        });
        JukeboxSetting previous = Settings.load(settingsFile).jukebox();
        clickWidget(context, widget(context, 3));
        check(Settings.load(settingsFile).jukebox() == previous.next(), "jukebox preference persists");
        context.clickScreenButton("gui.done");
        context.waitForScreen(null);
        context.runOnClient(client -> {
            MusicFrequency saved = client.options.musicFrequency().get();
            double volume = client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get();
            client.options.musicFrequency().set(saved == MusicFrequency.DEFAULT ? MusicFrequency.FREQUENT : MusicFrequency.DEFAULT);
            client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0);
            client.options.load();
            check(client.options.musicFrequency().get() == saved, "frequency survives option reload");
            check(client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get() == volume, "music survives option reload");
            Settings.load(settingsFile).completeFirstRun(() -> { throw new AssertionError("first run repeated"); });
        });
    }

    private static void checkReboundKey(ClientGameTestContext context) {
        context.runOnClient(client -> {
            KeyMapping.get("key.whostoppedthemusic.open").setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_N));
            KeyMapping.resetMapping();
        });
        context.getInput().pressKey(InputConstants.KEY_M);
        context.runOnClient(client -> check(client.gui.screen() == null, "old binding no longer opens screen"));
        context.getInput().pressKey(InputConstants.KEY_N);
        context.waitForScreen(MusicScreen.class);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(null);
        context.runOnClient(client -> {
            KeyMapping.get("key.whostoppedthemusic.open").setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_M));
            KeyMapping.resetMapping();
        });
    }

    private static void checkCassette(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.options.musicToast().set(MusicToastDisplayState.PAUSE_AND_TOAST);
            client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.7);
            client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.7);
            client.getMusicManager().stopPlaying();
            client.getMusicManager().startPlaying(Musics.MENU);
        });
        context.waitFor(client -> client.getMusicManager().getCurrentMusicTranslationKey() != null);
        context.waitTicks(10);
        context.runOnClient(client -> {
            check(!((PauseScreen) client.gui.screen()).rendersNowPlayingToast(), "cassette replaces duplicate pause song line");
            check(cassette(client.gui.screen()).getX() == Theme.MARGIN
                    && cassette(client.gui.screen()).getBottom() == client.gui.screen().height - Theme.MENU_FOOTER,
                    "cassette at bottom left of pause menu");
            String key = client.getMusicManager().getCurrentMusicTranslationKey();
            check(cassette(client.gui.screen()).status().getString().equals(
                    net.minecraft.network.chat.Component.translatable(key.replace('/', '.')).getString()),
                    "cassette owns live vanilla song title");
        });
        context.takeScreenshot("whostoppedthemusic-cassette-vanilla-line");
        clickWidget(context, context.computeOnClient(client -> cassette(client.gui.screen())));
        context.waitForScreen(MusicScreen.class);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(PauseScreen.class);
        context.runOnClient(client -> {
            client.options.musicToast().set(MusicToastDisplayState.NEVER);
            String key = client.getMusicManager().getCurrentMusicTranslationKey();
            check(cassette(client.gui.screen()).status().getString().equals(
                    net.minecraft.network.chat.Component.translatable(key.replace('/', '.')).getString()), "cassette shows the real song");
            var instance = ((MusicManagerAccessor) client.getMusicManager()).whostoppedthemusic$currentMusic();
            check(client.getSoundManager().isActive(instance), "vanilla sound instance remains active");
        });
        context.takeScreenshot("whostoppedthemusic-cassette-song");
        context.runOnClient(client -> {
            client.getMusicManager().stopPlaying();
            check(cassette(client.gui.screen()).status().getString().equals(
                    net.minecraft.network.chat.Component.translatable("whostoppedthemusic.cassette.silence").getString()), "truthful silence");
        });
        context.takeScreenshot("whostoppedthemusic-cassette-silence");
    }

    private static void checkTransport(ClientGameTestContext context) {
        checkDelayedTransport(context);
        // Let vanilla's real tick select the source; never manually start the main transport case.
        context.runOnClient(client -> {
            client.options.musicFrequency().set(MusicFrequency.DEFAULT);
            client.options.musicFrequency().set(MusicFrequency.CONSTANT);
        });
        context.waitFor(client -> MusicDirector.get(client).active());
        var old = context.computeOnClient(client -> MusicDirector.get(client).current());
        context.waitFor(client -> channelState(client, old, true).join());
        var topNext = context.computeOnClient(client -> Screens.getWidgets(client.gui.screen()).stream()
                .filter(w -> w.getMessage().getString().equals("Next")).findFirst().orElseThrow());
        clickWidget(context, topNext);
        context.waitFor(client -> channelStopped(client, old));
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        var next = context.computeOnClient(client -> MusicDirector.get(client).current());
        context.runOnClient(client -> {
            var director = MusicDirector.get(client);
            check(director.current() != old, "top Next replaces actual sound instance");
            check(director.current().getIdentifier().equals(client.getSituationalMusic().sound().value().location()),
                    "Next uses the actual situational vanilla pool");
        });
        clickWidget(context, context.computeOnClient(client -> cassette(client.gui.screen())));
        context.waitForScreen(MusicScreen.class);
        context.runOnClient(client -> {
            var controls = Screens.getWidgets(client.gui.screen());
            check(controls.size() == 8, "Vanilla controls omit Previous and retain Done");
            check(controls.stream().noneMatch(w -> w.getMessage().getString().equals("<")), "no previous-track button");
            var director = MusicDirector.get(client);
            var resolved = director.current().getSound().getLocation().toShortLanguageKey().replace('/', '.');
            check(director.status().getString().equals(net.minecraft.network.chat.Component.translatable(resolved).getString()),
                    "title comes from the resolved file, not the situational pool");
        });
        var music = context.computeOnClient(client -> MusicDirector.get(client).current());
        context.waitFor(client -> channelState(client, music, true).join());
        var record = context.computeOnClient(client -> {
            var sound = SimpleSoundInstance.forJukeboxSong(SoundEvent.createVariableRangeEvent(
                    Identifier.withDefaultNamespace("music_disc.cat")), Vec3.ZERO);
            client.getSoundManager().play(sound);
            return sound;
        });
        context.waitFor(client -> channelState(client, record, true).join());
        var effect = context.computeOnClient(client -> {
            var sound = new SimpleSoundInstance(Identifier.withDefaultNamespace("music_disc.cat"), SoundSource.AMBIENT,
                    1, 1, net.minecraft.util.RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
            client.getSoundManager().play(sound);
            return sound;
        });
        context.waitFor(client -> channelState(client, effect, true).join());
        clickWidget(context, widget(context, 4));
        context.waitFor(client -> channelState(client, music, false).join());
        context.runOnClient(client -> {
            check(MusicDirector.get(client).paused(), "pause holds the current vanilla instance");
            check(MusicDirector.get(client).status().getString().startsWith("Paused:"), "truthful paused label");
            client.getSoundManager().resume();
        });
        context.waitTicks(30);
        context.runOnClient(client -> {
            check(MusicDirector.get(client).current() == music && MusicDirector.get(client).paused(),
                    "pause survives automatic vanilla ticks without replacement");
            check(channelState(client, music, false).join(), "global menu resume cannot unpause held music");
            check(channelState(client, record, true).join(), "Pause never touches Records");
            check(channelState(client, effect, true).join(), "Pause never touches non-music effects");
        });
        context.getInput().setCursorPos(0, 0);
        context.waitTicks(2);
        context.takeScreenshot("whostoppedthemusic-paused");
        clickWidget(context, widget(context, 4));
        context.waitFor(client -> channelState(client, music, true).join());
        context.runOnClient(client -> check(MusicDirector.get(client).current() == music
                && !MusicDirector.get(client).paused(), "Resume continues the same channel, not a restart"));
        clickWidget(context, widget(context, 4));
        context.waitFor(client -> channelState(client, music, false).join());
        clickWidget(context, widget(context, 5));
        context.waitFor(client -> channelStopped(client, music));
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> {
            check(MusicDirector.get(client).current() != music, "screen Next starts actual next music");
            check(!MusicDirector.get(client).paused(), "Next clears the outgoing song's pause");
            check(channelState(client, record, true).join(), "Next never stops Records");
            check(channelState(client, effect, true).join(), "Next never stops non-music effects");
            client.getSoundManager().stop(record);
            client.getSoundManager().stop(effect);
        });
        // The same option callbacks used by vanilla must drive its automatic loop.
        var beforeConstant = context.computeOnClient(client -> MusicDirector.get(client).current());
        context.runOnClient(client -> client.options.musicFrequency().set(MusicFrequency.FREQUENT));
        clickWidget(context, widget(context, 2));
        context.runOnClient(client -> {
            check(client.options.musicFrequency().get() == MusicFrequency.CONSTANT,
                    "Frequency button selects vanilla Constant");
            client.getSoundManager().stop(MusicDirector.get(client).current());
        });
        context.waitFor(client -> channelStopped(client, beforeConstant)
                && MusicDirector.get(client).current() != beforeConstant && MusicDirector.get(client).active()
                && channelState(client, MusicDirector.get(client).current(), true).join());
        var automatic = context.computeOnClient(client -> MusicDirector.get(client).current());
        var slider = widget(context, 6);
        clickWidget(context, slider);
        context.getInput().pressKey(InputConstants.KEY_HOME);
        context.runOnClient(client -> check(client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get() == 0,
                "Music slider keyboard Home sets vanilla zero volume"));
        context.waitFor(client -> channelGain(client, automatic).join() == 0.0f);
        context.runOnClient(client -> {
            check(client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get() == 0,
                    "Music slider mutes the actual vanilla source");
            check(client.options.getSoundSourceOptionInstance(SoundSource.MASTER).get() == 0.7,
                    "Music slider leaves Master unchanged");
        });
        context.getInput().pressKey(InputConstants.KEY_END);
        context.waitFor(client -> MusicDirector.get(client).current() == automatic
                && channelState(client, automatic, true).join() && channelGain(client, automatic).join() > 0.0f);
        context.runOnClient(client -> check(client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get() == 1,
                "unmuting restores automatic vanilla playback"));
        context.getInput().setCursorPos(0, 0);
        context.waitTicks(2);
        context.takeScreenshot("whostoppedthemusic-transport");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(PauseScreen.class);
    }

    private static void checkDelayedTransport(ClientGameTestContext context) {
        var stopGate = new CompletableFuture<Void>();
        context.runOnClient(client -> {
            client.getMusicManager().stopPlaying();
            streamGate = stopGate;
            streamReady = new CompletableFuture<>();
            client.options.musicFrequency().set(MusicFrequency.DEFAULT);
            client.options.musicFrequency().set(MusicFrequency.CONSTANT);
        });
        context.waitFor(client -> streamReady.isDone());
        var old = context.computeOnClient(client -> MusicDirector.get(client).current());
        context.runOnClient(client -> {
            check(streamGate == null, "vanilla stream startup is held before channel.play");
            MusicDirector.get(client).next();
            stopGate.complete(null);
        });
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        boolean stopped = context.computeOnClient(client -> channelStopped(client, old));
        var pauseGate = new CompletableFuture<Void>();
        context.runOnClient(client -> {
            client.getMusicManager().stopPlaying();
            streamGate = pauseGate;
            streamReady = new CompletableFuture<>();
            client.options.musicFrequency().set(MusicFrequency.DEFAULT);
            client.options.musicFrequency().set(MusicFrequency.CONSTANT);
        });
        context.waitFor(client -> streamReady.isDone());
        var music = context.computeOnClient(client -> MusicDirector.get(client).current());
        context.runOnClient(client -> {
            MusicDirector.get(client).togglePlayback();
            pauseGate.complete(null);
        });
        context.waitTicks(20);
        boolean held = context.computeOnClient(client -> MusicDirector.get(client).current() == music
                && MusicDirector.get(client).paused() && channelState(client, music, false).join());
        LoggerFactory.getLogger("whostoppedthemusic-test").info(
                "Delayed real-channel results: outgoingStopped={}, incomingPaused={}", stopped, held);
        check(stopped, "Next must stop the outgoing source even when its stream finishes after stop");
        check(held, "Pause before stream startup must hold the actual source paused");
        context.runOnClient(client -> MusicDirector.get(client).togglePlayback());
        context.waitFor(client -> channelState(client, music, true).join());
        context.runOnClient(client -> client.getMusicManager().stopPlaying());
        LoggerFactory.getLogger("whostoppedthemusic-test").info(
                "TRANSPORT CHANNEL PASS: delayed outgoing stream stopped; delayed incoming stream paused then resumed");
    }

    private static boolean channelStopped(net.minecraft.client.Minecraft client, SoundInstance sound) {
        var engine = ((SoundManagerAccessor) client.getSoundManager()).whostoppedthemusic$engine();
        var handle = ((SoundEngineAccessor) engine).whostoppedthemusic$channels().get(sound);
        if (handle == null || handle.isStopped()) {
            return true;
        }
        // ChannelHandle.execute drops callbacks after release. Inspect on the sound executor
        // so cleanup between the initial check and execution still completes this assertion.
        try {
            var executorField = engine.getClass().getDeclaredField("executor");
            var channelField = handle.getClass().getDeclaredField("channel");
            executorField.setAccessible(true);
            channelField.setAccessible(true);
            return CompletableFuture.supplyAsync(() -> {
                try {
                    var channel = (com.mojang.blaze3d.audio.Channel) channelField.get(handle);
                    return channel == null || !channel.playing() && channel.stopped();
                } catch (IllegalAccessException failure) { throw new AssertionError(failure); }
            }, (java.util.concurrent.Executor) executorField.get(engine))
                    .orTimeout(5, java.util.concurrent.TimeUnit.SECONDS).join();
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    static CompletableFuture<Boolean> channelState(net.minecraft.client.Minecraft client,
                                                          SoundInstance sound, boolean playing) {
        var engine = ((SoundManagerAccessor) client.getSoundManager()).whostoppedthemusic$engine();
        var handle = ((SoundEngineAccessor) engine).whostoppedthemusic$channels().get(sound);
        if (handle == null || handle.isStopped()) {
            return CompletableFuture.completedFuture(false);
        }
        var result = new CompletableFuture<Boolean>();
        handle.execute(channel -> result.complete(playing ? channel.playing() : !channel.playing() && !channel.stopped()));
        return result.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    static CompletableFuture<Float> channelGain(net.minecraft.client.Minecraft client, SoundInstance sound) {
        var engine = ((SoundManagerAccessor) client.getSoundManager()).whostoppedthemusic$engine();
        var handle = ((SoundEngineAccessor) engine).whostoppedthemusic$channels().get(sound);
        var result = new CompletableFuture<Float>();
        check(handle != null && !handle.isStopped(), "volume check requires a real allocated source");
        handle.execute(channel -> {
            try {
                var source = com.mojang.blaze3d.audio.Channel.class.getDeclaredField("source");
                source.setAccessible(true);
                int id = source.getInt(channel);
                check(org.lwjgl.openal.AL10.alGetError() == 0, "clean native boundary before gain source=" + id);
                float gain = org.lwjgl.openal.AL10.alGetSourcef(id, org.lwjgl.openal.AL10.AL_GAIN);
                check(org.lwjgl.openal.AL10.alGetError() == 0, "gain native query source=" + id);
                result.complete(gain);
            } catch (ReflectiveOperationException exception) {
                result.completeExceptionally(exception);
            }
        });
        return result.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static AbstractWidget widget(ClientGameTestContext context, int index) {
        return context.computeOnClient(client -> Screens.getWidgets(client.gui.screen()).get(index));
    }

    private static CassetteWidget cassette(net.minecraft.client.gui.screens.Screen screen) {
        return Screens.getWidgets(screen).stream().filter(CassetteWidget.class::isInstance)
                .map(CassetteWidget.class::cast).findFirst().orElseThrow();
    }

    private static void clickWidget(ClientGameTestContext context, AbstractWidget widget) {
        double[] position = context.computeOnClient(client -> new double[]{
            (widget.getX() + widget.getWidth() / 2.0) * client.getWindow().getGuiScale(),
            (widget.getY() + widget.getHeight() / 2.0) * client.getWindow().getGuiScale()
        });
        context.getInput().setCursorPos(position[0], position[1]);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }

    private static void assertWidgetsFit(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var screen = client.gui.screen();
            var widgets = Screens.getWidgets(screen);
            if (screen instanceof MusicScreen && WhoStoppedTheMusic.settings().mode() == Mode.VANILLA) {
                var tape = widgets.get(0);
                var mode = widgets.get(1);
                var volume = widgets.get(6);
                check(volume instanceof MusicVolumeSlider, "vertical Music-only control at far right");
                check(tape.getRight() < mode.getX() && mode.getRight() < volume.getX(), "left tape, right controls, far-right volume");
                check(widgets.get(2).getY() > mode.getY() && widgets.get(3).getY() > widgets.get(2).getY(),
                        "Frequency and Jukebox below mode");
            }
            for (int i = 0; i < widgets.size(); i++) {
                var a = widgets.get(i);
                if (!a.visible) continue;
                check(a.getX() >= 0 && a.getY() >= 0 && a.getRight() <= screen.width && a.getBottom() <= screen.height,
                        "widget stays inside the small screen: " + a.getMessage().getString());
                for (int j = i + 1; j < widgets.size(); j++) {
                    var b = widgets.get(j);
                    if (!b.visible) continue;
                    check(a.getRight() <= b.getX() || b.getRight() <= a.getX() || a.getBottom() <= b.getY() || b.getBottom() <= a.getY(),
                            "widgets must not overlap: " + a.getMessage().getString() + " / " + b.getMessage().getString());
                }
            }
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
