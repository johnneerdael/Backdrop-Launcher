# Kotlin/Compose Production Implementation Plan

**Goal:** Deliver and publish a feature-complete native Kotlin/Compose Backdrop release with compatible in-place data and production signing.

**Spec:** `docs/superpowers/specs/2026-10-29-compose-production.md`.

**Execution:** Use the parallel-work skill for independent data, media and UI domains; parent owns build/platform/controller integration, review, device checks and release. All workers use `native-launcher/core/Contracts.kt` and own separate files. No worker publishes or touches production data.

- [x] Bootstrap production module and exact shared interfaces; preserve benchmark controls.
- [x] Data worker: legacy preferences encodings, schema12 SQLite compatibility/backup, installed inventory and full row/app persistence, meaningful fixtures/tests.
- [x] Media worker: Watch Next/cache/filter/playback contracts plus Trakt authorization/refresh/catalogues/TMDB/bundled wallpaper and cache behavior, fixture tests.
- [x] UI worker: complete Compose Home/settings/row/app/artwork/auth/catalogue flows, remote semantics, appearance/status/back options and state restoration.
- [x] Parent: platform app/screen saver/update actions, lifecycle/controller integration, production/benchmark flavors and resources/signing.
- [x] Native tests, DB/device migrations, Compose UI/remote/return checks, short resource captures and independent full-change review.
- [ ] Update capability/build/release docs and native exact-source CI/package provenance; commit/push/merge only verified code.
- [ ] Publish via production signing workflow and verify all release assets, checksums, package/version/code, certificate and source identity.
