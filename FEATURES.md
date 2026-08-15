# Who Stopped The Music — Accepted v1.1.3 Behavior

This document is the behavior contract for rebuilding the released Fabric client mod. It records the
accepted behavior of v1.1.3; it does not describe the temporary structural scaffold currently in this
worktree and does not propose additional features.

## Platform and scope

- The mod runs only on the Minecraft client. A server installation is neither required nor used.
- The accepted release targets Minecraft 26.2, Fabric Loader 0.19.3 or newer, and Java 21 or newer.
- It uses Minecraft's existing soundtrack and record audio. It adds no audio files.
- It affects the `MUSIC` and `RECORDS` sound categories only. It does not alter sound effects,
  ambient sounds, or jukebox mechanics.

## Default vanilla-controlled mode

Controls are off by default. While controls are off:

1. Minecraft remains responsible for choosing music and responding to music-type changes.
2. When no vanilla music instance is active, any positive inter-track delay is reduced to zero so the
   next eligible vanilla track can start without the normal random silence gap.
3. A no-argument vanilla request to stop music is ignored only while the current music instance is
   active, allowing that song to finish. Typed music-change stops remain available so vanilla can
   change music for contexts such as biome transitions.
4. The Music Settings controls do not change playback or track selection. The Music Volume control
   remains available because it is Minecraft's own Music volume setting.
5. Successful starts in the Music or Records category still update the current-song display and show
   the Now Playing notice.

Turning controls off stops all active Music and Records playback, including vanilla-origin sounds,
clears the active custom track, and returns selection authority to vanilla. The no-gap and
no-early-cutoff behavior remains active.

## Now Playing notice and song names

- Every successfully started `MUSIC` or `RECORDS` sound is observed; sounds that do not start are
  ignored.
- The resolved sound resource is preferred over its event/pool id when choosing a display name.
- Known soundtrack resources, record resources, and vanilla music pools use the catalog names below.
  An unknown id is displayed as a readable title made from its path; a missing id is `Unknown song`.
- The compact notice appears at the top center for 100 client ticks and fades during its final 20
  ticks. Re-observing the same sound identity while its notice is still visible does not restart it.
- The pause-menu cassette and Music Settings screen show the most recently observed song name, or
  `No song playing` before any song has been observed.

## Music Settings surfaces

The Music Settings screen opens when the player presses `Ctrl+M`, including when another screen is
open, or clicks the cassette at the top of the normal pause menu. Pressing `Ctrl+M` while Music
Settings is already open leaves the existing screen in place. Closing Music Settings returns to the
screen from which it was opened.

The accepted side-by-side screen contains:

- title and current-song text at the top;
- a case-insensitive search field and full-height checkbox track list on the left;
- Controls, Select All, Deselect All, Previous, Play/Stop, Next, Shuffle, status, and Done on the
  right;
- a vertical Music Volume control at the far right;
- per-track Play buttons and horizontally scrolling long titles.

Search matches either the displayed title or the resource id. Playback and selection widgets are
disabled while Controls is off. The pause-menu Previous and Next buttons are enabled only when
Controls is on and at least one track is selected; the cassette itself always opens Music Settings.

## Controlled playback

When Controls is on, the mod owns playback of the selected catalog resources:

- All catalog tracks begin selected on a fresh configuration.
- Selecting or deselecting a track changes whether controlled playback may choose it.
- Pressing a track's Play button starts that exact resource. It first stops both Music and Records
  playback. A direct play also selects the track if necessary.
- Play is shown when no Music or Records instance known to the controller is active. It starts one
  random selected track, or the next shuffle track when Shuffle is on.
- Stop is shown while music or a record known to the controller is active. It stops both Music and
  Records and keeps them silent until the player starts playback again or turns Controls off.
- Next and Previous stop both categories and choose another selected track. With Shuffle off, they
  walk the catalog's selected tracks in catalog order and wrap at either end. If no current catalog
  track is known, Next starts the first selected track and Previous starts the last.
- Deselecting the current track stops it and immediately chooses a replacement when one remains. With
  Shuffle off, replacement search moves forward from the removed track in catalog order and wraps.
  If none remains, playback becomes intentionally silent.
