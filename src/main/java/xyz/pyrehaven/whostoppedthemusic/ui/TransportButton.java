package xyz.pyrehaven.whostoppedthemusic.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public final class TransportButton extends Button {
    public enum Action { PLAY, PAUSE, RESUME, NEXT, PREVIOUS }

    private Action action;

    public TransportButton(int x, int y, int width, Action action, Runnable press) {
        super(x, y, width, Theme.CONTROL_HEIGHT, Component.empty(), ignored -> press.run(), DEFAULT_NARRATION);
        action(action);
    }

    public void action(Action action) {
        if (this.action == action) return;
        this.action = action;
        setMessage(Component.translatable("whostoppedthemusic." + action.name().toLowerCase(java.util.Locale.ROOT)));
        setTooltip(Tooltip.create(getMessage()));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Theme.buttonFrame(graphics, this);
        int x = getX() + getWidth() / 2;
        int y = getY() + getHeight() / 2;
        int radius = Theme.TRANSPORT_RADIUS;
        int bar = Theme.TRANSPORT_BAR;
        int color = active ? Theme.TEXT : Theme.BLUE;
        if (action == Action.PAUSE) {
            graphics.fill(x - radius, y - radius, x - radius + bar, y + radius, color);
            graphics.fill(x + radius - bar, y - radius, x + radius, y + radius, color);
            return;
        }
        boolean previous = action == Action.PREVIOUS;
        int direction = previous ? -1 : 1;
        int start = x - direction * radius;
        for (int column = 0; column < radius * 2; column++) {
            int height = radius - column / 2;
            int edge = start + direction * column;
            graphics.fill(edge, y - height, edge + 1, y + height, color);
        }
        if (action == Action.NEXT || previous) {
            int edge = x + direction * radius;
            graphics.fill(edge, y - radius, edge + bar, y + radius, color);
        }
    }
}
