# Building Backdrop Launcher

Build the production launcher from `native-launcher/` with Java17, Android
SDK API36 and build tools36.0.0. The checked build pins Kotlin2.3.21,
Compose1.11.4, Compose for TV Material1.1.0, AGP8.13.2 and its Gradle wrapper.
The application ID remains `nl.neerdael.backdrop_launcher`; debug builds append
`.debug`. Production does not embed a Flutter engine.

## Validate changes

```sh
python3 -W error -m unittest discover -s tool/release -p 'test_*.py' -v
cd native-launcher
./gradlew :app:testGithubReleaseUnitTest :app:lintGithubRelease :app:assembleGithubRelease --console plain
```

Use an optimized release build for TV performance checks. Without explicit
production signing configuration, Gradle uses a development key; these local
APKs cannot update an installed production release. The official package step
requires the pinned production certificate.

The separate [native Home benchmark](../../native-benchmark/README.md) preserves
the Views and Compose comparison, with separate benchmark packages and controls.
The `native-launcher` benchmark flavor also uses its own `.nativepreview` package.

The [resource optimization results](resource-optimization-results.md) describe
historical Flutter and native measurements. New production performance claims
require measurements of the finished Kotlin/Compose build.

## API configuration

The ten packaged backdrops work without API credentials. For authenticated
Trakt backdrops, export `TMDB_API_KEY` and `TRAKT_CLIENT_ID` before invoking
Gradle. Native BuildConfig reads these variables directly. The device flow uses
the public client ID; **do not add a client secret**. Keep values in the shell or
an ignored local environment file, never in source control.

Device login has no redirect callback. The existing Trakt application's registered
redirect value is retained only for its refresh-token request schema. Export
`TRAKT_REDIRECT_URI` when using a different registration.

## Release build

Export an absolute private keystore path through `BACKDROP_KEYSTORE_FILE`, with
`SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`.
Do not put signing material in the checkout. Run:

```sh
cd native-launcher
./gradlew :app:assembleGithubRelease --console plain
```

The APK is `native-launcher/app/build/outputs/apk/github/release/app-github-release.apk`
relative to the repository root. Local version defaults come from
`native-launcher/version.properties` (`version=1.4.0`, `code=4133`). The guarded
release pipeline selects increasing versions/codes from published history and
passes `-PreleaseVersion` / `-PreleaseCode` to the same build.

The native universal APK is copied to the existing universal, ARM64 and ARM32
public download names. All four names, including `backdrop-launcher.apk`, contain
identical bytes; the release manifest describes the actual architecture payload.

Never commit private signing material or API files. Official release automation
checks the production certificate, APK identity and exact source before
publication. Locally signed forks must use their own update distribution.

## Test TV

Specify the authorized TV explicitly in every ADB command. After installing a release APK:

```sh
adb -s TV_SERIAL install -r path/to/backdrop-launcher.apk
adb -s TV_SERIAL shell am start -n nl.neerdael.backdrop_launcher/.MainActivity
```

Some firmware requires the public TV-listings permission for Continue Watching:

```sh
adb -s TV_SERIAL shell pm grant nl.neerdael.backdrop_launcher android.permission.READ_TV_LISTINGS
```

The package ID changed from the earlier private Media Launcher build. Android treats it as a separate app; installation does not automatically migrate that package’s preferences. Do not document private root-only test-device migration as a public app feature.

## Artwork and translations

Place transparent app artwork under `appicons/`; the image-only picker discovers packaged entries through Android AssetManager. Keep ten bundled stills in `backdrops/`. Supplied Backdrop identity exports are under `assets/branding/`, native resources and `docs/assets/brand/`.

Edit `lib/l10n/app_*.arb`; the native build packages these translation sources.
Historical Flutter localization generation remains a separate legacy workflow. Do not remove upstream source copyright notices. Keep branding out of the startup path: no custom splash, animation or deliberate waiting period.

## User guide

```sh
python3 -m venv .venv-docs
.venv-docs/bin/pip install -r docs/site-requirements.txt
.venv-docs/bin/python -m mkdocs build --strict
.venv-docs/bin/python -m mkdocs serve
```

The public guide source is `docs/user-guide/`. Historical raw capture archives stay local; optimized guide screenshots include provenance in the guide’s maintenance metadata.

## Historical Flutter sources

The Dart implementation remains available as migration/reference material.
Reproduce its historical tests separately with Flutter3.41.9:

```sh
flutter pub get --enforce-lockfile
flutter analyze --no-fatal-infos
flutter test --reporter expanded
```

These commands do not build or validate the Kotlin/Compose production APK.
