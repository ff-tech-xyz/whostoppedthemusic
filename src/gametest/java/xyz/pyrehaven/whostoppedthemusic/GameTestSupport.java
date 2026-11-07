package xyz.pyrehaven.whostoppedthemusic;

import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.toasts.NowPlayingToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.lwjgl.openal.AL10;
import xyz.pyrehaven.whostoppedthemusic.mixin.SoundEngineAccessor;
import xyz.pyrehaven.whostoppedthemusic.mixin.SoundManagerAccessor;
import xyz.pyrehaven.whostoppedthemusic.ui.CassetteWidget;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicScreen;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicVolumeSlider;
import xyz.pyrehaven.whostoppedthemusic.ui.TransportButton;
import xyz.pyrehaven.whostoppedthemusic.ui.widget.TrackList;

final class GameTestSupport {
    static AbstractWidget namedWidgetOnClient(Screen screen, String name) {
        return Screens.getWidgets(screen).stream().filter(w -> w.getMessage().getString().equals(name)).findFirst().orElseThrow();
    }

    static TrackList trackList(Screen screen) {
        return Screens.getWidgets(screen).stream().filter(TrackList.class::isInstance).map(TrackList.class::cast).findFirst().orElseThrow();
    }

    static EditBox searchBox(Screen screen) {
        return Screens.getWidgets(screen).stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
    }

