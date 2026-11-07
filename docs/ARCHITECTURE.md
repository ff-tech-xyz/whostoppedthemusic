# Who Stopped The Music architecture

## What it is

Who Stopped The Music is a client-side Fabric mod that lets players choose what music Minecraft plays. It plugs into vanilla's own music player instead of running a second one next to it. It has three modes:

- **Vanilla:** Minecraft selects the music. Play, Pause/Resume and Next control its active background music without affecting Records or sound effects.
- **Basic:** one song list for the whole game. Tick the songs you want and get Play/Pause/Resume, separate Stop, Previous, Next and Shuffle controls.
- **Custom:** every place music can play (each biome pool, the Nether, the End, underwater, creative, the title screen and so on) is a button. Inside each one you choose the songs and give each a weight from 1 to 30.

Players can also drop their own `.mp3` and `.ogg` files into a `music` folder, or drag them onto the Add Songs screen, and use them in Basic or Custom. Music discs can be used too.

Version 1.2.0 is a **complete rebuild**. The compact cassette artwork/placement is ported from main; the old controller, build setup and extra folders do not carry over.

## Target

- Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3, Java 25
- Runs on: **client only** (`"environment": "client"`); no server install, works on any server
- Depends on: Fabric API, plus `dev.mccue:jlayer-decoder` bundled inside the jar for MP3 decoding
- License: CC0-1.0, the same as Happy Artillery, OMWH and 3rdvive. The bundled decoder keeps its own LGPL license; see "Bundled library" below

## How vanilla 26.3 picks music (what we hook into)

`MusicManager.tick()` already runs every client tick. Each tick it does this:

1. It asks `Minecraft.getSituationalMusic()` what should be playing. That returns `Musics.MENU` on the title screen and `Musics.END_BOSS` in the dragon fight. Otherwise it reads the `background_music` environment attribute at the camera; the dimension sets the base value and the biome can override it. Each value has a default, a creative and an underwater option.
2. The answer is a `Music`: a sound event (the **pool**, such as `minecraft:music.overworld.swamp`) plus min/max delay and a "replace current music" flag.
3. If the pool changed and the flag is set, the current song fades out.
4. Otherwise it counts down the delay, which the Music Frequency option (Default / Frequent / Constant) shortens. Then `startPlaying(Music)` creates a sound instance for the pool, and the sound engine picks a random file from the pool's weighted entries in `sounds.json`.
5. `showNowPlayingToastIfNeeded()` shows vanilla's `NowPlayingToast`. The pause screen also draws it when `options.musicToast()` allows.

A **tag** in this mod is one of those pools. Our hooks sit inside steps 1, 4 and 5. We never add a second tick loop.

## Structure

Current implemented slice (2026-11-07): Settings and first-launch Constant, Vanilla
transport, Basic soundtrack/disc selection and playback, shared controls and one SongToast in both modes,
and nearby jukebox fade/hold in both modes. Custom, imported files and Add Songs
remain planned. The M screen cycles Vanilla/Basic; the compact pause cassette
retains one live title and Next.

The compact cassette retains its player housing, main's centered width/30-pixel
height and y=6 placement. Its title always comes from the actual music instance;
the duplicate vanilla pause song line is suppressed without changing the toast
option. SongToast replaces the in-game notification in both modes. Clicking the tape opens MusicScreen. Both tape views
put one scrolling/clipped title between two reels and report silence, pause or
mute truthfully.

`MusicDirector` belongs to each vanilla MusicManager. It observes resolved sounds
with allocated channels, including automatic starts from vanilla's own tick. Play
from silence and Next stop the outgoing instance and call vanilla startPlaying with
the current situational Music. Play/Pause/Resume uses only currentMusic's channel
and preserves its playback position on resume. Activity follows the allocated
channel rather than SoundManager.isActive's deletion-grace bookkeeping. The title
comes from currentMusic's resolved Sound, not the pool. Vanilla has no Previous
control, history or sound-selection redirect; Done still returns to the parent. A held pause
freezes MusicManager.tick. MusicChannelMixin owns transport intent on that source,
on the sound thread: a stopped source cannot start when its asynchronous stream
finally attaches; a paused source starts paused and ignores global menu resume.
Basic adds a deck inside this same director; no extra tick loop or global mute exists.

Vanilla's `startPlaying` returns before stream decoding/attachment and `Channel.play`.
OpenAL stop/pause on an INITIAL source does nothing. Issuing those commands only
once therefore left outgoing audio able to start after Next and lost early Pause.
The channel retains stop/pause intent across startup. A stopped late-start channel
is destroyed after attachment, closing its stream without issuing alSourcePlay;
vanilla's normal channel cleanup then releases its handle. Only MusicManager's
own channels are managed; Records and effects retain vanilla behavior.

