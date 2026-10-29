# Resource Optimization Implementation Plan

> Use superpowers:executing-plans inline, with one fresh whole-change review at the end.

**Goal:** Reduce fullscreen image allocations, eager work and return-to-Home CPU in Flutter and the native migration candidate, measuring against the owner's Projectivy requirement.

**Status:** First-pass deliverables validated; see `docs/development/resource-optimization-results.md`. The complete-product lower-utilization gate remains open, including production native feature/data migration and equivalent-content acceptance.

**Architecture:** Shared display-size image keys and direct gradients in Flutter; visibility-aware services and bounded artwork preparation. Native retains lightweight scene/views and uses bounded, deduplicated image loading. Device checks run in short bursts.

**Tech stack:** Existing Dart/Flutter, Kotlin/Android Views, Python standard-library capture tools.

**Spec:** `docs/superpowers/specs/2026-10-29-resource-optimization.md`.

## Constraints and review focus

Preserve production data/default Home. Do not equate graphics attribution changes with total-memory savings. Test stale downloads, hidden/resumed services, startup offline artwork, identical image requests, scene changes and offscreen focus restoration. Keep allocation/CPU target status explicit.

### Task 1: Flutter image and lifecycle costs

- [x] Add failing tests for bounded startup preparation and hidden Watch Next work.
- [x] Remove fullscreen hero baking and share image decode keys; retain the existing scrim and transition appearance.
- [x] Bound startup artwork preparation and remove six-item poster prefetch.
- [x] Pause hidden polling/queues, reject stale completions and resume selected/lookahead work.
- [x] Run focused tests, full Flutter tests and analysis using the pinned SDK where available.

### Task 2: Native reuse and image budgets

- [x] Test data dirtiness/visibility and duplicate image requests before implementing reuse.
- [x] Keep native views/data/focus positions across return, refreshing when the scene/packages/provider change.
- [x] Bound and deduplicate image work; reload only attached/visible image targets on return.
- [x] Use short decoder ablations to identify remaining graphics costs and document budgets/quality. Hardware decoding reached the measured gain without reducing image size or color depth.
- [x] Run native tests/lint/release build and short device checks.

### Task 3: Measurement and review

- [x] Add short CPU-time capture where device access supports reliable process counters.
- [x] Record comparative graphics, memory and CPU evidence, mismatches and pass/fail status.
- [x] Obtain independent review, address significant findings and commit the change on the isolated branch.
