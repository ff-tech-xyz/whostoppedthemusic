# Music controls and configuration

Press **M**, or click the bottom-left title/pause cassette. Settings save as you change them. Music volume and Vanilla/Basic Frequency use Minecraft's options; Custom Frequency belongs to each scenario.

## Controls

| Control | What it changes |
| --- | --- |
| Mode | Cycles Vanilla, Basic and Custom; each mode keeps its own selection settings |
| Play / Pause / Resume | Starts music from silence or pauses/resumes the same background source |
| Next | Starts the next available song; Vanilla asks Minecraft for a situational choice |
| Previous | Basic/Custom history for the current deck; not available in Vanilla |
| Stop | Basic only: silence until Play, Next, row Play or restart; not saved |
| Shuffle | Basic only: shuffled deck or list order |
| Music slider | Minecraft's Music volume, without changing Master or Records |
| Frequency | Gap between songs: Default, Frequent or Constant; doesn't cut a playing song |
| Jukebox | Pause music within 64 blocks of a playing jukebox, or Play over |
| Add music | Shared confirmed MP3/OGG copy and permanent local-file removal screen |

Basic search matches titles, artists and file IDs, including approximate matches. Select All / Deselect All affect the whole library and are hidden while a nonblank search is active. All songs start selected, including newly discovered songs. A row's Play can play an unticked song without selecting it.

Music Toast in Minecraft's sound options controls the vanilla left-side song-change notification in all modes. Imported files use their literal filename title; known artists appear with the title on one line. Minecraft owns notification timing and accessibility duration. The pause menu keeps its cassette instead of duplicating the vanilla pause song line. These choices add no mod settings or migration.

## Custom scenarios

Overworld, Nether, End and other namespace groups start collapsed, followed by Misc scenarios. Click a header to expand it; search reveals matching rows. Vanilla biomes are available at the title menu. Joining replaces them with the world's exact biome registry. Biomes with a vanilla dimension tag join that group even if supplied by a mod. Removed biomes disappear from the browser but keep their saved overrides.

The camera-block biome always competes. Specials compete only when enabled and their condition is true:

| Scenario | Condition | Default priority | Enabled initially | Initial songs |
| --- | --- | --- | --- | --- |
| Credits | Credits screen | Always first, before Menu | Always | Loaded credits pool |
| Menu | No player, unless credits are showing | Always first | Always | Loaded menu pool |
| Boss | Vanilla boss-overlay music flag (normally the dragon fight) | 10 | Yes | Loaded dragon pool |
| Aquatic | Player underwater and camera biome tagged ocean or river | 8 | Yes | Loaded underwater pool |
| Thunder | Thunderstorm with precipitation at the camera block | 7 | No | Empty |
| Rain | Rain with RAIN precipitation at the camera block | 6 | No | Empty |
| Snow | Rain with SNOW precipitation at the camera block | 6 | No | Empty |
| Cave biomes | Lush caves, dripstone caves, deep dark or sulfur caves at the camera block | 5 | Always | Loaded biome pool |
| Creative | Player has instabuild and mayfly | 4 | Yes | Loaded creative pool |
| Underground | No sky visibility and camera Y below the configured level | 3 | No | Empty |
| Other biomes | Current camera-block biome | 2 | Always | Loaded biome/dimension pool |

Thunder can compete alongside Rain or Snow. Dry precipitation means no weather special applies. Weather starts with **Must be outside** on, requiring sky visibility and rain-heightmap exposure. Underground starts at **Y level 64**: Y63 can qualify, Y64 cannot; an open pit with sky visibility doesn't qualify.

Higher priority wins. Ties combine the winning lists, summing weights for shared songs. The most frequent winning Frequency is used; Jukebox pauses if any winner requests Pause music. Interrupt applies when a newly winning scenario requests it. Menu and Credits always interrupt; Boss starts with Interrupt on. Biomes inherit vanilla's replace-current flag; other specials start with Interrupt off. A noninterrupting change lets the outgoing song finish.

An empty winning list means silence, not fallback. An empty list tied with a nonempty winner still combines with that other list. Missing/unplayable songs are skipped; if every winning entry is unavailable, nothing starts. Add songs to an edited Pale Garden list to override its normal zero-music-volume behavior; the Music slider still applies.

### Edit songs and settings

Each scenario starts with **Constant** frequency and **Pause music** for jukeboxes. Biome songs come from the loaded default music slot, with dimension defaults where applicable. Special menu/credits/boss/aquatic/creative lists use their loaded vanilla pools. Resource packs and mods can change those defaults. Pale Garden and empty pools are silent. Runtime weights preserve the loaded vanilla odds, reducing or scaling only when needed to fit the editor's 1–100 range.

Drag from Soundtrack, Music Discs or Your Songs into the right cassette list. Duplicate adds do nothing. A new entry takes the most common weight in that list (the lower weight breaks a tie), or 10 for an empty list. Focus a cassette and use the slider or arrow keys; Home/End set 1/100. The displayed chance is the weight divided by this list's total, before any live tie merge. Deck draws are weighted without replacement, with history retained for up to 32 resolution keys.

Preview plays up to three seconds from the midpoint on the Music channel, fades, and resumes the same background source. Press again to cancel. Previewing another song, removing the previewed song or leaving the editor also ends it. Manual pause, silence and jukebox hold remain in effect afterward. Preview doesn't enter playback history, and Music mute also mutes preview.

