package xyz.pyrehaven.whostoppedthemusic;

import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.cassette;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.check;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.clickWidget;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.namedWidget;
import static xyz.pyrehaven.whostoppedthemusic.GameTestSupport.volume;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.MusicToastDisplayState;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.sounds.MusicManager.MusicFrequency;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.Musics;
import net.minecraft.sounds.SoundSource;
import xyz.pyrehaven.whostoppedthemusic.mixin.MusicManagerAccessor;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.Track;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicScreen;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;

final class VanillaGameTest {
    static void checkSharedControls(ClientGameTestContext context, Path settingsFile) {
        double master = context.computeOnClient(client -> client.options.getSoundSourceOptionInstance(SoundSource.MASTER).get());
        var toast = context.computeOnClient(client -> client.options.musicToast().get());
        context.clickScreenButton("whostoppedthemusic.mode.vanilla");
        context.runOnClient(client -> {
            check(WhoStoppedTheMusic.settings().mode() == Mode.BASIC, "mode cycles to Basic");
        });
        context.clickScreenButton("whostoppedthemusic.mode.basic");
        context.runOnClient(client -> check(WhoStoppedTheMusic.settings().mode() == Mode.CUSTOM, "mode cycles to Custom"));
        context.clickScreenButton("whostoppedthemusic.mode.custom");
        context.runOnClient(client -> {
            check(WhoStoppedTheMusic.settings().mode() == Mode.VANILLA, "mode cycles back to Vanilla");
            check(Screens.getWidgets(client.gui.screen()).size() == 8, "only tape, mode, frequency, jukebox, transports, music volume and Done");
        });
        var frequency = context.computeOnClient(client -> client.options.musicFrequency().get());
        clickWidget(context, namedWidget(context, "Frequency:"));
        context.runOnClient(client -> check(client.options.musicFrequency().get() != frequency, "frequency widget updates vanilla"));
        double music = context.computeOnClient(client -> client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get());
        clickWidget(context, volume(context));
        context.waitTicks(25);
        context.runOnClient(client -> check(client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get() != music,
                "music slider updates vanilla"));
        context.runOnClient(client -> {
            check(client.options.getSoundSourceOptionInstance(SoundSource.MASTER).get() == master, "music slider leaves Master unchanged");
            check(client.options.musicToast().get() == toast, "screen leaves toast preference unchanged");
        });
        JukeboxSetting previous = Settings.load(settingsFile).jukebox();
        clickWidget(context, namedWidget(context, "Jukebox:"));
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

    static void checkReboundKey(ClientGameTestContext context) {
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

    static void checkCassette(ClientGameTestContext context) {
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
            check(cassette(client.gui.screen()).getX() == Theme.MARGIN
                    && cassette(client.gui.screen()).getBottom() == client.gui.screen().height - Theme.MENU_FOOTER,
                    "cassette at bottom left of pause menu");
            String key = client.getMusicManager().getCurrentMusicTranslationKey();
            check(MusicDirector.get(client).metadata().equals(Track.metadata(Component.translatable(key.replace('/', '.')))),
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
            check(MusicDirector.get(client).metadata().equals(Track.metadata(Component.translatable(key.replace('/', '.')))), "cassette shows the real song");
            var instance = ((MusicManagerAccessor) client.getMusicManager()).whostoppedthemusic$currentMusic();
            check(client.getSoundManager().isActive(instance), "vanilla sound instance remains active");
        });
        context.takeScreenshot("whostoppedthemusic-cassette-song");
        context.runOnClient(client -> {
            client.getMusicManager().stopPlaying();
            check(MusicDirector.get(client).playbackStatus().getString().equals(
                    Component.translatable("whostoppedthemusic.cassette.silence").getString()), "truthful silence");
        });
        context.takeScreenshot("whostoppedthemusic-cassette-silence");
    }
}
