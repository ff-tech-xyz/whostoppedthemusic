package xyz.pyrehaven.whostoppedthemusic.ui.custom;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.Settings;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.custom.ScenarioId;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicControls;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;

/** Scenario navigation and shared settings; function 15 adds the library/song editor here. */
public final class ScenarioScreen extends Screen {
    private final Screen parent;
    private final Settings settings;
    private final ScenarioId scenario;
    private MusicControls controls;
    private int left;
    private int contentWidth;

    public ScenarioScreen(Screen parent, Settings settings, ScenarioId scenario) {
        super(ScenarioLabels.name(scenario));
        this.parent = parent;
        this.settings = settings;
        this.scenario = scenario;
    }

    @Override protected void init() {
        contentWidth = Math.min(Theme.MAX_WIDTH, width - Theme.MARGIN * 2);
        left = (width - contentWidth) / 2;
        controls = new MusicControls(minecraft, settings, () -> List.of(scenario));
        controls.scenarioSettings(left + Theme.PADDING, Theme.BASIC_TOP, contentWidth - Theme.PADDING * 2, this::addRenderableWidget);
        if (scenario.kind() == ScenarioId.Kind.SPECIAL && !ScenarioLabels.fixed(scenario)) {
            var playback = MusicDirector.get(minecraft).customPlayback();
            var toggle = new Toggle(title, () -> settings.scenario(scenario, playback.defaults(scenario).config()).active(), () -> {
                var defaults = playback.defaults(scenario).config();
                var config = settings.scenario(scenario, defaults);
                settings.setScenario(scenario, config.withActive(!config.active()), defaults);
            });
            toggle.setPosition(left + Theme.PADDING, Theme.BASIC_TOP + Theme.ROW * 2);
            addRenderableWidget(toggle);
        }
        addRenderableWidget(Theme.button(width / 2 - Theme.DONE_WIDTH / 2, height - Theme.MARGIN - Theme.CONTROL_HEIGHT,
                Theme.DONE_WIDTH, Component.translatable("gui.back"), button -> onClose()));
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, Theme.BACKGROUND);
        graphics.fill(left, Theme.MARGIN, left + contentWidth, height - Theme.MARGIN, Theme.SCENARIO_PANEL);
        graphics.text(font, title, left + Theme.PADDING, Theme.MARGIN + Theme.GAP, Theme.SCENARIO_TEXT, false);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        controls.updateSettings();
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
