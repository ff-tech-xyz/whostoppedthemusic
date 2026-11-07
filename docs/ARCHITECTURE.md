# Who Stopped The Music architecture

## What it is

Who Stopped The Music is a client-side Fabric mod that lets players choose what music Minecraft plays. It plugs into vanilla's own music player instead of running a second one next to it. It has three modes:

- **Vanilla:** Minecraft selects the music. Play, Pause/Resume and Next control its active background music without affecting Records or sound effects.
- **Basic:** one song list for the whole game. Tick the songs you want and get Play/Pause/Resume, separate Stop, Previous, Next and Shuffle controls.
- **Custom:** every registry biome and special scenario has songs with weights 1–100, priority, interrupt, frequency and jukebox settings. Tied winners merge their lists. An empty active winner means silence.

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

Custom scenarios are registry biomes or specials, not sound-pool tags. Our hooks
sit inside steps 1, 4 and 5. We never add a second tick loop. The supplied
`runtime/whostoppedthemusic/inputs/custom-1.2.0/CUSTOM_SPEC.md` defines the
authorized Custom behavior, subject to BUILD_CONTRACT.md's verified runtime-odds
correction; the sole remaining-work tracker is `plans/1.2.0.md`.

## Structure

Current implementation (2026-11-07): Settings and first-launch Constant, Vanilla
transport, Basic soundtrack/disc selection and playback, shared controls and one SongToast in all three modes,
and nearby jukebox fade/hold. Imported MP3/OGG files play through
the same Basic/Custom channel path. Add music manages those files through a shared screen;
Custom's scenario resolver, loaded runtime defaults, playback integration and
sparse settings v2, Custom main browser and shared biome/special song editor are
implemented, including midpoint preview and same-source resume. The M screen cycles Vanilla/Basic/Custom; the
compact pause cassette retains live title/artist metadata and Next. Shared
playback-driven reel artwork and empty-player spindle rods are implemented.

The compact cassette retains its player housing, centered width and y=6 placement.
Its 34-pixel height reserves two font-height lines without touching Game Menu.
Its metadata always comes from the actual resolved music instance;
the duplicate vanilla pause song line is suppressed without changing the toast
option. SongToast replaces the in-game notification in all three modes. Clicking the tape opens MusicScreen. Both tape views
put a bright title over a quieter artist between two reels. Unknown artists stay
blank. Pause status sits separately to the left of its housing; settings status
sits beneath the metadata. Full metadata/status is available in tooltips and
widget narration; browser tapes participate in keyboard focus/narration too.

`CassetteArtwork.drawPlayer` owns compact/settings players; `drawTrack` owns
library tapes. One geometry calculation positions the old 2×2 reel centers at
every scale: red rods belong to the player, dark open holes belong to list
cassettes. No live channel shows only a player outline and those two rods,
without shell, label or reels. Paused and muted music retain the loaded tape.
The shared artwork clock uses elapsed nanoseconds for one revolution per two
seconds; three thin spokes remain visible at the six-pixel narrow reel size.
Only the resolved playing file's list tape turns. Current tapes have an orange
border, focus/hover uses glow, and selection remains the independent tick.

The director samples native `Channel.playing()` on the sound executor from its
existing MusicManager tick, including held-pause and jukebox-fade ticks. Its one
observed advancing identity is checked against the current live source, so
pending streams and stopped/replaced channels do not drive animation. Explicit
Pause freezes immediately; mute leaves advancing channels turning. The artwork
owns one shared visual clock, not one playback timer/map per widget.
Custom/import screens reuse these exact shared UI owners and Theme's PyreHaven
palette, rather than a separate styling framework.

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

### Pure Custom resolver contract

`ContextResolver.resolve(Situation, Map<ScenarioId, ScenarioConfig>, previousWinners)`
returns an immutable `Resolution`. The map supplies effective settings, not JSON
overrides: the current biome and in-world specials must be present. Menu/credits
need only their own config. Missing effective configs fail explicitly.
`Settings` owns sparse persistence and supplies these effective values from loaded defaults.

`MusicDirector`'s `CustomPlayback` adapter keeps the previous tick's winner IDs,
starting with an empty collection, and passes `resolution.winners()`
on the next tick. The resolver retains no state. Ordinary interrupt flags apply
only to newly winning IDs; menu and credits always return interrupt. Removing an
interrupting winner or keeping an existing one does not trigger an interrupt.
`ScenarioConfig.Frequency` is pure; the playback adapter maps it to vanilla's
`MusicFrequency`. `SituationReader` populates namespaced biome tag strings and
the camera-block precipitation/exposure snapshot. `exposedToRain` represents the
heightmap exposure test; weather requires both it and sky visibility when outside
is required. This snapshot uses the camera block, except for the player's underwater
and abilities flags; credits read Gui.screen and the boss flag reads Gui.hud.

Winner IDs and merged track IDs sort canonically, making keys and deck entries
stable across input map iteration order. The key joins scenario IDs with `+`;
the synthetic sound-ID adapter hashes mixed keys with nameUUIDFromBytes. Scenario
song lists enforce unique IDs and weights 1–100. `Deck.Entry` accepts positive
raw sums, so two weight-100 entries merge to 200 and still draw at their exact
relative odds. Load-time clamping belongs to settings v2, not tie resolution.

### Loaded Custom defaults and selection odds

`CustomDefaults(library).biome(id, biome, dimension)` applies the loaded dimension
attributes and then the biome's own modifiers. Callers supply the applicable
dimension explicitly (including modded dimensions); the catalog/playback integration
owns that selection. Zero music volume, an absent default slot or an empty loaded
pool produces an empty song list. `Default(config, Optional<Music>)` retains the
source Music's delays and replace-current metadata even when the list is silent.
Special defaults use MENU/CREDITS/END_BOSS/UNDER_WATER/CREATIVE; underground and
weather are empty and inactive. Weather requires outside, underground uses Y64,
frequency starts Constant and jukebox starts Pause music. The four named vanilla
cave biomes start at priority 5; other biomes start at 2.

`TrackLibrary.poolEntries(pool)` reads its current loaded SoundManager each call,
without a separate defaults cache. A runtime event wrapper's weight is the total
of its referenced pool, not the JSON reference's declared weight. For referenced
total R, outer total T and leaf weight w, the draw probability is
`(R / T) * (w / R) = w / T`. The same cancellation applies at each nested alias.
Accumulate each reachable leaf's file weight once per branch and add duplicate
file IDs, including aliases reached along multiple branches. A path-local visiting
set skips cyclic edges while retaining valid siblings; vanilla's recursive weight
calculation itself has no meaningful finite odds for a cycle. Basic's `flatten`
and its sound modifiers remain unchanged.

Keep raw accumulated weights when they fit 1–100. When they exceed 100, reduce by
their common divisor first; if still too large, scale the maximum to 100, round
and floor at 1. Only that last case approximates odds. The scenario bound never
caps weights merged later by ContextResolver/Deck.

