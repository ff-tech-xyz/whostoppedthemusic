package xyz.pyrehaven.whostoppedthemusic;

import static xyz.pyrehaven.whostoppedthemusic.CassetteGameTest.checkCassetteLabels;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.assertWidgetsFit;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.channelState;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.channelStopped;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.check;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.checkSingleSongToast;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.clickWidget;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.namedWidget;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.nowPlayingToast;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.searchBox;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.trackList;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.MusicToastDisplayState;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.toasts.NowPlayingToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager.MusicFrequency;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.Track;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicScreen;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;
import xyz.pyrehaven.whostoppedthemusic.ui.TransportButton;

final class ControlsGameTest {
    static void checkUnifiedControls(ClientGameTestContext context) {
        context.getInput().resizeWindow(960, 720);
        try (var world = context.worldBuilder().create()) {
            context.waitForScreen(null);
            context.runOnClient(client -> {
                client.options.guiScale().set(2);
                client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.7);
                client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.7);
                client.options.musicFrequency().set(MusicFrequency.CONSTANT);
                WhoStoppedTheMusic.settings().setJukebox(JukeboxSetting.PLAY_OVER);
                WhoStoppedTheMusic.settings().setAllTicked(WhoStoppedTheMusic.LIBRARY.tracks().stream().map(t -> t.id()).toList(), true);
            });
            if (Boolean.getBoolean("whostoppedthemusic.artworkOnly")) {
                ScenarioArtworkGameTest.artwork(context);
                return;
            }
            var record = context.computeOnClient(client -> {
                var sound = SimpleSoundInstance.forJukeboxSong(SoundEvent.createVariableRangeEvent(
                        Identifier.withDefaultNamespace("music_disc.cat")), Vec3.ZERO);
                client.getSoundManager().play(sound);
                return sound;
            });
            var effect = context.computeOnClient(client -> {
                var sound = new SimpleSoundInstance(Identifier.withDefaultNamespace("music_disc.cat"), SoundSource.AMBIENT,
                        0.5f, 1, SoundInstance.createUnseededRandom(), true, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
                client.getSoundManager().play(sound);
                return sound;
            });
            context.waitFor(client -> channelState(client, record, true).join() && channelState(client, effect, true).join());
            for (Mode mode : Mode.values()) {
                checkModeControls(context, mode, record, effect);
                context.runOnClient(client -> check(channelState(client, record, true).join() && channelState(client, effect, true).join(),
                        mode + " controls leave Records and effects advancing"));
            }
            context.runOnClient(client -> {
                client.getSoundManager().stop(record);
                client.getSoundManager().stop(effect);
            });
            checkCassetteLabels(context);
        }
        LoggerFactory.getLogger("whostoppedthemusic-test").info(
                "SHARED CONTROLS PASS: Vanilla/Basic Play/Pause/same-source Resume/paused Next, pause-menu Next, row Play, held Basic Stop, automatic transitions, single reused toast, duration multiplier, Never, Records/effects isolation, browser state and 320x240");
    }

    static void checkModeControls(ClientGameTestContext context, Mode mode, SoundInstance record, SoundInstance effect) {
        context.runOnClient(client -> {
            var settings = WhoStoppedTheMusic.settings();
            settings.setMode(mode);
            MusicDirector.get(client).syncMode();
            client.getMusicManager().stopPlaying();
            client.options.musicToast().set(MusicToastDisplayState.PAUSE_AND_TOAST);
            client.options.notificationDisplayTime().set(2.0);
            client.gui.toastManager().clear();
            client.gui.setScreen(new MusicScreen(null, settings));
        });
        context.waitForScreen(MusicScreen.class);
        clickWidget(context, namedWidget(context, "Play"));
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        var sound = context.computeOnClient(client -> MusicDirector.get(client).current());
        context.runOnClient(client -> {
            checkSingleSongToast(client);
            var manager = client.gui.toastManager();
            var toast = nowPlayingToast(client);
            manager.showNowPlayingToast();
            check(nowPlayingToast(client) == toast, "vanilla callback reuses its dedicated toast");
            var timed = new NowPlayingToast();
            timed.showToast(client.options);
            timed.update(manager, 9999);
            check(timed.getWantedVisibility() == Toast.Visibility.SHOW, "2x notification lasts almost ten seconds");
            timed.update(manager, 10001);
            check(timed.getWantedVisibility() == Toast.Visibility.HIDE, "vanilla notification duration expires");
        });
        clickWidget(context, namedWidget(context, "Pause"));
        context.waitFor(client -> channelState(client, sound, false).join());
        context.runOnClient(client -> client.getSoundManager().resume());
        context.waitTicks(30);
        context.runOnClient(client -> {
            check(MusicDirector.get(client).paused() && MusicDirector.get(client).current() == sound
                    && channelState(client, sound, false).join(), mode + " held Pause survives ticks/global resume");
            check(channelState(client, record, true).join() && channelState(client, effect, true).join(),
                    "Records/effects keep advancing during held Pause");
        });
        clickWidget(context, namedWidget(context, "Resume"));
        context.waitFor(client -> channelState(client, sound, true).join());
        context.runOnClient(client -> check(MusicDirector.get(client).current() == sound, "Resume preserves exact source"));
        var toast = context.computeOnClient(GameTestSupport::nowPlayingToast);
        clickWidget(context, namedWidget(context, "Pause"));
        clickWidget(context, namedWidget(context, "Next"));
        context.waitFor(client -> channelStopped(client, sound) && channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> {
            check(!MusicDirector.get(client).paused(), "Next clears pause");
            checkSingleSongToast(client);
            check(nowPlayingToast(client) == toast, "transition updates same vanilla notification");
        });
        if (mode == Mode.BASIC) checkBasicStopAndRow(context, record, effect);
        var outgoing = context.computeOnClient(client -> MusicDirector.get(client).current());
        context.runOnClient(client -> client.getSoundManager().stop(outgoing));
        context.waitFor(client -> MusicDirector.get(client).current() != outgoing
                && channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> checkSingleSongToast(client));
        context.takeScreenshot("shared-controls-" + mode.name().toLowerCase(Locale.ROOT));
        context.runOnClient(client -> client.options.musicToast().set(MusicToastDisplayState.NEVER));
        context.waitTicks(2);
        clickWidget(context, namedWidget(context, "Next"));
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> {
            var hidden = nowPlayingToast(client);
            check(hidden == null || hidden.getWantedVisibility() == Toast.Visibility.HIDE, "Never hides vanilla toast");
        });
        context.clickScreenButton("gui.done");
        context.waitForScreen(null);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(PauseScreen.class);
        var beforeTop = context.computeOnClient(client -> MusicDirector.get(client).current());
        clickWidget(context, namedWidget(context, "Next"));
        context.waitFor(client -> channelStopped(client, beforeTop) && channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> client.options.notificationDisplayTime().set(1.0));
    }

    static void checkBasicStopAndRow(ClientGameTestContext context, SoundInstance record, SoundInstance effect) {
        clickWidget(context, namedWidget(context, "Stop"));
        context.waitTicks(40);
        context.runOnClient(client -> {
            var director = MusicDirector.get(client);
            check(!director.active() && !director.canPrevious(), "Basic Stop holds through Constant ticks");
            director.previous();
            check(!director.active(), "Previous cannot escape held Stop");
            check(channelState(client, record, true).join() && channelState(client, effect, true).join(),
                    "Records/effects keep advancing during held Stop");
        });
        clickWidget(context, namedWidget(context, "Play"));
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> {
            searchBox(client.gui.screen()).setValue("Pigstep");
            check(trackList(client.gui.screen()).children().getFirst().children().getLast() instanceof TransportButton,
                    "row Play shares drawn transport widget");
        });
        context.waitTicks(2);
        clickWidget(context, context.computeOnClient(client -> (AbstractWidget) trackList(client.gui.screen()).children().getFirst().children().getLast()));
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
        context.runOnClient(client -> check(MusicDirector.get(client).metadata().equals(Track.metadata(Component.literal("Lena Raine - Pigstep"))), "row Play starts selected track"));
        clickWidget(context, namedWidget(context, "<", Theme.BASIC_TOP));
        clickWidget(context, namedWidget(context, ">", Theme.BASIC_TOP));
        context.getInput().resizeWindow(320, 240);
        context.waitTicks(3);
        assertWidgetsFit(context);
        context.runOnClient(client -> check(searchBox(client.gui.screen()).getValue().equals("Pigstep"), "query survives collapse and resize"));
        context.takeScreenshot("shared-controls-basic-small");
        context.getInput().resizeWindow(960, 720);
        context.waitTicks(3);
    }
}
