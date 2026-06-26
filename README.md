# Who Stopped The Music

A client-side Minecraft Fabric mod for **Minecraft 26.2** that keeps vanilla music moving, names what is playing, and optionally gives players an in-game music panel.

## What it does

- Eliminates the random silence between vanilla music tracks.
- Prevents vanilla music from being cut short mid-song.
- Shows a brief top-of-screen **Now playing** HUD with mapped song names like `Aria Math`, `Sweden`, and `Pigstep`.
- Adds a **Music Panel** opened with `Ctrl+M`.
- Adds a **Music Panel** button to the pause menu.
- Starts with **Controls off** by default. When off, vanilla controls music normally, just without the random gaps.
- When Controls are on, lets players play, queue, shuffle, enable, or disable official Minecraft soundtrack songs and music-disc songs.
- Saves controls, shuffle, and disabled-song settings to `config/whostoppedthemusic.properties`.

## What it doesn't do

- Does not add new music.
- Does not affect sound effects or ambient sounds.
- Does not require a server install.

## Installation

Drop the `.jar` into your `mods/` folder. Client-side only.

## Compatibility

- Minecraft 26.2
- Fabric Loader 0.19.3+
- Java 21+

## License

MIT
