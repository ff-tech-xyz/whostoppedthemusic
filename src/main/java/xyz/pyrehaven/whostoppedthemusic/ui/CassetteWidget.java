package xyz.pyrehaven.whostoppedthemusic.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import xyz.pyrehaven.whostoppedthemusic.Settings;
import xyz.pyrehaven.whostoppedthemusic.mixin.MusicManagerAccessor;

public final class CassetteWidget extends Button {
    private final Minecraft client;
    private final PauseScreen parent;

    public CassetteWidget(Minecraft client, PauseScreen parent, Settings settings) {
        super(Theme.MARGIN, parent.height - Theme.MARGIN - Theme.CASSETTE_HEIGHT,
                Math.min(Theme.CASSETTE_WIDTH, parent.width - Theme.MARGIN * 2), Theme.CASSETTE_HEIGHT,
                Component.translatable("whostoppedthemusic.cassette.open"),
                button -> client.gui.setScreen(new MusicScreen(parent, settings)), DEFAULT_NARRATION);
        this.client = client;
        this.parent = parent;
        setTooltip(Tooltip.create(getMessage()));
    }

    public Component status() {
        SoundInstance music = ((MusicManagerAccessor) client.getMusicManager()).whostoppedthemusic$currentMusic();
        String key = client.getMusicManager().getCurrentMusicTranslationKey();
        boolean audible = music != null && client.getSoundManager().isActive(music)
                && client.options.getFinalSoundSourceVolume(SoundSource.MUSIC) > 0;
        if (!audible || key == null) {
            return Component.translatable("whostoppedthemusic.cassette.silence");
        }
        // Vanilla already owns the title at the top-left. Never repeat it here.
        return parent.rendersNowPlayingToast() ? Component.translatable("whostoppedthemusic.cassette.open")
                : Component.translatable(key.replace('/', '.'));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Component status = status();
        setMessage(Component.translatable("whostoppedthemusic.cassette.narration", status));
        graphics.fill(getX(), getY(), getRight(), getBottom(), Theme.CARD);
        graphics.outline(getX(), getY(), getWidth(), getHeight(), isHoveredOrFocused() ? Theme.GLOW : Theme.BLUE);
        graphics.fill(getX(), getY(), getX() + Theme.ACCENT, getBottom(), Theme.ORANGE);
        int reelY = getY() + Theme.REEL_INSET;
        int reelX = getX() + Theme.REEL_INSET;
        graphics.outline(reelX, reelY, Theme.CASSETTE_ICON - Theme.REEL_INSET, Theme.REEL * 2, Theme.ORANGE);
        graphics.outline(reelX + Theme.BORDER, reelY + Theme.REEL / 2, Theme.REEL, Theme.REEL, Theme.GLOW);
        graphics.outline(reelX + Theme.CASSETTE_ICON - Theme.REEL_INSET - Theme.REEL - Theme.BORDER,
                reelY + Theme.REEL / 2, Theme.REEL, Theme.REEL, Theme.GLOW);
        graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE)
                .acceptScrollingWithDefaultCenter(status.copy().withColor(Theme.TEXT),
                        getX() + Theme.CASSETTE_ICON + Theme.GAP, getRight() - Theme.GAP, getY(), getBottom());
    }
}
