package xyz.pyrehaven.whostoppedthemusic;

import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.assertWidgetsFit;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.channelGain;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.channelState;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.check;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.checkSingleSongToast;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.clickWidget;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.namedWidget;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.nowPlayingToast;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.searchBox;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.trackList;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Comparator;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MusicToastDisplayState;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager.MusicFrequency;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.JukeboxSongs;
import org.slf4j.LoggerFactory;
import xyz.pyrehaven.whostoppedthemusic.mixin.ClientJukeboxAccessor;
import xyz.pyrehaven.whostoppedthemusic.mixin.ClientLevelAccessor;
import xyz.pyrehaven.whostoppedthemusic.music.JukeboxWatcher;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.Track;
import xyz.pyrehaven.whostoppedthemusic.music.TrackSoundInstance;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicScreen;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;
import xyz.pyrehaven.whostoppedthemusic.ui.widget.TrackList;

final class BasicGameTest {
    static void checkBasicUi(ClientGameTestContext context) {
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
            check(MusicDirector.get(client).metadata().equals(Track.metadata(Component.literal("C418 - Sweden"))), "row Play starts exact ranked song on a real channel");
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

    static AbstractWidget basicNext(ClientGameTestContext context) {
        return context.computeOnClient(client -> Screens.getWidgets(client.gui.screen()).stream()
                .filter(w -> w.getY() == Theme.BASIC_TOP + Theme.ROW * 3 && w.getMessage().getString().equals("Next"))
                .max(Comparator.comparingInt(AbstractWidget::getX)).orElseThrow());
    }

    static void checkBasic(ClientGameTestContext context) {
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
            clickWidget(context, namedWidget(context, "Vanilla"));
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
                    instanceof TrackSoundInstance));
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

    static void checkBasicControls(ClientGameTestContext context) {
        context.getInput().resizeWindow(320, 240);
        context.waitTicks(5);
        assertWidgetsFit(context);
        context.takeScreenshot("whostoppedthemusic-basic-small");
        clickWidget(context, namedWidget(context, "Shuffle:"));
        context.runOnClient(client -> check(WhoStoppedTheMusic.settings().shuffle(), "Shuffle toggles on"));
        clickWidget(context, namedWidget(context, "Shuffle:"));
        context.runOnClient(client -> {
            var search = Screens.getWidgets(client.gui.screen()).stream().filter(EditBox.class::isInstance)
                    .map(EditBox.class::cast).findFirst().orElseThrow();
            search.setValue("cat");
            var list = Screens.getWidgets(client.gui.screen()).stream().filter(TrackList.class::isInstance)
                    .map(TrackList.class::cast).findFirst().orElseThrow();
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
        context.runOnClient(client -> Screens.getWidgets(client.gui.screen()).stream().filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).findFirst().orElseThrow().setValue("Pigstep"));
        context.waitTicks(3);
        var row = context.computeOnClient(client -> Screens.getWidgets(client.gui.screen()).stream()
                .filter(TrackList.class::isInstance)
                .map(TrackList.class::cast).findFirst().orElseThrow().children().getLast());
        clickWidget(context, (AbstractWidget) row.children().getFirst());
        context.runOnClient(client -> check(!Settings.load(FabricLoader.getInstance().getConfigDir().resolve("whostoppedthemusic.json"))
                .ticked("minecraft:records/pigstep"), "row tick writes saved selection"));
        clickWidget(context, (AbstractWidget) row.children().getFirst());
        clickWidget(context, (AbstractWidget) row.children().getLast());
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> {
            check(MusicDirector.get(client).metadata().equals(Track.metadata(Component.literal("Lena Raine - Pigstep"))), "row Play uses disc title on Music source");
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
            check(toast == null || toast.getWantedVisibility() == Toast.Visibility.HIDE,
                    "Music Toast Never hides vanilla notification");
            client.options.musicToast().set(MusicToastDisplayState.PAUSE_AND_TOAST);
        });
    }

    static void checkJukebox(ClientGameTestContext context, TestServerContext server) {
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
            var registry = client.level.registryAccess().lookupOrThrow(Registries.JUKEBOX_SONG);
            client.level.levelEvent(null, 1010, pos, registry.getId(registry.getValueOrThrow(JukeboxSongs.PIGSTEP)));
        });
        var records = context.computeOnClient(client -> ((ClientJukeboxAccessor)
                ((ClientLevelAccessor) client.level).whostoppedthemusic$events())
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
        context.waitFor(client -> !JukeboxWatcher.nearby(client));
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        server.runCommand("execute as @a at @s run tp @s ~-80 ~ ~");
        context.waitFor(client -> JukeboxWatcher.nearby(client));
        context.waitTicks(5);
        context.runOnClient(client -> client.level.levelEvent(null, 1011, pos, 0));
        context.waitFor(client -> !JukeboxWatcher.nearby(client)
                && channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> {
            // Audio completion can precede the server's stop event; stale map entries must not hold music.
            var registry = client.level.registryAccess().lookupOrThrow(Registries.JUKEBOX_SONG);
            client.level.levelEvent(null, 1010, pos, registry.getId(registry.getValueOrThrow(JukeboxSongs.PIGSTEP)));
        });
        var ending = context.computeOnClient(client -> ((ClientJukeboxAccessor)
                ((ClientLevelAccessor) client.level).whostoppedthemusic$events()).whostoppedthemusic$jukeboxes().get(pos));
        context.waitFor(client -> channelState(client, ending, true).join());
        context.runOnClient(client -> client.getSoundManager().stop(ending));
        context.waitTicks(25);
        context.runOnClient(client -> check(!JukeboxWatcher.nearby(client), "ended channel releases hold even before stop event"));
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
    }
}
