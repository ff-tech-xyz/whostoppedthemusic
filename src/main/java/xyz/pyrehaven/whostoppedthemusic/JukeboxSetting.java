package xyz.pyrehaven.whostoppedthemusic;

public enum JukeboxSetting {
    PLAY_OVER,
    PAUSE_MUSIC;

    public JukeboxSetting next() {
        return this == PLAY_OVER ? PAUSE_MUSIC : PLAY_OVER;
    }
}
