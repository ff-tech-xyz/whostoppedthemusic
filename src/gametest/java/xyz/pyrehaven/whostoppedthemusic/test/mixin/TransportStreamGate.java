package xyz.pyrehaven.whostoppedthemusic.test.mixin;

import java.util.concurrent.CompletableFuture;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.pyrehaven.whostoppedthemusic.ClientSmokeGameTest;

/** Hold real vanilla stream decoding at the asynchronous channel-start boundary. */
@Mixin(SoundBufferLibrary.class)
public abstract class TransportStreamGate {
    @Inject(method = "getStream", at = @At("RETURN"), cancellable = true)
    private void holdStream(Identifier id, boolean looping, CallbackInfoReturnable<CompletableFuture<AudioStream>> ci) {
        var gate = ClientSmokeGameTest.streamGate;
        if (gate != null) {
            ClientSmokeGameTest.streamGate = null;
            var ready = ClientSmokeGameTest.streamReady;
            ci.setReturnValue(ci.getReturnValue().thenCompose(stream -> {
                ready.complete(null);
                return gate.thenApply(ignored -> stream);
            }));
        }
    }
}
