package xyz.pyrehaven.whostoppedthemusic.music.custom;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import xyz.pyrehaven.whostoppedthemusic.Settings;

/** Loaded registry rows only; Settings retains absent biomes across worlds and removed packs. */
public final class BiomeCatalog {
    public record Entry(ScenarioId id, String group, Biome biome, DimensionType dimension) {}
    private final Map<ScenarioId, Entry> entries = new TreeMap<>();

    public void rebuild(RegistryAccess registries, DimensionType currentDimension, Settings settings) {
        entries.clear();
        if (currentDimension == null) return;
        var biomes = registries.lookupOrThrow(Registries.BIOME);
        var dimensions = registries.lookupOrThrow(Registries.DIMENSION_TYPE);
        var known = new TreeMap<String, String>();
        for (var holder : biomes.listElements().toList()) {
            Identifier id = holder.key().identifier();
            String group = holder.is(BiomeTags.IS_OVERWORLD) ? "overworld"
                    : holder.is(BiomeTags.IS_NETHER) ? "nether" : holder.is(BiomeTags.IS_END) ? "end" : id.getNamespace();
            var dimension = switch (group) {
                case "overworld" -> dimensions.getValueOrThrow(BuiltinDimensionTypes.OVERWORLD);
                case "nether" -> dimensions.getValueOrThrow(BuiltinDimensionTypes.NETHER);
                case "end" -> dimensions.getValueOrThrow(BuiltinDimensionTypes.END);
                default -> currentDimension;
            };
            var scenario = ScenarioId.biome(id.toString());
            entries.put(scenario, new Entry(scenario, group, holder.value(), dimension));
            known.put(id.toString(), group);
        }
        settings.discoverBiomes(known);
    }

    public List<Entry> entries() { return List.copyOf(entries.values()); }
    public Entry get(ScenarioId id) { return entries.get(id); }
}
