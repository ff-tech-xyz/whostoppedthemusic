package xyz.pyrehaven.whostoppedthemusic.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class MusicCatalog {
    private static final Map<String, String> TITLES = new HashMap<>();
    private static final List<MusicTrack> TRACKS = new ArrayList<>();

    static {
        // Official Minecraft 26.2 sounds.json concrete music resources, mapped to album/song titles.
        track("music/game/a_familiar_room", "A Familiar Room");
        track("music/game/an_ordinary_day", "An Ordinary Day");
        track("music/game/ancestry", "Ancestry");
        track("music/game/below_and_above", "Below and Above");
        track("music/game/broken_clocks", "Broken Clocks");
        track("music/game/bromeliad", "Bromeliad");
        track("music/game/clark", "Clark");
        track("music/game/comforting_memories", "Comforting Memories");
        track("music/game/creative/aria_math", "Aria Math");
        track("music/game/creative/biome_fest", "Biome Fest");
        track("music/game/creative/blind_spots", "Blind Spots");
        track("music/game/creative/dreiton", "Dreiton");
        track("music/game/creative/haunt_muskie", "Haunt Muskie");
        track("music/game/creative/taswell", "Taswell");
        track("music/game/crescent_dunes", "Crescent Dunes");
        track("music/game/danny", "Danny");
        track("music/game/deeper", "Deeper");
        track("music/game/dry_hands", "Dry Hands");
        track("music/game/ebb", "Ebb");
        track("music/game/echo_in_the_wind", "Echo in the Wind");
        track("music/game/eld_unknown", "Eld Unknown");
        track("music/game/end/alpha", "Alpha");
        track("music/game/end/boss", "Boss");
        track("music/game/end/the_end", "The End");
        track("music/game/endless", "Endless");
        track("music/game/featherfall", "Featherfall");
        track("music/game/fireflies", "Fireflies");
        track("music/game/floating_dream", "Floating Dream");
        track("music/game/haggstrom", "Haggstrom");
        track("music/game/home", "Home");
        track("music/game/infinite_amethyst", "Infinite Amethyst");
        track("music/game/key", "Key");
        track("music/game/komorebi", "komorebi");
        track("music/game/left_to_bloom", "Left to Bloom");
        track("music/game/lilypad", "Lilypad");
        track("music/game/living_mice", "Living Mice");
        track("music/game/memories", "Memories");
        track("music/game/mice_on_venus", "Mice on Venus");
        track("music/game/minecraft", "Minecraft");
        track("music/game/nether/ballad_of_the_cats", "Ballad of the Cats");
        track("music/game/nether/concrete_halls", "Concrete Halls");
        track("music/game/nether/crimson_forest/chrysopoeia", "Chrysopoeia");
        track("music/game/nether/dead_voxel", "Dead Voxel");
        track("music/game/nether/nether_wastes/rubedo", "Rubedo");
        track("music/game/nether/soulsand_valley/so_below", "So Below");
        track("music/game/nether/warmth", "Warmth");
        track("music/game/nightly", "Nightly");
        track("music/game/one_more_day", "One More Day");
        track("music/game/os_piano", "O's Piano");
        track("music/game/oxygene", "Oxygène");
        track("music/game/pokopoko", "pokopoko");
        track("music/game/puzzlebox", "Puzzlebox");
        track("music/game/shores", "Shores");
        track("music/game/stand_tall", "Stand Tall");
        track("music/game/subwoofer_lullaby", "Subwoofer Lullaby");
        track("music/game/swamp/aerie", "Aerie");
        track("music/game/swamp/firebugs", "Firebugs");
        track("music/game/swamp/labyrinthine", "Labyrinthine");
        track("music/game/sweden", "Sweden");
        track("music/game/watcher", "Watcher");
        track("music/game/water/axolotl", "Axolotl");
        track("music/game/water/dragon_fish", "Dragon Fish");
        track("music/game/water/shuniji", "Shuniji");
        track("music/game/wending", "Wending");
        track("music/game/wet_hands", "Wet Hands");
        track("music/game/yakusoku", "yakusoku");
        track("music/menu/beginning_2", "Beginning 2");
        track("music/menu/floating_trees", "Floating Trees");
        track("music/menu/moog_city_2", "Moog City 2");
        track("music/menu/mutation", "Mutation");

        disc("11", "11");
        disc("13", "13");
        disc("5", "5");
        disc("blocks", "blocks");
        disc("bounce", "Bounce");
        disc("cat", "cat");
        disc("chirp", "chirp");
        disc("creator", "Creator");
        disc("creator_music_box", "Creator (Music Box)");
        disc("far", "far");
        disc("lava_chicken", "Lava Chicken");
        disc("mall", "mall");
        disc("mellohi", "mellohi");
        disc("otherside", "otherside");
        disc("pigstep", "Pigstep");
        disc("precipice", "Precipice");
        disc("relic", "Relic");
        disc("stal", "stal");
        disc("strad", "strad");
        disc("tears", "Tears");
        disc("wait", "wait");
        disc("ward", "ward");

        // Event/pool labels used when a vanilla SoundInstance has not exposed the resolved file yet.
        alias("music.creative", "Creative Music");
        alias("music.credits", "Alpha");
        alias("music.dragon", "Boss");
        alias("music.end", "The End");
        alias("music.game", "Survival Music");
        alias("music.menu", "Menu Music");
        alias("music.under_water", "Underwater Music");
        alias("music.nether.basalt_deltas", "Basalt Deltas Music");
        alias("music.nether.crimson_forest", "Crimson Forest Music");
        alias("music.nether.nether_wastes", "Nether Wastes Music");
        alias("music.nether.soul_sand_valley", "Soul Sand Valley Music");
        alias("music.nether.warped_forest", "Warped Forest Music");
        alias("music.overworld.badlands", "Badlands Music");
        alias("music.overworld.bamboo_jungle", "Bamboo Jungle Music");
        alias("music.overworld.cherry_grove", "Cherry Grove Music");
        alias("music.overworld.deep_dark", "Deep Dark Music");
        alias("music.overworld.desert", "Desert Music");
        alias("music.overworld.dripstone_caves", "Dripstone Caves Music");
        alias("music.overworld.flower_forest", "Flower Forest Music");
        alias("music.overworld.forest", "Forest Music");
        alias("music.overworld.frozen_peaks", "Frozen Peaks Music");
        alias("music.overworld.grove", "Grove Music");
        alias("music.overworld.jagged_peaks", "Jagged Peaks Music");
        alias("music.overworld.jungle", "Jungle Music");
        alias("music.overworld.lush_caves", "Lush Caves Music");
        alias("music.overworld.meadow", "Meadow Music");
        alias("music.overworld.old_growth_taiga", "Old Growth Taiga Music");
        alias("music.overworld.snowy_slopes", "Snowy Slopes Music");
        alias("music.overworld.sparse_jungle", "Sparse Jungle Music");
        alias("music.overworld.stony_peaks", "Stony Peaks Music");
        alias("music.overworld.sulfur_caves", "Sulfur Caves Music");
        alias("music.overworld.swamp", "Swamp Music");
    }

    private MusicCatalog() {
    }

    public static List<MusicTrack> tracks() {
        return List.copyOf(TRACKS);
    }

    public static Component displayName(Identifier eventId, Identifier resolvedSoundId) {
        Optional<String> title = title(resolvedSoundId).or(() -> title(eventId));
        return Component.literal(title.orElseGet(() -> prettyName(resolvedSoundId != null ? resolvedSoundId : eventId)));
    }

    public static Optional<String> title(Identifier id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(TITLES.get(normalize(id)));
    }

    private static void track(String path, String title) {
        Identifier id = Identifier.withDefaultNamespace(path);
        alias(path, title);
        TRACKS.add(new MusicTrack(id, Component.literal(title)));
    }

    private static void disc(String discId, String title) {
        track("records/" + discId, title);
        alias("music_disc." + discId, title);
    }

    private static void alias(String path, String title) {
        String normalized = normalize(path);
        TITLES.put(normalized, title);
        TITLES.put("minecraft:" + normalized, title);
    }

    private static String normalize(Identifier id) {
        return normalize(id.toString());
    }

    private static String normalize(String raw) {
        String value = raw.toLowerCase(Locale.ROOT);
        if (value.startsWith("minecraft:")) {
            value = value.substring("minecraft:".length());
        }
        if (value.startsWith("sounds/")) {
            value = value.substring("sounds/".length());
        }
        if (value.endsWith(".ogg")) {
            value = value.substring(0, value.length() - ".ogg".length());
        }
        return value;
    }

    private static String prettyName(Identifier id) {
        if (id == null) {
            return "Unknown song";
        }
        String path = id.getPath().replace("music/", "").replace("records/", "").replace("music.", "");
        String cleaned = path.replace('_', ' ').replace('/', ' ').replace('.', ' ');
        StringBuilder out = new StringBuilder();
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
}
