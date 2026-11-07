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
    public static final int BASIC_TOP = 34;
    public static final int BASIC_SECOND = BASIC_TOP + ROW;
    public static final int BASIC_STATUS = BASIC_SECOND + ROW;
    public static final int BASIC_SEARCH = 100;
    public static final int BASIC_LIST = BASIC_SEARCH + ROW;
    public static final int BASIC_MODE_WIDTH = 70;
    public static final int BASIC_SHUFFLE_WIDTH = 100;
    public static final int BASIC_BULK_WIDTH = 40;
    public static final int BASIC_VOLUME_SPACE = 36;
    public static final int VOLUME_HEIGHT = 120;
    public static final int BASIC_FOOTER_SPACE = 64;
    public static final int BASIC_TRANSPORT_BOTTOM = BASIC_FOOTER_SPACE - GAP;
    public static final int TRACK_HEIGHT = 24;
    public static final int TRACK_HEADER_HEIGHT = 18;
    public static final int TRACK_PLAY_WIDTH = 24;
    public static final int TRACK_SCROLLBAR_SPACE = 10;
    public static final int TRACK_TEXT_GAP = 4;
    public static final int TOAST_TITLE_Y = 5;
    public static final int TOAST_SONG_Y = 18;

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
