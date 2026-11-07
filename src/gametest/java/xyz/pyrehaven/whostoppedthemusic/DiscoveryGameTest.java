package xyz.pyrehaven.whostoppedthemusic;

import com.mojang.serialization.Lifecycle;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.BackgroundMusic;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.timeline.Timeline;
import org.slf4j.LoggerFactory;
import xyz.pyrehaven.whostoppedthemusic.music.Track;
import xyz.pyrehaven.whostoppedthemusic.music.TrackLibrary;

final class DiscoveryGameTest {
    static void checkLiveNames(Minecraft client) {
        var library = new TrackLibrary();
        library.rebuild(client.getSoundManager(), RegistryAccess.EMPTY);
        var song = library.tracks().stream().filter(t -> t.id().equals("minecraft:music/game/sweden")).findFirst().orElseThrow();
        var disc = library.tracks().stream().filter(t -> t.id().equals("minecraft:records/pigstep")).findFirst().orElseThrow();
        check(song.title().getString().equals("C418 - Sweden"), "production library translates soundtrack");
        check(disc.title().getString().equals("Lena Raine - Pigstep"), "production library translates legacy disc");
        var fallback = library.tracks().stream().filter(t -> t.title().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents c
                && !net.minecraft.locale.Language.getInstance().has(c.getKey())).findFirst().orElseThrow();
        check(fallback.title().getString().equals(xyz.pyrehaven.whostoppedthemusic.music.TrackNames.prettify(fallback.id())), "production library missing translation fallback");
        var language = net.minecraft.locale.Language.getInstance();
        try {
            net.minecraft.locale.Language.inject(new net.minecraft.locale.Language() {
                @Override public String getOrDefault(String key, String fallback) {
                    return key.equals("music.game.sweden") ? "Test Artist - Changed Song" : language.getOrDefault(key, fallback);
                }
                @Override public boolean has(String key) { return language.has(key); }
                @Override public boolean isDefaultRightToLeft() { return language.isDefaultRightToLeft(); }
                @Override public net.minecraft.util.FormattedCharSequence getVisualOrder(net.minecraft.network.chat.FormattedText text) {
                    return language.getVisualOrder(text);
                }
            });
            check(song.title().getString().equals("Test Artist - Changed Song") && song.metadata().title().getString().equals("Changed Song")
                    && song.metadata().artist().getString().equals("Test Artist"), "retained library track resolves new language without rebuild");
        } finally { net.minecraft.locale.Language.inject(language); }
        check(song.title().getString().equals("C418 - Sweden"), "retained component resolves restored language");
        LoggerFactory.getLogger("whostoppedthemusic-test").info("LIVE NAMING PASS: production soundtrack/disc/fallback and retained component language switch");
    }

    static void run(ClientGameTestContext context) {
        var beforeJoin = context.computeOnClient(client -> {
            checkNames(client.getSoundManager(), WhoStoppedTheMusic.LIBRARY);
            return WhoStoppedTheMusic.LIBRARY.tracks();
        });
        try (var world = context.worldBuilder().create()) {
            context.waitForScreen(null);
            context.runOnClient(client -> {
                check(WhoStoppedTheMusic.LIBRARY.tracks() != beforeJoin, "world entry rebuilds the library");
                checkDefinitions(client);
                checkDefaults(client);
                checkPoolChanges(client);
                installDefinitions(client);
            });
            var reload = context.computeOnClient(Minecraft::reloadResourcePacks);
            context.waitFor(client -> reload.isDone());
            reload.join();
            context.runOnClient(client -> {
                var sounds = client.getSoundManager();
                checkDefaults(client);
                checkNames(sounds, WhoStoppedTheMusic.LIBRARY);
                check(!has(WhoStoppedTheMusic.LIBRARY, sounds, "entity.sheep.ambient"),
                        "resource reload uses current world registries, dropping stale test definitions");
                installDefinitions(client);
            });
        }
        context.runOnClient(client -> {
            checkNames(client.getSoundManager(), WhoStoppedTheMusic.LIBRARY);
            check(!has(WhoStoppedTheMusic.LIBRARY, client.getSoundManager(), "entity.sheep.ambient"),
                    "world exit removes old registry-only music");
        });
        LoggerFactory.getLogger("whostoppedthemusic-test").info("MUSIC DISCOVERY PASS: path matching, namespace rejection, biome variants, dimension, timeline, jukebox, dedupe, nested modifiers, resource reload and world entry/exit");
    }

