package xyz.pyrehaven.whostoppedthemusic;

import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.addLink;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.check;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.click;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.configFile;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.field;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.fit;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.list;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.popupCancels;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.press;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.rowButtons;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.search;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.smoothWheel;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.winner;
import static xyz.pyrehaven.whostoppedthemusic.ScenarioEditorGameTest.editor;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetTooltipHolder;
import net.minecraft.client.sounds.MusicManager.MusicFrequency;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import org.slf4j.LoggerFactory;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.custom.*;
import xyz.pyrehaven.whostoppedthemusic.ui.*;
import xyz.pyrehaven.whostoppedthemusic.ui.custom.*;
import xyz.pyrehaven.whostoppedthemusic.ui.widget.SettingButton;

final class CustomUiGameTest {
    static void run(ClientGameTestContext context) {
        context.getInput().resizeWindow(960, 720);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            WhoStoppedTheMusic.settings().setMode(Mode.VANILLA);
        });
        context.setScreen(() -> new MusicScreen(null, WhoStoppedTheMusic.settings()));
        settingTooltips(context, false);
        context.clickScreenButton("whostoppedthemusic.mode.vanilla");
        settingTooltips(context, false);
        check(context.computeOnClient(client -> WhoStoppedTheMusic.settings().mode()) == Mode.BASIC, "Vanilla cycles to Basic");
        var original = context.computeOnClient(client -> client.options.musicFrequency().get());
        press(context, "Frequency", 0);
        check(context.computeOnClient(client -> client.options.musicFrequency().get()) != original, "Basic still changes vanilla frequency");
        addLink(context);
        context.clickScreenButton("whostoppedthemusic.mode.basic");
        check(context.computeOnClient(client -> WhoStoppedTheMusic.settings().mode()) == Mode.CUSTOM, "Basic cycles to Custom");
        winner(context, ScenarioId.special(Special.MENU));
        context.waitTicks(3);
        settingTooltips(context, true);
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
                client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.7);
                client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.7);
                MusicDirector.get(client).next();
            });
            context.waitFor(client -> GameTestSupport.channelState(client, MusicDirector.get(client).current(), true).join());
            var source = context.computeOnClient(client -> MusicDirector.get(client).current());
            press(context, "Pause", 0);
            context.runOnClient(client -> check(MusicDirector.get(client).paused(), "shared Custom Pause holds channel"));
            press(context, "Resume", 0);
            context.waitFor(client -> GameTestSupport.channelState(client, source, true).join());
            press(context, "Next", 0);
            context.waitFor(client -> GameTestSupport.channelState(client, MusicDirector.get(client).current(), true).join());
            press(context, "Previous", 0);
            context.waitFor(client -> MusicDirector.get(client).active());
            sections(context);
            fit(context);
            context.takeScreenshot("custom-main-normal");
            smoothWheel(context, "scenario browser");
            search(context, "thunder");
            context.takeScreenshot("custom-search-thunder");
            context.runOnClient(client -> {
                check(list(client).children().size() == 2, "search matches special plus header");
                var toggle = list(client).children().stream().flatMap(row -> row.children().stream()).filter(Toggle.class::isInstance)
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
                WhoStoppedTheMusic.settings().discoverBiomes(Map.of(absent.id(), "removed"));
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
                var ids = new ArrayList<ScenarioId>();
                for (var special : Special.values()) ids.add(ScenarioId.special(special));
                p.catalog().entries().forEach(e -> ids.add(e.id()));
                ids.add(ScenarioId.biome("removed:grove"));
                loaded.discoverBiomes(Map.of("future:grove", "future"));
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
        LoggerFactory.getLogger("whostoppedthemusic-test").info("CUSTOM UI PASS: collapsed ordered clickable biome/Misc sections, search expansion/restoration, minimal mode-aware tooltips, three-mode cycle, Basic vanilla frequency, both Add routes, registry/search/toggle, tied silence labels/actions, playing/edited rows, normal/narrow bounds/query retention, shared editor controls, popup no/Esc/outside exact-byte cancel and Yes disk/absent/future defaults");
    }

    static void settingTooltips(ClientGameTestContext context, boolean custom) {
        context.runOnClient(client -> {
            var buttons = client.gui.screen().children().stream()
                    .filter(SettingButton.class::isInstance)
                    .map(AbstractWidget.class::cast).toList();
            check(buttons.size() == 2, "frequency and jukebox settings present");
            for (var button : buttons) {
                Tooltip tooltip;
                try {
                    var holder = AbstractWidget.class.getDeclaredField("tooltip");
                    holder.setAccessible(true);
                    tooltip = ((WidgetTooltipHolder) holder.get(button)).get();
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                var message = (Component) field(tooltip, "message");
                check(message.getString().equals(button.getMessage().getString()
                        + (custom ? "\nRight-click to apply to every scenario." : "")), "minimal tooltip for " + button.getMessage().getString());
            }
        });
    }

    static void sections(ClientGameTestContext context) {
        var titles = context.computeOnClient(client -> {
            var expected = new ArrayList<>(List.of("Overworld biomes", "Nether biomes", "End biomes"));
            MusicDirector.get(client).customPlayback().catalog().entries().stream().map(BiomeCatalog.Entry::group)
                    .filter(group -> !List.of("overworld", "nether", "end").contains(group)).distinct().sorted()
                    .forEach(group -> expected.add(group + " biomes"));
            expected.add("Misc");
            return expected;
        });
        context.runOnClient(client -> check(rowButtons(client).stream().map(b -> b.getMessage().getString()).toList()
                .equals(titles.stream().map(title -> "▶ " + title).toList()),
                "all sections collapsed in dimension/namespace order with Misc last: " + rowButtons(client).stream().map(b -> b.getMessage().getString()).toList()));
        context.takeScreenshot("custom-sections-collapsed");
        for (String title : titles) {
            context.runOnClient(client -> click(client, rowButtons(client).stream()
                    .filter(b -> b.getMessage().getString().equals("▶ " + title)).findFirst().orElseThrow(), 0));
            context.waitTicks(2);
            context.runOnClient(client -> {
                check(list(client).children().size() > titles.size(), title + " expands");
                click(client, rowButtons(client).stream().filter(b -> b.getMessage().getString().equals("▼ " + title)).findFirst().orElseThrow(), 0);
                check(list(client).children().size() == titles.size(), title + " collapses");
            });
            context.waitTicks(2);
        }
        search(context, "thunder");
        context.runOnClient(client -> {
            check(list(client).children().size() == 2, "search reveals matches inside collapsed Misc");
            click(client, rowButtons(client).getFirst(), 0);
            check(list(client).children().size() == 1, "search header can collapse");
        });
        context.waitTicks(2);
        context.runOnClient(client -> click(client, rowButtons(client).getFirst(), 0));
        context.waitTicks(2);
        context.runOnClient(client -> check(list(client).children().size() == 2, "search header can reopen"));
        search(context, "");
        context.runOnClient(client -> {
            check(list(client).children().size() == titles.size(), "clearing search restores collapsed state");
            click(client, rowButtons(client).getFirst(), 0);
        });
        context.waitTicks(2);
    }
}