```text
whostoppedthemusic/
├── .github/workflows/build.yml      # CI: build, unit tests, client game test (copied from RealSize, adapted)
├── .gitignore                       # copied from RealSize
├── LICENSE                          # CC0-1.0 text, copied from happy-artillery
├── LICENSE_jlayer                   # LGPL 2.1 text for the unmodified nested decoder
├── README.md                        # player docs: modes, music folder, settings, install
├── CHANGELOG.md                     # released versions only, starting fresh at 1.2.0
├── MODRINTH.md                      # Modrinth description
├── docs/
│   ├── ARCHITECTURE.md              # this file
│   ├── plans/1.2.0.md               # the plan for this rebuild
│   └── images/                      # README / Modrinth screenshots
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew, gradlew.bat, gradle/wrapper/   # copied from RealSize (Gradle 9.8.1 with checksum)
└── src/
    ├── main/
    │   ├── java/xyz/pyrehaven/whostoppedthemusic/
    │   │   ├── WhoStoppedTheMusic.java
    │   │   │   # Client entrypoint. Wiring only: loads Settings, registers the M key, the
    │   │   │   # pause-screen cassette (ScreenEvents.AFTER_INIT). Owns shared Settings/library,
    │   │   │   # applies first-launch Music Frequency at CLIENT_STARTED. No music logic.
    │   │   ├── Mode.java                    # VANILLA and BASIC; CUSTOM remains planned
    │   │   ├── JukeboxSetting.java          # enum PLAY_OVER, PAUSE_MUSIC
    │   │   ├── Settings.java
    │   │   │   # The only owner of mod settings. JSON at config/whostoppedthemusic.json,
    │   │   │   # loaded once at startup, saved only when the player changes something,
    │   │   │   # written to a temp file and atomically moved into place (same pattern as RealSize).
    │   │   ├── music/
    │   │   │   ├── Track.java               # id, SOUNDTRACK/DISC kind, translated title, loaded Sound
    │   │   │   ├── Tag.java                 # record: pool id, display name, group, vanilla entries (track + weight)
    │   │   │   ├── TrackNames.java          # pure: translated names/fallback and normalized title/artist/typo search ranking
    │   │   │   ├── TrackLibrary.java
    │   │   │   │   # Builds deduplicated soundtrack/disc tracks from loaded weighted sound entries,
    │   │   │   │   # follows event references with cycle protection; rebuilt after SoundManager.apply
    │   │   │   │   # and world registry changes. Matches music/theme in event paths and loaded
    │   │   │   │   # biome/dimension/timeline music plus jukebox definitions; discs win file dedupe.
    │   │   │   │   # Tags and imported files remain planned.
    │   │   │   ├── Deck.java
    │   │   │   │   # Pure logic, no Minecraft classes: weighted shuffle "deck" with history
    │   │   │   │   # for previous/next. Fully unit tested.
    │   │   │   ├── MusicDirector.java
    │   │   │   │   # Vanilla transport, Basic deck/history/stop/row play, one Basic pool,
    │   │   │   │   # truthful current-channel status, jukebox hold and toast selection.
    │   │   │   ├── TrackSoundInstance.java
    │   │   │   │   # The sound instance for a chosen track. Its identifier stays the pool id so
    │   │   │   │   # vanilla's pool-change logic keeps working. Resolves to the chosen file.
    │   │   │   │   # Reuses the loaded Sound on non-positional Music; custom streams remain planned.
    │   │   │   ├── Mp3AudioStream.java      # AudioStream over jlayer-decoder (16-bit PCM)
    │   │   │   └── JukeboxWatcher.java      # reads vanilla's event-driven map and live channels near the camera
    │   │   ├── custom/
    │   │   │   └── MusicFolder.java
    │   │   │       # .minecraft/music/: scan for .mp3/.ogg, copy dropped files in, delete a song,
    │   │   │       # open the folder. Never runs on a timer.
    │   │   ├── ui/
    │   │   │   ├── Theme.java               # PyreHaven colours, spacing and text styles in one place
    │   │   │   ├── MusicControls.java       # shared settings and live transport; panels place Basic additions
    │   │   │   ├── TransportButton.java     # drawn transport symbols, accessible actions and shared button states
    │   │   │   ├── MusicScreen.java         # Vanilla layout; retains BasicPanel across resize/collapse rebuilds
    │   │   │   ├── CassetteArtwork.java     # shared port of main's shell, label and crossed reels
    │   │   │   ├── MusicVolumeSlider.java   # vertical view of vanilla's Music option; keyboard and drag
    │   │   │   ├── BasicPanel.java          # collapsible browser, player placement and Basic-only Previous/Stop/Shuffle
    │   │   │   ├── CustomPanel.java         # Custom body: grid of tag buttons
    │   │   │   ├── TagEditorScreen.java     # picker | songs in tag | song settings
    │   │   │   ├── AddSongsScreen.java      # drop zone, your songs list, remove, open folder
    │   │   │   ├── SongToast.java           # our Now Playing toast (vanilla Toast interface)
    │   │   │   ├── CassetteWidget.java      # top compact cassette, live title and settings link
    │   │   │   └── widget/
    │   │   │       ├── TrackList.java       # smooth clipped cassette browser; ranked results or sections; nested Row owns tick/tape/play
    │   │   │       ├── TagButton.java       # tag name, song count, "edited" marker
    │   │   │       └── WeightSlider.java    # 1-30 slider with "5 in 34" chance text
    │   │   └── mixin/
    │   │       ├── MusicManagerAccessor.java        # currentMusic read-only, truthful cassette status
    │   │       ├── MusicManagerMixin.java           # owns director; observes automatic/manual starts and holds pause
    │   │       ├── PauseScreenMixin.java            # hides duplicate vanilla pause-menu song line
    │   │       ├── SoundManagerAccessor.java        # read-only sound engine access for current music channel
    │   │       ├── SoundEngineAccessor.java         # read-only instance/channel map
    │   │       ├── MusicChannelMixin.java           # music source stop/pause intent across async startup and menu resume
    │   │       ├── ClientJukeboxMixin.java          # read-only LevelEventHandler playingJukeboxSongs map
    │   │       ├── ClientLevelAccessor.java         # current level's LevelEventHandler
    │   │       ├── SoundEventReferenceAccessor.java # event-reference Sound in vanilla's weighted wrapper
    │   │       ├── SoundLibraryMixin.java           # rebuild library after loaded sound registry applies
    │   │       ├── TimelineMusicAccessor.java        # read-only timeline attribute tracks for music keyframes
    │   │       ├── MusicWorldMixin.java             # keep Basic source during world changes; rebuild library from new registries
    │   │       ├── LevelSoundMixin.java             # stop other world sources and clear scheduled/ticking sounds
    │   │       ├── NowPlayingToastMixin.java        # suppress vanilla duplicate in both modes, including its slide-out
    │   │       └── WeighedSoundEventsAccessor.java  # read-only access to a pool's entries (accessor, no logic)
    │   └── resources/
    │       ├── fabric.mod.json
    │       ├── whostoppedthemusic.mixins.json
    │       └── assets/whostoppedthemusic/
    │           ├── icon.png                 # kept from main (the Modrinth icon)
    │           └── lang/en_us.json          # every piece of UI text, no hard-coded English
    ├── test/java/xyz/pyrehaven/whostoppedthemusic/
    │   ├── DeckTest.java            # weights, deck cycle, no repeat across refills, prev/next, empty deck
    │   ├── SettingsTest.java        # defaults, round trip, bad/unknown file, atomic write, first-run flag
    │   ├── TrackNamesTest.java      # translated names/fallback; title/prefix/artist/token/typo ranking and rejected queries
    │   └── MusicFolderTest.java     # file filter, copy, duplicate names, delete
    └── gametest/
        ├── java/xyz/pyrehaven/whostoppedthemusic/
        │   ├── ClientSmokeGameTest.java # packaged UI/transport; controlsOnly selects shared transport/toast/held Stop/isolation
        │   ├── DiscoveryGameTest.java   # discoveryOnly: real loaded events, definition registries, dedupe, reload/world lifecycle
        │   └── test/mixin/TransportStreamGate.java # holds decoded vanilla streams before source attachment
        └── resources/
            ├── fabric.mod.json         # development-only client test entrypoint
            ├── assets/discovery/sounds.json # event-name, arbitrary definition, nested/cyclic alias fixtures over vanilla audio
            ├── assets/music_namespace/sounds.json # namespace-only rejection fixture
            └── whostoppedthemusic-test.mixins.json # development-only stream gate
```

