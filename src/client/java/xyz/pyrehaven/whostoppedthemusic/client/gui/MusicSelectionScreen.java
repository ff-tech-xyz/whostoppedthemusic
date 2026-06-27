package xyz.pyrehaven.whostoppedthemusic.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.client.MusicTrack;
import xyz.pyrehaven.whostoppedthemusic.client.WhostmMusicController;

import java.util.List;
import java.util.Locale;

public final class MusicSelectionScreen extends Screen {
    private static final int MARGIN = 8;
    private static final int GAP = 8;
    private static final int CONTROL_WIDTH = 180;
    private static final int VOLUME_WIDTH = 34;

    private final Screen parent;
    private TrackListWidget trackList;
    private Button previousButton;
    private Button nextButton;
    private Button stopButton;
    private Button shuffleButton;
    private Button selectAllButton;
    private Button deselectAllButton;
    private MusicVolumeSlider volumeSlider;
    private EditBox searchBox;
    private int statusX;
    private int statusY;
    private int statusWidth;

    public MusicSelectionScreen(Screen parent) {
        super(Component.translatable("screen.whostoppedthemusic.music"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        initSideBySideLayout();
        refreshControlState();
    }

    private void initSideBySideLayout() {
        int top = 32;
        int bottom = this.height - 36;
        int availableWidth = Math.max(240, this.width - MARGIN * 2);
        int volumeWidth = this.width >= 380 ? VOLUME_WIDTH : 26;
        int minListWidth = this.width >= 380 ? 160 : 120;
        int maxControlsWidth = Math.max(96, availableWidth - volumeWidth - GAP * 2 - minListWidth);
        int controlsWidth = Math.min(CONTROL_WIDTH, Math.min(maxControlsWidth, Math.max(112, availableWidth / 3)));
        int volumeX = this.width - MARGIN - volumeWidth;
        int controlsX = volumeX - GAP - controlsWidth;
        int listX = MARGIN;
        int listWidth = Math.max(80, controlsX - listX - GAP);
        int listTop = top + 24;
        int listHeight = Math.max(48, bottom - listTop);

        addSearchBox(listX, top, listWidth);
        this.trackList = new TrackListWidget(this.minecraft, listWidth, listHeight, listTop, listX);
        this.addRenderableWidget(this.trackList);

        int y = top;
        addControlsCheckbox(controlsX, y);
        y += 24;

        int smallGap = 4;
        int bulkWidth = Math.max(54, (controlsWidth - smallGap) / 2);
        addBulkButtons(controlsX, y, bulkWidth, Math.max(54, controlsWidth - bulkWidth - smallGap), smallGap);
        y += 24;

        int arrowWidth = Math.min(44, Math.max(34, (controlsWidth - smallGap * 2) / 4));
        int stopWidth = Math.max(44, controlsWidth - arrowWidth * 2 - smallGap * 2);
        this.previousButton = Button.builder(Component.translatable("button.whostoppedthemusic.previous"), button -> WhostmMusicController.skipPrevious(this.minecraft))
                .bounds(controlsX, y, arrowWidth, 20)
                .build();
        this.stopButton = Button.builder(WhostmMusicController.playStopMessage(this.minecraft), button -> {
                    WhostmMusicController.playOrStop(this.minecraft);
                    refreshControlState();
                })
                .bounds(controlsX + arrowWidth + smallGap, y, stopWidth, 20)
                .build();
        this.nextButton = Button.builder(Component.translatable("button.whostoppedthemusic.next"), button -> WhostmMusicController.skipNext(this.minecraft))
                .bounds(controlsX + arrowWidth + smallGap + stopWidth + smallGap, y, arrowWidth, 20)
                .build();
        this.addRenderableWidget(this.previousButton);
        this.addRenderableWidget(this.stopButton);
        this.addRenderableWidget(this.nextButton);
        y += 24;

        this.shuffleButton = Button.builder(shuffleMessage(), button -> {
                    WhostmMusicController.toggleShuffle(this.minecraft);
                    button.setMessage(shuffleMessage());
                    refreshControlState();
                })
                .bounds(controlsX, y, controlsWidth, 20)
                .build();
        this.addRenderableWidget(this.shuffleButton);
        y += 28;

        this.statusX = controlsX;
        this.statusY = y;
        this.statusWidth = controlsWidth;

        this.volumeSlider = new MusicVolumeSlider(volumeX, top, volumeWidth, Math.max(96, bottom - top), this.minecraft, true);
        this.addRenderableWidget(this.volumeSlider);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(controlsX, this.height - 28, controlsWidth, 20)
                .build());
    }

    private void addControlsCheckbox(int x, int y) {
        this.addRenderableWidget(Checkbox.builder(Component.translatable("button.whostoppedthemusic.controls"), this.font)
                .pos(x, y)
                .selected(WhostmMusicController.controlsEnabled(this.minecraft))
                .onValueChange((checkbox, selected) -> {
                    WhostmMusicController.setControlsEnabled(selected, this.minecraft);
                    refreshControlState();
                    if (this.trackList != null) {
                        this.trackList.rebuildEntries();
                    }
                })
                .build());
    }

    private void addSearchBox(int x, int y, int width) {
        this.searchBox = new EditBox(this.font, x, y, width, 20, Component.translatable("text.whostoppedthemusic.search"));
        this.searchBox.setHint(Component.translatable("text.whostoppedthemusic.search"));
        this.searchBox.setMaxLength(80);
        this.searchBox.setResponder(query -> {
            if (this.trackList != null) {
                this.trackList.setFilter(query);
            }
        });
        this.addRenderableWidget(this.searchBox);
    }

    private void addBulkButtons(int x, int y, int selectWidth, int deselectWidth, int gap) {
        this.selectAllButton = Button.builder(Component.translatable("button.whostoppedthemusic.select_all"), button -> {
                    WhostmMusicController.setAllEnabled(true, this.minecraft);
                    this.trackList.rebuildEntries();
                    refreshControlState();
                })
                .bounds(x, y, selectWidth, 20)
                .build();
        this.deselectAllButton = Button.builder(Component.translatable("button.whostoppedthemusic.deselect_all"), button -> {
                    WhostmMusicController.setAllEnabled(false, this.minecraft);
                    this.trackList.rebuildEntries();
                    refreshControlState();
                })
                .bounds(x + selectWidth + gap, y, deselectWidth, 20)
                .build();
        this.addRenderableWidget(this.selectAllButton);
        this.addRenderableWidget(this.deselectAllButton);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        refreshControlState();
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);

        extractor.centeredText(this.font, this.title, this.width / 2, 8, 0xFFF6F1E2);
        extractor.centeredText(this.font, WhostmMusicController.currentSongMessage(), this.width / 2, 20, 0xFFA0E0FF);
        extractor.textWithWordWrap(this.font, WhostmMusicController.status(this.minecraft), this.statusX, this.statusY, this.statusWidth, 0xFFA0E0FF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(this.parent);
    }

    private void refreshControlState() {
        boolean active = WhostmMusicController.controlsEnabled(this.minecraft);
        boolean hasEnabledTracks = WhostmMusicController.hasEnabledTracks(this.minecraft);
        boolean canSkip = active && hasEnabledTracks;
        if (this.previousButton != null) {
            this.previousButton.active = canSkip;
            this.nextButton.active = canSkip;
        }
        if (this.stopButton != null) {
            this.stopButton.active = active && (hasEnabledTracks || WhostmMusicController.isMusicPlaying(this.minecraft));
            this.stopButton.setMessage(WhostmMusicController.playStopMessage(this.minecraft));
        }
        if (this.volumeSlider != null) {
            this.volumeSlider.active = true;
            this.volumeSlider.syncFromOptions();
        }
        if (this.shuffleButton != null) {
            this.shuffleButton.active = active && hasEnabledTracks;
            this.shuffleButton.setMessage(shuffleMessage());
        }
        if (this.selectAllButton != null) {
            this.selectAllButton.active = active;
        }
        if (this.deselectAllButton != null) {
            this.deselectAllButton.active = active;
        }
        if (this.searchBox != null) {
            this.searchBox.active = active;
            this.searchBox.setEditable(active);
        }
        if (this.trackList != null) {
            this.trackList.refreshControlState(active);
        }
    }

    private static Component shuffleMessage() {
        return Component.translatable(WhostmMusicController.shuffle()
                ? "button.whostoppedthemusic.shuffle_on"
                : "button.whostoppedthemusic.shuffle_off");
    }

    private static final class MusicVolumeSlider extends AbstractSliderButton {
        private final Minecraft minecraft;
        private final boolean vertical;

        private MusicVolumeSlider(int x, int y, int width, int height, Minecraft minecraft, boolean vertical) {
            super(x, y, width, height, volumeMessage(WhostmMusicController.musicVolume(minecraft)), WhostmMusicController.musicVolume(minecraft));
            this.minecraft = minecraft;
            this.vertical = vertical;
        }

        private void syncFromOptions() {
            double optionValue = WhostmMusicController.musicVolume(this.minecraft);
            if (Math.abs(optionValue - this.value) > 0.001D) {
                this.value = optionValue;
                updateMessage();
            }
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            if (!this.vertical) {
                super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
                return;
            }

            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();
            int border = this.active ? 0xFFA0E0FF : 0xFF66717D;
            int fill = this.isHoveredOrFocused() ? 0xCC2A2435 : 0xAA14131B;
            int accent = 0xFFE7B966;
            int text = this.active ? 0xFFF6F1E2 : 0xFF7B8794;
            int trackTop = y + 24;
            int trackBottom = y + height - 28;
            int trackX = x + width / 2 - 2;
            int handleY = trackBottom - (int) Math.round(this.value * Math.max(1, trackBottom - trackTop));

            graphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, 0x66000000);
            graphics.fill(x, y, x + width, y + height, fill);
            graphics.outline(x, y, width, height, border);
            graphics.centeredText(this.minecraft.font, "Vol", x + width / 2, y + 7, text);
            graphics.fill(trackX, trackTop, trackX + 4, trackBottom, 0xFF333744);
            graphics.outline(trackX - 1, trackTop - 1, 6, trackBottom - trackTop + 2, 0xFF11121A);
            graphics.fill(x + 5, handleY - 3, x + width - 5, handleY + 4, accent);
            graphics.outline(x + 5, handleY - 3, width - 10, 7, 0xFF3C2626);
            graphics.centeredText(this.minecraft.font, percentText(this.value), x + width / 2, y + height - 17, text);
        }

        @Override
        public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
            if (!this.vertical) {
                super.onClick(event, doubleClick);
                return;
            }
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            setValueFromMouse(event.y());
        }