Evidence: cached 26.3 `SoundManager$Preparations$1.getWeight()` bytecode returns
the referenced `WeighedSoundEvents.getWeight()`. The asset index `26.3-34` maps
`minecraft/sounds.json` to `7975165ad4a3c0cb3c05078b914eac0f2c3b5f5f`.
Its game pool has 32 files totaling 37 weight; creative adds six unit-weight
files, for 38 files totaling 43. The supplied section 6 declared-reference/LCM
formula and six-weight-32 example were disproven and superseded, not implemented
as an alternative utility.

On 2026-11-07, the packaged `discoveryOnly` harness compared all 67 loaded vanilla
biomes' defaults and source metadata with their loaded pools before and after a
real resource reload, including pale-garden zero-volume and warped-forest empty
silence. An independent recursive draw-probability calculation uses runtime
`Weighted.getWeight()` at each branch; it checks creative and nested/duplicate
aliases whose declared reference weights deliberately differ (97/13). A full
43-card creative Deck cycle matches every file count. Additional checks cover a
cyclic branch, missing/empty pools, exact common-divisor reduction, approximate
600:7 scaling and live pool mutation followed by resource-reload restoration.
Existing Basic discovery/modifier/lifecycle checks still pass. Eleven focused
resolver/Deck JUnit cases pass. Command, artifact hash and log are in the plan's
function 12 evidence. Playback/settings and scenario UI now use these same defaults.

### Custom playback and catalog

MusicManager's existing HEAD hook resolves Custom exactly once per manager tick,
including held ticks. MusicDirector delegates scenario work to CustomPlayback;
there is no extra player or scheduler. BiomeCatalog rebuilds from the joined
registry, groups by vanilla dimension tags before namespace, and publishes known
biomes to Settings. Its loaded rows disappear on world exit; saved absent-biome
groups and overrides remain. Catalog rows retain applicable dimension defaults;
the current biome uses the actual current dimension even if a datapack reuses it.
Loaded defaults invalidate on world changes and sound-library reload/publication.

Each resolution gets a stable synthetic event: custom/<scenario>, or
custom/mix/<name UUID of canonical sorted key>. Thirty-two access-ordered Decks
retain each key's weighted cycle and history; edits configure/reset only that
deck when entries change. Unavailable library entries are filtered for playback,
not erased from Settings. An empty/all-missing winner blocks starts, without
falling through or cutting a noninterrupting outgoing song. Transport and SongToast
use the same TrackSoundInstance/channel owners as Basic.

The synthetic Music retains source min/max delays (GAME for source-less specials).
The supplied frequency OptionInstance redirect does not exist in actual 26.3:
tick reads the cached gameMusicFrequency field at bytecode offset 139, and
stopPlaying reads it at 41. Redirect both reads and stopPlaying's situational
Music lookup, leaving the global option and Vanilla/Basic unchanged. The inspected
bytecode is recorded in evidence/custom-music-bytecode.txt. Vanilla's canReplace
compares event identifiers and its replacement branch calls SoundManager.stop;
Custom reuses that branch rather than adding another transition loop. Retain a
new key's interrupt intent across held-pause/volume-fade ticks so vanilla can
consume the change on resume. Noninterrupting keys let the current song finish.

Jukebox hold reads the resolution's setting; release resets vanilla's delay to
zero. Edited winning song lists use environment gain 1, allowing edited pale
garden songs; unedited lists retain vanilla's gain. The Music category slider
still applies in the existing sound engine.

Focused packaged checks appear under functions 13–16 in the
plan. The mode button cycles all three modes; CustomPanel and ScenarioScreen
edit the settings consumed here. Human listening and a real dragon-fight session
remain unverified; function 17 records migration and documentation checks.

