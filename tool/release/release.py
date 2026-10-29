#!/usr/bin/env python3
"""Validate, package and publish releases from an exactly checked main commit.

Only the ``publish`` command writes to GitHub. Other commands are read-only there.
All subprocess arguments are passed directly, without a shell.
"""
from __future__ import annotations

import argparse
import hashlib
import html
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile
from typing import Any

REPOSITORY = 'johnneerdael/Backdrop-Launcher'
ABIS = ('universal', 'arm64-v8a', 'armeabi-v7a')
APPLICATION_ID = 'nl.neerdael.backdrop_launcher'
MAX_VERSION_CODE = 2_100_000_000
# Public fingerprint of the production key; changing this requires an explicit signing migration.
SIGNING_CERTIFICATE_SHA256 = '18a076c8f5666fb103184caf1a98c753a044cc54ffc8ddc54cdd13ccc339f93d'
VERSION_RE = r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)'
SHA_RE = re.compile(r'[0-9a-f]{40}')
INTERNAL_SCOPES = {'ci', 'build', 'test', 'tests', 'docs', 'release'}


def version_tuple(value: str, *, tag: bool = False) -> tuple[int, int, int]:
    match = re.fullmatch(('v?' if tag else '') + VERSION_RE, value) if isinstance(value, str) else None
    if not match:
        raise ValueError(f'Invalid stable version: {value!r}')
    return tuple(map(int, match.groups()))


def stable_tags(tags: list[str]) -> dict[tuple[int, int, int], str]:
    result = {}
    for tag in tags:
        parsed = version_tuple(tag, tag=True)
        if parsed in result:
            raise ValueError(f'duplicate stable version tags: {result[parsed]} and {tag}')
        result[parsed] = tag
    return result


def select_version(base: str, tags: list[str]) -> str:
    floor = version_tuple(base)
    versions = stable_tags(tags)
    latest = max(versions, default=(-1, -1, -1))
    selected = floor if floor > latest else (latest[0], latest[1], latest[2] + 1)
    return '.'.join(map(str, selected))


def parse_version_properties(content: str) -> tuple[str, int]:
    values = {}
    for line in content.splitlines():
        if not line.strip() or line.lstrip().startswith(('#', '!')):
            continue
        match = re.fullmatch(r'\s*(version|code)\s*=\s*([^\s]+)\s*', line)
        if not match or match[1] in values:
            raise ValueError('native-launcher/version.properties must contain one version and code')
        values[match[1]] = match[2]
    if set(values) != {'version', 'code'}:
        raise ValueError('Native version and Android code are required')
    version_tuple(values['version'])
    if not re.fullmatch(r'[1-9][0-9]*', values['code']):
        raise ValueError('Native Android code must be a positive integer')
    code = int(values['code'])
    select_build_number(code, 1, [])
    return values['version'], code


def native_toolchain(root: Path) -> dict:
    """Record the actual checked Gradle declarations, never an obsolete Flutter SDK."""
    native = root / 'native-launcher'
    build = (native / 'build.gradle').read_text()
    app = (native / 'app/build.gradle').read_text()
    wrapper = (native / 'gradle/wrapper/gradle-wrapper.properties').read_text()

    def one(pattern: str, source: str, field: str) -> str:
        matches = re.findall(pattern, source)
        if len(matches) != 1:
            raise ValueError(f'Expected one checked native toolchain declaration: {field}')
        return matches[0]

    return {
        'kotlin': one(r"id ['\"]org\.jetbrains\.kotlin\.android['\"] version ['\"]([^'\"]+)['\"]", build, 'kotlin'),
        'compose': one(r"implementation ['\"]androidx\.compose\.ui:ui:([^'\"]+)['\"]", app, 'compose'),
        'tv_material': one(r"implementation ['\"]androidx\.tv:tv-material:([^'\"]+)['\"]", app, 'tv_material'),
        'android_gradle_plugin': one(r"id ['\"]com\.android\.application['\"] version ['\"]([^'\"]+)['\"]", build, 'android_gradle_plugin'),
        'gradle': one(r'distributionUrl=.*?/gradle-([0-9.]+)-(?:all|bin)\.zip', wrapper, 'gradle'),
        'compile_sdk': int(one(r'\bcompileSdk\s+([0-9]+)', app, 'compile_sdk')),
        'java': '17',
    }


