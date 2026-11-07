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

    public MusicFolder(Path gameDirectory) { directory = gameDirectory.toAbsolutePath().normalize().resolve("music"); }
    public Path directory() { return directory; }

    public record Song(Path file) {
        public String id() { return "custom:" + file.getFileName(); }
        public String title() {
            String name = file.getFileName().toString();
            return name.substring(0, name.lastIndexOf('.'));
        }
    }

    public static boolean supported(Path path) {
        if (path.getFileName() == null) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".mp3") || name.endsWith(".ogg");
    }

    public List<Song> scan() throws IOException {
        prepare();
        try (var files = Files.list(directory)) {
            return files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(MusicFolder::supported).sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(Song::new).toList();
        }
    }

    public void prepare() throws IOException {
        Files.createDirectories(directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Music folder is not a directory");
    }

    /** Never replaces an existing name, including names differing only by case. */
    public Song importSong(Path source) throws IOException {
        prepare();
        if (!supported(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Not a regular MP3 or OGG file");
        }
        String name = source.getFileName().toString();
        int dot = name.lastIndexOf('.');
        var names = new java.util.HashSet<String>();
        try (var files = Files.list(directory)) {
            files.forEach(file -> names.add(file.getFileName().toString().toLowerCase(Locale.ROOT)));
        }
        for (int number = 1; ; number++) {
            String candidate = number == 1 ? name : name.substring(0, dot) + " (" + number + ")" + name.substring(dot);
            if (names.contains(candidate.toLowerCase(Locale.ROOT))) continue;
            Path target = directory.resolve(candidate);
            try (var input = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS)) {
                var output = Files.newOutputStream(target, java.nio.file.StandardOpenOption.CREATE_NEW,
                        java.nio.file.StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                try (output) {
                    input.transferTo(output);
                } catch (IOException error) {
                    Files.deleteIfExists(target);
                    throw error;
                }
                return new Song(target);
            } catch (java.nio.file.FileAlreadyExistsException collision) {
                names.add(candidate.toLowerCase(Locale.ROOT));
            }
        }
    }

    public void remove(Song song) throws IOException {
        prepare();
        Path file = song.file().toAbsolutePath().normalize();
        if (!directory.equals(file.getParent()) || !supported(file)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Not a song in the music folder");
        }
        Files.delete(file);
    }
}