    private static void checkDefaults(Minecraft client) {
        var library = WhoStoppedTheMusic.LIBRARY;
        var sounds = client.getSoundManager();
        var defaults = new xyz.pyrehaven.whostoppedthemusic.music.custom.CustomDefaults(library);
        var game = library.poolEntries(Identifier.parse("minecraft:music.game"));
        var creative = defaults.special(xyz.pyrehaven.whostoppedthemusic.music.custom.Special.CREATIVE).config().songs();
        check(game.size() == 32 && game.stream().mapToInt(e -> e.weight()).sum() == 37, "loaded 26.3 game is 32 files / 37 weight");
        check(creative.size() == 38 && creative.stream().mapToInt(e -> e.weight()).sum() == 43, "creative adds six unit files, not six weight-32 files");
        check(creative.containsAll(game), "creative preserves game file weights 1/2");
        check(library.poolEntries(Identifier.parse("discovery:odds_reference")).equals(game), "loaded alias ignores declared weight 97, including after reload");
        checkOdds(sounds, Identifier.parse("minecraft:music.creative"), creative);
        var deck = new xyz.pyrehaven.whostoppedthemusic.music.Deck(new java.util.Random(12));
        deck.configure(creative, true);
        var drawn = new java.util.TreeMap<String, Integer>();
        for (int i = 0; i < 43; i++) drawn.merge(deck.next(), 1, Integer::sum);
        for (var entry : creative) check(drawn.get(entry.id()) == entry.weight(), "Deck keeps creative odds: " + entry.id());

        var registries = client.level.registryAccess();
        var dimensions = registries.lookupOrThrow(Registries.DIMENSION_TYPE);
        int checked = 0;
        for (var biome : registries.lookupOrThrow(Registries.BIOME).listElements().toList()) {
            var id = biome.key().identifier();
            if (!id.getNamespace().equals("minecraft")) continue;
            var dimensionKey = biome.is(net.minecraft.tags.BiomeTags.IS_NETHER)
                    ? net.minecraft.world.level.dimension.BuiltinDimensionTypes.NETHER
                    : biome.is(net.minecraft.tags.BiomeTags.IS_END)
                    ? net.minecraft.world.level.dimension.BuiltinDimensionTypes.END
                    : net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD;
            var dimension = dimensions.getOrThrow(dimensionKey).value();
            var result = defaults.biome(id, biome.value(), dimension);
            var attribute = EnvironmentAttributes.BACKGROUND_MUSIC;
            var expected = biome.value().getAttributes().applyModifier(attribute,
                    dimension.attributes().applyModifier(attribute, attribute.defaultValue())).defaultMusic();
            check(result.music().equals(expected), "biome retains source delay/replace metadata: " + id);
            check(result.config().interrupt() == expected.map(net.minecraft.sounds.Music::replaceCurrentMusic).orElse(false), "biome interrupt: " + id);
            if (id.getPath().equals("pale_garden") || id.getPath().equals("warped_forest")) {
                check(result.config().songs().isEmpty(), "vanilla silence: " + id);
            } else if (expected.isPresent()) {
                checkOdds(sounds, expected.get().sound().value().location(), result.config().songs());
            } else check(result.config().songs().isEmpty(), "absent music is silence: " + id);
            check(result.config().frequency() == xyz.pyrehaven.whostoppedthemusic.music.custom.ScenarioConfig.Frequency.CONSTANT
                    && result.config().jukebox() == JukeboxSetting.PAUSE_MUSIC, "scenario setting defaults: " + id);
            checked++;
        }
        check(checked > 60, "all loaded vanilla biomes checked");
        for (var special : xyz.pyrehaven.whostoppedthemusic.music.custom.Special.values()) {
            var result = defaults.special(special);
            if (result.music().isPresent()) checkOdds(sounds, result.music().get().sound().value().location(), result.config().songs());
            else check(!result.config().active() && result.config().songs().isEmpty(), "empty optional specials start off");
        }
        for (String event : List.of("odds_nested", "odds_duplicate", "odds_reference")) {
            var id = Identifier.parse("discovery:" + event);
            checkOdds(sounds, id, library.poolEntries(id));
        }
        LoggerFactory.getLogger("whostoppedthemusic-test").info("CUSTOM DEFAULTS PASS: {} loaded vanilla biomes, pale garden/warped silence, special metadata, creative 38 files/43 Deck draws, nested/duplicate aliases", checked);
    }