## Key pieces

| Piece | Job | Talks to |
| --- | --- | --- |
| `Settings` | Holds and saves every mod setting | Everything reads it; only UI actions change it |
| `TrackLibrary` | Knows every track (soundtrack, discs, custom files) and every tag with its vanilla songs and weights | `SoundManager`, `MusicFolder`, `TrackNames` |
| `Deck` | Picks the next song from a weighted list; remembers history | Used only by `MusicDirector` |
| `MusicDirector` | Decides what plays for the current mode and tag; runs the buttons | `Settings`, `TrackLibrary`, `Deck`, `JukeboxWatcher` |
| `MusicManagerMixin` | Hands vanilla's choice to `MusicDirector` at the right moments | `MusicDirector`, `SongToast` |
| `TrackSoundInstance` | Plays the chosen file, including MP3/OGG from disk | Sound engine, `Mp3AudioStream` |
| `MusicScreen` and panels | All the UI | `Settings`, `MusicDirector`, `TrackLibrary` |

## Modes in detail

### Vanilla
- Song selection stays vanilla. The shared SongToast replaces the in-game notification; the compact cassette replaces the pause song line.
- Left: enlarged bare tape, with one live title between two reels. Right: Vanilla/Basic mode button, Frequency, Jukebox and Play/Pause/Resume/Next. A vertical Music volume slider is at the far right.
- Master and Music Toast remain in vanilla Options, not this screen. Music and Frequency write the existing vanilla options; Jukebox is saved in Settings and used by the director.

