# Compose for TV Evaluation Plan

**Goal:** Build a representative Kotlin/Compose Home and evaluate whether it meets the resource requirement against the existing Views control and Projectivy.

**Spec:** `docs/superpowers/specs/2026-10-29-compose-tv-evaluation.md`.

**Execution:** One isolated implementation worker under the parallel-work skill while the parent completes the existing release. Parent owns TV interaction, integration, measurements and final review. No change to the release branch and no worker push/publish.

- [x] Add compatible Compose compiler/dependency configuration and separate app identity.
- [x] Implement Compose Home with shared scene/provider/image policy and bounded lifecycle-aware work.
- [x] Cover meaningful focus/state/lifecycle boundaries, build optimized APK and pass lint/tests.
- [ ] Parent reviews and runs short controlled comparisons, rejecting interference and documenting version/profile/content limits.
- [ ] Record the adoption/hybrid decision against measured budgets; keep the production capability gate explicit.

## Implementation rulings

- Use Compose 1.11.4 and TV Material 1.1.0 with the matching Kotlin/Compose compiler 2.3.21. Keep API36 and the existing wrapper. Upgrade only the standalone native build to AGP8.13.2 because the initial AGP8.12 optimized build emitted Kotlin-metadata warnings; official AGP8.13.2 release notes identify R8 8.13.19 as the Kotlin2.3 support fix. Rebuild both controls with that same toolchain.
- Share scene/data/source/state files through explicit generated source inputs and extract the existing decoder policy unchanged. Keep renderer-specific lifecycle and target binding separate; do not link Views UI into Compose.
- Parent retains exclusive TV interaction, final branch review and all adoption decisions. Local tests/build/lint establish artifact readiness, not measured utilization or production parity.

## Local validation

- Both modules: release unit tests, release lint and optimized release assembly pass with AGP8.13.2; 11 Views tests and 15 Compose tests pass. The newly introduced selection/disposal assertions were observed failing before their implementation.
- Lint: no errors; 8 Views and 12 Compose advisory warnings (target/API/manifest conventions, available upgrades and KTX suggestions). No Kotlin metadata shrinker warnings remain.
- APK inspection: Compose package/activity identity and API levels match configuration, no debuggable manifest attribute, apksigner verification succeeds with the development certificate, and both APKs package dependency baseline profiles.
- TV testing and adoption remain unchecked above; parent owns source/scene/APK hashing, focus validation and short controlled measurements.

## Independent implementation review

A fresh reviewer reported no must-fix findings after checking decoder parity, pause/resume subscription renewal, disposal/stale completions and source refresh versioning. One nonblocking overload limitation is documented: rejected jobs from a full 32-slot queue do not retry until subscription changes. Fixed-content comparison must confirm every visible image loaded; broader production scene support needs a retry/cancellation policy. Parent still owns final integration review and TV acceptance, especially launch/return from a horizontally scrolled tile in a nonfirst row, carousel/backdrop replacement and details/settings return focus.

## Parent-directed hot-card follow-up

Replace only app-card TV Material surfaces after parent-observed navigation CPU/cache differences prompted source investigation. TV Material1.1 source installs scale, rounded clip/alpha and nested content-alpha layers. Use Foundation clickable with one transform-only layer, direct rounded fill/stroke, unchanged artwork bounds/opacity/resolution, and no redundant clip because artwork is inset inside the corners. Match the Views120ms accelerate/decelerate focus transition and1.06 scale, with immediate z-order; retain remote release activation and pressed feedback. Keep TV Material elsewhere.

Preserve the original077309 APK by hash before rebuilding. Candidate Compose release tests (15), lint (zero errors) and optimized assembly pass. A fresh reviewer checked final source against the exact TV Material/Foundation source versions and reported no substantive findings. Source review does not establish GPU/CPU savings; parent owns repeat warm navigation, screenshots and memory/cache sampling.
