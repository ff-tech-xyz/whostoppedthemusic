# Who Stopped The Music architecture

Client-only Fabric mod for Minecraft 26.3 and Java 25. See [configuration](CONFIGURATION.md) for player controls, scenario defaults and the JSON schema.

## Ownership and contracts

- `WhoStoppedTheMusic` owns initialization, settings, library publication and menu/key hooks.
- `MusicDirector` belongs to vanilla's `MusicManager`; selection and transport stay inside its existing tick. There is no second background player or tick loop.
- `Deck`, `TrackNames` and `ContextResolver` contain pure selection/naming rules. Basic uses one deck; Custom retains up to 32 resolution-key decks.
- `TrackLibrary` reads loaded sounds and registries. Nested event odds use runtime referenced-pool weights; duplicate leaves add, path-local cycles stop. Editor weights reduce by common divisor before proportional 1–100 scaling.
- `BiomeCatalog` uses bootstrapped vanilla definitions at menus and the exact joined registry in worlds. Missing saved biomes keep overrides without becoming invented rows.
- `ContextResolver` takes effective configs and previous winners explicitly. Highest priority wins; ties merge songs/raw weights, frequency and jukebox policy. Only newly winning interrupt flags interrupt; Menu/Credits always do. Empty winners mean silence, not fallback.
- `Settings` saves sparse v2 overrides atomically. Omitted songs inherit live defaults; explicit empty songs mean silence. Invalid Custom entries are warned/skipped independently. Save IO failures warn while preserving live preferences; malformed root files retain `.broken` recovery.
- `MusicChannelControl` retains pause/stop intent across asynchronous stream attachment. Native operations use sound-thread handles from `SoundChannels`; Records and effects remain vanilla-owned. `DirectorAccess` and `LevelSoundControl` are the other explicit mixin contracts.
- `TrackSoundInstance` supplies local MP3/OGG audio through the same music channel. `MusicFolder` owns flat discovery and confirmed copy/remove boundaries. JLayer stays bundled with its separate licence.
- `MidpointAudioStream` counts Ogg granules or compressed MP3 headers, decodes through the exact half-sample start and at most three seconds, then fades for 150 ms. MP3 decoding starts at zero to retain reservoir/filter history, not at an approximate byte seek.
- `Preview` owns one temporary source, never deck history. Cancel/end resumes the same background source while respecting manual pause and jukebox hold.
- `MusicControls`, `Theme` and `CassetteArtwork` own shared controls, surfaces and 19:10 tapes. Native channel progress drives reels/notes; pause freezes them, mute does not. Vanilla owns notification placement/options/timing; the toast mixin supplies resolved metadata.
- Feature-sized client tests share one Fabric entrypoint and support helpers. The delayed-stream gate and native boundary assertions remain development-only. Toggle tests diagnose input/pixels without changing renderer behavior.

## Build and verification

Run `./gradlew build` for the installable `build/libs/whostoppedthemusic-1.2.0.jar` and JUnit reports. Run `xvfb-run -a ./gradlew runClientGameTest -PpreviewOnly` for one packaged-client selection; omit the selection for the Vanilla smoke case. Other selections are listed in `ClientSmokeGameTest.FEATURES`.

Defaults are checkout-local `build/` and `run/<name>/`. Explicit `-PbuildDir=/absolute/build` and `-PruntimeDir=/absolute/runtime` overrides are optional. Class provenance logs live under the selected build directory's `evidence/`. CI relies on Gradle/Fabric failures, checks the artifact hash between groups, and retains reports/screenshots/logs, including menu, biome and toggle selections.

## Source inventory