### Basic
- There is **one pool for the whole game**: title screen, every biome and dimension, underwater, creative, the dragon fight and the credits. Vanilla never switches pools, so a song carries on when you change biome or leave the world.
- The song list has three sections: **Soundtrack**, **Music Discs** and **Your Songs**. Tick a song to put it in the deck.
- Every ticked song has weight 1. Shuffle on: the deck is shuffled. Shuffle off: songs play in list order. When the deck is used up it refills and repeats.
- No songs ticked: nothing plays.
- Controls: Play on each row (plays that song now, then the deck carries on), Play/Pause/Resume, separate Stop, Previous, Next, Shuffle, Select All, Deselect All, search, and Add Songs.
- Stop holds silence until the player presses Play, Next or a row's Play, or restarts the game. It is not saved.
- Defaults: every soundtrack song and every disc ticked. A newly added custom song starts ticked.

The implemented Basic body has Soundtrack and Music Discs only. OG Moo-cow's
2026-11-07 browser sketch supersedes the earlier full-width list. Left: search,
a smoothly scrolling cassette viewport, then Select All / Deselect All. Each song
has a selection toggle and Play Now, with a full central cassette and clipped
neighbours above/below as the list scrolls. Narrow rows put their controls below
the tape. Right: stacked Basic mode, Frequency and Jukebox, then Previous /
Play/Pause/Resume / Next, separate labeled Stop beside Shuffle, and a live current-song cassette underneath. Music
volume is at the far right; Done remains below both columns.

The inward arrow removes the browser from rendering, input and keyboard traversal
and centers the player. The outward arrow restores it. BasicPanel retains the
same search/list widgets across collapse and resize; TrackList preserves relative
song position when row height changes. Ticks remain in Settings, not UI state.
Empty/whitespace search shows stable kind/file-id order and section headings.
Nonempty search ranks normalized exact titles, title prefixes, artist matches,
substrings/tokens and bounded edit-distance typos, in that order; ties retain
library order and no headings appear. Select All / Deselect All are hidden,
inactive and action-guarded while a trimmed query is nonempty. With an empty
query they affect the whole library. Settings stores mode, shuffle and unticked
ids; Stop and bounded transport history stay in memory. Previous is unavailable
during Stop. Your Songs and Add Songs arrive with functions 9–10.

Basic selects through vanilla's startPlaying sound-instance seam and tick pool
lookup, using one non-replacing Music with GAME delays. Biome volume fades do not
mute this global pool. The world-change stop path keeps only the manager's Basic
source while retiring old-world sounds and queues. Resource reloads retain normal
sound-engine reload behavior. A live Basic source survives world exit/entry;
jukebox entries belong to the old level and cannot leak into the next one.

### Custom
- The body is a grid of **tag buttons**, one per pool, grouped: Title Screen & Special (menu, credits, dragon), Overworld, Nether, End, Underwater & Creative, and Other for pools from mods or resource packs.
- Each button shows the song count and an "Edited" marker once changed.
- **Every tag starts exactly as vanilla has it** (same songs, vanilla weights clamped to 1–30) until the player changes it. Only edited tags are saved.
- Clicking a tag opens the **Tag Editor**:
  - **Middle:** the songs in this tag.
  - **+ button:** opens the **left side**, a list of every available song (soundtrack, discs, your songs) with a search bar. Drag a song into the middle to add it. Double-click also adds it, for trackpads.
  - **Click a song in the middle:** the **right side** shows its settings in this tag: a weight slider from 1 to 30, its chance (for example "5 in 34 (15%)"), Preview, and Remove from tag.
  - **Reset to vanilla** button for the tag.
- **Weight means "how many copies of the song are in the deck."** With weights 5 and 29, a full deck has 34 cards, and the first song plays 5 of them. The deck is drawn without putting cards back and refills when empty. The same song never plays twice in a row across a refill when the tag has two or more songs.
- A tag with no songs: nothing plays there.

## The screen

OG Moo-cow's supplied sketches and revised contract govern this screen. The
mode button cycles exactly “Vanilla” and “Basic”. Custom arrives with its playback.
MusicControls builds the shared mode/Frequency/Jukebox settings and primary/Next
transport for both panels. Panels place them; Basic adds Previous, labeled Stop
and Shuffle. TransportButton draws Play/Resume, Pause and distinct Next/Previous
symbols, with translated narration and tooltips. The pause-menu Next and browser
row Play use this same button. Theme owns their dimensions, button frames and
colors as well as surrounding dark surfaces; no player housing surrounds the large tape.

