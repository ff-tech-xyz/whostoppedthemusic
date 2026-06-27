package xyz.pyrehaven.whostoppedthemusic.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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
    private static final List<Identifier> SHUFFLE_ORDER = new ArrayList<>();
    private static final List<Identifier> SHUFFLE_HISTORY = new ArrayList<>();

    private static EventMusicSoundInstance current;
    private static SoundInstance observedMusic;
    private static Identifier currentTrackId;
    private static Identifier savedTrackId;
    private static boolean controlsEnabled;
    private static boolean shuffle;
    private static boolean hardSilenced;
    private static boolean pendingResume;
    private static boolean wasInWorld;
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
            stopMusicChannel(minecraft);
            current = null;
            currentTrackId = null;
            pendingResume = false;
            resetShuffleOrder();
        } else if (savedTrackId != null && !hardSilenced) {
            pendingResume = true;
            if (shuffle) {
                resetShuffleCycle(savedTrackId);
            }
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
            if (shuffle) {
                syncShuffleOrderAround(currentTrackId);
            }
        } else {
            DISABLED.add(track.key());
            if (track.id().equals(currentTrackId)) {
                Optional<Identifier> replacement = selectReplacementAfterDisabling(track);
                stopMusicChannel(minecraft);
                current = null;
                currentTrackId = null;
                if (replacement.isPresent()) {
                    hardSilenced = false;
                    pendingResume = false;
                    play(replacement.get(), minecraft);
                } else {
                    hardSilenced = true;
                    pendingResume = false;
                    savedTrackId = null;
                }
            } else if (shuffle) {
                syncShuffleOrderAround(currentTrackId);
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
            hardSilenced = false;
            pendingResume = savedTrackId != null;
            if (shuffle) {
                syncShuffleOrderAround(currentTrackId == null ? savedTrackId : currentTrackId);
            }
        } else {
            for (MusicTrack track : TRACKS) {
                DISABLED.add(track.key());
            }
            hardSilenced = true;
            pendingResume = false;
            savedTrackId = null;
            resetShuffleOrder();
            stopMusicChannel(minecraft);
            current = null;
            currentTrackId = null;
            saveConfig(minecraft);
            return;
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
        }
        hardSilenced = false;
        pendingResume = false;
        stopMusicChannel(minecraft);
        current = null;
        currentTrackId = null;
        if (shuffle) {
            resetShuffleCycle(track.id());
        }
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

    public static void playOrStop(Minecraft minecraft) {
        if (!controlsEnabled(minecraft)) {
            return;
        }
        if (isMusicPlaying(minecraft)) {
            stop(minecraft);
        } else {
            playRandom(minecraft);
        }
    }

    public static void playRandom(Minecraft minecraft) {
        if (!controlsEnabled(minecraft)) {
            return;
        }
        Optional<Identifier> next = shuffle ? selectShuffledTrack(true) : selectRandomEnabledTrack();
        hardSilenced = false;
        pendingResume = false;
        stopMusicChannel(minecraft);
        current = null;
        currentTrackId = null;
        next.ifPresent(id -> play(id, minecraft));
        saveConfig(minecraft);
    }

    public static void stop(Minecraft minecraft) {
        if (!controlsEnabled(minecraft)) {
            return;
        }
        hardSilenced = true;
        pendingResume = false;
        savedTrackId = null;
        stopMusicChannel(minecraft);
        current = null;
        currentTrackId = null;
        resetShuffleOrder();
        saveConfig(minecraft);
    }

    public static void toggleShuffle(Minecraft minecraft) {
        if (!controlsEnabled(minecraft)) {
            return;
        }
        shuffle = !shuffle;
        if (shuffle && hasEnabledTracks(minecraft)) {
            hardSilenced = false;
            resetShuffleCycle(currentTrackId == null ? savedTrackId : currentTrackId);
        } else {
            resetShuffleOrder();
        }
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

    public static boolean isMusicPlaying(Minecraft minecraft) {
        return isCustomActive(minecraft)
                || (minecraft != null && observedMusic != null && minecraft.getSoundManager().isActive(observedMusic));
    }

    public static void noticeMusicStarted(SoundInstance instance) {
        observedMusic = instance;
    }

    public static Component playStopMessage(Minecraft minecraft) {
        return Component.translatable(isMusicPlaying(minecraft)
                ? "button.whostoppedthemusic.stop"
                : "button.whostoppedthemusic.play");
    }

    public static double musicVolume(Minecraft minecraft) {
        if (minecraft == null) {
            return 1.0D;
        }
        return minecraft.options.getSoundSourceVolume(SoundSource.MUSIC);
    }

    public static void setMusicVolume(Minecraft minecraft, double volume) {
        if (minecraft == null) {
            return;
        }
        double clamped = Math.max(0.0D, Math.min(1.0D, volume));
        minecraft.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(clamped);
        minecraft.options.save();
    }

    public static Component currentSongMessage() {
        return WhostmMusicHud.nowPlayingMessage();
    }

    public static Component status(Minecraft minecraft) {
        if (!controlsEnabled(minecraft)) {
            return Component.literal("Controls: off | Vanilla chooses music normally | No random gaps");
        }
        if (hardSilenced) {
            return Component.literal("Controls: on | Stopped | " + enabledTrackCount() + "/" + TRACKS.size() + " enabled | Shuffle: " + (shuffle ? "on" : "off"));
        }
        String currentName = currentTrackId == null
                ? WhostmMusicHud.currentSong().getString()
                : MusicCatalog.title(currentTrackId).orElse(currentTrackId.toString());
        String enabled = enabledTrackCount() + "/" + TRACKS.size() + " enabled";
        return Component.literal("Controls: on | Now playing: " + currentName + " | " + enabled + " | Shuffle: " + (shuffle ? "on" : "off"));
    }

    public static boolean shouldSuppressVanillaMusic(Minecraft minecraft) {
        if (!controlsEnabled(minecraft)) {
            return false;
        }
        return hardSilenced
                || !hasEnabledTracks(minecraft)
                || pendingResume
                || isCustomActive(minecraft)
                || (shuffle && hasEnabledTracks(minecraft));
    }

    public static boolean isCustomActive(Minecraft minecraft) {
        return minecraft != null && current != null && minecraft.getSoundManager().isActive(current);
    }

    public static void tick(Minecraft minecraft) {
        ensureConfig(minecraft);
        WhostmMusicHud.tick();
        boolean inWorld = minecraft != null && minecraft.level != null;
        if (!controlsEnabled) {
            wasInWorld = inWorld;
            return;
        }
        if (current != null && minecraft != null && !minecraft.getSoundManager().isActive(current)) {
            if (!inWorld && savedTrackId != null && !hardSilenced) {
                pendingResume = true;
            } else if (inWorld && !shuffle) {
                savedTrackId = null;
                saveConfig(minecraft);
            }
            current = null;
            currentTrackId = null;
        }
        if (inWorld && !wasInWorld && savedTrackId != null && !hardSilenced) {
            pendingResume = true;
        }
        wasInWorld = inWorld;

        if (current == null && pendingResume && !hardSilenced) {
            if (savedTrackId != null && isEnabled(savedTrackId)) {
                play(savedTrackId, minecraft);
            }
            pendingResume = false;
        }

        if (!hardSilenced) {
            playNextIfNeeded(minecraft);
        }
    }

    private static void skipTo(Minecraft minecraft, boolean forward) {
        if (!controlsEnabled(minecraft)) {
            return;
        }
        Optional<Identifier> next = selectManualTrack(forward);
        hardSilenced = false;
        pendingResume = false;
        stopMusicChannel(minecraft);
        current = null;
        currentTrackId = null;
        next.ifPresent(id -> play(id, minecraft));
        saveConfig(minecraft);
    }

    private static void playNextIfNeeded(Minecraft minecraft) {
        if (minecraft == null || current != null || hardSilenced || !shuffle) {
            return;
        }

        Optional<Identifier> next = selectShuffledTrack(true);
        next.ifPresent(id -> play(id, minecraft));
    }

    private static Optional<Identifier> selectManualTrack(boolean forward) {
        List<MusicTrack> enabled = enabledTracks();
        if (enabled.isEmpty()) {
            return Optional.empty();
        }
        if (shuffle) {
            return selectShuffledTrack(forward);
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

    private static Optional<Identifier> selectShuffledTrack(boolean forward) {
        ensureShuffleOrder();
        if (forward) {
            if (SHUFFLE_ORDER.isEmpty()) {
                Identifier lastPlayed = SHUFFLE_HISTORY.isEmpty() ? null : SHUFFLE_HISTORY.getLast();
                reshuffleForNextCycle(lastPlayed);
            }
            if (SHUFFLE_ORDER.isEmpty()) {
                return Optional.empty();
            }
            Identifier next = SHUFFLE_ORDER.removeFirst();
            addShuffleHistory(next);
            return Optional.of(next);
        }

        if (SHUFFLE_HISTORY.size() <= 1) {
            return SHUFFLE_HISTORY.isEmpty() ? Optional.empty() : Optional.of(SHUFFLE_HISTORY.getLast());
        }
        Identifier currentSelection = SHUFFLE_HISTORY.removeLast();
        SHUFFLE_ORDER.remove(currentSelection);
        SHUFFLE_ORDER.add(0, currentSelection);
        return Optional.of(SHUFFLE_HISTORY.getLast());
    }

    private static Optional<Identifier> selectRandomEnabledTrack() {
        List<MusicTrack> enabled = enabledTracks();
        if (enabled.isEmpty()) {
            return Optional.empty();
        }
        if (enabled.size() == 1) {
            return Optional.of(enabled.getFirst().id());
        }
        return Optional.of(enabled.get(RANDOM.nextInt(enabled.size())).id());
    }

    private static Optional<Identifier> selectReplacementAfterDisabling(MusicTrack disabledTrack) {
        if (enabledTracks().isEmpty()) {
            return Optional.empty();
        }
        if (shuffle) {
            return selectShuffledTrack(true);
        }
        int disabledIndex = TRACKS.indexOf(disabledTrack);
        if (disabledIndex < 0) {
            return Optional.of(enabledTracks().getFirst().id());
        }
        for (int offset = 1; offset <= TRACKS.size(); offset++) {
            MusicTrack candidate = TRACKS.get((disabledIndex + offset) % TRACKS.size());
            if (isEnabled(candidate)) {
                return Optional.of(candidate.id());
            }
        }
        return Optional.empty();
    }

    private static List<MusicTrack> enabledTracks() {
        return TRACKS.stream()
                .filter(WhostmMusicController::isEnabled)
                .toList();
    }

    private static int enabledTrackCount() {
        return enabledTracks().size();
    }

    private static List<Identifier> enabledTrackIds() {
        return enabledTracks().stream()
                .map(MusicTrack::id)
                .toList();
    }

    private static void ensureShuffleOrder() {
        if (!shuffle) {
            resetShuffleOrder();
            return;
        }

        Set<Identifier> enabled = new HashSet<>(enabledTrackIds());
        if (enabled.isEmpty()) {
            resetShuffleOrder();
            return;
        }

        SHUFFLE_ORDER.removeIf(id -> !enabled.contains(id));
        SHUFFLE_HISTORY.removeIf(id -> !enabled.contains(id));

        if (currentTrackId != null && enabled.contains(currentTrackId) && !currentTrackId.equals(lastShuffleHistory())) {
            SHUFFLE_ORDER.remove(currentTrackId);
            addShuffleHistory(currentTrackId);
        }

        Set<Identifier> known = new HashSet<>(SHUFFLE_ORDER);
        known.addAll(SHUFFLE_HISTORY);
        List<Identifier> missing = new ArrayList<>();
        for (Identifier id : enabled) {
            if (!known.contains(id)) {
                missing.add(id);
            }
        }
        Collections.shuffle(missing, RANDOM);
        SHUFFLE_ORDER.addAll(missing);
    }

    private static void resetShuffleOrder() {
        SHUFFLE_ORDER.clear();
        SHUFFLE_HISTORY.clear();
    }

    private static void syncShuffleOrderAround(Identifier anchor) {
        if (!shuffle) {
            return;
        }
        ensureShuffleOrder();
        if (anchor != null && isEnabled(anchor) && !anchor.equals(lastShuffleHistory())) {
            SHUFFLE_ORDER.remove(anchor);
            addShuffleHistory(anchor);
        }
    }

    private static void resetShuffleCycle(Identifier anchor) {
        resetShuffleOrder();
        List<Identifier> enabled = new ArrayList<>(enabledTrackIds());
        if (enabled.isEmpty()) {
            return;
        }

        if (anchor != null && enabled.remove(anchor)) {
            addShuffleHistory(anchor);
        }
        Collections.shuffle(enabled, RANDOM);
        SHUFFLE_ORDER.addAll(enabled);
    }

    private static void reshuffleForNextCycle(Identifier lastPlayed) {
        SHUFFLE_ORDER.clear();
        List<Identifier> enabled = new ArrayList<>(enabledTrackIds());
        if (enabled.isEmpty()) {
            return;
        }
        Collections.shuffle(enabled, RANDOM);
        if (lastPlayed != null && enabled.size() > 1 && lastPlayed.equals(enabled.getFirst())) {
            Collections.rotate(enabled, -1);
        }
        SHUFFLE_ORDER.addAll(enabled);
    }

    private static Identifier lastShuffleHistory() {
        return SHUFFLE_HISTORY.isEmpty() ? null : SHUFFLE_HISTORY.getLast();
    }

    private static void addShuffleHistory(Identifier id) {
        if (id == null) {
            return;
        }
        if (id.equals(lastShuffleHistory())) {
            return;
        }
        SHUFFLE_HISTORY.add(id);
        int maxHistory = Math.max(32, TRACKS.size() * 4);
        while (SHUFFLE_HISTORY.size() > maxHistory) {
            SHUFFLE_HISTORY.removeFirst();
        }
    }

    private static boolean isEnabled(Identifier id) {
        return id != null && DISABLED.stream().noneMatch(disabled -> disabled.equals(id.toString()));
    }

    private static void play(Identifier id, Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        if (shuffle) {
            ensureShuffleOrder();
            SHUFFLE_ORDER.remove(id);
            addShuffleHistory(id);
        }
        EventMusicSoundInstance instance = new EventMusicSoundInstance(id);
        current = instance;
        currentTrackId = id;
        savedTrackId = id;
        hardSilenced = false;
        pendingResume = false;
        minecraft.getSoundManager().play(instance);
        saveConfig(minecraft);
    }

    private static void stopMusicChannel(Minecraft minecraft) {
        if (minecraft != null) {
            minecraft.getSoundManager().stop(null, SoundSource.MUSIC);
            minecraft.getSoundManager().stop(null, SoundSource.RECORDS);
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
        hardSilenced = Boolean.parseBoolean(props.getProperty("silenced", "false"));
        String currentId = props.getProperty("current", "").trim();
        savedTrackId = currentId.isEmpty() ? null : Identifier.tryParse(currentId);
        pendingResume = controlsEnabled && !hardSilenced && savedTrackId != null;
        DISABLED.clear();
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
        props.setProperty("silenced", Boolean.toString(hardSilenced));
        props.setProperty("current", savedTrackId == null ? "" : savedTrackId.toString());
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
