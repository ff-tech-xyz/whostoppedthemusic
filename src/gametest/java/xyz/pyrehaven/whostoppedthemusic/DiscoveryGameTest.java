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
                installDefinitions(client);
            });
            var reload = context.computeOnClient(Minecraft::reloadResourcePacks);
            context.waitFor(client -> reload.isDone());
            reload.join();
            context.runOnClient(client -> {
                var sounds = client.getSoundManager();
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
