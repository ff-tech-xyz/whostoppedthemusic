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

/** Joined registry in-world; bootstrapped vanilla definitions at the title menu. */
public final class BiomeCatalog {
    public record Entry(ScenarioId id, String group, Biome biome, DimensionType dimension) {}
    private Map<ScenarioId, Entry> entries;

    public void rebuild(RegistryAccess registries, DimensionType currentDimension, Settings settings) {
        entries = null;
        if (currentDimension == null) return;
        entries = new TreeMap<>();
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

    public List<Entry> entries() { return List.copyOf(currentEntries().values()); }
    public Entry get(ScenarioId id) { return currentEntries().get(id); }

    private Map<ScenarioId, Entry> currentEntries() {
        return entries == null ? Menu.ENTRIES : entries;
    }

    private static final class Menu {
        private static final Map<ScenarioId, Entry> ENTRIES = create();

        private static Map<ScenarioId, Entry> create() {
            var registries = net.minecraft.data.registries.VanillaRegistries.createWorldLookup();
            var dimensions = net.minecraft.world.level.levelgen.presets.WorldPresets.createNormalWorldDimensions(registries);
            var overworld = dimensions.get(net.minecraft.world.level.dimension.LevelStem.OVERWORLD).orElseThrow().type().value();
            var result = new TreeMap<ScenarioId, Entry>();
            registries.lookupOrThrow(Registries.BIOME).listElements().forEach(holder -> {
                var id = ScenarioId.biome(holder.key().identifier().toString());
                result.put(id, new Entry(id, holder.key().identifier().getNamespace(), holder.value(), overworld));
            });
            // Bootstrap lookups have no bound tags. Use vanilla's actual dimension biome sources,
            // not a hardcoded biome list or previously saved IDs from an absent pack.
            dimensions.dimensions().forEach((key, stem) -> stem.generator().getBiomeSource().possibleBiomes().forEach(holder -> {
                var id = ScenarioId.biome(holder.unwrapKey().orElseThrow().identifier().toString());
                String group = key.equals(net.minecraft.world.level.dimension.LevelStem.END) ? "end"
                        : key.equals(net.minecraft.world.level.dimension.LevelStem.NETHER) ? "nether" : "overworld";
                result.put(id, new Entry(id, group, holder.value(), stem.type().value()));
            }));
            return java.util.Collections.unmodifiableMap(result);
        }
    }
}
