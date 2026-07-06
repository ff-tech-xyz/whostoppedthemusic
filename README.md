# Who Stopped The Music

Who Stopped The Music is a client-side Minecraft mod that makes the game’s music feel less broken and easier to control.

Minecraft normally leaves long quiet gaps between songs, and sometimes the music stops in ways that feel random. This mod keeps the soundtrack moving, shows you what song is playing, and gives you a simple in-game music controller when you want more control.

## What it does

- Removes the long silent gaps between normal Minecraft music tracks.
- Helps songs keep playing instead of getting cut off too early.
- Shows a small "Now playing" notice when music or records start.
- Adds a cassette-style music button to the pause menu.
- Adds a Music Settings screen, opened with `Ctrl+M`.
- Lets you play, stop, skip, shuffle, search, and choose which official songs can play.
- Works with the Minecraft soundtrack and music discs.
- Saves your music settings between game sessions.

By default, the mod stays out of the way. Minecraft still chooses the music like normal, but the annoying silence gaps and early cutoffs are fixed. If you turn on the extra controls, you can take over playback yourself and choose what you want to hear.

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
