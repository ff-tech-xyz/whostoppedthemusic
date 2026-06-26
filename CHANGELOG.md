# Changelog

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
