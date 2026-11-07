package xyz.pyrehaven.whostoppedthemusic.music.custom;

import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.world.level.levelgen.Heightmap;

/** One camera-block snapshot per manager tick; player-only conditions stay on the player. */
public final class SituationReader {
    private SituationReader() {}

    public static Situation read(Minecraft client) {
        boolean credits = client.gui.screen() instanceof WinScreen;
        if (client.player == null || client.level == null) {
            return new Situation(credits, true, false, false, false, null, Set.of(), 0,
                    false, false, false, false, Situation.Precipitation.NONE);
        }
        var level = client.level;
        var pos = client.gameRenderer.mainCamera().blockPosition();
        var biome = level.getBiome(pos);
        var id = biome.unwrapKey().orElseThrow().identifier();
        var abilities = client.player.getAbilities();
        return new Situation(credits, false, client.gui.hud.getBossOverlay().shouldPlayMusic(),
                abilities.instabuild && abilities.mayfly, client.player.isUnderWater(),
                ScenarioId.biome(id.toString()), biome.tags().map(tag -> tag.location().toString()).collect(Collectors.toSet()),
                pos.getY(), level.canSeeSky(pos),
                level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) <= pos.getY(),
                level.isRaining(), level.isThundering(), Situation.Precipitation.valueOf(
                        biome.value().getPrecipitationAt(pos, level.getSeaLevel()).name()));
    }
}
