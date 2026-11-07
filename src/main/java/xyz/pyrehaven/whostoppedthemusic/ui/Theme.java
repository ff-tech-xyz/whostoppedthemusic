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
    public static final int SCENARIO_PANEL = 0xFFC8D1D9;
    public static final int SCENARIO_BOX = 0xFF737373;
    public static final int SCENARIO_FILL = 0xFFB4B4B4;
    public static final int SCENARIO_TEXT = 0xFF000000;
    public static final int SCENARIO_ROW = 34;
    public static final int TOGGLE_WIDTH = 26;
    public static final int TOGGLE_HEIGHT = 12;
    public static final int POPUP_WIDTH = 196;
    public static final int POPUP_HEIGHT = 62;
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
                settingContents(graphics, this);
            }
        };
    }

    public static void settingContents(GuiGraphicsExtractor graphics, Button button) {
        buttonFrame(graphics, button);
        graphics.textRendererForWidget(button, GuiGraphicsExtractor.HoveredTextEffects.NONE)
                .acceptScrollingWithDefaultCenter(button.getMessage().copy().withColor(button.active ? TEXT : BLUE),
                        button.getX() + GAP, button.getRight() - GAP, button.getY(), button.getBottom());
    }

    public static net.minecraft.client.gui.components.EditBox search(net.minecraft.client.gui.Font font) {
        return search(font, Component.translatable("whostoppedthemusic.search"));
    }

    public static net.minecraft.client.gui.components.EditBox search(net.minecraft.client.gui.Font font, Component text) {
        var search = new net.minecraft.client.gui.components.EditBox(font, 0, 0, CONTROL_HEIGHT, CONTROL_HEIGHT, text);
        search.setHint(text);
        search.setMaxLength(SEARCH_LIMIT);
        return search;
    }

    public static void toggle(GuiGraphicsExtractor graphics, int x, int y, boolean on, boolean focused) {
        int radius = TOGGLE_HEIGHT / 2;
        for (int row = 0; row < TOGGLE_HEIGHT; row++) {
            int inset = row == 0 || row == TOGGLE_HEIGHT - 1 ? radius / 2 : row == 1 || row == TOGGLE_HEIGHT - 2 ? BORDER : 0;
            graphics.fill(x + inset, y + row, x + TOGGLE_WIDTH - inset, y + row + BORDER,
                    on ? SCENARIO_TEXT : SCENARIO_FILL);
        }
        int knobX = x + (on ? TOGGLE_WIDTH - TOGGLE_HEIGHT : 0) + BORDER;
        graphics.fill(knobX, y + BORDER, knobX + TOGGLE_HEIGHT - BORDER * 2, y + TOGGLE_HEIGHT - BORDER,
                on ? SCENARIO_FILL : SCENARIO_BOX);
        if (focused) graphics.outline(x, y, TOGGLE_WIDTH, TOGGLE_HEIGHT, TEXT);
    }

    public static void buttonFrame(GuiGraphicsExtractor graphics, Button button) {
        graphics.fill(button.getX(), button.getY(), button.getRight(), button.getBottom(), CARD);
        graphics.outline(button.getX(), button.getY(), button.getWidth(), button.getHeight(),
                button.active && button.isHoveredOrFocused() ? GLOW : BLUE);
        graphics.fill(button.getX(), button.getY(), button.getX() + ACCENT, button.getBottom(),
                button.active ? ORANGE : BLUE);
    }
}
