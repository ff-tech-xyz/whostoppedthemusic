package xyz.pyrehaven.whostoppedthemusic.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

public final class WhostmMusicController {
    private static final Random RANDOM = new Random();
    private static final ArrayDeque<Identifier> QUEUE = new ArrayDeque<>();
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
            QUEUE.clear();
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
            QUEUE.removeIf(id -> id.equals(track.id()));
            if (track.id().equals(currentTrackId)) {
                skip(minecraft);
            }
        }
        saveConfig(minecraft);
    }

    public static void playNow(MusicTrack track, Minecraft minecraft) {
        if (!controlsEnabled(minecraft)) {
            return;
        }
        if (!isEnabled(track)) {
            setEnabled(track, true, minecraft);
        }
        stopCurrent(minecraft);
        play(track.id(), minecraft);
    }

    public static void enqueue(MusicTrack track, Minecraft minecraft) {
        if (controlsEnabled(minecraft) && isEnabled(track)) {
            QUEUE.addLast(track.id());
        }
    }

    public static void skip(Minecraft minecraft) {
        if (!controlsEnabled(minecraft)) {
            return;
        }
        stopCurrent(minecraft);
        current = null;
        currentTrackId = null;
        playNextIfNeeded(minecraft);
    }

    public static void stop(Minecraft minecraft) {
        if (!controlsEnabled(minecraft)) {
            return;
        }
        stopCurrent(minecraft);
        current = null;
        currentTrackId = null;
    }

    public static void clearQueue(Minecraft minecraft) {
        if (controlsEnabled(minecraft)) {
            QUEUE.clear();
        }
    }

    public static void toggleShuffle(Minecraft minecraft) {
        if (!controlsEnabled(minecraft)) {
            return;
        }
        shuffle = !shuffle;
        saveConfig(minecraft);
    }

    public static boolean shuffle() {
        return shuffle;
    }

    public static int queueSize() {
        return QUEUE.size();
    }

    public static Component status(Minecraft minecraft) {
        if (!controlsEnabled(minecraft)) {
            return Component.literal("Controls: off | Vanilla music control active | No random gaps");
        }
        String currentName = currentTrackId == null
                ? "none"
                : MusicCatalog.title(currentTrackId).orElse(currentTrackId.toString());
        return Component.literal("Controls: on | Now playing: " + currentName + " | Queue: " + QUEUE.size() + " | Shuffle: " + (shuffle ? "on" : "off"));
    }

    public static boolean shouldSuppressVanillaMusic(Minecraft minecraft) {
        return controlsEnabled(minecraft) && (isCustomActive(minecraft) || !QUEUE.isEmpty() || shuffle);
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

    private static void playNextIfNeeded(Minecraft minecraft) {
        if (minecraft == null || current != null) {
            return;
        }

        Optional<Identifier> next = nextTrack();
        next.ifPresent(id -> play(id, minecraft));
    }

    private static Optional<Identifier> nextTrack() {
        while (!QUEUE.isEmpty()) {
            Identifier queued = QUEUE.removeFirst();
            if (!DISABLED.contains(queued.toString())) {
                return Optional.of(queued);
            }
        }

        if (!shuffle) {
            return Optional.empty();
        }

        List<Identifier> enabled = TRACKS.stream()
                .filter(WhostmMusicController::isEnabled)
                .map(MusicTrack::id)
                .toList();
        if (enabled.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(enabled.get(RANDOM.nextInt(enabled.size())));
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