```text
┌────────────────────────────────────────────────────────────┐
│ Who Stopped The Music                                      │
│                               [ Vanilla             ] Music│
│  ┌──── bare tape ──────────┐   [ Frequency: Constant  ]   │  │
│  │ [reel] song title [reel]│   [ Jukebox: Pause music ]   ●  │
│  └────────────────────────┘   [Play/Pause] [Next]        │  │
│                                                        70% │
│                         [ Done ]                           │
└────────────────────────────────────────────────────────────┘
```

- Music volume and Frequency use **vanilla's own options** from `minecraft.options`. Their themed views update those options directly and save on closing; no duplicate volume/frequency state exists.
- The screen opens with **M** (rebindable in Controls → Who Stopped The Music) or by clicking the pause-menu cassette.
- **PyreHaven style:** `Theme.java` owns orange `#E46011`, blue `#27607C`, glow `#4FA8D8`, background `#070B10`, card `#0E141C`, text `#F2F4F7`, and layout dimensions. Orange is the main accent; blue/glow mark edges and focus. **No logo anywhere in the UI.**
- The UI must work at GUI scales 1–4 and down to a 320×240 window. Narrow windows shrink the tape and right column while preserving left/right placement; titles scroll inside their reel window.

## Hooks

Implemented hooks are checked against 26.3 bytecode and the packaged client.
Custom selection and custom-file streaming remain planned.

1. **`MusicManagerMixin`**
   - Hold `tick()` during an explicit transport pause, Basic Stop or a nearby jukebox; observe `startPlaying(Music)` results; clear channel pause on `stopPlaying()`.
   - Redirect the `getSituationalMusic()` call inside tick.
     - **Basic:** return one constant `Music` (`whostoppedthemusic:basic`, built with `Holder.direct`, vanilla `GAME` delays, `replaceCurrentMusic = false`).
     - **Vanilla / Custom:** pass vanilla's answer through.
     - **Jukebox set to Pause and a nearby jukebox is playing:** invoke vanilla `fadePlaying(0)` and hold starts. Leaving 64-block camera range, an ended channel or a stop event releases the hold. Play over leaves vanilla behavior alone. Explicit transport pause and Basic Stop remain held.
   - `startPlaying(Music)`: the verified `SimpleSoundInstance.forMusic` call is the existing sound-selection seam.
     - **Vanilla:** normal starts and sound resolution are unchanged.
     - **Basic:** ask `MusicDirector` for the next deck/history/row track. Build a `TrackSoundInstance` whose identifier is the Basic pool id. If there's no track or playback is held, cancel before sound creation. Custom remains planned.
   - Both toast calls in `startPlaying` and `showNowPlayingToastIfNeeded()` delegate to the director. Both modes reuse one `SongToast` through the toast manager, following `options.musicToast()` and the notification duration multiplier. New sound identities replace its title and reset its duration; repeated callbacks for the same sound leave the timer alone. `NowPlayingToastMixin` suppresses the legacy duplicate in both modes, including option callbacks and slide-out. The replacement hides when its sound stops, is replaced or Music Toast disallows notifications.
2. **`PauseScreenMixin`:** implemented: return false from `rendersNowPlayingToast()` for the full pause menu so the compact cassette owns the title. The cassette and Next are added through Fabric `ScreenEvents.AFTER_INIT`, not this mixin. `MusicChannelMixin` retains stop/pause intent on MusicManager-owned sources across asynchronous startup and global menu resume; accessors expose the existing channel without replacing the sound engine.
3. **`ClientJukeboxMixin` / `ClientLevelAccessor`:** read `ClientLevel.levelEventHandler.playingJukeboxSongs`, which 26.3's `LevelEventHandler.playJukeboxSong` / `stopJukeboxSong` already own. The watcher verifies allocated live channels; no duplicate position map, timer or world lifecycle state exists.
4. **`WeighedSoundEventsAccessor` / `SoundEventReferenceAccessor`:** read weighted files and event-reference entries recursively, preserving volume/pitch multipliers and stream flags. `SoundLibraryMixin` rebuilds after SoundManager applies the loaded registry; `MusicWorldMixin` rebuilds with the arriving world's registries (or empty registries on exit). `TimelineMusicAccessor` exposes loaded attribute tracks so every music keyframe is discoverable without time sampling. Titles are translated components, resolved against the current language rather than a reload-order snapshot.
5. **`MusicWorldMixin` / `LevelSoundMixin`:** the exact `updateLevelInEngines(ClientLevel, boolean)` sound-stop call keeps the active Basic source only. The sound engine retires other sources using existing channel stop intent, clears queued/ticking old-world sounds, and lets its normal cleanup release handles. Vanilla and resource-reload/device-reset stop paths are unchanged.

