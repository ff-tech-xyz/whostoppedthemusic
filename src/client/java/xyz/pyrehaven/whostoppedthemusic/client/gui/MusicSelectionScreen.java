package xyz.pyrehaven.whostoppedthemusic.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

    private final Screen parent;
    private TrackListWidget trackList;
    private Button previousButton;
    private Button nextButton;
    private Button stopButton;
    private Button shuffleButton;
    private Button selectAllButton;
    private Button deselectAllButton;
    private EditBox searchBox;

    public MusicSelectionScreen(Screen parent) {
        super(Component.translatable("screen.whostoppedthemusic.music"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (this.width >= 540) {
            initWideLayout();
        } else {
            initCompactLayout();
        }
        refreshControlState();
    }

    private void initWideLayout() {
        int top = 32;
        int bottom = this.height - 8;
        int controlsX = this.width - CONTROL_WIDTH - MARGIN;
        int listX = MARGIN;
        int listWidth = Math.max(180, controlsX - listX - GAP);
        int listHeight = Math.max(48, bottom - top);

        this.trackList = new TrackListWidget(this.minecraft, listWidth, listHeight, top, listX);
        this.addRenderableWidget(this.trackList);

        int y = top;
        addControlsCheckbox(controlsX, y);
        y += 24;

        addSearchBox(controlsX, y, CONTROL_WIDTH);
        y += 28;

        addBulkButtons(controlsX, y, CONTROL_WIDTH, CONTROL_WIDTH, 0, true);
        y += 52;

        this.previousButton = Button.builder(Component.translatable("button.whostoppedthemusic.previous"), button -> WhostmMusicController.skipPrevious(this.minecraft))
                .bounds(controlsX, y, 52, 20)
                .build();
        this.stopButton = Button.builder(Component.translatable("button.whostoppedthemusic.stop"), button -> WhostmMusicController.stop(this.minecraft))
                .bounds(controlsX + 64, y, 52, 20)
                .build();
        this.nextButton = Button.builder(Component.translatable("button.whostoppedthemusic.next"), button -> WhostmMusicController.skipNext(this.minecraft))
                .bounds(controlsX + 128, y, 52, 20)
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
                .bounds(controlsX, y, CONTROL_WIDTH, 20)
                .build();
        this.addRenderableWidget(this.shuffleButton);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(controlsX, this.height - 28, CONTROL_WIDTH, 20)
                .build());
    }

    private void initCompactLayout() {
        int center = this.width / 2;
        int rowWidth = Math.min(548, Math.max(220, this.width - 16));
        int rowX = center - rowWidth / 2;
        int rowY = 32;
        int selectWidth = 82;
        int deselectWidth = 92;

        addControlsCheckbox(center - 47, rowY);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(rowX + rowWidth - 58, rowY, 58, 20)
                .build());
        addSearchBox(rowX, rowY + 24, rowWidth);
        addBulkButtons(center - (selectWidth + deselectWidth + 6) / 2, rowY + 48, selectWidth, deselectWidth, 6, false);

        int bottomControlsY = this.height - 28;
        int listTop = rowY + 76;
        int listHeight = Math.max(48, this.height - listTop - 52);
        this.trackList = new TrackListWidget(this.minecraft, this.width, listHeight, listTop, 0);
        this.addRenderableWidget(this.trackList);

        this.previousButton = Button.builder(Component.translatable("button.whostoppedthemusic.previous"), button -> WhostmMusicController.skipPrevious(this.minecraft))
                .bounds(center - 132, bottomControlsY, 38, 20)
                .build();
        this.stopButton = Button.builder(Component.translatable("button.whostoppedthemusic.stop"), button -> WhostmMusicController.stop(this.minecraft))
                .bounds(center - 90, bottomControlsY, 64, 20)
                .build();
        this.nextButton = Button.builder(Component.translatable("button.whostoppedthemusic.next"), button -> WhostmMusicController.skipNext(this.minecraft))
                .bounds(center - 22, bottomControlsY, 38, 20)
                .build();
        this.shuffleButton = Button.builder(shuffleMessage(), button -> {
                    WhostmMusicController.toggleShuffle(this.minecraft);
                    button.setMessage(shuffleMessage());
                    refreshControlState();
                })
                .bounds(center + 20, bottomControlsY, 110, 20)
                .build();
        this.addRenderableWidget(this.previousButton);
        this.addRenderableWidget(this.stopButton);
        this.addRenderableWidget(this.nextButton);
        this.addRenderableWidget(this.shuffleButton);
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

    private void addBulkButtons(int x, int y, int selectWidth, int deselectWidth, int gap, boolean stacked) {
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
                .bounds(stacked ? x : x + selectWidth + gap, stacked ? y + 24 : y, deselectWidth, 20)
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
        extractor.text(this.font, WhostmMusicController.status(this.minecraft), MARGIN, this.height - 42, 0xFFA0E0FF);
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
            this.stopButton.active = active;
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
            this.enabled = Checkbox.builder(track.displayName(), minecraft.font)
                    .selected(WhostmMusicController.isEnabled(track))
                    .maxWidth(340)
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

            this.enabled.extractRenderState(extractor, mouseX, mouseY, partialTick);
            this.play.extractRenderState(extractor, mouseX, mouseY, partialTick);
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
