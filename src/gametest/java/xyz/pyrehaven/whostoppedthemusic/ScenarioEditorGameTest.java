package xyz.pyrehaven.whostoppedthemusic;

import static xyz.pyrehaven.whostoppedthemusic.CustomUiGameTest.settingTooltips;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.check;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.click;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.config;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.configBytes;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.field;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.fit;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.labelPixels;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.library;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.list;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.popupCancels;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.press;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.rowButtons;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.search;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.smoothWheel;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.songs;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.toggle;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.winner;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.sounds.MusicManager.MusicFrequency;
import org.slf4j.LoggerFactory;
import xyz.pyrehaven.whostoppedthemusic.music.Deck;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.custom.*;
import xyz.pyrehaven.whostoppedthemusic.ui.*;
import xyz.pyrehaven.whostoppedthemusic.ui.custom.*;
import xyz.pyrehaven.whostoppedthemusic.ui.widget.WeightSlider;

final class ScenarioEditorGameTest {
    static void editor(ClientGameTestContext context) {
        var forest = ScenarioId.biome("minecraft:forest");
        var plains = ScenarioId.biome("minecraft:plains");
        context.waitFor(client -> SituationReader.read(client).biome().equals(forest));
        context.runOnClient(client -> {
            var folder = client.gameDirectory.toPath().resolve("music");
            try {
                Files.createDirectories(folder);
                for (var entry : Map.of("stereo.mp3", "Editor-check.mp3", "mono.ogg", "Editor-check.ogg").entrySet()) {
                    try (var input = CustomUiGameTest.class.getResourceAsStream("/audio/" + entry.getKey())) {
                        Files.copy(input, folder.resolve(entry.getValue()), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                Files.copy(folder.resolve("Editor-check.mp3"), folder.resolve("Hover-check cassette title longer than the library window.mp3"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException error) { throw new UncheckedIOException(error); }
            var settings = WhoStoppedTheMusic.settings();
            var defaults = MusicDirector.get(client).customPlayback().defaults(forest).config();
            settings.setScenario(forest, defaults.withSongs(List.of()).withPriority(9).withInterrupt(true), defaults);
        });
        var refresh = context.computeOnClient(client -> WhoStoppedTheMusic.LIBRARY.refreshSongs());
        context.waitFor(client -> refresh.isDone());
        var parent = context.computeOnClient(client -> client.gui.screen());
        context.setScreen(() -> new ScenarioScreen(parent, WhoStoppedTheMusic.settings(), forest));
        context.waitTicks(4);
        settingTooltips(context, true);
        fit(context);
        hoverCase(context);
        drag(context, "Pigstep", true);
        context.runOnClient(client -> check(config(client, forest).songs().size() == 1 && config(client, forest).songs().getFirst().weight() == 10,
                "empty drag starts at 10"));
        var before = configBytes();
        drag(context, "Pigstep", true);
        check(Arrays.equals(before, configBytes()), "duplicate drop leaves exact disk bytes");
        winner(context, forest);
        context.runOnClient(client -> MusicDirector.get(client).next());
        context.waitFor(client -> MusicDirector.get(client).playing(WhoStoppedTheMusic.LIBRARY.tracks().stream()
                .filter(t -> t.id().endsWith("/pigstep")).findFirst().orElseThrow())
                && GameTestSupport.channelState(client, MusicDirector.get(client).current(), true).join());
        drag(context, "Sweden", false);
        context.runOnClient(client -> check(config(client, forest).songs().size() == 1, "outside drop changes nothing"));
        drag(context, "Sweden", true);
        sliderKey(context, InputConstants.KEY_END);
        context.runOnClient(client -> check(config(client, forest).songs().getLast().weight() == 100, "slider End reaches 100 on focused added song"));
        sliderKey(context, InputConstants.KEY_HOME);
        context.runOnClient(client -> check(config(client, forest).songs().getLast().weight() == 1, "slider Home reaches 1"));
        context.runOnClient(client -> check(client.gui.screen().children().stream().filter(WeightSlider.class::isInstance)
                .map(AbstractWidget.class::cast).findFirst().orElseThrow().getMessage().getString().contains("1 in 11 (9.1%)"), "focused chance uses this list's weight total"));
        context.takeScreenshot("editor-forest-normal");
        press(context, "Remove", 0);
        press(context, "Remove", 0);
        context.runOnClient(client -> client.getMusicManager().stopPlaying());
        context.waitFor(client -> MusicDirector.get(client).customPlayback().resolution().silent());
        context.runOnClient(client -> check(config(client, forest).songs().isEmpty(), "remove last produces empty silence"));
        press(context, "Reset to vanilla", 0);
        context.runOnClient(client -> {
            var c = config(client, forest);
            var d = MusicDirector.get(client).customPlayback().defaults(forest).config();
            check(c.songs().equals(d.songs()) && c.priority() == 9 && c.interrupt(), "reset restores songs only");
            check(!WhoStoppedTheMusic.settings().songsEdited(forest, d), "reset clears edited marker");
            WhoStoppedTheMusic.settings().setScenario(plains, config(client, plains).withSongs(List.of(
                    new Deck.Entry("minecraft:records/pigstep", 7))),
                    MusicDirector.get(client).customPlayback().defaults(plains).config());
        });
        press(context, "Copy songs", 0);
        search(context, "biomes");
        smoothWheel(context, "copy picker");
        context.takeScreenshot("editor-copy-picker-normal");
        before = configBytes();
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        check(Arrays.equals(before, configBytes()), "copy cancel leaves bytes unchanged");
        press(context, "Copy songs", 0);
        search(context, "plains");
        context.runOnClient(client -> check(list(client).children().stream().flatMap(r -> r.children().stream()).noneMatch(Toggle.class::isInstance), "copy picker omits toggles"));
        context.waitTicks(3);
        context.runOnClient(client -> click(client, rowButtons(client).stream().filter(b -> b.getMessage().getString().startsWith("Plains")).findFirst().orElseThrow(), 0));
        context.waitForScreen(ScenarioScreen.class);
        context.runOnClient(client -> check(config(client, forest).songs().equals(config(client, plains).songs())
                && config(client, forest).priority() == 9 && config(client, forest).interrupt(), "copy replaces songs and weights only"));
        context.runOnClient(client -> check(songs(client).children().size() == 1
                && songs(client).focusedId().equals("minecraft:records/pigstep"), "picker return refreshes retained cassette rows and focus"));
        drag(context, "Sweden", true);
        context.runOnClient(client -> check(config(client, forest).songs().getLast().weight() == 7, "nonempty drag uses modal weight"));
        for (String extension : List.of("mp3", "ogg")) {
            drag(context, "Editor-check." + extension, true);
            // Search includes IDs; the literal file name is retained in the entry.
            context.runOnClient(client -> {
                var track = WhoStoppedTheMusic.LIBRARY.tracks().stream().filter(t -> t.id().equals("custom:Editor-check." + extension)).findFirst().orElseThrow();
                check(config(client, forest).songs().stream().anyMatch(e -> e.id().equals(track.id())), "own song can be dragged: " + extension);
                var d = MusicDirector.get(client).customPlayback().defaults(forest).config();
                WhoStoppedTheMusic.settings().setScenario(forest, config(client, forest).withSongs(List.of(new Deck.Entry(track.id(), 7))), d);
            });
            context.waitTicks(3);
            context.runOnClient(client -> MusicDirector.get(client).next());
            context.waitFor(client -> GameTestSupport.channelState(client, MusicDirector.get(client).current(), true).join()
                    && MusicDirector.get(client).playing(WhoStoppedTheMusic.LIBRARY.tracks().stream().filter(t -> t.id().equals("custom:Editor-check." + extension)).findFirst().orElseThrow()));
        }
        context.runOnClient(client -> {
            var d = MusicDirector.get(client).customPlayback().defaults(forest).config();
            WhoStoppedTheMusic.settings().setScenario(forest, config(client, forest).withSongs(List.of(
                    new Deck.Entry("absent:music/gone", 25))), d);
        });
        context.waitTicks(4);
        context.runOnClient(client -> check(songs(client).children().stream().flatMap(r -> r.children().stream()).filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast).anyMatch(w -> w.getMessage().getString().contains("Missing")), "missing cassette retained and marked"));
        context.takeScreenshot("editor-missing");
        popupCancels(context, "Frequency:");
        press(context, "Frequency:", 0);
        press(context, "Jukebox:", 0);
        context.runOnClient(client -> {
            var c = config(client, forest);
            check(c.frequency() == ScenarioConfig.Frequency.DEFAULT && c.jukebox() == JukeboxSetting.PLAY_OVER, "editor cycles its fixture's Constant/Pause settings to Default/Play over");
            check(client.options.musicFrequency().get() == MusicFrequency.DEFAULT, "editor leaves global frequency unchanged");
            var thunder = ScenarioId.special(Special.THUNDER);
            var d = MusicDirector.get(client).customPlayback().defaults(thunder).config();
            WhoStoppedTheMusic.settings().setScenario(thunder, config(client, thunder).withSongs(List.of(
                    new Deck.Entry("minecraft:music/game/sweden", 20),
                    new Deck.Entry("minecraft:records/pigstep", 30),
                    new Deck.Entry("custom:Editor-check.mp3", 50))), d);
        });
        for (int scale : List.of(2, 1, 4)) {
            boolean narrow = scale == 1;
            String suffix = scale == 4 ? "-scale4" : narrow ? "-small" : "-normal";
            context.getInput().resizeWindow(scale == 4 ? 1280 : narrow ? 320 : 960, scale == 4 ? 960 : narrow ? 240 : 720);
            context.runOnClient(client -> client.options.guiScale().set(scale));
            for (var special : Special.values()) {
                context.setScreen(() -> new ScenarioScreen(parent, WhoStoppedTheMusic.settings(), ScenarioId.special(special)));
                context.waitTicks(3);
                if (special == Special.THUNDER) {
                    context.runOnClient(client -> songs(client).selectSong("minecraft:records/pigstep"));
                    context.waitTicks(3);
                }
                fit(context);
                context.runOnClient(client -> check(client.getWindow().getGuiScale() == scale, "actual GUI scale " + scale));
                context.runOnClient(client -> {
                    long toggles = client.gui.screen().children().stream().filter(Toggle.class::isInstance).count();
                    boolean fixed = special == Special.MENU || special == Special.CREDITS;
                    boolean weather = List.of(Special.THUNDER, Special.RAIN, Special.SNOW).contains(special);
                    check(toggles == (fixed ? 0 : weather ? 3 : 2), "variant controls: " + special);
                    if (special == Special.UNDERGROUND) {
                        var level = client.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                        level.setValue("-256");
                        check(config(client, ScenarioId.special(special)).yLevel() == -256, "negative whole Y level");
                        level.setValue("bad");
                        check(config(client, ScenarioId.special(special)).yLevel() == -256, "invalid Y is not persisted");
                    }
                    if (!fixed) {
                        var id = ScenarioId.special(special);
                        var previous = config(client, id);

                        click(client, toggle(client, "Active"), 0);
                        check(config(client, id).active() != previous.active(), "active editor toggle");
                        click(client, toggle(client, "Interrupt"), 0);
                        check(config(client, id).interrupt() != previous.interrupt(), "interrupt editor toggle");
                        if (weather) {
                            click(client, toggle(client, "Must be outside"), 0);
                            check(config(client, id).mustBeOutside() != previous.mustBeOutside(), "outside editor toggle");
                        }
                        var plus = client.gui.screen().children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                                .filter(w -> w.getMessage().getString().equals("+")).findFirst().orElseThrow();
                        var minus = client.gui.screen().children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                                .filter(w -> w.getMessage().getString().equals("−")).findFirst().orElseThrow();
                        for (int i = 0; i < 12; i++) click(client, plus, 0);
                        check(config(client, id).priority() == 10, "priority ceiling");
                        for (int i = 0; i < 12; i++) click(client, minus, 0);
                        check(config(client, id).priority() == 1, "priority floor");
                    }
                });
                if (scale == 4 || special == Special.THUNDER || special == Special.UNDERGROUND || special == Special.MENU)
                    context.takeScreenshot("editor-" + special.name().toLowerCase(Locale.ROOT) + suffix);
            }
            if (scale == 4) {
                context.setScreen(() -> new ScenarioScreen(parent, WhoStoppedTheMusic.settings(), forest));
                context.waitTicks(3);
                fit(context);
                context.takeScreenshot("editor-forest-scale4");
                press(context, "Copy songs", 0);
                fit(context);
                search(context, "biomes");
                smoothWheel(context, "copy picker scale4");
                context.takeScreenshot("editor-copy-picker-scale4");
                search(context, "plains");
                context.takeScreenshot("editor-copy-picker-search-scale4");
                context.getInput().pressKey(InputConstants.KEY_ESCAPE);
                popupCancels(context, "Frequency:");
            }
        }
        context.getInput().resizeWindow(320, 240);
        context.runOnClient(client -> client.options.guiScale().set(1));
        context.setScreen(() -> new ScenarioScreen(parent, WhoStoppedTheMusic.settings(), forest));
        context.waitTicks(3);
        fit(context);
        context.takeScreenshot("editor-forest-small");
        context.runOnClient(client -> client.gui.screen().onClose());
        context.waitForScreen(MusicScreen.class);
        LoggerFactory.getLogger("whostoppedthemusic-test").info("SCENARIO EDITOR PASS: drag/duplicate/outside/modal weights, focused 1–100, forest Pigstep and own MP3/OGG native playback, remove-last silence, songs-only reset/copy, missing retained, shared popup cancel, all biome/special variants normal/narrow/actual scale4 and copy-picker motion");
    }

    static void sliderKey(ClientGameTestContext context, int key) {
        context.runOnClient(client -> click(client, client.gui.screen().children().stream()
                .filter(WeightSlider.class::isInstance).map(AbstractWidget.class::cast).findFirst().orElseThrow(), 0));
        context.getInput().pressKey(key);
        context.waitTicks(3);
    }

    static void drag(ClientGameTestContext context, String query, boolean inside) {
        search(context, query);
        context.runOnClient(client -> {
            var row = library(client).children().stream().flatMap(r -> r.children().stream()).filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast).filter(w -> w.getMessage().getString().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT).replace(".mp3", "").replace(".ogg", "")))
                    .findFirst().orElseThrow(() -> new AssertionError("Missing drag row: " + query));
            click(client, row, 0);
        });
        boolean capture = query.equals("Pigstep") && inside && context.computeOnClient(client -> field(client.gui.screen(), "dragging") != null);
        if (capture) {
            context.getInput().setCursorPos(420, 320);
            context.waitTicks(2);
            var image = context.takeScreenshot("cassette-held-pigstep");
            context.runOnClient(client -> {
                var tape = (AbstractWidget) field(client.gui.screen(), "heldTape");
                var pixels = labelPixels(client, image, tape, false);
                check(Arrays.stream(pixels).noneMatch(p -> p == Theme.SPINDLE), "loose held tape has no red player rods");
                check(Arrays.stream(pixels).anyMatch(p -> p == Theme.GLOW), "held artwork contains reel edges, not just a title");
            });
        }
        context.runOnClient(client -> {
            var target = songs(client);
            var event = new MouseButtonEvent(inside ? target.getX() + target.getWidth() / 2.0 : 0,
                    inside ? target.getY() + target.getHeight() / 2.0 : 0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
            client.gui.screen().mouseDragged(event, 1, 1);
            client.gui.screen().mouseReleased(event);
        });
        if (capture) {
            var arriving = context.takeScreenshot("cassette-inserting");
            context.waitTicks(8);
            var settled = context.takeScreenshot("cassette-inserted");
            context.runOnClient(client -> {
                var tape = songs(client).children().getFirst().children().stream()
                        .map(AbstractWidget.class::cast).findFirst().orElseThrow();
                check(!Arrays.equals(labelPixels(client, arriving, tape, false), labelPixels(client, settled, tape, false)),
                        "dropped tape visibly slides into the focused deck slot");
                check(field(client.gui.screen(), "dragging") == null, "release clears held tape");
            });
            LoggerFactory.getLogger("whostoppedthemusic-test").info("CASSETTE DRAG PASS: held reel artwork has no red rods; accepted drop slides into focused slot and clears drag");
        }
        context.waitTicks(4);
    }

    static void hoverCase(ClientGameTestContext context) {
        search(context, "Hover-check");
        context.getInput().setCursorPos(0, 0);
        context.waitTicks(3);
        var row = context.computeOnClient(client -> library(client).children().stream()
                .flatMap(r -> r.children().stream()).map(AbstractWidget.class::cast).findFirst().orElseThrow());
        var first = context.takeScreenshot("case-idle-start");
        context.waitTicks(35);
        var idle = context.takeScreenshot("case-idle-later");
        context.runOnClient(client -> check(Arrays.equals(labelPixels(client, first, row, true), labelPixels(client, idle, row, true)),
                "overflowing case title stays pixel-identical without hover"));
        var scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
        context.getInput().setCursorPos((row.getX() + row.getWidth() / 2.0) * scale,
                (row.getY() + row.getHeight() / 2.0) * scale);
        context.waitTicks(3);
        var start = context.takeScreenshot("case-hover-start");
        context.waitTicks(35);
        var moving = context.takeScreenshot("case-hover-moving");
        context.runOnClient(client -> check(!Arrays.equals(labelPixels(client, start, row, true), labelPixels(client, moving, row, true)),
                "hovering moves overflowing case title"));
        context.getInput().setCursorPos(0, 0);
        context.waitTicks(2);
        var reset = context.takeScreenshot("case-leave-reset");
        context.runOnClient(client -> check(Arrays.equals(labelPixels(client, first, row, true), labelPixels(client, reset, row, true)),
                "leaving resets case title pixels immediately"));
        LoggerFactory.getLogger("whostoppedthemusic-test").info("HOVER CASE PASS: stationary idle, hover-only motion, reset on leave using packaged renderer pixels");
    }
}
