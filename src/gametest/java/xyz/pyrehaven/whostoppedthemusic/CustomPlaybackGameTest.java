package xyz.pyrehaven.whostoppedthemusic;

import static xyz.pyrehaven.whostoppedthemusic.ClientSmokeGameTest.channelState;
import static xyz.pyrehaven.whostoppedthemusic.ClientSmokeGameTest.channelGain;

import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.sounds.MusicManager.MusicFrequency;
import net.minecraft.sounds.SoundSource;
import xyz.pyrehaven.whostoppedthemusic.mixin.MusicManagerAccessor;
import xyz.pyrehaven.whostoppedthemusic.music.Deck;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.TrackSoundInstance;
import xyz.pyrehaven.whostoppedthemusic.music.custom.*;

final class CustomPlaybackGameTest {
    static void run(ClientGameTestContext context) {
        context.runOnClient(client -> {
            WhoStoppedTheMusic.settings().setMode(Mode.CUSTOM);
            client.options.musicFrequency().set(MusicFrequency.DEFAULT);
            client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.7);
            client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.7);
        });
        awaitWinner(context, ScenarioId.special(Special.MENU));
        awaitAudio(context);
        try (var world = context.worldBuilder().create()) {
            context.waitForScreen(null);
            var server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("weather clear");
            server.runCommand("tp @a 0 100 0");
            server.runCommand("fill -8 99 -8 8 99 8 stone");
            setBiome(context, server, "swamp");
            var swamp = ScenarioId.biome("minecraft:swamp");
            awaitWinner(context, swamp);
            context.runOnClient(client -> {
                var director = MusicDirector.get(client);
                check(director.customPlayback().catalog().entries().size() == 67, "all registry biomes cataloged");
                check(WhoStoppedTheMusic.settings().knownBiomes().size() >= 67, "known biomes persisted");
                check(director.customPlayback().resolution().entries().equals(director.customPlayback().defaults(swamp).config().songs()), "swamp keeps loaded default odds");
                check(director.music(null).minDelay() == director.customPlayback().defaults(swamp).music().orElseThrow().minDelay(), "source delays retained");
                director.next();
            });
            awaitAudio(context);
            context.runOnClient(client -> {
                check(MusicDirector.get(client).current() instanceof TrackSoundInstance, "Custom uses shared resolved source");
                check(client.options.musicFrequency().get() == MusicFrequency.DEFAULT, "Custom leaves global option alone");
                client.getMusicManager().stopPlaying();
                check(((MusicManagerAccessor) client.getMusicManager()).whostoppedthemusic$nextSongDelay() == 200, "Constant stop delay uses scenario not global");
            });
            context.waitTicks(220);
            awaitAudio(context);
            var first = context.computeOnClient(client -> MusicDirector.get(client).current());
            context.runOnClient(client -> MusicDirector.get(client).togglePlayback());
            context.waitTicks(5);
            context.runOnClient(client -> check(MusicDirector.get(client).paused() && channelState(client, first, false).join(), "shared pause holds exact channel"));
            context.runOnClient(client -> MusicDirector.get(client).togglePlayback());
            context.waitFor(client -> channelState(client, first, true).join());
            context.runOnClient(client -> MusicDirector.get(client).next());
            awaitAudio(context);
            context.runOnClient(client -> {
                check(MusicDirector.get(client).canPrevious(), "Custom history enabled");
                MusicDirector.get(client).previous();
            });
            awaitAudio(context);
            context.runOnClient(client -> check(((TrackSoundInstance) MusicDirector.get(client).current()).track().id()
                    .equals(((TrackSoundInstance) first).track().id()), "Previous restores current key history"));
            checkWeather(context, server, swamp);
            checkSilenceAndVolume(context, server);
            checkCache(context);
            checkDimensions(context, server);
            checkBoss(context);
            server.runCommand("execute as @a in minecraft:overworld run tp @s 0 100 0");
            context.waitFor(client -> client.level.dimension().identifier().toString().equals("minecraft:overworld"));
            setBiome(context, server, "swamp");
            context.waitTicks(10);
            context.runOnClient(client -> {
                var director = MusicDirector.get(client);
                var settings = WhoStoppedTheMusic.settings();
                settings.setMode(Mode.BASIC);
                director.syncMode();
                director.next();
            });
            awaitAudio(context);
            context.runOnClient(client -> check(MusicDirector.get(client).current().getIdentifier().equals(MusicDirector.BASIC.sound().value().location()), "Basic source unchanged"));
            context.runOnClient(client -> {
                WhoStoppedTheMusic.settings().setMode(Mode.VANILLA);
                MusicDirector.get(client).syncMode();
                MusicDirector.get(client).next();
            });
            awaitAudio(context);
            context.runOnClient(client -> check(!(MusicDirector.get(client).current() instanceof TrackSoundInstance), "Vanilla selection unchanged"));
            context.runOnClient(client -> WhoStoppedTheMusic.settings().setMode(Mode.CUSTOM));
        }
        context.waitForScreen(TitleScreen.class);
        awaitWinner(context, ScenarioId.special(Special.MENU));
        awaitAudio(context);
        context.setScreen(() -> new net.minecraft.client.gui.screens.WinScreen(false, () -> {}));
        awaitWinner(context, ScenarioId.special(Special.CREDITS));
        awaitAudio(context);
        context.setScreen(TitleScreen::new);
        awaitWinner(context, ScenarioId.special(Special.MENU));
        awaitAudio(context);
        org.slf4j.LoggerFactory.getLogger("whostoppedthemusic-test").info("CUSTOM PLAYBACK PASS: menu/world/credits/menu, registry catalog, swamp/Nether/End defaults/delay/frequency, transport/history, thunder/roof and paused transition, per-scenario jukebox, empty/missing silence, pale garden edited volume, boss overlay, 32-key LRU, unchanged Basic/Vanilla");
    }

    private static void checkWeather(ClientGameTestContext context, TestServerContext server, ScenarioId swamp) {
        var thunder = ScenarioId.special(Special.THUNDER);
        context.runOnClient(client -> edit(client, thunder, true, true, JukeboxSetting.PLAY_OVER, List.of(new Deck.Entry("minecraft:records/pigstep", 1))));
        context.runOnClient(client -> MusicDirector.get(client).togglePlayback());
        server.runCommand("weather thunder");
        awaitWinner(context, thunder);
        context.waitTicks(10);
        context.runOnClient(client -> {
            check(MusicDirector.get(client).paused(), "winner changes do not release held pause");
            MusicDirector.get(client).togglePlayback();
        });
        awaitAudio(context);
        context.waitFor(client -> MusicDirector.get(client).current() instanceof TrackSoundInstance chosen
                && chosen.track().id().equals("minecraft:records/pigstep"));
        context.runOnClient(client -> check(((TrackSoundInstance) MusicDirector.get(client).current()).track().id().equals("minecraft:records/pigstep"), "thunder automatically interrupts into configured song"));
        var outside = context.computeOnClient(client -> MusicDirector.get(client).current());
        server.runCommand("fill -3 104 -3 3 104 3 stone");
        awaitWinner(context, swamp);
        context.runOnClient(client -> check(MusicDirector.get(client).current() == outside, "noninterrupt biome lets thunder song finish under roof"));
        server.runCommand("fill -3 104 -3 3 104 3 air");
        awaitWinner(context, thunder);
        context.runOnClient(client -> MusicDirector.get(client).next());
        awaitAudio(context);
        var pos = context.computeOnClient(client -> client.gameRenderer.mainCamera().blockPosition().immutable());
        context.runOnClient(client -> {
            var registry = client.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.JUKEBOX_SONG);
            client.level.levelEvent(null, 1010, pos, registry.getId(registry.getValueOrThrow(net.minecraft.world.item.JukeboxSongs.CAT)));
        });
        context.waitFor(client -> xyz.pyrehaven.whostoppedthemusic.music.JukeboxWatcher.nearby(client));
        context.waitTicks(10);
        context.runOnClient(client -> check(MusicDirector.get(client).active(), "scenario Play over ignores jukebox"));
        context.runOnClient(client -> edit(client, thunder, true, true, JukeboxSetting.PAUSE_MUSIC, List.of(new Deck.Entry("minecraft:records/pigstep", 1))));
        context.waitTicks(330);
        context.runOnClient(client -> {
            check(!MusicDirector.get(client).active(), "scenario Pause music fades/holds");
            MusicDirector.get(client).next();
            check(!MusicDirector.get(client).active(), "manual next respects scenario jukebox hold");
            client.level.levelEvent(null, 1011, pos, 0);
        });
        awaitAudio(context);
        context.runOnClient(client -> edit(client, thunder, true, true, JukeboxSetting.PLAY_OVER, List.of()));
        // Empty edited list does not interrupt an already winning key; next stops it and stays silent.
        context.waitTicks(2);
        context.runOnClient(client -> MusicDirector.get(client).next());
        context.waitTicks(20);
        context.runOnClient(client -> check(!MusicDirector.get(client).active() && !MusicDirector.get(client).mayStart(), "empty active winner is silence"));
        context.runOnClient(client -> edit(client, thunder, true, true, JukeboxSetting.PLAY_OVER, List.of(new Deck.Entry("removed:missing", 100))));
        context.waitTicks(2);
        context.runOnClient(client -> check(!MusicDirector.get(client).canNext(), "all missing songs give silence"));
        context.runOnClient(client -> {
            var d = MusicDirector.get(client).customPlayback().defaults(thunder).config();
            WhoStoppedTheMusic.settings().setScenario(thunder, d, d);
        });
        server.runCommand("weather clear");
        awaitWinner(context, swamp);
    }

    private static void checkSilenceAndVolume(ClientGameTestContext context, TestServerContext server) {
        setBiome(context, server, "pale_garden");
        var pale = ScenarioId.biome("minecraft:pale_garden");
        awaitWinner(context, pale);
        context.runOnClient(client -> MusicDirector.get(client).next());
        context.waitTicks(330);
        context.runOnClient(client -> check(!MusicDirector.get(client).active() && !MusicDirector.get(client).canNext(), "unedited pale garden stays silent"));
        context.runOnClient(client -> edit(client, pale, true, false, JukeboxSetting.PLAY_OVER, List.of(new Deck.Entry("minecraft:records/pigstep", 1))));
        context.waitTicks(3);
        context.runOnClient(client -> MusicDirector.get(client).next());
        awaitAudio(context);
        context.waitTicks(15);
        context.runOnClient(client -> check(channelGain(client, MusicDirector.get(client).current()).join() > 0, "edited pale garden escapes environment volume zero"));
    }

    private static void checkCache(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var p = new CustomPlayback(WhoStoppedTheMusic.settings(), WhoStoppedTheMusic.LIBRARY);
            p.catalog().rebuild(client.level.registryAccess(), client.level.dimensionType(), WhoStoppedTheMusic.settings());
            var biomes = p.catalog().entries().stream().map(BiomeCatalog.Entry::id).toList();
            p.tick(biomeSituation(biomes.getFirst()), client.level.dimensionType());
            var first = p.deck();
            for (int i = 1; i < 32; i++) p.tick(biomeSituation(biomes.get(i)), client.level.dimensionType());
            p.tick(biomeSituation(biomes.getFirst()), client.level.dimensionType());
            check(first == p.deck(), "32 keys retain original deck");
            p.tick(biomeSituation(biomes.get(32)), client.level.dimensionType());
            p.tick(biomeSituation(biomes.get(1)), client.level.dimensionType());
            var second = p.deck();
            check(second != first, "different keys have different decks");
            p.tick(biomeSituation(biomes.getFirst()), client.level.dimensionType());
            check(p.deck() == first, "LRU access keeps most recently used key");
            p.tick(biomeSituation(biomes.get(2)), client.level.dimensionType());
            // Revisit every old key to force actual eviction of the first key.
            for (int i = 1; i < 34; i++) p.tick(biomeSituation(biomes.get(i)), client.level.dimensionType());
            p.tick(biomeSituation(biomes.getFirst()), client.level.dimensionType());
            check(p.deck() != first, "33 other keys evict original deck");
        });
    }

    private static void checkDimensions(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("execute as @a in minecraft:the_nether run tp @s 0 100 0");
        context.waitFor(client -> client.level.dimension().identifier().toString().equals("minecraft:the_nether"));
        server.runCommand("execute in minecraft:the_nether run fillbiome -8 96 -8 8 112 8 minecraft:crimson_forest");
        var crimson = ScenarioId.biome("minecraft:crimson_forest");
        awaitWinner(context, crimson);
        context.runOnClient(client -> {
            var d = MusicDirector.get(client);
            check(d.customPlayback().resolution().entries().equals(d.customPlayback().defaults(crimson).config().songs()), "Nether uses its own songs");
            d.next();
        });
        awaitAudio(context);
        var nether = context.computeOnClient(client -> MusicDirector.get(client).current());
        server.runCommand("execute as @a in minecraft:the_end run tp @s 0 100 0");
        context.waitFor(client -> client.level.dimension().identifier().toString().equals("minecraft:the_end"));
        awaitWinner(context, ScenarioId.biome("minecraft:the_end"));
        context.waitFor(client -> MusicDirector.get(client).current() != nether);
        context.runOnClient(client -> {
            check(MusicDirector.get(client).music(null).replaceCurrentMusic(), "End inherits vanilla replace flag");
            MusicDirector.get(client).next();
        });
        awaitAudio(context);
    }

    private static void checkBoss(ClientGameTestContext context) {
        var id = java.util.UUID.randomUUID();
        context.runOnClient(client -> {
            var event = new net.minecraft.server.level.ServerBossEvent(id, net.minecraft.network.chat.Component.literal("Dragon"),
                    net.minecraft.world.BossEvent.BossBarColor.PURPLE, net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
            event.setPlayBossMusic(true);
            client.gui.hud.getBossOverlay().update(net.minecraft.network.protocol.game.ClientboundBossEventPacket.createAddPacket(event));
        });
        awaitWinner(context, ScenarioId.special(Special.BOSS));
        context.runOnClient(client -> {
            var p = MusicDirector.get(client).customPlayback();
            check(p.music().replaceCurrentMusic() && p.resolution().entries().equals(p.defaults(ScenarioId.special(Special.BOSS)).config().songs()), "boss overlay uses dragon defaults and interrupts");
            client.gui.hud.getBossOverlay().update(net.minecraft.network.protocol.game.ClientboundBossEventPacket.createRemovePacket(id));
        });
    }

    private static Situation biomeSituation(ScenarioId id) {
        return new Situation(false, false, false, false, false, id, Set.of(), 100, true, true, false, false, Situation.Precipitation.NONE);
    }

    private static void setBiome(ClientGameTestContext context, TestServerContext server, String biome) {
        server.runCommand("fillbiome -8 96 -8 8 112 8 minecraft:" + biome);
        context.waitFor(client -> SituationReader.read(client).biome().equals(ScenarioId.biome("minecraft:" + biome)));
    }

    private static void edit(Minecraft client, ScenarioId id, boolean active, boolean interrupt, JukeboxSetting jukebox, List<Deck.Entry> songs) {
        var d = MusicDirector.get(client).customPlayback().defaults(id).config();
        WhoStoppedTheMusic.settings().setScenario(id, new ScenarioConfig(d.priority(), active, interrupt, d.frequency(), jukebox,
                d.mustBeOutside(), d.yLevel(), songs), d);
    }

    private static void awaitWinner(ClientGameTestContext context, ScenarioId id) {
        context.waitFor(client -> MusicDirector.get(client).customPlayback().resolution() != null
                && MusicDirector.get(client).customPlayback().resolution().winners().equals(List.of(id)));
    }

    private static void awaitAudio(ClientGameTestContext context) {
        context.waitFor(client -> channelState(client, MusicDirector.get(client).current(), true).join());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
