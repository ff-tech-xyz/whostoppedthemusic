package xyz.pyrehaven.whostoppedthemusic.music.custom;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import xyz.pyrehaven.whostoppedthemusic.JukeboxSetting;
import xyz.pyrehaven.whostoppedthemusic.music.TrackLibrary;

/** Loaded defaults, recomputed after resource/registry changes rather than saved as overrides. */
public final class CustomDefaults {
    private static final Set<String> CAVES = Set.of("lush_caves", "dripstone_caves", "deep_dark", "sulfur_caves");
    private final TrackLibrary library;

    /** Keep the source's delay/replace metadata even when its pool is empty or volume is zero. */
    public record Default(ScenarioConfig config, Optional<Music> music) {}

    public CustomDefaults(TrackLibrary library) { this.library = library; }

    public Default biome(Identifier id, Biome biome, DimensionType dimension) {
        var attribute = EnvironmentAttributes.BACKGROUND_MUSIC;
        var background = dimension.attributes().applyModifier(attribute, attribute.defaultValue());
        var music = biome.getAttributes().applyModifier(attribute, background).defaultMusic();
        var volumeAttribute = EnvironmentAttributes.MUSIC_VOLUME;
        float volume = biome.getAttributes().applyModifier(volumeAttribute,
                dimension.attributes().applyModifier(volumeAttribute, volumeAttribute.defaultValue()));
        int priority = id.getNamespace().equals("minecraft") && CAVES.contains(id.getPath()) ? 5 : 2;
        return defaults(priority, true, music.map(Music::replaceCurrentMusic).orElse(false), false, music, volume <= 0);
    }

    public Default special(Special special) {
        Music music = switch (special) {
            case MENU -> Musics.MENU;
            case CREDITS -> Musics.CREDITS;
            case BOSS -> Musics.END_BOSS;
            case AQUATIC -> Musics.UNDER_WATER;
            case CREATIVE -> Musics.CREATIVE;
            default -> null;
        };
        int priority = switch (special) {
            case BOSS -> 10;
            case AQUATIC -> 8;
            case THUNDER -> 7;
            case RAIN, SNOW -> 6;
            case CREATIVE -> 4;
            case UNDERGROUND -> 3;
            case MENU, CREDITS -> 10;
        };
        boolean weather = special == Special.THUNDER || special == Special.RAIN || special == Special.SNOW;
        return defaults(priority, !weather && special != Special.UNDERGROUND,
                special == Special.MENU || special == Special.CREDITS || special == Special.BOSS,
                weather, Optional.ofNullable(music), false);
    }

    private Default defaults(int priority, boolean active, boolean interrupt, boolean outside,
                             Optional<Music> music, boolean silent) {
        var songs = silent ? List.<xyz.pyrehaven.whostoppedthemusic.music.Deck.Entry>of()
                : music.map(value -> library.poolEntries(value.sound().value().location())).orElse(List.of());
        return new Default(new ScenarioConfig(priority, active, interrupt, ScenarioConfig.Frequency.CONSTANT,
                JukeboxSetting.PAUSE_MUSIC, outside, 64, songs), music);
    }
}
