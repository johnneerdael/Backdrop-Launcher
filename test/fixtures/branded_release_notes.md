![Backdrop Launcher](https://github.com/johnneerdael/Backdrop-Launcher/raw/a820759cc5bdbd43ac98afbc6c67cbe25ff90fd5/docs/assets/brand/release-banner.png)

# Backdrop Launcher 1.3.0

**Backdrop Launcher — Your TV. Your way.**

[User guide](https://backdrop-launcher.neerdael.nl/) · [All releases](https://github.com/johnneerdael/Backdrop-Launcher/releases)

## What’s new

## Your TV. Your way.

Meet **Backdrop Launcher**: a cinematic Android TV home built around your apps, your artwork and the way you watch. This is the first release with the new Backdrop identity.

- **Your apps, your rows.** Create, reorder and hide rows; choose exactly which apps appear on Home.
- **Continue Watching.** Resume items supplied by compatible TV apps, with a spacious carousel and optional immersive artwork.
- **Cinematic backdrops.** Use included offline artwork or connect Trakt for trending and anticipated movie/show backdrops and popular public lists.
- **Personal touches.** Choose app artwork, accent colour, focus outlines and app-card opacity in 10% steps. Existing accent choices remain yours.
- **A quieter screen.** Choose an uncluttered clock style and customize date/time display.
- **New identity throughout.** Adaptive app icon, TV banner, About screen, repository and illustrated user guide now use the supplied Backdrop Launcher branding.
- **Lean startup.** Locally cached artwork, wallpaper and Continue Watching remain available on startup, with no added branding splash or deliberate wait.

Brightness control and Wi-Fi usage tracking have been removed from the launcher. Trakt supplies backdrop artwork; this release does not add Trakt viewing-history synchronization.

Backdrop Launcher uses a new Android application ID and installs separately from the earlier Media Launcher test builds. It is not an in-place upgrade of that package.

## Downloads

| Package | Download | Use it for |
| --- | --- | --- |
| Universal APK | [backdrop-launcher-1.3.0.apk](https://github.com/johnneerdael/Backdrop-Launcher/releases/download/v1.3.0/backdrop-launcher-1.3.0.apk) | Recommended; ARM32 and ARM64 Android TV devices |
| ARM64 APK | [backdrop-launcher-1.3.0-arm64-v8a.apk](https://github.com/johnneerdael/Backdrop-Launcher/releases/download/v1.3.0/backdrop-launcher-1.3.0-arm64-v8a.apk) | 64-bit ARM devices |
| ARM32 APK | [backdrop-launcher-1.3.0-armeabi-v7a.apk](https://github.com/johnneerdael/Backdrop-Launcher/releases/download/v1.3.0/backdrop-launcher-1.3.0-armeabi-v7a.apk) | 32-bit ARM devices |
| Stable download link | [backdrop-launcher.apk](https://github.com/johnneerdael/Backdrop-Launcher/releases/latest/download/backdrop-launcher.apk) | Always the latest universal APK |

Backdrop Launcher is a **separate install from Media Launcher**. Set it as your TV’s home app after installation. See the [installation guide](https://backdrop-launcher.neerdael.nl/get-started/install/).

## Source and verification

- Android version: **1.3.0 (4127)**. APKs are release-signed with the Backdrop Launcher signing key.
- [SHA256SUMS](https://github.com/johnneerdael/Backdrop-Launcher/releases/download/v1.3.0/SHA256SUMS) covers all release assets. Download the assets together and run `sha256sum --check SHA256SUMS`.
- [Release manifest](https://github.com/johnneerdael/Backdrop-Launcher/releases/download/v1.3.0/release-manifest.json) records the exact source, successful Checks run, Android versionCode, APK checksums and signing certificate SHA-256.
- [Corresponding source archive](https://github.com/johnneerdael/Backdrop-Launcher/releases/download/v1.3.0/backdrop-launcher-1.3.0-source.tar.gz) · [Source commit `a820759cc5bd`](https://github.com/johnneerdael/Backdrop-Launcher/tree/a820759cc5bdbd43ac98afbc6c67cbe25ff90fd5) · [Full changelog](https://github.com/johnneerdael/Backdrop-Launcher/commits/a820759cc5bdbd43ac98afbc6c67cbe25ff90fd5).
- [GPL-3.0 license](https://github.com/johnneerdael/Backdrop-Launcher/releases/download/v1.3.0/LICENSE) · [Credits](https://github.com/johnneerdael/Backdrop-Launcher/releases/download/v1.3.0/CREDITS.md). Built on FLauncher by Étienne Fesser and the Media Launcher community; Flutter and third-party notices remain available in the app.

Verified by [Checks run 40611902068, attempt 1](https://github.com/johnneerdael/Backdrop-Launcher/actions/runs/40611902068).