def select_build_number(base: int, run_number: int, published_codes: list[int]) -> int:
    values = [base, run_number, *published_codes]
    if any(type(value) is not int or not 0 < value <= MAX_VERSION_CODE for value in values):
        raise ValueError('Android codes and workflow run numbers must be positive bounded integers')
    if len(published_codes) != len(set(published_codes)):
        raise ValueError('duplicate published Android versionCode')
    selected = max(base, run_number, max(published_codes, default=0) + 1)
    if selected > MAX_VERSION_CODE:
        raise ValueError('Android versionCode exhausted')
    return selected


def require_source(sha: str, main_sha: str, published_shas: list[str], repository: str) -> None:
    if repository != REPOSITORY:
        raise ValueError(f'Releases are limited to {REPOSITORY}')
    if not SHA_RE.fullmatch(sha) or sha != main_sha:
        raise NotReady('Release source must be the exact full SHA at the current main tip')
    if sha in published_shas:
        raise NotReady('This source commit has already been released')


def require_checks(runs: list[dict], sha: str, repository: str,
                   expected_run_id: int | None = None) -> dict:
    eligible = [run for run in runs if (
        run.get('head_sha') == sha and run.get('event') == 'push'
        and run.get('head_branch') == 'main'
        and (run.get('head_repository') or {}).get('full_name') == repository
        and run.get('path') == '.github/workflows/checks.yml'
    )]
    if not eligible:
        raise ValueError('No Checks push run for this exact main commit and repository')
    latest = max(eligible, key=lambda run: (int(run['run_number']), int(run['run_attempt'])))
    if latest.get('status') != 'completed' or latest.get('conclusion') != 'success':
        raise ValueError('The latest Checks run for this commit must have succeeded')
    if expected_run_id is not None and latest['id'] != expected_run_id:
        raise ValueError('The triggering Checks run is stale')
    return latest


def public_notes(commits: list[tuple[str, str]], repository: str) -> str:
    if repository != REPOSITORY:
        raise ValueError('Invalid notes repository')
    notes, seen = [], set()
    for sha, subject in commits:
        match = re.fullmatch(r'(feat|fix|perf|revert|improvement)(?:\(([^)]+)\))?!?:\s+(.+)', subject)
        if not match or match[2] in INTERNAL_SCOPES or subject.casefold() in seen:
            continue
        if not SHA_RE.fullmatch(sha):
            raise ValueError('Invalid source SHA in notes')
        seen.add(subject.casefold())
        text = html.escape(match[3], quote=False)
        text = re.sub(r'([\\`*\[\]_])', r'\\\1', text)
        notes.append(f'- {text} ([{sha[:7]}](https://github.com/{repository}/commit/{sha}))')
    return '\n'.join(notes)


