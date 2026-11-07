package xyz.pyrehaven.whostoppedthemusic;

import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.assertWidgetsFit;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.channelState;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.check;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.clickWidget;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.namedWidgetOnClient;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.searchBox;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.trackList;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.slf4j.LoggerFactory;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.Track;
import xyz.pyrehaven.whostoppedthemusic.ui.AddSongsScreen;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicScreen;

final class AddSongsGameTest {
    static void checkAddSongs(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 960);
        context.runOnClient(client -> client.options.guiScale().set(4));
        var parent = context.computeOnClient(client -> client.gui.screen());
        Path game = context.computeOnClient(client -> client.gameDirectory.toPath());
        Path source = game.resolve("add-songs-inputs");
        Path folder = game.resolve("music");
        try {
            Files.createDirectories(source);
            Files.createDirectories(folder);
            try (var files = Files.list(folder)) {
                for (var file : files.filter(p -> p.getFileName().toString().startsWith("Add-check")).toList()) Files.delete(file);
            }
            for (var entry : Map.of("stereo.mp3", "Add-check.mp3", "mono.ogg", "Add-check.ogg").entrySet()) {
                try (var input = ClientSmokeGameTest.class.getResourceAsStream("/audio/" + entry.getKey())) {
                    Files.copy(input, source.resolve(entry.getValue()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.writeString(source.resolve("Add-check.png"), "not music");
        } catch (IOException error) { throw new AssertionError(error); }
        context.runOnClient(client -> {
            WhoStoppedTheMusic.settings().setMode(Mode.BASIC);
            MusicDirector.get(client).syncMode();
            MusicDirector.get(client).stop();
            client.gui.setScreen(new MusicScreen(parent, WhoStoppedTheMusic.settings()));
            Screens.getWidgets(client.gui.screen()).stream().filter(EditBox.class::isInstance)
                    .map(EditBox.class::cast).findFirst().orElseThrow().setValue("Add-check");
        });
        var basic = context.computeOnClient(client -> client.gui.screen());
        context.clickScreenButton("whostoppedthemusic.add_music");
        context.waitForScreen(AddSongsScreen.class);
        context.waitFor(client -> namedWidgetOnClient(client.gui.screen(), "Done").active);
        context.runOnClient(client -> {
            var location = AddSongsScreen.class.getProtectionDomain().getCodeSource().getLocation().toString();
            check(location.endsWith("whostoppedthemusic-1.2.0.jar"), "Add music screen comes from installable jar");
            client.gui.screen().onFilesDrop(List.of(source.resolve("Add-check.mp3"), source.resolve("Add-check.ogg")));
        });
        context.waitForScreen(ConfirmScreen.class);
        check(!Files.exists(folder.resolve("Add-check.mp3")), "drop waits for confirmation");
        assertWidgetsFit(context);
        context.runOnClient(client -> check(client.getWindow().getGuiScale() == 4, "actual import GUI scale4"));
        context.takeScreenshot("add-songs-confirm");
        context.clickScreenButton("gui.no");
        context.waitForScreen(AddSongsScreen.class);
        check(!Files.exists(folder.resolve("Add-check.mp3")), "No leaves folder unchanged");
        context.runOnClient(client -> client.gui.screen().onFilesDrop(List.of(source.resolve("Add-check.png"))));
        context.waitForScreen(AlertScreen.class);
        context.runOnClient(client -> check(client.gui.screen().getNarrationMessage().getString().contains("Only regular MP3"), "invalid extension explains rejection"));
        assertWidgetsFit(context);
        context.takeScreenshot("add-songs-invalid-scale4");
        context.clickScreenButton("gui.back");
        context.waitForScreen(AddSongsScreen.class);
        context.runOnClient(client -> client.gui.screen().onFilesDrop(List.of(source.resolve("Add-check.mp3"), source.resolve("Add-check.ogg"))));
        context.waitForScreen(ConfirmScreen.class);
        context.clickScreenButton("gui.yes");
        context.waitForScreen(AlertScreen.class);
        try {
            check(Files.mismatch(source.resolve("Add-check.mp3"), folder.resolve("Add-check.mp3")) == -1,
                    "confirmed MP3 copy matches original bytes");
            check(Files.mismatch(source.resolve("Add-check.ogg"), folder.resolve("Add-check.ogg")) == -1,
                    "confirmed OGG copy matches original bytes");
        } catch (IOException error) { throw new AssertionError(error); }
        var imported = context.computeOnClient(client -> WhoStoppedTheMusic.LIBRARY.tracks().stream()
                .filter(t -> t.id().startsWith("custom:Add-check")).toList());
        check(imported.size() == 2 && imported.stream().allMatch(Track::playable), "import refresh probes both files");
        assertWidgetsFit(context);
        context.takeScreenshot("add-songs-success-scale4");
        for (var track : imported) {
            var sound = context.computeOnClient(client -> {
                MusicDirector.get(client).play(track);
                return MusicDirector.get(client).current();
            });
            context.waitFor(client -> channelState(client, sound, true).join());
            context.runOnClient(client -> MusicDirector.get(client).stop());
        }
        context.clickScreenButton("gui.back");
        context.waitForScreen(AddSongsScreen.class);
        context.runOnClient(client -> client.gui.screen().onFilesDrop(List.of(source.resolve("Add-check.mp3"))));
        context.waitForScreen(ConfirmScreen.class);
        context.clickScreenButton("gui.yes");
        context.waitForScreen(AlertScreen.class);
        context.runOnClient(client -> check(client.gui.screen().getNarrationMessage().getString().contains("Add-check (2).mp3"), "numbered duplicate reported"));
        try {
            check(Files.mismatch(source.resolve("Add-check.mp3"), folder.resolve("Add-check (2).mp3")) == -1, "duplicate gets independent exact copy");
        } catch (IOException error) { throw new AssertionError(error); }
        context.clickScreenButton("gui.back");
        context.waitForScreen(AddSongsScreen.class);
        context.runOnClient(client -> Screens.getWidgets(client.gui.screen()).stream().filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).findFirst().orElseThrow().setValue("Add-check"));
        context.waitTicks(3);
        assertWidgetsFit(context);
        context.takeScreenshot("add-songs-list");
        context.runOnClient(client -> searchBox(client.gui.screen()).setValue("Add-check (2)"));
        context.waitTicks(3);
        clickWidget(context, context.computeOnClient(client -> (AbstractWidget) trackList(client.gui.screen()).children().getFirst().children().getLast()));
        context.waitForScreen(ConfirmScreen.class);
        context.runOnClient(client -> check(client.gui.screen().getNarrationMessage().getString().contains("Add-check (2).mp3"), "Remove names exact file before deletion"));
        assertWidgetsFit(context);
        context.takeScreenshot("add-songs-remove-scale4");
        context.clickScreenButton("gui.cancel");
        context.waitForScreen(AddSongsScreen.class);
        check(Files.exists(folder.resolve("Add-check (2).mp3")), "cancel keeps duplicate");
        context.waitTicks(3);
        clickWidget(context, context.computeOnClient(client -> (AbstractWidget) trackList(client.gui.screen()).children().getFirst().children().getLast()));
        context.waitForScreen(ConfirmScreen.class);
        context.clickScreenButton("whostoppedthemusic.remove");
        context.waitForScreen(AlertScreen.class);
        check(!Files.exists(folder.resolve("Add-check (2).mp3")), "confirmed Remove deletes the named duplicate");
        check(Files.exists(source.resolve("Add-check.mp3")) && Files.exists(folder.resolve("Add-check.mp3")), "source and first copy survive removal");
        context.runOnClient(client -> check(WhoStoppedTheMusic.LIBRARY.tracks().stream().noneMatch(t -> t.id().equals("custom:Add-check (2).mp3")), "removal refreshes library"));
        context.clickScreenButton("gui.back");
        context.waitForScreen(AddSongsScreen.class);
        context.clickScreenButton("whostoppedthemusic.open_music_folder");
        context.runOnClient(client -> searchBox(client.gui.screen()).setValue("Add-check"));
        context.getInput().resizeWindow(320, 240);
        context.waitTicks(3);
        assertWidgetsFit(context);
        context.takeScreenshot("add-songs-small");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(MusicScreen.class);
        context.runOnClient(client -> {
            check(client.gui.screen() == basic, "returns to same Basic screen");
            var query = Screens.getWidgets(client.gui.screen()).stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
            check(query.getValue().equals("Add-check"), "parent search retained");
            check(trackList(client.gui.screen()).children().size() == 2, "parent library view refreshed after file operations");
        });
        context.clickScreenButton("whostoppedthemusic.add_music");
        context.waitForScreen(AddSongsScreen.class);
        context.waitFor(client -> namedWidgetOnClient(client.gui.screen(), "Done").active);
        context.clickScreenButton("gui.done");
        context.waitForScreen(MusicScreen.class);
        context.clickScreenButton("gui.done");
        context.waitForScreen(TitleScreen.class);
        LoggerFactory.getLogger("whostoppedthemusic-test").info("ADD SONGS PASS: packaged shared screen, import No/Yes, exact MP3/OGG copies, bad extension, numbered duplicate, cancel/delete, source retention, library refresh, search/return/Done/Escape and 320x240");
    }
}