```text
whostoppedthemusic/
├── .github/workflows/build.yml      # CI: build, unit tests, client game test (copied from RealSize, adapted)
├── .gitignore                       # copied from RealSize
├── LICENSE                          # CC0-1.0 text, copied from happy-artillery
├── LICENSE_jlayer                   # LGPL 2.1 text for the unmodified nested decoder
├── README.md                        # player docs: modes, music folder, settings, install
├── CHANGELOG.md                     # scoped 1.2.0 notes under Unreleased
├── docs/
│   ├── ARCHITECTURE.md              # this file
│   ├── CONFIGURATION.md             # controls, scenario defaults, settings v2 and migration
│   ├── plans/1.2.0.md               # the plan for this rebuild
│   └── images/                      # basic.png, custom.png, forest-editor.png, add-music.png; provenance README.md
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
    │   │   ├── Mode.java                    # VANILLA, BASIC and CUSTOM
    │   │   ├── JukeboxSetting.java          # enum PLAY_OVER, PAUSE_MUSIC
    │   │   ├── Settings.java
    │   │   │   # The only owner of mod settings. JSON at config/whostoppedthemusic.json,
    │   │   │   # loaded once at startup; saves player changes, first-run initialization and discovered biome groups,
    │   │   │   # written to a temp file and atomically moved into place (same pattern as RealSize).
    │   │   ├── music/
    │   │   │   ├── Track.java               # id, kind, translated display source, resolved title/artist metadata, loaded Sound
    │   │   │   ├── TrackNames.java          # pure: naming convention, structured names and normalized title/artist/typo search ranking
    │   │   │   ├── OverflowMotion.java      # pure: endpoint waits and constant-speed bidirectional label motion
    │   │   │   ├── TrackLibrary.java
    │   │   │   │   # Builds deduplicated soundtrack/disc tracks from loaded weighted sound entries,
    │   │   │   │   # follows event references with cycle protection; rebuilt after SoundManager.apply
    │   │   │   │   # and world registry changes. Matches music/theme in event paths and loaded
    │   │   │   │   # biome/dimension/timeline music plus jukebox definitions; discs win file dedupe.
    │   │   │   │   # Owns asynchronous folder scans/probes and client-thread publication; poolEntries expands loaded runtime odds for Custom.
    │   │   │   ├── Deck.java
    │   │   │   │   # Pure logic, no Minecraft classes: weighted shuffle "deck" with history
    │   │   │   │   # for previous/next. Positive weights include raw tied sums; scenario edit bounds live in ScenarioConfig.
    │   │   │   ├── MusicDirector.java
    │   │   │   │   # Shared transport, Basic deck/history/stop/row play, Custom adapter, one Basic pool,
    │   │   │   │   # truthful channel status/advancement and resolved-file match, jukebox hold and toast.
    │   │   │   ├── TrackSoundInstance.java
    │   │   │   │   # The sound instance for a chosen track. Its identifier stays the pool id so
    │   │   │   │   # vanilla's pool-change logic keeps working. Resolves to the chosen file.
    │   │   │   │   # Reuses loaded Sound on non-positional Music; Fabric getAudioStream opens local MP3/OGG on vanilla IO executor.
    │   │   │   ├── Mp3AudioStream.java      # AudioStream over jlayer-decoder (16-bit PCM)
    │   │   │   ├── MidpointAudioStream.java # Ogg granule/decoded MP3 sample count, exact half-frame skip, bounded PCM and final 150 ms fade
    │   │   │   ├── LocalSong.java           # scanned file, decode availability, probe/open, late read failures end the stream
    │   │   │   ├── JukeboxWatcher.java      # reads vanilla's event-driven map and live channels near the camera
    │   │   │   └── custom/
    │   │   │       ├── Special.java         # the nine scenario specials
    │   │   │       ├── ScenarioId.java      # typed biome/special identity and canonical sortable key
    │   │   │       ├── ScenarioConfig.java  # immutable effective settings, pure Frequency enum, unique songs with weights 1–100
    │   │   │       ├── Situation.java       # immutable camera/player/screen snapshot, biome tags and block precipitation
    │   │   │       ├── ContextResolver.java # stateless priority/tie/settings resolution with explicit previous winners
    │   │   │       ├── Resolution.java      # sorted winners/key, raw merged entries, interrupt/settings and silence
    │   │   │       ├── SituationReader.java # one live camera/player/screen snapshot per manager tick
    │   │   │       ├── CustomDefaults.java  # loaded dimension/biome/special defaults, source Music metadata and silence
    │   │   │       ├── BiomeCatalog.java    # joined registry rows/groups/dimensions; persistent known biomes via Settings
    │   │   │       ├── CustomPlayback.java  # effective defaults/resolution, synthetic Music and 32-key LRU decks
    │   │   │       └── Preview.java         # director-owned temporary native source; existing tick, background pause/resume and cancellation
    │   │   ├── custom/
    │   │   │   └── MusicFolder.java
    │   │   │       # Game-directory music/: case-insensitive .mp3/.ogg scan, file-name identity/title; excludes links/directories.
    │   │   │       # Owns exclusive-create numbered copies and folder-confined removal; rejects links. Never runs on a timer.
    │   │   ├── ui/
    │   │   │   ├── Theme.java               # PyreHaven colours, spacing, shared search construction and text styles
    │   │   │   ├── MusicControls.java       # shared settings/transport/right player; scenario targets and apply-all capture, Basic Stop/Shuffle
    │   │   │   ├── TransportButton.java     # drawn transport symbols, accessible actions and shared button states
    │   │   │   ├── MusicScreen.java         # three-mode routing; retains BasicPanel/CustomPanel query/scroll through rebuilds
    │   │   │   ├── CassetteArtwork.java     # shared player/list shell, spindle geometry and playback-driven reel clock
    │   │   │   ├── MusicVolumeSlider.java   # vertical view of vanilla's Music option; keyboard and drag
    │   │   │   ├── BasicPanel.java          # collapsible song browser and placement of the shared player
    │   │   │   ├── custom/                 # shared components; Theme owns PyreHaven surfaces
    │   │   │   │   ├── ScenarioLabels.java # translated names shared by browser/player/editor
    │   │   │   │   ├── ScenarioList.java   # grouped/filterable loaded scenarios; explicit no-toggle copy-picker mode
    │   │   │   │   ├── CustomPanel.java    # scenario search/list, active toggles and shared right player
    │   │   │   │   ├── ScenarioScreen.java # one biome/special settings/song editor, drag/drop, reset and shared-list copy picker
    │   │   │   │   ├── ApplyToAllPopup.java # right-click confirmation, all known/future scenarios
    │   │   │   │   └── Toggle.java         # shared pill toggle, translated setting/value tooltip and narration
    │   │   │   ├── AddSongsScreen.java      # shared Add music action, confirmed drops/removal, TrackList management, open folder
    │   │   │   ├── SongToast.java           # title-first two-line Now Playing toast (vanilla Toast interface)
    │   │   │   ├── OverflowText.java        # per-line content clock and clipped fractional tape/toast rendering
    │   │   │   ├── CassetteWidget.java      # top compact cassette, live title and settings link
    │   │   │   └── widget/
    │   │   │       ├── SettingButton.java  # shared left-cycle/right-confirm control with full-label tooltip
    │   │   │       ├── SmoothList.java      # shared wheel target/frame interpolation for TrackList and ScenarioList
    │   │   │       ├── TrackList.java       # clipped track rows: Basic, local removal, grouped drag-library and focused scenario cassettes
    │   │   │       └── WeightSlider.java    # integer 1–100 mouse/keyboard editor with this list's weight/chance narration and tooltip
    │   │   └── mixin/
    │   │       ├── MusicManagerAccessor.java        # currentMusic/frequency/delay access and vanilla fade invoker
    │   │       ├── MusicManagerMixin.java           # owns director; observes automatic/manual starts and holds pause
    │   │       ├── PauseScreenMixin.java            # hides duplicate vanilla pause-menu song line
    │   │       ├── SoundManagerAccessor.java        # read-only sound engine access for current music channel
    │   │       ├── SoundEngineAccessor.java         # read-only instance/channel map
    │   │       ├── MusicChannelMixin.java           # music source stop/pause intent across async startup and menu resume
    │   │       ├── PreviewVolumeMixin.java          # preview-only gain ignores background fades; ordinary Music/Master options still apply
    │   │       ├── ClientJukeboxMixin.java          # read-only LevelEventHandler playingJukeboxSongs map
    │   │       ├── ClientLevelAccessor.java         # current level's LevelEventHandler
    │   │       ├── SoundEventReferenceAccessor.java # event-reference Sound in vanilla's weighted wrapper
    │   │       ├── SoundLibraryMixin.java           # rebuild library after loaded sound registry applies
    │   │       ├── TimelineMusicAccessor.java        # read-only timeline attribute tracks for music keyframes
    │   │       ├── MusicWorldMixin.java             # keep Basic source during world changes; rebuild library/catalog from new registries
    │   │       ├── LevelSoundMixin.java             # stop other world sources and clear scheduled/ticking sounds
    │   │       ├── NowPlayingToastMixin.java        # suppress vanilla duplicate in all modes, including its slide-out
    │   │       └── WeighedSoundEventsAccessor.java  # read-only access to a pool's entries (accessor, no logic)
    │   └── resources/
    │       ├── fabric.mod.json
    │       ├── whostoppedthemusic.mixins.json
    │       └── assets/whostoppedthemusic/
    │           ├── icon.png                 # kept from main (the Modrinth icon)
    │           └── lang/en_us.json          # all UI prose; only shipped locale, Minecraft supplies option captions
    ├── test/java/xyz/pyrehaven/whostoppedthemusic/
    │   ├── DeckTest.java            # weights, deck cycle, no repeat across refills, prev/next, empty deck
    │   ├── ContextResolverTest.java # pure precedence/ties/raw sums/settings/interrupt, 96 weather cases, underground and aquatic
    │   ├── SettingsTest.java        # v1/v2 sparse round trip, absent/empty songs, known/future biomes, bounds, broken recovery, atomic save
    │   ├── TrackNamesTest.java      # metadata, translated names/fallback and search ranking
    │   ├── OverflowMotionTest.java  # waits, speed, reversal, fitting and content clocks
    │   ├── MusicFolderTest.java     # folder/identity, numbered exact copies, source retention, remove and link/path boundaries
    │   └── LocalAudioTest.java      # full generated PCM duration/tone, EOF, bad-file rejection, exact midpoint PCM and fade for MP3/OGG
    ├── test/resources/audio/        # shared generated CC0 sine-wave MP3/OGG fixtures and provenance README
    └── gametest/
        ├── java/xyz/pyrehaven/whostoppedthemusic/
        │   ├── ClientSmokeGameTest.java # packaged UI/transport; controlsOnly/artworkOnly/ownSongsOnly/addSongsOnly focused selections
        │   ├── DiscoveryGameTest.java   # discoveryOnly: loaded discovery/default odds, 67 biomes, creative Deck, aliases/cycle/bounds/reload
        │   ├── CustomPlaybackGameTest.java # customOnly: packaged scenario playback/settings/transport/weather/jukebox/silence/LRU/mode regression
        │   ├── CustomUiGameTest.java       # customUiOnly: main/editor; previewOnly: native midpoint/duration/resume/cancel/local/mute/jukebox/history
        │   └── test/mixin/
        │       ├── NativeBoundary.java      # fail on OpenAL errors before/after native channel operations in the existing harness
        │       └── TransportStreamGate.java # holds decoded vanilla streams before source attachment
        └── resources/
            ├── fabric.mod.json         # development-only client test entrypoint
            ├── assets/discovery/sounds.json # event-name, arbitrary definition, nested/cyclic alias fixtures over vanilla audio
            ├── assets/music_namespace/sounds.json # namespace-only rejection fixture
            └── whostoppedthemusic-test.mixins.json # development-only stream gate and native assertions
```

