package xyz.pyrehaven.whostoppedthemusic.music;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.network.chat.Component;

public record Track(String id, Kind kind, Component title, Sound sound) {
    public enum Kind { SOUNDTRACK, DISC }
}
