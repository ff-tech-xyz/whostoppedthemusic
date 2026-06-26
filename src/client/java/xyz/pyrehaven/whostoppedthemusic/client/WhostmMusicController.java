package xyz.pyrehaven.whostoppedthemusic.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

public final class WhostmMusicController {
    private static final Random RANDOM = new Random();
    private static final Set<String> DISABLED = new HashSet<>();
    private static final List<MusicTrack> TRACKS = MusicCatalog.tracks();

    private static EventMusicSoundInstance current;
    private static Identifier currentTrackId;
    private static boolean controlsEnabled;
    private static boolean shuffle;
    private static boolean configLoaded;

    private WhostmMusicController() {
    }

    public static List<MusicTrack> tracks(Minecraft minecraft) {
        ensureConfig(minecraft);
        return TRACKS;
    }

    public static boolean controlsEnabled(Minecraft minecraft) {
        ensureConfig(minecraft);
        return controlsEnabled;
    }

    public static void setControlsEnabled(boolean enabled, Minecraft minecraft) {
        ensureConfig(minecraft);
        controlsEnabled = enabled;
        if (!enabled) {
            stopCurrent(minecraft);
            current = null;
            currentTrackId = null;
        }
        saveConfig(minecraft);
    }

    public static boolean isEnabled(MusicTrack track) {
        return !DISABLED.contains(track.key());
    }

    public static void setEnabled(MusicTrack track, boolean enabled, Minecraft minecraft) {
        if (!controlsEnabled(minecraft)) {
            return;
        }
        if (enabled) {
            DISABLED.remove(track.key());
        } else {
            DISABLED.add(track.key());
            if (track.id().equals(currentTrackId)) {
                stopCurrent(minecraft);
                current = null;
                currentTrackId = null;
                playNextIfNeeded(minecraft);
            }
        }
        saveConfig(minecraft);
    }

    public static void setAllEnabled(boolean enabled, Minecraft minecraft) {
        if (!controlsEnabled(minecraft)) {
            return;
        }
        if (enabled) {
            DISABLED.clear();
        } else {
            for (MusicTrack track : TRACKS) {
                DISABLED.add(track.key());
            }
        }
        if (currentTrackId != null && !isCurrentTrackEnabled()) {
            stopCurrent(minecraft);
            current = null;
            currentTrackId = null;
        }
        saveConfig(minecraft);
        playNextIfNeeded(minecraft);
    }

    public static void playNow(MusicTrack track, Minecraft minecraft) {
        if (!controlsEnabled(minecraft)) {
            return;
        }
        if (!isEnabled(track)) {
            DISABLED.remove(track.key());
            saveConfig(minecraft);
        }
        stopCurrent(minecraft);
        play(track.id(), minecraft);
    }

    public static void skip(Minecraft minecraft) {
        skipNext(minecraft);
    }

    public static void skipNext(Minecraft minecraft) {
        skipTo(minecraft, true);
    }

    public static void skipPrevious(Minecraft minecraft) {
        skipTo(minecraft, false);
    }

    public static void stop(Minecraft minecraft) {
        if (!controlsEnabled(minecraft)) {
            return;
        }
        stopCurrent(minecraft);
        current = null;
        currentTrackId = null;
    }

    public static void toggleShuffle(Minecraft minecraft) {
        if (!controlsEnabled(minecraft)) {
            return;
        }
        shuffle = !shuffle;
        saveConfig(minecraft);
        playNextIfNeeded(minecraft);
    }

    public static boolean shuffle() {
        return shuffle;
    }

    public static boolean hasEnabledTracks(Minecraft minecraft) {
        ensureConfig(minecraft);
        return TRACKS.stream().anyMatch(WhostmMusicController::isEnabled);
    }

    public static Component currentSongMessage() {
        return WhostmMusicHud.nowPlayingMessage();
    }

    public static Component status(Minecraft minecraft) {
        if (!controlsEnabled(minecraft)) {
            return Component.literal("Controls: off | Vanilla chooses music normally | No random gaps");
        }
        String currentName = currentTrackId == null
                ? WhostmMusicHud.currentSong().getString()
                : MusicCatalog.title(currentTrackId).orElse(currentTrackId.toString());
        String enabled = enabledTrackCount() + "/" + TRACKS.size() + " enabled";
        return Component.literal("Controls: on | Now playing: " + currentName + " | " + enabled + " | Shuffle: " + (shuffle ? "on" : "off"));
    }

    public static boolean shouldSuppressVanillaMusic(Minecraft minecraft) {
        return controlsEnabled(minecraft) && (isCustomActive(minecraft) || (shuffle && hasEnabledTracks(minecraft)));
    }