### Add music file-management path

`AddSongsScreen.button(parent, settings, x, y, width)` is the common entry action.
Basic and Custom place it beside Done through MusicScreen.
Both lead to the same screen instance type. `Theme.search` constructs the same
search widget used by Basic. `TrackList` owns local-only management rows via its
remove callback, keeping cassette rendering, ranked search, smooth clipping,
scrolling, focus and narration in the existing list. Management rows expose Remove
instead of playlist ticks/Play; even an undecodable local file can be removed.

The screen handles vanilla dropped-file events and uses vanilla ConfirmScreen
before copying or permanent deletion, with AlertScreen reporting rejections and
per-file results. A mixed drop containing unsupported files is rejected as a whole.
No file changes precede Yes. Copies/deletes run on vanilla's IO executor;
TrackLibrary publishes its refreshed library on the client thread before the
screen reports completion. Busy actions and Escape are disabled during that work.
The retained parent screen refreshes its view on return without losing its query.

MusicFolder owns filesystem mutations. It accepts regular MP3/OGG files without
following source links, uses exclusive creation for destination files, and keeps
existing files intact by numbering duplicates (including case-only collisions).
Failed transfers close the output before removing their partial copy. Removal
accepts only a regular supported file directly inside the normalized music folder;
outside paths, file links and a symlinked music directory are rejected. Open folder
prepares that directory then calls the same `Blaze3D.openPath` as vanilla resource
packs. File operation failures are reported and logged, rather than called success.

Focused command:
`xvfb-run -a ./gradlew jar test --tests '*MusicFolderTest' runClientGameTest -PaddSongsOnly --console=plain`.
The four folder tests and packaged Add music check passed on 2026-11-07. The client
injects the real dropped-file callback, confirms exact MP3/OGG copies and native
playback, No/Yes, bad extensions, duplicate naming, cancel/delete, rescan/return
and 320×240 bounds. Three captures were inspected. The evidence archive is
`runtime/whostoppedthemusic/evidence/add-songs-evidence.zip`; the sole plan records
the tested artifact hash and limitations. Native desktop drag delivery and the
external file-manager window remain human integration checks.

## Key pieces

| Piece | Job | Talks to |
| --- | --- | --- |
| `Settings` | Holds and atomically saves every mod setting | UI edits, first-run initialization and catalog discovery; unchanged playback ticks do not write |
| `TrackLibrary` | Knows tracks and loaded pools; runtime weighted expansion supplies Custom defaults | `SoundManager`, `MusicFolder`, `TrackNames` |
| `Deck` | Picks the next song from a weighted list; remembers history | `MusicDirector` for Basic, its `CustomPlayback` adapter for each Custom key |
| `MusicDirector` | Decides what plays for the mode/resolution; runs transport and exposes actual source/file/advancement | `Settings`, `TrackLibrary`, `Deck`, `JukeboxWatcher` |
| `MusicManagerMixin` | Hands vanilla's choice to `MusicDirector` at the right moments | `MusicDirector`, `SongToast` |
| `TrackSoundInstance` | Plays the chosen file, including MP3/OGG from disk | Sound engine, `Mp3AudioStream` |
| `Preview` | Holds one temporary excerpt and the paused background identity; ends/resumes on the existing tick | `MusicDirector`, existing sound engine/channel controls |
| `MidpointAudioStream` | Counts samples, skips exactly half and bounds/fades the PCM excerpt | Ogg pages, existing OGG/MP3 decoders |
| `MusicScreen` and panels | All the UI | `Settings`, `MusicDirector`, `TrackLibrary` |

## Modes in detail

### Vanilla
- Song selection stays vanilla. The shared SongToast replaces the in-game notification; the compact cassette replaces the pause song line.
- Left: enlarged bare tape, with live title and artist between two reels. Right: the three-mode button, Frequency, Jukebox and Play/Pause/Resume/Next. A vertical Music volume slider is at the far right.
- Master and Music Toast remain in vanilla Options, not this screen. Music and Frequency write the existing vanilla options; Jukebox is saved in Settings and used by the director.

### Basic
- There is **one pool for the whole game**: title screen, every biome and dimension, underwater, creative, the dragon fight and the credits. Vanilla never switches pools, so a song carries on when you change biome or leave the world.
- The song list has three sections: **Soundtrack**, **Music Discs** and **Your Songs**. Tick a song to put it in the deck.
- Every ticked song has weight 1. Shuffle on: the deck is shuffled. Shuffle off: songs play in list order. When the deck is used up it refills and repeats.
- No songs ticked: nothing plays.
- Controls: Play on each row (plays that song now, then the deck carries on), Play/Pause/Resume, separate Stop, Previous, Next, Shuffle, Select All, Deselect All, search, and Add Songs.
- Stop holds silence until the player presses Play, Next or a row's Play, or restarts the game. It is not saved.
- Defaults: every soundtrack song and every disc ticked. A newly added custom song starts ticked.

