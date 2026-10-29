# Compose for TV evaluation — 29 October 2026

**Status:** a separate Kotlin/Compose candidate is built and source-reviewed. Initial device comparisons are promising on memory but show that stock TV Material card interactions cost more CPU in this implementation. A lighter hot-card path and a full seven-category performance audit are implemented. The final audited APK still needs on-device acceptance; the TV became asleep with no focused window, and subsequent captures were rejected. No final Compose adoption or complete production migration is claimed.

Production release [1.3.5](https://github.com/johnneerdael/Backdrop-Launcher/releases/tag/v1.3.5) remains the current Flutter launcher with the first resource-work improvements. It was merged through PR #1, built from source `d0d3750d262dd8fb5f10fb09c01fadf9dc1980a8`, published with Android code 4132, and independently downloaded/verified against all distribution checksums and the production signing certificate. The Compose work is separate.

## Candidate and comparison control

The candidate uses Kotlin/Compose compiler 2.3.21, Compose 1.11.4, TV Material 1.1.0, AGP 8.13.2 and API36. AGP was upgraded only in the standalone benchmark build to support Kotlin 2.3 metadata shrinking. The Views control was rebuilt with the same shrinker. Both release APKs are optimized, non-debuggable, locally development-signed and contain dependency baseline profiles. Packaged profiles do not establish matched AOT compilation or an app-specific generated baseline profile.

The Views and Compose test sandboxes received the identical version-1 scene JSON: the same full-resolution Fight Club backdrop, two fixed app rows with bundled artwork, two local Continue Watching images/progress values, accent and card opacity. Screenshots confirm populated artwork and closely matching geometry. Typography, gear rendering, progress-track styling and toolkit focus implementation differ slightly. Projectivy retained its user configuration, so its measurements are a secondary configured-device comparison, not matched-content framework evidence.

No whole-window or image resolution/color-depth reduction was used. The shared asynchronous hardware decoder retains the same path, size, byte, deadline and cancellation guards. The default 4 MiB reusable artwork budget and bounded worker queue apply to both implementations.

## Recorded short observations

Values below are individual guarded snapshots, not statistical means. CPU is process user+system time from kernel counters; navigation is six alternating Right/Left actions. Actual intervals, process identity, screenshots and raw graphics-cache reports are retained. Do not compare CPU percentages without their elapsed intervals or attribute changes entirely to code without controlling runtime/profile/governor state.

| Observation | Implementation | Graphics MiB | PSS MiB | CPU seconds / elapsed |
| --- | --- | ---: | ---: | --- |
| Initial controlled idle | Views | 5.39 | 45.03 | 0 / 2.27 s |
| Initial controlled idle | Stock TV Material Compose | 5.93 | 63.91 | 0 / 2.31 s |
| First navigation | Stock TV Material Compose | 3.54 | 61.91 | 2.44 / 3.43 s |
| Same navigation pass | Views | 5.38 | 41.20 | 0.25 / 3.26 s |
| Same navigation pass, different content | Projectivy | 27.91 | 111.20 | 0.97 / 3.50 s |
| Warm repeat | Stock TV Material Compose | 3.68 | 62.09 | 1.74 / 3.31 s |
| First lighter-card navigation | Foundation hot-card Compose | 5.91 | 52.13 | 0.86 / 4.58 s |

The graphics counters moved with transient cache retention: the stock Compose run lost its 2 MiB scratch texture between observations; Projectivy retained about 18 MiB of scratch render targets in the navigation pass. This is why one low counter cannot establish the overall resource requirement. Full RSS/Swap denominators remain in raw results and were not dropped.

The original stock TV Material surface used several graphics-layer/clip/alpha paths per card. The lighter candidate keeps TV Material for the other controls, while app cards use Foundation click handling, one nonclipping scale layer and direct rounded fill/stroke. It preserves the 1.06 focus scale, matches Views' 120ms focus timing, and retains remote activation/pressed feedback. The hot-card observation improved sampled CPU time, but the different elapsed/runtime conditions and single observation prevent a universal improvement claim.

## Official skill audit and remaining checks

The requested `jetpack-compose-performance` skill and mandatory references were used for an exhaustive seven-category audit; see `native-benchmark/COMPOSE_PERFORMANCE_AUDIT.md`. The final implementation removes image/layout snapshot feedback, replaces repeated global-position callbacks with reusable visibility/drawing nodes, supplies lazy keys/content types, hoists density, defers animated reads, caches drawing/formatting allocations and preserves asynchronous bounded decoding. No blanket stability annotations, new-version-only APIs or fixture-specific eager app lists were introduced.

The Android CLI and profiler skills were loaded only for the relevant UI/CPU work. CLI 1.1.16717390 is available, and the pinned Perfetto trace processor setup was verified. No useful system trace was obtained: foreground guards rejected recording attempts while the TV was asleep. No profiler session was left active. Source-based findings are not presented as trace-proven CPU causes.

The XML-to-Compose migration skill does not describe the Flutter Home rewrite. The broader Leanback-to-Compose-TV skill offers useful D-pad/focus/restoration reference patterns, although this app does not use Leanback UI components. Its entire migration recipe and heavier card defaults are not automatically applied to a resource-constrained benchmark. The existing native clock/screensaver XML has no demonstrated migration benefit.

The final audited Compose release suite passes 18 tests; lint reports zero errors, release assembly/signature verification pass, and independent source review has no outstanding must-fix findings. Views release tests/lint/build passed in the same build. These checks do not replace the pending on-device cases:

- Populated Crop/Fit/scrim screenshots and lazy image-node exit/reentry/reuse.
- Nonfirst-row, horizontally scrolled app launch/return focus and images.
- Continue Watching cycling/playback return and details/settings return, including rapid pause/resume.
- Repeated warm navigation and idle resource checks in both target orders, plus a short CPU trace and app-specific baseline-profile follow-up if warranted.

**Decision:** keep Kotlin/Compose for TV as an active migration candidate, retain Views as the measured control, and do not promote the candidate to the production launcher until the remaining behavior/resource and full feature/data gates pass. The owner's lower-than-Projectivy requirement remains an acceptance condition.

## Artifacts

Compose APK: `native-benchmark/compose/build/outputs/apk/release/compose-release.apk`, package/component `nl.neerdael.backdrop_launcher.composebenchmark/.ComposeHomeActivity`.

Views control: `native-benchmark/app/build/outputs/apk/release/app-release.apk`, component `nl.neerdael.backdrop_launcher.nativebenchmark/.HomeActivity`.

Private scene backups, raw results, screenshots, rejected attempts and APK-hash identities remain under the ignored local evidence directory. The final audited APK is identified separately from the stock `077309...` and lighter-card `f34bf8...` versions used in the observations above. Do not apply the older observations to the final APK without testing it.