def render_notes(repository: str, sha: str, version: str, code: int,
                 changes: str, previous_tag: str | None) -> str:
    if repository != REPOSITORY or not SHA_RE.fullmatch(sha):
        raise ValueError('Invalid immutable notes source')
    version_tuple(version)
    select_build_number(code, 1, [])
    if not changes.strip() or re.search(r'\b(TODO|TBD|PLACEHOLDER)\b', changes):
        raise ValueError('Release notes must contain finished public changes')
    if previous_tag:
        version_tuple(previous_tag, tag=True)
    base = f'https://github.com/{repository}'
    download = f'{base}/releases/download/v{version}'
    history = f'{base}/compare/{previous_tag}...{sha}' if previous_tag else f'{base}/commits/{sha}'
    return f'''![Backdrop Launcher]({base}/raw/{sha}/docs/assets/brand/release-banner.png)

# Backdrop Launcher {version}

**Backdrop Launcher — Your TV. Your way.**

[User guide](https://backdrop-launcher.neerdael.nl/) · [All releases]({base}/releases)

## What’s new

{changes.strip()}

## Downloads

| Package | Download | Use it for |
| --- | --- | --- |
| Universal APK | [backdrop-launcher-{version}.apk]({download}/backdrop-launcher-{version}.apk) | Recommended native Kotlin/Compose launcher |
| ARM64 compatibility alias | [backdrop-launcher-{version}-arm64-v8a.apk]({download}/backdrop-launcher-{version}-arm64-v8a.apk) | Identical universal APK; retained download name |
| ARM32 compatibility alias | [backdrop-launcher-{version}-armeabi-v7a.apk]({download}/backdrop-launcher-{version}-armeabi-v7a.apk) | Identical universal APK; retained download name |
| Stable download link | [backdrop-launcher.apk]({base}/releases/latest/download/backdrop-launcher.apk) | Always the latest universal APK |

Backdrop Launcher is a **separate install from Media Launcher**. Set it as your TV’s home app after installation. See the [installation guide](https://backdrop-launcher.neerdael.nl/get-started/install/).

## Source and verification

- Android version: **{version} ({code})**. APKs are release-signed with the Backdrop Launcher signing key.
- [SHA256SUMS]({download}/SHA256SUMS) covers all release assets. Download the assets together and run `sha256sum --check SHA256SUMS`.
- [Release manifest]({download}/release-manifest.json) records the exact source, successful Checks run, Android versionCode, APK checksums and signing certificate SHA-256.
- [Corresponding source archive]({download}/backdrop-launcher-{version}-source.tar.gz) · [Source commit `{sha[:12]}`]({base}/tree/{sha}) · [Full changelog]({history}).
- [GPL-3.0 license]({download}/LICENSE) · [Credits]({download}/CREDITS.md). Native Kotlin/Compose implementation based on FLauncher by Étienne Fesser and the Media Launcher community. The source archive retains historical contributions and dependency attribution.
'''


def unique_object(pairs: list[tuple[str, Any]]) -> dict:
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f'duplicate JSON field: {key}')
        result[key] = value
    return result


def read_manifest(content: str, tag: str) -> dict:
    manifest = json.loads(content, object_pairs_hook=unique_object)
    if not isinstance(manifest, dict) or type(manifest.get('schema')) is not int or manifest['schema'] != 1:
        raise ValueError('Unsupported release manifest schema')
    if manifest.get('repository') != REPOSITORY:
        raise ValueError('Invalid release manifest repository')
    if version_tuple(manifest.get('version', '')) != version_tuple(tag, tag=True):
        raise ValueError('Release manifest version differs from its release tag')
    if not isinstance(manifest.get('source_sha'), str) or not SHA_RE.fullmatch(manifest['source_sha']):
        raise ValueError('Invalid release manifest source SHA')
    select_build_number(manifest.get('android_version_code'), 1, [])
    return manifest


def find_apks(root: Path) -> dict[str, Path]:
    candidates = list(root.rglob('app-github-release.apk'))
    if len(candidates) != 1 or not candidates[0].is_file() or candidates[0].stat().st_size == 0:
        raise ValueError(f'Expected exactly one nonempty native GitHub release APK under {root}')
    # Preserve public updater/download names without claiming these are architecture splits.
    return {abi: candidates[0] for abi in ABIS}


def native_payload(path: Path) -> list[str]:
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if 'AndroidManifest.xml' not in names or 'classes.dex' not in names:
            raise ValueError('Native APK lacks its manifest or application code')
        if any(name.startswith('assets/flutter_assets/') or name.endswith(('/libflutter.so', '/libapp.so')) for name in names):
            raise ValueError('Production native APK must not contain the Flutter runtime or assets')
        architectures = sorted({name.split('/')[1] for name in names if re.fullmatch(r'lib/[^/]+/[^/]+\.so', name)})
        if architectures and not {'armeabi-v7a', 'arm64-v8a'}.issubset(architectures):
            raise ValueError('Universal APK must retain both advertised ARM architectures')
        return architectures