The implemented Basic body has Soundtrack, Music Discs and Your Songs. OG Moo-cow's
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
during Stop. Your Songs and the shared Add music screen manage imported files.

Basic selects through vanilla's startPlaying sound-instance seam and tick pool
lookup, using one non-replacing Music with GAME delays. Biome volume fades do not
mute this global pool. The world-change stop path keeps only the manager's Basic
source while retiring old-world sounds and queues. Resource reloads retain normal
sound-engine reload behavior. A live Basic source survives world exit/entry;
jukebox entries belong to the old level and cannot leak into the next one.

### Custom
Implemented behavior:
- Main left: searchable specials, then registry biomes grouped by vanilla dimension tags or namespace. Specials except menu/credits have active toggles. Rows show priority/song count, playing and edited distinctions. Right: the shared player/transport/cassette/volume and Add music action; frequency/jukebox labels name all current winners.
- `SituationReader` captures camera-biome/sky/heightmap/precipitation and player underwater/creative state once per existing tick. Pure `ContextResolver` checks credits before menu, then highest-priority biome/active specials. Ties use sorted IDs, sum duplicate-song weights, strongest frequency, Pause music if any winner requests it, and interrupt if any newly winning scenario requests it. An empty active winner is silence, never fallback.
- Implemented defaults use loaded runtime weights: creative adds six weight-1 files to game's 32 files weighted 1/2 (total 43). Nested aliases retain leaf weights and duplicate files add; only oversized reduced weights require approximation. Basic's flattening is unchanged. Pale garden/empty pools stay silent. Weather and underground start disabled; frequency starts Constant and jukebox starts Pause music.
- Custom uses MusicManager → director → existing Deck/TrackSoundInstance, synthetic pool identifiers per resolution, and 32-entry LRU deck history. Vanilla's pool-change/fade/delay logic stays authoritative. Scenario frequency does not overwrite vanilla's global option; edited lists bypass vanilla's zero-volume trap.
- One biome/special editor shares existing search, cassette scrolling, transport/tooltips and Theme. Library drag adds unique songs; focused tape edits weight 1–100 and chance, previews or removes. Reset restores songs only; copy replaces songs/weights only. Right-click frequency/jukebox confirms apply-to-all, including known unloaded and future biomes; Esc/outside/no does nothing.
- Preview pauses the existing background channel, uses about three seconds from the song midpoint, then fades and resumes the same source. Repeated preview, removal or leaving closes it immediately, without deck-history mutation. Imported songs share this path; missing IDs remain stored and are skipped at playback.
- Complete conditions, priorities, schema and supplied screen hierarchy remain in CUSTOM_SPEC.md; functions 11–17 in the plan are the implementation sequence.

## The screen

OG Moo-cow's supplied sketches and revised contract govern this screen. The
mode button cycles Vanilla → Basic → Custom → Vanilla.
MusicControls builds the shared mode/Frequency/Jukebox settings, right player and
transport for Basic and Custom. Basic adds labeled Stop and Shuffle within that
same owner. TransportButton draws Play/Resume, Pause and distinct Next/Previous
symbols, with translated narration and tooltips. The pause-menu Next and browser
row Play use this same button. Theme owns their dimensions, button frames and
colors as well as surrounding dark surfaces. Loaded settings players show a bare
tape; silence shows only an empty player outline and red spindle rods.

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

- Music volume always uses vanilla's own Music option. Vanilla/Basic Frequency also uses the vanilla option and saves on closing. Custom Frequency/Jukebox uses effective scenario settings only; it never writes vanilla's frequency option.
- The screen opens with **M** (rebindable in Controls → Who Stopped The Music) or by clicking the pause-menu cassette.
- **PyreHaven style:** `Theme.java` owns orange `#E46011`, blue `#27607C`, glow `#4FA8D8`, background `#070B10`, card `#0E141C`, text `#F2F4F7`, and layout dimensions. Orange is the main accent; blue/glow mark edges and focus. **No logo anywhere in the UI.**
- The UI must work at GUI scales 1–4 and down to a 320×240 window. Narrow windows shrink the tape and right column while preserving left/right placement; titles scroll inside their reel window.

CustomPanel retains its shared Theme search and ScenarioList across rebuilds.
ScenarioList shares SmoothList's wheel target/frame interpolation with TrackList,
retains vanilla's container/focus machinery, and lists specials in
enum order, then loaded biome groups in Overworld/Nether/End/namespace order.
Absent biomes remain in Settings but are hidden. Theme owns the navy panels,
dark-blue rows, light labels, orange accents and pill geometry. OverflowText
retains shadow-free labels; toggles use orange on tracks and dark off tracks.
Each live row derives count/priority, edited state and winner outline from the
existing settings/playback owners. A separate Toggle consumes its click without
opening the row. Row clicks open the same ScenarioScreen for every biome/special.

ScenarioScreen places the supplied two-row settings grid above a grouped song
library and the existing smooth clipped cassette list. It does not introduce a
SongLibraryList look-alike: TrackList owns compact drag-source rows and scenario
cassettes in addition to its existing Basic and file-management views. All views
share search ranking, scrolling, row/focus/narration and cassette artwork. The
editor library groups non-Minecraft IDs, including imported files, under Your
Songs. Existing IDs display an in-list marker and cannot start a duplicate drag.
The screen retains only the in-progress drag and focused entry identity; songs,
weights and settings remain owned by Settings. A release inside the song viewport
adds once, using the modal existing weight (lowest weight wins a frequency tie)
or 10 for an empty list. A release outside only clears the drag.

Clicking a cassette focuses/centers it. WeightSlider edits that entry from 1–100,
including mouse drag, arrows and Home/End. Its label, tooltip and narration use
the edited list's own total, not merged playback odds. Remove retains intentional
empty silence. Missing IDs keep their weight and a gray Missing cassette; local
decode failures retain the shared Can't play state. List/resource changes refresh
the retained widgets without resetting the library query/scroll.

The same Toggle handles standalone active/interrupt/outside cells, and the same
MusicControls/SettingButton handles concise editor frequency/jukebox plus the
existing apply-all popup. Concise labels use the shared OverflowText without text
shadows and the same button frame; there is no gray styling path. Biomes omit Active;
weather adds Must be outside; underground exposes an integer Y field with invalid
text highlighted and unpersisted. Boss/aquatic/creative leave its cell empty;
menu/credits omit active/priority/interrupt. Priority clamps to 1–10.

Reset changes only songs to loaded defaults. CopyPicker is a nested navigation
screen using ScenarioList's explicit picker mode (no toggles), with the shared
search and scroll owner. Picking replaces songs/weights only; cancel is inert.
The header and main rows derive edited state from Settings.songsEdited. Preview
is enabled only for a focused playable track and calls the director's Preview;
missing/unavailable songs retain Remove but cannot start an excerpt.

### Midpoint preview ownership

