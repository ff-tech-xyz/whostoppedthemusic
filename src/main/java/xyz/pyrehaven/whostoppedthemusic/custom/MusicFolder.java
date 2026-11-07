package xyz.pyrehaven.whostoppedthemusic.custom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** The game directory owns these files; discovery is explicitly requested, never polled. */
public final class MusicFolder {
    private final Path directory;

    public MusicFolder(Path gameDirectory) { directory = gameDirectory.resolve("music"); }
    public Path directory() { return directory; }

    public record Song(Path file) {
        public String id() { return "custom:" + file.getFileName(); }
        public String title() {
            String name = file.getFileName().toString();
            return name.substring(0, name.lastIndexOf('.'));
        }
    }

    public static boolean supported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".mp3") || name.endsWith(".ogg");
    }

    public List<Song> scan() throws IOException {
        Files.createDirectories(directory);
        try (var files = Files.list(directory)) {
            return files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(MusicFolder::supported).sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(Song::new).toList();
        }
    }
}
