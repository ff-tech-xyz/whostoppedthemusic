package xyz.pyrehaven.whostoppedthemusic;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.custom.CustomDefaults;
import xyz.pyrehaven.whostoppedthemusic.music.custom.ScenarioId;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicScreen;
import xyz.pyrehaven.whostoppedthemusic.ui.custom.ScenarioList;
import xyz.pyrehaven.whostoppedthemusic.ui.custom.ScenarioScreen;

/** Real mouse/key input through the packaged client's normal music entry points. */
final class BiomeUiGameTest {
    static void run(ClientGameTestContext context) {
        context.getInput().resizeWindow(960, 720);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            WhoStoppedTheMusic.settings().setMode(Mode.VANILLA);
            WhoStoppedTheMusic.settings().discoverBiomes(Map.of("removed:grove", "removed"));
        });
        openMusic(context);
        click(context, "Vanilla");
        click(context, "Basic");
        openBiome(context, "title-before-world");
        var menuEntries = context.computeOnClient(client -> MusicDirector.get(client).customPlayback().catalog().entries());
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(TitleScreen.class);
        try (var world = context.worldBuilder().create()) {
            context.waitForScreen(null);
            openMusic(context);
            openBiome(context, "loaded-world");
            context.runOnClient(client -> {
                var catalog = MusicDirector.get(client).customPlayback().catalog();
                var registry = client.level.registryAccess().lookupOrThrow(Registries.BIOME);
                check(catalog.entries().size() == registry.size(), "catalog contains exactly the loaded registry, including pack/mod entries");
                for (var entry : catalog.entries()) {
                    check(entry.biome() == registry.getValue(Identifier.parse(entry.id().id())),
                            "catalog uses loaded biome definition: " + entry.id());
                }
                var defaults = new CustomDefaults(WhoStoppedTheMusic.LIBRARY);
                for (var menu : menuEntries) {
                    var loaded = catalog.get(menu.id());
                    check(menu.group().equals(loaded.group()), "menu group matches vanilla registry tags: " + menu.id());
                    var id = Identifier.parse(menu.id().id());
                    check(defaults.biome(id, menu.biome(), menu.dimension()).config().equals(
                            defaults.biome(id, loaded.biome(), loaded.dimension()).config()),
                            "menu defaults match loaded vanilla definitions: " + menu.id());
                }
            });
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitForScreen(null);
        }
        context.waitForScreen(TitleScreen.class);
        openMusic(context);
        openBiome(context, "title-after-world");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(TitleScreen.class);
        LoggerFactory.getLogger("whostoppedthemusic-test").info(
                "BIOME UI PASS: real M/mode/header/biome clicks open settings before world, in loaded world, and after exit; collapsed ordered sections and exact loaded registry; absent saved mod omitted");
    }

    private static void openMusic(ClientGameTestContext context) {
        context.getInput().pressKey(InputConstants.KEY_M);
        context.waitForScreen(MusicScreen.class);
        context.waitTicks(4);
    }

    private static void openBiome(ClientGameTestContext context, String stage) {
        context.runOnClient(client -> {
            var labels = rows(client).stream().map(b -> b.getMessage().getString()).toList();
            check(labels.size() >= 4 && labels.subList(0, 3).equals(List.of(
                    "▶ Overworld biomes", "▶ Nether biomes", "▶ End biomes"))
                    && labels.getLast().equals("▶ Misc"), stage + " has collapsed ordered biome sections: " + labels);
            check(labels.stream().noneMatch(s -> s.contains("removed")), "saved absent mod is not an invented row");
        });
        click(context, "▶ Overworld biomes");
        context.runOnClient(client -> check(rows(client).stream().anyMatch(b -> b.getMessage().getString().startsWith("Badlands")),
                stage + " real header click expands biome rows"));
        context.takeScreenshot("biome-sections-" + stage);
        click(context, "Badlands");
        context.waitForScreen(ScenarioScreen.class);
        context.waitTicks(3);
        context.runOnClient(client -> check(client.gui.screen().children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast).anyMatch(w -> w.getMessage().getString().equals("Reset to vanilla songs")),
                stage + " opens editable biome settings"));
        var id = ScenarioId.biome("minecraft:badlands");
        int priority = context.computeOnClient(client -> WhoStoppedTheMusic.settings().scenario(id,
                MusicDirector.get(client).customPlayback().defaults(id).config()).priority());
        click(context, "+");
        context.runOnClient(client -> check(WhoStoppedTheMusic.settings().scenario(id,
                MusicDirector.get(client).customPlayback().defaults(id).config()).priority() == priority + 1,
                stage + " real input changes biome priority"));
        click(context, "−");
        context.takeScreenshot("biome-settings-" + stage);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(MusicScreen.class);
    }

    private static List<Button> rows(Minecraft client) {
        return client.gui.screen().children().stream().filter(ScenarioList.class::isInstance).map(ScenarioList.class::cast)
                .findFirst().orElseThrow().children().stream().flatMap(row -> row.children().stream())
                .filter(Button.class::isInstance).map(Button.class::cast).toList();
    }

    private static void click(ClientGameTestContext context, String prefix) {
        context.waitTicks(3);
        double[] pos = context.computeOnClient(client -> {
            var screen = client.gui.screen();
            var controls = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast);
            var candidates = Stream.concat(controls, screen.children().stream()
                    .filter(ScenarioList.class::isInstance).map(ScenarioList.class::cast).flatMap(list -> list.children().stream())
                    .flatMap(row -> row.children().stream()).filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast));
            var button = candidates.filter(w -> w.getMessage().getString().startsWith(prefix)).findFirst()
                    .orElseThrow(() -> new AssertionError("Missing control: " + prefix));
            double scale = client.getWindow().getGuiScale();
            return new double[] {(button.getX() + button.getWidth() / 2.0) * scale,
                    (button.getY() + button.getHeight() / 2.0) * scale};
        });
        context.getInput().setCursorPos(pos[0], pos[1]);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
