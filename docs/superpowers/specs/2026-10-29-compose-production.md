# Production Kotlin/Compose migration

Owner explicitly authorizes finalizing Kotlin + Compose and publishing a release. Preserve the shipped capability set and existing user configuration/data; publishing the source benchmark as the finished launcher is not acceptable. Kotlin + Compose is the selected product direction despite the measured cost relative to Views. Lower utilization than Projectivy remains a measured acceptance requirement, with exact limits reported if not demonstrated.

## Architecture

Promote the audited UI into a standalone `native-launcher/` production build, package `nl.neerdael.backdrop_launcher`, activity `.MainActivity`, using Kotlin2.3.21/Compose1.11.4/TV Material1.1.0/AGP8.13.2/API36. Keep a separate benchmark flavor/package and the old benchmark controls. No Flutter engine/runtime in production APK. Retain production signing certificate, updater identity, Home activity identity and native clock screensaver component.

Use `core/Contracts.kt` for independent worker boundaries. Reuse the bounded hardware decoder and audited image nodes. Use manual constructor dependencies consistent with the existing lightweight stack; do not add a DI framework solely for tests. All database, provider and network work stays off UI threads. Pause hidden work; reuse unchanged state/views; keep short device captures.

## Capability and compatibility gate

- Persistent TV/Android/custom rows: create, rename, reorder, hide/delete, membership/order/size/sort; globally hide apps; app actions, info/uninstall and packaged artwork.
- Continue Watching provider items, permission/filter/max-item settings, progress, carousel/hero, cached startup, playback intents and focus memory.
- Ten bundled backdrops plus Trakt device login/token refresh/catalogues/popular lists/TMDB downloads, restored last image and interval/resume settings.
- Appearance, accent, labels, borders, opacity, status/date/time/network controls, back actions and existing native screensaver styles.
- Production GitHub update checks, readable notes, verified downloads and Android installer/unknown-source consent.
- Preserve `app_flutter/db.sqlite` schema12 row/app identities and memberships. Back up before changes. Preserve legacy `FlutterSharedPreferences` keys and exact serialized scalar/list/token encodings. Unknown newer DB schema stays inert; never reset user data.

Use existing Dart code/tests/docs as authoritative behavior references. No silent removal or placeholder implementations of shipped features. Report real-device and provider/account validation limits honestly. Validate synthetic legacy fixtures plus isolated-device app behavior before production publication. Keep private snapshots/signing/API configuration out of Git.

## Release

Migrate exact-source checks/build/signing/package/source-manifest workflows to the native build, preserve public APK asset names and increasing Android code, and record native toolchain provenance. Choose the next appropriate release through the existing guarded main-source pipeline. Do not publish until tests, native behavior/data checks and independent review pass. Do not bypass source/signing gates.
