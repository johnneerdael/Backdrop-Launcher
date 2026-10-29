# Native Home Benchmark Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this approved first-stage benchmark inline. Track verification and rulings in the execution ledger.

**Goal:** Deliver an installable, engine-free native Home prototype and repeatable memory comparison with explicit parity limits.

**Status:** Delivered locally on `codex/native-home-benchmark`. Build/lint and 34 tests pass. See `docs/development/native-home-benchmark-results.md` for measurements and explicit device-validation limits; matching-content acceptance and an uncontested offscreen return-focus check remain open migration gates.

**Architecture:** Standalone Android build and package alongside the Flutter product. Versioned scene input separates repeatable visual fixtures from installed-app discovery. Native image loading, RecyclerView rows and a gradient overlay replace the Flutter rendering path.

**Tech Stack:** Kotlin, Android Views, RecyclerView, Android SDK 36, Java 17+, Gradle; Python standard-library benchmark tools.

**Spec:** `docs/superpowers/specs/2026-10-29-native-home-benchmark-design.md`

## Global Constraints

- Keep the production package, data, default Home and Flutter implementation intact.
- Use a separate non-debuggable release APK with no Flutter dependency.
- Preserve supplied artwork licences and upstream copyright.
- Keep device captures, scene exports and private artwork outside version control.
- Distinguish demonstration results from equivalent-content acceptance.

## Review Focus

- Missing or revoked TV-provider permission: show a usable Home and explain unavailable Continue Watching.
- Failed, oversized or changing artwork: bounded decoding, cancellation and no stale image assignment.
- Missing application or invalid launch intent: keep Home usable without crashing.
- Background/return lifecycle: release images safely and restore the scene and focus.
- Invalid memory output or wrong foreground: fail measurement, never report a misleading zero.

### Task 1: Native Home prototype

**Files:** Create `native-benchmark/` build, manifest, Kotlin scene/image/home classes and scene fixture; add build documentation and ignored artifact paths.

**Interfaces:** Read `scene.json` from the benchmark's external-files directory, version 1, containing backdrop, appearance, rows and optional Continue Watching items. Read optional local scene images only below that directory or packaged assets. Produce `app-release.apk` with the separate package ID.

- [x] Add build configuration, wrapper and versioned scene contract.
- [x] Test image sizing and scene validation before implementing the corresponding logic.
- [x] Implement native Home, focus navigation, app launching and Watch Next fallback.
- [x] Build release, run unit checks and lint, inspect APK for Flutter absence.
- [x] Install alongside the existing app and verify remote navigation and lifecycle on the TV.

### Task 2: Repeatable measurement

**Files:** Create `tool/native_benchmark/measure.py`, parser/summary tests and instructions.

**Interfaces:** CLI requires an explicit serial, output directory and package/activity mapping. Parse raw Android meminfo into KiB PSS/RSS/Swap PSS and graphics; produce raw captures and structured JSON with scene/display/build identity.

- [x] Write failing parser tests for real-format output, missing processes/metrics and foreground validation.
- [x] Implement collection and aggregation with bounded command timeouts and restoration in `finally`.
- [x] Run tests and capture settled/native-navigation/background samples where the TV is available.
- [x] Compare results with screenshots and disclose mismatches in a written report.

### Task 3: Review and handoff

- [x] Run focused checks and verify the existing production files are unchanged.
- [x] Obtain a fresh whole-branch review, resolve significant findings and recheck affected behavior.
- [x] Record measured results, known parity gaps and the next migration gate; deliver APK and worktree paths.
