package xyz.pyrehaven.whostoppedthemusic;

import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.check;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.labelPixels;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.library;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.search;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.songs;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.track;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.winner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.MusicToastDisplayState;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.toasts.NowPlayingToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;
import xyz.pyrehaven.whostoppedthemusic.music.Deck;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.custom.*;
import xyz.pyrehaven.whostoppedthemusic.ui.*;
import xyz.pyrehaven.whostoppedthemusic.ui.custom.*;

final class ScenarioArtworkGameTest {
    static void artwork(ClientGameTestContext context) {
        var biome = context.computeOnClient(client -> SituationReader.read(client).biome());
        context.runOnClient(client -> {
            var folder = client.gameDirectory.toPath().resolve("music");
            try (var input = CustomUiGameTest.class.getResourceAsStream("/audio/stereo.mp3")) {
                Files.createDirectories(folder);
                Files.copy(input, folder.resolve("Artwork-check.mp3"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException error) { throw new UncheckedIOException(error); }
            WhoStoppedTheMusic.settings().setMode(Mode.CUSTOM);
            var d = MusicDirector.get(client).customPlayback().defaults(biome).config();
            WhoStoppedTheMusic.settings().setScenario(biome, d.withPriority(10).withSongs(List.of(
                    new Deck.Entry("minecraft:music/game/sweden", 1),
                    new Deck.Entry("minecraft:records/pigstep", 1))), d);
        });
        var refresh = context.computeOnClient(client -> WhoStoppedTheMusic.LIBRARY.refreshSongs());
        context.waitFor(client -> refresh.isDone());
        winner(context, biome);
        for (Mode mode : Mode.values()) {
            context.runOnClient(client -> {
                WhoStoppedTheMusic.settings().setMode(mode);
                var director = MusicDirector.get(client);
                director.syncMode();
                client.gui.setScreen(null);
                client.options.musicToast().set(MusicToastDisplayState.PAUSE_AND_TOAST);
                client.options.notificationDisplayTime().set(2.0);
                if (mode == Mode.VANILLA) director.next();
                else director.play(track("custom:Artwork-check.mp3"));
            });
            context.waitFor(client -> MusicDirector.get(client).advancing());
            context.runOnClient(client -> {
                GameTestSupport.checkSingleSongToast(client);
                var toast = GameTestSupport.nowPlayingToast(client);
                try {
                    var text = toast.getClass().getDeclaredMethod("getNowPlayingString", String.class);
                    text.setAccessible(true);
                    var actual = (Component) text.invoke(null, client.getMusicManager().getCurrentMusicTranslationKey());
                    var metadata = MusicDirector.get(client).metadata();
                    var expected = metadata.artist().getString().isEmpty() ? metadata.title()
                            : metadata.artist().copy().append(" - ").append(metadata.title());
                    check(actual.equals(expected) && !actual.getString().contains("\n"), "native single-line toast uses resolved metadata in " + mode);
                    if (mode != Mode.VANILLA) check(actual.getString().equals("Artwork-check"), "literal imported title, not synthetic pool key");
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                var timed = new NowPlayingToast();
                timed.showToast(client.options);
                timed.update(client.gui.toastManager(), 9999);
                check(timed.getWantedVisibility() == Toast.Visibility.SHOW, "native 2x duration");
                timed.update(client.gui.toastManager(), 10001);
                check(timed.getWantedVisibility() == Toast.Visibility.HIDE, "native duration expires");
            });
            context.waitTicks(15);
            context.takeScreenshot("native-notification-" + mode);
            context.runOnClient(client -> client.options.musicToast().set(MusicToastDisplayState.NEVER));
            context.runOnClient(client -> check(GameTestSupport.nowPlayingToast(client) == null, "Never removes native notification"));
        }
        context.runOnClient(client -> {
            WhoStoppedTheMusic.settings().setMode(Mode.CUSTOM);
            MusicDirector.get(client).syncMode();
        });
        winner(context, biome);
        for (int width : new int[]{960, 320}) {
            context.getInput().resizeWindow(width, width == 320 ? 240 : 720);
            context.runOnClient(client -> client.options.guiScale().set(width == 320 ? 1 : 2));
            context.setScreen(() -> new ScenarioScreen(null, WhoStoppedTheMusic.settings(), biome));
            search(context, "Sweden");
            context.runOnClient(client -> {
                songs(client).selectSong("minecraft:music/game/sweden");
                MusicDirector.get(client).play(track("minecraft:music/game/sweden"));
            });
            context.waitFor(client -> MusicDirector.get(client).displayAdvancing());
            artworkPixels(context, "scenario-" + width + "-playing", true, true);
            var background = context.computeOnClient(client -> MusicDirector.get(client).current());
            context.runOnClient(client -> MusicDirector.get(client).togglePlayback());
            context.waitFor(client -> !MusicDirector.get(client).displayAdvancing());
            artworkPixels(context, "scenario-" + width + "-paused", false, true);
            context.runOnClient(client -> MusicDirector.get(client).preview().toggle(track("minecraft:music/game/sweden")));
            context.waitFor(client -> MusicDirector.get(client).displayAdvancing());
            artworkPixels(context, "scenario-" + width + "-preview", true, true);
            context.runOnClient(client -> {
                MusicDirector.get(client).preview().cancel();
                check(MusicDirector.get(client).current() == background && MusicDirector.get(client).paused(), "preview retains manually paused background");
                MusicDirector.get(client).stop();
            });
            context.waitFor(client -> !MusicDirector.get(client).active());
            artworkPixels(context, "scenario-" + width + "-silent", false, false);
        }
        context.runOnClient(client -> {
            client.gui.setScreen(new PauseScreen(true));
            MusicDirector.get(client).next();
        });
        context.waitFor(client -> MusicDirector.get(client).advancing());
        context.waitTicks(3);
        context.runOnClient(client -> check(client.gui.screen().children().stream().anyMatch(CassetteWidget.class::isInstance)
                && !((PauseScreen) client.gui.screen()).rendersNowPlayingToast(), "pause player retained without duplicate song line"));
        context.takeScreenshot("scenario-pause-player-retained");
        MenuGameTest.checkMenuNotes(context, "scenario-pause-player-playing", true);
        context.runOnClient(client -> MusicDirector.get(client).togglePlayback());
        context.waitFor(client -> !MusicDirector.get(client).advancing());
        MenuGameTest.checkMenuNotes(context, "scenario-pause-player-paused", false);
        LoggerFactory.getLogger("whostoppedthemusic-test").info("SCENARIO ARTWORK / VANILLA NOTIFICATION PASS: native top-left toast in all modes, imported metadata, Never/2x duration, scenario case/open red spindles/reels/notes, paused/silent suppression, preview native state, pause player retained at 960/320");
    }

    static void artworkPixels(ClientGameTestContext context, String name, boolean moving, boolean loaded) {
        context.getInput().setCursorPos(0, 0);
        context.waitTicks(3);
        var tape = context.computeOnClient(client -> (AbstractWidget) songs(client).children().getFirst().children().getFirst());
        var geometry = context.computeOnClient(client -> CassetteArtwork.geometry(tape.getX(), tape.getY(), tape.getWidth(), tape.getHeight()));
        CassetteGameTest.checkReelPixels(context, name, geometry, moving, loaded ? Theme.SPINDLE : Theme.BACKGROUND);
        var first = context.takeScreenshot(name + "-case-a");
        context.waitTicks(3);
        var second = context.takeScreenshot(name + "-case-b");
        context.runOnClient(client -> {
            var cassette = (AbstractWidget) library(client).children().getFirst().children().getFirst();
            var a = labelPixels(client, first, cassette, false);
            var b = labelPixels(client, second, cassette, false);
            check(moving != Arrays.equals(a, b), "case actual motion/freeze: " + name);
            check(Arrays.stream(a).filter(color -> color == Theme.SPINDLE).count() == (loaded ? 8L * (int) client.getWindow().getGuiScale() * (int) client.getWindow().getGuiScale() : 0), "case has two exact red spindle dots only for its loaded source");
            try {
                var image = ImageIO.read(first.toFile());
                int scale = (int) client.getWindow().getGuiScale();
                int glow = 0;
                for (int y = (geometry.labelY() + geometry.labelHeight() + 1) * scale; y < (geometry.y() + geometry.height() - 1) * scale; y++) {
                    for (int x = geometry.x() * scale; x < (geometry.x() + geometry.width()) * scale; x++) {
                        if (image.getRGB(x, y) == Theme.GLOW) glow++;
                    }
                }
                check(moving ? glow > 0 : glow == 0, "open tape notes only while advancing: " + name);
            } catch (IOException error) { throw new UncheckedIOException(error); }
        });
    }
}