- Select All selects the entire catalog. Deselect All deselects the entire catalog, stops Music and
  Records, clears the resumable track, and keeps playback silent.
- While controlled playback is active, pending restoration, shuffled, intentionally silent, or has
  no selected tracks, vanilla music scheduling is suppressed so it cannot compete with the controller.
- Music Volume writes Minecraft's built-in Music volume option from 0% through 100% and saves the
  Minecraft options file; it does not create a second volume setting.

## Deterministic shuffle

- Shuffle is off by default and may be changed only while Controls is on and at least one track is
  selected.
- Turning Shuffle on builds a randomly ordered cycle from the selected tracks. A current or saved
  selected track anchors the cycle and is treated as already played.
- Next consumes the predetermined cycle. A selected track does not repeat until every selected track
  in that cycle has been used.
- After exhaustion, a new cycle is shuffled. When more than one track is selected, the new cycle does
  not begin with the track that ended the preceding cycle.
- Previous walks backward through actual shuffle history rather than making a new random choice. The
  track left by Previous is returned to the front of the remaining order so Next can revisit it.
- Enabling or disabling tracks removes unavailable entries and incorporates newly available entries;
  the order remains anchored around the current track.
- Turning Shuffle off clears shuffle order/history. Tracks started without Shuffle do not
  automatically continue after they end; tracks started with Shuffle do.

## World changes and persistence

Settings are stored in `config/whostoppedthemusic.properties` using these keys and fresh-install
defaults:

| Key | Default | Meaning |
| --- | --- | --- |
| `controls` | `false` | Whether controlled playback is enabled. |
| `shuffle` | `false` | Whether deterministic shuffle is enabled. |
| `disabled` | empty | Comma-separated resource ids excluded from controlled playback. |
| `current` | empty | Exact resource id to resume when appropriate. |
| `silenced` | `false` | Whether Stop or an empty selection intentionally requires silence. |

- Runtime changes to controls, shuffle, selection, current track, and silence state are saved.
- If controlled playback leaves a world while a resumable track exists and playback was not
  intentionally silenced, that exact selected track is resumed after a world becomes available again.
- The same pending resume occurs after startup when Controls is on, `current` is valid and selected,
  and `silenced` is false.
- A naturally completed non-shuffle track clears its saved current track while in a world. Shuffle
  advances to the next cycle entry.
- Read or write I/O failure does not prevent the music screen or in-memory controls from operating.

## Exact selectable catalog

The v1.1.3 catalog order is part of the accepted behavior because non-shuffle Previous/Next and
replacement selection depend on it.

### Soundtrack resources

