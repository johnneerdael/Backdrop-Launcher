# Kotlin and Compose production launcher

This is the production Android TV application. The application ID and Home/Dream
components match existing Backdrop releases, so a production-signed update keeps
the installed launcher configuration. Flutter is not required to build it and no
Flutter runtime is packaged. The previous Dart implementation is retained outside
this directory as a migration reference.

See [build instructions](../docs/development/building.md) and
[guarded releases](../docs/development/releases.md).

## Boundaries

- `data/`: existing Flutter preference encodings and schema12 SQLite storage.
  Before the first database write, retain a consistent, integrity-checked snapshot
  at `app_flutter/native-backup/schema12-before-native.sqlite`. Reject unknown
  future schemas; never reset them. Keep raw hidden memberships during editing.
- `media/`: Trakt device authorization/catalogues, TMDB backdrops, bounded disk
  caches and the public Android TV Watch Next provider. Network/provider calls
  are blocking contracts dispatched by the controller or UI onto workers.
- `core/`: domain contracts, bounded hardware image decoder and request state.
- `ui/`: Compose for TV Home/settings, remote focus memory, lazy artwork nodes,
  visible-only clocks and network callbacks. App cards use the audited lightweight
  Foundation surface; settings use TV Material controls.
- `platform/`: GitHub updates checked against the official manifest, package,
  version and pinned production signing certificate before installer hand-off.
- `MainActivity`: foreground scheduling and platform actions. Pause removes
  timers, cancels pending work and image subscriptions, and rejects late results.
  Package and provider events coalesce; unchanged installed inventory is reused.

The packaged artwork cache is bounded to 4MiB. Full-screen artwork is decoded at
its display size and is not duplicated into a baked scrim bitmap. Only the
selected Continue Watching image and one lookahead are prepared; the provider's
configured item limit does not eagerly load every poster.

## Validation

Run the production unit tests, lint and optimized build from this directory:

```sh
./gradlew :app:testGithubReleaseUnitTest :app:lintGithubRelease :app:assembleGithubRelease
```

For isolated TV checks, use `benchmarkDebug` / `benchmarkRelease`. Their package
is `.nativepreview` (plus `.debug` for debug); their manifest has no Home filter
and their self-updater is disabled. They do not open the installed production
app's private files. Specify the TV serial for every device operation.

`app/src/androidTest` checks D-pad movement, held versus short Select, and settings
navigation during a pending mutation. Unit fixtures exercise legacy databases,
WAL backups, hidden memberships, token encodings, auth/network failures, bounded
artwork, focus restoration and update trust checks.

[Release validation](../docs/development/compose-production-validation.md) records
what was actually checked on the TV and the limits of those checks. Real account
approval and every third-party playback intent require separate acceptance;
protocol fixtures do not establish those live outcomes.
