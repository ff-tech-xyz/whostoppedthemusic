package xyz.pyrehaven.whostoppedthemusic.music;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Musics;
import net.minecraft.util.valueproviders.MultipliedFloats;
import net.minecraft.world.attribute.BackgroundMusic;
import net.minecraft.world.attribute.EnvironmentAttributes;
import xyz.pyrehaven.whostoppedthemusic.mixin.SoundEventReferenceAccessor;
import xyz.pyrehaven.whostoppedthemusic.mixin.TimelineMusicAccessor;
import xyz.pyrehaven.whostoppedthemusic.mixin.WeighedSoundEventsAccessor;

public final class TrackLibrary {
    private List<Track> tracks = List.of();
    private int scanRevision;
    private SoundManager sounds;

    public List<Track> tracks() { return tracks; }

    /** File IO and decoder probes stay on vanilla's IO executor; publication belongs to the client. */
    public java.util.concurrent.CompletableFuture<Void> refreshSongs() {
        var client = Minecraft.getInstance();
        int revision = ++scanRevision;
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            var folder = new xyz.pyrehaven.whostoppedthemusic.custom.MusicFolder(client.gameDirectory.toPath());
            try {
                return folder.scan().stream().map(song -> {
                    var local = new LocalSong(song);
                    local.probe();
                    var location = Identifier.fromNamespaceAndPath("whostoppedthemusic", "local/"
                            + java.util.HexFormat.of().formatHex(song.file().getFileName().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    var sound = new Sound(location, net.minecraft.util.valueproviders.ConstantFloat.of(1),
                            net.minecraft.util.valueproviders.ConstantFloat.of(1), 1, Sound.Type.FILE, true, false, 16);
                    var title = Component.literal(song.title());
                    return new Track(song.id(), Track.Kind.LOCAL, title, sound,
                            new Track.Metadata(title, Component.empty()), local);
                }).toList();
            } catch (java.io.IOException error) {
                org.slf4j.LoggerFactory.getLogger("whostoppedthemusic").warn("Can't scan music folder", error);
                return List.<Track>of();
            }
        }, net.minecraft.util.Util.nonCriticalIoPool()).thenAcceptAsync(local -> {
            if (revision != scanRevision) return;
            var merged = new ArrayList<>(tracks.stream().filter(t -> t.local() == null).toList());
            merged.addAll(local);
            tracks = List.copyOf(merged);
            MusicDirector.get(client).libraryChanged();
        }, client::execute);
    }

    public void rebuild(SoundManager sounds) {
        var level = Minecraft.getInstance().level;
        rebuild(sounds, level == null ? RegistryAccess.EMPTY : level.registryAccess());
    }

