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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

public final class WhostmMusicController {
    private static final List<Identifier> FALLBACK_TRACK_IDS = List.of(
            Identifier.withDefaultNamespace("music.menu"),
            Identifier.withDefaultNamespace("music.game"),
            Identifier.withDefaultNamespace("music.creative"),
            Identifier.withDefaultNamespace("music.credits"),
            Identifier.withDefaultNamespace("music.dragon"),
            Identifier.withDefaultNamespace("music.end"),
            Identifier.withDefaultNamespace("music.under_water"),
            Identifier.withDefaultNamespace("music.nether.basalt_deltas"),
            Identifier.withDefaultNamespace("music.nether.crimson_forest"),
            Identifier.withDefaultNamespace("music.nether.nether_wastes"),
            Identifier.withDefaultNamespace("music.nether.soul_sand_valley"),
            Identifier.withDefaultNamespace("music.nether.warped_forest"),
            Identifier.withDefaultNamespace("music.overworld.badlands"),
            Identifier.withDefaultNamespace("music.overworld.bamboo_jungle"),
            Identifier.withDefaultNamespace("music.overworld.cherry_grove"),
            Identifier.withDefaultNamespace("music.overworld.deep_dark"),
            Identifier.withDefaultNamespace("music.overworld.desert"),
            Identifier.withDefaultNamespace("music.overworld.dripstone_caves"),
            Identifier.withDefaultNamespace("music.overworld.flower_forest"),
            Identifier.withDefaultNamespace("music.overworld.forest"),
            Identifier.withDefaultNamespace("music.overworld.frozen_peaks"),
            Identifier.withDefaultNamespace("music.overworld.grove"),
            Identifier.withDefaultNamespace("music.overworld.jagged_peaks"),
            Identifier.withDefaultNamespace("music.overworld.jungle"),
            Identifier.withDefaultNamespace("music.overworld.lush_caves"),
            Identifier.withDefaultNamespace("music.overworld.meadow"),
            Identifier.withDefaultNamespace("music.overworld.old_growth_taiga"),
            Identifier.withDefaultNamespace("music.overworld.snowy_slopes"),
            Identifier.withDefaultNamespace("music.overworld.sparse_jungle"),
            Identifier.withDefaultNamespace("music.overworld.stony_peaks"),
            Identifier.withDefaultNamespace("music.overworld.swamp")
    );

    private static final Random RANDOM = new Random();
    private static final ArrayDeque<Identifier> QUEUE = new ArrayDeque<>();
    private static final Set<String> DISABLED = new HashSet<>();
    private static List<MusicTrack> tracks = List.of();
    private static EventMusicSoundInstance current;
    private static Identifier currentTrackId;
    private static boolean shuffle;
    private static boolean configLoaded;

    private WhostmMusicController() {
    }

    public static List<MusicTrack> tracks(Minecraft minecraft) {
        ensureConfig(minecraft);
        refreshTracks(minecraft);
        return tracks;
    }

    public static boolean isEnabled(MusicTrack track) {
        return !DISABLED.contains(track.key());
    }

    public static void setEnabled(MusicTrack track, boolean enabled, Minecraft minecraft) {
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
        if (!isEnabled(track)) {
            setEnabled(track, true, minecraft);
        }
        stopCurrent(minecraft);
        play(track.id(), minecraft);
    }

    public static void enqueue(MusicTrack track) {
        if (isEnabled(track)) {
            QUEUE.addLast(track.id());
        }
    }

    public static void skip(Minecraft minecraft) {
        stopCurrent(minecraft);
        current = null;
        currentTrackId = null;
        playNextIfNeeded(minecraft);
    }

    public static void stop(Minecraft minecraft) {
        stopCurrent(minecraft);
        current = null;
        currentTrackId = null;
        shuffle = false;
    }

    public static void clearQueue() {
        QUEUE.clear();
    }

    public static void toggleShuffle(Minecraft minecraft) {
        shuffle = !shuffle;
        saveConfig(minecraft);
    }

    public static boolean shuffle() {
        return shuffle;
    }

    public static int queueSize() {
        return QUEUE.size();
    }

    public static Component status() {
        String currentName = currentTrackId == null ? "none" : prettyPath(currentTrackId.getPath());
        return Component.literal("Now playing: " + currentName + " | Queue: " + QUEUE.size() + " | Shuffle: " + (shuffle ? "on" : "off"));
    }

    public static boolean shouldSuppressVanillaMusic(Minecraft minecraft) {
        return isCustomActive(minecraft) || !QUEUE.isEmpty() || shuffle;
    }

    public static boolean isCustomActive(Minecraft minecraft) {
        return minecraft != null && current != null && minecraft.getSoundManager().isActive(current);
    }

    public static void tick(Minecraft minecraft) {
        ensureConfig(minecraft);
        refreshTracks(minecraft);
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

        List<Identifier> enabled = tracks.stream()
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

    private static void refreshTracks(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }

        List<Identifier> ids = minecraft.getSoundManager().getAvailableSounds().stream()
                .filter(id -> Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace()))
                .filter(id -> id.getPath().startsWith("music."))
                .sorted(Comparator.comparing(Identifier::toString))
                .toList();

        if (ids.isEmpty()) {
            ids = FALLBACK_TRACK_IDS;
        }

        List<MusicTrack> refreshed = new ArrayList<>(ids.size());
        for (Identifier id : ids) {
            refreshed.add(new MusicTrack(id, Component.literal(prettyPath(id.getPath()))));
        }
        tracks = List.copyOf(refreshed);
    }

    private static String prettyPath(String path) {
        String cleaned = path.replace("music.", "").replace('_', ' ').replace('.', ' ');
        StringBuilder out = new StringBuilder("Music: ");
        boolean upper = true;
        for (char c : cleaned.toCharArray()) {
            if (upper && Character.isLetter(c)) {
                out.append(Character.toUpperCase(c));
                upper = false;
            } else {
                out.append(c);
            }
            if (c == ' ') {
                upper = true;
            }
        }
        return out.toString();
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