    private static void checkOdds(SoundManager sounds, Identifier event, List<xyz.pyrehaven.whostoppedthemusic.music.Deck.Entry> entries) {
        var expected = new java.util.TreeMap<String, Double>();
        runtimeOdds(sounds, event, 1, expected);
        int total = entries.stream().mapToInt(e -> e.weight()).sum();
        check(entries.size() == expected.size(), "same file set: " + event);
        for (var entry : entries) check(Math.abs(entry.weight() / (double) total - expected.getOrDefault(entry.id(), -1.0)) < 1e-10,
                "runtime probability: " + event + " / " + entry.id());
    }

    private static void runtimeOdds(SoundManager sounds, Identifier event, double chance, java.util.Map<String, Double> result) {
        var pool = sounds.getSoundEvent(event);
        for (var entry : ((xyz.pyrehaven.whostoppedthemusic.mixin.WeighedSoundEventsAccessor) pool).whostoppedthemusic$entries()) {
            double probability = chance * entry.getWeight() / pool.getWeight();
            if (entry instanceof net.minecraft.client.resources.sounds.Sound file) result.merge(file.getLocation().toString(), probability, Double::sum);
            else if (entry instanceof xyz.pyrehaven.whostoppedthemusic.mixin.SoundEventReferenceAccessor reference)
                runtimeOdds(sounds, reference.whostoppedthemusic$reference().getLocation(), probability, result);
        }
    }

    private record Cycle(net.minecraft.client.resources.sounds.Sound whostoppedthemusic$reference)
            implements xyz.pyrehaven.whostoppedthemusic.mixin.SoundEventReferenceAccessor,
            net.minecraft.client.sounds.Weighted<net.minecraft.client.resources.sounds.Sound> {
        public int getWeight() { throw new AssertionError("cycle must not recurse through runtime getWeight"); }
        public net.minecraft.client.resources.sounds.Sound getSound(RandomSource random) { throw new AssertionError(); }
        public void preloadIfRequired(net.minecraft.client.sounds.SoundEngine engine) { throw new AssertionError(); }
    }

