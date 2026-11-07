package xyz.pyrehaven.whostoppedthemusic.ui;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    private Button play;
    private Button previous;
    private Button next;
    private boolean collapsed;

    public BasicPanel(Minecraft client, Settings settings) {
        this.client = client;
        this.settings = settings;
        search = new EditBox(client.font, 0, 0, Theme.CONTROL_HEIGHT, Theme.CONTROL_HEIGHT, text("search"));
        search.setHint(text("search"));
        search.setMaxLength(Theme.SEARCH_LIMIT);
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
        add.accept(Theme.button(playerX, y, playerWidth, text("mode.basic"), button -> changeMode.run()));
        add.accept(Theme.button(playerX, y + Theme.ROW, playerWidth, frequencyLabel(), button -> {
            var option = client.options.musicFrequency();
            var values = net.minecraft.client.sounds.MusicManager.MusicFrequency.values();
            option.set(values[(option.get().ordinal() + 1) % values.length]);
            button.setMessage(frequencyLabel());
        }));
        add.accept(Theme.button(playerX, y + Theme.ROW * 2, playerWidth, jukeboxLabel(), button -> {
            settings.setJukebox(settings.jukebox().next());
            button.setMessage(jukeboxLabel());
        }));
        int third = (playerWidth - Theme.GAP * 2) / 3;
        int transportY = y + Theme.ROW * 3;
        previous = action(playerX, transportY, third, "<", "previous", director::previous);
        play = action(playerX + third + Theme.GAP, transportY, third, ">", "play", director::togglePlayback);
        next = action(playerX + (third + Theme.GAP) * 2, transportY, third, ">", "next", director::next);
        add.accept(previous);
        add.accept(play);
        add.accept(next);
        add.accept(Theme.button(playerX, transportY + Theme.ROW, playerWidth, shuffleLabel(), button -> {
            settings.setShuffle(!settings.shuffle());
            director.playlistChanged();
            button.setMessage(shuffleLabel());
        }));
        int tapeY = transportY + Theme.ROW * 2;
        int tapeHeight = Math.min(Math.min(Theme.BASIC_TAPE_MAX_HEIGHT, playerWidth * 3 / 5), bottom - tapeY);
        var tape = new Button(playerX, tapeY, playerWidth, tapeHeight, director.status(), button -> { }, supplier -> supplier.get()) {
            @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                setMessage(director.status());
                CassetteArtwork.draw(graphics, this, getMessage(), false);
            }
        };
        add.accept(tape);
        add.accept(new MusicVolumeSlider(client, playerX + playerWidth + Theme.GAP, y, bottom - y));
        update(director);
    }

    public void update(MusicDirector director) {
        play.setMessage(Component.literal(director.active() ? "[]" : ">"));
        play.setTooltip(Tooltip.create(text(director.active() ? "stop" : "play")));
        play.active = director.active() || director.canNext();
        previous.active = director.canPrevious();
        next.active = director.canNext();
    }

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

    private Button action(int x, int y, int width, String icon, String key, Runnable press) {
        var button = Theme.button(x, y, width, Component.literal(icon), ignored -> press.run());
        button.setTooltip(Tooltip.create(text(key)));
        return button;
    }

    private Component frequencyLabel() {
        return Component.translatable("whostoppedthemusic.frequency", client.options.musicFrequency().get().caption());
    }

    private Component jukeboxLabel() {
        return Component.translatable("whostoppedthemusic.jukebox",
                text("jukebox." + settings.jukebox().name().toLowerCase(java.util.Locale.ROOT)));
    }

    private Component shuffleLabel() {
        return Component.translatable("whostoppedthemusic.shuffle", Component.translatable(settings.shuffle() ? "options.on" : "options.off"));
    }

    private static Component text(String key) { return Component.translatable("whostoppedthemusic." + key); }
}