    public void rebuild(SoundManager sounds, RegistryAccess registries) {
        this.sounds = sounds;
        var events = new TreeSet<Identifier>();
        var discs = new TreeMap<Identifier, Component>();
        for (var event : sounds.getAvailableSounds()) {
            String path = event.getPath();
            if (path.contains("music") || path.contains("theme")) events.add(event);
            if (path.startsWith("music_disc.")) {
                discs.put(event, null);
            }
        }
        for (var music : List.of(Musics.MENU, Musics.CREATIVE, Musics.CREDITS, Musics.END_BOSS,
                Musics.END, Musics.UNDER_WATER, Musics.GAME)) {
            events.add(music.sound().value().location());
        }
        addMusic(events, EnvironmentAttributes.BACKGROUND_MUSIC.defaultValue());
        registries.lookup(Registries.BIOME).ifPresent(biomes -> biomes.forEach(biome ->
                addMusic(events, biome.getAttributes().applyModifier(EnvironmentAttributes.BACKGROUND_MUSIC, BackgroundMusic.EMPTY))));
        registries.lookup(Registries.DIMENSION_TYPE).ifPresent(dimensions -> dimensions.forEach(dimension ->
                addMusic(events, dimension.attributes().applyModifier(EnvironmentAttributes.BACKGROUND_MUSIC, BackgroundMusic.EMPTY))));
        registries.lookup(Registries.TIMELINE).ifPresent(timelines -> timelines.forEach(timeline -> {
            var track = ((TimelineMusicAccessor) timeline).whostoppedthemusic$tracks().get(EnvironmentAttributes.BACKGROUND_MUSIC);
            if (track != null) addTimelineMusic(events, track);
        }));
        registries.lookup(Registries.JUKEBOX_SONG).ifPresent(songs -> songs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(key -> key.identifier().toString())))
                .forEach(entry -> discs.putIfAbsent(entry.getValue().soundEvent().value().location(), entry.getValue().description())));

        var unique = new LinkedHashMap<String, Track>();
        // Definition-backed discs must keep their kind even when a soundtrack pool aliases the file.
        events.addAll(discs.keySet());
        var ordered = events.stream().sorted(Comparator.comparing((Identifier event) -> !discs.containsKey(event))
                .thenComparing(Comparator.naturalOrder())).toList();
        for (var event : ordered) {
            boolean disc = discs.containsKey(event);
            for (var sound : flatten(sounds, event, new HashSet<>())) {
                String id = sound.getLocation().toString();
                var title = discs.get(event);
                if (title == null) title = Component.translatableWithFallback(
                        TrackNames.key(id, disc ? event.toString() : null), TrackNames.prettify(id));
                unique.putIfAbsent(id, new Track(id, disc ? Track.Kind.DISC : Track.Kind.SOUNDTRACK, title, sound));
            }
        }
        for (var track : tracks) if (track.local() != null) unique.put(track.id(), track);
        tracks = unique.values().stream().sorted(Comparator.comparing(Track::kind).thenComparing(Track::id)).toList();
    }

    private static void addMusic(Set<Identifier> events, BackgroundMusic background) {
        for (var music : List.of(background.defaultMusic(), background.creativeMusic(), background.underwaterMusic())) {
            music.ifPresent(value -> events.add(value.sound().value().location()));
        }
    }

    private static void addTimelineMusic(Set<Identifier> events, net.minecraft.world.timeline.AttributeTrack<?, ?> track) {
        for (var frame : track.argumentTrack().keyframes()) {
            // BACKGROUND_MUSIC's supported modifier is override; inspect its typed value, not a sampled time.
            if (frame.value() instanceof BackgroundMusic background) addMusic(events, background);
        }
    }

    /** Event wrappers weigh their referenced total, so each alias contributes its leaf weights once. */
    public List<Deck.Entry> poolEntries(Identifier event) {
        var weights = new TreeMap<String, Long>();
        if (sounds != null) poolWeights(event, new HashSet<>(), weights);
        long max = weights.values().stream().mapToLong(Long::longValue).max().orElse(0);
        long divisor = 1;
        if (max > 100) {
            divisor = 0;
            for (long weight : weights.values()) {
                long a = divisor, b = weight;
                while (b != 0) { long remainder = a % b; a = b; b = remainder; }
                divisor = a;
            }
        }
        final long common = divisor;
        final double scale = Math.max(1.0, (double) max / common / 100);
        return weights.entrySet().stream().map(entry -> new Deck.Entry(entry.getKey(),
                (int) Math.max(1, Math.round(entry.getValue() / (double) common / scale)))).toList();
    }

    private void poolWeights(Identifier event, Set<Identifier> visiting, Map<String, Long> weights) {
        var pool = sounds.getSoundEvent(event);
        if (pool == null || !visiting.add(event)) return;
        for (var entry : ((WeighedSoundEventsAccessor) pool).whostoppedthemusic$entries()) {
            if (entry instanceof Sound file && file.getWeight() > 0) {
                weights.merge(file.getLocation().toString(), (long) file.getWeight(), Math::addExact);
            } else if (entry instanceof SoundEventReferenceAccessor reference) {
                poolWeights(reference.whostoppedthemusic$reference().getLocation(), visiting, weights);
            }
        }
        visiting.remove(event);
    }

    private List<Sound> flatten(SoundManager sounds, Identifier event, Set<Identifier> visiting) {
        var pool = sounds.getSoundEvent(event);
        if (pool == null || !visiting.add(event)) return List.of();
        var result = new ArrayList<Sound>();
        for (var entry : ((WeighedSoundEventsAccessor) pool).whostoppedthemusic$entries()) {
            if (entry instanceof Sound file) {
                result.add(file);
            } else if (entry instanceof SoundEventReferenceAccessor accessor) {
                var reference = accessor.whostoppedthemusic$reference();
                for (var file : flatten(sounds, reference.getLocation(), visiting)) {
                    result.add(new Sound(file.getLocation(), new MultipliedFloats(file.getVolume(), reference.getVolume()),
                            new MultipliedFloats(file.getPitch(), reference.getPitch()), reference.getWeight(), Sound.Type.FILE,
                            file.shouldStream() || reference.shouldStream(), file.shouldPreload(), file.getAttenuationDistance()));
                }
            }
        }
        visiting.remove(event);
        return result;
    }
}
