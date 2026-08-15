package xyz.pyrehaven.whostoppedthemusic;

import net.fabricmc.api.ClientModInitializer;

/**
 * Deliberate fail-loud entrypoint for the clean-rebuild groundwork.
 *
 * <p>The released gameplay implementation has been removed. Keeping a real client entrypoint makes
 * accidental installation fail immediately instead of presenting a healthy-looking no-op mod.</p>
 */
public final class WhoStoppedTheMusicClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        throw new IllegalStateException(
                "Who Stopped The Music is a non-deployable structural scaffold; gameplay is not implemented"
        );
    }
}