    private static void checkPoolChanges(Minecraft client) {
        var library = WhoStoppedTheMusic.LIBRARY;
        var id = Identifier.parse("discovery:odds_reference");
        var pool = client.getSoundManager().getSoundEvent(id);
        var entries = ((xyz.pyrehaven.whostoppedthemusic.mixin.WeighedSoundEventsAccessor) pool).whostoppedthemusic$entries();
        var before = List.copyOf(entries);
        var base = library.poolEntries(id);
        var file = pool.getSound(RandomSource.create(0));
        try {
            var cycle = new net.minecraft.client.resources.sounds.Sound(id, file.getVolume(), file.getPitch(), 1,
                    net.minecraft.client.resources.sounds.Sound.Type.SOUND_EVENT, false, false, 16);
            pool.addSound(new Cycle(cycle));
            check(library.poolEntries(id).equals(base), "cyclic branch stops without losing valid siblings");
            entries.clear();
            pool.addSound(new net.minecraft.client.resources.sounds.Sound(file.getLocation(), file.getVolume(), file.getPitch(), 600,
                    net.minecraft.client.resources.sounds.Sound.Type.FILE, false, false, 16));
            check(library.poolEntries(id).getFirst().weight() == 1, "exact gcd reduction when max exceeds 100");
            var other = Identifier.parse(file.getLocation().toString().equals("minecraft:music/game/sweden")
                    ? "minecraft:music/game/clark" : "minecraft:music/game/sweden");
            pool.addSound(new net.minecraft.client.resources.sounds.Sound(other, file.getVolume(), file.getPitch(), 7,
                    net.minecraft.client.resources.sounds.Sound.Type.FILE, false, false, 16));
            var scaled = library.poolEntries(id);
            check(scaled.size() == 2 && scaled.stream().mapToInt(e -> e.weight()).max().orElseThrow() == 100
                    && scaled.stream().mapToInt(e -> e.weight()).min().orElseThrow() == 1, "oversized coprime pool scales/rounds within scenario bound");
            entries.clear();
            check(library.poolEntries(id).isEmpty(), "loaded pack replacement with empty pool is immediate");
            check(library.poolEntries(Identifier.parse("discovery:missing")).isEmpty(), "missing pool is silent");
        } finally {
            entries.clear();
            entries.addAll(before);
        }
        check(library.poolEntries(id).equals(base), "restored pool has no stale default cache");
        pool.addSound(file);
        check(!library.poolEntries(id).equals(base), "live loaded pool change updates defaults immediately; next resource reload must restore it");
    }

    private static void checkNames(SoundManager sounds, TrackLibrary library) {
        check(has(library, sounds, "entity.chicken.ambient"), "forestmusic matches anywhere in path");
        var cow = track(library, sounds, "entity.cow.ambient");
        check(cow != null, "forest_theme and nested references are discovered");
        check(cow.sound().shouldStream(), "nested reference retains streaming");
        var original = sounds.getSoundEvent(Identifier.parse("minecraft:entity.cow.ambient"))
                .getSound(RandomSource.create(0));
        check(Math.abs(cow.sound().getVolume().sample(RandomSource.create(0))
                - original.getVolume().sample(RandomSource.create(0)) * 0.25f) < 0.0001f, "nested volume multipliers survive");
        check(Math.abs(cow.sound().getPitch().sample(RandomSource.create(0))
                - original.getPitch().sample(RandomSource.create(0)) * 0.25f) < 0.0001f, "nested pitch multipliers survive");
        check(!has(library, sounds, "entity.bat.ambient"), "music in namespace alone does not match");
        check(track(library, sounds, "block.note_block.bell").kind() == Track.Kind.DISC, "legacy disc classification survives");
        check(library.tracks().stream().map(Track::id).distinct().count() == library.tracks().size(), "one row per file id");
    }

    private static void checkDefinitions(Minecraft client) {
        var sounds = client.getSoundManager();
        var library = new TrackLibrary();
        library.rebuild(sounds, RegistryAccess.EMPTY);
        check(!has(library, sounds, "entity.sheep.ambient"), "arbitrary path needs a definition");
        library.rebuild(sounds, definitions(client.level.dimensionType()));
        for (var event : List.of("entity.sheep.ambient", "entity.pig.ambient", "entity.cat.ambient",
                "entity.horse.ambient", "entity.villager.ambient")) {
            var track = track(library, sounds, event);
            check(track != null && track.kind() == Track.Kind.SOUNDTRACK, "definition discovers " + event);
        }
        var disc = track(library, sounds, "block.note_block.harp");
        check(disc.kind() == Track.Kind.DISC, "jukebox definition wins over earlier soundtrack alias");
        check(disc.title().getString().equals("Definition Disc"), "arbitrary disc uses jukebox description");
        check(library.tracks().stream().filter(t -> t.id().equals(disc.id())).count() == 1, "disc aliases dedupe");
        var first = library.tracks().stream().map(Track::id).toList();
        library.rebuild(sounds, definitions(client.level.dimensionType()));
        check(first.equals(library.tracks().stream().map(Track::id).toList()), "rebuild ordering is stable");
        library.rebuild(sounds, RegistryAccess.EMPTY);
        check(!has(library, sounds, "entity.sheep.ambient"), "replaced registries remove stale definitions");
        check(track(library, sounds, "block.note_block.harp").kind() == Track.Kind.SOUNDTRACK,
                "disc classification does not leak after definition removal");
    }