        @Override
        protected void onDrag(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
            if (!this.vertical) {
                super.onDrag(event, dragX, dragY);
                return;
            }
            setValueFromMouse(event.y());
        }

        private void setValueFromMouse(double mouseY) {
            double top = this.getY() + 24.0D;
            double bottom = this.getY() + this.getHeight() - 28.0D;
            double range = Math.max(1.0D, bottom - top);
            double next = 1.0D - (mouseY - top) / range;
            this.setValue(Math.max(0.0D, Math.min(1.0D, next)));
        }

        @Override
        protected void updateMessage() {
            this.setMessage(volumeMessage(this.value));
        }

        @Override
        protected void applyValue() {
            WhostmMusicController.setMusicVolume(this.minecraft, this.value);
        }

        private static Component volumeMessage(double value) {
            int percent = percent(value);
            return Component.translatable("button.whostoppedthemusic.volume", percent);
        }

        private static String percentText(double value) {
            return percent(value) + "%";
        }

        private static int percent(double value) {
            return (int) Math.round(Math.max(0.0D, Math.min(1.0D, value)) * 100.0D);
        }
    }

    private static final class TrackListWidget extends ContainerObjectSelectionList<TrackEntry> {
        private final Minecraft minecraft;
        private String filter = "";

        private TrackListWidget(Minecraft minecraft, int width, int height, int top, int x) {
            super(minecraft, width, height, top, 24);
            this.minecraft = minecraft;
            this.setX(x);
            this.centerListVertically = false;
            rebuildEntries();
        }

        private void setFilter(String filter) {
            this.filter = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
            rebuildEntries();
            this.setScrollAmount(0.0D);
        }

        private void rebuildEntries() {
            this.clearEntries();
            for (MusicTrack track : WhostmMusicController.tracks(this.minecraft)) {
                if (matches(track)) {
                    this.addEntry(new TrackEntry(track, this.minecraft));
                }
            }
        }

        private boolean matches(MusicTrack track) {
            if (this.filter.isEmpty()) {
                return true;
            }
            return track.displayName().getString().toLowerCase(Locale.ROOT).contains(this.filter)
                    || track.key().toLowerCase(Locale.ROOT).contains(this.filter);
        }

        private void refreshControlState(boolean controlsActive) {
            for (TrackEntry entry : this.children()) {
                entry.refreshControlState(controlsActive);
            }
        }

        @Override
        public int getRowWidth() {
            return Math.max(180, this.getWidth() - 24);
        }

        @Override
        protected int scrollBarX() {
            return this.getX() + this.getWidth() - 6;
        }
    }

    private static final class TrackEntry extends ContainerObjectSelectionList.Entry<TrackEntry> {
        private final MusicTrack track;
        private final Minecraft minecraft;
        private final Checkbox enabled;
        private final Button play;
        private final List<GuiEventListener> children;
        private final List<NarratableEntry> narratables;

        private TrackEntry(MusicTrack track, Minecraft minecraft) {
            this.track = track;
            this.minecraft = minecraft;
            this.enabled = Checkbox.builder(Component.empty(), minecraft.font)
                    .selected(WhostmMusicController.isEnabled(track))
                    .maxWidth(Checkbox.getBoxSize(minecraft.font))
                    .onValueChange((checkbox, selected) -> WhostmMusicController.setEnabled(track, selected, minecraft))
                    .build();
            this.play = Button.builder(Component.translatable("button.whostoppedthemusic.play"), button -> WhostmMusicController.playNow(track, minecraft))
                    .size(58, 20)
                    .build();
            this.children = List.of(this.enabled, this.play);
            this.narratables = List.of(this.enabled, this.play);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor extractor, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int y = this.getContentY() + 2;
            int x = this.getContentX() + 4;
            int right = this.getContentRight() - 4;
            refreshControlState(WhostmMusicController.controlsEnabled(this.minecraft));

            this.enabled.setPosition(x, y + 1);
            this.play.setPosition(right - 62, y);

            int checkboxSize = Checkbox.getBoxSize(this.minecraft.font);
            int titleX = x + checkboxSize + 6;
            int titleWidth = Math.max(20, this.play.getX() - titleX - 8);

            this.enabled.extractRenderState(extractor, mouseX, mouseY, partialTick);
            drawMarqueeTitle(extractor, this.track.displayName().getString(), titleX, y + 6, titleWidth, hovered);
            this.play.extractRenderState(extractor, mouseX, mouseY, partialTick);
        }

        private void drawMarqueeTitle(GuiGraphicsExtractor extractor, String title, int x, int y, int width, boolean hovered) {
            int color = WhostmMusicController.isEnabled(this.track) ? 0xFFF6F1E2 : 0xFF7B8794;
            int textWidth = this.minecraft.font.width(title);
            if (textWidth <= width) {
                extractor.text(this.minecraft.font, title, x, y, color);
                return;
            }

            int overflow = textWidth - width;
            int pauseMs = hovered ? 250 : 900;
            int scrollMs = Math.max(1800, overflow * 45);
            long phase = Math.floorMod(System.currentTimeMillis() + Math.abs(this.track.key().hashCode() % 1000), (pauseMs + scrollMs) * 2L);
            int offset;
            if (phase < pauseMs) {
                offset = 0;
            } else if (phase < pauseMs + scrollMs) {
                offset = (int) Math.round((phase - pauseMs) * overflow / (double) scrollMs);
            } else if (phase < pauseMs * 2L + scrollMs) {
                offset = overflow;
            } else {
                offset = overflow - (int) Math.round((phase - pauseMs * 2L - scrollMs) * overflow / (double) scrollMs);
            }

            extractor.enableScissor(x, y - 1, x + width, y + this.minecraft.font.lineHeight + 1);
            extractor.text(this.minecraft.font, title, x - offset, y, color);
            extractor.disableScissor();
        }

        private void refreshControlState(boolean controlsActive) {
            boolean enabledTrack = WhostmMusicController.isEnabled(this.track);
            this.enabled.active = controlsActive;
            this.play.active = controlsActive && enabledTrack;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.children;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.narratables;
        }
    }
}
