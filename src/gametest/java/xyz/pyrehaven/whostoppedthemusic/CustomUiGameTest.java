package xyz.pyrehaven.whostoppedthemusic;

import java.util.List;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.sounds.MusicManager.MusicFrequency;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.custom.*;
import xyz.pyrehaven.whostoppedthemusic.ui.*;
import xyz.pyrehaven.whostoppedthemusic.ui.custom.*;

/** Focused selection of the existing packaged-client harness, not a second launcher. */
final class CustomUiGameTest {
    static void preview(ClientGameTestContext context) {
        context.getInput().resizeWindow(960, 720);
        var menu = ScenarioId.special(Special.MENU);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.7);
            client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MUSIC).set(0.7);
            WhoStoppedTheMusic.settings().setMode(Mode.CUSTOM);
            var folder = client.gameDirectory.toPath().resolve("music");
            try {
                Files.createDirectories(folder);
                for (var entry : java.util.Map.of("stereo.mp3", "Preview-check.mp3", "mono.ogg", "Preview-check.ogg").entrySet()) {
                    try (var input = CustomUiGameTest.class.getResourceAsStream("/audio/" + entry.getKey())) {
                        Files.copy(input, folder.resolve(entry.getValue()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        });
        var refresh = context.computeOnClient(client -> WhoStoppedTheMusic.LIBRARY.refreshSongs());
        context.waitFor(client -> refresh.isDone());
        winner(context, menu);
        context.runOnClient(client -> {
            var defaults = MusicDirector.get(client).customPlayback().defaults(menu).config();
            WhoStoppedTheMusic.settings().setScenario(menu, defaults.withSongs(List.of(
                    new xyz.pyrehaven.whostoppedthemusic.music.Deck.Entry("minecraft:music/game/sweden", 1),
                    new xyz.pyrehaven.whostoppedthemusic.music.Deck.Entry("minecraft:records/pigstep", 1),
                    new xyz.pyrehaven.whostoppedthemusic.music.Deck.Entry("custom:Preview-check.mp3", 1),
                    new xyz.pyrehaven.whostoppedthemusic.music.Deck.Entry("custom:Preview-check.ogg", 1))), defaults);
        });
        context.waitTicks(3);
        context.setScreen(() -> new ScenarioScreen(null, WhoStoppedTheMusic.settings(), menu));
        context.waitTicks(3);
        context.runOnClient(client -> MusicDirector.get(client).play(track("minecraft:music/game/sweden")));
        context.waitFor(client -> ClientSmokeGameTest.channelState(client, MusicDirector.get(client).current(), true).join());
        var background = context.computeOnClient(client -> MusicDirector.get(client).current());
        var handle = context.computeOnClient(client -> channels(client).get(background));
        var history = context.computeOnClient(CustomUiGameTest::deckState);
        var bytes = configBytes();
        selectPreview(context, "minecraft:records/pigstep");
        var excerpt = context.computeOnClient(CustomUiGameTest::previewSource);
        context.waitFor(client -> ClientSmokeGameTest.channelState(client, excerpt, true).join());
        long start = System.nanoTime();
        var offset = context.computeOnClient(client -> sampleOffset(client, background));
        context.waitTicks(8);
        context.runOnClient(client -> {
            check(ClientSmokeGameTest.channelState(client, background, false).join(), "background native source is paused");
            check(sampleOffset(client, background) == offset, "paused background sample position is frozen");
            check(excerpt.getSource() == net.minecraft.sounds.SoundSource.MUSIC, "preview uses Music category");
            var stream = nativeStream(client, excerpt);
            check(stream instanceof xyz.pyrehaven.whostoppedthemusic.music.MidpointAudioStream, "native source owns midpoint stream");
            long total = (long) field(stream, "totalFrames");
            check((long) field(stream, "startFrame") == total / 2 && total > 44100 * 10, "resource Ogg midpoint uses real granule sample length");
            check((long) field(stream, "excerptFrames") == (long) (((net.minecraft.client.sounds.AudioStream) stream).getFormat().getSampleRate() * 3), "native preview contains exactly three seconds");
        });
        context.takeScreenshot("preview-menu-active");
        context.waitFor(client -> !MusicDirector.get(client).preview().active());
        double elapsed = (System.nanoTime() - start) / 1_000_000_000.0;
        check(elapsed > 2.5 && elapsed < 4.5, "real preview duration near 3 seconds: " + elapsed);
        context.waitFor(client -> ClientSmokeGameTest.channelState(client, background, true).join());
        context.runOnClient(client -> {
            check(MusicDirector.get(client).current() == background && channels(client).get(background) == handle, "resume retains same sound and native handle");
            check(deckState(client).equals(history), "preview leaves deck cards/cursor/history exact");
        });
        check(Arrays.equals(bytes, configBytes()), "preview changes no persisted settings");
        previewCancellation(context, menu, background);
        for (String extension : List.of("mp3", "ogg")) {
            selectPreview(context, "custom:Preview-check." + extension);
            var local = context.computeOnClient(CustomUiGameTest::previewSource);
            context.waitFor(client -> ClientSmokeGameTest.channelState(client, local, true).join());
            context.runOnClient(client -> {
                var stream = nativeStream(client, local);
                long total = (long) field(stream, "totalFrames");
                check((long) field(stream, "startFrame") == total / 2, "import midpoint: " + extension);
                check(total > 128000 && total < 150000, "import uses full three-second fixture sample count: " + extension);
                check(ClientSmokeGameTest.channelGain(client, local).join() > 0, "local preview has actual nonzero Music gain");
            });
            context.waitFor(client -> !MusicDirector.get(client).preview().active());
            context.waitFor(client -> ClientSmokeGameTest.channelState(client, background, true).join());
        }
        context.runOnClient(client -> client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MUSIC).set(0.0));
        selectPreview(context, "minecraft:records/pigstep");
        var muted = context.computeOnClient(CustomUiGameTest::previewSource);
        context.waitFor(client -> ClientSmokeGameTest.channelState(client, muted, true).join());
        context.runOnClient(client -> check(ClientSmokeGameTest.channelGain(client, muted).join() == 0, "muted preview stays silent on real source"));
        context.runOnClient(client -> MusicDirector.get(client).preview().cancel());
        context.runOnClient(client -> client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MUSIC).set(0.7));
        context.runOnClient(client -> MusicDirector.get(client).togglePlayback());
        selectPreview(context, "minecraft:records/pigstep");
        context.runOnClient(client -> MusicDirector.get(client).preview().cancel());
        context.waitTicks(3);
        context.runOnClient(client -> check(MusicDirector.get(client).paused() && ClientSmokeGameTest.channelState(client, background, false).join(), "preexisting manual pause is preserved"));
        context.runOnClient(client -> {
            MusicDirector.get(client).togglePlayback();
            var defaults = MusicDirector.get(client).customPlayback().defaults(menu).config();
            WhoStoppedTheMusic.settings().setScenario(menu, defaults.withSongs(List.of()), defaults);
            client.getMusicManager().stopPlaying();
        });
        context.waitTicks(4);
        context.runOnClient(client -> MusicDirector.get(client).preview().toggle(track("minecraft:records/pigstep")));
        context.waitFor(client -> ClientSmokeGameTest.channelState(client, previewSource(client), true).join());
        context.runOnClient(client -> MusicDirector.get(client).preview().cancel());
        context.waitTicks(4);
        context.runOnClient(client -> check(MusicDirector.get(client).current() == null && !MusicDirector.get(client).active(), "silence resumes nothing"));
        context.runOnClient(client -> client.gui.setScreen(null));
        previewJukebox(context);
        context.runOnClient(client -> client.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));
        org.slf4j.LoggerFactory.getLogger("whostoppedthemusic-test").info("MIDPOINT PREVIEW PASS: actual {}s excerpt, Ogg granules/import MP3+OGG, frozen same native source, repeat/replace/remove/exit/close/early cancel, silence/manual-pause/mute/jukebox, unchanged deck and config", elapsed);
    }

    private static void previewCancellation(ClientGameTestContext context, ScenarioId menu, net.minecraft.client.resources.sounds.SoundInstance background) {
        selectPreview(context, "minecraft:records/pigstep");
        var first = context.computeOnClient(CustomUiGameTest::previewSource);
        selectPreview(context, "minecraft:music/game/sweden");
        context.waitFor(client -> !ClientSmokeGameTest.channelState(client, first, true).join());
        check(context.computeOnClient(client -> MusicDirector.get(client).preview().playing("minecraft:music/game/sweden")), "new preview replaces old");
        press(context, "▶ Preview", 0);
        context.runOnClient(client -> check(!MusicDirector.get(client).preview().active(), "repeat cancels instead of restarting"));
        context.waitFor(client -> ClientSmokeGameTest.channelState(client, background, true).join());
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
            check(!MusicDirector.get(client).preview().active() && !ClientSmokeGameTest.channelState(client, early, true).join(), "cancel before decoding cannot late-start a native source");
            check(!channels(client).containsKey(early), "early canceled preview retires the native handle through engine cleanup");
            var d = MusicDirector.get(client).customPlayback().defaults(menu).config();
            WhoStoppedTheMusic.settings().setScenario(menu, config(client, menu).withSongs(List.of(
                    new xyz.pyrehaven.whostoppedthemusic.music.Deck.Entry("minecraft:records/pigstep", 1),
                    new xyz.pyrehaven.whostoppedthemusic.music.Deck.Entry("custom:Preview-check.mp3", 1),
                    new xyz.pyrehaven.whostoppedthemusic.music.Deck.Entry("custom:Preview-check.ogg", 1))), d);
        });
        context.waitTicks(3);
    }

    private static void previewJukebox(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.waitForScreen(null);
            context.runOnClient(client -> WhoStoppedTheMusic.settings().applyJukeboxToAll(JukeboxSetting.PAUSE_MUSIC));
            context.waitTicks(4);
            var pos = context.computeOnClient(client -> client.gameRenderer.mainCamera().blockPosition().immutable());
            context.runOnClient(client -> {
                var registry = client.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.JUKEBOX_SONG);
                client.level.levelEvent(null, 1010, pos, registry.getId(registry.getValueOrThrow(net.minecraft.world.item.JukeboxSongs.CAT)));
            });
            context.waitFor(client -> MusicDirector.get(client).jukeboxHolding());
            context.waitTicks(330);
            context.runOnClient(client -> check(!MusicDirector.get(client).active(), "jukebox already holds background silence"));
            context.runOnClient(client -> MusicDirector.get(client).preview().toggle(track("minecraft:records/pigstep")));
            var source = context.computeOnClient(CustomUiGameTest::previewSource);
            context.waitFor(client -> ClientSmokeGameTest.channelState(client, source, true).join());
            context.runOnClient(client -> check(ClientSmokeGameTest.channelGain(client, source).join() > 0, "preview audible over existing jukebox hold"));
            context.waitFor(client -> !MusicDirector.get(client).preview().active());
            context.waitTicks(4);
            context.runOnClient(client -> {
                check(!MusicDirector.get(client).active() && MusicDirector.get(client).jukeboxHolding(), "preview completion preserves jukebox hold");
                client.level.levelEvent(null, 1011, pos, 0);
                MusicDirector.get(client).next();
            });
            context.waitFor(client -> ClientSmokeGameTest.channelState(client, MusicDirector.get(client).current(), true).join());
            var background = context.computeOnClient(client -> MusicDirector.get(client).current());
            // A jukebox that begins during the preview must not briefly release the old song.
            context.runOnClient(client -> {
                MusicDirector.get(client).preview().toggle(track("minecraft:records/pigstep"));
                var registry = client.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.JUKEBOX_SONG);
                client.level.levelEvent(null, 1010, pos, registry.getId(registry.getValueOrThrow(net.minecraft.world.item.JukeboxSongs.CAT)));
            });
            var canceled = context.computeOnClient(CustomUiGameTest::previewSource);
            context.waitFor(client -> MusicDirector.get(client).jukeboxHolding());
            context.runOnClient(client -> MusicDirector.get(client).preview().cancel());
            context.waitTicks(3);
            context.runOnClient(client -> {
                check(ClientSmokeGameTest.channelState(client, background, false).join(), "jukebox beginning during preview keeps same background paused");
                client.level.levelEvent(null, 1011, pos, 0);
            });
            context.waitFor(client -> ClientSmokeGameTest.channelState(client, background, true).join());
            context.waitFor(client -> !channels(client).containsKey(canceled));
        }
    }

    private static void selectPreview(ClientGameTestContext context, String id) {
        context.runOnClient(client -> editorLists(client).get(1).selectSong(id));
        context.waitTicks(2);
        press(context, "▶ Preview", 0);
    }

    private static xyz.pyrehaven.whostoppedthemusic.music.Track track(String id) {
        return WhoStoppedTheMusic.LIBRARY.tracks().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
    }

    private static Object field(Object owner, String name) {
        try {
            var field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static java.util.Map<net.minecraft.client.resources.sounds.SoundInstance, net.minecraft.client.sounds.ChannelAccess.ChannelHandle> channels(Minecraft client) {
        var engine = ((xyz.pyrehaven.whostoppedthemusic.mixin.SoundManagerAccessor) client.getSoundManager()).whostoppedthemusic$engine();
        return ((xyz.pyrehaven.whostoppedthemusic.mixin.SoundEngineAccessor) engine).whostoppedthemusic$channels();
    }

    private static net.minecraft.client.resources.sounds.SoundInstance previewSource(Minecraft client) {
        return (net.minecraft.client.resources.sounds.SoundInstance) field(MusicDirector.get(client).preview(), "source");
    }

    private static <T> T onSource(Minecraft client, net.minecraft.client.resources.sounds.SoundInstance sound,
                                   java.util.function.Function<com.mojang.blaze3d.audio.Channel, T> read) {
        var result = new java.util.concurrent.CompletableFuture<T>();
        var handle = channels(client).get(sound);
        check(handle != null && !handle.isStopped(), "native source allocated");
        handle.execute(channel -> {
            try { result.complete(read.apply(channel)); }
            catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS).join();
    }

    private static int sampleOffset(Minecraft client, net.minecraft.client.resources.sounds.SoundInstance sound) {
        return onSource(client, sound, channel -> {
            int source = (int) field(channel, "source");
            check(org.lwjgl.openal.AL10.alGetError() == 0, "clean native boundary before sample offset source=" + source);
            int offset = org.lwjgl.openal.AL10.alGetSourcei(source, org.lwjgl.openal.AL11.AL_SAMPLE_OFFSET);
            check(org.lwjgl.openal.AL10.alGetError() == 0, "sample offset native query source=" + source);
            return offset;
        });
    }

    private static Object nativeStream(Minecraft client, net.minecraft.client.resources.sounds.SoundInstance sound) {
        return onSource(client, sound, channel -> field(channel, "stream"));
    }

    private static List<Object> deckState(Minecraft client) {
        var deck = MusicDirector.get(client).customPlayback().deck();
        return List.of(List.copyOf((List<?>) field(deck, "history")), List.copyOf((List<?>) field(deck, "cards")), field(deck, "cursor"));
    }

    static void run(ClientGameTestContext context) {
        context.getInput().resizeWindow(960, 720);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            WhoStoppedTheMusic.settings().setMode(Mode.VANILLA);
        });
        context.setScreen(() -> new MusicScreen(null, WhoStoppedTheMusic.settings()));
        settingTooltips(context, false);
        context.clickScreenButton("whostoppedthemusic.mode.vanilla");
        settingTooltips(context, false);
        check(context.computeOnClient(client -> WhoStoppedTheMusic.settings().mode()) == Mode.BASIC, "Vanilla cycles to Basic");
        var original = context.computeOnClient(client -> client.options.musicFrequency().get());
        press(context, "Frequency", 0);
        check(context.computeOnClient(client -> client.options.musicFrequency().get()) != original, "Basic still changes vanilla frequency");
        addLink(context);
        context.clickScreenButton("whostoppedthemusic.mode.basic");
        check(context.computeOnClient(client -> WhoStoppedTheMusic.settings().mode()) == Mode.CUSTOM, "Basic cycles to Custom");
        winner(context, ScenarioId.special(Special.MENU));
        context.waitTicks(3);
        settingTooltips(context, true);
        press(context, "Frequency (Menu)", 0);
        popupCancels(context, "Frequency (Menu)");
        addLink(context);
        context.clickScreenButton("whostoppedthemusic.mode.custom");
        check(context.computeOnClient(client -> WhoStoppedTheMusic.settings().mode()) == Mode.VANILLA, "Custom cycles to Vanilla");
        context.runOnClient(client -> {
            WhoStoppedTheMusic.settings().setMode(Mode.CUSTOM);
            WhoStoppedTheMusic.settings().applyFrequencyToAll(ScenarioConfig.Frequency.CONSTANT);
            WhoStoppedTheMusic.settings().applyJukeboxToAll(JukeboxSetting.PAUSE_MUSIC);
            client.options.musicFrequency().set(MusicFrequency.DEFAULT);
            client.gui.setScreen(null);
        });
        try (var world = context.worldBuilder().create()) {
            context.waitForScreen(null);
            var server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a 0 100 0");
            server.runCommand("fill -8 99 -8 8 99 8 stone");
            server.runCommand("weather thunder");
            server.runCommand("fillbiome -8 96 -8 8 112 8 minecraft:swamp");
            var swamp = ScenarioId.biome("minecraft:swamp");
            var thunder = ScenarioId.special(Special.THUNDER);
            var rain = ScenarioId.special(Special.RAIN);
            context.waitFor(client -> SituationReader.read(client).biome().equals(swamp));
            context.runOnClient(client -> {
                var p = MusicDirector.get(client).customPlayback();
                for (var special : List.of(Special.UNDERGROUND, Special.THUNDER, Special.RAIN, Special.SNOW)) {
                    var id = ScenarioId.special(special);
                    WhoStoppedTheMusic.settings().setScenario(id, p.defaults(id).config(), p.defaults(id).config());
                }
            });
            winner(context, swamp);
            context.waitFor(client -> SituationReader.read(client).thundering() && SituationReader.read(client).raining());
            context.setScreen(() -> new MusicScreen(null, WhoStoppedTheMusic.settings()));
            context.waitTicks(5);
            context.runOnClient(client -> {
                client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.7);
                client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MUSIC).set(0.7);
                MusicDirector.get(client).next();
            });
            context.waitFor(client -> ClientSmokeGameTest.channelState(client, MusicDirector.get(client).current(), true).join());
            var source = context.computeOnClient(client -> MusicDirector.get(client).current());
            press(context, "Pause", 0);
            context.runOnClient(client -> check(MusicDirector.get(client).paused(), "shared Custom Pause holds channel"));
            press(context, "Resume", 0);
            context.waitFor(client -> ClientSmokeGameTest.channelState(client, source, true).join());
            press(context, "Next", 0);
            context.waitFor(client -> ClientSmokeGameTest.channelState(client, MusicDirector.get(client).current(), true).join());
            press(context, "Previous", 0);
            context.waitFor(client -> MusicDirector.get(client).active());
            sections(context);
            fit(context);
            context.takeScreenshot("custom-main-normal");
            smoothWheel(context, "scenario browser");
            search(context, "thunder");
            context.takeScreenshot("custom-search-thunder");
            context.runOnClient(client -> {
                check(list(client).children().size() == 2, "search matches special plus header");
                var toggle = list(client).children().get(1).children().stream().filter(Toggle.class::isInstance)
                        .map(Button.class::cast).findFirst().orElseThrow();
                click(client, toggle, 0);
                var p = MusicDirector.get(client).customPlayback();
                check(WhoStoppedTheMusic.settings().scenario(thunder, p.defaults(thunder).config()).active(),
                        "toggle click enables thunder at " + toggle.getRectangle() + "; screen=" + client.gui.screen().getClass());
            });
            winner(context, thunder);
            context.runOnClient(client -> {
                var p = MusicDirector.get(client).customPlayback();
                for (var id : List.of(thunder, rain)) {
                    var d = p.defaults(id).config();
                    WhoStoppedTheMusic.settings().setScenario(id, new ScenarioConfig(7, true, true,
                            ScenarioConfig.Frequency.CONSTANT, JukeboxSetting.PAUSE_MUSIC, true, 64, List.of()), d);
                }
                client.getMusicManager().stopPlaying();
            });
            context.waitFor(client -> MusicDirector.get(client).customPlayback().resolution().winners().size() == 2);
            context.waitTicks(5);
            press(context, "Frequency (Rain + Thunder)", 0);
            press(context, "Juke box (Rain + Thunder)", 0);
            context.runOnClient(client -> {
                var p = MusicDirector.get(client).customPlayback();
                check(p.resolution().silent(), "empty tied winners stay silent");
                for (var id : List.of(thunder, rain)) {
                    var c = WhoStoppedTheMusic.settings().scenario(id, p.defaults(id).config());
                    check(c.frequency() == ScenarioConfig.Frequency.DEFAULT && c.jukebox() == JukeboxSetting.PLAY_OVER, "main edits every tied silent winner");
                }
                check(rowButtons(client).stream().anyMatch(b -> b.getMessage().getString().contains("playing")), "playing row state");
                var d = p.defaults(swamp).config();
                WhoStoppedTheMusic.settings().setScenario(swamp, new ScenarioConfig(d.priority(), d.active(), d.interrupt(),
                        ScenarioConfig.Frequency.FREQUENT, JukeboxSetting.PAUSE_MUSIC, d.mustBeOutside(), d.yLevel(), List.of()), d);
                var absent = ScenarioId.biome("removed:grove");
                WhoStoppedTheMusic.settings().discoverBiomes(java.util.Map.of(absent.id(), "removed"));
                WhoStoppedTheMusic.settings().setScenario(absent, d.withFrequency(ScenarioConfig.Frequency.CONSTANT), d);
            });
            popupCancels(context, "Juke box (Rain + Thunder)");
            press(context, "Frequency (Rain + Thunder)", 1);
            context.waitForScreen(ApplyToAllPopup.class);
            context.takeScreenshot("custom-apply-popup");
            context.clickScreenButton("gui.yes");
            press(context, "Juke box (Rain + Thunder)", 1);
            context.clickScreenButton("gui.yes");
            context.runOnClient(client -> {
                var loaded = Settings.load(configFile());
                var p = MusicDirector.get(client).customPlayback();
                var ids = new java.util.ArrayList<ScenarioId>();
                for (var special : Special.values()) ids.add(ScenarioId.special(special));
                p.catalog().entries().forEach(e -> ids.add(e.id()));
                ids.add(ScenarioId.biome("removed:grove"));
                loaded.discoverBiomes(java.util.Map.of("future:grove", "future"));
                ids.add(ScenarioId.biome("future:grove"));
                for (var id : ids) {
                    var c = loaded.scenario(id, p.defaults(swamp).config());
                    check(c.frequency() == ScenarioConfig.Frequency.DEFAULT && c.jukebox() == JukeboxSetting.PLAY_OVER,
                            "Yes survives disk reload for loaded, absent/future scenario: " + id);
                }
                check(client.options.musicFrequency().get() == MusicFrequency.DEFAULT, "Custom never writes vanilla frequency");
            });
            search(context, "swamp");
            context.runOnClient(client -> check(rowButtons(client).stream().anyMatch(b -> b.getMessage().getString().contains("edited")), "edited list marker"));
            server.runCommand("fillbiome -8 96 -8 8 112 8 minecraft:forest");
            editor(context);
            context.getInput().resizeWindow(320, 240);
            context.runOnClient(client -> client.options.guiScale().set(1));
            context.waitTicks(5);
            fit(context);
            context.getInput().setCursorPos(0, 0);
            context.takeScreenshot("custom-main-small");
            context.runOnClient(client -> {
                check(((EditBox) client.gui.screen().children().getFirst()).getValue().equals("swamp"), "query retained on resize");
                click(client, rowButtons(client).stream().filter(b -> b.getMessage().getString().startsWith("Swamp")).findFirst().orElseThrow(), 0);
            });
            context.waitForScreen(ScenarioScreen.class);
            popupCancels(context, "Frequency:");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitForScreen(MusicScreen.class);
        }
        org.slf4j.LoggerFactory.getLogger("whostoppedthemusic-test").info("CUSTOM UI PASS: collapsed ordered clickable biome/Misc sections, search expansion/restoration, minimal mode-aware tooltips, three-mode cycle, Basic vanilla frequency, both Add routes, registry/search/toggle, tied silence labels/actions, playing/edited rows, normal/narrow bounds/query retention, shared editor controls, popup no/Esc/outside exact-byte cancel and Yes disk/absent/future defaults");
    }

    private static void settingTooltips(ClientGameTestContext context, boolean custom) {
        context.runOnClient(client -> {
            var buttons = client.gui.screen().children().stream()
                    .filter(xyz.pyrehaven.whostoppedthemusic.ui.widget.SettingButton.class::isInstance)
                    .map(AbstractWidget.class::cast).toList();
            check(buttons.size() == 2, "frequency and jukebox settings present");
            for (var button : buttons) {
                net.minecraft.client.gui.components.Tooltip tooltip;
                try {
                    var holder = AbstractWidget.class.getDeclaredField("tooltip");
                    holder.setAccessible(true);
                    tooltip = ((net.minecraft.client.gui.components.WidgetTooltipHolder) holder.get(button)).get();
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                var message = (net.minecraft.network.chat.Component) field(tooltip, "message");
                check(message.getString().equals(button.getMessage().getString()
                        + (custom ? "\nRight-click to apply to every scenario." : "")), "minimal tooltip for " + button.getMessage().getString());
            }
        });
    }

    private static void sections(ClientGameTestContext context) {
        var titles = context.computeOnClient(client -> {
            var expected = new java.util.ArrayList<>(List.of("Overworld biomes", "Nether biomes", "End biomes"));
            MusicDirector.get(client).customPlayback().catalog().entries().stream().map(BiomeCatalog.Entry::group)
                    .filter(group -> !List.of("overworld", "nether", "end").contains(group)).distinct().sorted()
                    .forEach(group -> expected.add(group + " biomes"));
            expected.add("Misc");
            return expected;
        });
        context.runOnClient(client -> check(rowButtons(client).stream().map(b -> b.getMessage().getString()).toList()
                .equals(titles.stream().map(title -> "▶ " + title).toList()),
                "all sections collapsed in dimension/namespace order with Misc last: " + rowButtons(client).stream().map(b -> b.getMessage().getString()).toList()));
        context.takeScreenshot("custom-sections-collapsed");
        for (String title : titles) {
            context.runOnClient(client -> click(client, rowButtons(client).stream()
                    .filter(b -> b.getMessage().getString().equals("▶ " + title)).findFirst().orElseThrow(), 0));
            context.waitTicks(2);
            context.runOnClient(client -> {
                check(list(client).children().size() > titles.size(), title + " expands");
                click(client, rowButtons(client).stream().filter(b -> b.getMessage().getString().equals("▼ " + title)).findFirst().orElseThrow(), 0);
                check(list(client).children().size() == titles.size(), title + " collapses");
            });
            context.waitTicks(2);
        }
        search(context, "thunder");
        context.runOnClient(client -> {
            check(list(client).children().size() == 2, "search reveals matches inside collapsed Misc");
            click(client, rowButtons(client).getFirst(), 0);
            check(list(client).children().size() == 1, "search header can collapse");
        });
        context.waitTicks(2);
        context.runOnClient(client -> click(client, rowButtons(client).getFirst(), 0));
        context.waitTicks(2);
        context.runOnClient(client -> check(list(client).children().size() == 2, "search header can reopen"));
        search(context, "");
        context.runOnClient(client -> {
            check(list(client).children().size() == titles.size(), "clearing search restores collapsed state");
            click(client, rowButtons(client).getFirst(), 0);
        });
        context.waitTicks(2);
    }

    private static void smoothWheel(ClientGameTestContext context, String consumer) {
        var list = context.computeOnClient(CustomUiGameTest::list);
        double target = Math.min(Theme.SCENARIO_ROW * 3.0, context.computeOnClient(client -> list.maxScrollAmount()));
        check(target > 0, consumer + " has scrollable content");
        context.runOnClient(client -> {
            list.setScrollAmount(0);
            check(list.mouseScrolled(list.getX() + 4, list.getY() + 4, 0, -6), consumer + " handles wheel");
            check(list.scrollAmount() == 0, consumer + " wheel sets target without immediate jump");
        });
        context.waitFor(client -> list.scrollAmount() > 0);
        context.runOnClient(client -> check(list.scrollAmount() < target, consumer + " renders a live intermediate position"));
        context.waitFor(client -> Math.abs(list.scrollAmount() - target) < Theme.SCROLL_EPSILON);
        context.runOnClient(client -> list.setScrollAmount(0));
        org.slf4j.LoggerFactory.getLogger("whostoppedthemusic-test").info("SMOOTH SCENARIO PASS: {} intermediate and settled target {}", consumer, target);
    }

    private static ScenarioConfig config(Minecraft client, ScenarioId id) {
        return WhoStoppedTheMusic.settings().scenario(id, MusicDirector.get(client).customPlayback().defaults(id).config());
    }

    private static void editor(ClientGameTestContext context) {
        var forest = ScenarioId.biome("minecraft:forest");
        var plains = ScenarioId.biome("minecraft:plains");
        context.waitFor(client -> SituationReader.read(client).biome().equals(forest));
        context.runOnClient(client -> {
            var folder = client.gameDirectory.toPath().resolve("music");
            try {
                Files.createDirectories(folder);
                for (var entry : java.util.Map.of("stereo.mp3", "Editor-check.mp3", "mono.ogg", "Editor-check.ogg").entrySet()) {
                    try (var input = CustomUiGameTest.class.getResourceAsStream("/audio/" + entry.getKey())) {
                        Files.copy(input, folder.resolve(entry.getValue()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                Files.copy(folder.resolve("Editor-check.mp3"), folder.resolve("Hover-check cassette title longer than the library window.mp3"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            var settings = WhoStoppedTheMusic.settings();
            var defaults = MusicDirector.get(client).customPlayback().defaults(forest).config();
            settings.setScenario(forest, defaults.withSongs(List.of()).withPriority(9).withInterrupt(true), defaults);
        });
        var refresh = context.computeOnClient(client -> WhoStoppedTheMusic.LIBRARY.refreshSongs());
        context.waitFor(client -> refresh.isDone());
        var parent = context.computeOnClient(client -> client.gui.screen());
        context.setScreen(() -> new ScenarioScreen(parent, WhoStoppedTheMusic.settings(), forest));
        context.waitTicks(4);
        settingTooltips(context, true);
        fit(context);
        hoverCase(context);
        drag(context, "Pigstep", true);
        context.runOnClient(client -> check(config(client, forest).songs().size() == 1 && config(client, forest).songs().getFirst().weight() == 10,
                "empty drag starts at 10"));
        var before = configBytes();
        drag(context, "Pigstep", true);
        check(Arrays.equals(before, configBytes()), "duplicate drop leaves exact disk bytes");
        winner(context, forest);
        context.runOnClient(client -> MusicDirector.get(client).next());
        context.waitFor(client -> MusicDirector.get(client).playing(WhoStoppedTheMusic.LIBRARY.tracks().stream()
                .filter(t -> t.id().endsWith("/pigstep")).findFirst().orElseThrow())
                && ClientSmokeGameTest.channelState(client, MusicDirector.get(client).current(), true).join());
        drag(context, "Sweden", false);
        context.runOnClient(client -> check(config(client, forest).songs().size() == 1, "outside drop changes nothing"));
        drag(context, "Sweden", true);
        sliderKey(context, InputConstants.KEY_END);
        context.runOnClient(client -> check(config(client, forest).songs().getLast().weight() == 100, "slider End reaches 100 on focused added song"));
        sliderKey(context, InputConstants.KEY_HOME);
        context.runOnClient(client -> check(config(client, forest).songs().getLast().weight() == 1, "slider Home reaches 1"));
        context.runOnClient(client -> check(client.gui.screen().children().stream().filter(xyz.pyrehaven.whostoppedthemusic.ui.widget.WeightSlider.class::isInstance)
                .map(AbstractWidget.class::cast).findFirst().orElseThrow().getMessage().getString().contains("1 in 11 (9.1%)"), "focused chance uses this list's weight total"));
        context.takeScreenshot("editor-forest-normal");
        press(context, "Remove", 0);
        press(context, "Remove", 0);
        context.runOnClient(client -> client.getMusicManager().stopPlaying());
        context.waitFor(client -> MusicDirector.get(client).customPlayback().resolution().silent());
        context.runOnClient(client -> check(config(client, forest).songs().isEmpty(), "remove last produces empty silence"));
        press(context, "Reset to vanilla", 0);
        context.runOnClient(client -> {
            var c = config(client, forest);
            var d = MusicDirector.get(client).customPlayback().defaults(forest).config();
            check(c.songs().equals(d.songs()) && c.priority() == 9 && c.interrupt(), "reset restores songs only");
            check(!WhoStoppedTheMusic.settings().songsEdited(forest, d), "reset clears edited marker");
            WhoStoppedTheMusic.settings().setScenario(plains, config(client, plains).withSongs(List.of(
                    new xyz.pyrehaven.whostoppedthemusic.music.Deck.Entry("minecraft:records/pigstep", 7))),
                    MusicDirector.get(client).customPlayback().defaults(plains).config());
        });
        press(context, "Copy songs", 0);
        search(context, "biomes");
        smoothWheel(context, "copy picker");
        context.takeScreenshot("editor-copy-picker-normal");
        before = configBytes();
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        check(Arrays.equals(before, configBytes()), "copy cancel leaves bytes unchanged");
        press(context, "Copy songs", 0);
        search(context, "plains");
        context.runOnClient(client -> check(list(client).children().stream().flatMap(r -> r.children().stream()).noneMatch(Toggle.class::isInstance), "copy picker omits toggles"));
        context.waitTicks(3);
        context.runOnClient(client -> click(client, rowButtons(client).stream().filter(b -> b.getMessage().getString().startsWith("Plains")).findFirst().orElseThrow(), 0));
        context.waitForScreen(ScenarioScreen.class);
        context.runOnClient(client -> check(config(client, forest).songs().equals(config(client, plains).songs())
                && config(client, forest).priority() == 9 && config(client, forest).interrupt(), "copy replaces songs and weights only"));
        context.runOnClient(client -> check(editorLists(client).get(1).children().size() == 1
                && editorLists(client).get(1).focusedId().equals("minecraft:records/pigstep"), "picker return refreshes retained cassette rows and focus"));
        drag(context, "Sweden", true);
        context.runOnClient(client -> check(config(client, forest).songs().getLast().weight() == 7, "nonempty drag uses modal weight"));
        for (String extension : List.of("mp3", "ogg")) {
            drag(context, "Editor-check." + extension, true);
            // Search includes IDs; the literal file name is retained in the entry.
            context.runOnClient(client -> {
                var track = WhoStoppedTheMusic.LIBRARY.tracks().stream().filter(t -> t.id().equals("custom:Editor-check." + extension)).findFirst().orElseThrow();
                check(config(client, forest).songs().stream().anyMatch(e -> e.id().equals(track.id())), "own song can be dragged: " + extension);
                var d = MusicDirector.get(client).customPlayback().defaults(forest).config();
                WhoStoppedTheMusic.settings().setScenario(forest, config(client, forest).withSongs(List.of(new xyz.pyrehaven.whostoppedthemusic.music.Deck.Entry(track.id(), 7))), d);
            });
            context.waitTicks(3);
            context.runOnClient(client -> MusicDirector.get(client).next());
            context.waitFor(client -> ClientSmokeGameTest.channelState(client, MusicDirector.get(client).current(), true).join()
                    && MusicDirector.get(client).playing(WhoStoppedTheMusic.LIBRARY.tracks().stream().filter(t -> t.id().equals("custom:Editor-check." + extension)).findFirst().orElseThrow()));
        }
        context.runOnClient(client -> {
            var d = MusicDirector.get(client).customPlayback().defaults(forest).config();
            WhoStoppedTheMusic.settings().setScenario(forest, config(client, forest).withSongs(List.of(
                    new xyz.pyrehaven.whostoppedthemusic.music.Deck.Entry("absent:music/gone", 25))), d);
        });
        context.waitTicks(4);
        context.runOnClient(client -> check(editorLists(client).get(1).children().stream().flatMap(r -> r.children().stream()).filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast).anyMatch(w -> w.getMessage().getString().contains("Missing")), "missing cassette retained and marked"));
        context.takeScreenshot("editor-missing");
        popupCancels(context, "Frequency:");
        press(context, "Frequency:", 0);
        press(context, "Jukebox:", 0);
        context.runOnClient(client -> {
            var c = config(client, forest);
            check(c.frequency() == ScenarioConfig.Frequency.DEFAULT && c.jukebox() == JukeboxSetting.PLAY_OVER, "editor cycles its fixture's Constant/Pause settings to Default/Play over");
            check(client.options.musicFrequency().get() == MusicFrequency.DEFAULT, "editor leaves global frequency unchanged");
            var thunder = ScenarioId.special(Special.THUNDER);
            var d = MusicDirector.get(client).customPlayback().defaults(thunder).config();
            WhoStoppedTheMusic.settings().setScenario(thunder, config(client, thunder).withSongs(List.of(
                    new xyz.pyrehaven.whostoppedthemusic.music.Deck.Entry("minecraft:music/game/sweden", 20),
                    new xyz.pyrehaven.whostoppedthemusic.music.Deck.Entry("minecraft:records/pigstep", 30),
                    new xyz.pyrehaven.whostoppedthemusic.music.Deck.Entry("custom:Editor-check.mp3", 50))), d);
        });
        for (int scale : List.of(2, 1, 4)) {
            boolean narrow = scale == 1;
            String suffix = scale == 4 ? "-scale4" : narrow ? "-small" : "-normal";
            context.getInput().resizeWindow(scale == 4 ? 1280 : narrow ? 320 : 960, scale == 4 ? 960 : narrow ? 240 : 720);
            context.runOnClient(client -> client.options.guiScale().set(scale));
            for (var special : Special.values()) {
                context.setScreen(() -> new ScenarioScreen(parent, WhoStoppedTheMusic.settings(), ScenarioId.special(special)));
                context.waitTicks(3);
                if (special == Special.THUNDER) {
                    context.runOnClient(client -> editorLists(client).get(1).selectSong("minecraft:records/pigstep"));
                    context.waitTicks(3);
                }
                fit(context);
                context.runOnClient(client -> check(client.getWindow().getGuiScale() == scale, "actual GUI scale " + scale));
                context.runOnClient(client -> {
                    long toggles = client.gui.screen().children().stream().filter(Toggle.class::isInstance).count();
                    boolean fixed = special == Special.MENU || special == Special.CREDITS;
                    boolean weather = List.of(Special.THUNDER, Special.RAIN, Special.SNOW).contains(special);
                    check(toggles == (fixed ? 0 : weather ? 3 : 2), "variant controls: " + special);
                    if (special == Special.UNDERGROUND) {
                        var level = client.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                        level.setValue("-256");
                        check(config(client, ScenarioId.special(special)).yLevel() == -256, "negative whole Y level");
                        level.setValue("bad");
                        check(config(client, ScenarioId.special(special)).yLevel() == -256, "invalid Y is not persisted");
                    }
                    if (!fixed) {
                        var id = ScenarioId.special(special);
                        var previous = config(client, id);
                        var togglesList = client.gui.screen().children().stream().filter(Toggle.class::isInstance).map(AbstractWidget.class::cast).toList();
                        click(client, togglesList.getFirst(), 0);
                        check(config(client, id).active() != previous.active(), "active editor toggle");
                        click(client, togglesList.get(1), 0);
                        check(config(client, id).interrupt() != previous.interrupt(), "interrupt editor toggle");
                        if (weather) {
                            click(client, togglesList.get(2), 0);
                            check(config(client, id).mustBeOutside() != previous.mustBeOutside(), "outside editor toggle");
                        }
                        var plus = client.gui.screen().children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                                .filter(w -> w.getMessage().getString().equals("+")).findFirst().orElseThrow();
                        var minus = client.gui.screen().children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                                .filter(w -> w.getMessage().getString().equals("−")).findFirst().orElseThrow();
                        for (int i = 0; i < 12; i++) click(client, plus, 0);
                        check(config(client, id).priority() == 10, "priority ceiling");
                        for (int i = 0; i < 12; i++) click(client, minus, 0);
                        check(config(client, id).priority() == 1, "priority floor");
                    }
                });
                if (scale == 4 || special == Special.THUNDER || special == Special.UNDERGROUND || special == Special.MENU)
                    context.takeScreenshot("editor-" + special.name().toLowerCase(java.util.Locale.ROOT) + suffix);
            }
            if (scale == 4) {
                context.setScreen(() -> new ScenarioScreen(parent, WhoStoppedTheMusic.settings(), forest));
                context.waitTicks(3);
                fit(context);
                context.takeScreenshot("editor-forest-scale4");
                press(context, "Copy songs", 0);
                fit(context);
                search(context, "biomes");
                smoothWheel(context, "copy picker scale4");
                context.takeScreenshot("editor-copy-picker-scale4");
                search(context, "plains");
                context.takeScreenshot("editor-copy-picker-search-scale4");
                context.getInput().pressKey(InputConstants.KEY_ESCAPE);
                popupCancels(context, "Frequency:");
            }
        }
        context.getInput().resizeWindow(320, 240);
        context.runOnClient(client -> client.options.guiScale().set(1));
        context.setScreen(() -> new ScenarioScreen(parent, WhoStoppedTheMusic.settings(), forest));
        context.waitTicks(3);
        fit(context);
        context.takeScreenshot("editor-forest-small");
        context.runOnClient(client -> client.gui.screen().onClose());
        context.waitForScreen(MusicScreen.class);
        org.slf4j.LoggerFactory.getLogger("whostoppedthemusic-test").info("SCENARIO EDITOR PASS: drag/duplicate/outside/modal weights, focused 1–100, forest Pigstep and own MP3/OGG native playback, remove-last silence, songs-only reset/copy, missing retained, shared popup cancel, all biome/special variants normal/narrow/actual scale4 and copy-picker motion");
    }

    private static List<xyz.pyrehaven.whostoppedthemusic.ui.widget.TrackList> editorLists(Minecraft client) {
        return client.gui.screen().children().stream().filter(xyz.pyrehaven.whostoppedthemusic.ui.widget.TrackList.class::isInstance)
                .map(xyz.pyrehaven.whostoppedthemusic.ui.widget.TrackList.class::cast).toList();
    }

    private static void sliderKey(ClientGameTestContext context, int key) {
        context.runOnClient(client -> click(client, client.gui.screen().children().stream()
                .filter(xyz.pyrehaven.whostoppedthemusic.ui.widget.WeightSlider.class::isInstance).map(AbstractWidget.class::cast).findFirst().orElseThrow(), 0));
        context.getInput().pressKey(key);
        context.waitTicks(3);
    }

    private static void drag(ClientGameTestContext context, String query, boolean inside) {
        search(context, query);
        context.runOnClient(client -> {
            var lists = editorLists(client);
            var row = lists.getFirst().children().stream().flatMap(r -> r.children().stream()).filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast).filter(w -> w.getMessage().getString().toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT).replace(".mp3", "").replace(".ogg", "")))
                    .findFirst().orElseThrow(() -> new AssertionError("Missing drag row: " + query));
            click(client, row, 0);
        });
        boolean capture = query.equals("Pigstep") && inside && context.computeOnClient(client -> field(client.gui.screen(), "dragging") != null);
        if (capture) {
            context.getInput().setCursorPos(420, 320);
            context.waitTicks(2);
            var image = context.takeScreenshot("cassette-held-pigstep");
            context.runOnClient(client -> {
                var tape = (AbstractWidget) field(client.gui.screen(), "heldTape");
                var pixels = labelPixels(client, image, tape, false);
                check(Arrays.stream(pixels).noneMatch(p -> p == Theme.SPINDLE), "loose held tape has no red player rods");
                check(Arrays.stream(pixels).anyMatch(p -> p == Theme.GLOW), "held artwork contains reel edges, not just a title");
            });
        }
        context.runOnClient(client -> {
            var lists = editorLists(client);
            var target = lists.get(1);
            var event = new MouseButtonEvent(inside ? target.getX() + target.getWidth() / 2.0 : 0,
                    inside ? target.getY() + target.getHeight() / 2.0 : 0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
            client.gui.screen().mouseDragged(event, 1, 1);
            client.gui.screen().mouseReleased(event);
        });
        if (capture) {
            var arriving = context.takeScreenshot("cassette-inserting");
            context.waitTicks(8);
            var settled = context.takeScreenshot("cassette-inserted");
            context.runOnClient(client -> {
                var tape = editorLists(client).get(1).children().getFirst().children().stream()
                        .map(AbstractWidget.class::cast).findFirst().orElseThrow();
                check(!Arrays.equals(labelPixels(client, arriving, tape, false), labelPixels(client, settled, tape, false)),
                        "dropped tape visibly slides into the focused deck slot");
                check(field(client.gui.screen(), "dragging") == null, "release clears held tape");
            });
            org.slf4j.LoggerFactory.getLogger("whostoppedthemusic-test").info("CASSETTE DRAG PASS: held reel artwork has no red rods; accepted drop slides into focused slot and clears drag");
        }
        context.waitTicks(4);
    }

    private static void hoverCase(ClientGameTestContext context) {
        search(context, "Hover-check");
        context.getInput().setCursorPos(0, 0);
        context.waitTicks(3);
        var row = context.computeOnClient(client -> editorLists(client).getFirst().children().stream()
                .flatMap(r -> r.children().stream()).map(AbstractWidget.class::cast).findFirst().orElseThrow());
        var first = context.takeScreenshot("case-idle-start");
        context.waitTicks(35);
        var idle = context.takeScreenshot("case-idle-later");
        context.runOnClient(client -> check(Arrays.equals(labelPixels(client, first, row, true), labelPixels(client, idle, row, true)),
                "overflowing case title stays pixel-identical without hover"));
        var scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
        context.getInput().setCursorPos((row.getX() + row.getWidth() / 2.0) * scale,
                (row.getY() + row.getHeight() / 2.0) * scale);
        context.waitTicks(3);
        var start = context.takeScreenshot("case-hover-start");
        context.waitTicks(35);
        var moving = context.takeScreenshot("case-hover-moving");
        context.runOnClient(client -> check(!Arrays.equals(labelPixels(client, start, row, true), labelPixels(client, moving, row, true)),
                "hovering moves overflowing case title"));
        context.getInput().setCursorPos(0, 0);
        context.waitTicks(2);
        var reset = context.takeScreenshot("case-leave-reset");
        context.runOnClient(client -> check(Arrays.equals(labelPixels(client, first, row, true), labelPixels(client, reset, row, true)),
                "leaving resets case title pixels immediately"));
        org.slf4j.LoggerFactory.getLogger("whostoppedthemusic-test").info("HOVER CASE PASS: stationary idle, hover-only motion, reset on leave using packaged renderer pixels");
    }

    private static int[] labelPixels(Minecraft client, Path path, AbstractWidget widget, boolean labelOnly) {
        try {
            var image = javax.imageio.ImageIO.read(path.toFile());
            double scale = client.getWindow().getGuiScale();
            int inset = labelOnly ? Theme.CASE_SPINE + Theme.LABEL_PADDING : 0;
            int x = (int) ((widget.getX() + inset) * scale);
            int y = (int) ((widget.getY() + (labelOnly ? Theme.ACCENT : 0)) * scale);
            int w = (int) ((widget.getWidth() - inset - (labelOnly ? Theme.GAP + Theme.LABEL_PADDING : 0)) * scale);
            int h = (int) ((widget.getHeight() - (labelOnly ? Theme.ACCENT * 2 : 0)) * scale);
            return image.getRGB(x, y, w, h, null, 0, w);
        } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }

    private static void addLink(ClientGameTestContext context) {
        context.clickScreenButton("whostoppedthemusic.add_music");
        context.waitForScreen(AddSongsScreen.class);
        context.waitFor(client -> client.gui.screen().children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast).anyMatch(w -> w.active && w.getMessage().getString().equals("Done")));
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(MusicScreen.class);
    }

    private static Path configFile() { return FabricLoader.getInstance().getConfigDir().resolve("whostoppedthemusic.json"); }
    private static byte[] configBytes() {
        try { return Files.readAllBytes(configFile()); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }

    private static void popupCancels(ClientGameTestContext context, String label) {
        for (int cancel = 0; cancel < 3; cancel++) {
            var before = configBytes();
            press(context, label, 1);
            context.waitForScreen(ApplyToAllPopup.class);
            check(Arrays.equals(before, configBytes()), "right-click leaves exact disk bytes");
            if (cancel == 0) context.clickScreenButton("gui.no");
            else if (cancel == 1) context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            else context.runOnClient(client -> client.gui.screen().mouseClicked(new MouseButtonEvent(0, 0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false));
            check(Arrays.equals(before, configBytes()), "cancel leaves exact disk bytes");
        }
    }

    private static void press(ClientGameTestContext context, String prefix, int button) {
        context.runOnClient(client -> {
            var widget = client.gui.screen().children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                    .filter(w -> w.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow(() -> new AssertionError("Missing control: " + prefix));
            click(client, widget, button);
        });
        context.waitTicks(2);
    }

    private static void click(Minecraft client, AbstractWidget widget, int button) {
        check(client.gui.screen().mouseClicked(new MouseButtonEvent(widget.getX() + widget.getWidth() / 2.0,
                widget.getY() + widget.getHeight() / 2.0,
                new MouseButtonInfo(button == 0 ? InputConstants.MOUSE_BUTTON_LEFT : InputConstants.MOUSE_BUTTON_RIGHT, 0)), false), "click handled");
    }

    private static ScenarioList list(Minecraft client) {
        return client.gui.screen().children().stream().filter(ScenarioList.class::isInstance).map(ScenarioList.class::cast).findFirst().orElseThrow();
    }

    private static List<Button> rowButtons(Minecraft client) {
        return list(client).children().stream().flatMap(r -> r.children().stream()).filter(Button.class::isInstance)
                .map(Button.class::cast).filter(b -> !(b instanceof Toggle)).toList();
    }

    private static void search(ClientGameTestContext context, String value) {
        context.runOnClient(client -> client.gui.screen().children().stream().filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).findFirst().orElseThrow().setValue(value));
        context.waitTicks(3);
    }

    private static void winner(ClientGameTestContext context, ScenarioId id) {
        context.waitFor(client -> MusicDirector.get(client).customPlayback().resolution() != null
                && MusicDirector.get(client).customPlayback().resolution().winners().equals(List.of(id)));
    }

    private static void fit(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var screen = client.gui.screen();
            var widgets = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
            for (var w : widgets) check(w.getX() >= 0 && w.getY() >= 0 && w.getRight() <= screen.width && w.getBottom() <= screen.height
                    && w.getWidth() > 0 && w.getHeight() > 0, "widget fits: " + w.getMessage().getString());
            for (int i = 0; i < widgets.size(); i++) for (int j = i + 1; j < widgets.size(); j++) {
                var a = widgets.get(i); var b = widgets.get(j);
                check(a.getRight() <= b.getX() || b.getRight() <= a.getX() || a.getBottom() <= b.getY() || b.getBottom() <= a.getY(), "widgets do not overlap");
            }
        });
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