    public static boolean isCustomActive(Minecraft minecraft) {
        return minecraft != null && current != null && minecraft.getSoundManager().isActive(current);
    }

    public static void tick(Minecraft minecraft) {
        ensureConfig(minecraft);
        WhostmMusicHud.tick();
        if (!controlsEnabled) {
            return;
        }
        if (current != null && !minecraft.getSoundManager().isActive(current)) {
            current = null;
            currentTrackId = null;
        }
        playNextIfNeeded(minecraft);
    }

    private static void skipTo(Minecraft minecraft, boolean forward) {
        if (!controlsEnabled(minecraft)) {
            return;
        }
        Optional<Identifier> next = selectManualTrack(forward);
        stopCurrent(minecraft);
        current = null;
        currentTrackId = null;
        next.ifPresent(id -> play(id, minecraft));
    }

    private static void playNextIfNeeded(Minecraft minecraft) {
        if (minecraft == null || current != null || !shuffle) {
            return;
        }

        Optional<Identifier> next = selectRandomTrack();
        next.ifPresent(id -> play(id, minecraft));
    }

    private static Optional<Identifier> selectManualTrack(boolean forward) {
        List<MusicTrack> enabled = enabledTracks();
        if (enabled.isEmpty()) {
            return Optional.empty();
        }
        if (shuffle) {
            return selectRandomTrack();
        }
        int currentIndex = -1;
        if (currentTrackId != null) {
            for (int i = 0; i < enabled.size(); i++) {
                if (enabled.get(i).id().equals(currentTrackId)) {
                    currentIndex = i;
                    break;
                }
            }
        }
        int nextIndex;
        if (currentIndex < 0) {
            nextIndex = forward ? 0 : enabled.size() - 1;
        } else if (forward) {
            nextIndex = (currentIndex + 1) % enabled.size();
        } else {
            nextIndex = (currentIndex - 1 + enabled.size()) % enabled.size();
        }
        return Optional.of(enabled.get(nextIndex).id());
    }

    private static Optional<Identifier> selectRandomTrack() {
        List<MusicTrack> enabled = enabledTracks();
        if (enabled.isEmpty()) {
            return Optional.empty();
        }
        if (enabled.size() == 1) {
            return Optional.of(enabled.getFirst().id());
        }
        MusicTrack selected;
        do {
            selected = enabled.get(RANDOM.nextInt(enabled.size()));
        } while (selected.id().equals(currentTrackId));
        return Optional.of(selected.id());
    }

    private static List<MusicTrack> enabledTracks() {
        return TRACKS.stream()
                .filter(WhostmMusicController::isEnabled)
                .toList();
    }

    private static int enabledTrackCount() {
        return enabledTracks().size();
    }

    private static boolean isCurrentTrackEnabled() {
        return currentTrackId == null || DISABLED.stream().noneMatch(disabled -> disabled.equals(currentTrackId.toString()));
    }

    private static void play(Identifier id, Minecraft minecraft) {
        EventMusicSoundInstance instance = new EventMusicSoundInstance(id);
        current = instance;
        currentTrackId = id;
        minecraft.getSoundManager().play(instance);
    }

    private static void stopCurrent(Minecraft minecraft) {
        if (minecraft != null && current != null) {
            minecraft.getSoundManager().stop(current);
        }
    }

    private static void ensureConfig(Minecraft minecraft) {
        if (configLoaded || minecraft == null) {
            return;
        }
        configLoaded = true;
        Properties props = new Properties();
        Path path = configPath(minecraft);
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            } catch (IOException ignored) {
                // Bad config should not stop the client from opening the music menu.
            }
        }
        controlsEnabled = Boolean.parseBoolean(props.getProperty("controls", "false"));
        shuffle = Boolean.parseBoolean(props.getProperty("shuffle", "false"));
        String disabled = props.getProperty("disabled", "");
        for (String id : disabled.split(",")) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                DISABLED.add(trimmed);
            }
        }
    }

    private static void saveConfig(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        Properties props = new Properties();
        props.setProperty("controls", Boolean.toString(controlsEnabled));
        props.setProperty("shuffle", Boolean.toString(shuffle));
        props.setProperty("disabled", String.join(",", DISABLED));
        Path path = configPath(minecraft);
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "Who Stopped The Music client settings");
            }
        } catch (IOException ignored) {
            // Keep the music UI usable even if config writing fails.
        }
    }

    private static Path configPath(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath().resolve("config").resolve("whostoppedthemusic.properties");
    }
}
