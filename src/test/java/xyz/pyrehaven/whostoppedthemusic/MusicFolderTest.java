package xyz.pyrehaven.whostoppedthemusic;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.pyrehaven.whostoppedthemusic.custom.MusicFolder;

final class MusicFolderTest {
    @TempDir Path game;

    @Test void scanUsesGameDirectoryAndFiltersFilesWithoutFollowingLinks() throws Exception {
        var folder = new MusicFolder(game);
        assertEquals(List.of(), folder.scan());
        Files.writeString(folder.directory().resolve("Artist - Title.MP3"), "invalid but listed");
        Files.writeString(folder.directory().resolve("夜.ogg"), "invalid but listed");
        Files.writeString(folder.directory().resolve("cover.png"), "image");
        Files.createDirectory(folder.directory().resolve("directory.mp3"));
        Files.createSymbolicLink(folder.directory().resolve("link.ogg"), folder.directory().resolve("夜.ogg"));
        var songs = folder.scan();
        assertEquals(List.of("custom:Artist - Title.MP3", "custom:夜.ogg"), songs.stream().map(MusicFolder.Song::id).toList());
        assertEquals(List.of("Artist - Title", "夜"), songs.stream().map(MusicFolder.Song::title).toList());
        assertEquals(songs, new MusicFolder(game).scan());
        Files.delete(songs.getFirst().file());
        assertEquals(1, folder.scan().size());
    }

    @Test void sameTitleDifferentExtensionKeepsDifferentSourceIdentity() throws Exception {
        var folder = new MusicFolder(game);
        folder.scan();
        Files.createFile(folder.directory().resolve("same.mp3"));
        Files.createFile(folder.directory().resolve("same.ogg"));
        var songs = folder.scan();
        assertEquals(songs.getFirst().title(), songs.getLast().title());
        assertNotEquals(songs.getFirst().id(), songs.getLast().id());
    }
}
