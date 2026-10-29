# Resource optimization first pass — 29 October 2026

The first pass is implemented and validated. The native candidate measured lower graphics, PSS and process CPU time than the installed Projectivy configuration in two short checks. **The complete-product requirement remains open:** native is still a migration prototype, content/settings differ, native RSS remains higher, and the Flutter application still exceeds Projectivy's graphics footprint.

## Changes

Flutter now draws the cinematic gradient directly over the source image instead of allocating a second baked fullscreen image. Outgoing full-size decode keys are retired after the fade, with rapid return-to-image protection. Startup prepares at most seven apps from the first visible row; other artwork loads on demand. Continue Watching prefetch covers the selected item and one lookahead instead of six items. Visibility/generation checks prevent hidden polling, late disk misses starting network/provider requests, and stale responses refilling the cache. Resume restores missing selected artwork even if the provider fails. Interrupted cached-image preparation preserves valid offline files. Clock timers stop while hidden and resume immediately; changing seconds/interval settings reconfigures the timer.

Native now uses hardware image decoding on Android 9+ and hardware copies of rasterized app icons on supported devices. Older supported Android versions retain the bounded software path. Image requests coalesce by source/size, queue capacity is bounded, and detached/hidden targets stop requesting work. Views, focus, scroll position and the small artwork cache survive unchanged returns. Package/provider/local-file changes invalidate data; referenced artwork subdirectories are watched, with revision validation on return. Watch Next-only refreshes reuse the discovered app rows.

No whole-window resolution, backdrop decode budget or color-depth reduction was used to achieve the native measurements. Both windows render at 1920×1080 on the test TV. Image ownership/CPU copies changed; PSS, RSS and Swap PSS are reported separately to expose accounting differences.

## Short measurements

Each row below is one guarded snapshot, not a statistical mean. Graphics and memory values are MiB. Idle capture order was native then Projectivy; navigation order was reversed. The navigation workload used six alternating Right/Left actions. CPU values come from process user+system ticks with PID/start-time validation and actual device uptime; the tick frequency was 100 Hz.

| Check | App | Graphics | PSS | RSS | Swap PSS | Process CPU time / elapsed |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Idle | Native | 5.39 | 82.22 | 168.68 | 0.39 | 0.01 s / 3.61 s |
| Idle | Projectivy | 7.66 | 88.11 | 111.68 | 47.08 | 0.82 s / 3.24 s |
| Six navigation actions | Native | 5.39 | 48.17 | 121.48 | 5.91 | 0.23 s / 4.64 s |
| Six navigation actions | Projectivy | 5.66 | 85.85 | 111.61 | 43.61 | 1.09 s / 4.15 s |

The earlier native prototype was approximately 16.2 MiB graphics. An intermediate ablation using hardware decoding only for encoded images measured 7.72 MiB; applying the same ownership strategy to rasterized app icons reached approximately 5.4 MiB. These observations support the decoder/resource changes. They do not isolate every memory allocation or prove a universal cross-framework ratio.

Native PSS varied substantially between the fresh and warmed checks. Existing apps also held substantial swapped memory. Native RSS did **not** beat Projectivy. The scenes have different wallpaper, row contents and Continue Watching presentation/filtering; APK identity, screenshots, raw meminfo and CPU counters are retained with each capture. The shorter intervals also have tick quantization and scheduling noise. Do not substitute these results for matched-content, complete-feature acceptance.

## Flutter result and migration decision

An optimized, non-debuggable Flutter release was built and installed under the separate `nl.neerdael.backdrop_launcher.optimized` test identity, with the Home intent removed. This protects the production installation and default Home. It used fresh default settings, unlike the user's configured production app.

Its first short snapshot recorded 36.10 MiB graphics, 179.13 MiB PSS, 250.87 MiB RSS and 0.48 MiB Swap PSS, with zero observed CPU ticks over 2.61 seconds. This fresh/cold result is not directly comparable to the earlier warm production measurement. It nevertheless does **not** demonstrate the required lower graphics footprint. The Flutter fixes remove known avoidable work; the native Home migration remains the direction supported by the measured graphics gap.

The direct Flutter hero path now uses the shared display-height decode. Very wide or portrait hero sources require visual acceptance against the former baked cover path; the default and ordinary 16:9 scenes are retained in screenshots/tests. This first pass does not certify every image aspect ratio.

## Return, correctness and build validation

The final native APK passed an uncontested short return check after six Right actions: focus remained on the same offscreen app, and `loads=1`, `renders=1`, `version=1` before and after opening TV settings and returning. This verifies that unchanged return avoided package enumeration, data reload and Home reconstruction in that check. The original foreground activity was restored.

- Flutter 3.41.9: 235 tests passed, four existing skips; analysis passed with 32 informational diagnostics and no errors/warnings.
- Native: ten unit tests passed; release build and Android lint passed.
- Capture tools: eight tests passed, including CPU parsing/restart rejection and foreground restoration.
- Existing release tools: 20 tests passed.
- Independent review: significant lifecycle/cache issues fixed and rechecked; no outstanding significant findings.
- Both release APKs built and installed as separate test packages. The primary checkout, production package/data and default Home were preserved. No merge, push or production release was performed.

The full test run exposed an existing missing-child race in wallpaper fixture teardown. It now uses the same bounded ENOENT-only cleanup retry already used by the Watch Next fixture; other filesystem failures still fail the test.

## Artifacts and acceptance status

Final native APK: `native-benchmark/app/build/outputs/apk/release/app-release.apk`.

Native SHA-256: `974aefe1ccddb797faba160f44804b8903942d9b05004b25d8a89c1c8da2bc53`.

Flutter test APK: `build/app/outputs/apk/github/release/backdrop-launcher-universal-release.apk`. It is locally development-signed, labelled **Backdrop Optimized Test**, and is not a production update.

Flutter test SHA-256: `911ae15c9c76e03d0ff8e2c0c7f343b1d0273bef34151b17eb9808a77fba2b2a`.

Ignored local evidence: `native-benchmark/evidence/2026-10-29-resource-optimization/`, including decoder ablation, both comparison orders, Flutter snapshot, final native return check, APK hashes, screenshots, build/test logs and temporary Flutter test-package build overrides. Earlier comparative APKs are identified by the hashes inside their individual runs; the final return check used the final native APK.

**Accepted first-pass deliverables:** image/lifecycle changes, native reuse, passing tests/builds, and qualified short measurements.

**Still required for the owner's product target:** migrate the remaining production capabilities to native, preserve existing configuration/data, and demonstrate lower utilization with equivalent scene/settings and full capability coverage. Do not mark the full launcher target achieved from this prototype result.
