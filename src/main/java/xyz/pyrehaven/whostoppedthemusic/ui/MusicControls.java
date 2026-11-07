package xyz.pyrehaven.whostoppedthemusic.ui;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.Settings;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;

/** Shared controls observe the director; panels own their placement. */
public final class MusicControls {
    private final Minecraft client;
    private final Settings settings;
    private TransportButton primary;
    private TransportButton next;

    public MusicControls(Minecraft client, Settings settings) {
        this.client = client;
        this.settings = settings;
    }

    public void settings(int x, int y, int width, Consumer<AbstractWidget> add, Runnable changeMode) {
        add.accept(Theme.button(x, y, width, text("mode." + settings.mode().name().toLowerCase(java.util.Locale.ROOT)),
                button -> changeMode.run()));
        add.accept(Theme.button(x, y + Theme.ROW, width, frequencyLabel(), button -> {
            var option = client.options.musicFrequency();
            var values = net.minecraft.client.sounds.MusicManager.MusicFrequency.values();
            option.set(values[(option.get().ordinal() + 1) % values.length]);
            button.setMessage(frequencyLabel());
        }));
        add.accept(Theme.button(x, y + Theme.ROW * 2, width, jukeboxLabel(), button -> {
            settings.setJukebox(settings.jukebox().next());
            button.setMessage(jukeboxLabel());
        }));
    }

    public void transport(int x, int y, int width, Consumer<AbstractWidget> add) {
        var director = MusicDirector.get(client);
        primary = new TransportButton(x, y, width, TransportButton.Action.PLAY, () -> {
            director.togglePlayback();
            update();
        });
        next = new TransportButton(x + width + Theme.GAP, y, width, TransportButton.Action.NEXT, () -> {
            director.next();
            update();
        });
        add.accept(primary);
        add.accept(next);
        update();
    }

    public void update() {
        var director = MusicDirector.get(client);
        primary.active = director.active() || director.canNext();
        primary.action(!director.active() ? TransportButton.Action.PLAY
                : director.paused() ? TransportButton.Action.RESUME : TransportButton.Action.PAUSE);
        next.active = director.canNext();
    }

    private Component frequencyLabel() {
        return Component.translatable("whostoppedthemusic.frequency", client.options.musicFrequency().get().caption());
    }

    private Component jukeboxLabel() {
        return Component.translatable("whostoppedthemusic.jukebox",
                text("jukebox." + settings.jukebox().name().toLowerCase(java.util.Locale.ROOT)));
    }

    private static Component text(String key) { return Component.translatable("whostoppedthemusic." + key); }
}