def verify_apk_identity(apks: dict[str, Path], plan: dict, aapt2: str) -> list[str]:
    architectures = set()
    for path in set(apks.values()):
        report = command([aapt2, 'dump', 'badging', str(path)])
        packages = re.findall(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", report, re.MULTILINE)
        if packages != [(APPLICATION_ID, str(plan['android_version_code']), plan['version'])]:
            raise ValueError('APK package/version/code differs from the production release plan')
        architectures.update(native_payload(path))
    return sorted(architectures)


class NotReady(ValueError):
    """A valid event that should skip publication without failing the workflow."""


def command(args: list[str], *, root: Path | None = None, binary: bool = False) -> Any:
    return subprocess.check_output(args, cwd=root, text=not binary)


class Github:
    def get(self, endpoint: str) -> Any:
        return json.loads(command(['gh', 'api', endpoint]), object_pairs_hook=unique_object)

    def pages(self, endpoint: str, key: str | None = None) -> list:
        pages = json.loads(command(['gh', 'api', '--paginate', '--slurp', endpoint]))
        return [item for page in pages for item in (page[key] if key else page)]

    def raw(self, endpoint: str) -> str:
        return command(['gh', 'api', '-H', 'Accept: application/octet-stream', endpoint])


def git(*args: str, root: Path = Path('.')) -> str:
    return command(['git', *args], root=root).strip()


def source_at_tag(tag: str, root: Path) -> str:
    version_tuple(tag, tag=True)
    sha = git('rev-parse', f'refs/tags/{tag}^{{commit}}', root=root)
    if not SHA_RE.fullmatch(sha):
        raise ValueError(f'Cannot resolve immutable release tag {tag}')
    return sha


def has_tag(tag: str, root: Path) -> bool:
    return subprocess.run(['git', 'show-ref', '--verify', '--quiet', f'refs/tags/{tag}'], cwd=root).returncode == 0


def release_history(api: Github, repository: str, root: Path) -> tuple[list[dict], list[dict], list[dict]]:
    all_releases = api.pages(f'repos/{repository}/releases?per_page=100')
    published, manifests = [], []
    for entry in all_releases:
        if entry['draft'] or entry['prerelease'] or not re.fullmatch('v?' + VERSION_RE, entry['tag_name']):
            continue
        published.append(entry)
    stable_tags([entry['tag_name'] for entry in published])
    for entry in published:
        tag = entry['tag_name']
        entry['source_sha'] = source_at_tag(tag, root)
        assets = [asset for asset in entry['assets'] if asset['name'] == 'release-manifest.json']
        if len(assets) > 1:
            raise ValueError(f'duplicate manifests in release {tag}')
        if assets:
            manifest = read_manifest(api.raw(f'repos/{repository}/releases/assets/{assets[0]["id"]}'), tag)
            if manifest['source_sha'] != entry['source_sha']:
                raise ValueError(f'Manifest source differs from tag {tag}')
            manifests.append(manifest)
        elif version_tuple(tag, tag=True) >= (1, 3, 0):
            raise ValueError(f'Backdrop release {tag} is missing its versionCode manifest')
    return published, manifests, all_releases


def make_plan(repository: str, sha: str, run_number: int, run_id: int, *,
              root: Path = Path('.'), api: Github | None = None,
              expected_run_id: int | None = None, force: bool = False) -> dict:
    api = api or Github()
    if repository != REPOSITORY or not SHA_RE.fullmatch(sha):
        raise ValueError('Invalid release repository or full source SHA')
    main_sha = api.get(f'repos/{repository}/git/ref/heads/main')['object']['sha']
    require_source(sha, main_sha, [], repository)
    if git('rev-parse', 'HEAD', root=root) != sha:
        raise ValueError('Checkout differs from the proposed release source')
    runs = api.pages(f'repos/{repository}/actions/workflows/checks.yml/runs?head_sha={sha}&event=push&per_page=100', 'workflow_runs')
    checks = require_checks(runs, sha, repository, expected_run_id)
    published, manifests, all_releases = release_history(api, repository, root)
    require_source(sha, main_sha, [entry['source_sha'] for entry in published], repository)
    base, code_floor = parse_version_properties((root / 'native-launcher/version.properties').read_text())
    version = select_version(base, [entry['tag_name'] for entry in published])
    code = select_build_number(code_floor, run_number, [entry['android_version_code'] for entry in manifests])
    tag = f'v{version}'
    drafts = [entry for entry in all_releases if entry['tag_name'] in (tag, version)]
    if drafts and (len(drafts) != 1 or not drafts[0]['draft'] or drafts[0]['tag_name'] != tag or drafts[0]['target_commitish'] != sha):
        raise ValueError('Proposed tag conflicts with an existing release or unrelated draft')
    if has_tag(tag, root) and (not drafts or source_at_tag(tag, root) != sha):
        raise ValueError('Proposed release tag already exists without a matching recoverable draft')
    previous = max(published, key=lambda entry: version_tuple(entry['tag_name'], tag=True), default=None)
    previous_tag = previous['tag_name'] if previous else None
    previous_sha = previous['source_sha'] if previous else None
    if previous_sha and subprocess.run(['git', 'merge-base', '--is-ancestor', previous_sha, sha], cwd=root).returncode:
        raise ValueError('Previous release is not an ancestor of this main source')
    revision_range = f'{previous_sha}..{sha}' if previous_sha else sha
    raw = git('log', '--no-merges', '--format=%H%x09%s', revision_range, root=root)
    commits = [tuple(line.split('\t', 1)) for line in raw.splitlines() if line]
    changes = public_notes(commits, repository)
    curated_path = f'docs/releases/{version}.md'
    curated = root / curated_path
    if curated.is_file():
        changed = not previous_sha or bool(git('diff', '--name-only', previous_sha, sha, '--', curated_path, root=root))
        if changed:
            changes = curated.read_text()
    if not changes.strip():
        if not force:
            raise NotReady('No public release notes since the previous release')
        changes = '- Maintenance update from the verified main source.'
    notes = render_notes(repository, sha, version, code, changes, previous_tag)
    notes += f'\nVerified by [Checks run {checks["id"]}, attempt {checks["run_attempt"]}](https://github.com/{repository}/actions/runs/{checks["id"]}).\n'
    return {
        'schema': 1, 'repository': repository, 'source_sha': sha, 'version': version,
        'tag': tag, 'android_version_code': code, 'previous_tag': previous_tag,
        'checks_run_id': checks['id'], 'checks_run_attempt': checks['run_attempt'],
        'release_run_id': run_id, 'release_run_number': run_number, 'notes': notes,
        'build_toolchain': native_toolchain(root),
    }


def read_plan(path: Path) -> dict:
    raw = path.read_text()
    data = json.loads(raw, object_pairs_hook=unique_object)
    plan = read_manifest(raw, data.get('tag', ''))
    if plan.get('tag') != 'v' + plan['version']:
        raise ValueError('Invalid release plan tag')
    for field in ('checks_run_id', 'checks_run_attempt', 'release_run_id'):
        if type(plan.get(field)) is not int or plan[field] <= 0:
            raise ValueError(f'Invalid release plan {field}')
    if not isinstance(plan.get('notes'), str) or not plan['notes'].strip():
        raise ValueError('Release plan lacks public notes')
    return plan


def require_certificates(certificates: dict[str, str]) -> str:
    if set(certificates) != set(ABIS) or len(set(certificates.values())) != 1:
        raise ValueError('All three APKs must use the same signing certificate')
    certificate = certificates['universal']
    if certificate != SIGNING_CERTIFICATE_SHA256:
        raise ValueError('APK signing certificate differs from the production Backdrop Launcher key')
    return certificate


def verify_apk_signatures(apks: dict[str, Path], apksigner: str) -> dict[str, str]:
    certificates = {}
    verified = {}
    for abi, path in apks.items():
        if path not in verified:
            report = command([apksigner, 'verify', '--verbose', '--print-certs', str(path)])
            fingerprints = re.findall(r'^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$', report, re.MULTILINE)
            if len(fingerprints) != 1:
                raise ValueError(f'{abi} APK must have exactly one verified signer')
            verified[path] = fingerprints[0].lower()
        certificates[abi] = verified[path]
    require_certificates(certificates)
    return certificates


def package_assets(plan: dict, apk_dir: Path, output: Path, root: Path,
                   certificates: dict[str, str]) -> None:
    certificate = require_certificates(certificates)
    if git('rev-parse', 'HEAD', root=root) != plan['source_sha']:
        raise ValueError('Package checkout differs from the checked source')
    if output.exists() and any(output.iterdir()):
        raise ValueError('Release output directory must be empty')
    output.mkdir(parents=True, exist_ok=True)
    apks = find_apks(apk_dir)
    toolchain = native_toolchain(root)
    if plan.get('build_toolchain') != toolchain:
        raise ValueError('Native build toolchain differs from the checked release plan')
    architectures = native_payload(apks['universal'])
    version = plan['version']
    version_tuple(version)
    names = {
        'universal': f'backdrop-launcher-{version}.apk',
        'arm64-v8a': f'backdrop-launcher-{version}-arm64-v8a.apk',
        'armeabi-v7a': f'backdrop-launcher-{version}-armeabi-v7a.apk',
    }
    for abi, path in apks.items():
        shutil.copyfile(path, output / names[abi])
    shutil.copyfile(apks['universal'], output / 'backdrop-launcher.apk')
    source = output / f'backdrop-launcher-{version}-source.tar.gz'
    with source.open('wb') as stream:
        subprocess.run(['git', 'archive', '--format=tar.gz', f'--prefix=backdrop-launcher-{version}/', plan['source_sha']], cwd=root, stdout=stream, check=True)
    shutil.copyfile(root / 'LICENSE', output / 'LICENSE')
    (output / 'CREDITS.md').write_text('''# Backdrop Launcher credits

Backdrop Launcher is developed by John Neerdael and contributors.

- FLauncher by Étienne Fesser: https://gitlab.com/flauncher/flauncher
- Arc/Media Launcher by Badis Meddouri and contributors: https://github.com/meddouribadis/arclauncher
- LTvLauncher © 2026 LeanBitLab; retained source copyright headers identify upstream contributions.
- Kotlin: https://kotlinlang.org/
- Android Jetpack Compose and Compose for TV: https://developer.android.com/compose

The corresponding source archive contains copyright headers, the GPL-3.0 license,
Native dependency declarations are in native-launcher/app/build.gradle, with the
Kotlin/AGP versions and Gradle wrapper pinned alongside it. Historical Flutter/Dart
source and dependency attribution remain in the source archive for provenance;
the production APK does not embed the Flutter runtime.
TMDB and Trakt supply metadata and imagery; their inclusion does not imply endorsement.
''')
    apk_info = {
        path.name: {'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'size_bytes': path.stat().st_size}
        for path in sorted(output.glob('*.apk'))
    }
    manifest = {key: value for key, value in plan.items() if key != 'notes'}
    manifest.update({'signing_certificate_sha256': certificate, 'apks': apk_info,
                     'implementation': 'kotlin-compose', 'application_id': APPLICATION_ID,
                     'build_toolchain': toolchain, 'flavor': 'github', 'build_variant': 'githubRelease',
                     'apk_distribution': {'architecture': 'architecture-neutral' if not architectures else 'universal',
                                          'native_abis': architectures, 'identical_aliases': True},
                     'self_updater_enabled': True})
    (output / 'release-manifest.json').write_text(json.dumps(manifest, indent=2, sort_keys=True) + '\n')
    hashes = [f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}' for path in sorted(output.iterdir())]
    (output / 'SHA256SUMS').write_text('\n'.join(hashes) + '\n')


def verify_distribution(plan: dict, output: Path) -> list[Path]:
    version = plan['version']
    expected = {
        'backdrop-launcher.apk', f'backdrop-launcher-{version}.apk',
        f'backdrop-launcher-{version}-arm64-v8a.apk', f'backdrop-launcher-{version}-armeabi-v7a.apk',
        f'backdrop-launcher-{version}-source.tar.gz', 'LICENSE', 'CREDITS.md',
        'release-manifest.json', 'SHA256SUMS',
    }
    if {path.name for path in output.iterdir()} != expected:
        raise ValueError('Distribution has missing or unexpected release files')
    entries = {}
    for line in (output / 'SHA256SUMS').read_text().splitlines():
        match = re.fullmatch(r'([0-9a-f]{64})  ([A-Za-z0-9_.-]+)', line)
        if not match or match[2] in entries:
            raise ValueError('Malformed or duplicate checksum entries')
        entries[match[2]] = match[1]
    if set(entries) != expected - {'SHA256SUMS'}:
        raise ValueError('Checksums do not cover the exact distribution')
    for name, digest in entries.items():
        if hashlib.sha256((output / name).read_bytes()).hexdigest() != digest:
            raise ValueError(f'Checksum differs for {name}')
    manifest = read_manifest((output / 'release-manifest.json').read_text(), plan['tag'])
    for field in ('source_sha', 'android_version_code', 'checks_run_id', 'checks_run_attempt', 'release_run_id'):
        if manifest.get(field) != plan.get(field):
            raise ValueError(f'Packaged manifest differs from the release plan: {field}')
    expected_apks = {name for name in expected if name.endswith('.apk')}
    if set(manifest.get('apks', {})) != expected_apks:
        raise ValueError('Release manifest APK metadata differs from the distribution')
    for name in expected_apks:
        metadata = manifest['apks'][name]
        if metadata != {'sha256': entries[name], 'size_bytes': (output / name).stat().st_size}:
            raise ValueError(f'Release manifest APK metadata differs for {name}')
    if entries['backdrop-launcher.apk'] != entries[f'backdrop-launcher-{version}.apk']:
        raise ValueError('Stable alias differs from the universal APK')
    if any(entries[name] != entries['backdrop-launcher.apk'] for name in expected_apks):
        raise ValueError('Compatibility aliases differ from the universal native APK')
    if manifest.get('signing_certificate_sha256') != SIGNING_CERTIFICATE_SHA256:
        raise ValueError('Release manifest certificate differs from the production signing key')
    if (manifest.get('implementation') != 'kotlin-compose' or manifest.get('application_id') != APPLICATION_ID
            or manifest.get('build_toolchain') != plan.get('build_toolchain') or 'flutter_version' in manifest
            or manifest.get('flavor') != 'github' or manifest.get('build_variant') != 'githubRelease'
            or manifest.get('self_updater_enabled') is not True):
        raise ValueError('Release manifest native build provenance differs from the release plan')
    architectures = native_payload(output / 'backdrop-launcher.apk')
    distribution = {'architecture': 'architecture-neutral' if not architectures else 'universal',
                    'native_abis': architectures, 'identical_aliases': True}
    if manifest.get('apk_distribution') != distribution:
        raise ValueError('Release manifest architecture metadata differs from the native APK')
    return sorted(output.iterdir())


def verify_uploaded_assets(files: list[Path], record: dict, sha: str) -> None:
    if not record.get('draft') or record.get('target_commitish') != sha:
        raise ValueError('Uploaded release must remain a draft for the exact checked source')
    assets = record.get('assets', [])
    names = [asset['name'] for asset in assets]
    if len(names) != len(set(names)) or set(names) != {path.name for path in files}:
        raise ValueError('Uploaded release has missing, duplicate or unexpected assets')
    indexed = {asset['name']: asset for asset in assets}
    for path in files:
        asset = indexed[path.name]
        digest = 'sha256:' + hashlib.sha256(path.read_bytes()).hexdigest()
        if asset.get('state') != 'uploaded' or asset.get('size') != path.stat().st_size:
            raise ValueError(f'Release upload is incomplete: {path.name}')
        if asset.get('digest') and asset['digest'] != digest:
            raise ValueError(f'Release server checksum differs: {path.name}')


def publish(plan: dict, output: Path, root: Path, api: Github) -> None:
    files = verify_distribution(plan, output)
    repository, sha = plan['repository'], plan['source_sha']
    require_source(sha, api.get(f'repos/{repository}/git/ref/heads/main')['object']['sha'], [], repository)
    runs = api.pages(f'repos/{repository}/actions/workflows/checks.yml/runs?head_sha={sha}&event=push&per_page=100', 'workflow_runs')
    checked = require_checks(runs, sha, repository, plan['checks_run_id'])
    if checked['run_attempt'] != plan['checks_run_attempt']:
        raise ValueError('Checks were rerun after release packaging; create a fresh release plan')
    records = [entry for entry in api.pages(f'repos/{repository}/releases?per_page=100') if entry['tag_name'] == plan['tag']]
    if records and (len(records) != 1 or not records[0]['draft'] or records[0]['target_commitish'] != sha):
        raise ValueError('Existing release is published or belongs to a different source')
    notes_path = output.parent / 'release-notes.md'
    notes_path.write_text(plan['notes'])
    common = ['--repo', repository]
    if not records:
        subprocess.run(['gh', 'release', 'create', plan['tag'], '--target', sha,
                        '--title', f'Backdrop Launcher {plan["version"]}', '--notes-file', str(notes_path), '--draft', *common], check=True)
    subprocess.run(['gh', 'release', 'upload', plan['tag'], *map(str, files), '--clobber', *common], check=True)
    uploaded = [entry for entry in api.pages(f'repos/{repository}/releases?per_page=100') if entry['tag_name'] == plan['tag']]
    if len(uploaded) != 1:
        raise ValueError('Uploaded draft release could not be resolved uniquely')
    verify_uploaded_assets(files, uploaded[0], sha)
    subprocess.run(['gh', 'release', 'edit', plan['tag'], '--target', sha,
                    '--title', f'Backdrop Launcher {plan["version"]}', '--notes-file', str(notes_path),
                    '--draft=false', '--latest', *common], check=True)


def write_outputs(values: dict) -> None:
    if os.environ.get('GITHUB_OUTPUT'):
        with Path(os.environ['GITHUB_OUTPUT']).open('a') as stream:
            for key, value in values.items():
                stream.write(f'{key}={value}\n')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    gate = commands.add_parser('plan', help='Read-only release gate and immutable build plan')
    gate.add_argument('--output', type=Path, required=True)
    gate.add_argument('--force', action='store_true', help='Allow an internal-only manual release; checks still required')
    package = commands.add_parser('package', help='Verify signed APKs and prepare release assets')
    package.add_argument('--plan', type=Path, required=True)
    package.add_argument('--apk-dir', type=Path, required=True)
    package.add_argument('--output', type=Path, required=True)
    package.add_argument('--apksigner', required=True)
    package.add_argument('--aapt2', required=True, help='Android build-tools verifier for APK package/version/code')
    publication = commands.add_parser('publish', help='Publish verified assets, resuming only a matching draft')
    publication.add_argument('--plan', type=Path, required=True)
    publication.add_argument('--dist', type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.command == 'plan':
            plan = make_plan(os.environ['GITHUB_REPOSITORY'], os.environ['RELEASE_SHA'],
                             int(os.environ['GITHUB_RUN_NUMBER']), int(os.environ['GITHUB_RUN_ID']),
                             expected_run_id=int(os.environ['CHECKS_RUN_ID']) if os.environ.get('CHECKS_RUN_ID') else None,
                             force=args.force)
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(json.dumps(plan, indent=2, sort_keys=True) + '\n')
            write_outputs({'ok': 'true', 'sha': plan['source_sha'], 'version': plan['version'],
                           'code': plan['android_version_code'], 'tag': plan['tag']})
            print(f'Release ready: {plan["tag"]}, Android {plan["android_version_code"]}, source {plan["source_sha"]}')
        elif args.command == 'package':
            plan = read_plan(args.plan)
            certificates = verify_apk_signatures(find_apks(args.apk_dir), args.apksigner)
            verify_apk_identity(find_apks(args.apk_dir), plan, args.aapt2)
            package_assets(plan, args.apk_dir, args.output, Path('.'), certificates)
            verify_distribution(plan, args.output)
            print(f'Prepared and verified {len(list(args.output.iterdir()))} release assets')
        else:
            publish(read_plan(args.plan), args.dist, Path('.'), Github())
    except NotReady as error:
        write_outputs({'ok': 'false'})
        print(f'Release skipped: {error}')
    except (ValueError, KeyError, OSError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        print(f'Release failed: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
