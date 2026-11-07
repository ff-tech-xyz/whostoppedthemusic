package xyz.pyrehaven.whostoppedthemusic;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.pyrehaven.whostoppedthemusic.music.MusicFolder;

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

    @Test void copiesKeepSourcesAndNumberDuplicateNamesWithoutOverwriting() throws Exception {
        var folder = new MusicFolder(game);
        var source = Files.writeString(game.resolve("Signal.MP3"), "original");
        var first = folder.importSong(source);
        Files.writeString(source, "new copy");
        var second = folder.importSong(source);
        var lowerCase = Files.writeString(game.resolve("signal.mp3"), "case duplicate");
        var third = folder.importSong(lowerCase);
        assertEquals("Signal.MP3", first.file().getFileName().toString());
        assertEquals("Signal (2).MP3", second.file().getFileName().toString());
        assertEquals("signal (3).mp3", third.file().getFileName().toString());
        assertEquals("original", Files.readString(first.file()));
        assertEquals("new copy", Files.readString(second.file()));
        assertEquals("new copy", Files.readString(source));
        folder.remove(second);
        assertFalse(Files.exists(second.file()));
        assertTrue(Files.exists(first.file()));
        assertTrue(Files.exists(source));
    }

    @Test void mutationsRejectOutsideFilesLinksDirectoriesAndUnsupportedFiles() throws Exception {
        var folder = new MusicFolder(game);
        folder.prepare();
        var outside = Files.writeString(game.resolve("outside.ogg"), "outside");
        var link = Files.createSymbolicLink(folder.directory().resolve("link.ogg"), outside);
        var directory = Files.createDirectory(game.resolve("directory.mp3"));
        var image = Files.writeString(game.resolve("image.png"), "png");
        for (var invalid : List.of(link, directory, image)) assertThrows(IOException.class, () -> folder.importSong(invalid));
        assertThrows(IOException.class, () -> folder.remove(new MusicFolder.Song(outside)));
        assertThrows(IOException.class, () -> folder.remove(new MusicFolder.Song(link)));
        assertEquals("outside", Files.readString(outside));
        Files.delete(link);
        Files.delete(folder.directory());
        Files.createSymbolicLink(folder.directory(), game);
        assertThrows(IOException.class, folder::scan);
        assertThrows(IOException.class, () -> folder.importSong(outside));
        assertThrows(IOException.class, () -> folder.remove(new MusicFolder.Song(folder.directory().resolve("outside.ogg"))));
        assertTrue(Files.exists(outside));
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
