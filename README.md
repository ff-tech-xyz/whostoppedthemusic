# Who Stopped The Music

A client-side Minecraft Fabric mod for **Minecraft 26.2** that keeps vanilla music moving, shows the real song name, and adds an optional in-game music controller for the official Minecraft soundtrack and music discs.

## What it does now

- Removes the long random silence gap between vanilla music tracks.
- Prevents vanilla music from being cut short mid-song.
- Shows a compact top-of-screen **Now playing** toast when music or records start, using mapped song names such as `Aria Math`, `Sweden`, and `Pigstep`.
- Adds a compact cassette player to the top of the pause menu. The cassette shows the current song, has previous and next buttons, and opens Music Settings when clicked.
- Adds **Music Settings**, opened with `Ctrl+M`, with search and the full song list on the left, controls on the right, and a vertical Music Volume slider on the far right.
- Starts with **Controls off** by default. In this mode, vanilla still chooses music normally, but the no-gap and no-cutoff fixes stay active.
- When **Controls** are on, the mod takes over playback for selected official soundtrack songs and music discs.
- Lets players play, stop, skip previous, skip next, enable shuffle, search songs, select all songs, deselect all songs, and enable or disable individual tracks.
- Saves controls, shuffle, disabled songs, current song, and stopped or silenced state to `config/whostoppedthemusic.properties`.

## Deterministic shuffle

Shuffle is no longer random every time Next is pressed. When shuffle turns on, the mod builds a shuffled cycle from the currently selected songs.

- Next walks forward through that predetermined cycle.
- Previous walks backward through the songs that actually played.
- A selected song should not repeat until every selected song in the current cycle has played.
- When the cycle is exhausted, the mod reshuffles the selected songs and starts a new cycle.
- If the selected song list changes, the shuffle order is rebuilt around the current song so playback stays predictable.

## Music Settings behavior

- **Controls off:** vanilla controls track choice. The panel is mostly read-only, but no-gap playback still works.
- **Controls on:** the panel can directly play selected official soundtrack and music-disc tracks.
- **Play:** starts a random selected song when nothing is currently playing.
- **Stop:** stops music and keeps it silent until the player starts music again or turns controls off.
- **Previous / Next:** move through the deterministic shuffle cycle when shuffle is on, or choose from selected songs when shuffle is off.
- **Shuffle:** creates and uses a full selected-song cycle before repeating songs.
- **Select All / Deselect All:** enable or disable every listed track. Deselect All stops playback and keeps music silent.
- **Music Volume:** changes Minecraft's built-in Music volume option.

## What it does not do

- Does not add new music.
- Does not affect sound effects or ambient sounds.
- Does not require a server install.
- Does not change jukebox mechanics beyond showing the song-name toast when records start.

## Installation

Drop the `.jar` into your `mods/` folder. Client-side only. It works in singleplayer and on multiplayer servers without a server-side install.

## Compatibility

- Minecraft 26.2
- Fabric Loader 0.19.3+
- Java 21+

## More details

See [`docs/HOW_IT_WORKS.md`](docs/HOW_IT_WORKS.md) for implementation notes, config behavior, and exact shuffle rules.

## Source

https://github.com/ff-tech-xyz/whostoppedthemusic

## License

MIT