    static AbstractWidget namedWidget(ClientGameTestContext context, String prefix) {
        return context.computeOnClient(client -> Screens.getWidgets(client.gui.screen()).stream()
                .filter(w -> w.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow());
    }

    static AbstractWidget namedWidget(ClientGameTestContext context, String label, int y) {
        return context.computeOnClient(client -> Screens.getWidgets(client.gui.screen()).stream()
                .filter(w -> w.getY() == y && w.getMessage().getString().equals(label)).findFirst().orElseThrow());
    }

    static boolean channelStopped(Minecraft client, SoundInstance sound) {
        var engine = ((SoundManagerAccessor) client.getSoundManager()).whostoppedthemusic$engine();
        var handle = ((SoundEngineAccessor) engine).whostoppedthemusic$channels().get(sound);
        if (handle == null || handle.isStopped()) {
            return true;
        }
        // ChannelHandle.execute drops callbacks after release. Inspect on the sound executor
        // so cleanup between the initial check and execution still completes this assertion.
        try {
            var executorField = engine.getClass().getDeclaredField("executor");
            var channelField = handle.getClass().getDeclaredField("channel");
            executorField.setAccessible(true);
            channelField.setAccessible(true);
            return CompletableFuture.supplyAsync(() -> {
                try {
                    var channel = (Channel) channelField.get(handle);
                    return channel == null || !channel.playing() && channel.stopped();
                } catch (IllegalAccessException failure) { throw new AssertionError(failure); }
            }, (Executor) executorField.get(engine))
                    .orTimeout(5, TimeUnit.SECONDS).join();
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    static CompletableFuture<Boolean> channelState(Minecraft client,
                                                          SoundInstance sound, boolean playing) {
        var engine = ((SoundManagerAccessor) client.getSoundManager()).whostoppedthemusic$engine();
        var handle = ((SoundEngineAccessor) engine).whostoppedthemusic$channels().get(sound);
        if (handle == null || handle.isStopped()) {
            return CompletableFuture.completedFuture(false);
        }
        var result = new CompletableFuture<Boolean>();
        handle.execute(channel -> result.complete(playing ? channel.playing() : !channel.playing() && !channel.stopped()));
        return result.orTimeout(5, TimeUnit.SECONDS);
    }

    static CompletableFuture<Float> channelGain(Minecraft client, SoundInstance sound) {
        var engine = ((SoundManagerAccessor) client.getSoundManager()).whostoppedthemusic$engine();
        var handle = ((SoundEngineAccessor) engine).whostoppedthemusic$channels().get(sound);
        var result = new CompletableFuture<Float>();
        check(handle != null && !handle.isStopped(), "volume check requires a real allocated source");
        handle.execute(channel -> {
            try {
                var source = Channel.class.getDeclaredField("source");
                source.setAccessible(true);
                int id = source.getInt(channel);
                check(AL10.alGetError() == 0, "clean native boundary before gain source=" + id);
                float gain = AL10.alGetSourcef(id, AL10.AL_GAIN);
                check(AL10.alGetError() == 0, "gain native query source=" + id);
                result.complete(gain);
            } catch (ReflectiveOperationException exception) {
                result.completeExceptionally(exception);
            }
        });
        return result.orTimeout(5, TimeUnit.SECONDS);
    }

    static AbstractWidget primary(ClientGameTestContext context) {
        return context.computeOnClient(client -> Screens.getWidgets(client.gui.screen()).stream()
                .filter(TransportButton.class::isInstance)
                .filter(w -> Set.of("Play", "Pause", "Resume").contains(w.getMessage().getString()))
                .findFirst().orElseThrow());
    }

    static MusicVolumeSlider volume(ClientGameTestContext context) {
        return context.computeOnClient(client -> Screens.getWidgets(client.gui.screen()).stream()
                .filter(MusicVolumeSlider.class::isInstance).map(MusicVolumeSlider.class::cast).findFirst().orElseThrow());
    }

    static CassetteWidget cassette(Screen screen) {
        return Screens.getWidgets(screen).stream().filter(CassetteWidget.class::isInstance)
                .map(CassetteWidget.class::cast).findFirst().orElseThrow();
    }

    static void clickWidget(ClientGameTestContext context, AbstractWidget widget) {
        double[] position = context.computeOnClient(client -> new double[]{
            (widget.getX() + widget.getWidth() / 2.0) * client.getWindow().getGuiScale(),
            (widget.getY() + widget.getHeight() / 2.0) * client.getWindow().getGuiScale()
        });
        context.getInput().setCursorPos(position[0], position[1]);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }

    static void assertWidgetsFit(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var screen = client.gui.screen();
            var widgets = Screens.getWidgets(screen);
            if (screen instanceof MusicScreen && WhoStoppedTheMusic.settings().mode() == Mode.VANILLA) {
                var tape = widgets.stream().filter(w -> w instanceof Button && !(w instanceof TransportButton) && w.getHeight() > 20).findFirst().orElseThrow();
                var mode = namedWidgetOnClient(screen, "Vanilla");
                var volume = widgets.stream().filter(MusicVolumeSlider.class::isInstance).findFirst().orElseThrow();
                check(volume instanceof MusicVolumeSlider, "vertical Music-only control at far right");
                check(tape.getRight() < mode.getX() && mode.getRight() < volume.getX(), "left tape, right controls, far-right volume");
                check(widgets.stream().filter(w -> w.getMessage().getString().startsWith("Frequency:")).findFirst().orElseThrow().getY() > mode.getY() && widgets.stream().filter(w -> w.getMessage().getString().startsWith("Jukebox:")).findFirst().orElseThrow().getY() > widgets.stream().filter(w -> w.getMessage().getString().startsWith("Frequency:")).findFirst().orElseThrow().getY(),
                        "Frequency and Jukebox below mode");
            }
            for (int i = 0; i < widgets.size(); i++) {
                var a = widgets.get(i);
                if (!a.visible) continue;
                check(a.getX() >= 0 && a.getY() >= 0 && a.getRight() <= screen.width && a.getBottom() <= screen.height,
                        "widget stays inside the small screen: " + a.getMessage().getString());
                for (int j = i + 1; j < widgets.size(); j++) {
                    var b = widgets.get(j);
                    if (!b.visible) continue;
                    check(a.getRight() <= b.getX() || b.getRight() <= a.getX() || a.getBottom() <= b.getY() || b.getBottom() <= a.getY(),
                            "widgets must not overlap: " + a.getMessage().getString() + " / " + b.getMessage().getString());
                }
            }
        });
    }

    static void checkSingleSongToast(Minecraft client) {
        var toast = nowPlayingToast(client);
        check(toast != null && toast.getWantedVisibility() == Toast.Visibility.SHOW, "vanilla notification is visible");
        check(toast.xPos(client.getWindow().getGuiScaledWidth(), 1) == 0 && toast.yPos(0) == 0,
                "song notification uses vanilla's top-left placement");
    }

    static NowPlayingToast nowPlayingToast(Minecraft client) {
        try {
            var manager = client.gui.toastManager();
            var vanilla = manager.getClass().getDeclaredField("nowPlayingToast");
            vanilla.setAccessible(true);
            var instance = vanilla.get(manager);
            if (instance == null) return null;
            var field = instance.getClass().getDeclaredField("toast");
            field.setAccessible(true);
            return (NowPlayingToast) field.get(instance);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
