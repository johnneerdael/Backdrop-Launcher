# Releases and GitHub Pages

Backdrop Launcher publishes signed Android APKs from
[`johnneerdael/Backdrop-Launcher`](https://github.com/johnneerdael/Backdrop-Launcher).
The user guide is built with MkDocs and hosted at
[backdrop-launcher.neerdael.nl](https://backdrop-launcher.neerdael.nl/).

## What triggers a release

1. A push to `main` runs **Checks**: Python release/benchmark-tool tests, native
   benchmark validation, and Kotlin/Compose production unit tests, Android lint
   and a GitHub-flavor release compilation.
   The smoke APK uses a disposable signing key and placeholder API credentials;
   it is never uploaded or published. Pull requests run the same checks without
   production secrets.
2. After that push workflow succeeds, **Release** checks the exact full source
   SHA against the current `main` tip, the canonical repository and the latest
   successful `push` run of `.github/workflows/checks.yml`. A newer failed,
   incomplete or rerun check cannot borrow an older success.
3. The gate checks published stable releases and immutable tags. Already released
   commits and stale main commits are skipped. Malformed or conflicting release
   history fails closed. Forks, PR workflows and arbitrary tag pushes cannot ship.
4. A release needs public changes since the last stable release. Prefer a curated
   `docs/releases/<version>.md` entry; otherwise `feat:`, `fix:`, `perf:`,
   `revert:` and `improvement:` commit subjects become linked release bullets.
   Internal `ci`, `build`, `test`, `tests`, `docs` and `release` scopes are omitted.
   Repeated subjects appear once. Documentation and internal work alone do not
   create automatic releases.
5. The exact checked source is built again with the production key. All APKs must
   verify successfully with `apksigner` and the pinned Backdrop signing certificate.
   `aapt2` verifies the package ID, semantic version and Android code against the
   immutable plan; packaging rejects Flutter runtime libraries and assets.
   The publish job checks the source and successful Checks run again, validates
   every artifact checksum, uploads to a draft and publishes after upload succeeds.

For a deliberate maintenance release with no public commit subjects, run
**Release** manually from `main` and provide its full 40-character SHA in `sha`.
The successful push checks and all source/signing gates still apply. Running
**Checks** through `workflow_dispatch` alone does not qualify a commit: use its
successful push run or rerun that existing push run.

Release operations are serialized with the `backdrop-release` concurrency group.
A successful draft upload followed by a publication failure can be retried for the
same main source; only a matching draft with the exact tag and source is reused.
The pipeline never replaces a published release. If `main` has advanced past a
failed draft, inspect that draft and its tag before removing them manually; the
next checked main source may then release. Do not publish a leftover draft by hand
without verifying its complete asset set and source.

## Version and Android build-number policy

`native-launcher/version.properties` supplies the stable semantic version and
minimum Android code. `version=1.4.0` and `code=4133` set the native migration
floors; the historical Flutter pubspec no longer selects production versions.

- If that semantic version is newer than every published stable version, use it.
  Otherwise increment the highest published stable version’s patch component.
  Legacy `1.2.0` and modern `v1.3.0` tags are understood; both spellings for the
  same version are rejected as ambiguous. Prereleases are excluded.
- The Android versionCode is
  `max(native code floor, GITHUB_RUN_NUMBER, highest published manifest code + 1)`.
  With published codes below `4133` and a small run number, the native release
  code is `4133`; its next published release is at least `4134`. A higher base or
  workflow run number raises the floor. Workflow reorganization or a run-number
  reset cannot lower it.
- Every published Backdrop release from `1.3.0` onward must retain its
  `release-manifest.json`. Missing manifests, conflicting versions and duplicate
  published codes stop publication. Legacy Media Launcher releases below `1.3.0`
  may lack this manifest. Android’s maximum accepted code is `2100000000`.

A normal automatic patch release does not need a version-file edit. Raise the
native version for a deliberate minor or major release, and add matching curated
notes. Local builds use `native-launcher/version.properties`; CI passes the
selected `-PreleaseVersion=<version>` and `-PreleaseCode=<code>` explicitly.
Keep release manifests and tags intact, and use this pipeline for every
production release so code history stays complete.

## Assets and verification

Each release contains:

| Asset | Purpose |
| --- | --- |
| `backdrop-launcher.apk` | Stable universal alias for `/releases/latest/download/backdrop-launcher.apk` |
| `backdrop-launcher-<version>.apk` | Versioned native universal APK |
| `backdrop-launcher-<version>-arm64-v8a.apk` | Universal APK under the retained ARM64 compatibility name |
| `backdrop-launcher-<version>-armeabi-v7a.apk` | Universal APK under the retained ARM32 compatibility name |
| `backdrop-launcher-<version>-source.tar.gz` | Corresponding source from the exact checked Git commit |
| `release-manifest.json` | Source, versionCode, workflow runs, APK hashes, signing fingerprint and build configuration |
| `LICENSE`, `CREDITS.md` | License and project/dependency attribution |
| `SHA256SUMS` | Checksums for all other release assets |

The four APK download names contain identical bytes. The ARM aliases preserve
existing links; they are not separately optimized architecture splits. The
manifest records Kotlin, Compose, Compose for TV, AGP, Gradle, Java and compile
SDK versions from the checked native build. It records an architecture-neutral
payload when no native libraries are bundled, or the actual native ABIs for a
universal payload. It does not claim a Flutter SDK built the production APK.

Download all assets into one directory and run `sha256sum --check SHA256SUMS`.
To check an APK signature with Android build tools, run
`apksigner verify --verbose --print-certs backdrop-launcher.apk`. The production
certificate SHA-256 fingerprint is:

```text
18A076C8F5666FB103184CAF1A98C753A044CC54FFC8DDC54CDD13CCC339F93D
```

Release notes use the banner from the exact source commit, include download and
source links, and state that Backdrop Launcher is a separate install from Media
Launcher. The new app does not upgrade an existing Media Launcher installation.

## Repository secrets

Provision these GitHub Actions repository secrets; never commit their values:

| Secret | Used for |
| --- | --- |
| `SIGNING_JKS_FILE_BASE64` | Base64-encoded production JKS |
| `SIGNING_KEYSTORE_PASSWORD` | JKS password |
| `SIGNING_KEY_ALIAS` | Signing alias |
| `SIGNING_KEY_PASSWORD` | Private-key password |
| `TMDB_API_KEY` | Bundled TMDB client configuration |
| `TRAKT_CLIENT_ID` | Bundled Trakt client configuration |

`TRAKT_REDIRECT_URI` is an optional repository variable. Gradle reads provider
configuration directly from these environment variables into native BuildConfig;
no Dart configuration file or Flutter setup is needed. The GitHub production
flavor enables the updater; benchmark builds disable it. The workflow restores
the private JKS to the runner temporary directory, exports `BACKDROP_KEYSTORE_FILE`
and passes the existing `SIGNING_*` secrets to Gradle. The key is removed even
when the build fails. No Firebase configuration or Google Play credentials are
required, and this workflow does not publish to Google Play. Preserve the
signing key securely: changing it prevents in-place Backdrop updates.

**Optional VirusTotal scan** is manual-only. It submits a chosen published stable
release when `VIRUS_TOTAL_API_KEY` is configured and appends direct analysis links
without replacing the branded notes. Without that optional secret it performs no
scan or release edit. The release pipeline does not depend on this service.

## User-guide deployment

**User guide** builds `python -m mkdocs build --strict` for guide pull requests and
main changes. Only the rendered `build/user-guide-site` directory becomes the
Pages artifact. Main uses the official `configure-pages`, `upload-pages-artifact`
and `deploy-pages` actions, with the `github-pages` environment and deployment
permissions restricted to the deploy job. PRs cannot deploy.

Repository setup must select **GitHub Actions** as the Pages source, configure
`backdrop-launcher.neerdael.nl` as the custom domain, and point the domain’s DNS to
the account’s GitHub Pages host. `docs/user-guide/CNAME` preserves the custom
domain in built output. These are repository/domain settings; writing the
workflow does not change them. See
[GitHub’s custom-domain documentation](https://docs.github.com/en/pages/configuring-a-custom-domain-for-your-github-pages-site/about-custom-domains-and-github-pages).

## Local automation checks

```sh
python3 -W error -m unittest discover -s tool/release -p 'test_*.py' -v
actionlint
python3 -m pip install -r docs/site-requirements.txt
python3 -m mkdocs build --strict
```

Action references are pinned to verified release commit SHAs. When updating
an action, verify its release/tag on the action’s own repository and rerun
`actionlint` and these tests.
