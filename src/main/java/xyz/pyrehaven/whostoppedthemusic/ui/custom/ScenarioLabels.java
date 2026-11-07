package xyz.pyrehaven.whostoppedthemusic.ui.custom;

import java.util.List;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.music.TrackNames;
import xyz.pyrehaven.whostoppedthemusic.music.custom.ScenarioId;

/** The same translated scenario names in the browser, player and editor. */
public final class ScenarioLabels {
    private ScenarioLabels() {}

    public static Component name(ScenarioId id) {
        return id.kind() == ScenarioId.Kind.SPECIAL
                ? Component.translatable("whostoppedthemusic.scenario." + id.id())
                : Component.translatableWithFallback("biome." + id.id().replace(':', '.').replace('/', '.'),
                        TrackNames.prettify(id.id()));
    }

    public static Component names(List<ScenarioId> ids) {
        var label = Component.empty();
        for (var id : ids) {
            if (!label.getString().isEmpty()) label.append(" + ");
            label.append(name(id));
        }
        return label;
    }

    public static boolean fixed(ScenarioId id) {
        return id.kind() == ScenarioId.Kind.SPECIAL && (id.id().equals("menu") || id.id().equals("credits"));
    }
}