Fabric API instead of mixins:
- Key: `fabric-key-mapping-api-v1`, checked in one `ClientTickEvents.END_CLIENT_TICK` listener. This is the only tick listener in the mod.
- Pause cassette: `fabric-screen-api-v1`.
- Track list rebuild: after the existing SoundManager resource apply, so the registry is complete.
- Custom audio: `fabric-sound-api-v1`.

**No ticking unless needed:**
- Music logic only runs inside vanilla's `MusicManager.tick`.
- The jukebox check runs there only when the setting is Pause and `JukeboxWatcher` has an entry.
- The toast uses vanilla's toast manager.
- Long titles scroll by render time.
- The music folder is scanned when a screen opens, after a drop and on resource reload. Never on a timer.

## Tracks and names

- **Discovery:** loaded sound events whose path contains `music` or `theme` (namespace alone does not qualify), plus events referenced by built-in music and loaded biome/dimension background music (default, creative and underwater), timeline music keyframes and jukebox songs regardless of event name. Rebuild after sound resources apply and world registry transitions; server definitions become available after joining that server. Only referenced weighted sound entries are flattened, with cycle protection and file-id dedupe. Jukebox definitions and legacy `music_disc.` pools take DISC precedence over soundtrack aliases of the same file; jukebox descriptions supply their titles.
- **Tags:** planned pool UI uses these discovered events.
  - Display name: lang key `whostoppedthemusic.tag.<namespace>.<path>` (vanilla ones written in `en_us.json`).
  - Fallback: prettified path, for example "Overworld · Cherry Grove".
- **Soundtrack tracks:** the file entries inside those pools.
  - Title from vanilla's lang: the file path with `/` turned into `.`. For example, `music/game/sweden` → `music.game.sweden` → "C418 - Sweden".
  - Fallback: prettified file name.
  - The real `Sound` entry is reused, so volume and stream settings from `sounds.json` are kept.
- **Discs:** loaded jukebox-song sound events plus legacy `music_disc.` events. Definitions provide their description; legacy pools use `jukebox_song.minecraft.<id>` ("Lena Raine - Pigstep"). 26.3 removes the old `item.minecraft.music_disc_<id>.desc` keys through `lang/deprecated.json`, even though they remain in en_us.json. Discs play on the Music channel as background music, not positional.
- **Your songs:** files in `.minecraft/music/` ending in `.mp3` or `.ogg`, not case-sensitive. The title is the file name without its extension.
  - OGG is decoded with vanilla's `JOrbisAudioStream`, MP3 with `Mp3AudioStream`.
  - Decoding starts on a background executor, never the render thread.
  - A file that fails to decode is skipped with one log line and marked "Can't play" in the UI.
- **Track ids** in settings:
  - Vanilla files use their file id (`minecraft:music/game/sweden`).
  - Discs use the disc's file id (`minecraft:records/pigstep`).
  - Custom files use `custom:<file name>`.

## Settings file

`config/whostoppedthemusic.json`. Unknown keys are ignored. A broken file is renamed to `.broken` and defaults are used. No migration from 1.1.x: the old `whostoppedthemusic.properties` is ignored. Stores configVersion, firstRunDone, mode (VANILLA/BASIC), jukebox and basic.shuffle/unticked. Custom below is the planned later schema.

```json
{
  "configVersion": 1,
  "firstRunDone": true,
  "mode": "VANILLA",
  "jukebox": "PAUSE_MUSIC",
  "basic": {
    "shuffle": true,
    "unticked": ["minecraft:records/11"]
  },
  "custom": {
    "minecraft:music.overworld.swamp": [
      { "track": "minecraft:music/game/swamp/aerie", "weight": 5 },
      { "track": "custom:My Song", "weight": 29 }
    ]
  }
}
```

- **First launch** (no file yet): set vanilla `musicFrequency` to `CONSTANT`, save vanilla options, then set `firstRunDone`. After that the mod never touches the frequency again.
- Basic stores only **unticked** tracks, so new soundtrack songs from future updates start ticked.
- Custom stores only **edited** tags, as a full list of the tag's songs and weights. Reset removes the entry.

## Bundled library

- `dev.mccue:jlayer-decoder` is the decoder-only part of JLayer (LGPL), repackaged in 2024 with no `java.desktop` dependency.
- Phred checks on Maven Central:
  - the exact latest version,
  - its license file,
  - that the artifact matches its public source.
- Ship it **unmodified** as a nested jar with Loom's `include`. Copy its license into the jar as `LICENSE_jlayer`. Our own code stays CC0-1.0.
- If that artifact can't be verified, stop and ask. Don't swap in another library without approval.

### Decoder verification (function 1, 2026-11-06)

