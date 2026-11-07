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
    public static final int ARTIST = 0xFFB7CADA;
    public static final int SPINDLE = 0xFFE04444;
    public static final long REEL_PERIOD_NANOS = 2_000_000_000L;
    public static final int LABEL_PADDING = 3;
    public static final int LABEL_LINE_GAP = 2;
    public static final int PAUSE_TAPE_HEIGHT = 34;
    public static final int SMALL_REEL = 6;
    public static final int LABEL_MIN_TAPE_HEIGHT = 34;
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
    public static final int SEARCH_LIMIT = 100;
    public static final int BASIC_PLAYER_MIN = 122;
    public static final int BASIC_PLAYER_MAX = 210;
    public static final int BASIC_VOLUME_SPACE = 30;
    public static final int BASIC_BROWSER_MAX_HEIGHT = 230;

    public static final int BASIC_TAPE_MAX_HEIGHT = 130;
    public static final double SCROLL_RESPONSE_MS = 65.0;
    public static final int SCROLL_MAX_FRAME_MS = 100;
    public static final double SCROLL_EPSILON = 0.1;
    public static final int TRACK_SIDE_CONTROLS_MIN = 180;
    public static final int VOLUME_HEIGHT = 120;
    public static final int BASIC_FOOTER_SPACE = MARGIN + CONTROL_HEIGHT + GAP * 2;
    public static final int TRACK_HEIGHT = 100;
    public static final int TRACK_HEADER_HEIGHT = 18;
    public static final int TRACK_PLAY_WIDTH = 24;
    public static final int TRACK_REMOVE_WIDTH = 64;
    public static final int IMPORT_HEADER_ROWS = 2;
    public static final int TRACK_SCROLLBAR_SPACE = 10;
    public static final int TRACK_TEXT_GAP = 4;
    public static final int TOAST_TITLE_Y = 5;
    public static final int TOAST_SONG_Y = 18;
    public static final int TRANSPORT_RADIUS = 4;
    public static final int TRANSPORT_BAR = 2;
    public static final int BASIC_STOP_WIDTH = 44;
    public static final int PAUSE_NEXT_WIDTH = 28;
    public static final int PAUSE_NEXT_Y = 13;
    public static final int PAUSE_NEXT_GAP = 4;
    public static final int VANILLA_NARROW_THRESHOLD = 360;
    public static final int VANILLA_CONTROLS_WIDTH = 170;
    public static final int VANILLA_TOP = 44;
    public static final int VANILLA_TAPE_MIN_HEIGHT = 80;
    public static final int VANILLA_TAPE_MAX_HEIGHT = 160;

    private Theme() {
    }

    public static int browserHeight(int width) {
        boolean narrow = width < TRACK_SIDE_CONTROLS_MIN;
        int tapeWidth = width - TRACK_SCROLLBAR_SPACE
                - (narrow ? 0 : CONTROL_HEIGHT + TRACK_PLAY_WIDTH + TRACK_TEXT_GAP * 2);
        int rowHeight = tapeWidth * 3 / 5 + GAP * 2 + (narrow ? ROW : 0);
        return Math.min(BASIC_BROWSER_MAX_HEIGHT, rowHeight * 3 / 2);
    }

    public static Button button(int x, int y, int width, Component label, Button.OnPress action) {
        return new Button(x, y, width, CONTROL_HEIGHT, label, action, supplier -> supplier.get()) {
            @Override
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                buttonFrame(graphics, this);
                graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE)
                        .acceptScrollingWithDefaultCenter(getMessage().copy().withColor(active ? TEXT : BLUE),
                                getX() + GAP, getRight() - GAP, getY(), getBottom());
            }
        };
    }

    public static net.minecraft.client.gui.components.EditBox search(net.minecraft.client.gui.Font font) {
        var text = Component.translatable("whostoppedthemusic.search");
        var search = new net.minecraft.client.gui.components.EditBox(font, 0, 0, CONTROL_HEIGHT, CONTROL_HEIGHT, text);
        search.setHint(text);
        search.setMaxLength(SEARCH_LIMIT);
        return search;
    }

    public static void buttonFrame(GuiGraphicsExtractor graphics, Button button) {
        graphics.fill(button.getX(), button.getY(), button.getRight(), button.getBottom(), CARD);
        graphics.outline(button.getX(), button.getY(), button.getWidth(), button.getHeight(),
                button.active && button.isHoveredOrFocused() ? GLOW : BLUE);
        graphics.fill(button.getX(), button.getY(), button.getX() + ACCENT, button.getBottom(),
                button.active ? ORANGE : BLUE);
    }
}
