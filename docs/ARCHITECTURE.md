# Who Stopped The Music architecture

## What it is

Who Stopped The Music is a client-side Fabric mod that lets players choose what music Minecraft plays. It plugs into vanilla's own music player instead of running a second one next to it. It has three modes:

- **Vanilla:** the mod stays out of the way and Minecraft picks music exactly as normal.
- **Basic:** one song list for the whole game. Tick the songs you want and get play, stop, previous, next and shuffle controls.
- **Custom:** every place music can play (each biome pool, the Nether, the End, underwater, creative, the title screen and so on) is a button. Inside each one you choose the songs and give each a weight from 1 to 30.

Players can also drop their own `.mp3` and `.ogg` files into a `music` folder, or drag them onto the Add Songs screen, and use them in Basic or Custom. Music discs can be used too.

Version 1.2.0 is a **complete rebuild**. None of the 1.1.x code, build setup or extra folders carries over.

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

Current implemented slice (2026-11-06): Settings and first-launch Constant, the M
MusicScreen with vanilla option widgets and Vanilla body, and a clickable pause
cassette. Basic/Custom and the library/playback hooks below remain planned, not
implemented. This slice defaults to Vanilla, not Basic. Jukebox is saved only;
its playback effect belongs to function 8.

The cassette sits at the bottom of the pause menu, away from vanilla's top-left
song line. When that line is present the cassette says only “Music settings”,
not the song a second time. Otherwise it reads the actual active music instance
and vanilla's translation key, or says nothing is playing. A read-only
`mixin/MusicManagerAccessor.java` exposes that instance; it changes no playback.

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
    │   │   │   # pause-screen cassette (ScreenEvents.AFTER_INIT) and the TrackLibrary reload
    │   │   │   # listener (later). Applies first-launch Music Frequency at CLIENT_STARTED. No music logic.
    │   │   ├── Mode.java                    # VANILLA now; BASIC/CUSTOM added with their playback
    │   │   ├── JukeboxSetting.java          # enum PLAY_OVER, PAUSE_MUSIC
    │   │   ├── Settings.java
    │   │   │   # The only owner of mod settings. JSON at config/whostoppedthemusic.json,
    │   │   │   # loaded once at startup, saved only when the player changes something,
    │   │   │   # written to a temp file and atomically moved into place (same pattern as RealSize).
    │   │   ├── music/
    │   │   │   ├── Track.java               # record: id, kind (SOUNDTRACK / DISC / CUSTOM), title, source
    │   │   │   ├── Tag.java                 # record: pool id, display name, group, vanilla entries (track + weight)
    │   │   │   ├── TrackNames.java          # pure: file id -> lang key -> title; prettify fallback
    │   │   │   ├── TrackLibrary.java
    │   │   │   │   # Builds the list of tracks and tags from the game's loaded sound data and
    │   │   │   │   # the music folder. Rebuilt on resource reload and when the folder changes.
    │   │   │   ├── Deck.java
    │   │   │   │   # Pure logic, no Minecraft classes: weighted shuffle "deck" with history
    │   │   │   │   # for previous/next. Fully unit tested.
    │   │   │   ├── MusicDirector.java
    │   │   │   │   # The only place that decides what plays: mode -> which deck -> which track.
    │   │   │   │   # Owns Play / Stop / Previous / Next / Shuffle and play-now.
    │   │   │   ├── TrackSoundInstance.java
    │   │   │   │   # The sound instance for a chosen track. Its identifier stays the pool id so
    │   │   │   │   # vanilla's pool-change logic keeps working. Resolves to the chosen file.
    │   │   │   │   # Streams custom files through FabricSoundInstance#getAudioStream.
    │   │   │   ├── Mp3AudioStream.java      # AudioStream over jlayer-decoder (16-bit PCM)
    │   │   │   └── JukeboxWatcher.java      # set of jukeboxes playing near the camera; event-driven
    │   │   ├── custom/
    │   │   │   └── MusicFolder.java
    │   │   │       # .minecraft/music/: scan for .mp3/.ogg, copy dropped files in, delete a song,
    │   │   │       # open the folder. Never runs on a timer.
    │   │   ├── ui/
    │   │   │   ├── Theme.java               # PyreHaven colours, spacing and text styles in one place
    │   │   │   ├── MusicScreen.java         # root screen: mode, shared settings row, body for the mode
    │   │   │   ├── BasicPanel.java          # Basic body: search, song list, controls
    │   │   │   ├── CustomPanel.java         # Custom body: grid of tag buttons
    │   │   │   ├── TagEditorScreen.java     # picker | songs in tag | song settings
    │   │   │   ├── AddSongsScreen.java      # drop zone, your songs list, remove, open folder
    │   │   │   ├── SongToast.java           # our Now Playing toast (vanilla Toast interface)
    │   │   │   ├── CassetteWidget.java      # pause-menu cassette: song/silence or settings link; no transport yet
    │   │   │   └── widget/
    │   │   │       ├── TrackRow.java        # checkbox + title (scrolls if long) + play button
    │   │   │       ├── TrackList.java       # scrolling list of TrackRows with section headers
    │   │   │       ├── TagButton.java       # tag name, song count, "edited" marker
    │   │   │       └── WeightSlider.java    # 1-30 slider with "5 in 34" chance text
    │   │   └── mixin/
    │   │       ├── MusicManagerAccessor.java        # currentMusic read-only, truthful cassette status
    │   │       ├── MusicManagerMixin.java           # planned music hooks (see "Hooks")
    │   │       ├── PauseScreenMixin.java            # hides vanilla's pause-menu music line in Basic/Custom
    │   │       ├── ClientJukeboxMixin.java          # client-side jukebox start/stop -> JukeboxWatcher
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
    │   ├── TrackNamesTest.java      # lang key mapping, disc names, file-name titles, prettify fallback
    │   └── MusicFolderTest.java     # file filter, copy, duplicate names, delete
    └── gametest/
        ├── java/xyz/pyrehaven/whostoppedthemusic/
        │   └── ClientSmokeGameTest.java # packaged Vanilla UI, first-run Constant, options/persistence, keys, cassette, small window
        └── resources/fabric.mod.json   # development-only client test entrypoint
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
- Song choice, the toast and the pause-menu music line are all fully vanilla.
- The shared settings row shows volumes, Music Frequency, Music Toast and Jukebox. Jukebox is currently saved only; its effect will apply in every mode with function 8.
- The body says one line: Minecraft picks the music.

