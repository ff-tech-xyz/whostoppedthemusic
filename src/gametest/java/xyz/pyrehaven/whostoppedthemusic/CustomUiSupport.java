package xyz.pyrehaven.whostoppedthemusic;

import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.check;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import org.slf4j.LoggerFactory;
import xyz.pyrehaven.whostoppedthemusic.mixin.SoundEngineAccessor;
import xyz.pyrehaven.whostoppedthemusic.mixin.SoundManagerAccessor;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.Track;
import xyz.pyrehaven.whostoppedthemusic.music.custom.*;
import xyz.pyrehaven.whostoppedthemusic.ui.*;
import xyz.pyrehaven.whostoppedthemusic.ui.custom.*;
import xyz.pyrehaven.whostoppedthemusic.ui.widget.TrackList;

final class CustomUiSupport {
    static Track track(String id) {
        return WhoStoppedTheMusic.LIBRARY.find(id).orElseThrow();
    }

    static Object field(Object owner, String name) {
        try {
            var field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    static Map<SoundInstance, ChannelAccess.ChannelHandle> channels(Minecraft client) {
        var engine = ((SoundManagerAccessor) client.getSoundManager()).whostoppedthemusic$engine();
        return ((SoundEngineAccessor) engine).whostoppedthemusic$channels();
    }

    static void smoothWheel(ClientGameTestContext context, String consumer) {
        var list = context.computeOnClient(CustomUiSupport::list);
        double target = Math.min(Theme.SCENARIO_ROW * 3.0, context.computeOnClient(client -> list.maxScrollAmount()));
        check(target > 0, consumer + " has scrollable content");
        context.runOnClient(client -> {
            list.setScrollAmount(0);
            check(list.mouseScrolled(list.getX() + 4, list.getY() + 4, 0, -6), consumer + " handles wheel");
            check(list.scrollAmount() == 0, consumer + " wheel sets target without immediate jump");
        });
        context.waitFor(client -> list.scrollAmount() > 0);
        context.runOnClient(client -> check(list.scrollAmount() < target, consumer + " renders a live intermediate position"));
        context.waitFor(client -> Math.abs(list.scrollAmount() - target) < Theme.SCROLL_EPSILON);
        context.runOnClient(client -> list.setScrollAmount(0));
        LoggerFactory.getLogger("whostoppedthemusic-test").info("SMOOTH SCENARIO PASS: {} intermediate and settled target {}", consumer, target);
    }

    static ScenarioConfig config(Minecraft client, ScenarioId id) {
        return WhoStoppedTheMusic.settings().scenario(id, MusicDirector.get(client).customPlayback().defaults(id).config());
    }

    static TrackList library(Minecraft client) {
        return (TrackList) field(client.gui.screen(), "library");
    }

    static TrackList songs(Minecraft client) {
        return (TrackList) field(client.gui.screen(), "songs");
    }

    static AbstractWidget toggle(Minecraft client, String label) {
        return client.gui.screen().children().stream().filter(Toggle.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().startsWith(label)).findFirst().orElseThrow();
    }

    static int[] labelPixels(Minecraft client, Path path, AbstractWidget widget, boolean labelOnly) {
        try {
            var image = ImageIO.read(path.toFile());
            double scale = client.getWindow().getGuiScale();
            int inset = labelOnly ? Theme.CASE_SPINE + Theme.LABEL_PADDING : 0;
            int x = (int) ((widget.getX() + inset) * scale);
            int y = (int) ((widget.getY() + (labelOnly ? Theme.ACCENT : 0)) * scale);
            int w = (int) ((widget.getWidth() - inset - (labelOnly ? Theme.GAP + Theme.LABEL_PADDING : 0)) * scale);
            int h = (int) ((widget.getHeight() - (labelOnly ? Theme.ACCENT * 2 : 0)) * scale);
            return image.getRGB(x, y, w, h, null, 0, w);
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }

    static void addLink(ClientGameTestContext context) {
        context.clickScreenButton("whostoppedthemusic.add_music");
        context.waitForScreen(AddSongsScreen.class);
        context.waitFor(client -> client.gui.screen().children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast).anyMatch(w -> w.active && w.getMessage().getString().equals("Done")));
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(MusicScreen.class);
    }

    static Path configFile() { return FabricLoader.getInstance().getConfigDir().resolve("whostoppedthemusic.json"); }

    static byte[] configBytes() {
        try { return Files.readAllBytes(configFile()); }
        catch (IOException error) { throw new UncheckedIOException(error); }
    }

    static void popupCancels(ClientGameTestContext context, String label) {
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

    static void press(ClientGameTestContext context, String prefix, int button) {
        context.runOnClient(client -> {
            var widget = client.gui.screen().children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                    .filter(w -> w.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow(() -> new AssertionError("Missing control: " + prefix));
            click(client, widget, button);
        });
        context.waitTicks(2);
    }

    static void click(Minecraft client, AbstractWidget widget, int button) {
        check(client.gui.screen().mouseClicked(new MouseButtonEvent(widget.getX() + widget.getWidth() / 2.0,
                widget.getY() + widget.getHeight() / 2.0,
                new MouseButtonInfo(button == 0 ? InputConstants.MOUSE_BUTTON_LEFT : InputConstants.MOUSE_BUTTON_RIGHT, 0)), false), "click handled");
    }

    static ScenarioList list(Minecraft client) {
        return client.gui.screen().children().stream().filter(ScenarioList.class::isInstance).map(ScenarioList.class::cast).findFirst().orElseThrow();
    }

    static List<Button> rowButtons(Minecraft client) {
        return list(client).children().stream().flatMap(r -> r.children().stream()).filter(Button.class::isInstance)
                .map(Button.class::cast).filter(b -> !(b instanceof Toggle)).toList();
    }

    static void search(ClientGameTestContext context, String value) {
        context.runOnClient(client -> client.gui.screen().children().stream().filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).findFirst().orElseThrow().setValue(value));
        context.waitTicks(3);
    }

    static void winner(ClientGameTestContext context, ScenarioId id) {
        context.waitFor(client -> MusicDirector.get(client).customPlayback().resolution() != null
                && MusicDirector.get(client).customPlayback().resolution().winners().equals(List.of(id)));
    }

    static void fit(ClientGameTestContext context) {
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

}
