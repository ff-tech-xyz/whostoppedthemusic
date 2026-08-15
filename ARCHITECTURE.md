# Who Stopped The Music Structure

`MusicController` is the single playback/state owner. Vanilla hooks and UI surfaces only translate
Minecraft events or player actions into that owner; they do not keep parallel playback, selection,
shuffle, or persistence state. This is the proposed complete tracked tree for the clean v1.1.3
rebuild. The current groundwork intentionally contains only the fail-loud client entrypoint and
packaging resources from this production tree; gameplay owners and executable tests are absent until
they are implemented against `FEATURES.md`.

```text
whostoppedthemusic/
├── src/
│   ├── client/
│   │   ├── java/xyz/pyrehaven/whostoppedthemusic/
│   │   │   ├── WhoStoppedTheMusicClient.java
│   │   │   │   # Fabric client entrypoint and composition root. Wires the owners below; the
│   │   │   │   # groundwork version throws immediately so its jar cannot masquerade as playable.
│   │   │   ├── music/
│   │   │   │   ├── MusicController.java
│   │   │   │   │   # Sole owner of controls, selection, current playback, deliberate silence,
│   │   │   │   │   # world-change resume, Next/Previous, and shuffle cycle/history state.
│   │   │   │   ├── MusicCatalog.java
│   │   │   │   │   # Ordered official v1.1.3 soundtrack/record catalog, aliases, and fallback names.
│   │   │   │   ├── MusicTrack.java
│   │   │   │   │   # Immutable catalog entry: exact resource id and player-facing title.
│   │   │   │   └── DirectMusicSoundInstance.java
│   │   │   │       # Resolves a selected exact vanilla audio resource as non-looping Music playback.
│   │   │   ├── config/
│   │   │   │   └── MusicSettingsStore.java
│   │   │   │       # Sole owner of config/whostoppedthemusic.properties defaults, load, and save.
│   │   │   ├── ui/
│   │   │   │   ├── MusicSettingsScreen.java
│   │   │   │   │   # Side-by-side search/list/control/volume screen; delegates every action.
│   │   │   │   ├── CassetteButton.java
│   │   │   │   │   # Reusable narrated cassette widget used by the pause-menu surface.
│   │   │   │   └── NowPlayingHud.java
│   │   │   │       # Sole current-song/name-notice presentation state and cassette/toast rendering.
│   │   │   └── mixin/
│   │   │       ├── MusicManagerMixin.java
│   │   │       │   # Vanilla scheduling boundary: no-gap/no-early-stop policy and controller suppression.
│   │   │       ├── SoundManagerMixin.java
│   │   │       │   # Observes successful Music/Records starts and forwards them to controller/HUD owners.
│   │   │       ├── MinecraftClientMixin.java
│   │   │       │   # One client-tick boundary for controller lifecycle and notice timing.
│   │   │       ├── KeyboardHandlerMixin.java
│   │   │       │   # Ctrl+M boundary that opens Music Settings without owning key or screen state.
│   │   │       ├── PauseScreenMixin.java
│   │   │       │   # Adds cassette and delegated Previous/Next controls to the normal pause menu.
│   │   │       └── HudMixin.java
│   │   │           # Invokes Now Playing render extraction; owns no presentation state.
│   │   └── resources/
│   │       ├── whostoppedthemusic.mixins.json
│   │       │   # Required client mixin declarations for the six Minecraft integration boundaries.
│   │       └── assets/whostoppedthemusic/lang/en_us.json
│   │           # Player-facing Music Settings, control, status, and Now Playing strings.
│   ├── main/
│   │   └── resources/
│   │       ├── fabric.mod.json
│   │       │   # Client-only Fabric metadata, dependencies, version, icon, and client entrypoint.
│   │       └── assets/whostoppedthemusic/icon.png
│   │           # Packaged mod icon; no audio assets belong in this project.
│   └── test/
│       └── java/xyz/pyrehaven/whostoppedthemusic/
│           ├── MusicPlaybackTest.java
│           │   # Default vanilla policy, controlled actions, suppression, silence, and world resume.
│           ├── ShuffleCycleTest.java
│           │   # Cycle completeness, history Previous/Next, reselection, and boundary reshuffles.
│           ├── MusicCatalogTest.java
│           │   # Exact ordered catalog, aliases, fallback titles, and observable start handling.
│           ├── MusicSettingsStoreTest.java
│           │   # Defaults, exact keys, round trips, malformed values, and I/O failure behavior.
│           └── MusicUiContractTest.java
│               # Control gating, search, open/close routes, volume ownership, and notice timing.
├── ARCHITECTURE.md
│   # This proposed file tree. Groundwork and implementation reviews compare placement against it.
├── FEATURES.md
│   # Accepted released v1.1.3 behavior preserved as the clean-rebuild contract.
├── README.md
│   # Current repository/build status and links to the behavior and structure contracts.
├── CHANGELOG.md
│   # Public release history; scaffold work does not create a release entry or version.
├── LICENSE
├── .gitignore
│   # Excludes Gradle, IDE, build, and local runtime output.
├── build.gradle
│   # Preserved Fabric Loom client build, resource expansion, Java level, and verification tasks.
├── gradle.properties
│   # Preserved Minecraft, Fabric Loader, Loom, Java memory, version, and Maven coordinates.
├── settings.gradle
├── gradlew
├── gradlew.bat
└── gradle/
    ├── minecraft/
    │   ├── 26.2-custom.json
    │   │   # Loom-compatible Minecraft 26.2 metadata consumed by the build.
    │   └── identity-official-26.2.jar
    │       # Pinned official-name mapping input consumed by the build.
    └── wrapper/
        ├── gradle-wrapper.jar
        └── gradle-wrapper.properties
```

Generated output, Gradle caches, IDE state, local run directories, stale platform projects, old
mixin declarations, and compatibility placeholders are deliberately outside this tree.