### Basic
- There is **one pool for the whole game**: title screen, every biome and dimension, underwater, creative, the dragon fight and the credits. Vanilla never switches pools, so a song carries on when you change biome or leave the world.
- The song list has three sections: **Soundtrack**, **Music Discs** and **Your Songs**. Tick a song to put it in the deck.
- Every ticked song has weight 1. Shuffle on: the deck is shuffled. Shuffle off: songs play in list order. When the deck is used up it refills and repeats.
- No songs ticked: nothing plays.
- Controls: Play on each row (plays that song now, then the deck carries on), Play/Stop, Previous, Next, Shuffle, Select All, Deselect All, search, and Add Songs.
- Stop holds silence until the player presses Play, Next or a row's Play, or restarts the game. It is not saved.
- Defaults: every soundtrack song and every disc ticked. A newly added custom song starts ticked.

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

The implemented slice has a fixed “Mode: Vanilla” label. Mode cycling only arrives
with the later bodies/playback; there are no pretend playback or inactive mode
buttons. The diagram below describes the complete planned screen, not current
Basic/Custom functionality. The four shared Minecraft controls are vanilla's own
widgets; Theme styles the surrounding screen, Jukebox/Done buttons and cassette.

```text
┌──────────────────────────────────────────────────────────────┐
│              Who Stopped The Music      [ Mode: Basic ▸ ]    │  click Mode to cycle Vanilla → Basic → Custom
│  Master ▬▬▬▬▬●▬  Music ▬▬▬▬●▬▬  [Frequency: Constant]         │  shared row, every mode
│  [Music Toast: Pause and Toast]  [Jukebox: Pause music]      │
├──────────────────────────────────────────────────────────────┤
│  body for the current mode                                   │
│  Basic: [search] song list (left) | now playing, ◀ ■ ▶,      │
│         Shuffle, Select/Deselect All, Add Songs (right)      │
│  Custom: tag button grid + Add Songs                         │
│  Vanilla: "Minecraft picks the music."                       │
├──────────────────────────────────────────────────────────────┤
│                           [ Done ]                           │
└──────────────────────────────────────────────────────────────┘
```

- Master and Music volume, Music Frequency and Music Toast are **vanilla's own options**, created from `minecraft.options` (`getSoundSourceOptionInstance(...)`, `musicFrequency()`, `musicToast()`). That way they always match the vanilla settings screen.
- The screen opens with **M** (rebindable in Controls → Who Stopped The Music) or by clicking the pause-menu cassette.
- **PyreHaven style:** `Theme.java` owns orange `#E46011`, blue `#27607C`, glow `#4FA8D8`, background `#070B10`, card `#0E141C`, text `#F2F4F7`, and layout dimensions. Orange is the main accent; blue/glow mark edges and focus. **No logo anywhere in the UI.**
- The UI must work at GUI scales 1–4 and down to a 320×240 window. Narrow windows stack panels instead of overlapping.

## Hooks

The goal is the fewest hooks possible, all inside calls vanilla already makes. Method names marked **(confirm)** were not checked against 26.3 source while writing this. Check them before building on them.

