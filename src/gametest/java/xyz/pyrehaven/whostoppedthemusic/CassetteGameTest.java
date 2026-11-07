package xyz.pyrehaven.whostoppedthemusic;

import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.assertWidgetsFit;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.cassette;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.channelState;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.check;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.checkSingleSongToast;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.trackList;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.MusicToastDisplayState;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Util;
import org.slf4j.LoggerFactory;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.Track;
import xyz.pyrehaven.whostoppedthemusic.music.TrackLibrary;
import xyz.pyrehaven.whostoppedthemusic.music.TrackNames;
import xyz.pyrehaven.whostoppedthemusic.ui.CassetteArtwork;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicScreen;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;
import xyz.pyrehaven.whostoppedthemusic.ui.TransportButton;

final class CassetteGameTest {
    static void checkCassetteLabels(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> WhoStoppedTheMusic.LIBRARY.tracks());
        var translated = Component.translatable("music.game.sweden");
        var shortName = Track.metadata(translated);
        check(shortName.title().getString().equals("Sweden") && shortName.artist().getString().equals("C418"),
                "translated component supplies separate metadata");
        var styled = Track.metadata(Component.literal("Artist-Name - ").append(Component.literal("Night-Song - Reprise")
                .withStyle(ChatFormatting.BOLD)));
        check(styled.title().getString().equals("Night-Song - Reprise")
                && styled.title().toFlatList(Style.EMPTY).getFirst().getStyle().isBold(),
                "hyphens and component sibling styles survive naming");
        var longName = new Track.Metadata(Component.literal("Night-Song - Reprise across the very distant mountains"),
                Component.literal("Artist-Name and the extraordinarily long orchestra credit"));
        var unknown = Track.metadata(Component.translatableWithFallback("test.missing.modded.name",
                TrackNames.prettify("other:music/forest_theme")));
        check(unknown.title().getString().equals("Forest Theme") && unknown.artist().getString().isEmpty(),
                "modded fallback keeps artist genuinely blank");
        var fixtures = List.of(shortName, longName, unknown);
        try {
            for (int size : new int[]{960, 320}) {
                context.getInput().resizeWindow(size, size == 320 ? 240 : 720);
                for (int index = 0; index < fixtures.size(); index++) {
                    var fixture = fixtures.get(index);
                    context.runOnClient(client -> {
                        var tracks = original.stream().map(track -> new Track(track.id(), track.kind(), track.title(),
                                track.sound(), fixture, track.local())).toList();
                        setTestTracks(tracks);
                        check(tracks.getFirst().metadata() == fixture, "explicit metadata takes precedence over display convention");
                    });
                    for (Mode mode : Mode.values()) {
                        String capture = "labels-" + size + "-" + index + "-" + mode.name().toLowerCase(Locale.ROOT);
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
                        long toastStart = Util.getMillis();
                        context.waitFor(client -> Util.getMillis() - toastStart >= 700);
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

    static void setTestTracks(List<Track> tracks) {
        try {
            var field = TrackLibrary.class.getDeclaredField("tracks");
            field.setAccessible(true);
            field.set(WhoStoppedTheMusic.LIBRARY, tracks);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    static AbstractWidget playerTape(ClientGameTestContext context, boolean compact) {
        return context.computeOnClient(client -> compact ? cassette(client.gui.screen())
                : Screens.getWidgets(client.gui.screen()).stream().filter(widget -> widget instanceof Button
                        && !(widget instanceof TransportButton) && widget.getHeight() > Theme.CONTROL_HEIGHT).findFirst().orElseThrow());
    }

    static CassetteArtwork.Geometry tapeGeometry(ClientGameTestContext context, AbstractWidget tape, boolean compact) {
        return context.computeOnClient(client -> CassetteArtwork.geometry(tape.getX(), tape.getY(), tape.getWidth(),
                tape.getHeight()));
    }

    static void checkPlayerReels(ClientGameTestContext context, String name, boolean moves, boolean compact) {
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
            var a = ImageIO.read(first.toFile());
            var b = ImageIO.read(second.toFile());
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
        } catch (IOException exception) { throw new AssertionError(exception); }
    }

    static void checkEmptyPlayer(ClientGameTestContext context, String name, boolean compact) {
        var tape = playerTape(context, compact);
        var geometry = tapeGeometry(context, tape, compact);
        checkReelPixels(context, name, geometry, false, Theme.SPINDLE);
        Path capture = context.takeScreenshot(name + "-outline");
        int scale = context.computeOnClient(client -> (int) client.getWindow().getGuiScale());
        try {
            var image = ImageIO.read(capture.toFile());
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
        } catch (IOException exception) { throw new AssertionError(exception); }
    }
}
