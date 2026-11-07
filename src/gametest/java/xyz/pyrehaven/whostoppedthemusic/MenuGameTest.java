package xyz.pyrehaven.whostoppedthemusic;

import static xyz.pyrehaven.whostoppedthemusic.CassetteGameTest.checkEmptyPlayer;
import static xyz.pyrehaven.whostoppedthemusic.CassetteGameTest.checkPlayerReels;
import static xyz.pyrehaven.whostoppedthemusic.CassetteGameTest.playerTape;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.assertWidgetsFit;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.cassette;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.check;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.clickWidget;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.MusicToastDisplayState;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.sounds.SoundSource;
import org.slf4j.LoggerFactory;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.ui.CassetteArtwork;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicScreen;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;

final class MenuGameTest {
    static void checkMenuPlayers(ClientGameTestContext context) {
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

    static void assertMenuFit(ClientGameTestContext context) {
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
            var a = ImageIO.read(first.toFile());
            var b = ImageIO.read(second.toFile());
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
        } catch (IOException exception) { throw new AssertionError(exception); }
    }
}