The director owns one Preview for its manager. The manager's currentMusic and
channel remain unchanged: Preview uses ChannelControl.pause(true) on that source,
then asks the existing SoundManager for one non-positional TrackSoundInstance on
MUSIC. It never calls Deck.next/played/previous or MusicManager.startPlaying.
The existing holdTick polls its native handle and freezes ordinary manager
selection/delay/fade while the excerpt exists. There is no preview scheduler,
background player, UI-owned sound state or history copy.

TrackSoundInstance opens the same local/resource decoder on vanilla's IO pool.
MidpointAudioStream reads Ogg page granule sample counts; MP3 counts decoded
frames in a bounded-memory pass, including VBR, then reopens. Both discard exact
half-frame PCM, preserving over-read decoder chunks. The stream emits at most
three seconds (less if the remaining half is shorter), with a linear 150 ms
fade in the final samples. Native channel lifetime owns decoder cleanup.
Preview forces streaming and pitch 1 so an arbitrary library sound can use this
excerpt path and sample duration. It retains the track's normal volume modifier.

PreviewVolumeMixin applies vanilla's clamped sound volume and final Music option
to this instance only, bypassing the background category's environment/jukebox
fade gain. Master still applies through vanilla. Preview never changes category
gain, the Music option or another source's volume. A muted preview allocates a
silent native channel and ends normally.

Same-track Preview cancels; another track cancels/replaces. Removal, reset that
removes the excerpt, editor removal (including picker/popup transitions), world
changes, manager stop/mode/transport and missing-library/decode failure end it.
Cancellation stops the managed source, including asynchronous late startup.
Late canceled startup reports a stopped source to vanilla's ChannelAccess tick
after the decoded stream attaches, instead of directly destroying a Channel that
still has a live handle. That tick releases the handle, library source and stream
together. Category-volume callbacks queued before cleanup see a valid source;
callbacks afterward are skipped by ChannelHandle.execute. Cancellation never
issues native Play. Only the retained matching background identity resumes; manual pause and a live
jukebox hold keep it paused. The director's existing jukebox-release branch
unpauses that source when the hold ends, retaining its position if it still exists.
Background silence remains silence; preview cannot create a history entry.

The packaged previewOnly selection verifies resource Ogg midpoint/three-second
native playback, exact frozen sample offset and identical background handle on
resume, unchanged deck cards/cursor/history and config bytes, all editor cancel
routes, imported MP3/OGG, manual pause/silence, mute, existing jukebox hold and a
jukebox beginning during preview. LocalAudioTest compares excerpt PCM against
independently decoded half-file PCM and checks exact length/zero-ended fade.
The plan records the scoped correction evidence: NativeBoundary traced the old
teardown diagnostic to refreshCategoryVolume calling setVolume on a directly
destroyed source. Normal ChannelAccess retirement now owns cleanup, and preview
plus delayed transport checks pass with native-boundary assertions enabled.
Human listening and final delivery acceptance remain pending.

MusicControls accepts a scenario-ID supplier: current resolution winners on the
main screen (even when silent), or the edited ID on ScenarioScreen. It combines
effective settings for display and writes a left-click change to every target.
SettingButton uses 26.3's SDL InputConstants mouse buttons, retains normal
keyboard activation and shares Theme's actual button renderer. Right-click
captures the current value and opens ApplyToAllPopup without mutation. The modal
consumes outside clicks; No/Esc/outside only returns to the retained parent.
Only Yes calls Settings.applyFrequencyToAll/applyJukeboxToAll, whose persisted
defaults cover all specials, loaded/absent known biomes and future discovery.
Custom and Basic both place AddSongsScreen.button, returning to the same parent.

Function 14's packaged customUiOnly check passed on 2026-11-07: normal scale 2
and 320×240 scale 1 bounds/non-overlap, three-mode cycle, search/active toggle,
playing/edited rows, live shared Pause/Resume/Next/Previous, tied silent-winner
labels/edits, both Add links and editor settings navigation. Popup cancellation
preserves exact config bytes; Yes survives disk reload for every known/special
and an absent/future biome. The existing basicUiOnly selection also passes after
moving the right-player layout into MusicControls. See the plan for commands,
logs and artifact hash. Human visual/listening acceptance remains pending.

## Hooks

Implemented hooks are checked against 26.3 bytecode and the packaged client.

1. **`MusicManagerMixin`**
   - The HEAD hook runs the director's existing holdTick, resolving Custom once per tick and observing preview completion even while held. Hold vanilla tick during transport Pause, Basic Stop, preview or a nearby jukebox; observe `startPlaying(Music)` results; clear transport/preview state on `stopPlaying()`.
   - Redirect `getSituationalMusic()` inside tick and stopPlaying.
     - **Basic:** return one constant `Music` (`whostoppedthemusic:basic`, built with `Holder.direct`, vanilla `GAME` delays, `replaceCurrentMusic = false`).
     - **Custom:** return the resolution's synthetic Music with loaded source delays and interrupt intent.
     - **Vanilla:** pass vanilla's answer through.
   - Redirect the cached `gameMusicFrequency` field in tick/stopPlaying: Custom uses the winners' frequency; Vanilla/Basic retain the option. Redirect tick's environment gain: Basic uses 1, edited Custom winners use 1, otherwise use vanilla's gain.
   - **Jukebox set to Pause and a nearby jukebox is playing:** invoke vanilla `fadePlaying(0)` and hold starts. Leaving 64-block camera range, an ended channel or a stop event releases the hold. Play over leaves vanilla behavior alone. Explicit transport pause and Basic Stop remain held.
   - `startPlaying(Music)`: the verified `SimpleSoundInstance.forMusic` call is the existing sound-selection seam.
     - **Vanilla:** normal starts and sound resolution are unchanged.
     - **Basic/Custom:** ask `MusicDirector` for the next deck/history/row track (Custom delegates to its resolution-key deck). Build a `TrackSoundInstance` with the applicable synthetic pool id. If there's no track or playback is held, cancel before sound creation.
   - Both toast calls in `startPlaying` and `showNowPlayingToastIfNeeded()` delegate to the director. All three modes reuse one `SongToast` through the toast manager, following `options.musicToast()` and the notification duration multiplier. New sound identities replace its title and reset its duration; repeated callbacks for the same sound leave the timer alone. `NowPlayingToastMixin` suppresses the legacy duplicate, including option callbacks and slide-out. The replacement hides when its sound stops, is replaced or Music Toast disallows notifications.
