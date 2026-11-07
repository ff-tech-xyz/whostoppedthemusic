package xyz.pyrehaven.whostoppedthemusic;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.MusicToastDisplayState;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.sounds.MusicManager.MusicFrequency;
import net.minecraft.sounds.Musics;
import net.minecraft.sounds.SoundSource;
import org.slf4j.LoggerFactory;
import xyz.pyrehaven.whostoppedthemusic.mixin.MusicManagerAccessor;
import xyz.pyrehaven.whostoppedthemusic.ui.CassetteWidget;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicScreen;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicVolumeSlider;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.mixin.SoundManagerAccessor;
import xyz.pyrehaven.whostoppedthemusic.mixin.SoundEngineAccessor;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.CompletableFuture;

public final class ClientSmokeGameTest implements FabricClientGameTest, ClientModInitializer {
    private static volatile boolean firstRunObserved;
    public static volatile CompletableFuture<Void> streamGate;
    public static volatile CompletableFuture<Void> streamReady;

    @Override
    public void onInitializeClient() {
        // Entrypoint order is not guaranteed. Check after all startup callbacks,
        // before the first tick starts gametests and resets the vanilla options.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> client.schedule(() -> {
            check(client.options.musicFrequency().get() == MusicFrequency.CONSTANT, "fresh launch sets Constant before test defaults");
            firstRunObserved = true;
        }));
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitForScreen(TitleScreen.class);
        check(firstRunObserved, "first-launch Constant was observed before gametest resets vanilla options");
        Path settingsFile = FabricLoader.getInstance().getConfigDir().resolve("whostoppedthemusic.json");
        check(Settings.load(settingsFile).firstRunDone(), "startup must finish first-run settings");
        try (var world = context.worldBuilder().create()) {
            context.waitForScreen(null);
            context.getInput().pressKey(InputConstants.KEY_M);
            context.waitForScreen(MusicScreen.class);
            context.takeScreenshot("whostoppedthemusic-vanilla");
            assertWidgetsFit(context);
            checkSharedControls(context, settingsFile);
            checkReboundKey(context);
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitForScreen(PauseScreen.class);
            checkCassette(context);
            checkTransport(context);
            context.getInput().resizeWindow(320, 240);
            context.waitTicks(5);
            assertWidgetsFit(context);
            clickWidget(context, context.computeOnClient(client -> cassette(client.gui.screen())));
            context.waitForScreen(MusicScreen.class);
            assertWidgetsFit(context);
            context.getInput().setCursorPos(0, 0);
            context.waitTicks(2);
            context.takeScreenshot("whostoppedthemusic-vanilla-small");
        }
        LoggerFactory.getLogger("whostoppedthemusic-test").info(
                "WHOSTOPPEDTHEMUSIC CLIENT PASS: first-run Constant, Vanilla UI, M/rebind, options/persistence, top cassette, live song/silence, Next/Previous/Pause/Resume, music-only volume, inert mode, 320x240");
    }

    private static void checkSharedControls(ClientGameTestContext context, Path settingsFile) {
        double master = context.computeOnClient(client -> client.options.getSoundSourceOptionInstance(SoundSource.MASTER).get());
        var toast = context.computeOnClient(client -> client.options.musicToast().get());
        clickWidget(context, widget(context, 1));
        context.runOnClient(client -> {
            check(client.gui.screen() instanceof MusicScreen, "Vanilla mode stays on the same screen");
            check(Screens.getWidgets(client.gui.screen()).get(1).getMessage().getString().equals("Vanilla"), "mode is inert Vanilla");
            check(Screens.getWidgets(client.gui.screen()).size() == 9, "only tape, mode, frequency, jukebox, transports, music volume and Done");
        });
        var frequency = context.computeOnClient(client -> client.options.musicFrequency().get());
        clickWidget(context, widget(context, 2));
        context.runOnClient(client -> check(client.options.musicFrequency().get() != frequency, "frequency widget updates vanilla"));
        double music = context.computeOnClient(client -> client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get());
        clickWidget(context, widget(context, 7));
        context.waitTicks(25);
        context.runOnClient(client -> check(client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get() != music,
                "music slider updates vanilla"));
        context.runOnClient(client -> {
            check(client.options.getSoundSourceOptionInstance(SoundSource.MASTER).get() == master, "music slider leaves Master unchanged");
            check(client.options.musicToast().get() == toast, "screen leaves toast preference unchanged");
        });
        JukeboxSetting previous = Settings.load(settingsFile).jukebox();
        clickWidget(context, widget(context, 3));
        check(Settings.load(settingsFile).jukebox() == previous.next(), "jukebox preference persists");
        context.clickScreenButton("gui.done");
        context.waitForScreen(null);
        context.runOnClient(client -> {
            MusicFrequency saved = client.options.musicFrequency().get();
            double volume = client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get();
            client.options.musicFrequency().set(saved == MusicFrequency.DEFAULT ? MusicFrequency.FREQUENT : MusicFrequency.DEFAULT);
            client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0);
            client.options.load();
            check(client.options.musicFrequency().get() == saved, "frequency survives option reload");
            check(client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get() == volume, "music survives option reload");
            Settings.load(settingsFile).completeFirstRun(() -> { throw new AssertionError("first run repeated"); });
        });
    }

    private static void checkReboundKey(ClientGameTestContext context) {
        context.runOnClient(client -> {
            KeyMapping.get("key.whostoppedthemusic.open").setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_N));
            KeyMapping.resetMapping();
        });
        context.getInput().pressKey(InputConstants.KEY_M);
        context.runOnClient(client -> check(client.gui.screen() == null, "old binding no longer opens screen"));
        context.getInput().pressKey(InputConstants.KEY_N);
        context.waitForScreen(MusicScreen.class);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(null);
        context.runOnClient(client -> {
            KeyMapping.get("key.whostoppedthemusic.open").setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_M));
            KeyMapping.resetMapping();
        });
    }

    private static void checkCassette(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.options.musicToast().set(MusicToastDisplayState.PAUSE_AND_TOAST);
            client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.7);
            client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.7);
            client.getMusicManager().stopPlaying();
            client.getMusicManager().startPlaying(Musics.MENU);
        });
        context.waitFor(client -> client.getMusicManager().getCurrentMusicTranslationKey() != null);
        context.waitTicks(10);
        context.runOnClient(client -> {
            check(!((PauseScreen) client.gui.screen()).rendersNowPlayingToast(), "cassette replaces duplicate pause song line");
            check(cassette(client.gui.screen()).getY() == 6, "cassette at top of pause menu");
            String key = client.getMusicManager().getCurrentMusicTranslationKey();
            check(cassette(client.gui.screen()).status().getString().equals(
                    net.minecraft.network.chat.Component.translatable(key.replace('/', '.')).getString()),
                    "cassette owns live vanilla song title");
        });
        context.takeScreenshot("whostoppedthemusic-cassette-vanilla-line");
        clickWidget(context, context.computeOnClient(client -> cassette(client.gui.screen())));
        context.waitForScreen(MusicScreen.class);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(PauseScreen.class);
        context.runOnClient(client -> {
            client.options.musicToast().set(MusicToastDisplayState.NEVER);
            String key = client.getMusicManager().getCurrentMusicTranslationKey();
            check(cassette(client.gui.screen()).status().getString().equals(
                    net.minecraft.network.chat.Component.translatable(key.replace('/', '.')).getString()), "cassette shows the real song");
            var instance = ((MusicManagerAccessor) client.getMusicManager()).whostoppedthemusic$currentMusic();
            check(client.getSoundManager().isActive(instance), "vanilla sound instance remains active");
        });
        context.takeScreenshot("whostoppedthemusic-cassette-song");
        context.runOnClient(client -> {
            client.getMusicManager().stopPlaying();
            check(cassette(client.gui.screen()).status().getString().equals(
                    net.minecraft.network.chat.Component.translatable("whostoppedthemusic.cassette.silence").getString()), "truthful silence");
        });
        context.takeScreenshot("whostoppedthemusic-cassette-silence");
    }

    private static void checkTransport(ClientGameTestContext context) {
        checkDelayedTransport(context);
        context.runOnClient(client -> {
            check(!new MusicDirector(client, new net.minecraft.client.sounds.MusicManager(client)).canPrevious(),
                    "Previous disabled without observed history");
            client.getMusicManager().startPlaying(Musics.MENU);
        });
        context.waitFor(client -> MusicDirector.get(client).active());
        var old = context.computeOnClient(client -> MusicDirector.get(client).current());
        context.waitFor(client -> channelState(client, old, true).join());
        var file = old.getSound().getLocation();
        var topNext = context.computeOnClient(client -> Screens.getWidgets(client.gui.screen()).stream()
                .filter(w -> w.getMessage().getString().equals(">") && w.getY() == 11).findFirst().orElseThrow());
        clickWidget(context, topNext);
        context.waitFor(client -> channelStopped(client, old));
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        var next = context.computeOnClient(client -> MusicDirector.get(client).current());
        context.runOnClient(client -> {
            var director = MusicDirector.get(client);
            check(director.current() != old, "top Next replaces actual sound instance");
            check(director.current().getIdentifier().equals(client.getSituationalMusic().sound().value().location()),
                    "Next uses the actual situational vanilla pool");
        });
        clickWidget(context, context.computeOnClient(client -> cassette(client.gui.screen())));
        context.waitForScreen(MusicScreen.class);
        clickWidget(context, widget(context, 4));
        context.waitFor(client -> channelStopped(client, next));
        var previous = context.computeOnClient(client -> MusicDirector.get(client).current());
        context.waitFor(client -> channelState(client, previous, true).join());
        context.runOnClient(client -> check(MusicDirector.get(client).current().getSound().getLocation().equals(file),
                "Previous restores the actual resolved historical song, not a random pool entry"));
        context.waitTicks(10);
        var music = context.computeOnClient(client -> MusicDirector.get(client).current());
        context.waitFor(client -> channelState(client, music, true).join());
        var record = context.computeOnClient(client -> {
            var sound = SimpleSoundInstance.forJukeboxSong(SoundEvent.createVariableRangeEvent(
                    Identifier.withDefaultNamespace("music_disc.cat")), Vec3.ZERO);
            client.getSoundManager().play(sound);
            return sound;
        });
        context.waitFor(client -> channelState(client, record, true).join());
        var effect = context.computeOnClient(client -> {
            var sound = new SimpleSoundInstance(Identifier.withDefaultNamespace("music_disc.cat"), SoundSource.AMBIENT,
                    1, 1, net.minecraft.util.RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
            client.getSoundManager().play(sound);
            return sound;
        });
        context.waitFor(client -> channelState(client, effect, true).join());
        clickWidget(context, widget(context, 5));
        context.waitFor(client -> channelState(client, music, false).join());
        context.runOnClient(client -> {
            check(MusicDirector.get(client).paused(), "pause holds the current vanilla instance");
            check(MusicDirector.get(client).status().getString().startsWith("Paused:"), "truthful paused label");
            client.getSoundManager().resume();
        });
        context.waitTicks(5);
        context.runOnClient(client -> {
            check(channelState(client, music, false).join(), "global menu resume cannot unpause held music");
            check(channelState(client, record, true).join(), "Pause never touches Records");
            check(channelState(client, effect, true).join(), "Pause never touches non-music effects");
        });
        context.getInput().setCursorPos(0, 0);
        context.waitTicks(2);
        context.takeScreenshot("whostoppedthemusic-paused");
        clickWidget(context, widget(context, 5));
        context.waitFor(client -> channelState(client, music, true).join());
        context.runOnClient(client -> check(MusicDirector.get(client).current() == music
                && !MusicDirector.get(client).paused(), "Resume continues the same channel, not a restart"));
        clickWidget(context, widget(context, 5));
        context.waitFor(client -> channelState(client, music, false).join());
        clickWidget(context, widget(context, 6));
        context.waitFor(client -> channelStopped(client, music));
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> {
            check(MusicDirector.get(client).current() != music, "screen Next starts actual next music");
            check(!MusicDirector.get(client).paused(), "Next clears the outgoing song's pause");
            check(channelState(client, record, true).join(), "Next never stops Records");
            check(channelState(client, effect, true).join(), "Next never stops non-music effects");
            client.getSoundManager().stop(record);
            client.getSoundManager().stop(effect);
        });
        context.getInput().setCursorPos(0, 0);
        context.waitTicks(2);
        context.takeScreenshot("whostoppedthemusic-transport");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(PauseScreen.class);
    }

    private static void checkDelayedTransport(ClientGameTestContext context) {
        var stopGate = new CompletableFuture<Void>();
        var old = context.computeOnClient(client -> {
            client.getMusicManager().stopPlaying();
            streamGate = stopGate;
            streamReady = new CompletableFuture<>();
            client.getMusicManager().startPlaying(Musics.MENU);
            return MusicDirector.get(client).current();
        });
        context.waitFor(client -> streamReady.isDone());
        context.runOnClient(client -> {
            check(streamGate == null, "vanilla stream startup is held before channel.play");
            MusicDirector.get(client).next();
            stopGate.complete(null);
        });
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        boolean stopped = context.computeOnClient(client -> channelStopped(client, old));
        var pauseGate = new CompletableFuture<Void>();
        var music = context.computeOnClient(client -> {
            client.getMusicManager().stopPlaying();
            streamGate = pauseGate;
            streamReady = new CompletableFuture<>();
            client.getMusicManager().startPlaying(Musics.MENU);
            return MusicDirector.get(client).current();
        });
        context.waitFor(client -> streamReady.isDone());
        context.runOnClient(client -> {
            MusicDirector.get(client).togglePause();
            pauseGate.complete(null);
        });
        context.waitTicks(20);
        boolean held = context.computeOnClient(client -> channelState(client, music, false).join());
        LoggerFactory.getLogger("whostoppedthemusic-test").info(
                "Delayed real-channel results: outgoingStopped={}, incomingPaused={}", stopped, held);
        check(stopped, "Next must stop the outgoing source even when its stream finishes after stop");
        check(held, "Pause before stream startup must hold the actual source paused");
        context.runOnClient(client -> MusicDirector.get(client).togglePause());
        context.waitFor(client -> channelState(client, music, true).join());
        context.runOnClient(client -> client.getMusicManager().stopPlaying());
        LoggerFactory.getLogger("whostoppedthemusic-test").info(
                "TRANSPORT CHANNEL PASS: delayed outgoing stream stopped; delayed incoming stream paused then resumed");
    }

    private static boolean channelStopped(net.minecraft.client.Minecraft client, SoundInstance sound) {
        var engine = ((SoundManagerAccessor) client.getSoundManager()).whostoppedthemusic$engine();
        var handle = ((SoundEngineAccessor) engine).whostoppedthemusic$channels().get(sound);
        if (handle == null || handle.isStopped()) {
            return true;
        }
        var result = new CompletableFuture<Boolean>();
        handle.execute(channel -> {
            LoggerFactory.getLogger("whostoppedthemusic-test").info("Outgoing source playing={}, stopped={}",
                    channel.playing(), channel.stopped());
            result.complete(!channel.playing() && channel.stopped());
        });
        return result.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS).join();
    }

    private static CompletableFuture<Boolean> channelState(net.minecraft.client.Minecraft client,
                                                          SoundInstance sound, boolean playing) {
        var engine = ((SoundManagerAccessor) client.getSoundManager()).whostoppedthemusic$engine();
        var handle = ((SoundEngineAccessor) engine).whostoppedthemusic$channels().get(sound);
        if (handle == null || handle.isStopped()) {
            return CompletableFuture.completedFuture(false);
        }
        var result = new CompletableFuture<Boolean>();
        handle.execute(channel -> result.complete(playing ? channel.playing() : !channel.playing() && !channel.stopped()));
        return result.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static AbstractWidget widget(ClientGameTestContext context, int index) {
        return context.computeOnClient(client -> Screens.getWidgets(client.gui.screen()).get(index));
    }

    private static CassetteWidget cassette(net.minecraft.client.gui.screens.Screen screen) {
        return Screens.getWidgets(screen).stream().filter(CassetteWidget.class::isInstance)
                .map(CassetteWidget.class::cast).findFirst().orElseThrow();
    }

    private static void clickWidget(ClientGameTestContext context, AbstractWidget widget) {
        double[] position = context.computeOnClient(client -> new double[]{
            (widget.getX() + widget.getWidth() / 2.0) * client.getWindow().getGuiScale(),
            (widget.getY() + widget.getHeight() / 2.0) * client.getWindow().getGuiScale()
        });
        context.getInput().setCursorPos(position[0], position[1]);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }

    private static void assertWidgetsFit(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var screen = client.gui.screen();
            var widgets = Screens.getWidgets(screen);
            if (screen instanceof MusicScreen) {
                var tape = widgets.get(0);
                var mode = widgets.get(1);
                var volume = widgets.get(7);
                check(volume instanceof MusicVolumeSlider, "vertical Music-only control at far right");
                check(tape.getRight() < mode.getX() && mode.getRight() < volume.getX(), "left tape, right controls, far-right volume");
                check(widgets.get(2).getY() > mode.getY() && widgets.get(3).getY() > widgets.get(2).getY(),
                        "Frequency and Jukebox below mode");
            }
            for (int i = 0; i < widgets.size(); i++) {
                var a = widgets.get(i);
                check(a.getX() >= 0 && a.getY() >= 0 && a.getRight() <= screen.width && a.getBottom() <= screen.height,
                        "widget stays inside the small screen: " + a.getMessage().getString());
                for (int j = i + 1; j < widgets.size(); j++) {
                    var b = widgets.get(j);
                    check(a.getRight() <= b.getX() || b.getRight() <= a.getX() || a.getBottom() <= b.getY() || b.getBottom() <= a.getY(),
                            "widgets must not overlap: " + a.getMessage().getString() + " / " + b.getMessage().getString());
                }
            }
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
