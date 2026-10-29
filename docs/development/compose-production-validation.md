# Kotlin/Compose 1.4.0 validation

Validated on 29 October 2026. Production source is `native-launcher/`; the TV
checks used its isolated preview flavors, not the installed production app's
private data. Projectivy remained the default Home throughout.

## Checks

- 65 production unit tests passed; release lint reported zero errors.
- Optimized production and preview APKs, debug preview and instrumentation APK
  built. APK inspection found no Flutter runtime or Flutter assets. Universal
  APK payload includes ARM32, ARM64, x86 and x86_64 support.
- 22 release-tool tests and workflow syntax checks passed. Release packaging
  verifies package/version/code, production certificate, checksums, exact source
  and the latest successful main-push Checks run.
- Two instrumentation tests passed on the TV: D-pad focus plus held/short Select,
  and a settings Back button disabled during a pending mutation.
- An independent whole-change source review found no unresolved release blockers.

## Legacy upgrade fixture on the TV

Seeded a synthetic Flutter schema12 database and exact legacy preferences into
only the debug preview package, then installed/started the native update. Three
row IDs, names, sort/order/height/hidden values and all five original memberships
survived. Globally hidden membership remained present. Custom rows stayed intact;
newly discovered TV apps were added only to the inventory row. The legacy seconds
launch timestamp, artwork selection, date/time/back options and fake Trakt token
remained unchanged. The pre-native database backup passed integrity verification
and retained the original rows and memberships. The installed production app was
not reset or opened through a debug data bridge.

Unit fixtures separately cover WAL snapshots, migrations, unknown future schema
rejection, AUTOINCREMENT identity, list/double encodings and hidden memberships.
This establishes fixture compatibility; it does not prove every private database
or account configuration.

## Device behavior

The optimized preview rendered real public Watch Next items and progress after
the TV-listings permission was granted and Continue Watching was enabled through
settings. Home artwork, lazy app rows, settings navigation and focus were checked
with the remote and Android CLI layout snapshots. App-card remote input and held
Select behavior also passed instrumentation. The existing native moving-clock
Dream service is retained unchanged.

Live Trakt account approval, every third-party playback target, and production
Android installer consent were not exercised. Their protocols, error paths and
trust boundaries have fixture/source coverage; these remain explicit acceptance
limits rather than claimed live results.

## Short resource observation

Same TCL G17, Android14, 1920x1080 display override, density320. One settled
snapshot per launcher and six alternating Right/Left actions; the native first
app row had focus. Native preview had Continue Watching enabled, real provider
items and no connected Trakt account. Projectivy kept its existing configuration.
Content, process age and residency differ, so these are observations, not matched
feature benchmarks or statistical means.

| Metric | Kotlin/Compose preview | Projectivy |
| --- | ---: | ---: |
| PSS | 75.76 MiB | 92.37 MiB |
| Graphics PSS | 12.98 MiB | 5.27 MiB |
| RSS | 173.26 MiB | 113.38 MiB |
| Swap PSS | 0.32 MiB | 49.35 MiB |
| CPU for six actions | 0.55 s / 4.82 s elapsed | 1.04 s / 4.20 s elapsed |

Total PSS and navigation CPU were lower in this observation. Graphics remained
above Projectivy; do not claim universal resource superiority. The previous
optimized Flutter observation was 36.1MiB graphics, so this production native
scene is substantially smaller, while differing content prevents treating that
as a controlled framework-only percentage. PSS, RSS and swap are distinct and
must not be interchanged.

Two repeat samples after the background/return cycle recorded native PSS
77.01–77.42MiB versus Projectivy86.56–87.95MiB, graphics12.23–12.24MiB versus
5.39–5.41MiB, and CPU0.52–0.54s versus0.85–0.89s for six actions. RSS was
175.83–176.37MiB versus114.51–115.88MiB; swap PSS0.25MiB versus44.92–44.94MiB.
The repeat supports the same narrower conclusion: lower PSS/navigation CPU in
these scenes, with a remaining graphics gap.

A separate background/return observation recorded zero CPU ticks during each
2.26-second idle interval. Graphics was 5.26MiB while hidden and 9.10MiB after
return, with total PSS 68.47MiB and 73.05MiB respectively. These short intervals
support stopped idle work; they do not establish long-run behavior or leak freedom.

Preview APK SHA256:
`4f941ccd1b3e9c3fa1105b3222775f833e4f3ff0cce4012b65851e9ef862acaf`.
Raw captures, screenshots, fixture databases and device identifiers remain local
under the release-validation evidence directory; they are not release assets.