```text
music/game/a_familiar_room — A Familiar Room
music/game/an_ordinary_day — An Ordinary Day
music/game/ancestry — Ancestry
music/game/below_and_above — Below and Above
music/game/broken_clocks — Broken Clocks
music/game/bromeliad — Bromeliad
music/game/clark — Clark
music/game/comforting_memories — Comforting Memories
music/game/creative/aria_math — Aria Math
music/game/creative/biome_fest — Biome Fest
music/game/creative/blind_spots — Blind Spots
music/game/creative/dreiton — Dreiton
music/game/creative/haunt_muskie — Haunt Muskie
music/game/creative/taswell — Taswell
music/game/crescent_dunes — Crescent Dunes
music/game/danny — Danny
music/game/deeper — Deeper
music/game/dry_hands — Dry Hands
music/game/ebb — Ebb
music/game/echo_in_the_wind — Echo in the Wind
music/game/eld_unknown — Eld Unknown
music/game/end/alpha — Alpha
music/game/end/boss — Boss
music/game/end/the_end — The End
music/game/endless — Endless
music/game/featherfall — Featherfall
music/game/fireflies — Fireflies
music/game/floating_dream — Floating Dream
music/game/haggstrom — Haggstrom
music/game/home — Home
music/game/infinite_amethyst — Infinite Amethyst
music/game/key — Key
music/game/komorebi — komorebi
music/game/left_to_bloom — Left to Bloom
music/game/lilypad — Lilypad
music/game/living_mice — Living Mice
music/game/memories — Memories
music/game/mice_on_venus — Mice on Venus
music/game/minecraft — Minecraft
music/game/nether/ballad_of_the_cats — Ballad of the Cats
music/game/nether/concrete_halls — Concrete Halls
music/game/nether/crimson_forest/chrysopoeia — Chrysopoeia
music/game/nether/dead_voxel — Dead Voxel
music/game/nether/nether_wastes/rubedo — Rubedo
music/game/nether/soulsand_valley/so_below — So Below
music/game/nether/warmth — Warmth
music/game/nightly — Nightly
music/game/one_more_day — One More Day
music/game/os_piano — O's Piano
music/game/oxygene — Oxygène
music/game/pokopoko — pokopoko
music/game/puzzlebox — Puzzlebox
music/game/shores — Shores
music/game/stand_tall — Stand Tall
music/game/subwoofer_lullaby — Subwoofer Lullaby
music/game/swamp/aerie — Aerie
music/game/swamp/firebugs — Firebugs
music/game/swamp/labyrinthine — Labyrinthine
music/game/sweden — Sweden
music/game/watcher — Watcher
music/game/water/axolotl — Axolotl
music/game/water/dragon_fish — Dragon Fish
music/game/water/shuniji — Shuniji
music/game/wending — Wending
music/game/wet_hands — Wet Hands
music/game/yakusoku — yakusoku
music/menu/beginning_2 — Beginning 2
music/menu/floating_trees — Floating Trees
music/menu/moog_city_2 — Moog City 2
music/menu/mutation — Mutation
```

### Record resources

```text
records/11 — 11
records/13 — 13
records/5 — 5
records/blocks — blocks
records/bounce — Bounce
records/cat — cat
records/chirp — chirp
records/creator — Creator
records/creator_music_box — Creator (Music Box)
records/far — far
records/lava_chicken — Lava Chicken
records/mall — mall
records/mellohi — mellohi
records/otherside — otherside
records/pigstep — Pigstep
records/precipice — Precipice
records/relic — Relic
records/stal — stal
records/strad — strad
records/tears — Tears
records/wait — wait
records/ward — ward
```

### Vanilla music-event fallback labels

These labels are used when a started vanilla sound has not exposed its resolved audio resource yet.

```text
music.creative — Creative Music
music.credits — Alpha
music.dragon — Boss
music.end — The End
music.game — Survival Music
music.menu — Menu Music
music.under_water — Underwater Music
music.nether.basalt_deltas — Basalt Deltas Music
music.nether.crimson_forest — Crimson Forest Music
music.nether.nether_wastes — Nether Wastes Music
music.nether.soul_sand_valley — Soul Sand Valley Music
music.nether.warped_forest — Warped Forest Music
music.overworld.badlands — Badlands Music
music.overworld.bamboo_jungle — Bamboo Jungle Music
music.overworld.cherry_grove — Cherry Grove Music
music.overworld.deep_dark — Deep Dark Music
music.overworld.desert — Desert Music
music.overworld.dripstone_caves — Dripstone Caves Music
music.overworld.flower_forest — Flower Forest Music
music.overworld.forest — Forest Music
music.overworld.frozen_peaks — Frozen Peaks Music
music.overworld.grove — Grove Music
music.overworld.jagged_peaks — Jagged Peaks Music
music.overworld.jungle — Jungle Music
music.overworld.lush_caves — Lush Caves Music
music.overworld.meadow — Meadow Music
music.overworld.old_growth_taiga — Old Growth Taiga Music
music.overworld.snowy_slopes — Snowy Slopes Music
music.overworld.sparse_jungle — Sparse Jungle Music
music.overworld.stony_peaks — Stony Peaks Music
music.overworld.sulfur_caves — Sulfur Caves Music
music.overworld.swamp — Swamp Music
```

## Explicit non-features

The accepted release does not add music, download music, control server playback, alter sound effects
or ambient sounds, add commands, change jukebox behavior, or provide a NeoForge build.
