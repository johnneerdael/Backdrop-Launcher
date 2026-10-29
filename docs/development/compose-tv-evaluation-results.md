# Compose for TV evaluation — 29 October 2026

**Status:** the final audited Kotlin/Compose candidate has passed core device checks and an identical-scene navigation comparison. Views remains the lower-resource control: about one quarter of the sampled navigation CPU time and 15 MiB less PSS in the latest pass. The owner subsequently selected Kotlin + Compose for TV as the preferred product direction for easier UI feature development and alignment with current Android UI tooling. The complete production migration and lower-than-Projectivy acceptance remain open. Earlier asleep/disconnected attempts were rejected and are excluded.

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
| Final audited navigation | Compose | 5.92 | 48.38 | 0.91 / 4.81 s |
| Same final pass | Views | 5.38 | 33.42 | 0.23 / 4.64 s |

The graphics counters moved with transient cache retention: the stock Compose run lost its 2 MiB scratch texture between observations; Projectivy retained about 18 MiB of scratch render targets in the navigation pass. This is why one low counter cannot establish the overall resource requirement. Full RSS/Swap denominators remain in raw results and were not dropped.

The original stock TV Material surface used several graphics-layer/clip/alpha paths per card. The lighter candidate keeps TV Material for the other controls, while app cards use Foundation click handling, one nonclipping scale layer and direct rounded fill/stroke. It preserves the 1.06 focus scale, matches Views' 120ms focus timing, and retains remote activation/pressed feedback. The hot-card observation improved sampled CPU time, but the different elapsed/runtime conditions and single observation prevent a universal improvement claim.

## Official skill audit and remaining checks

The requested `jetpack-compose-performance` skill and mandatory references were used for an exhaustive seven-category audit; see `native-benchmark/COMPOSE_PERFORMANCE_AUDIT.md`. The final implementation removes image/layout snapshot feedback, replaces repeated global-position callbacks with reusable visibility/drawing nodes, supplies lazy keys/content types, hoists density, defers animated reads, caches drawing/formatting allocations and preserves asynchronous bounded decoding. No blanket stability annotations, new-version-only APIs or fixture-specific eager app lists were introduced.

The Android CLI and profiler skills were loaded only for the relevant UI/CPU work. CLI 1.1.16717390 is available, and the pinned Perfetto trace processor setup was verified. The CLI instrumentation server successfully provided focused-element layouts for device checks. The CLI profiler daemon timed out during initialization, with no session left active. The official Perfetto recording helper subsequently produced a five-second trace, but the target package/PID was absent from the scheduling query and navigation overlap was not established. That trace is not used to attribute navigation CPU or claim a root cause. CPU values in this report still come from the guarded kernel process counters. The pinned trace-processor session was stopped after inspection.

The XML-to-Compose migration skill does not describe the Flutter Home rewrite. The broader Leanback-to-Compose-TV skill offers useful D-pad/focus/restoration reference patterns, although this app does not use Leanback UI components. Its entire migration recipe and heavier card defaults are not automatically applied to a resource-constrained benchmark. The existing native clock/screensaver XML has no demonstrated migration benefit.

The final audited Compose release suite passes 18 tests; lint reports zero errors, release assembly/signature verification pass, and independent source review has no outstanding must-fix findings. Views release tests/lint/build passed in the same build. Device checks on the final audited APK now verify:

- Fully populated normal-mode Fit/Crop artwork, including images after lazy-row movement and rapid return.
- Horizontal first-row movement, then second-row selection, launching Play Store and returning to the same actually focused second-row item (CLI focus state and Activity diagnostics agree).
- Continue Watching A→B cycling with the correct Matrix image after return.
- Details dialog initial Close focus, opening TV settings, return to the dialog, Back to Home and rapid pause/resume.
- Throughout this sequence, source version, data-load and scene-render counters remained at one. The Home scene was not reloaded/rebuilt on return.

The final matched pass recorded Compose RSS 131.22 MiB and Swap PSS 0.60 MiB, versus Views RSS 93.68 MiB and Swap PSS 5.11 MiB. Process ages and swapping still differ; the short checks are observations, not a distribution or a complete-feature comparison. No fresh Projectivy observation was substituted into this final matched Views/Compose pass.

Still unverified: a horizontally scrolled second-row launch target, actual Continue Watching playback (the local fixture has no playback intents), fullscreen scrim/hero screenshot equivalence and app-specific generated baseline-profile results. These remain production behavior gates; no full feature/data migration is claimed.

**Resource-only finding:** Views is the measured minimum for this Home workload.

**Product direction, clarified by the owner:** proceed toward Kotlin + Compose for TV because reusable declarative UI, state-driven updates, previews/testing, consistent theming and the modern TV ecosystem support faster feature development. This preference was expressed after reviewing the measured Views/Compose tradeoff. Keep the lean Foundation hot-card path, bounded hardware images and lifecycle controls; retain Views as a benchmark reference. The owner's lower-than-Projectivy requirement still applies to the complete product, and remains a release acceptance gate. Selecting the architecture does not mean the production launcher has already been migrated.

## Artifacts

Compose APK: `native-benchmark/compose/build/outputs/apk/release/compose-release.apk`, package/component `nl.neerdael.backdrop_launcher.composebenchmark/.ComposeHomeActivity`.

Views control: `native-benchmark/app/build/outputs/apk/release/app-release.apk`, component `nl.neerdael.backdrop_launcher.nativebenchmark/.HomeActivity`.

Private scene backups, raw results, screenshots, rejected attempts and APK-hash identities remain under the ignored local evidence directory. The final audited APK is identified separately from the stock `077309...` and lighter-card `f34bf8...` versions used in the observations above. Do not apply the older observations to the final APK without testing it.
