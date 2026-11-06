package xyz.pyrehaven.whostoppedthemusic.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

/** Vertical view of the existing vanilla Music option, not a separate volume setting. */
public final class MusicVolumeSlider extends AbstractWidget {
    private final Minecraft client;

    public MusicVolumeSlider(Minecraft client, int x, int y, int height) {
        super(x, y, 30, height, Component.translatable("soundCategory.music"));
        this.client = client;
    }

    private double value() {
        return client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get();
    }

    private void set(double value) {
        client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(Math.clamp(value, 0.0, 1.0));
    }

    private void setFromMouse(double y) {
        set(1 - (y - getY() - 20) / (getHeight() - 40));
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        setFromMouse(event.y());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        setFromMouse(event.y());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        switch (event.key()) {
            case InputConstants.KEY_UP, InputConstants.KEY_RIGHT -> set(value() + 0.05);
            case InputConstants.KEY_DOWN, InputConstants.KEY_LEFT -> set(value() - 0.05);
            case InputConstants.KEY_HOME -> set(0);
            case InputConstants.KEY_END -> set(1);
            default -> { return false; }
        }
        return true;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int center = getX() + getWidth() / 2;
        int bottom = getBottom() - 20;
        int knob = bottom - (int) Math.round(value() * (getHeight() - 40));
        graphics.centeredText(client.font, getMessage(), center, getY(), Theme.TEXT);
        graphics.fill(center - 2, getY() + 20, center + 2, bottom, Theme.BLUE);
        graphics.fill(center - 2, knob, center + 2, bottom, Theme.ORANGE);
        graphics.fill(center - 7, knob - 3, center + 7, knob + 3, isHoveredOrFocused() ? Theme.GLOW : Theme.TEXT);
        graphics.centeredText(client.font, Component.literal(Math.round(value() * 100) + "%"),
                center, getBottom() - 9, Theme.TEXT);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
        return Component.translatable("options.percent_value", getMessage(), Math.round(value() * 100));
    }
}
