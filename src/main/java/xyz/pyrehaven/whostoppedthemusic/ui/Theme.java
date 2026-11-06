package xyz.pyrehaven.whostoppedthemusic.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class Theme {
    public static final int ORANGE = 0xFFE46011;
    public static final int BLUE = 0xFF27607C;
    public static final int GLOW = 0xFF4FA8D8;
    public static final int BACKGROUND = 0xFF070B10;
    public static final int CARD = 0xFF0E141C;
    public static final int TEXT = 0xFFF2F4F7;
    public static final int MARGIN = 8;
    public static final int PADDING = 12;
    public static final int GAP = 6;
    public static final int LINE = 12;
    public static final int BORDER = 1;
    public static final int ACCENT = 2;
    public static final int CONTROL_HEIGHT = 20;
    public static final int ROW = CONTROL_HEIGHT + GAP;
    public static final int MAX_WIDTH = 600;
    public static final int DONE_WIDTH = 120;

    private Theme() {
    }

    public static Button button(int x, int y, int width, Component label, Button.OnPress action) {
        return new Button(x, y, width, CONTROL_HEIGHT, label, action, supplier -> supplier.get()) {
            @Override
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                graphics.fill(getX(), getY(), getRight(), getBottom(), CARD);
                graphics.outline(getX(), getY(), getWidth(), getHeight(), isHoveredOrFocused() ? GLOW : BLUE);
                graphics.fill(getX(), getY(), getX() + ACCENT, getBottom(), ORANGE);
                graphics.centeredText(net.minecraft.client.Minecraft.getInstance().font, getMessage(),
                        getX() + getWidth() / 2, getY() + GAP, active ? TEXT : BLUE);
            }
        };
    }
}