Maven Central metadata selects `2024.04.19` as both latest and release. The POM
identifies `bowbahdoe/java-audio-stack` as its public source and LGPL 2.1 as its
license. Its source headers permit LGPL 2 or later; the repository's root license
is LGPL 3. We include the LGPL 2.1 text identified by the artifact's POM, without
relicensing the decoder as CC0.

- Public release tag: `v2024.04.19`, commit `b41a7fe4535c6776fad2fdd919e493712d7c1a27`.
- All 28 published Java source files match that tag byte-for-byte; all five
  library resources match too. Recompiling for Java 17 produced 35 identical
  class files. The remaining differences were inspected: constant-pool ordering,
  module/parameter attributes and compiler-generated string-concatenation code, not a
  different implementation. This is source/binary correspondence verification,
  not a claim of a fully reproducible upstream build.
- Maven jar SHA-256: `000075405dba36ebec26595fc10c33dfea871dafb2651c7f616dd0977411c54b`.
- Maven sources SHA-256: `1fcaedb60df04d7f8921fd51215baeadf0261fc04380349842725be2ef292861`.
- `LICENSE_jlayer` is the GNU-hosted LGPL 2.1 text, SHA-256
  `20e50fe7aae3e56378ebf0417d9de904f55a0e61e4df315333e632a4d3555d95`.
- The module requires only `java.base`, not `java.desktop`.
- Loom's `include` adds generated `fabric.mod.json` to make the library loadable
  as a nested Fabric dependency. Every original jar entry remains byte-identical;
  the nested zip as a whole therefore has a different hash. No decoder code or
  resource is modified.

Sources: Maven Central `dev/mccue/jlayer-decoder/maven-metadata.xml`, the release
POM/jar/sources jar under that Maven path, the public GitHub release tag above,
and `https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt`.

Function 1 introduced the packaged-client title-screen test. The Vanilla UI slice
extends that same test to first-launch Constant (after all startup callbacks),
M/rebinding, real vanilla options and disk reload, Jukebox persistence, top cassette
navigation, situational Next, channel Pause/Resume,
Records/non-Music isolation, active song/silence and 320×240 placement/bounds/non-overlap.
The transport regression holds real decoded vanilla streams before channel attachment:
the original transport leaves the outgoing OpenAL source playing after Next and
loses Pause before startup. The fixed source stops without late playback and starts
paused until explicitly resumed. Both Next paths also assert outgoing
source shutdown and incoming playback, including Next from paused music. These are
source-state checks, not a claim of human listening verification.
The focused transport selection is
`xvfb-run -a ./gradlew runClientGameTest -PtransportOnly --console=plain`.
It bypasses unrelated first-run/rebinding/layout checks and exercises Play from
silence, Done navigation, automatic tick-selected music (also with delayed streams),
pause across ticks/global resume, same-source resume, both Next controls, resolved
song titles and removal of Previous. Clicking Frequency to Constant drives automatic
replacement after the test ends the outgoing source; the Music slider changes the
real OpenAL source gain to zero and back while Master stays unchanged. It shortens
the outgoing song via vanilla SoundManager.stop rather than waiting a full song.
This is source-state/gain verification, not human listening acceptance.

Five `SettingsTest` cases cover
defaults, first-run idempotence, round trip/unknown keys/temp cleanup, corrupt
file recovery, failed first-run initialization, Basic mode/shuffle/unticked round trip and new-track defaults. Three Deck cases cover weighted cycles/history/empty/single and two names cases cover active translation keys and fallback. CI rejects missing/empty/skipped
JUnit reports, missing UI screenshots or pass marker, and source-output loading
instead of the packaged jar. No empty suite is called passed.

## How to build

Focused shared-controls verification: `xvfb-run -a ./gradlew jar runClientGameTest -PcontrolsOnly --console=plain`.
The existing packaged-client harness exercises both modes' Play/Pause/same-source
Resume and Next from pause, pause-menu Next, Basic row Play and held Stop, one
updated SongToast on manual/automatic transitions, Music Toast Never, notification
duration scaling and Records/effects isolation during pause and Stop. It also
checks browser query retention through collapse/resize and 320×240 bounds.
The outgoing-source assertion inspects the handle on the sound executor: vanilla
ChannelHandle.execute intentionally skips callbacks after channel release.

Focused discovery verification: `xvfb-run -a ./gradlew runClientGameTest -PdiscoveryOnly --console=plain`.
This loads the packaged jar and test-only event aliases over vanilla audio, checks
name/definition discovery and nested modifiers, then exercises world entry, resource
reload and world exit. The original prefix filter fails the `forestmusic` assertion.

Copy the Gradle setup from **RealSize** (`realsize-mod`, the newest 26.3 setup), then change it for a client-only mod.

**`gradle.properties`**
```properties
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true

mod_version=1.2.0
maven_group=xyz.pyrehaven
archives_base_name=whostoppedthemusic

minecraft_version=26.3
loom_version=1.18.4
loader_version=0.19.5
fabric_api_version=0.162.0+26.3
jlayer_version=<checked latest dev.mccue:jlayer-decoder>
```

