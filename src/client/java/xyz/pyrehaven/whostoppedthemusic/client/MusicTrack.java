package xyz.pyrehaven.whostoppedthemusic.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public record MusicTrack(Identifier id, Component displayName) {
    public String key() {
        return this.id.toString();
    }
}
