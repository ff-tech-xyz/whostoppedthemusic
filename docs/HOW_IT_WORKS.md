# How Who Stopped The Music Works

Who Stopped The Music is a client-side Fabric mod. It does not need a server install and it does not add any audio files. It changes how the client schedules and controls official Minecraft music.

## Default no-gap mode

Controls are off by default. In that default mode, Minecraft still chooses music the vanilla way. The mod only fixes the parts that make playback feel broken:

1. The long random delay between songs is removed, so the next vanilla song can start without waiting through a silence gap.
2. Forced vanilla stops that would cut a song short are ignored, so the current song can finish naturally.
3. When a song or record starts, the mod maps the sound id to a readable song name and shows a compact top-of-screen toast.

This means players get continuous vanilla music without needing to touch the Music Settings screen.

## Music Settings mode

Press `Ctrl+M` or click the pause-menu cassette to open Music Settings.

When the **Controls** checkbox is on, Who Stopped The Music takes over music playback for the selected official tracks. The screen contains:

- a search field for filtering songs,
- a checkbox list of official soundtrack songs and music discs,
- Play or Stop,
- Previous and Next,
- Shuffle on or off,
- Select All and Deselect All,
- a vertical Music Volume slider that edits Minecraft's built-in Music volume option.

When **Controls** is off, vanilla chooses songs again. The no-gap and no-cutoff behavior remains active.

## Deterministic shuffle

Older shuffle behavior picked a fresh random song whenever Next was pressed. That meant Previous could not reliably go back to the song that just played, and repeats could happen before every selected song had been heard.

The current shuffle behavior is predetermined:

1. When shuffle turns on, the mod builds a shuffled cycle from all currently selected songs.
2. If a song is already playing, that song is treated as the cycle anchor.
3. Next moves forward through the cycle.
4. Previous moves backward through the playback history, so it returns to the previous shuffled song instead of choosing a new random one.
5. A selected song is not used again until every selected song in that cycle has been used.
6. After the cycle is exhausted, the mod reshuffles the selected songs and starts a new cycle.
7. If the selected song list changes, the shuffle order is rebuilt around the current song.

## Stop and silence behavior

Stop is intentionally stronger than a normal skip. When panel controls are on, Stop silences music and records until the player starts playback again or turns Controls off. Deselect All also stops playback and keeps music silent because there are no selected tracks to play.

## Persistence

Client settings are saved to:

```text
config/whostoppedthemusic.properties
```

The saved state includes:

- whether Controls is on,
- whether Shuffle is on,
- disabled song ids,
- the current song id,
- whether playback was intentionally stopped or silenced.

## User interface surfaces

- **Pause menu cassette:** compact current-song display, Previous, Next, and click-to-open settings.
- **Music Settings:** full controls, song selection, search, deterministic shuffle, and volume.
- **Top-of-screen toast:** compact PyreHaven-style Now Playing notice when the active song changes.

## Boundaries

Who Stopped The Music does not add new music, does not change sound effects, does not change ambient sounds, and does not require anything on the server.
