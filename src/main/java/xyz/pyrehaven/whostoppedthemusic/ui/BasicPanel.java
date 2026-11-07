package xyz.pyrehaven.whostoppedthemusic.ui;

import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.Settings;
import xyz.pyrehaven.whostoppedthemusic.WhoStoppedTheMusic;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.ui.widget.TrackList;

public final class BasicPanel {
    private final Button play;
    private final Button previous;

    public BasicPanel(Minecraft client, Settings settings, int x, int width, int height, Consumer<AbstractWidget> add) {
        var director = MusicDirector.get(client);
        int bulkSpace = 2 * (Theme.BASIC_BULK_WIDTH + Theme.GAP);
        var search = new EditBox(client.font, x, Theme.BASIC_SEARCH, width - bulkSpace, Theme.CONTROL_HEIGHT, text("search"));
        search.setHint(text("search"));
        add.accept(search);
        var list = new TrackList(client, settings, x, Theme.BASIC_LIST, width, height - Theme.BASIC_LIST - Theme.BASIC_FOOTER_SPACE);
        add.accept(list);
        search.setResponder(value -> list.filter(value.toLowerCase(Locale.ROOT)));
        add.accept(Theme.button(x + width - bulkSpace + Theme.GAP, Theme.BASIC_SEARCH, Theme.BASIC_BULK_WIDTH, text("all"), button -> {
            settings.setAllTicked(WhoStoppedTheMusic.LIBRARY.tracks().stream().map(t -> t.id()).toList(), true);
            director.playlistChanged();
        }));
        add.accept(Theme.button(x + width - Theme.BASIC_BULK_WIDTH, Theme.BASIC_SEARCH, Theme.BASIC_BULK_WIDTH, text("none"), button -> {
            settings.setAllTicked(WhoStoppedTheMusic.LIBRARY.tracks().stream().map(t -> t.id()).toList(), false);
            director.playlistChanged();
        }));
        int third = (width - Theme.GAP * 2) / 3;
        int y = height - Theme.BASIC_TRANSPORT_BOTTOM;
        previous = Theme.button(x, y, third, text("previous"), button -> director.previous());
        play = Theme.button(x + third + Theme.GAP, y, third, text("play"), button -> director.togglePlayback());
        add.accept(previous);
        add.accept(play);
        add.accept(Theme.button(x + (third + Theme.GAP) * 2, y, third, text("next"), button -> director.next()));
        update(director);
    }

    public void update(MusicDirector director) {
        play.setMessage(text(director.active() ? "stop" : "play"));
        play.active = director.active() || director.canNext();
        previous.active = director.canPrevious();
    }

    private static Component text(String key) { return Component.translatable("whostoppedthemusic." + key); }
}