    private static void installDefinitions(Minecraft client) {
        WhoStoppedTheMusic.LIBRARY.rebuild(client.getSoundManager(), definitions(client.level.dimensionType()));
        check(has(WhoStoppedTheMusic.LIBRARY, client.getSoundManager(), "entity.sheep.ambient"), "test definition installed");
    }

    private static RegistryAccess definitions(DimensionType base) {
        var biomes = new MappedRegistry<Biome>(Registries.BIOME, Lifecycle.stable());
        var background = new BackgroundMusic(Optional.of(net.minecraft.sounds.Musics.createGameMusic(sound("unnamed_biome"))),
                Optional.of(net.minecraft.sounds.Musics.createGameMusic(sound("unnamed_creative"))),
                Optional.of(net.minecraft.sounds.Musics.createGameMusic(sound("unnamed_underwater"))));
        Registry.register(biomes, Identifier.parse("discovery:forest"), new Biome.BiomeBuilder()
                .temperature(0.5f).downfall(0.5f).specialEffects(new BiomeSpecialEffects.Builder().waterColor(0).build())
                .generationSettings(BiomeGenerationSettings.EMPTY).setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, background).build());
        var dimensions = new MappedRegistry<DimensionType>(Registries.DIMENSION_TYPE, Lifecycle.stable());
        Registry.register(dimensions, Identifier.parse("discovery:dimension"), new DimensionType(base.hasFixedTime(),
                base.hasSkyLight(), base.hasCeiling(), base.hasEnderDragonFight(), base.coordinateScale(), base.minY(),
                base.height(), base.logicalHeight(), base.infiniburn(), base.ambientLight(), base.monsterSettings(),
                base.skybox(), base.cardinalLightType(), EnvironmentAttributeMap.builder()
                        .set(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(sound("unnamed_dimension"))).build(),
                base.timelines(), base.defaultClock()));
        var timelines = new MappedRegistry<Timeline>(Registries.TIMELINE, Lifecycle.stable());
        Registry.register(timelines, Identifier.parse("discovery:timeline"), Timeline.builder(Holder.direct(new WorldClock()))
                .addTrack(EnvironmentAttributes.BACKGROUND_MUSIC, frames -> frames
                        .addKeyframe(0, BackgroundMusic.EMPTY).addKeyframe(100, new BackgroundMusic(sound("unnamed_timeline"))))
                .build());
        var songs = new MappedRegistry<JukeboxSong>(Registries.JUKEBOX_SONG, Lifecycle.stable());
        Registry.register(songs, Identifier.parse("discovery:disc"),
                new JukeboxSong(sound("unnamed_disc"), Component.literal("Definition Disc"), 100, 1));
        Registry.register(songs, Identifier.parse("discovery:missing"),
                new JukeboxSong(sound("missing_event"), Component.literal("Missing"), 100, 1));
        return new RegistryAccess.ImmutableRegistryAccess(List.of(biomes.freeze(), dimensions.freeze(), timelines.freeze(), songs.freeze()));
    }

    private static Holder<SoundEvent> sound(String path) {
        return Holder.direct(SoundEvent.createVariableRangeEvent(Identifier.parse("discovery:" + path)));
    }

    private static Track track(TrackLibrary library, SoundManager sounds, String vanillaEvent) {
        var pool = sounds.getSoundEvent(Identifier.parse("minecraft:" + vanillaEvent));
        check(pool != null && pool.getWeight() > 0, "vanilla fixture exists: " + vanillaEvent);
        var file = pool.getSound(RandomSource.create(0)).getLocation().toString();
        return library.tracks().stream().filter(t -> t.id().equals(file)).findFirst().orElse(null);
    }

    private static boolean has(TrackLibrary library, SoundManager sounds, String event) {
        return track(library, sounds, event) != null;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
