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
import xyz.pyrehaven.whostoppedthemusic.client.WhostmMusicHud;

import java.util.List;
import java.util.Locale;

public final class MusicSelectionScreen extends Screen {
    private final Screen parent;
    private TrackListWidget trackList;
    private Button previousButton;
    private Button nextButton;
    private Button stopButton;
    private Button shuffleButton;
    private Button selectAllButton;
    private Button deselectAllButton;
    private EditBox searchBox;
    private int hintY;

    public MusicSelectionScreen(Screen parent) {
        super(Component.translatable("screen.whostoppedthemusic.music"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int cassetteWidth = Math.min(360, Math.max(220, this.width - 112));
        int cassetteX = center - cassetteWidth / 2;
        int cassetteY = 8;
        int cassetteHeight = 44;
        int arrowY = cassetteY + cassetteHeight / 2 - 10;

        this.previousButton = Button.builder(Component.translatable("button.whostoppedthemusic.previous"), button -> WhostmMusicController.skipPrevious(this.minecraft))
                .bounds(Math.max(8, cassetteX - 32), arrowY, 28, 20)
                .build();
        this.nextButton = Button.builder(Component.translatable("button.whostoppedthemusic.next"), button -> WhostmMusicController.skipNext(this.minecraft))
                .bounds(Math.min(this.width - 36, cassetteX + cassetteWidth + 4), arrowY, 28, 20)
                .build();
        this.addRenderableWidget(this.previousButton);
        this.addRenderableWidget(this.nextButton);

        int rowWidth = Math.min(548, Math.max(220, this.width - 16));
        int rowX = center - rowWidth / 2;
        int rowY = 60;
        int listTop;
        int selectWidth = 82;
        int deselectWidth = 92;

        if (rowWidth >= 430) {
            int controlsWidth = 94;
            int gap = 5;
            int searchWidth = Math.max(110, rowWidth - controlsWidth - selectWidth - deselectWidth - gap * 3);
            this.addControlsCheckbox(rowX, rowY + 1);
            this.addSearchBox(rowX + controlsWidth + gap, rowY, searchWidth);
            int selectX = rowX + controlsWidth + gap + searchWidth + gap;
            this.addBulkButtons(selectX, rowY, selectWidth, deselectWidth, gap);
            this.hintY = 78;
            listTop = 86;
        } else {
            this.addControlsCheckbox(center - 47, rowY + 1);
            this.addSearchBox(rowX, rowY + 24, rowWidth);
            int gap = 6;
            int bulkWidth = selectWidth + deselectWidth + gap;
            this.addBulkButtons(center - bulkWidth / 2, rowY + 48, selectWidth, deselectWidth, gap);
            this.hintY = rowY + 72;
            listTop = rowY + 82;
        }

        int bottomControlsY = this.height - 28;
        int listHeight = Math.max(48, this.height - listTop - 52);
        this.trackList = new TrackListWidget(this.minecraft, this.width, listHeight, listTop);
        this.addRenderableWidget(this.trackList);

        this.stopButton = Button.builder(Component.translatable("button.whostoppedthemusic.stop"), button -> WhostmMusicController.stop(this.minecraft))
                .bounds(center - 132, bottomControlsY, 78, 20)
                .build();
        this.shuffleButton = Button.builder(shuffleMessage(), button -> {
                    WhostmMusicController.toggleShuffle(this.minecraft);
                    button.setMessage(shuffleMessage());
                    refreshControlState();
                })
                .bounds(center - 50, bottomControlsY, 110, 20)
                .build();
        this.addRenderableWidget(this.stopButton);
        this.addRenderableWidget(this.shuffleButton);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(center + 64, bottomControlsY, 78, 20)
                .build());
        refreshControlState();
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

        int cassetteWidth = Math.min(360, Math.max(220, this.width - 112));
        int cassetteX = this.width / 2 - cassetteWidth / 2;
        WhostmMusicHud.drawCassette(
                extractor,
                this.font,
                cassetteX,
                8,
                cassetteWidth,
                44,
                Component.translatable("text.whostoppedthemusic.cassette_title"),
                WhostmMusicController.currentSongMessage(),
                242
        );
        extractor.centeredText(this.font, Component.translatable("text.whostoppedthemusic.settings_hint"), this.width / 2, this.hintY, 0xA7B9CB);
        extractor.centeredText(this.font, WhostmMusicController.status(this.minecraft), this.width / 2, this.height - 44, 0xA0E0FF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(this.parent);
    }

    private void refreshControlState() {
        boolean active = WhostmMusicController.controlsEnabled(this.minecraft);
        boolean canSkip = active && WhostmMusicController.hasEnabledTracks(this.minecraft);
        if (this.previousButton != null) {
            this.previousButton.active = canSkip;
            this.nextButton.active = canSkip;
        }
        if (this.stopButton != null) {
            this.stopButton.active = active;
            this.shuffleButton.active = active && WhostmMusicController.hasEnabledTracks(this.minecraft);
            this.shuffleButton.setMessage(shuffleMessage());
            this.selectAllButton.active = active;
            this.deselectAllButton.active = active;
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

        private TrackListWidget(Minecraft minecraft, int width, int height, int top) {
            super(minecraft, width, height, top, 24);
            this.minecraft = minecraft;
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
            return Math.min(520, Math.max(300, this.width - 52));
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
