package xyz.pyrehaven.whostoppedthemusic;

import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.assertWidgetsFit;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.channelGain;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.channelState;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.channelStopped;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.check;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.trackList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.sounds.MusicManager.MusicFrequency;
import net.minecraft.sounds.SoundSource;
import org.slf4j.LoggerFactory;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.Track;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicScreen;

final class LocalSongsGameTest {
    static void checkOwnSongs(ClientGameTestContext context) {
        context.getInput().resizeWindow(960, 720);
        Path folder = context.computeOnClient(client -> client.gameDirectory.toPath().resolve("music"));
        var parent = context.computeOnClient(client -> client.gui.screen());
        try {
            Files.createDirectories(folder);
            for (var entry : Map.of("stereo.mp3", "Artist - Signal.MP3", "mono.ogg", "Plain.ogg").entrySet()) {
                try (var input = ClientSmokeGameTest.class.getResourceAsStream("/audio/" + entry.getKey())) {
                    Files.copy(input, folder.resolve(entry.getValue()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.writeString(folder.resolve("Broken.mp3"), "This is a text file, not an MP3");
            Files.writeString(folder.resolve("Broken.ogg"), "This is not OGG either");
        } catch (IOException error) { throw new AssertionError(error); }
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
            var invalidRows = list.children().stream().filter(row -> row.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                    .anyMatch(widget -> widget.getMessage().getString().lines().findFirst().orElse("").equals("Broken"))).toList();
            check(invalidRows.size() == 2, "both invalid formats remain visible beside valid matching soundtrack songs");
            for (var row : invalidRows) {
                var play = row.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                        .filter(widget -> widget.getMessage().getString().startsWith("Can't play:")).findFirst().orElseThrow();
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
        try { Files.delete(mp3.local().song().file()); }
        catch (IOException error) { throw new AssertionError(error); }
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
}
