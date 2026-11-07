package xyz.pyrehaven.whostoppedthemusic;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicScreen;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;
import xyz.pyrehaven.whostoppedthemusic.ui.custom.ScenarioList;
import xyz.pyrehaven.whostoppedthemusic.ui.custom.ScenarioScreen;
import xyz.pyrehaven.whostoppedthemusic.ui.custom.Toggle;

/** Pixel contract for live switches, through normal settings navigation and mouse input. */
final class ToggleGameTest {
    static void run(ClientGameTestContext context) {
        context.getInput().resizeWindow(960, 720);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            WhoStoppedTheMusic.settings().setMode(Mode.VANILLA);
            xyz.pyrehaven.whostoppedthemusic.music.MusicDirector.get(client).syncMode();
        });
        context.getInput().pressKey(InputConstants.KEY_M);
        context.waitForScreen(MusicScreen.class);
        context.clickScreenButton("whostoppedthemusic.mode.vanilla");
        context.clickScreenButton("whostoppedthemusic.mode.basic");
        context.runOnClient(client -> widgets(client).filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).findFirst().orElseThrow().setValue("Thunder"));
        context.waitTicks(3);
        var rowToggle = context.computeOnClient(client -> widgets(client).filter(Toggle.class::isInstance).findFirst().orElseThrow());
        exercise(context, rowToggle, "row");
        var open = context.computeOnClient(client -> widgets(client).filter(w -> !(w instanceof Toggle)
                && w.getMessage().getString().startsWith("Thunder")).findFirst().orElseThrow());
        click(context, open);
        context.waitForScreen(ScenarioScreen.class);
        context.waitTicks(3);
        for (int[] window : new int[][] {{960, 720, 2}, {1280, 960, 4}, {320, 240, 1}}) {
            context.getInput().resizeWindow(window[0], window[1]);
            context.runOnClient(client -> client.options.guiScale().set(window[2]));
            context.waitTicks(3);
            var toggles = context.computeOnClient(client -> widgets(client).filter(Toggle.class::isInstance).toList());
            for (int i = 0; i < toggles.size(); i++) exercise(context, toggles.get(i), "editor-scale" + window[2] + "-" + i);
        }
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(MusicScreen.class);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(net.minecraft.client.gui.screens.TitleScreen.class);
        org.slf4j.LoggerFactory.getLogger("whostoppedthemusic-test").info(
                "TOGGLE RENDER PASS: normal M/search/row/editor navigation, live row and all three editor switches, both directions, hovered/focused and pointer-away pixels without reopening");
    }

    private static Stream<AbstractWidget> widgets(Minecraft client) {
        var children = client.gui.screen().children();
        return Stream.concat(children.stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast),
                children.stream().filter(ScenarioList.class::isInstance).map(ScenarioList.class::cast)
                        .flatMap(list -> list.children().stream()).flatMap(row -> row.children().stream())
                        .filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast));
    }

    private static void exercise(ClientGameTestContext context, AbstractWidget toggle, String name) {
        var screen = context.computeOnClient(client -> client.gui.screen());
        boolean initial = context.computeOnClient(client -> toggle.getMessage().getString().endsWith("ON"));
        for (int step = 0; step < 3; step++) {
            if (step > 0) click(context, toggle);
            context.waitTicks(2);
            boolean on = context.computeOnClient(client -> toggle.getMessage().getString().endsWith("ON"));
            if (on != (step == 1 ? !initial : initial)) throw new AssertionError("mouse input must flip " + name);
            pixels(context, toggle, context.takeScreenshot("toggle-" + name + "-" + step + "-focused"), on);
            context.getInput().setCursorPos(0, 0);
            context.waitTicks(2);
            pixels(context, toggle, context.takeScreenshot("toggle-" + name + "-" + step + "-away"), on);
            context.runOnClient(client -> {
                if (client.gui.screen() != screen) throw new AssertionError("toggle must retain the same settings page");
            });
        }
    }

    private static void click(ClientGameTestContext context, AbstractWidget widget) {
        double[] pos = context.computeOnClient(client -> {
            double scale = client.getWindow().getGuiScale();
            return new double[] {(widget.getX() + widget.getWidth() / 2.0) * scale,
                    (widget.getY() + widget.getHeight() / 2.0) * scale};
        });
        context.getInput().setCursorPos(pos[0], pos[1]);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }

    private static void pixels(ClientGameTestContext context, AbstractWidget toggle, Path capture, boolean on) {
        int scale = context.computeOnClient(client -> (int) client.getWindow().getGuiScale());
        int left = context.computeOnClient(client -> toggle.getRight() - Theme.TOGGLE_WIDTH);
        int top = context.computeOnClient(client -> toggle.getY() + (toggle.getHeight() - Theme.TOGGLE_HEIGHT) / 2);
        boolean focused = context.computeOnClient(client -> toggle.isHoveredOrFocused());
        try {
            var image = javax.imageio.ImageIO.read(capture.toFile());
            int knob = (on ? Theme.TOGGLE_WIDTH - Theme.TOGGLE_HEIGHT : 0) + Theme.BORDER;
            if (focused) {
                for (int x = 0; x < Theme.TOGGLE_WIDTH; x++) {
                    for (int y : new int[] {0, Theme.TOGGLE_HEIGHT - 1}) {
                        int actual = image.getRGB((left + x) * scale, (top + y) * scale);
                        if (actual != Theme.TEXT) throw new AssertionError(capture + " focus outline missing at " + x + "," + y
                                + " actual=" + Integer.toHexString(actual));
                    }
                }
            }
            // Interior scan excludes the focus outline and rounded cap, but covers every knob/track pixel.
            for (int y = 2; y < Theme.TOGGLE_HEIGHT - 2; y++) {
                for (int x = 1; x < Theme.TOGGLE_WIDTH - 1; x++) {
                    int expected = x >= knob && x < knob + Theme.TOGGLE_HEIGHT - 2 ? Theme.TEXT
                            : on ? Theme.ORANGE : Theme.BACKGROUND;
                    int actual = image.getRGB((left + x) * scale, (top + y) * scale);
                    if (actual != expected) throw new AssertionError(capture + " on=" + on + " pixel " + x + "," + y
                            + " expected=" + Integer.toHexString(expected) + " actual=" + Integer.toHexString(actual));
                }
            }
        } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
}
