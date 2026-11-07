package xyz.pyrehaven.whostoppedthemusic.ui;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.Settings;
import xyz.pyrehaven.whostoppedthemusic.WhoStoppedTheMusic;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.ui.widget.TrackList;

public final class BasicPanel {
    private final Minecraft client;
    private final Settings settings;
    private final EditBox search;
    private final TrackList list;
    private final Button all;
    private final Button none;
    private final MusicControls controls;
    private boolean collapsed;

    public BasicPanel(Minecraft client, Settings settings) {
        this.client = client;
        this.settings = settings;
        controls = new MusicControls(client, settings);
        search = Theme.search(client.font);
        list = new TrackList(client, settings, 0, 0, Theme.TRACK_HEIGHT, Theme.TRACK_HEIGHT);
        all = Theme.button(0, 0, Theme.CONTROL_HEIGHT, text("all"), button -> setAll(true));
        none = Theme.button(0, 0, Theme.CONTROL_HEIGHT, text("none"), button -> setAll(false));
        all.setTooltip(Tooltip.create(text("all")));
        none.setTooltip(Tooltip.create(text("none")));
        search.setResponder(value -> {
            list.filter(value);
            updateBrowserVisibility();
        });
    }

    public void layout(int left, int contentWidth, int height, Consumer<AbstractWidget> add, Runnable rebuild, Runnable changeMode) {
        int x = left + Theme.PADDING;
        int available = contentWidth - Theme.PADDING * 2;
        int playerWidth = Math.clamp((available - Theme.BASIC_VOLUME_SPACE) * 2 / 5,
                Theme.BASIC_PLAYER_MIN, Theme.BASIC_PLAYER_MAX);
        int playerX = collapsed ? left + (contentWidth - playerWidth) / 2
                : x + available - Theme.BASIC_VOLUME_SPACE - playerWidth;
        int y = Theme.BASIC_TOP;
        int bottom = height - Theme.BASIC_FOOTER_SPACE;
        int browserWidth = playerX - x - Theme.CONTROL_HEIGHT - Theme.GAP * 2;
        // Keep browser widgets alive so rebuilds preserve query and scroll state.
        if (!collapsed) {
            search.setRectangle(browserWidth, Theme.CONTROL_HEIGHT, x, y);
            int listY = y + Theme.ROW;
            int listHeight = Math.min(Theme.browserHeight(browserWidth), bottom - Theme.ROW - listY);
            list.layout(x, listY, browserWidth, listHeight);
            all.setRectangle((browserWidth - Theme.GAP) / 2, Theme.CONTROL_HEIGHT, x, listY + listHeight + Theme.GAP);
            none.setRectangle(browserWidth - all.getWidth() - Theme.GAP, Theme.CONTROL_HEIGHT,
                    x + all.getWidth() + Theme.GAP, all.getY());
            add.accept(search);
            add.accept(list);
            add.accept(all);
            add.accept(none);
        }
        updateBrowserVisibility();
        var collapse = Theme.button(playerX - Theme.GAP - Theme.CONTROL_HEIGHT, y, Theme.CONTROL_HEIGHT,
                Component.literal(collapsed ? ">" : "<"), button -> {
                    collapsed = !collapsed;
                    rebuild.run();
                });
        collapse.setTooltip(Tooltip.create(text(collapsed ? "browser.expand" : "browser.collapse")));
        add.accept(collapse);
        var director = MusicDirector.get(client);
        controls.player(playerX, y, playerWidth, bottom, add, changeMode);
        update(director);
    }

    public void update(MusicDirector director) {
        controls.update();
    }

    public void refreshSongs() { list.filter(search.getValue()); }

    private void updateBrowserVisibility() {
        search.visible = search.active = !collapsed;
        list.visible = list.active = !collapsed;
        all.visible = all.active = none.visible = none.active = !collapsed && search.getValue().isBlank();
    }

    private void setAll(boolean ticked) {
        if (!search.getValue().isBlank()) return;
        settings.setAllTicked(WhoStoppedTheMusic.LIBRARY.tracks().stream().map(t -> t.id()).toList(), ticked);
        MusicDirector.get(client).playlistChanged();
    }

    private static Component text(String key) { return Component.translatable("whostoppedthemusic." + key); }
}
