# Native Home benchmark

This is the first measurement gate for a possible native Android migration. It builds a separate Kotlin/Android Views APK with app rows, app launching, Continue Watching and a cinematic backdrop. It has **no Flutter dependency** and does not replace Backdrop, register as a default Home candidate, or modify the production app's data.

This is a prototype. Settings editing, row management, Trakt login/catalogues, updating, full accessibility/localization and production data migration are not implemented here. Different content and settings must be disclosed with every comparison. A smaller prototype footprint alone does not establish production feature parity or attribute every byte of the existing gap to Flutter.

## Build and install

Use Android SDK 36, Java 17 or 21 and the included Gradle wrapper. Set `ANDROID_HOME` to the SDK directory, or create an ignored `local.properties` containing `sdk.dir=...`.

```sh
cd native-benchmark
./gradlew :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
adb -s TV_SERIAL install -r app/build/outputs/apk/release/app-release.apk
adb -s TV_SERIAL shell am start -n nl.neerdael.backdrop_launcher.nativebenchmark/.HomeActivity
```

The release build is optimized and non-debuggable, signed with a local development certificate. This APK is not an official production release or update. The package is `nl.neerdael.backdrop_launcher.nativebenchmark`.

The default demonstration discovers TV apps alphabetically, uses an included backdrop and reads the public Watch Next provider. If the TV requires a listings permission, grant it only to this benchmark:

```sh
adb -s TV_SERIAL shell pm grant nl.neerdael.backdrop_launcher.nativebenchmark android.permission.READ_TV_LISTINGS
```

Unsupported firmware or denied permission leaves Home usable with an explanation under the gear button. Up/Down changes app rows; Left/Right moves between apps or Continue Watching items; Select launches the focused item. The gear button explains the scene and opens TV settings. It is not the production settings implementation.

## Match visible content

Supply a version-1 JSON file named `scene.json` in the benchmark's external-files directory. Paths refer to packaged assets or files below that directory. Absolute paths, parent traversal and URL paths in scene JSON are rejected. Public Watch Next provider images can use HTTPS or content URIs.

```json
{
  "version": 1,
  "name": "Explicit matching scene",
  "backdrop": "comparison-wallpaper.jpg",
  "accent": "FFFFFF",
  "tileOpacity": 0.2,
  "bigPicture": false,
  "scrim": false,
  "showAppNames": false,
  "discoverApps": false,
  "rows": [
    {"name": "Streaming", "apps": [
      {"package": "example.tv.app", "label": "Example", "artwork": "appicons/movies.png"}
    ]}
  ],
  "watchNext": [
    {"title": "Example programme", "description": "Example episode", "image": "poster.jpg", "progress": 40}
  ]
}
```

Launch the benchmark once to create its external-files directory, then copy the scene and any private images. These files remain outside Git. Relaunch the activity after updating a scene.

```sh
adb -s TV_SERIAL push /local/scene.json /sdcard/Android/data/nl.neerdael.backdrop_launcher.nativebenchmark/files/scene.json
adb -s TV_SERIAL push /local/comparison-wallpaper.jpg /sdcard/Android/data/nl.neerdael.backdrop_launcher.nativebenchmark/files/comparison-wallpaper.jpg
adb -s TV_SERIAL push /local/poster.jpg /sdcard/Android/data/nl.neerdael.backdrop_launcher.nativebenchmark/files/poster.jpg
```

`backdrop`, `accent`, `tileOpacity`, `bigPicture`, `scrim`, `showAppNames` and ordered `rows` control the scene. App artwork is optional and otherwise uses the installed app icon. Continue Watching entries can include `intentUri` and `package` for playback; a scene without playback targets remains viewable. Empty `watchNext` reads live provider items. An unsupported/malformed scene shows an error instead of silently falling back to different content.

The renderer decodes images off the main thread, bounds image dimensions to the display (at most 3840×2160), and retains at most 4 MiB of reusable artwork. On Android 9+, image decoding targets hardware bitmaps; supported older devices use the bounded software fallback. Visible images and Android rendering buffers are additional allocations. It draws the cinematic scrim directly over the backdrop and does not bake an additional fullscreen image. Stopping cancels pending image work and releases displayed image references while retaining lightweight views, focus and bounded cached artwork. Unchanged returns reuse Home; package, provider and referenced local-artwork changes invalidate the appropriate data.

## Measure

Run from the repository root, with the TV available for automated app switching. Explicitly state scene/settings differences in `--scope`. Use `--scene` to archive and hash a matching local scene. The output directory must not already exist.

```sh
python3 -m unittest discover -s tool/native_benchmark -p 'test_*.py' -v
python3 tool/native_benchmark/measure.py \
  --serial TV_SERIAL \
  --output /local/private-results/run-01 \
  --target nl.neerdael.backdrop_launcher/.MainActivity \
  --target nl.neerdael.backdrop_launcher.nativebenchmark/.HomeActivity \
  --target com.spocky.projengmenu/.ui.home.MainActivity \
  --samples 5 --interval 3 --settle 12 \
  --scope 'Describe exact visual/content parity and every known mismatch'
```

The tool captures settled, navigated, background and returned states. It stores raw memory reports, screenshots, frame statistics, package metadata, installed APK hashes, display/device identity and `results.json`. Values are KiB; divide by 1024 for MiB. PSS, RSS and Swap PSS stay separate. Missing metrics/processes, multiple-process reports and unexpected foreground windows fail the run instead of becoming zero or silently undercounting. The tool restores and verifies the original foreground activity in `finally` and checks that the default Home resolution did not change. It does not force-stop apps or clear data.

Repeat runs with target orders reversed to assess warm/order effects. Navigation is a fixed D-pad sequence, not proof that each UI traversed identical content. Screenshots are necessary evidence. Android HWUI frame counters do not capture Flutter raster work equivalently; raw frame reports are diagnostic evidence, not a cross-framework FPS verdict. CPU snapshots likewise do not establish equal CPU cost.

For a short burst, select one target with `--state settled --samples 1 --settle 2 --interval 1`. This captures one snapshot in roughly ten seconds, depending on ADB latency. It is a quick observation, not a stable mean or cold-start measurement. Repeat `--state` to select additional states without running the full sequence.

Add `--cpu-seconds 3` to capture process user+system CPU time from `/proc/PID/stat`, with device uptime and clock-tick frequency. Process restarts invalidate the interval. Add `--cpu-workload navigation` with an explicit foreground `--state` for six alternating Right/Left actions; the actual elapsed interval and action count are recorded. CPU percentages use one-core normalization. Short intervals have tick quantization and scheduling noise; repeat both target orders before making comparative claims. The diagnostic Activity dump exposes native `loads`, `renders`, source `version` and current focus for brief return/reuse checks.

For a migration decision, compare equivalent backdrop pixels, rows, artwork, Continue Watching, display resolution and settings, then validate remote navigation, launch/return behavior, transition peaks and background memory. Keep the full production migration behind that gate.