**`settings.gradle`:** RealSize's `pluginManagement` block (Fabric maven, Maven Central, Gradle Plugin Portal), with `rootProject.name = 'whostoppedthemusic'`.

**`build.gradle`:** start from RealSize's file and change:
- `loom { clientOnlyMinecraftJar() }`, with a single `main` source set. No `splitEnvironmentSourceSets`, because everything is client code. `mods { whostoppedthemusic { sourceSet sourceSets.main } }`.
- `fabricApi.configureTests { createSourceSet = true; modId = 'whostoppedthemusic-test'; enableGameTests = false; enableClientGameTests = true }`. Drop the server run, `gameTest` and `server-smoke.py` pieces.
- Keep RealSize's runtime paths with the project name swapped: build dir `../../runtime/whostoppedthemusic/build`, run dirs `../../runtime/whostoppedthemusic/run/<name>`. Source stays in the checkout; builds, jars and worlds stay under `~/runtime`.
- Dependencies:
  - `minecraft`
  - `implementation` fabric-loader
  - `implementation` fabric-api
  - `implementation` and `include` `dev.mccue:jlayer-decoder:${jlayer_version}`
  - `testImplementation` fabric-loader-junit
- Java 25 toolchain, `options.release = 25`, JUnit platform.
- `processResources` expands `version`, `minecraft_version`, `loader_version` and `fabric_api_version` into `fabric.mod.json`.
- The jar gets `LICENSE_whostoppedthemusic` and `LICENSE_jlayer`.
- `runClientGameTest` runs against the packaged jar, as RealSize does.

**`fabric.mod.json`**
```json
{
  "schemaVersion": 1,
  "id": "whostoppedthemusic",
  "version": "${version}",
  "name": "Who Stopped The Music",
  "description": "Choose the music Minecraft plays: one simple song list, or full control over every biome and situation, with your own MP3 and OGG files.",
  "authors": ["PyreHaven"],
  "contact": {
    "homepage": "https://pyrehaven.xyz",
    "sources": "https://github.com/ff-tech-xyz/whostoppedthemusic",
    "issues": "https://github.com/ff-tech-xyz/whostoppedthemusic/issues"
  },
  "license": "CC0-1.0",
  "icon": "assets/whostoppedthemusic/icon.png",
  "environment": "client",
  "entrypoints": { "client": ["xyz.pyrehaven.whostoppedthemusic.WhoStoppedTheMusic"] },
  "mixins": ["whostoppedthemusic.mixins.json"],
  "depends": {
    "fabricloader": ">=${loader_version}",
    "fabric-api": ">=${fabric_api_version}",
    "minecraft": "~${minecraft_version}",
    "java": ">=25"
  }
}
```

**Mixins file:** package `xyz.pyrehaven.whostoppedthemusic.mixin`, `JAVA_25`, all mixins under `client`, `"injectors": { "defaultRequire": 1 }` so a broken hook fails loudly instead of silently.

**Commands** (Java 25):
```bash
./gradlew clean build --console=plain                 # compile, unit tests, jar
./gradlew runClient --console=plain                   # play-test client
xvfb-run -a ./gradlew runClientGameTest --console=plain   # headless client smoke test
```
The jar lands in `~/runtime/whostoppedthemusic/build/libs/whostoppedthemusic-1.2.0.jar`.

**CI:** RealSize's `build.yml` with the server-smoke step removed and the names and paths changed. Keep the pinned action SHAs, Java 25, the empty-report check and the artifact upload.

## Rules for this codebase

- **One owner per job:** settings only in `Settings`, song choice only in `MusicDirector`, the track list only in `TrackLibrary`. No second manager, wrapper or fallback beside a broken path. Fix the path itself.
- **Mixins only where Fabric API has no suitable hook**: the exact sound registry, music, channel, toast and world-transition seams above; accessors remain read-only except the manager's next-delay/invoker transport seam. Each owns one job.
- **No new tick work.** The single tick listener only checks the key.
- **No hard-coded English** in Java. All text goes in `en_us.json`.
- **No hard-coded colours or sizes** outside `Theme.java`.
- **Pure logic stays Minecraft-free** (`Deck`, `TrackNames`, the settings data) so it can be unit tested.
- **No disk writes in the music path.** Settings save only on player changes.
- **Never stop the Records channel.** Jukeboxes are never cut off by our buttons.
- This tree is the full layout. Adding, moving or removing a source file means updating this file first.

## Out of scope

- No server-side part, networking or anything that needs the server to have the mod.
- No new built-in music. The mod only uses vanilla's files, discs and the player's own files.
- No changes to sound effects, ambient sounds or how jukeboxes themselves work.
- No NeoForge or other loaders.
- No downloading music from the internet.
