package xyz.pyrehaven.whostoppedthemusic.music.custom;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.dimension.DimensionType;
import xyz.pyrehaven.whostoppedthemusic.Settings;
import xyz.pyrehaven.whostoppedthemusic.music.Deck;
import xyz.pyrehaven.whostoppedthemusic.music.TrackLibrary;

/** Custom selection adapter inside MusicDirector, with vanilla still owning timing and channels. */
public final class CustomPlayback {
    private final Settings settings;
    private final TrackLibrary library;
    private final CustomDefaults defaults;
    private final BiomeCatalog catalog = new BiomeCatalog();
    private final Map<ScenarioId, CustomDefaults.Default> loadedDefaults = new TreeMap<>();
    private final LinkedHashMap<String, Deck> decks = new LinkedHashMap<>(32, 0.75f, true);
    private Resolution resolution;
    private Music music;
    private boolean edited;
    private ScenarioId currentBiome;

    public CustomPlayback(Settings settings, TrackLibrary library) {
        this.settings = settings;
        this.library = library;
        defaults = new CustomDefaults(library);
    }

    public BiomeCatalog catalog() { return catalog; }
    public Resolution resolution() { return resolution; }
    public Music music() { return music; }
    public boolean edited() { return edited; }
    public Deck deck() { return decks.get(resolution.key()); }

    public void reload() { loadedDefaults.clear(); currentBiome = null; }
    public void resetWinners() { resolution = null; }

    public CustomDefaults.Default defaults(ScenarioId id) {
        return loadedDefaults.computeIfAbsent(id, key -> {
            if (key.kind() == ScenarioId.Kind.SPECIAL) return defaults.special(Special.valueOf(key.id().toUpperCase(Locale.ROOT)));
            var entry = catalog.get(key);
            return defaults.biome(Identifier.parse(key.id()), entry.biome(), entry.dimension());
        });
    }

    public void tick(Situation situation, DimensionType dimension) {
        var configs = new TreeMap<ScenarioId, ScenarioConfig>();
        for (var special : Special.values()) {
            var id = ScenarioId.special(special);
            configs.put(id, settings.scenario(id, defaults(id).config()));
        }
        if (situation.biome() != null) {
            var id = situation.biome();
            // Current biome inherits the actual dimension, including a datapack's reused biome.
            var entry = catalog.get(id);
            if (!id.equals(currentBiome)) {
                loadedDefaults.put(id, defaults.biome(Identifier.parse(id.id()), entry.biome(), dimension));
                currentBiome = id;
            }
            configs.put(id, settings.scenario(id, defaults(id).config()));
        }
        String previousKey = resolution == null ? null : resolution.key();
        var previous = resolution == null ? List.<ScenarioId>of() : resolution.winners();
        var resolved = ContextResolver.resolve(situation, configs, previous);
        var available = library.tracks().stream().filter(t -> t.playable()).map(t -> t.id()).collect(Collectors.toSet());
        resolution = new Resolution(resolved.winners(), resolved.entries().stream().filter(e -> available.contains(e.id())).toList(),
                resolved.interrupt(), resolved.frequency(), resolved.jukebox());
        edited = resolution.winners().stream().anyMatch(id -> settings.songsEdited(id, defaults(id).config()));
        String key = resolution.key();
        // A held pause or volume fade can defer vanilla's pool-change check beyond this tick.
        boolean replace = resolution.interrupt() || key.equals(previousKey) && music.replaceCurrentMusic();
        var deck = decks.computeIfAbsent(key, ignored -> new Deck(new Random()));
        if (decks.size() > 32) decks.remove(decks.keySet().iterator().next());
        deck.configure(resolution.entries(), true);
        var source = defaults(resolution.winners().getFirst()).music().orElse(Musics.GAME);
        String path = resolution.winners().size() == 1 ? "custom/" + key
                : "custom/mix/" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
        music = new Music(Holder.direct(SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath("whostoppedthemusic", path))),
                source.minDelay(), source.maxDelay(), replace);
    }
}
