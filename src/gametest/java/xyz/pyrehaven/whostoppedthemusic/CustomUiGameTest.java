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
            popupCancels(context, "Frequency (Swamp)");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitForScreen(MusicScreen.class);
        }
        org.slf4j.LoggerFactory.getLogger("whostoppedthemusic-test").info("CUSTOM UI PASS: three-mode cycle, Basic vanilla frequency, both Add routes, registry/search/toggle, tied silence labels/actions, playing/edited rows, normal/narrow bounds/query retention, shared editor controls, popup no/Esc/outside exact-byte cancel and Yes disk/absent/future defaults");
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