```text
whostoppedthemusic/
├── src/gametest/java/xyz/pyrehaven/whostoppedthemusic/AddSongsGameTest.java
├── src/gametest/java/xyz/pyrehaven/whostoppedthemusic/BasicGameTest.java
├── src/gametest/java/xyz/pyrehaven/whostoppedthemusic/BiomeUiGameTest.java
├── src/gametest/java/xyz/pyrehaven/whostoppedthemusic/CassetteGameTest.java
├── src/gametest/java/xyz/pyrehaven/whostoppedthemusic/ClientSmokeGameTest.java
├── src/gametest/java/xyz/pyrehaven/whostoppedthemusic/ControlsGameTest.java
├── src/gametest/java/xyz/pyrehaven/whostoppedthemusic/CustomPlaybackGameTest.java
├── src/gametest/java/xyz/pyrehaven/whostoppedthemusic/CustomUiGameTest.java
├── src/gametest/java/xyz/pyrehaven/whostoppedthemusic/CustomUiSupport.java
├── src/gametest/java/xyz/pyrehaven/whostoppedthemusic/DiscoveryGameTest.java
├── src/gametest/java/xyz/pyrehaven/whostoppedthemusic/GameTestSupport.java
├── src/gametest/java/xyz/pyrehaven/whostoppedthemusic/LocalSongsGameTest.java
├── src/gametest/java/xyz/pyrehaven/whostoppedthemusic/MenuGameTest.java
├── src/gametest/java/xyz/pyrehaven/whostoppedthemusic/PreviewGameTest.java
├── src/gametest/java/xyz/pyrehaven/whostoppedthemusic/ScenarioArtworkGameTest.java
├── src/gametest/java/xyz/pyrehaven/whostoppedthemusic/ScenarioEditorGameTest.java
├── src/gametest/java/xyz/pyrehaven/whostoppedthemusic/ToggleGameTest.java
├── src/gametest/java/xyz/pyrehaven/whostoppedthemusic/TransportGameTest.java
├── src/gametest/java/xyz/pyrehaven/whostoppedthemusic/VanillaGameTest.java
├── src/gametest/java/xyz/pyrehaven/whostoppedthemusic/test/mixin/NativeBoundary.java
├── src/gametest/java/xyz/pyrehaven/whostoppedthemusic/test/mixin/TransportStreamGate.java
├── src/gametest/resources/assets/discovery/sounds.json
├── src/gametest/resources/assets/music_namespace/sounds.json
├── src/gametest/resources/fabric.mod.json
├── src/gametest/resources/whostoppedthemusic-test.mixins.json
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/JukeboxSetting.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/Mode.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/Settings.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/WhoStoppedTheMusic.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/mixin/ClientJukeboxAccessor.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/mixin/ClientLevelAccessor.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/mixin/LevelSoundMixin.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/mixin/MusicChannelMixin.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/mixin/MusicManagerAccessor.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/mixin/MusicManagerMixin.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/mixin/MusicWorldMixin.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/mixin/NowPlayingToastMixin.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/mixin/PauseScreenMixin.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/mixin/PreviewVolumeMixin.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/mixin/SoundEngineAccessor.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/mixin/SoundEventReferenceAccessor.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/mixin/SoundLibraryMixin.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/mixin/SoundManagerAccessor.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/mixin/TimelineMusicAccessor.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/mixin/WeighedSoundEventsAccessor.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/Deck.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/DirectorAccess.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/JukeboxWatcher.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/LevelSoundControl.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/LocalSong.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/MidpointAudioStream.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/Mp3AudioStream.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/MusicChannelControl.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/MusicDirector.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/MusicFolder.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/OverflowMotion.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/SoundChannels.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/Track.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/TrackLibrary.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/TrackNames.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/TrackSoundInstance.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/custom/BiomeCatalog.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/custom/ContextResolver.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/custom/CustomDefaults.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/custom/CustomPlayback.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/custom/Preview.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/custom/Resolution.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/custom/ScenarioConfig.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/custom/ScenarioId.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/custom/Situation.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/custom/SituationReader.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/music/custom/Special.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/ui/AddSongsScreen.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/ui/BasicPanel.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/ui/CassetteArtwork.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/ui/CassetteWidget.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/ui/MusicControls.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/ui/MusicScreen.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/ui/MusicVolumeSlider.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/ui/OverflowText.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/ui/Theme.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/ui/TransportButton.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/ui/custom/ApplyToAllPopup.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/ui/custom/CustomPanel.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/ui/custom/ScenarioLabels.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/ui/custom/ScenarioList.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/ui/custom/ScenarioScreen.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/ui/custom/Toggle.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/ui/widget/SettingButton.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/ui/widget/SmoothList.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/ui/widget/TrackList.java
├── src/main/java/xyz/pyrehaven/whostoppedthemusic/ui/widget/WeightSlider.java
├── src/main/resources/assets/whostoppedthemusic/icon.png
├── src/main/resources/assets/whostoppedthemusic/lang/en_us.json
├── src/main/resources/fabric.mod.json
├── src/main/resources/whostoppedthemusic.mixins.json
├── src/test/java/xyz/pyrehaven/whostoppedthemusic/ContextResolverTest.java
├── src/test/java/xyz/pyrehaven/whostoppedthemusic/DeckTest.java
├── src/test/java/xyz/pyrehaven/whostoppedthemusic/LocalAudioTest.java
├── src/test/java/xyz/pyrehaven/whostoppedthemusic/MusicFolderTest.java
├── src/test/java/xyz/pyrehaven/whostoppedthemusic/OverflowMotionTest.java
├── src/test/java/xyz/pyrehaven/whostoppedthemusic/SettingsTest.java
├── src/test/java/xyz/pyrehaven/whostoppedthemusic/TrackNamesTest.java
├── src/test/resources/audio/README.txt
├── src/test/resources/audio/mono.ogg
├── src/test/resources/audio/stereo.mp3
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/wrapper/gradle-wrapper.properties
├── .github/workflows/build.yml
├── docs/ARCHITECTURE.md
└── docs/CONFIGURATION.md
```
