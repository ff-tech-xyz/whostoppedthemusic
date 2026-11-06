package xyz.pyrehaven.whostoppedthemusic;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.LoggerFactory;

public final class WhoStoppedTheMusic implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LoggerFactory.getLogger("whostoppedthemusic").info("Who Stopped The Music 1.2.0 loaded");
    }
}