1. **`MusicManagerMixin`**
   - `tick()`: wrap the `getSituationalMusic()` call.
     - **Basic:** return one constant `Music` (`whostoppedthemusic:basic`, built with `Holder.direct`, vanilla `GAME` delays, `replaceCurrentMusic = false`).
     - **Vanilla / Custom:** pass vanilla's answer through.
     - **Jukebox set to Pause and a nearby jukebox is playing:** fade the current song and hold the next one.
   - `startPlaying(Music)`: wrap the creation of the sound instance (confirm the exact call, expected `SimpleSoundInstance.forMusic`).
     - **Vanilla:** unchanged.
     - **Basic / Custom:** ask `MusicDirector` for a track for this pool. Build a `TrackSoundInstance` whose identifier is the pool id. If there's no track, start nothing.
   - `showNowPlayingToastIfNeeded()`: in Basic/Custom, cancel vanilla's toast and show `SongToast` instead, following `options.musicToast()`. This is how the player never sees two toasts.
2. **`PauseScreenMixin`:** in Basic/Custom, skip the pause screen's call that draws vanilla's music line (confirm where `NowPlayingToast.extractToast` is called). The cassette is added through Fabric `ScreenEvents.AFTER_INIT`, not this mixin.
3. **`ClientJukeboxMixin`:** the client method that starts and stops a jukebox song at a block position (confirm the class in 26.3; it was `LevelRenderer.playJukeboxSong` / `stopJukeboxSong` in 1.21). It tells `JukeboxWatcher`.
4. **`WeighedSoundEventsAccessor`:** reads a pool's weighted entries so `TrackLibrary` can list them. Entries that point to another event are followed recursively.

Fabric API instead of mixins:
- Key: `fabric-key-mapping-api-v1`, checked in one `ClientTickEvents.END_CLIENT_TICK` listener. This is the only tick listener in the mod.
- Pause cassette: `fabric-screen-api-v1`.
- Track list rebuild: `fabric-resource-loader-v1` client reload listener.
- Custom audio: `fabric-sound-api-v1`.

**No ticking unless needed:**
- Music logic only runs inside vanilla's `MusicManager.tick`.
- The jukebox check runs there only when the setting is Pause and `JukeboxWatcher` has an entry.
- The toast uses vanilla's toast manager.
- Long titles scroll by render time.
- The music folder is scanned when a screen opens, after a drop and on resource reload. Never on a timer.

## Tracks and names

- **Tags:** every sound event whose path starts with `music.`, from `SoundManager.getAvailableSounds()`, plus any pool seen at runtime. This works on the title screen and picks up modded and resource-pack pools.
  - Display name: lang key `whostoppedthemusic.tag.<namespace>.<path>` (vanilla ones written in `en_us.json`).
  - Fallback: prettified path, for example "Overworld · Cherry Grove".
- **Soundtrack tracks:** the file entries inside those pools.
  - Title from vanilla's lang: the file path with `/` turned into `.`. For example, `music/game/sweden` → `music.game.sweden` → "C418 - Sweden".
  - Fallback: prettified file name.
  - The real `Sound` entry is reused, so volume and stream settings from `sounds.json` are kept.
- **Discs:** sound events starting with `music_disc.`. Title from `item.minecraft.music_disc_<id>.desc` ("Lena Raine - Pigstep"). They play on the Music channel as background music, not positional.
- **Your songs:** files in `.minecraft/music/` ending in `.mp3` or `.ogg`, not case-sensitive. The title is the file name without its extension.
  - OGG is decoded with vanilla's `JOrbisAudioStream`, MP3 with `Mp3AudioStream`.
  - Decoding starts on a background executor, never the render thread.
  - A file that fails to decode is skipped with one log line and marked "Can't play" in the UI.
- **Track ids** in settings:
  - Vanilla files use their file id (`minecraft:music/game/sweden`).
  - Discs use the disc's file id (`minecraft:records/pigstep`).
  - Custom files use `custom:<file name>`.

## Settings file

`config/whostoppedthemusic.json`. Unknown keys are ignored. A broken file is renamed to `.broken` and defaults are used. No migration from 1.1.x: the old `whostoppedthemusic.properties` is ignored. Currently stores configVersion, firstRunDone, mode (VANILLA) and jukebox. The Basic/Custom fields below are the later schema, not implemented data yet.

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
extends that same test to first-launch Constant, M/rebinding, the real vanilla
options and disk reload, Jukebox persistence, cassette navigation, active vanilla
song/silence and 320×240 widget bounds/non-overlap. Four `SettingsTest` cases cover
defaults, first-run idempotence, round trip/unknown keys/temp cleanup, corrupt
file recovery and failed first-run initialization. CI rejects missing/empty/skipped
JUnit reports, missing UI screenshots or pass marker, and source-output loading
instead of the packaged jar. No empty suite is called passed.

## How to build

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
- **Mixins only where Fabric API has no hook**: the four planned hooks above, plus the read-only currentMusic accessor used by the Vanilla cassette. Each owns one job.
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
