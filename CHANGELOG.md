# Changelog

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
