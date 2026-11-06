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
        return Mode.VANILLA;
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
