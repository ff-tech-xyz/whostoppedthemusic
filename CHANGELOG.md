# Changelog

## 1.2.1 — Unreleased

Calmer, clearer screens.

- One accent color. Orange now only means on, picked or playing. Everything else is neutral grey.
- Buttons are plain: one fill and one border. The orange stripe on the left of every button and the dark bottom shadow are gone.
- Cassettes keep their shell, label and reels, but lose the extra inner border, stripes, bars and corner dots.
- Basic mode lists songs as compact rows, with select and play on the same line as the song, so you can see many more songs at once. Clicking a row also selects it.
- Frequency and Jukebox buttons show their current value, like "Frequency: Constant".
- Shuffle uses the same on/off switch as Interrupt and Active.
- Reset sits after Copy, is quieter, and asks you to click again before it clears a scenario's songs. While it waits, its text turns bold and a thin bar shows how long you have.
- The weight readout says "plays about 58% of the time" instead of "18 in 31 (58.1%)", and never rounds a song down to 0% or up to 100% when it shares the list.
- Switches that are off now show a grey track, so off is as easy to read as on.
- Buttons you can't use look sunken, not just greyed text.
- The song that's playing gets an orange outline in every list, not only on big cassettes.

## 1.2.0

- Rebuilt for Minecraft 26.3 as a client-only Fabric mod.
- Press M for Vanilla music controls, Basic's one-list-everywhere selection, or Custom's biome and special-scenario soundtracks.
- Give Custom scenarios weighted songs, priority, interrupt, frequency and jukebox settings. Equal priorities combine lists; empty winners let you choose silence.
- Add your own MP3 and OGG files with a confirmed copy, or place them in the instance's music folder. Use them and music discs in Basic or Custom.
- Preview songs from the midpoint, then resume the same background song. Copy or reset a scenario's songs without replacing its other settings.
- Shared Play/Pause/Resume and skip controls, separate Basic Stop and Shuffle, searchable cassette lists and title/artist labels. Playing scenario tapes and library cases show red spindle dots, turning reels and notes, including previews; paused or silent tapes don't animate.
- Minecraft's left-side Now Playing notification in every mode, with imported song names. Bottom-left title/pause cassettes keep their Next button. Music Frequency starts on Constant; jukeboxes can hold background music while they play nearby.
- Custom biome sections start collapsed and remain available before joining a world; joining uses the world's exact biome registry.

See the [configuration guide](docs/CONFIGURATION.md) for defaults and settings compatibility.
