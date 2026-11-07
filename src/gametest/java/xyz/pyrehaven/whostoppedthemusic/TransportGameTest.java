package xyz.pyrehaven.whostoppedthemusic;

import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.cassette;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.channelGain;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.channelState;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.channelStopped;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.check;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.clickWidget;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.namedWidget;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.primary;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.volume;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager.MusicFrequency;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.Track;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicScreen;
import xyz.pyrehaven.whostoppedthemusic.ui.TransportButton;

final class TransportGameTest {
    static void run(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.waitForScreen(null);
            context.runOnClient(client -> {
                client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.7);
                client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.7);
                client.getMusicManager().stopPlaying();
            });
            context.getInput().pressKey(InputConstants.KEY_M);
            context.waitForScreen(MusicScreen.class);
            context.runOnClient(client -> {
                var controls = Screens.getWidgets(client.gui.screen());
                var play = controls.stream().filter(TransportButton.class::isInstance).filter(w -> w.getMessage().getString().equals("Play")).findFirst().orElseThrow();
                check(play.active, "Play must be available when vanilla is silent");
                check(controls.stream().noneMatch(w -> w.getMessage().getString().equals("<")),
                        "Vanilla has no previous-track button");
            });
            clickWidget(context, primary(context));
            context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
            context.runOnClient(client -> check(MusicDirector.get(client).active(), "Play starts vanilla music"));
            context.clickScreenButton("gui.done");
            context.waitForScreen(null);
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitForScreen(PauseScreen.class);
            checkTransport(context);
        }
        LoggerFactory.getLogger("whostoppedthemusic-test").info("VANILLA TRANSPORT PASS");
    }

    static void checkTransport(ClientGameTestContext context) {
        checkDelayedTransport(context);
        // Let vanilla's real tick select the source; never manually start the main transport case.
        context.runOnClient(client -> {
            client.options.musicFrequency().set(MusicFrequency.DEFAULT);
            client.options.musicFrequency().set(MusicFrequency.CONSTANT);
        });
        context.waitFor(client -> MusicDirector.get(client).active());
        var old = context.computeOnClient(client -> MusicDirector.get(client).current());
        context.waitFor(client -> channelState(client, old, true).join());
        var topNext = context.computeOnClient(client -> Screens.getWidgets(client.gui.screen()).stream()
                .filter(w -> w.getMessage().getString().equals("Next")).findFirst().orElseThrow());
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
        context.runOnClient(client -> {
            var controls = Screens.getWidgets(client.gui.screen());
            check(controls.size() == 8, "Vanilla controls omit Previous and retain Done");
            check(controls.stream().noneMatch(w -> w.getMessage().getString().equals("<")), "no previous-track button");
            var director = MusicDirector.get(client);
            var resolved = director.current().getSound().getLocation().toShortLanguageKey().replace('/', '.');
            check(director.metadata().equals(Track.metadata(Component.translatable(resolved))),
                    "title comes from the resolved file, not the situational pool");
        });
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
                    1, 1, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
            client.getSoundManager().play(sound);
            return sound;
        });
        context.waitFor(client -> channelState(client, effect, true).join());
        clickWidget(context, primary(context));
        context.waitFor(client -> channelState(client, music, false).join());
        context.runOnClient(client -> {
            check(MusicDirector.get(client).paused(), "pause holds the current vanilla instance");
            check(MusicDirector.get(client).playbackStatus().getString().equals("Paused"), "truthful paused label");
            client.getSoundManager().resume();
        });
        context.waitTicks(30);
        context.runOnClient(client -> {
            check(MusicDirector.get(client).current() == music && MusicDirector.get(client).paused(),
                    "pause survives automatic vanilla ticks without replacement");
            check(channelState(client, music, false).join(), "global menu resume cannot unpause held music");
            check(channelState(client, record, true).join(), "Pause never touches Records");
            check(channelState(client, effect, true).join(), "Pause never touches non-music effects");
        });
        context.getInput().setCursorPos(0, 0);
        context.waitTicks(2);
        context.takeScreenshot("whostoppedthemusic-paused");
        clickWidget(context, primary(context));
        context.waitFor(client -> channelState(client, music, true).join());
        context.runOnClient(client -> check(MusicDirector.get(client).current() == music
                && !MusicDirector.get(client).paused(), "Resume continues the same channel, not a restart"));
        clickWidget(context, primary(context));
        context.waitFor(client -> channelState(client, music, false).join());
        clickWidget(context, namedWidget(context, "Next"));
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
        // The same option callbacks used by vanilla must drive its automatic loop.
        var beforeConstant = context.computeOnClient(client -> MusicDirector.get(client).current());
        context.runOnClient(client -> client.options.musicFrequency().set(MusicFrequency.FREQUENT));
        clickWidget(context, namedWidget(context, "Frequency:"));
        context.runOnClient(client -> {
            check(client.options.musicFrequency().get() == MusicFrequency.CONSTANT,
                    "Frequency button selects vanilla Constant");
            client.getSoundManager().stop(MusicDirector.get(client).current());
        });
        context.waitFor(client -> channelStopped(client, beforeConstant)
                && MusicDirector.get(client).current() != beforeConstant && MusicDirector.get(client).active()
                && channelState(client, MusicDirector.get(client).current(), true).join());
        var automatic = context.computeOnClient(client -> MusicDirector.get(client).current());
        var slider = volume(context);
        clickWidget(context, slider);
        context.getInput().pressKey(InputConstants.KEY_HOME);
        context.runOnClient(client -> check(client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get() == 0,
                "Music slider keyboard Home sets vanilla zero volume"));
        context.waitFor(client -> channelGain(client, automatic).join() == 0.0f);
        context.runOnClient(client -> {
            check(client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get() == 0,
                    "Music slider mutes the actual vanilla source");
            check(client.options.getSoundSourceOptionInstance(SoundSource.MASTER).get() == 0.7,
                    "Music slider leaves Master unchanged");
        });
        context.getInput().pressKey(InputConstants.KEY_END);
        context.waitFor(client -> MusicDirector.get(client).current() == automatic
                && channelState(client, automatic, true).join() && channelGain(client, automatic).join() > 0.0f);
        context.runOnClient(client -> check(client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get() == 1,
                "unmuting restores automatic vanilla playback"));
        context.getInput().setCursorPos(0, 0);
        context.waitTicks(2);
        context.takeScreenshot("whostoppedthemusic-transport");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(PauseScreen.class);
    }

    static void checkDelayedTransport(ClientGameTestContext context) {
        var stopGate = new CompletableFuture<Void>();
        context.runOnClient(client -> {
            client.getMusicManager().stopPlaying();
            ClientSmokeGameTest.streamGate = stopGate;
            ClientSmokeGameTest.streamReady = new CompletableFuture<>();
            client.options.musicFrequency().set(MusicFrequency.DEFAULT);
            client.options.musicFrequency().set(MusicFrequency.CONSTANT);
        });
        context.waitFor(client -> ClientSmokeGameTest.streamReady.isDone());
        var old = context.computeOnClient(client -> MusicDirector.get(client).current());
        context.runOnClient(client -> {
            check(ClientSmokeGameTest.streamGate == null, "vanilla stream startup is held before channel.play");
            MusicDirector.get(client).next();
            stopGate.complete(null);
        });
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        boolean stopped = context.computeOnClient(client -> channelStopped(client, old));
        var pauseGate = new CompletableFuture<Void>();
        context.runOnClient(client -> {
            client.getMusicManager().stopPlaying();
            ClientSmokeGameTest.streamGate = pauseGate;
            ClientSmokeGameTest.streamReady = new CompletableFuture<>();
            client.options.musicFrequency().set(MusicFrequency.DEFAULT);
            client.options.musicFrequency().set(MusicFrequency.CONSTANT);
        });
        context.waitFor(client -> ClientSmokeGameTest.streamReady.isDone());
        var music = context.computeOnClient(client -> MusicDirector.get(client).current());
        context.runOnClient(client -> {
            MusicDirector.get(client).togglePlayback();
            pauseGate.complete(null);
        });
        context.waitTicks(20);
        boolean held = context.computeOnClient(client -> MusicDirector.get(client).current() == music
                && MusicDirector.get(client).paused() && channelState(client, music, false).join());
        LoggerFactory.getLogger("whostoppedthemusic-test").info(
                "Delayed real-channel results: outgoingStopped={}, incomingPaused={}", stopped, held);
        check(stopped, "Next must stop the outgoing source even when its stream finishes after stop");
        check(held, "Pause before stream startup must hold the actual source paused");
        context.runOnClient(client -> MusicDirector.get(client).togglePlayback());
        context.waitFor(client -> channelState(client, music, true).join());
        context.runOnClient(client -> client.getMusicManager().stopPlaying());
        LoggerFactory.getLogger("whostoppedthemusic-test").info(
                "TRANSPORT CHANNEL PASS: delayed outgoing stream stopped; delayed incoming stream paused then resumed");
    }
}
