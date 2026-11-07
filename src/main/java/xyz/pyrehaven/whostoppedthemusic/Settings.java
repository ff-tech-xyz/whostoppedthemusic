package xyz.pyrehaven.whostoppedthemusic;

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

public final class Settings {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private boolean firstRunDone;
    private JukeboxSetting jukebox = JukeboxSetting.PAUSE_MUSIC;
    private Mode mode = Mode.VANILLA;
    private boolean shuffle = true;
    private final java.util.Set<String> unticked = new java.util.HashSet<>();

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
            return settings;
        } catch (JsonParseException | IllegalStateException | IllegalArgumentException | UnsupportedOperationException e) {
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

    public void setAllTicked(java.util.Collection<String> ids, boolean value) {
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
        data.addProperty("configVersion", 1);
        data.addProperty("firstRunDone", firstRunDone);
        data.addProperty("mode", mode().name());
        data.addProperty("jukebox", jukebox.name());
        JsonObject basic = new JsonObject();
        basic.addProperty("shuffle", shuffle);
        basic.add("unticked", JSON.toJsonTree(unticked.stream().sorted().toList()));
        data.add("basic", basic);
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
            throw new UncheckedIOException(e);
        }
    }
}
