package xyz.pyrehaven.whostoppedthemusic;

import static xyz.pyrehaven.whostoppedthemusic.WhoStoppedTheMusic.LOGGER;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import xyz.pyrehaven.whostoppedthemusic.music.Deck;
import xyz.pyrehaven.whostoppedthemusic.music.custom.ScenarioConfig;
import xyz.pyrehaven.whostoppedthemusic.music.custom.ScenarioId;

public final class Settings {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private boolean firstRunDone;
    private JukeboxSetting jukebox = JukeboxSetting.PAUSE_MUSIC;
    private Mode mode = Mode.VANILLA;
    private boolean shuffle = true;
    private final Set<String> unticked = new HashSet<>();
    private ScenarioConfig.Frequency customFrequency = ScenarioConfig.Frequency.CONSTANT;
    private JukeboxSetting customJukebox = JukeboxSetting.PAUSE_MUSIC;
    private final Map<String, String> knownBiomes = new TreeMap<>();
    private final Map<ScenarioId, JsonObject> scenarios = new TreeMap<>();

    private Settings(Path file) {
        this.file = file;
    }

    public static Settings load(Path file) {
        Settings settings = new Settings(file);
        if (!Files.exists(file)) {
            return settings;
        }
        try {
            JsonObject data = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (data.has("firstRunDone")) {
                settings.firstRunDone = data.get("firstRunDone").getAsBoolean();
            }
            if (data.has("jukebox")) {
                settings.jukebox = JukeboxSetting.valueOf(data.get("jukebox").getAsString());
            }
            if (data.has("mode")) {
                settings.mode = Mode.valueOf(data.get("mode").getAsString());
            }
            if (data.has("basic")) {
                var basic = data.get("basic").getAsJsonObject();
                if (basic.has("shuffle")) settings.shuffle = basic.get("shuffle").getAsBoolean();
                if (basic.has("unticked")) {
                    for (var id : basic.get("unticked").getAsJsonArray()) settings.unticked.add(id.getAsString());
                }
            }
            if (data.has("custom")) settings.readCustom(data.getAsJsonObject("custom"));
            return settings;
        } catch (JsonParseException | IllegalStateException | IllegalArgumentException | UnsupportedOperationException | NullPointerException | ClassCastException e) {
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            return new Settings(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Mode mode() {
        return mode;
    }

    public boolean shuffle() { return shuffle; }

    public Map<String, String> knownBiomes() { return Map.copyOf(knownBiomes); }

    public void discoverBiomes(Map<String, String> biomes) {
        boolean changed = biomes.entrySet().stream().anyMatch(e -> !e.getValue().equals(knownBiomes.get(e.getKey())));
        if (changed) { knownBiomes.putAll(biomes); save(); }
    }

    /** Missing properties inherit loaded defaults; an explicit empty songs array means silence. */
    public ScenarioConfig scenario(ScenarioId id, ScenarioConfig defaults) {
        return applyScenario(scenarios.getOrDefault(id, new JsonObject()), customBase(defaults));
    }

    public boolean songsEdited(ScenarioId id, ScenarioConfig defaults) {
        return !scenario(id, defaults).songs().equals(defaults.songs());
    }

    public void setScenario(ScenarioId id, ScenarioConfig value, ScenarioConfig defaults) {
        JsonObject override = scenarioJson(value);
        JsonObject base = scenarioJson(customBase(defaults));
        base.entrySet().forEach(e -> { if (e.getValue().equals(override.get(e.getKey()))) override.remove(e.getKey()); });
        if (override.isEmpty()) scenarios.remove(id);
        else scenarios.put(id, override);
        save();
    }

    public void applyFrequencyToAll(ScenarioConfig.Frequency value) {
        customFrequency = value;
        scenarios.values().forEach(o -> o.remove("frequency"));
        save();
    }

    public void applyJukeboxToAll(JukeboxSetting value) {
        customJukebox = value;
        scenarios.values().forEach(o -> o.remove("jukebox"));
        save();
    }

    private ScenarioConfig customBase(ScenarioConfig value) {
        return new ScenarioConfig(value.priority(), value.active(), value.interrupt(), customFrequency, customJukebox,
                value.mustBeOutside(), value.yLevel(), value.songs());
    }

    private void readCustom(JsonObject custom) {
        if (custom.has("defaults")) {
            var defaults = custom.getAsJsonObject("defaults");
            if (defaults.has("frequency")) customFrequency = ScenarioConfig.Frequency.valueOf(defaults.get("frequency").getAsString());
            if (defaults.has("jukebox")) customJukebox = JukeboxSetting.valueOf(defaults.get("jukebox").getAsString());
        }
        if (custom.has("knownBiomes")) custom.getAsJsonObject("knownBiomes").entrySet().forEach(e -> {
            try {
                ScenarioId.biome(e.getKey());
                knownBiomes.put(e.getKey(), e.getValue().getAsString());
            } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException | NullPointerException error) {
                LOGGER.warn("Ignoring invalid known biome {} in {}", e.getKey(), file, error);
            }
        });
        if (custom.has("scenarios")) custom.getAsJsonObject("scenarios").entrySet().forEach(e -> {
            try {
                var id = ScenarioId.parse(e.getKey());
                var override = e.getValue().getAsJsonObject().deepCopy();
                var validated = applyScenario(override, new ScenarioConfig(2, true, false, customFrequency, customJukebox,
                        true, 64, List.of()));
                if (override.has("songs")) override.add("songs", scenarioJson(validated).get("songs"));
                if (override.has("priority")) override.addProperty("priority", validated.priority());
                scenarios.put(id, override);
            } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException | NullPointerException | ClassCastException error) {
                LOGGER.warn("Ignoring invalid Custom scenario {} in {}", e.getKey(), file, error);
            }
        });
    }

    private static ScenarioConfig applyScenario(JsonObject o, ScenarioConfig d) {
        List<Deck.Entry> songs = d.songs();
        if (o.has("songs")) {
            var entries = new ArrayList<Deck.Entry>();
            for (var element : o.getAsJsonArray("songs")) {
                var song = element.getAsJsonObject();
                entries.add(new Deck.Entry(song.get("id").getAsString(), Math.clamp(song.get("weight").getAsInt(), 1, 100)));
            }
            songs = entries;
        }
        return new ScenarioConfig(o.has("priority") ? Math.clamp(o.get("priority").getAsInt(), 1, 10) : d.priority(),
                o.has("active") ? o.get("active").getAsBoolean() : d.active(),
                o.has("interrupt") ? o.get("interrupt").getAsBoolean() : d.interrupt(),
                o.has("frequency") ? ScenarioConfig.Frequency.valueOf(o.get("frequency").getAsString()) : d.frequency(),
                o.has("jukebox") ? JukeboxSetting.valueOf(o.get("jukebox").getAsString()) : d.jukebox(),
                o.has("mustBeOutside") ? o.get("mustBeOutside").getAsBoolean() : d.mustBeOutside(),
                o.has("yLevel") ? o.get("yLevel").getAsInt() : d.yLevel(), songs);
    }

    private static JsonObject scenarioJson(ScenarioConfig c) {
        var o = new JsonObject();
        o.addProperty("priority", c.priority());
        o.addProperty("active", c.active());
        o.addProperty("interrupt", c.interrupt());
        o.addProperty("frequency", c.frequency().name());
        o.addProperty("jukebox", c.jukebox().name());
        o.addProperty("mustBeOutside", c.mustBeOutside());
        o.addProperty("yLevel", c.yLevel());
        o.add("songs", JSON.toJsonTree(c.songs()));
        return o;
    }

    public boolean ticked(String id) { return !unticked.contains(id); }

    public void setMode(Mode value) {
        if (mode != value) { mode = value; save(); }
    }

    public void setShuffle(boolean value) {
        if (shuffle != value) { shuffle = value; save(); }
    }

    public void setTicked(String id, boolean value) {
        boolean changed = value ? unticked.remove(id) : unticked.add(id);
        if (changed) save();
    }

    public void setAllTicked(Collection<String> ids, boolean value) {
        boolean changed = value ? unticked.removeAll(ids) : unticked.addAll(ids);
        if (changed) save();
    }

    public JukeboxSetting jukebox() {
        return jukebox;
    }

    public boolean firstRunDone() {
        return firstRunDone;
    }

    public void completeFirstRun(Runnable applyVanillaDefault) {
        if (!firstRunDone) {
            applyVanillaDefault.run();
            firstRunDone = true;
            save();
        }
    }

    public void setJukebox(JukeboxSetting value) {
        if (jukebox != value) {
            jukebox = value;
            save();
        }
    }

    private void save() {
        JsonObject data = new JsonObject();
        data.addProperty("configVersion", 2);
        data.addProperty("firstRunDone", firstRunDone);
        data.addProperty("mode", mode().name());
        data.addProperty("jukebox", jukebox.name());
        JsonObject basic = new JsonObject();
        basic.addProperty("shuffle", shuffle);
        basic.add("unticked", JSON.toJsonTree(unticked.stream().sorted().toList()));
        data.add("basic", basic);
        var custom = new JsonObject();
        var defaults = new JsonObject();
        defaults.addProperty("frequency", customFrequency.name());
        defaults.addProperty("jukebox", customJukebox.name());
        custom.add("defaults", defaults);
        custom.add("knownBiomes", JSON.toJsonTree(knownBiomes));
        var overrides = new JsonObject();
        scenarios.forEach((id, value) -> { if (!value.isEmpty()) overrides.add(id.toString(), value); });
        custom.add("scenarios", overrides);
        data.add("custom", custom);
        try {
            Path directory = file.toAbsolutePath().getParent();
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, "whostoppedthemusic-", ".tmp");
            try {
                Files.writeString(temporary, JSON.toJson(data) + System.lineSeparator());
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            LOGGER.warn("Can't save music settings to {}; changes remain active in memory", file, e);
        }
    }
}
