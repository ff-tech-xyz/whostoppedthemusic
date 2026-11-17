package xyz.pyrehaven.whostoppedthemusic.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class Theme {
    /*
     * One accent, everything else neutral. Orange only ever means "on / selected / playing",
     * so it stands out when it shows up instead of being on every edge.
     */
    public static final int ORANGE = 0xFFE46011;
    public static final int EDGE = 0xFF2C3542;
    public static final int HOVER = 0xFF8A96A8;
    public static final int MUTED = 0xFF5C6676;
    public static final int BACKGROUND = 0xFF0B0E13;
    public static final int CARD = 0xFF141922;
    public static final int RAISED = 0xFF1C232E;
    public static final int TEXT = 0xFFECEEF1;

    public static final int SCENARIO_ROW = 34;
    public static final int LIBRARY_ROW = 24;
    public static final int BASIC_ROW = 26;
    public static final int CASE_SPINE = 7;
    public static final int CASSETTE_WIDTH = 190;
    public static final int CASSETTE_HEIGHT = 100;
    public static final int DRAG_WIDTH = CASSETTE_WIDTH;
    public static final int DRAG_HEIGHT = CASSETTE_HEIGHT;
    public static final int INSERT_MILLIS = 260;

    public static final int EDITOR_BACK_WIDTH = 48;
    public static final int INTEGER_LENGTH = 11;
    public static final int PRIORITY_LABEL_WIDTH = 60;
    public static final int TOGGLE_WIDTH = 26;
    public static final int TOGGLE_HEIGHT = 12;
    public static final int POPUP_WIDTH = 196;
    public static final int POPUP_HEIGHT = 62;
    public static final int ARTIST = 0xFF9AA4B2;
    public static final int SPINDLE = MUTED;
    public static final long REEL_PERIOD_NANOS = 2_000_000_000L;
    public static final int LABEL_PADDING = 3;
    public static final int LABEL_LINE_GAP = 2;
    public static final int MENU_BUTTON_WIDTH = 200;
    public static final int MENU_FOOTER = 28;
    public static final int NOTE_RISE = 24;
    public static final int NOTE_COUNT = 3;
    public static final long NOTE_PERIOD_NANOS = 1_800_000_000L;
    public static final int SMALL_REEL = 6;
    public static final int REEL_WIDTH_DIVISOR = 24;
    public static final int LABEL_MIN_TAPE_HEIGHT = 34;
    public static final int MARGIN = 8;
    public static final int PADDING = 12;
    public static final int GAP = 6;
    public static final int LINE = 12;
    public static final int BORDER = 1;
    public static final int ACCENT = 2;
    public static final int CONTROL_HEIGHT = 20;
    public static final int ROW = CONTROL_HEIGHT + GAP;
    public static final int EDITOR_CONTROLS = ROW * 2 + LINE;
    public static final int EDITOR_DETAIL = ROW * 2 + LINE;
    public static final int MISSING_OVERLAY = 0x66737373;
    public static final int MAX_WIDTH = 600;
    public static final int DONE_WIDTH = 120;
    public static final int BASIC_TOP = 34;
    public static final int SEARCH_LIMIT = 100;
    public static final int BASIC_PLAYER_MIN = 122;
    public static final int BASIC_PLAYER_MAX = 210;
    public static final int BASIC_VOLUME_SPACE = 30;

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
    public static final int TRANSPORT_RADIUS = 4;
    public static final int TRANSPORT_BAR = 2;
    public static final int BASIC_STOP_WIDTH = 44;
    public static final int PAUSE_NEXT_WIDTH = 28;
    public static final int PAUSE_NEXT_GAP = 4;
    public static final int VANILLA_NARROW_THRESHOLD = 360;
    public static final int VANILLA_CONTROLS_WIDTH = 170;
    public static final int VANILLA_TOP = 44;

    private Theme() {
    }

    public static MutableComponent text(String key, Object... args) {
        return Component.translatable("whostoppedthemusic." + key, args);
    }

    public static void panel(GuiGraphicsExtractor graphics, int left, int width, int height) {
        graphics.fill(left, MARGIN, left + width, height - MARGIN, CARD);
    }

    public static void panelHeading(GuiGraphicsExtractor graphics, Font font, Component title, int left, int width, int height) {
        panel(graphics, left, width, height);
        graphics.text(font, title, left + PADDING, MARGIN + GAP, TEXT);
    }

    public static Button button(int x, int y, int width, Component label, Button.OnPress action) {
        return captionButton(x, y, width, null, label, action);
    }

    public static Button captionButton(int x, int y, int width, Component caption, Component description, Button.OnPress action) {
        var button = new Button(x, y, width, CONTROL_HEIGHT, description, action, supplier -> supplier.get()) {
            private final OverflowText line = new OverflowText();
            @Override
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                scenarioContents(graphics, this, caption == null ? getMessage() : caption, line, getX() + GAP, getRight() - GAP);
            }
        };
        button.setTooltip(Tooltip.create(description));
        return button;
    }

    public static void scenarioContents(GuiGraphicsExtractor graphics, Button button, Component label, OverflowText line, int left, int right) {
        buttonFrame(graphics, button);
        var font = Minecraft.getInstance().font;
        int inset = Math.max(0, (right - left - font.width(label)) / 2);
        line.draw(graphics, font, label, left + inset, right,
                button.getY() + (button.getHeight() - font.lineHeight) / 2, button.active ? TEXT : MUTED, false, false, button.isHovered());
    }

    public static EditBox search(Font font) {
        return search(font, Component.translatable("whostoppedthemusic.search"));
    }

    public static EditBox search(Font font, Component text) {
        var search = new EditBox(font, 0, 0, CONTROL_HEIGHT, CONTROL_HEIGHT, text);
        search.setHint(text);
        search.setMaxLength(SEARCH_LIMIT);
        return search;
    }

    public static void toggle(GuiGraphicsExtractor graphics, int x, int y, boolean on, boolean focused) {
        int radius = TOGGLE_HEIGHT / 2;
        for (int row = 0; row < TOGGLE_HEIGHT; row++) {
            int inset = row == 0 || row == TOGGLE_HEIGHT - 1 ? radius / 2 : row == 1 || row == TOGGLE_HEIGHT - 2 ? BORDER : 0;
            graphics.fill(x + inset, y + row, x + TOGGLE_WIDTH - inset, y + row + BORDER,
                    on ? ORANGE : MUTED);
        }
        int knobX = x + (on ? TOGGLE_WIDTH - TOGGLE_HEIGHT : 0) + BORDER;
        graphics.fill(knobX, y + BORDER, knobX + TOGGLE_HEIGHT - BORDER * 2, y + TOGGLE_HEIGHT - BORDER,
                TEXT);
        if (focused) graphics.outline(x, y, TOGGLE_WIDTH, TOGGLE_HEIGHT, TEXT);
    }

    /**
     * Plain frame: one fill and one outline. Hover lightens both; no stripes or shadows.
     * Disabled buttons sink into the background so they don't look clickable.
     */
    public static void buttonFrame(GuiGraphicsExtractor graphics, Button button) {
        if (!button.active) {
            graphics.fill(button.getX(), button.getY(), button.getRight(), button.getBottom(), BACKGROUND);
            graphics.outline(button.getX(), button.getY(), button.getWidth(), button.getHeight(), CARD);
            return;
        }
        boolean hot = button.isHoveredOrFocused();
        graphics.fill(button.getX(), button.getY(), button.getRight(), button.getBottom(), hot ? RAISED : CARD);
        graphics.outline(button.getX(), button.getY(), button.getWidth(), button.getHeight(), hot ? HOVER : EDGE);
    }
}
