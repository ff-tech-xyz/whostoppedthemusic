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
import xyz.pyrehaven.whostoppedthemusic.ui.MusicScreen;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicVolumeSlider;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;
import xyz.pyrehaven.whostoppedthemusic.ui.widget.TrackList;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
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
                || Boolean.getBoolean("whostoppedthemusic.basicUiOnly")) {
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
                "WHOSTOPPEDTHEMUSIC CLIENT PASS: first-run Constant, Vanilla UI, M/rebind, options/persistence, top cassette, live song/silence, Play/Next/Pause/Resume, music-only volume, inert mode, 320x240");
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
            check(list.children().stream().allMatch(row -> row.children().size() == 2), "ranked search has no headers");
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
            check(list.children().getFirst().children().size() == 2, "first ranked result is a song");
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
                    && w.getMessage().getString().equals("C418 - Sweden")), "current-song cassette below transport updates live");
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
                .filter(w -> w.getY() == Theme.BASIC_TOP + Theme.ROW * 3 && w.getMessage().getString().equals(">"))
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
                    && list.children().stream().allMatch(row -> row.children().size() == 2), "search filters without section headers");
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
        clickWidget(context, namedWidget(context, ">", Theme.BASIC_TOP + Theme.ROW * 3));
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        var first = context.computeOnClient(client -> MusicDirector.get(client).current().getSound().getLocation());
        clickWidget(context, basicNext(context));
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> check(!MusicDirector.get(client).current().getSound().getLocation().equals(first), "ordered Next uses other ticked track"));
        clickWidget(context, namedWidget(context, "<", Theme.BASIC_TOP + Theme.ROW * 3));
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> check(MusicDirector.get(client).current().getSound().getLocation().equals(first), "Previous restores exact historical track"));
        clickWidget(context, namedWidget(context, "[]", Theme.BASIC_TOP + Theme.ROW * 3));
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
            check(client.gui.toastManager().getToast(xyz.pyrehaven.whostoppedthemusic.ui.SongToast.class,
                    net.minecraft.client.gui.components.toasts.Toast.NO_TOKEN) != null, "single replacement SongToast exists");
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
            var toast = client.gui.toastManager().getToast(xyz.pyrehaven.whostoppedthemusic.ui.SongToast.class,
                    net.minecraft.client.gui.components.toasts.Toast.NO_TOKEN);
            check(toast == null || toast.getWantedVisibility() == net.minecraft.client.gui.components.toasts.Toast.Visibility.HIDE,
                    "Music Toast Never hides replacement");
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

    private static void checkSingleSongToast(net.minecraft.client.Minecraft client) {
        try {
            var manager = client.gui.toastManager();
            var queued = manager.getClass().getDeclaredField("queued");
            var visible = manager.getClass().getDeclaredField("visibleToasts");
            var vanilla = manager.getClass().getDeclaredField("nowPlayingToast");
            queued.setAccessible(true);
            visible.setAccessible(true);
            vanilla.setAccessible(true);
            long count = ((java.util.Collection<?>) queued.get(manager)).stream()
                    .filter(xyz.pyrehaven.whostoppedthemusic.ui.SongToast.class::isInstance).count();
            for (var instance : (java.util.List<?>) visible.get(manager)) {
                var field = instance.getClass().getDeclaredField("toast");
                field.setAccessible(true);
                if (field.get(instance) instanceof xyz.pyrehaven.whostoppedthemusic.ui.SongToast) count++;
            }
            check(count == 1, "exactly one queued/visible SongToast");
            var instance = vanilla.get(manager);
            if (instance != null) {
                var field = instance.getClass().getDeclaredField("toast");
                field.setAccessible(true);
                check(((net.minecraft.client.gui.components.toasts.Toast) field.get(instance)).getWantedVisibility()
                        == net.minecraft.client.gui.components.toasts.Toast.Visibility.HIDE, "vanilla toast hidden in Basic");
            }
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void checkSharedControls(ClientGameTestContext context, Path settingsFile) {
        double master = context.computeOnClient(client -> client.options.getSoundSourceOptionInstance(SoundSource.MASTER).get());
        var toast = context.computeOnClient(client -> client.options.musicToast().get());
        clickWidget(context, widget(context, 1));
        context.runOnClient(client -> {
            check(WhoStoppedTheMusic.settings().mode() == Mode.BASIC, "mode cycles to Basic");
        });
        clickWidget(context, widget(context, 0));
        context.runOnClient(client -> {
            check(Screens.getWidgets(client.gui.screen()).get(1).getMessage().getString().equals("Vanilla"), "mode cycles back to Vanilla");
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
            check(cassette(client.gui.screen()).getY() == 6, "cassette at top of pause menu");
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
                .filter(w -> w.getMessage().getString().equals(">") && w.getY() == 11).findFirst().orElseThrow());
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
        var result = new CompletableFuture<Boolean>();
        handle.execute(channel -> {
            LoggerFactory.getLogger("whostoppedthemusic-test").info("Outgoing source playing={}, stopped={}",
                    channel.playing(), channel.stopped());
            result.complete(!channel.playing() && channel.stopped());
        });
        return result.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS).join();
    }

    private static CompletableFuture<Boolean> channelState(net.minecraft.client.Minecraft client,
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

    private static CompletableFuture<Float> channelGain(net.minecraft.client.Minecraft client, SoundInstance sound) {
        var engine = ((SoundManagerAccessor) client.getSoundManager()).whostoppedthemusic$engine();
        var handle = ((SoundEngineAccessor) engine).whostoppedthemusic$channels().get(sound);
        var result = new CompletableFuture<Float>();
        check(handle != null && !handle.isStopped(), "volume check requires a real allocated source");
        handle.execute(channel -> {
            try {
                var source = com.mojang.blaze3d.audio.Channel.class.getDeclaredField("source");
                source.setAccessible(true);
                result.complete(org.lwjgl.openal.AL10.alGetSourcef(source.getInt(channel), org.lwjgl.openal.AL10.AL_GAIN));
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