2. **`PauseScreenMixin`:** implemented: return false from `rendersNowPlayingToast()` for the full pause menu so the compact cassette owns the title. The cassette and Next are added through Fabric `ScreenEvents.AFTER_INIT`, not this mixin. `MusicChannelMixin` retains stop/pause intent on MusicManager-owned sources across asynchronous startup and global menu resume; accessors expose the existing channel without replacing the sound engine.
3. **`ClientJukeboxMixin` / `ClientLevelAccessor`:** read `ClientLevel.levelEventHandler.playingJukeboxSongs`, which 26.3's `LevelEventHandler.playJukeboxSong` / `stopJukeboxSong` already own. The watcher verifies allocated live channels; no duplicate position map, timer or world lifecycle state exists.
4. **`WeighedSoundEventsAccessor` / `SoundEventReferenceAccessor`:** read weighted files and event-reference entries recursively, preserving volume/pitch multipliers and stream flags. `SoundLibraryMixin` rebuilds after SoundManager applies the loaded registry; `MusicWorldMixin` rebuilds with the arriving world's registries (or empty registries on exit). `TimelineMusicAccessor` exposes loaded attribute tracks so every music keyframe is discoverable without time sampling. Titles are translated components, resolved against the current language rather than a reload-order snapshot.
5. **`MusicWorldMixin` / `LevelSoundMixin`:** the exact `updateLevelInEngines(ClientLevel, boolean)` sound-stop call keeps the active Basic source only. World changes cancel preview and update the Custom catalog. The sound engine retires other sources using existing channel stop intent, clears queued/ticking old-world sounds, and lets its normal cleanup release handles. Vanilla and resource-reload/device-reset stop paths are unchanged.
6. **`PreviewVolumeMixin`:** the sound-engine volume calculation bypasses background fade gain for the one preview instance, retaining Music/Master and track volume. It never changes category gain.

Fabric API instead of mixins:
- Key: `fabric-key-mapping-api-v1`, checked in one `ClientTickEvents.END_CLIENT_TICK` listener. This is the only tick listener in the mod.
- Pause cassette: `fabric-screen-api-v1`.
- Track list rebuild: after the existing SoundManager resource apply, so the registry is complete.
- Custom audio: `fabric-sound-api-v1`.

**No ticking unless needed:**
- Music logic only runs inside vanilla's `MusicManager.tick`.
- The jukebox check runs there only when the setting is Pause and `JukeboxWatcher` has an entry.
- The toast uses vanilla's toast manager.
- `OverflowText` shares clipped, fractional-position rendering across cassette title,
  artist and separate status lines and SongToast. Each line owns an `OverflowMotion`
  content clock: fitting text stays still; overflow waits 1 second at each endpoint
  and travels at 20 GUI pixels/second in alternating directions. Only changed line
  content restarts its clock; resizing and unrelated metadata/status changes do not.
- The music folder is scanned on Basic's first initialization and resource reload,
  on vanilla's IO executor. Later Add Songs actions explicitly request refresh after
  copy/removal. Never on a timer. A revision rejects superseded scans; publication
  merges with the current resource library on the client thread. Rebuilds retain
  existing local tracks while a new scan runs. A rescan preserves a valid playing
  source, including unticked row-play and held Pause; missing/unplayable current
  local tracks stop through the director.

## Tracks and names

`Track` retains its translated display component and exposes structured component
metadata. Explicit title/artist fields take precedence. Otherwise `TrackNames`
recognizes the existing `Artist - Title` convention once at the naming boundary,
preserving remaining hyphens; component slicing retains translated sibling styles.
Resolution happens in the current language, not as a resource-apply snapshot.
Unknown artists remain empty. Search ranks structured title/artist and file IDs.
`MusicDirector.metadata()` resolves Basic/Custom's Track directly and Vanilla's actual
sound-file identity through the library, with translated/prettified fallback.
`playbackStatus()` is independent of that metadata. SongToast begins with title
immediately, followed by artist, sharing the same independent overflow renderer.

- **Discovery:** loaded sound events whose path contains `music` or `theme` (namespace alone does not qualify), plus events referenced by built-in music and loaded biome/dimension background music (default, creative and underwater), timeline music keyframes and jukebox songs regardless of event name. Rebuild after sound resources apply and world registry transitions; server definitions become available after joining that server. Only referenced weighted sound entries are flattened, with cycle protection and file-id dedupe. Jukebox definitions and legacy `music_disc.` pools take DISC precedence over soundtrack aliases of the same file; jukebox descriptions supply their titles.
- **Scenarios:** Custom UI is registry-biome/special based, not a grid of discovered sound pools. Loaded events supply exact-odds default song lists.
- **Soundtrack tracks:** the file entries inside those pools.
  - Title from vanilla's lang: the file path with `/` turned into `.`. For example, `music/game/sweden` → `music.game.sweden` → "C418 - Sweden".
  - Fallback: prettified file name.
  - The real `Sound` entry is reused, so volume and stream settings from `sounds.json` are kept.
- **Discs:** loaded jukebox-song sound events plus legacy `music_disc.` events. Definitions provide their description; legacy pools use `jukebox_song.minecraft.<id>` ("Lena Raine - Pigstep"). 26.3 removes the old `item.minecraft.music_disc_<id>.desc` keys through `lang/deprecated.json`, even though they remain in en_us.json. Discs play on the Music channel as background music, not positional.
- **Your songs:** files in `.minecraft/music/` ending in `.mp3` or `.ogg`, not case-sensitive. The title is the file name without its extension.
  - `MusicFolder` resolves `music/` against the actual game directory, not a fixed launcher path.
    Discovery is flat, deterministic, and excludes symlinks and directories.
    File-name titles remain literal (including `Artist - Title`); artist stays blank.
  - OGG is decoded with vanilla's `JOrbisAudioStream`, MP3 with `Mp3AudioStream`.
    Mono/stereo audio uses native format; MP3 emits incremental interleaved signed
    16-bit PCM in native byte order, retaining partial frames between reads.
  - Decoder probes and playback opens use vanilla's IO executor, never the render
    thread. A probe reads an initial sample block, not the whole song. Playback
    stays streamed and vanilla owns channel/stream cleanup, pause and gain.
  - A file that fails decoding is marked unavailable and logged once per scanned
    `LocalSong`. Rescanning reprobes it, allowing repaired files to recover.
    Unavailable selections remain saved but are excluded from the deck. The existing
    cassette status renderer shows "Can't play" with tooltip/narration; row Play is
    disabled. Late read errors mark the file and end its stream instead of escaping
    onto the sound thread. Failed asynchronous opens release their INITIAL native
    source through the director, even if the file disappeared after discovery.
  - `Track.local` carries the file/open capability for Basic, Custom and previews;
    no second player, scheduler or UI playback map exists. The sound uses a
    collision-free UTF-8 hex file-name identifier under `whostoppedthemusic:local/`;
    display/settings identity remains the literal `custom:<file name>`.
- **Track ids** in settings:
  - Vanilla files use their file id (`minecraft:music/game/sweden`).
  - Discs use the disc's file id (`minecraft:records/pigstep`).
  - Custom files use `custom:<file name>`.

## Settings file

