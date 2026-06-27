# Changelog

## [1.1.3-test.8] - 2026-06-27

### Fixed
- Removed the stacked Music Settings fallback so normal GUI-scale screens use the compact side-by-side layout: search plus track list on the left, controls on the right, and the vertical volume slider on the far right.

## [1.1.3-test.7] - 2026-06-27

### Changed
- Wide Music Settings now keeps the title and current song centered, puts search plus the full-height song list on the left, stacks the controls on the right, and moves Music Volume to a vertical far-right slider.

## [1.1.3-test.6] - 2026-06-27

### Added
- Music Settings now includes a Music Volume slider wired directly to Minecraft's built-in Music volume option.
- Long song names in the list now scroll sideways in a smooth player-style marquee instead of being cut off.

### Changed
- Wide Music Settings layout now stacks controls on the left and keeps the song list on the right.
- The Stop button now becomes Play when nothing is currently playing; pressing Play starts a random enabled song.

## [1.1.3-test.5] - 2026-06-27

### Fixed
- Shuffle now builds a predetermined cycle when it is turned on, walks forward and backward through that cycle, and reshuffles only after every enabled song has been used.
- Previous now returns to the prior shuffled song instead of asking shuffle for a fresh random pick.

### Changed
- Replaced the top-of-screen song-change popup with a compact PyreHaven-style toast instead of the busier cassette overlay.

## [1.1.3-test.4] - 2026-06-26

### Fixed
- Stop now forces the music and record channels silent while panel controls are on, instead of letting vanilla immediately refill the silence.
- Deselecting the currently playing track now stops it and advances to the next enabled track; Deselect All stops playback and keeps music silent.
- Controls, shuffle, disabled songs, the current song, and the stopped/silenced state now persist through world changes and game restarts.

### Changed
- Made Music Settings more minimal: compact title/status text, soundtrack list on the left, and controls on the right on normal-width screens.
- Shrunk the pause-menu cassette control so it takes less room at the top of the pause screen.

## [1.1.3-test.3] - 2026-06-26

### Added
- Cassette-player UI at the top of the pause menu. Click the cassette to open Music Settings.
- Current-song display on the pause-menu cassette, Music Settings cassette, and top-of-screen song-change HUD.
- Previous/next arrow controls in both the pause menu and Music Settings.
- Search bar plus Select All and Deselect All controls in Music Settings.

### Changed
- Replaced the queue UI with simpler checkbox-based enable/disable selection while keeping shuffle.
- Polished the music HUD into a cassette-style overlay.

## [1.1.3-test.2] - 2026-06-26

### Added
- Official Minecraft soundtrack song-name mapping for the music panel, including album tracks such as `Aria Math`, `Sweden`, and `Mice on Venus`.
- Music-disc songs in the panel, including `Pigstep`, `otherside`, `Creator`, `Lava Chicken`, and the rest of the official Java Edition discs.
- Top-of-screen `Now playing` HUD notice whenever music or records start, mapped to the real song name when known.
- Top-level `Controls` toggle in the panel, off by default.

### Changed
- When `Controls` is off, the panel controls are disabled and vanilla controls music normally, preserving the original no-random-gap behavior.
- When `Controls` is on, the panel can directly play exact soundtrack and disc resources instead of only vanilla weighted music-event pools.

## [1.1.3-test.1] - 2026-06-26

### Added
- Music Panel screen opened with `Ctrl+M`.
- Pause-menu Music Panel button.
- Vanilla music event list with play-now, queue, shuffle, skip, stop, clear-queue, and enable/disable controls.
- Client settings saved to `config/whostoppedthemusic.properties`.

### Changed
- Vanilla music is suppressed while the custom queue or shuffle mode is active so the panel stays in charge of playback.
- README now matches the current Minecraft 26.2 Fabric release.

## [1.1.2] - 2026-06-26

### Changed
- Updated Fabric target to Minecraft 26.2.

## [1.1.1] - 2026-06-26

### Changed
- Prepared official release build.

## [1.0.0] - 2026-03-01

### Added
- Initial client-side release.
- Removed long vanilla silence gaps between music tracks.
- Prevented no-arg vanilla music stops from cutting active songs short.
