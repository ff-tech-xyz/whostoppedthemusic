package xyz.pyrehaven.whostoppedthemusic;

import java.util.List;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.sounds.MusicManager.MusicFrequency;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.custom.*;
import xyz.pyrehaven.whostoppedthemusic.ui.*;
import xyz.pyrehaven.whostoppedthemusic.ui.custom.*;

/** Focused selection of the existing packaged-client harness, not a second launcher. */
final class CustomUiGameTest {
    static void run(ClientGameTestContext context) {
        context.getInput().resizeWindow(960, 720);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            WhoStoppedTheMusic.settings().setMode(Mode.VANILLA);
        });
        context.setScreen(() -> new MusicScreen(null, WhoStoppedTheMusic.settings()));
        context.clickScreenButton("whostoppedthemusic.mode.vanilla");
        check(context.computeOnClient(client -> WhoStoppedTheMusic.settings().mode()) == Mode.BASIC, "Vanilla cycles to Basic");
        var original = context.computeOnClient(client -> client.options.musicFrequency().get());
        press(context, "Frequency", 0);
        check(context.computeOnClient(client -> client.options.musicFrequency().get()) != original, "Basic still changes vanilla frequency");
        addLink(context);
        context.clickScreenButton("whostoppedthemusic.mode.basic");
        check(context.computeOnClient(client -> WhoStoppedTheMusic.settings().mode()) == Mode.CUSTOM, "Basic cycles to Custom");
        winner(context, ScenarioId.special(Special.MENU));
        context.waitTicks(3);
        press(context, "Frequency (Menu)", 0);
        popupCancels(context, "Frequency (Menu)");
        addLink(context);
        context.clickScreenButton("whostoppedthemusic.mode.custom");
        check(context.computeOnClient(client -> WhoStoppedTheMusic.settings().mode()) == Mode.VANILLA, "Custom cycles to Vanilla");
        context.runOnClient(client -> {
            WhoStoppedTheMusic.settings().setMode(Mode.CUSTOM);
            WhoStoppedTheMusic.settings().applyFrequencyToAll(ScenarioConfig.Frequency.CONSTANT);
            WhoStoppedTheMusic.settings().applyJukeboxToAll(JukeboxSetting.PAUSE_MUSIC);
            client.options.musicFrequency().set(MusicFrequency.DEFAULT);
            client.gui.setScreen(null);
        });
        try (var world = context.worldBuilder().create()) {
            context.waitForScreen(null);
            var server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a 0 100 0");
            server.runCommand("fill -8 99 -8 8 99 8 stone");
            server.runCommand("weather thunder");
            server.runCommand("fillbiome -8 96 -8 8 112 8 minecraft:swamp");
            var swamp = ScenarioId.biome("minecraft:swamp");
            var thunder = ScenarioId.special(Special.THUNDER);
            var rain = ScenarioId.special(Special.RAIN);
            context.waitFor(client -> SituationReader.read(client).biome().equals(swamp));
            context.runOnClient(client -> {
                var p = MusicDirector.get(client).customPlayback();
                for (var special : List.of(Special.UNDERGROUND, Special.THUNDER, Special.RAIN, Special.SNOW)) {
                    var id = ScenarioId.special(special);
                    WhoStoppedTheMusic.settings().setScenario(id, p.defaults(id).config(), p.defaults(id).config());
                }
            });
            winner(context, swamp);
            context.waitFor(client -> SituationReader.read(client).thundering() && SituationReader.read(client).raining());
            context.setScreen(() -> new MusicScreen(null, WhoStoppedTheMusic.settings()));
            context.waitTicks(5);
            context.runOnClient(client -> {
                client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.7);
                client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MUSIC).set(0.7);
                MusicDirector.get(client).next();
            });
            context.waitFor(client -> ClientSmokeGameTest.channelState(client, MusicDirector.get(client).current(), true).join());
            var source = context.computeOnClient(client -> MusicDirector.get(client).current());
            press(context, "Pause", 0);
            context.runOnClient(client -> check(MusicDirector.get(client).paused(), "shared Custom Pause holds channel"));
            press(context, "Resume", 0);
            context.waitFor(client -> ClientSmokeGameTest.channelState(client, source, true).join());
            press(context, "Next", 0);
            context.waitFor(client -> ClientSmokeGameTest.channelState(client, MusicDirector.get(client).current(), true).join());
            press(context, "Previous", 0);
            context.waitFor(client -> MusicDirector.get(client).active());
            context.runOnClient(client -> {
                var rows = list(client).children();
                check(rows.size() >= 80, "nine specials and all 67 biomes plus headers");
                check(rows.subList(0, 3).stream().flatMap(r -> r.children().stream()).noneMatch(Toggle.class::isInstance), "menu/credits have no toggle");
            });
            fit(context);
            context.takeScreenshot("custom-main-normal");
            search(context, "thunder");
            context.takeScreenshot("custom-search-thunder");
            context.runOnClient(client -> {
                check(list(client).children().size() == 2, "search matches special plus header");
                var toggle = list(client).children().get(1).children().stream().filter(Toggle.class::isInstance)
                        .map(Button.class::cast).findFirst().orElseThrow();
                click(client, toggle, 0);
                var p = MusicDirector.get(client).customPlayback();
                check(WhoStoppedTheMusic.settings().scenario(thunder, p.defaults(thunder).config()).active(),
                        "toggle click enables thunder at " + toggle.getRectangle() + "; screen=" + client.gui.screen().getClass());
            });
            winner(context, thunder);
            context.runOnClient(client -> {
                var p = MusicDirector.get(client).customPlayback();
                for (var id : List.of(thunder, rain)) {
                    var d = p.defaults(id).config();
                    WhoStoppedTheMusic.settings().setScenario(id, new ScenarioConfig(7, true, true,
                            ScenarioConfig.Frequency.CONSTANT, JukeboxSetting.PAUSE_MUSIC, true, 64, List.of()), d);
                }
                client.getMusicManager().stopPlaying();
            });
            context.waitFor(client -> MusicDirector.get(client).customPlayback().resolution().winners().size() == 2);
            context.waitTicks(5);
            press(context, "Frequency (Rain + Thunder)", 0);
            press(context, "Juke box (Rain + Thunder)", 0);
            context.runOnClient(client -> {
                var p = MusicDirector.get(client).customPlayback();
                check(p.resolution().silent(), "empty tied winners stay silent");
                for (var id : List.of(thunder, rain)) {
                    var c = WhoStoppedTheMusic.settings().scenario(id, p.defaults(id).config());
                    check(c.frequency() == ScenarioConfig.Frequency.DEFAULT && c.jukebox() == JukeboxSetting.PLAY_OVER, "main edits every tied silent winner");
                }
                check(rowButtons(client).stream().anyMatch(b -> b.getMessage().getString().contains("playing")), "playing row state");
                var d = p.defaults(swamp).config();
                WhoStoppedTheMusic.settings().setScenario(swamp, new ScenarioConfig(d.priority(), d.active(), d.interrupt(),
                        ScenarioConfig.Frequency.FREQUENT, JukeboxSetting.PAUSE_MUSIC, d.mustBeOutside(), d.yLevel(), List.of()), d);
                var absent = ScenarioId.biome("removed:grove");
                WhoStoppedTheMusic.settings().discoverBiomes(java.util.Map.of(absent.id(), "removed"));
                WhoStoppedTheMusic.settings().setScenario(absent, d.withFrequency(ScenarioConfig.Frequency.CONSTANT), d);
            });
            popupCancels(context, "Juke box (Rain + Thunder)");
            press(context, "Frequency (Rain + Thunder)", 1);
            context.waitForScreen(ApplyToAllPopup.class);
            context.takeScreenshot("custom-apply-popup");
            context.clickScreenButton("gui.yes");
            press(context, "Juke box (Rain + Thunder)", 1);
            context.clickScreenButton("gui.yes");
            context.runOnClient(client -> {
                var loaded = Settings.load(configFile());
                var p = MusicDirector.get(client).customPlayback();
                var ids = new java.util.ArrayList<ScenarioId>();
                for (var special : Special.values()) ids.add(ScenarioId.special(special));
                p.catalog().entries().forEach(e -> ids.add(e.id()));
                ids.add(ScenarioId.biome("removed:grove"));
                loaded.discoverBiomes(java.util.Map.of("future:grove", "future"));
                ids.add(ScenarioId.biome("future:grove"));
                for (var id : ids) {
                    var c = loaded.scenario(id, p.defaults(swamp).config());
                    check(c.frequency() == ScenarioConfig.Frequency.DEFAULT && c.jukebox() == JukeboxSetting.PLAY_OVER,
                            "Yes survives disk reload for loaded, absent/future scenario: " + id);
                }
                check(client.options.musicFrequency().get() == MusicFrequency.DEFAULT, "Custom never writes vanilla frequency");
            });
            search(context, "swamp");
            context.runOnClient(client -> check(rowButtons(client).stream().anyMatch(b -> b.getMessage().getString().contains("edited")), "edited list marker"));
            server.runCommand("fillbiome -8 96 -8 8 112 8 minecraft:forest");
            editor(context);
            context.getInput().resizeWindow(320, 240);
            context.runOnClient(client -> client.options.guiScale().set(1));
            context.waitTicks(5);
            fit(context);
            context.getInput().setCursorPos(0, 0);
            context.takeScreenshot("custom-main-small");
            context.runOnClient(client -> {
                check(((EditBox) client.gui.screen().children().getFirst()).getValue().equals("swamp"), "query retained on resize");
                click(client, rowButtons(client).stream().filter(b -> b.getMessage().getString().startsWith("Swamp")).findFirst().orElseThrow(), 0);
            });
            context.waitForScreen(ScenarioScreen.class);
            popupCancels(context, "Frequency:");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitForScreen(MusicScreen.class);
        }
        org.slf4j.LoggerFactory.getLogger("whostoppedthemusic-test").info("CUSTOM UI PASS: three-mode cycle, Basic vanilla frequency, both Add routes, registry/search/toggle, tied silence labels/actions, playing/edited rows, normal/narrow bounds/query retention, shared editor controls, popup no/Esc/outside exact-byte cancel and Yes disk/absent/future defaults");
    }

    private static ScenarioConfig config(Minecraft client, ScenarioId id) {
        return WhoStoppedTheMusic.settings().scenario(id, MusicDirector.get(client).customPlayback().defaults(id).config());
    }

    private static void editor(ClientGameTestContext context) {
        var forest = ScenarioId.biome("minecraft:forest");
        var plains = ScenarioId.biome("minecraft:plains");
        context.waitFor(client -> SituationReader.read(client).biome().equals(forest));
        context.runOnClient(client -> {
            var folder = client.gameDirectory.toPath().resolve("music");
            try {
                Files.createDirectories(folder);
                for (var entry : java.util.Map.of("stereo.mp3", "Editor-check.mp3", "mono.ogg", "Editor-check.ogg").entrySet()) {
                    try (var input = CustomUiGameTest.class.getResourceAsStream("/audio/" + entry.getKey())) {
                        Files.copy(input, folder.resolve(entry.getValue()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            var settings = WhoStoppedTheMusic.settings();
            var defaults = MusicDirector.get(client).customPlayback().defaults(forest).config();
            settings.setScenario(forest, defaults.withSongs(List.of()).withPriority(9).withInterrupt(true), defaults);
        });
        var refresh = context.computeOnClient(client -> WhoStoppedTheMusic.LIBRARY.refreshSongs());
        context.waitFor(client -> refresh.isDone());
        var parent = context.computeOnClient(client -> client.gui.screen());
        context.setScreen(() -> new ScenarioScreen(parent, WhoStoppedTheMusic.settings(), forest));
        context.waitTicks(4);
        fit(context);
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
                && ClientSmokeGameTest.channelState(client, MusicDirector.get(client).current(), true).join());
        drag(context, "Sweden", false);
        context.runOnClient(client -> check(config(client, forest).songs().size() == 1, "outside drop changes nothing"));
        drag(context, "Sweden", true);
        sliderKey(context, InputConstants.KEY_END);
        context.runOnClient(client -> check(config(client, forest).songs().getLast().weight() == 100, "slider End reaches 100 on focused added song"));
        sliderKey(context, InputConstants.KEY_HOME);
        context.runOnClient(client -> check(config(client, forest).songs().getLast().weight() == 1, "slider Home reaches 1"));
        context.runOnClient(client -> check(client.gui.screen().children().stream().filter(xyz.pyrehaven.whostoppedthemusic.ui.widget.WeightSlider.class::isInstance)
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
                    new xyz.pyrehaven.whostoppedthemusic.music.Deck.Entry("minecraft:records/pigstep", 7))),
                    MusicDirector.get(client).customPlayback().defaults(plains).config());
        });
        press(context, "Copy songs", 0);
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
        context.runOnClient(client -> check(editorLists(client).get(1).children().size() == 1
                && editorLists(client).get(1).focusedId().equals("minecraft:records/pigstep"), "picker return refreshes retained cassette rows and focus"));
        drag(context, "Sweden", true);
        context.runOnClient(client -> check(config(client, forest).songs().getLast().weight() == 7, "nonempty drag uses modal weight"));
        for (String extension : List.of("mp3", "ogg")) {
            drag(context, "Editor-check." + extension, true);
            // Search includes IDs; the literal file name is retained in the entry.
            context.runOnClient(client -> {
                var track = WhoStoppedTheMusic.LIBRARY.tracks().stream().filter(t -> t.id().equals("custom:Editor-check." + extension)).findFirst().orElseThrow();
                check(config(client, forest).songs().stream().anyMatch(e -> e.id().equals(track.id())), "own song can be dragged: " + extension);
                var d = MusicDirector.get(client).customPlayback().defaults(forest).config();
                WhoStoppedTheMusic.settings().setScenario(forest, config(client, forest).withSongs(List.of(new xyz.pyrehaven.whostoppedthemusic.music.Deck.Entry(track.id(), 7))), d);
            });
            context.waitTicks(3);
            context.runOnClient(client -> MusicDirector.get(client).next());
            context.waitFor(client -> ClientSmokeGameTest.channelState(client, MusicDirector.get(client).current(), true).join()
                    && MusicDirector.get(client).playing(WhoStoppedTheMusic.LIBRARY.tracks().stream().filter(t -> t.id().equals("custom:Editor-check." + extension)).findFirst().orElseThrow()));
        }
        context.runOnClient(client -> {
            var d = MusicDirector.get(client).customPlayback().defaults(forest).config();
            WhoStoppedTheMusic.settings().setScenario(forest, config(client, forest).withSongs(List.of(
                    new xyz.pyrehaven.whostoppedthemusic.music.Deck.Entry("absent:music/gone", 25))), d);
        });
        context.waitTicks(4);
        context.runOnClient(client -> check(editorLists(client).get(1).children().stream().flatMap(r -> r.children().stream()).filter(AbstractWidget.class::isInstance)
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
                    new xyz.pyrehaven.whostoppedthemusic.music.Deck.Entry("minecraft:music/game/sweden", 20),
                    new xyz.pyrehaven.whostoppedthemusic.music.Deck.Entry("minecraft:records/pigstep", 30),
                    new xyz.pyrehaven.whostoppedthemusic.music.Deck.Entry("custom:Editor-check.mp3", 50))), d);
        });
        for (boolean narrow : List.of(false, true)) {
            context.getInput().resizeWindow(narrow ? 320 : 960, narrow ? 240 : 720);
            context.runOnClient(client -> client.options.guiScale().set(narrow ? 1 : 2));
            for (var special : Special.values()) {
                context.setScreen(() -> new ScenarioScreen(parent, WhoStoppedTheMusic.settings(), ScenarioId.special(special)));
                context.waitTicks(3);
                if (special == Special.THUNDER) {
                    context.runOnClient(client -> editorLists(client).get(1).selectSong("minecraft:records/pigstep"));
                    context.waitTicks(3);
                }
                fit(context);
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
                        var togglesList = client.gui.screen().children().stream().filter(Toggle.class::isInstance).map(AbstractWidget.class::cast).toList();
                        click(client, togglesList.getFirst(), 0);
                        check(config(client, id).active() != previous.active(), "active editor toggle");
                        click(client, togglesList.get(1), 0);
                        check(config(client, id).interrupt() != previous.interrupt(), "interrupt editor toggle");
                        if (weather) {
                            click(client, togglesList.get(2), 0);
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
                if (special == Special.THUNDER || special == Special.UNDERGROUND || special == Special.MENU)
                    context.takeScreenshot("editor-" + special.name().toLowerCase(java.util.Locale.ROOT) + (narrow ? "-small" : "-normal"));
            }
        }
        context.setScreen(() -> new ScenarioScreen(parent, WhoStoppedTheMusic.settings(), forest));
        context.waitTicks(3);
        fit(context);
        context.takeScreenshot("editor-forest-small");
        context.runOnClient(client -> client.gui.screen().onClose());
        context.waitForScreen(MusicScreen.class);
        org.slf4j.LoggerFactory.getLogger("whostoppedthemusic-test").info("SCENARIO EDITOR PASS: drag/duplicate/outside/modal weights, focused 1–100, forest Pigstep and own MP3/OGG native playback, remove-last silence, songs-only reset/copy, missing retained, shared popup cancel, all biome/special variants normal/narrow");
    }

    private static List<xyz.pyrehaven.whostoppedthemusic.ui.widget.TrackList> editorLists(Minecraft client) {
        return client.gui.screen().children().stream().filter(xyz.pyrehaven.whostoppedthemusic.ui.widget.TrackList.class::isInstance)
                .map(xyz.pyrehaven.whostoppedthemusic.ui.widget.TrackList.class::cast).toList();
    }

    private static void sliderKey(ClientGameTestContext context, int key) {
        context.runOnClient(client -> click(client, client.gui.screen().children().stream()
                .filter(xyz.pyrehaven.whostoppedthemusic.ui.widget.WeightSlider.class::isInstance).map(AbstractWidget.class::cast).findFirst().orElseThrow(), 0));
        context.getInput().pressKey(key);
        context.waitTicks(3);
    }

    private static void drag(ClientGameTestContext context, String query, boolean inside) {
        search(context, query);
        context.runOnClient(client -> {
            var lists = editorLists(client);
            var row = lists.getFirst().children().stream().flatMap(r -> r.children().stream()).filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast).filter(w -> w.getMessage().getString().toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT).replace(".mp3", "").replace(".ogg", "")))
                    .findFirst().orElseThrow(() -> new AssertionError("Missing drag row: " + query));
            click(client, row, 0);
            var target = lists.get(1);
            var event = new MouseButtonEvent(inside ? target.getX() + target.getWidth() / 2.0 : 0,
                    inside ? target.getY() + target.getHeight() / 2.0 : 0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
            client.gui.screen().mouseDragged(event, event.x() - row.getX(), event.y() - row.getY());
            client.gui.screen().mouseReleased(event);
        });
        context.waitTicks(4);
    }

    private static void addLink(ClientGameTestContext context) {
        context.clickScreenButton("whostoppedthemusic.add_music");
        context.waitForScreen(AddSongsScreen.class);
        context.waitFor(client -> client.gui.screen().children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast).anyMatch(w -> w.active && w.getMessage().getString().equals("Done")));
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(MusicScreen.class);
    }

    private static Path configFile() { return FabricLoader.getInstance().getConfigDir().resolve("whostoppedthemusic.json"); }
    private static byte[] configBytes() {
        try { return Files.readAllBytes(configFile()); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }

    private static void popupCancels(ClientGameTestContext context, String label) {
        for (int cancel = 0; cancel < 3; cancel++) {
            var before = configBytes();
            press(context, label, 1);
            context.waitForScreen(ApplyToAllPopup.class);
            check(Arrays.equals(before, configBytes()), "right-click leaves exact disk bytes");
            if (cancel == 0) context.clickScreenButton("gui.no");
            else if (cancel == 1) context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            else context.runOnClient(client -> client.gui.screen().mouseClicked(new MouseButtonEvent(0, 0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false));
            check(Arrays.equals(before, configBytes()), "cancel leaves exact disk bytes");
        }
    }

    private static void press(ClientGameTestContext context, String prefix, int button) {
        context.runOnClient(client -> {
            var widget = client.gui.screen().children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                    .filter(w -> w.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow(() -> new AssertionError("Missing control: " + prefix));
            click(client, widget, button);
        });
        context.waitTicks(2);
    }

    private static void click(Minecraft client, AbstractWidget widget, int button) {
        check(client.gui.screen().mouseClicked(new MouseButtonEvent(widget.getX() + widget.getWidth() / 2.0,
                widget.getY() + widget.getHeight() / 2.0,
                new MouseButtonInfo(button == 0 ? InputConstants.MOUSE_BUTTON_LEFT : InputConstants.MOUSE_BUTTON_RIGHT, 0)), false), "click handled");
    }

    private static ScenarioList list(Minecraft client) {
        return client.gui.screen().children().stream().filter(ScenarioList.class::isInstance).map(ScenarioList.class::cast).findFirst().orElseThrow();
    }

    private static List<Button> rowButtons(Minecraft client) {
        return list(client).children().stream().flatMap(r -> r.children().stream()).filter(Button.class::isInstance)
                .map(Button.class::cast).filter(b -> !(b instanceof Toggle)).toList();
    }

    private static void search(ClientGameTestContext context, String value) {
        context.runOnClient(client -> client.gui.screen().children().stream().filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).findFirst().orElseThrow().setValue(value));
        context.waitTicks(3);
    }

    private static void winner(ClientGameTestContext context, ScenarioId id) {
        context.waitFor(client -> MusicDirector.get(client).customPlayback().resolution() != null
                && MusicDirector.get(client).customPlayback().resolution().winners().equals(List.of(id)));
    }

    private static void fit(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var screen = client.gui.screen();
            var widgets = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
            for (var w : widgets) check(w.getX() >= 0 && w.getY() >= 0 && w.getRight() <= screen.width && w.getBottom() <= screen.height
                    && w.getWidth() > 0 && w.getHeight() > 0, "widget fits: " + w.getMessage().getString());
            for (int i = 0; i < widgets.size(); i++) for (int j = i + 1; j < widgets.size(); j++) {
                var a = widgets.get(i); var b = widgets.get(j);
                check(a.getRight() <= b.getX() || b.getRight() <= a.getX() || a.getBottom() <= b.getY() || b.getBottom() <= a.getY(), "widgets do not overlap");
            }
        });
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