`config/whostoppedthemusic.json`. Unknown keys are ignored. A broken file is renamed to `.broken` and defaults are used. No migration from 1.1.x: the old `whostoppedthemusic.properties` is ignored. Settings writes configVersion 2, preserving firstRunDone, global jukebox and Basic shuffle/unticked values from v1. Custom adds sparse scenario overrides and known biome groups; unedited song lists are computed from loaded resources rather than persisted. Writes retain the existing same-directory temp-file/atomic-move path.

```json
{
  "configVersion": 2,
  "firstRunDone": true,
  "mode": "VANILLA",
  "jukebox": "PAUSE_MUSIC",
  "basic": {
    "shuffle": true,
    "unticked": ["minecraft:records/11"]
  },
  "custom": {
    "defaults": { "frequency": "CONSTANT", "jukebox": "PAUSE_MUSIC" },
    "knownBiomes": { "minecraft:forest": "overworld" },
    "scenarios": {
      "biome/minecraft/forest": {
        "priority": 2,
        "songs": [{ "id": "minecraft:music/game/sweden", "weight": 25 }]
      },
      "special/thunder": { "active": false, "songs": [] }
    }
  }
}
```

- **First launch** (`firstRunDone` false): set vanilla `musicFrequency` to `CONSTANT`, save vanilla options, then set `firstRunDone`. Later launches preserve the option; explicit Vanilla/Basic UI edits still change it.
- Basic stores only **unticked** tracks, so new soundtrack songs from future updates start ticked.
- Custom stores only overrides from runtime defaults. Missing `songs` means defaults;
  `songs: []` means deliberate silence. Song reset leaves settings unchanged.
  Known biome settings survive missing mods; apply-to-all also sets defaults for
  future discovery. Clamp weights 1–100 on load.

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

Nine `SettingsTest` cases cover
defaults, first-run idempotence, round trip/unknown keys/temp cleanup, corrupt
file recovery, failed first-run initialization, Basic mode/shuffle/unticked round trip, byte-preserving v1 load with every Custom ID inheriting defaults, complete v2 scenario-field round trip, unresolved song retention, sparse v2 song inheritance/silence, absent/future biomes, apply-all defaults and load clamping. Three Deck cases cover weighted cycles/history/empty/single. TrackNamesTest covers keys, prettification, metadata and search; the packaged DiscoveryGameTest.checkLiveNames checks actual TrackLibrary soundtrack/disc/fallback components and live-language changes without rebuilding. CI rejects missing/empty/skipped
JUnit reports, missing UI screenshots or pass marker, and source-output loading
instead of the packaged jar. No empty suite is called passed.

## How to build

Focused phase-3 verification:
`xvfb-run -a ./gradlew jar runClientGameTest -PartworkOnly --console=plain`.
This selection extends the existing harness, using packaged artwork/director
classes and pixel comparisons of live screenshots. It checks both modes at
960×720 scale 2, 320×240 scale 1 and 1280×960 scale 4: advancing/frozen/muted
reels, same-source resume, Next, silence/held Stop, only the resolved-file row
turning despite identical titles, holes staying open and exact 2×2 red rods
without a cassette inside empty settings/pause players.

Focused shared-controls verification: `xvfb-run -a ./gradlew jar runClientGameTest -PcontrolsOnly --console=plain`.
Phase-2 metadata/overflow verification adds
`test --tests '*TrackNamesTest' --tests '*OverflowMotionTest'` to that same command.
The selection includes 48 targeted label captures at normal and 320×240 sizes,
with translated/styled/explicit/hyphenated/unknown/modded metadata and changed tracks
on both modes' players, browser, pause and toast. Full suites remain release/checkpoint work.
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

The Gradle setup was adapted from RealSize; the checked-in files are authoritative.

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
jlayer_version=2024.04.19
```

**`settings.gradle`:** Fabric Maven, Maven Central and Gradle Plugin Portal, with `rootProject.name = 'whostoppedthemusic'`.

**`build.gradle`:**
- `loom { clientOnlyMinecraftJar() }`, with a single `main` source set. No `splitEnvironmentSourceSets`, because everything is client code. `mods { whostoppedthemusic { sourceSet sourceSets.main } }`.
- `fabricApi.configureTests { createSourceSet = true; modId = 'whostoppedthemusic-test'; enableGameTests = false; enableClientGameTests = true }` supplies the client harness.
- Build dir `../../runtime/whostoppedthemusic/build`, run dirs `../../runtime/whostoppedthemusic/run/<name>`. Source stays in the checkout; builds, jars and worlds stay under `/home/p-h-r-e-d/phred2.0/runtime`.
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
The jar lands in `/home/p-h-r-e-d/phred2.0/runtime/whostoppedthemusic/build/libs/whostoppedthemusic-1.2.0.jar`.

**CI:** `.github/workflows/build.yml` builds with Java 25, runs unit/default packaged-client checks and all 11 existing feature selections. It requires the current three-mode default marker, nonempty unit reports, screenshots, per-feature markers, clean native boundaries, packaged production-class provenance and the same artifact hash, retaining per-selection logs/captures. Hosted CI results are separate from local evidence.

## Rules for this codebase

UI prose, including import failures, scenario labels, preview help, Missing and
Can't play, uses `lang/en_us.json`. It is the only shipped locale. Minecraft
provides option captions/Yes/No/navigation and biome translation keys; absent
mod biome/group translations fall back to their supplied names. Glyph controls,
literal file paths/IDs and filenames are data rather than English prose. Toggle
uses one translated setting/value pattern for active, interrupt and outside,
instead of describing every setting as Active. Preview help describes the
midpoint/cancel behavior. Function 17 records source-key and packaged-locale checks;
this is translation-ready English, not a claim of other-language translations.

- **One owner per job:** settings only in `Settings`, song choice only in `MusicDirector`, the track list only in `TrackLibrary`. No second manager, wrapper or fallback beside a broken path. Fix the path itself.
- **Mixins only where Fabric API has no suitable hook**: the exact sound registry, music, channel, toast and world-transition seams above; accessors remain read-only except the manager's next-delay/invoker transport seam. Each owns one job.
- **No new tick work.** The single tick listener only checks the key.
- **No hard-coded English** in Java. All text goes in `en_us.json`.
- **No hard-coded colours or sizes** outside `Theme.java`.
- **Pure logic stays Minecraft-free** (`Deck`, `TrackNames`, the settings data) so it can be unit tested.
- **No repeated playback writes.** Settings save on player changes, first-run initialization and changed discovered-biome groups; unchanged ticks do not persist runtime decks/history.
- **Never stop the Records channel.** Jukeboxes are never cut off by our buttons.
- This tree is the full layout. Adding, moving or removing a source file means updating this file first.

## Out of scope

- No server-side part, networking or anything that needs the server to have the mod.
- No new built-in music. The mod only uses vanilla's files, discs and the player's own files.
- No changes to sound effects, ambient sounds or how jukeboxes themselves work.
- No NeoForge or other loaders.
- No downloading music from the internet.
