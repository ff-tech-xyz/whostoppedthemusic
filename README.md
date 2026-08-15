# Who Stopped The Music

Who Stopped The Music is a client-only Minecraft music-control mod. The latest accepted release is
v1.1.3 for Minecraft 26.2 on Fabric.

## Repository status: structural groundwork

This worktree is an intentionally non-deployable clean-rebuild scaffold. The released gameplay
implementation has been removed. The project can be compiled so its retained build coordinates and
metadata remain verifiable, but the resulting jar is **not a playable mod**: its client entrypoint
throws immediately with a structural-groundwork error.

Do not distribute, install, publish, or test the scaffold jar as a release. No new version has been
assigned, and the retained `1.1.3` build coordinate does not mean these scaffold bytes are v1.1.3
release bytes.

- [`FEATURES.md`](FEATURES.md) is the accepted v1.1.3 behavior contract for the rebuild.
- [`ARCHITECTURE.md`](ARCHITECTURE.md) is the proposed complete tracked file tree and ownership map.
- [`CHANGELOG.md`](CHANGELOG.md) preserves public release history.

The released source and artifacts remain available through Git history and the project's public
release channels.

## Compatibility contract

The rebuild remains scoped to Minecraft 26.2, Fabric Loader 0.19.3+, Java 21+, and client-only use.
This groundwork does not change dependencies, toolchain versions, artifact version, Maven group, or
archive name.

## Source

https://github.com/ff-tech-xyz/whostoppedthemusic

## License

MIT
