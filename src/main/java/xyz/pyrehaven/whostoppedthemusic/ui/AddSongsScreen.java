package xyz.pyrehaven.whostoppedthemusic.ui;

import static xyz.pyrehaven.whostoppedthemusic.ui.Theme.text;

import com.mojang.blaze3d.Blaze3D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import xyz.pyrehaven.whostoppedthemusic.Settings;
import xyz.pyrehaven.whostoppedthemusic.WhoStoppedTheMusic;
import xyz.pyrehaven.whostoppedthemusic.music.MusicFolder;
import xyz.pyrehaven.whostoppedthemusic.music.Track;
import xyz.pyrehaven.whostoppedthemusic.ui.widget.TrackList;

/** Shared file-management destination for every music mode. */
public final class AddSongsScreen extends Screen {
    private final Screen parent;
    private final Settings settings;
    private final MusicFolder folder;
    private EditBox search;
    private TrackList list;
    private Button done;
    private Button open;
    private boolean busy;
    private int left;
    private int contentWidth;

    public AddSongsScreen(Screen parent, Settings settings) {
        super(text("add_music"));
        this.parent = parent;
        this.settings = settings;
        folder = new MusicFolder(minecraft.gameDirectory.toPath());
    }

    public static Button button(Screen parent, Settings settings, int x, int y, int width) {
        return Theme.button(x, y, width, text("add_music"), button ->
                Minecraft.getInstance().gui.setScreen(new AddSongsScreen(parent, settings)));
    }

    @Override protected void init() {
        boolean first = list == null;
        if (first) {
            search = Theme.search(font);
            list = new TrackList(minecraft, settings, 0, 0, Theme.TRACK_HEIGHT, Theme.TRACK_HEIGHT, this::confirmRemove);
            search.setResponder(list::filter);
        }
        contentWidth = Math.min(Theme.MAX_WIDTH, width - Theme.MARGIN * 2);
        left = (width - contentWidth) / 2;
        int x = left + Theme.PADDING;
        int available = contentWidth - Theme.PADDING * 2;
        int y = Theme.BASIC_TOP + Theme.ROW * Theme.IMPORT_HEADER_ROWS;
        search.setRectangle(available, Theme.CONTROL_HEIGHT, x, y);
        addRenderableWidget(search);
        int footer = height - Theme.MARGIN - Theme.CONTROL_HEIGHT;
        list.layout(x, y + Theme.ROW, available, Math.max(Theme.CONTROL_HEIGHT, footer - y - Theme.ROW - Theme.GAP));
        addRenderableWidget(list);
        int half = (available - Theme.GAP) / 2;
        open = addRenderableWidget(Theme.button(x, footer, half, text("open_music_folder"), button -> openFolder()));
        open.setTooltip(Tooltip.create(Component.literal(folder.directory().toString())));
        done = addRenderableWidget(Theme.button(x + half + Theme.GAP, footer, available - half - Theme.GAP,
                Component.translatable("gui.done"), button -> onClose()));
        if (first) {
            setBusy(true);
            WhoStoppedTheMusic.LIBRARY.refreshSongs().thenRun(() -> {
                list.filter(search.getValue());
                setBusy(false);
            });
        }
        setBusy(busy);
    }

    @Override public void onFilesDrop(List<Path> files) {
        if (busy || files.isEmpty()) return;
        var sources = List.copyOf(files);
        var invalid = sources.stream().filter(path -> !MusicFolder.supported(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList();
        if (!invalid.isEmpty()) {
            alert(text("import_rejected", names(invalid)));
            return;
        }
        minecraft.gui.setScreen(new ConfirmScreen(yes -> {
            minecraft.gui.setScreen(this);
            if (yes) changeFiles(() -> importFiles(sources));
        }, title, text("import_confirm", names(sources))));
    }

    private Component importFiles(List<Path> sources) {
        var result = Component.empty();
        for (var source : sources) {
            try {
                var song = folder.importSong(source);
                result.append(text("imported", source.getFileName().toString(), song.file().getFileName().toString()));
            } catch (IOException error) {
                WhoStoppedTheMusic.LOGGER.warn("Can't import song {}", source, error);
                result.append(text("import_failed", source.getFileName().toString()));
            }
            result.append("\n");
        }
        return result;
    }

    private void confirmRemove(Track track) {
        if (busy) return;
        minecraft.gui.setScreen(new ConfirmScreen(yes -> {
            minecraft.gui.setScreen(this);
            if (yes) changeFiles(() -> {
                try {
                    folder.remove(track.local().song());
                    return text("removed", track.local().song().file().getFileName().toString());
                } catch (IOException error) {
                    WhoStoppedTheMusic.LOGGER.warn("Can't remove song {}", track.id(), error);
                    return text("remove_failed", track.local().song().file().getFileName().toString());
                }
            });
        }, text("remove_track", track.title()), text("remove_confirm", track.local().song().file().getFileName().toString()),
                text("remove"), Component.translatable("gui.cancel")));
    }

    private void changeFiles(Supplier<Component> operation) {
        setBusy(true);
        CompletableFuture.supplyAsync(operation, Util.nonCriticalIoPool()).thenAcceptAsync(report ->
                WhoStoppedTheMusic.LIBRARY.refreshSongs().thenRun(() -> {
                    list.filter(search.getValue());
                    setBusy(false);
                    alert(report);
                }), minecraft::execute);
    }

    private void openFolder() {
        try {
            folder.prepare();
            Blaze3D.openPath(folder.directory());
        } catch (IOException error) {
            WhoStoppedTheMusic.LOGGER.warn("Can't open music folder", error);
            alert(text("folder_failed"));
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        done.active = open.active = list.active = search.active = !value;
    }

    private void alert(Component message) {
        minecraft.gui.setScreen(new AlertScreen(() -> minecraft.gui.setScreen(this), title, message));
    }

    @Override public Component getNarrationMessage() {
        return title.copy().append(". ").append(text(busy ? "import_busy" : "import_hint"));
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, Theme.BACKGROUND);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Theme.panelHeading(graphics, font, title, left, contentWidth, height);
        var lines = font.split(text(busy ? "import_busy" : "import_hint"), contentWidth - Theme.PADDING * 2);
        int y = Theme.BASIC_TOP;
        for (var line : lines) {
            graphics.text(font, line, left + Theme.PADDING, y, Theme.ARTIST);
            y += Theme.LINE;
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override public boolean shouldCloseOnEsc() { return !busy; }
    @Override public void onClose() { if (!busy) minecraft.gui.setScreen(parent); }

    private static String names(List<Path> files) {
        return files.stream().map(path -> path.getFileName() == null ? path.toString() : path.getFileName().toString())
                .collect(Collectors.joining(", "));
    }
}
