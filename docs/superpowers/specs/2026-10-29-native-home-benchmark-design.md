# Native Home memory benchmark

Approved direction: evaluate a staged migration to native Android, starting with a representative Home screen before replacing the shipped Flutter application. Repeat measurements supplied by the owner show approximately 29 MiB lower total memory in Projectivy, with approximately 27 MiB of the difference in graphics (34.1 versus 7.2 MiB). CPU is approximately equal. These are owner-supplied measurements, not a new result from this implementation.

## Approach

Build a standalone Kotlin/Android Views application under `native-benchmark/`, with a separate package (`nl.neerdael.backdrop_launcher.nativebenchmark`) and no Flutter build or runtime dependency. Use RecyclerView for app rows, native focus/scale animation, ImageView for artwork, and a Canvas gradient overlay instead of baking fullscreen scrim textures. Keep the cinematic Home arrangement and Continue Watching. Load bounded, display-sized images off the UI thread, release visual resources when stopped, and reload on return.

Use a versioned, local scene JSON plus optional local artwork for repeatable matching of the visible content. Provide a bundled demonstration scene and installed-app discovery; identify those as demonstrations, never as feature parity. Read the public Watch Next provider where permitted. Never copy credentials, write the existing launcher's database/preferences, or change the default Home app. Importing existing arrangements must be explicit and read-only; a full product migration and production data migration remain a later stage.

## Alternatives considered

- Optimize Flutter alone: potentially recovers extra textures, but leaves the framework rendering floor unresolved. Include a later ablation if needed; do not silently alter the production app for this benchmark.
- Hybrid native Home with Flutter retained: useful for incremental product delivery, but an unsuitable first test of the engine-free footprint.
- Standalone native Home: chosen for the first gate because it gives an engine-free, side-by-side comparison without replacing the current app.

## Measurement and acceptance

Build optimized, non-debuggable release code (a development certificate is acceptable for this separately identified benchmark). Inspect the APK to confirm absence of Flutter libraries/assets. Capture raw `dumpsys meminfo`, graphics/frame information, package/build identity, scene identity, display size/density, and screenshots. Sample settled Home repeatedly, after a navigation sequence, and while another app is foreground. Record PSS, RSS and Swap PSS separately, and reject missing metrics or absent processes instead of treating them as zero. Check foreground state around samples. Restore the prior foreground/default Home after device work.

Report the scope and visual/content mismatches alongside numbers. A demonstration scene can establish feasibility, but cannot prove the complete migration meets the memory target. The migration gate needs equivalent content/settings and preserved navigation/launch behavior, plus a substantial reduction toward Projectivy's footprint without a regression in frame responsiveness. Do not publish or replace the production launcher at this stage.
