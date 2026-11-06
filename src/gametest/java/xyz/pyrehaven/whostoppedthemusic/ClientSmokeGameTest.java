package xyz.pyrehaven.whostoppedthemusic;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;
import org.slf4j.LoggerFactory;

public final class ClientSmokeGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitForScreen(TitleScreen.class);
        context.waitTicks(40);
        context.takeScreenshot("whostoppedthemusic-title");
        LoggerFactory.getLogger("whostoppedthemusic-test").info("WHOSTOPPEDTHEMUSIC CLIENT PASS: title screen");
    }
}
