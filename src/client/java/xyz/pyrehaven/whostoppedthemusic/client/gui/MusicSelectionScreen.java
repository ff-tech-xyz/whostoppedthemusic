package xyz.pyrehaven.whostoppedthemusic.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.client.MusicTrack;
import xyz.pyrehaven.whostoppedthemusic.client.WhostmMusicController;

import java.util.List;

public final class MusicSelectionScreen extends Screen {
    private final Screen parent;
    private TrackListWidget trackList;
    private Button skipButton;
    private Button stopButton;
    private Button shuffleButton;
    private Button clearQueueButton;

    public MusicSelectionScreen(Screen parent) {
        super(Component.translatable("screen.whostoppedthemusic.music"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int listTop = 62;
        int bottomControlsY = this.height - 28;
        int listHeight = Math.max(48, this.height - listTop - 42);

        this.addRenderableWidget(Checkbox.builder(Component.translatable("button.whostoppedthemusic.controls"), this.font)
                .pos(this.width / 2 - 52, 34)
                .selected(WhostmMusicController.controlsEnabled(this.minecraft))
                .onValueChange((checkbox, selected) -> {
                    WhostmMusicController.setControlsEnabled(selected, this.minecraft);
                    refreshControlState();
                })
                .build());

        this.trackList = new TrackListWidget(this.minecraft, this.width, listHeight, listTop);
        this.addRenderableWidget(this.trackList);

        int center = this.width / 2;
        this.skipButton = Button.builder(Component.translatable("button.whostoppedthemusic.skip"), button -> WhostmMusicController.skip(this.minecraft))
                .bounds(center - 154, bottomControlsY, 72, 20)
                .build();
        this.stopButton = Button.builder(Component.translatable("button.whostoppedthemusic.stop"), button -> WhostmMusicController.stop(this.minecraft))
                .bounds(center - 78, bottomControlsY, 72, 20)
                .build();
        this.shuffleButton = Button.builder(shuffleMessage(), button -> {
                    WhostmMusicController.toggleShuffle(this.minecraft);
                    button.setMessage(shuffleMessage());
                })
                .bounds(center - 2, bottomControlsY, 92, 20)
                .build();
        this.clearQueueButton = Button.builder(Component.translatable("button.whostoppedthemusic.clear_queue"), button -> WhostmMusicController.clearQueue(this.minecraft))
                .bounds(center + 94, bottomControlsY, 92, 20)
                .build();
        this.addRenderableWidget(this.skipButton);
        this.addRenderableWidget(this.stopButton);
        this.addRenderableWidget(this.shuffleButton);
        this.addRenderableWidget(this.clearQueueButton);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(this.width - 82, 8, 74, 20)
                .build());
        refreshControlState();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        refreshControlState();
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        extractor.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        extractor.centeredText(this.font, Component.translatable("text.whostoppedthemusic.hotkey"), this.width / 2, 26, 0xA0A0A0);
        extractor.centeredText(this.font, Component.translatable("text.whostoppedthemusic.controls_hint"), this.width / 2, 50, 0x808080);
        extractor.centeredText(this.font, WhostmMusicController.status(this.minecraft), this.width / 2, this.height - 42, 0xA0E0FF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(this.parent);
    }

    private void refreshControlState() {
        boolean active = WhostmMusicController.controlsEnabled(this.minecraft);
        if (this.skipButton != null) {
            this.skipButton.active = active;
            this.stopButton.active = active;
            this.shuffleButton.active = active;
            this.shuffleButton.setMessage(shuffleMessage());
            this.clearQueueButton.active = active;
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
        private TrackListWidget(Minecraft minecraft, int width, int height, int top) {
            super(minecraft, width, height, top, 24);
            this.centerListVertically = false;
            for (MusicTrack track : WhostmMusicController.tracks(minecraft)) {
                this.addEntry(new TrackEntry(track, minecraft));
            }
        }

        private void refreshControlState(boolean controlsActive) {
            for (TrackEntry entry : this.children()) {
                entry.refreshControlState(controlsActive);
            }
        }

        @Override
        public int getRowWidth() {
            return Math.min(460, Math.max(300, this.width - 80));
        }
    }

    private static final class TrackEntry extends ContainerObjectSelectionList.Entry<TrackEntry> {
        private final MusicTrack track;
        private final Minecraft minecraft;
        private final Checkbox enabled;
        private final Button play;
        private final Button queue;
        private final List<GuiEventListener> children;
        private final List<NarratableEntry> narratables;

        private TrackEntry(MusicTrack track, Minecraft minecraft) {
            this.track = track;
            this.minecraft = minecraft;
            this.enabled = Checkbox.builder(track.displayName(), minecraft.font)
                    .selected(WhostmMusicController.isEnabled(track))
                    .maxWidth(290)
                    .onValueChange((checkbox, selected) -> WhostmMusicController.setEnabled(track, selected, minecraft))
                    .build();
            this.play = Button.builder(Component.translatable("button.whostoppedthemusic.play"), button -> WhostmMusicController.playNow(track, minecraft))
                    .size(48, 20)
                    .build();
            this.queue = Button.builder(Component.translatable("button.whostoppedthemusic.queue"), button -> WhostmMusicController.enqueue(track, minecraft))
                    .size(58, 20)
                    .build();
            this.children = List.of(this.enabled, this.play, this.queue);
            this.narratables = List.of(this.enabled, this.play, this.queue);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor extractor, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int y = this.getContentY() + 2;
            int x = this.getContentX() + 4;
            int right = this.getContentRight() - 4;
            refreshControlState(WhostmMusicController.controlsEnabled(this.minecraft));

            this.enabled.setPosition(x, y + 1);
            this.play.setPosition(right - 112, y);
            this.queue.setPosition(right - 60, y);

            this.enabled.extractRenderState(extractor, mouseX, mouseY, partialTick);
            this.play.extractRenderState(extractor, mouseX, mouseY, partialTick);
            this.queue.extractRenderState(extractor, mouseX, mouseY, partialTick);
        }

        private void refreshControlState(boolean controlsActive) {
            boolean enabledTrack = WhostmMusicController.isEnabled(this.track);
            this.enabled.active = controlsActive;
            this.play.active = controlsActive && enabledTrack;
            this.queue.active = controlsActive && enabledTrack;
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
