package xyz.pyrehaven.whostoppedthemusic.music;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.valueproviders.MultipliedFloats;
import xyz.pyrehaven.whostoppedthemusic.mixin.SoundEventReferenceAccessor;
import xyz.pyrehaven.whostoppedthemusic.mixin.WeighedSoundEventsAccessor;

public final class TrackLibrary {
    private List<Track> tracks = List.of();

    public List<Track> tracks() { return tracks; }

    public void rebuild(SoundManager sounds) {
        var unique = new LinkedHashMap<String, Track>();
        for (var event : sounds.getAvailableSounds().stream().sorted().toList()) {
            boolean disc = event.getPath().startsWith("music_disc.");
            if (!disc && !event.getPath().startsWith("music.")) continue;
            for (var sound : flatten(sounds, event, new HashSet<>())) {
                String id = sound.getLocation().toString();
                var title = Component.translatableWithFallback(TrackNames.key(id, disc ? event.toString() : null), TrackNames.prettify(id));
                unique.putIfAbsent(id, new Track(id, disc ? Track.Kind.DISC : Track.Kind.SOUNDTRACK, title, sound));
            }
        }
        tracks = unique.values().stream().sorted(Comparator.comparing(Track::kind).thenComparing(Track::id)).toList();
    }

    private List<Sound> flatten(SoundManager sounds, Identifier event, Set<Identifier> visiting) {
        var pool = sounds.getSoundEvent(event);
        if (pool == null || !visiting.add(event)) return List.of();
        var result = new ArrayList<Sound>();
        for (var entry : ((WeighedSoundEventsAccessor) pool).whostoppedthemusic$entries()) {
            if (entry instanceof Sound file) {
                result.add(file);
            } else if (entry instanceof SoundEventReferenceAccessor accessor) {
                var reference = accessor.whostoppedthemusic$reference();
                for (var file : flatten(sounds, reference.getLocation(), visiting)) {
                    result.add(new Sound(file.getLocation(), new MultipliedFloats(file.getVolume(), reference.getVolume()),
                            new MultipliedFloats(file.getPitch(), reference.getPitch()), reference.getWeight(), Sound.Type.FILE,
                            file.shouldStream() || reference.shouldStream(), file.shouldPreload(), file.getAttenuationDistance()));
                }
            }
        }
        visiting.remove(event);
        return result;
    }
}
