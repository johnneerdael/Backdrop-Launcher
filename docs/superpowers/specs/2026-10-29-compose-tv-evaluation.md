# Kotlin and Compose for TV evaluation

Owner direction: migrate toward Jetpack Compose with Kotlin where beneficial, preserving the requirement for lower utilization than Projectivy. Keep the production optimization release separate and moving through its existing signing/publication gates. The Medium article motivates productivity and maintainability; it supplies no Android TV memory benchmark.

## Design

Add a separately identified Compose Home application alongside the existing Kotlin/Views control under the standalone native build. Reuse the scene/model/provider contracts and bounded hardware image-decoding policy. Implement actual Compose UI and focus/navigation, including app rows, the same artwork/backdrop, Continue Watching, clock and benchmark details. Use TV Material components where appropriate and current non-deprecated Foundation lazy lists. Do not replace the whole screen with AndroidView wrappers and call that a pure Compose result.

Keep the candidate's screen/data/assets/configuration comparable with the Views control. Preserve visual/focus behavior and the existing lifecycle policy: no hidden polling/decoding, bounded visible-image loading, preserved scene/focus and no unchanged-return reload. Produce an optimized non-debuggable release APK with default library profiles and development signing; retain full APK identity and document profile/warm-up limitations. Keep Flutter and production data/default Home untouched.

Use compatible stable dependencies for the existing API36/AGP8.12 build, or justify an isolated build upgrade. Compose1.12 requires compileSdk37/AGP9 per official setup documentation; avoid silently suppressing that compatibility requirement. Kotlin and its Compose compiler plugin must match. The existing Kotlin2.3.21 compiler and a compatible stable Compose1.11.x/TV Material1.1.0 are acceptable comparison candidates. Document the versions tested instead of claiming all Compose versions behave identically.

## Acceptance and decision

Unit/build/lint checks pass. Parent runs short same-TV measurements for Views, Compose and Projectivy with controlled scene content and the same six-action workload where possible. Record PSS/RSS/Swap, graphics, process CPU time, frames/return focus and source identity; reject foreground interference. Do not infer memory equivalence from generic scroll-jank benchmarks. Adopt Compose where these resource and behavior checks support it; retain Views on a costly hot path if a hybrid is beneficial. Full production feature/data migration remains a separate capability gate and must never be claimed complete from the comparison app.
