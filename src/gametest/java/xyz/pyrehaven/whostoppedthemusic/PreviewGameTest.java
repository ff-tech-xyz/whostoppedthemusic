package xyz.pyrehaven.whostoppedthemusic;

import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.channels;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.check;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.config;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.configBytes;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.field;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.press;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.songs;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.track;
import static xyz.pyrehaven.whostoppedthemusic.CustomUiSupport.winner;

import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.JukeboxSongs;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.slf4j.LoggerFactory;
import xyz.pyrehaven.whostoppedthemusic.music.Deck;
import xyz.pyrehaven.whostoppedthemusic.music.MidpointAudioStream;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.custom.*;
import xyz.pyrehaven.whostoppedthemusic.ui.*;
import xyz.pyrehaven.whostoppedthemusic.ui.custom.*;

final class PreviewGameTest {
    static void preview(ClientGameTestContext context) {
        context.getInput().resizeWindow(960, 720);
        var menu = ScenarioId.special(Special.MENU);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.7);
            client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.7);
            WhoStoppedTheMusic.settings().setMode(Mode.CUSTOM);
            var folder = client.gameDirectory.toPath().resolve("music");
            try {
                Files.createDirectories(folder);
                for (var entry : Map.of("stereo.mp3", "Preview-check.mp3", "mono.ogg", "Preview-check.ogg").entrySet()) {
                    try (var input = CustomUiGameTest.class.getResourceAsStream("/audio/" + entry.getKey())) {
                        Files.copy(input, folder.resolve(entry.getValue()), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } catch (IOException error) { throw new UncheckedIOException(error); }
        });
        var refresh = context.computeOnClient(client -> WhoStoppedTheMusic.LIBRARY.refreshSongs());
        context.waitFor(client -> refresh.isDone());
        winner(context, menu);
        context.runOnClient(client -> {
            var defaults = MusicDirector.get(client).customPlayback().defaults(menu).config();
            WhoStoppedTheMusic.settings().setScenario(menu, defaults.withSongs(List.of(
                    new Deck.Entry("minecraft:music/game/sweden", 1),
                    new Deck.Entry("minecraft:records/pigstep", 1),
                    new Deck.Entry("custom:Preview-check.mp3", 1),
                    new Deck.Entry("custom:Preview-check.ogg", 1))), defaults);
        });
        context.waitTicks(3);
        context.setScreen(() -> new ScenarioScreen(null, WhoStoppedTheMusic.settings(), menu));
        context.waitTicks(3);
        context.runOnClient(client -> MusicDirector.get(client).play(track("minecraft:music/game/sweden")));
        context.waitFor(client -> GameTestSupport.channelState(client, MusicDirector.get(client).current(), true).join());
        var background = context.computeOnClient(client -> MusicDirector.get(client).current());
        var handle = context.computeOnClient(client -> channels(client).get(background));
        var history = context.computeOnClient(PreviewGameTest::deckState);
        var bytes = configBytes();
        selectPreview(context, "minecraft:records/pigstep");
        var excerpt = context.computeOnClient(PreviewGameTest::previewSource);
        context.waitFor(client -> GameTestSupport.channelState(client, excerpt, true).join());
        long start = System.nanoTime();
        var offset = context.computeOnClient(client -> sampleOffset(client, background));
        context.waitTicks(8);
        context.runOnClient(client -> {
            check(GameTestSupport.channelState(client, background, false).join(), "background native source is paused");
            check(sampleOffset(client, background) == offset, "paused background sample position is frozen");
            check(excerpt.getSource() == SoundSource.MUSIC, "preview uses Music category");
            var stream = nativeStream(client, excerpt);
            check(stream instanceof MidpointAudioStream, "native source owns midpoint stream");
            long total = (long) field(stream, "totalFrames");
            check((long) field(stream, "startFrame") == total / 2 && total > 44100 * 10, "resource Ogg midpoint uses real granule sample length");
            check((long) field(stream, "excerptFrames") == (long) (((AudioStream) stream).getFormat().getSampleRate() * 3), "native preview contains exactly three seconds");
        });
        context.takeScreenshot("preview-menu-active");
        context.waitFor(client -> !MusicDirector.get(client).preview().active());
        double elapsed = (System.nanoTime() - start) / 1_000_000_000.0;
        check(elapsed > 2.5 && elapsed < 4.5, "real preview duration near 3 seconds: " + elapsed);
        context.waitFor(client -> GameTestSupport.channelState(client, background, true).join());
        context.runOnClient(client -> {
            check(MusicDirector.get(client).current() == background && channels(client).get(background) == handle, "resume retains same sound and native handle");
            check(deckState(client).equals(history), "preview leaves deck cards/cursor/history exact");
        });
        check(Arrays.equals(bytes, configBytes()), "preview changes no persisted settings");
        previewCancellation(context, menu, background);
        for (String extension : List.of("mp3", "ogg")) {
            selectPreview(context, "custom:Preview-check." + extension);
            var local = context.computeOnClient(PreviewGameTest::previewSource);
            context.waitFor(client -> GameTestSupport.channelState(client, local, true).join());
            context.runOnClient(client -> {
                var stream = nativeStream(client, local);
                long total = (long) field(stream, "totalFrames");
                check((long) field(stream, "startFrame") == total / 2, "import midpoint: " + extension);
                check(total > 128000 && total < 150000, "import uses full three-second fixture sample count: " + extension);
                check(GameTestSupport.channelGain(client, local).join() > 0, "local preview has actual nonzero Music gain");
            });
            context.waitFor(client -> !MusicDirector.get(client).preview().active());
            context.waitFor(client -> GameTestSupport.channelState(client, background, true).join());
        }
        context.runOnClient(client -> client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0));
        selectPreview(context, "minecraft:records/pigstep");
        var muted = context.computeOnClient(PreviewGameTest::previewSource);
        context.waitFor(client -> GameTestSupport.channelState(client, muted, true).join());
        context.runOnClient(client -> check(GameTestSupport.channelGain(client, muted).join() == 0, "muted preview stays silent on real source"));
        context.runOnClient(client -> MusicDirector.get(client).preview().cancel());
        context.runOnClient(client -> client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.7));
        context.runOnClient(client -> MusicDirector.get(client).togglePlayback());
        selectPreview(context, "minecraft:records/pigstep");
        context.runOnClient(client -> MusicDirector.get(client).preview().cancel());
        context.waitTicks(3);
        context.runOnClient(client -> check(MusicDirector.get(client).paused() && GameTestSupport.channelState(client, background, false).join(), "preexisting manual pause is preserved"));
        context.runOnClient(client -> {
            MusicDirector.get(client).togglePlayback();
            var defaults = MusicDirector.get(client).customPlayback().defaults(menu).config();
            WhoStoppedTheMusic.settings().setScenario(menu, defaults.withSongs(List.of()), defaults);
            client.getMusicManager().stopPlaying();
        });
        context.waitTicks(4);
        context.runOnClient(client -> MusicDirector.get(client).preview().toggle(track("minecraft:records/pigstep")));
        context.waitFor(client -> GameTestSupport.channelState(client, previewSource(client), true).join());
        context.runOnClient(client -> MusicDirector.get(client).preview().cancel());
        context.waitTicks(4);
        context.runOnClient(client -> check(MusicDirector.get(client).current() == null && !MusicDirector.get(client).active(), "silence resumes nothing"));
        context.runOnClient(client -> client.gui.setScreen(null));
        previewJukebox(context);
        context.runOnClient(client -> client.gui.setScreen(new TitleScreen()));
        LoggerFactory.getLogger("whostoppedthemusic-test").info("MIDPOINT PREVIEW PASS: actual {}s excerpt, Ogg granules/import MP3+OGG, frozen same native source, repeat/replace/remove/exit/close/early cancel, silence/manual-pause/mute/jukebox, unchanged deck and config", elapsed);
    }

    static void previewCancellation(ClientGameTestContext context, ScenarioId menu, SoundInstance background) {
        selectPreview(context, "minecraft:records/pigstep");
        var first = context.computeOnClient(PreviewGameTest::previewSource);
        selectPreview(context, "minecraft:music/game/sweden");
        context.waitFor(client -> !GameTestSupport.channelState(client, first, true).join());
        check(context.computeOnClient(client -> MusicDirector.get(client).preview().playing("minecraft:music/game/sweden")), "new preview replaces old");
        press(context, "▶ Preview", 0);
        context.runOnClient(client -> check(!MusicDirector.get(client).preview().active(), "repeat cancels instead of restarting"));
        context.waitFor(client -> GameTestSupport.channelState(client, background, true).join());
        selectPreview(context, "minecraft:records/pigstep");
        context.runOnClient(client -> client.gui.screen().onClose());
        context.runOnClient(client -> check(!MusicDirector.get(client).preview().active(), "closing editor cancels"));
        context.setScreen(() -> new ScenarioScreen(null, WhoStoppedTheMusic.settings(), menu));
        selectPreview(context, "minecraft:records/pigstep");
        press(context, "Copy songs", 0);
        context.runOnClient(client -> check(!MusicDirector.get(client).preview().active(), "picker transition cancels preview"));
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(ScenarioScreen.class);
        selectPreview(context, "custom:Preview-check.ogg");
        press(context, "Remove", 0);
        context.runOnClient(client -> check(!MusicDirector.get(client).preview().active(), "removing previewed song cancels"));
        var early = context.computeOnClient(client -> {
            var p = MusicDirector.get(client).preview();
            p.toggle(track("minecraft:records/pigstep"));
            var source = previewSource(client);
            p.cancel();
            return source;
        });
        context.waitTicks(12);
        context.waitFor(client -> !channels(client).containsKey(early));
        context.runOnClient(client -> {
            check(!MusicDirector.get(client).preview().active() && !GameTestSupport.channelState(client, early, true).join(), "cancel before decoding cannot late-start a native source");
            check(!channels(client).containsKey(early), "early canceled preview retires the native handle through engine cleanup");
            var d = MusicDirector.get(client).customPlayback().defaults(menu).config();
            WhoStoppedTheMusic.settings().setScenario(menu, config(client, menu).withSongs(List.of(
                    new Deck.Entry("minecraft:records/pigstep", 1),
                    new Deck.Entry("custom:Preview-check.mp3", 1),
                    new Deck.Entry("custom:Preview-check.ogg", 1))), d);
        });
        context.waitTicks(3);
    }

    static void previewJukebox(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.waitForScreen(null);
            context.runOnClient(client -> WhoStoppedTheMusic.settings().applyJukeboxToAll(JukeboxSetting.PAUSE_MUSIC));
            context.waitTicks(4);
            var pos = context.computeOnClient(client -> client.gameRenderer.mainCamera().blockPosition().immutable());
            context.runOnClient(client -> {
                var registry = client.level.registryAccess().lookupOrThrow(Registries.JUKEBOX_SONG);
                client.level.levelEvent(null, 1010, pos, registry.getId(registry.getValueOrThrow(JukeboxSongs.CAT)));
            });
            context.waitFor(client -> MusicDirector.get(client).jukeboxHolding());
            context.waitTicks(330);
            context.runOnClient(client -> check(!MusicDirector.get(client).active(), "jukebox already holds background silence"));
            context.runOnClient(client -> MusicDirector.get(client).preview().toggle(track("minecraft:records/pigstep")));
            var source = context.computeOnClient(PreviewGameTest::previewSource);
            context.waitFor(client -> GameTestSupport.channelState(client, source, true).join());
            context.runOnClient(client -> check(GameTestSupport.channelGain(client, source).join() > 0, "preview audible over existing jukebox hold"));
            context.waitFor(client -> !MusicDirector.get(client).preview().active());
            context.waitTicks(4);
            context.runOnClient(client -> {
                check(!MusicDirector.get(client).active() && MusicDirector.get(client).jukeboxHolding(), "preview completion preserves jukebox hold");
                client.level.levelEvent(null, 1011, pos, 0);
                MusicDirector.get(client).next();
            });
            context.waitFor(client -> GameTestSupport.channelState(client, MusicDirector.get(client).current(), true).join());
            var background = context.computeOnClient(client -> MusicDirector.get(client).current());
            // A jukebox that begins during the preview must not briefly release the old song.
            context.runOnClient(client -> {
                MusicDirector.get(client).preview().toggle(track("minecraft:records/pigstep"));
                var registry = client.level.registryAccess().lookupOrThrow(Registries.JUKEBOX_SONG);
                client.level.levelEvent(null, 1010, pos, registry.getId(registry.getValueOrThrow(JukeboxSongs.CAT)));
            });
            var canceled = context.computeOnClient(PreviewGameTest::previewSource);
            context.waitFor(client -> MusicDirector.get(client).jukeboxHolding());
            context.runOnClient(client -> MusicDirector.get(client).preview().cancel());
            context.waitTicks(3);
            context.runOnClient(client -> {
                check(GameTestSupport.channelState(client, background, false).join(), "jukebox beginning during preview keeps same background paused");
                client.level.levelEvent(null, 1011, pos, 0);
            });
            context.waitFor(client -> GameTestSupport.channelState(client, background, true).join());
            context.waitFor(client -> !channels(client).containsKey(canceled));
        }
    }

    static void selectPreview(ClientGameTestContext context, String id) {
        context.runOnClient(client -> songs(client).selectSong(id));
        context.waitTicks(2);
        press(context, "▶ Preview", 0);
    }

    static SoundInstance previewSource(Minecraft client) {
        return (SoundInstance) field(MusicDirector.get(client).preview(), "source");
    }

    static <T> T onSource(Minecraft client, SoundInstance sound,
                                   Function<Channel, T> read) {
        var result = new CompletableFuture<T>();
        var handle = channels(client).get(sound);
        check(handle != null && !handle.isStopped(), "native source allocated");
        handle.execute(channel -> {
            try { result.complete(read.apply(channel)); }
            catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result.orTimeout(5, TimeUnit.SECONDS).join();
    }

    static int sampleOffset(Minecraft client, SoundInstance sound) {
        return onSource(client, sound, channel -> {
            int source = (int) field(channel, "source");
            check(AL10.alGetError() == 0, "clean native boundary before sample offset source=" + source);
            int offset = AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET);
            check(AL10.alGetError() == 0, "sample offset native query source=" + source);
            return offset;
        });
    }

    static Object nativeStream(Minecraft client, SoundInstance sound) {
        return onSource(client, sound, channel -> field(channel, "stream"));
    }

    static List<Object> deckState(Minecraft client) {
        var deck = MusicDirector.get(client).customPlayback().deck();
        return List.of(List.copyOf((List<?>) field(deck, "history")), List.copyOf((List<?>) field(deck, "cards")), field(deck, "cursor"));
    }
}
