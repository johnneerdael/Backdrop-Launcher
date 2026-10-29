# Building Backdrop Launcher

Use Flutter 3.41.9 (Dart 3.11), Android SDK API 36 and Java 17 or 21. The application targets Android TV. The Android application ID is `nl.neerdael.backdrop_launcher`; debug builds append `.debug`.

## Validate changes

```sh
flutter pub get --enforce-lockfile
flutter analyze --no-fatal-infos
flutter test --reporter expanded
python3 -W error -m unittest discover -s tool/release -p 'test_*.py' -v
```

The analyzer currently has pre-existing informational diagnostics. Warnings and errors fail CI. Use an optimized release build for TV performance checks.

The separate [native Home benchmark](../../native-benchmark/README.md) evaluates a possible Android Views migration. It has its own package and build, and is not a replacement production launcher.

## API configuration

The ten packaged backdrops work without API credentials. For authenticated Trakt backdrops, create these ignored files in the project root:

```text
# .tmdb
API=your_tmdb_v3_api_key

# .trakt
CLIENTID=your_trakt_client_id
```

Alternatively, export `TMDB_API_KEY` and `TRAKT_CLIENT_ID`. The device flow uses the public client ID; **do not add a client secret**. Generate the ignored build configuration:

```sh
python3 tool/write_build_config.py
```

Device login has no redirect callback. The existing Trakt application’s registered redirect value is retained only for its refresh-token request schema. Set `TRAKT_REDIRECT_URI` before running the script when using your own Trakt registration.

## Release build

Provide a private signing keystore through `BACKDROP_KEYSTORE_FILE` and these environment variables: `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. Alternatively, place an ignored `android/app/upload-keystore.jks` with the same signing variables, or configure the existing `storeFile`, `storePassword`, `keyAlias`, `keyPassword` fields in ignored `android/local.properties`.

```sh
flutter build apk --release --flavor github \
  --target-platform android-arm,android-arm64 \
  --dart-define-from-file=.dart_tool/trakt-config.json \
  --dart-define=ENABLE_SELF_UPDATER=true
```

Never commit private signing material or API files. Official release automation checks its signing certificate against the production public certificate. Locally signed forks must use their own update distribution.

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

Place transparent app artwork under `appicons/`; the image-only picker discovers packaged entries from Flutter’s asset manifest. Keep ten bundled stills in `backdrops/`. Supplied Backdrop identity exports are under `assets/branding/`, native resources and `docs/assets/brand/`.

Edit `lib/l10n/app_*.arb` and run `flutter gen-l10n`. Do not remove upstream source copyright notices. Keep branding out of the startup path: no custom splash, animation or deliberate waiting period.

## User guide

```sh
python3 -m venv .venv-docs
.venv-docs/bin/pip install -r docs/site-requirements.txt
.venv-docs/bin/python -m mkdocs build --strict
.venv-docs/bin/python -m mkdocs serve
```

The public guide source is `docs/user-guide/`. Historical raw capture archives stay local; optimized guide screenshots include provenance in the guide’s maintenance metadata.
