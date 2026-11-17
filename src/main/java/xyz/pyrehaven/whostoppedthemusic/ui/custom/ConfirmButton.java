package xyz.pyrehaven.whostoppedthemusic.ui.custom;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import xyz.pyrehaven.whostoppedthemusic.ui.OverflowText;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;

/**
 * A quiet button for actions that throw work away. The first click only arms it
 * ("Click again"); a second click within a few seconds runs the action.
 */
public final class ConfirmButton extends Button {
    private static final long ARMED_MILLIS = 3000;
    private final Component caption;
    private final Component confirm;
    private final Runnable action;
    private final OverflowText line = new OverflowText();
    private long armedUntil;

    public ConfirmButton(int x, int y, int width, Component caption, Component confirm, Component description, Runnable action) {
        super(x, y, width, Theme.CONTROL_HEIGHT, description, button -> ((ConfirmButton) button).press(), supplier -> supplier.get());
        this.caption = caption;
        this.confirm = confirm;
        this.action = action;
        setTooltip(Tooltip.create(description));
    }

    private boolean armed() { return Util.getMillis() < armedUntil; }

    private void press() {
        if (armed()) {
            armedUntil = 0;
            action.run();
        } else armedUntil = Util.getMillis() + ARMED_MILLIS;
    }

    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Theme.buttonFrame(graphics, this);
        var font = Minecraft.getInstance().font;
        boolean armed = armed();
        var label = armed ? confirm : caption;
        int left = getX() + Theme.GAP;
        int right = getRight() - Theme.GAP;
        int inset = Math.max(0, (right - left - font.width(label)) / 2);
        line.draw(graphics, font, label, left + inset, right, getY() + (getHeight() - font.lineHeight) / 2,
                !active ? Theme.MUTED : armed ? Theme.ORANGE : Theme.ARTIST, false, false, isHovered());
    }
}
