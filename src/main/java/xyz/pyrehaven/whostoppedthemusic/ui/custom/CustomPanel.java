package xyz.pyrehaven.whostoppedthemusic.ui.custom;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.Settings;
import xyz.pyrehaven.whostoppedthemusic.music.custom.ScenarioId;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicControls;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;

public final class CustomPanel {
    private final EditBox search;
    private final ScenarioList list;
    private final MusicControls controls;

    public CustomPanel(Minecraft client, Settings settings, Consumer<ScenarioId> open) {
        search = Theme.search(client.font, Component.translatable("whostoppedthemusic.scenario.search"));
        list = new ScenarioList(client, settings, open);
        search.setResponder(list::filter);
        controls = new MusicControls(client, settings);
    }

    public void layout(int left, int contentWidth, int height, Consumer<AbstractWidget> add, Runnable changeMode) {
        int x = left + Theme.PADDING;
        int available = contentWidth - Theme.PADDING * 2;
        int playerWidth = Math.clamp((available - Theme.BASIC_VOLUME_SPACE) * 2 / 5,
                Theme.BASIC_PLAYER_MIN, Theme.BASIC_PLAYER_MAX);
        int playerX = x + available - Theme.BASIC_VOLUME_SPACE - playerWidth;
        int browserWidth = playerX - x - Theme.GAP;
        int bottom = height - Theme.BASIC_FOOTER_SPACE;
        list.refresh();
        search.setRectangle(browserWidth, Theme.CONTROL_HEIGHT, x, Theme.BASIC_TOP);
        list.updateSizeAndPosition(browserWidth, bottom - Theme.BASIC_TOP - Theme.ROW, x, Theme.BASIC_TOP + Theme.ROW);
        add.accept(search);
        add.accept(list);
        controls.player(playerX, Theme.BASIC_TOP, playerWidth, bottom, add, changeMode);
    }

    public void update() { controls.update(); }
    public void refresh() { list.refresh(); }
}
