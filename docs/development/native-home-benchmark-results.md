# Native Home feasibility results — 29 October 2026

The standalone Kotlin/Android Views prototype builds, runs alongside the production launcher, and contains no Flutter libraries or assets. The first full comparison shows substantially lower graphics allocation than Backdrop, while remaining above Projectivy. This supports continuing the native evaluation; it does **not** establish equivalent-content or production feature parity.

## Measurements

The first completed run captured 36 samples: three launchers × four states × three samples, with five seconds to settle and two seconds between samples. Each state retained raw `dumpsys meminfo -a`, screenshots and frame reports. All package memory reports contained exactly one process, and each app retained its PID throughout that run. Display rendering was 1920×1080 at density 320; the physical panel is 3840×2160.

Graphics allocation, mean MiB:

| Application | Settled Home | After navigation | Background | Returned Home |
| --- | ---: | ---: | ---: | ---: |
| Production Backdrop 1.3.4 | 33.50 | 51.79 | 32.45 | 34.73 |
| Initial native prototype | 16.28 | 16.29 | 16.06 | 16.29 |
| Installed Projectivy | 6.96 | 7.01 | 5.11 | 7.01 |

Settled Home memory, mean MiB; keep the accounting categories separate:

| Application | PSS | RSS | Swap PSS |
| --- | ---: | ---: | ---: |
| Production Backdrop | 109.79 | 136.49 | 33.36 |
| Initial native prototype | 78.70 | 158.38 | 0.46 |
| Installed Projectivy | 90.86 | 115.97 | 43.83 |

The native process was newly installed, whereas the existing applications had substantial swapped memory. Its PSS also varied with allocation/collection: settled samples ranged from 66.57 to 102.89 MiB. These differences in process history prevent treating the PSS means as a controlled framework comparison. No processes were force-stopped and no existing app data was cleared.

A reverse-order run was interrupted when a competing app took the foreground before the native samples. Its `complete` field is false, and its partial results are excluded from the comparison above. The collector rejects unexpected foreground windows, missing metrics and multiple-process output instead of reporting misleading zeros or partial totals.

After review fixes, a short snapshot of the **final APK** recorded 16.23 MiB graphics, 89.08 MiB PSS, 183.69 MiB RSS and 0.68 MiB Swap PSS. This is one observation, not a stable mean. The original foreground activity was restored and verified, and default Home resolution was unchanged. Subsequent captures can select just one state with `--state settled --samples 1 --settle 2 --interval 1` instead of running the full sequence.

## Scope and interpretation

The native demonstration uses the bundled Fight Club backdrop, an alphabetically discovered app row and live public Watch Next. The production launchers retained their own wallpaper, app rows, artwork selections and Watch Next filtering. Screenshots show these content differences. The initial native run also displayed app labels; the final default hides them. The final APK includes review fixes for image snapshots/cancellation, hero selection and focus restoration.

The roughly halved graphics allocation is a feasibility signal. It does not prove that Flutter alone causes the entire gap, nor that this native implementation already matches Projectivy's footprint. Its background graphics allocation also remained near 16 MiB despite dropping view/image references; releasing references is not proof that Android immediately returns graphics allocations.

CPU snapshots and Android frame reports are retained for diagnostics. HWUI counters do not capture Flutter rendering equivalently, so no cross-framework CPU or FPS improvement is claimed.

## Validation and remaining gate

- Optimized, non-debuggable release build and Android lint pass (zero lint errors; eight compatibility/dependency warnings).
- Seven native unit tests, seven benchmark-tool tests and 20 existing release-tool tests pass.
- Independent review found no remaining significant source findings after fixes.
- Final APK was installed and captured on the TV; public Watch Next populated successfully after granting the benchmark its TV-listings permission.
- A local fixture visually confirmed the fullscreen hero uses the selected image and cinematic scrim. D-pad navigation reached the ninth tile of a second row, and selecting it opened the production Backdrop app.
- The offscreen return-focus smoke check was **inconclusive**: another package was foreground by the time the UI hierarchy was captured. It is not recorded as a passing return-focus test. Row/tile identity restoration, moved/removed app handling, hero fallback and cancellation have unit/source coverage, but those remaining device paths need an uncontested short check.
- Temporary fixture files were removed and the original foreground was restored. The primary checkout and production launcher source/data/default Home were preserved.
- CI coverage was added for the standalone build and Python parser tests; remote CI has not been run for this local branch.

Before expanding into a full product migration, supply an explicit scene matching the production wallpaper, row order, app artwork, labels and Continue Watching selection/filtering. Verify short remote-navigation/launch/return checks, then repeat bounded captures. Settings editing, row management, Trakt catalogue/login flows, updating, complete localization/accessibility and production data migration remain outside this first-stage prototype.

## Artifacts

Source and build instructions: `native-benchmark/README.md`.

Final APK: `native-benchmark/app/build/outputs/apk/release/app-release.apk` (11,672,573 bytes; locally development-signed).

Final APK SHA-256: `3efd0258f58f68efd7898dc7caf05093c84c7a1b855a3c5a2163f20017a1e18f`.

The first comparison used initial native APK SHA-256 `6660e9f3fdf4acb0cfaf0ac341213615eb51dccd875afffbbf038aa1ff7d994c`; its results are kept distinct from the final-artifact snapshot.

Private raw evidence is retained locally under the ignored `native-benchmark/evidence/2026-10-29/`: `comparison-01/`, rejected `comparison-02/`, `final-native-short/`, and the navigation fixture/script/results/screenshot. Do not publish these raw captures without reviewing their device and app-content metadata.