The matching library case and scenario cassette use the actual background or preview source: red spindle dots identify the loaded track, reels turn while its native channel advances, and notes appear only during playback. Pausing freezes reels and hides notes; stopping clears the playing marker. Muting changes volume, not channel progress. Unrelated songs and the held drag cassette remain still.

Reset restores only the loaded default songs. Copy songs opens the scenario picker and replaces only songs and weights. Both preserve priority, interrupt, frequency and other settings. The edited marker describes changes to songs, not changes to every setting.

On the main screen, Frequency/Jukebox edit all current winners, even a silent winner. In the editor they edit that scenario. Right-click opens **Apply to every scenario?** without changing the value. Yes copies the displayed value to every special and known biome and sets the default for future biomes. No, Escape or clicking outside leaves settings unchanged. Custom never writes Minecraft's global Frequency option.

## Your music folder

Use **Add music → Open music folder** to find the actual game instance's `music/` directory. It is beside that instance's `mods/` and `config/`, not a world-save folder. Discovery is flat: regular files ending in `.mp3` or `.ogg`, case insensitive. Subdirectories and links aren't imported. Filenames, without extensions, are shown literally as titles.

Drop files on Add music, then confirm. Unsupported files reject the whole drop. Duplicate filenames get numbered copies; originals remain in place. Removing a file there permanently deletes only the selected local copy after a confirmation naming it. Removing a song in the scenario editor only edits that scenario. Reopen the song browser or reload resources to rescan manually placed or repaired files. Can't play marks decode failure; a missing scenario ID remains saved for when its file returns.

## Settings file

The mod loads `<instance>/config/whostoppedthemusic.json` at startup. Prefer the UI; to edit JSON, close Minecraft, change the file and relaunch. Writes use a temporary file in the same directory followed by an atomic replacement.

| Field | Default / meaning |
| --- | --- |
| `configVersion` | Saved as `2` |
| `firstRunDone` | `false` until vanilla Frequency is initialized to Constant and saved; then `true` |
| `mode` | `VANILLA`; also accepts `BASIC` and `CUSTOM` |
| `jukebox` | `PAUSE_MUSIC`; global Vanilla/Basic choice, or `PLAY_OVER` |
| `basic.shuffle` | `true` |
| `basic.unticked` | `[]`; only excluded IDs are stored, so new tracks start selected |
| `custom.defaults.frequency` | `CONSTANT`; also `DEFAULT` or `FREQUENT` |
| `custom.defaults.jukebox` | `PAUSE_MUSIC`; also `PLAY_OVER` |
| `custom.knownBiomes` | `{}`; discovered biome IDs mapped to browser groups |
| `custom.scenarios` | `{}`; sparse overrides keyed by `biome/<namespace>/<path>` or `special/<name>` |

Scenario overrides accept `priority` (1–10), `active`, `interrupt`, `frequency`, `jukebox`, `mustBeOutside`, `yLevel` (whole number) and `songs`. Only meaningful controls are exposed for each kind: biomes have no active switch; Menu/Credits always win and interrupt; only weather exposes outside and only Underground exposes Y level. Defaults are described above.

The `songs` field has two different meanings:

- Omitted: inherit the current loaded default songs, including later resource changes.
- `[]`: deliberately empty; a winning scenario is silent unless tied with songs from another winner.

This example selects Custom, gives Forest a Sweden/Pigstep mix and enables a silent Rain scenario. It's an edited setup, not the generated defaults:

```json
{
  "configVersion": 2,
  "firstRunDone": true,
  "mode": "CUSTOM",
  "jukebox": "PAUSE_MUSIC",
  "basic": { "shuffle": true, "unticked": [] },
  "custom": {
    "defaults": { "frequency": "CONSTANT", "jukebox": "PAUSE_MUSIC" },
    "knownBiomes": { "minecraft:forest": "overworld" },
    "scenarios": {
      "biome/minecraft/forest": {
        "songs": [
          { "id": "minecraft:music/game/sweden", "weight": 25 },
          { "id": "minecraft:records/pigstep", "weight": 10 }
        ]
      },
      "special/rain": { "active": true, "songs": [] }
    }
  }
}
```

Resource song IDs are sound-file IDs such as `minecraft:music/game/sweden` or `minecraft:records/pigstep`, not event-pool IDs. A local file `My song.mp3` has ID `custom:My song.mp3`, including its extension and case. Each song appears once per list. On load, weights clamp to 1–100 and priorities to 1–10. Unknown fields are ignored. Invalid Custom scenario/known-biome entries are warned and skipped without discarding good preferences or other scenarios; malformed root data and root enum values trigger broken-file recovery.

### Migration and recovery

Version-1 JSON from earlier 1.2.0 builds loads without rewriting it. Mode, global Jukebox, Basic Shuffle/exclusions and `firstRunDone` are preserved. Custom starts at its defaults, independent of the global jukebox choice. The next settings change saves version 2. Setting `firstRunDone` to true prevents first-launch frequency initialization from running again.

The 1.1.x `whostoppedthemusic.properties` file is not migrated. A malformed JSON file is renamed to `whostoppedthemusic.json.broken` and defaults are used; a later corrupt file replaces that recovery copy. Stop the game and inspect that file before restoring edits. Save IO failures log a warning and keep changes active in memory; a later successful save persists them. Startup read/recovery IO failures still report an error.

[Player overview](../README.md) · [Architecture and build commands](ARCHITECTURE.md)
