"""Behavior checks for release inputs, trusted checks, notes and packaged assets."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


SCRIPT = Path(__file__).with_name('release.py')
SPEC = importlib.util.spec_from_file_location('backdrop_release', SCRIPT)
if SCRIPT.exists():
    import sys
    release = importlib.util.module_from_spec(SPEC)
    sys.modules[SPEC.name] = release
    SPEC.loader.exec_module(release)
else:
    release = None

SHA = 'a' * 40
REPO = 'johnneerdael/Backdrop-Launcher'


def green_run(**overrides):
    result = {
        'id': 123, 'run_number': 12, 'run_attempt': 1,
        'path': '.github/workflows/checks.yml',
        'event': 'push', 'head_branch': 'main', 'head_sha': SHA,
        'head_repository': {'full_name': REPO},
        'status': 'completed', 'conclusion': 'success',
    }
    result.update(overrides)
    return result


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(release, 'release tooling must exist')

    def test_version_uses_base_then_increments_highest_patch(self):
        self.assertEqual(release.select_version('1.3.0', ['1.2.0']), '1.3.0')
        self.assertEqual(release.select_version('1.3.0', ['1.3.0', 'v1.3.2']), '1.3.3')
        self.assertEqual(release.select_version('2.0.0', ['1.3.2']), '2.0.0')

    def test_version_rejects_malformed_or_equivalent_duplicate_tags(self):
        for value in ['1.3', '01.3.0', '1.3.0\nmalicious', '1.3.0;echo secret', 'v1.3.0']:
            with self.subTest(value=value), self.assertRaises(ValueError):
                release.select_version(value, [])
        with self.assertRaisesRegex(ValueError, 'duplicate'):
            release.select_version('1.3.0', ['1.3.0', 'v1.3.0'])

    def test_pubspec_requires_exactly_one_valid_version(self):
        self.assertEqual(release.parse_pubspec('name: flauncher\nversion: 1.3.0+4127\n'), ('1.3.0', 4127))
        for content in ['version: 1.3.0', 'version: 1.3.0+0', 'version: 1.3.0+4127\nversion: 1.3.0+4127', 'version: 1.3.0+4127; ls']:
            with self.subTest(content=content), self.assertRaises(ValueError):
                release.parse_pubspec(content)

    def test_android_code_preserves_first_base_and_increases_published_codes(self):
        self.assertEqual(release.select_build_number(4127, 3, []), 4127)
        self.assertEqual(release.select_build_number(4127, 4, [4127, 4128]), 4129)
        self.assertEqual(release.select_build_number(5000, 4, [4128]), 5000)
        self.assertEqual(release.select_build_number(4127, 6000, [4128]), 6000)
        for args in [(4127, 0, []), (4127, 1, [4127, 4127]), (2100000000, 1, [2100000000])]:
            with self.subTest(args=args), self.assertRaises(ValueError):
                release.select_build_number(*args)

    def test_checks_require_exact_successful_main_push_from_canonical_repo(self):
        self.assertEqual(release.require_checks([green_run()], SHA, REPO)['id'], 123)
        for override in [
            {'head_sha': 'b' * 40}, {'event': 'pull_request'}, {'head_branch': 'feature'},
            {'head_repository': {'full_name': 'attacker/fork'}}, {'conclusion': 'failure'},
            {'status': 'in_progress'}, {'path': '.github/workflows/other.yml'},
        ]:
            with self.subTest(override=override), self.assertRaises(ValueError):
                release.require_checks([green_run(**override)], SHA, REPO)
        with self.assertRaises(ValueError):
            release.require_checks([green_run()], SHA, REPO, expected_run_id=124)

    def test_newer_failed_or_running_checks_block_older_success(self):
        for status, conclusion in [('completed', 'failure'), ('in_progress', None)]:
            newer = green_run(id=124, run_number=13, status=status, conclusion=conclusion)
            with self.subTest(status=status), self.assertRaises(ValueError):
                release.require_checks([green_run(), newer], SHA, REPO)
        rerun = green_run(run_attempt=2, conclusion='failure')
        with self.assertRaises(ValueError):
            release.require_checks([green_run(), rerun], SHA, REPO)

    def test_gate_rejects_unchecked_or_stale_or_already_released_source(self):
        release.require_source(SHA, SHA, [], REPO)
        for args in [
            ('a' * 39, SHA, [], REPO), (SHA, 'b' * 40, [], REPO),
            (SHA, SHA, [SHA], REPO), (SHA, SHA, [], 'attacker/fork'),
        ]:
            with self.subTest(args=args), self.assertRaises(ValueError):
                release.require_source(*args)

    def test_public_notes_ignore_internal_commits_and_deduplicate_subjects(self):
        commits = [
            (SHA, 'feat(home): add custom rows'), ('b' * 40, 'fix(ci): repair runner'),
            ('c' * 40, 'docs: update the guide'), ('d' * 40, 'feat(home): add custom rows'),
            ('e' * 40, 'fix: keep [TV] focus <stable>'),
        ]
        notes = release.public_notes(commits, REPO)
        self.assertIn('add custom rows', notes)
        self.assertEqual(notes.count('add custom rows'), 1)
        self.assertNotIn('repair runner', notes)
        self.assertNotIn('update the guide', notes)
        self.assertIn(r'\[TV\]', notes)
        self.assertNotIn('<stable>', notes)
        self.assertIn(f'https://github.com/{REPO}/commit/{SHA}', notes)

    def test_notes_validate_immutable_banner_downloads_and_new_install_notice(self):
        notes = release.render_notes(REPO, SHA, '1.3.0', 4127, '- A new home screen.\n', '1.2.0')
        self.assertIn(f'/raw/{SHA}/docs/assets/brand/release-banner.png', notes)
        self.assertIn('Backdrop Launcher — Your TV. Your way.', notes)
        self.assertIn('## What’s new', notes)
        self.assertIn('/v1.3.0/backdrop-launcher-1.3.0-arm64-v8a.apk', notes)
        self.assertIn('https://backdrop-launcher.neerdael.nl/', notes)
        self.assertIn('separate install from Media Launcher', notes)
        self.assertIn('SHA256SUMS', notes)
        self.assertIn(f'/compare/1.2.0...{SHA}', notes)
        for content in ['', 'TODO: write notes']:
            with self.subTest(content=content), self.assertRaises(ValueError):
                release.render_notes(REPO, SHA, '1.3.0', 4127, content, None)
        with self.assertRaises(ValueError):
            release.render_notes(REPO, SHA + '\ncommand', '1.3.0', 4127, '- Notes', None)

    def test_manifest_rejects_conflicting_version_source_and_invalid_codes(self):
        manifest = {'schema': 1, 'repository': REPO, 'version': '1.3.0', 'source_sha': SHA, 'android_version_code': 4127}
        self.assertEqual(release.read_manifest(json.dumps(manifest), 'v1.3.0')['android_version_code'], 4127)
        for change in [{'version': '1.4.0'}, {'source_sha': 'main'}, {'android_version_code': True}, {'android_version_code': 0}, {'repository': 'attacker/fork'}]:
            with self.subTest(change=change), self.assertRaises(ValueError):
                release.read_manifest(json.dumps(dict(manifest, **change)), 'v1.3.0')
        with self.assertRaisesRegex(ValueError, 'duplicate'):
            release.read_manifest('{"schema":1,"schema":2}', 'v1.3.0')

    def test_apk_inputs_require_all_three_unique_variants(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for abi in release.ABIS:
                (root / f'backdrop-launcher-{abi}-release.apk').write_bytes(abi.encode())
            self.assertEqual(set(release.find_apks(root)), set(release.ABIS))
            nested = root / 'duplicate'
            nested.mkdir()
            (nested / 'backdrop-launcher-universal-release.apk').write_bytes(b'duplicate')
            with self.assertRaisesRegex(ValueError, 'exactly one'):
                release.find_apks(root)
            (nested / 'backdrop-launcher-universal-release.apk').unlink()
            (root / 'backdrop-launcher-arm64-v8a-release.apk').unlink()
            with self.assertRaisesRegex(ValueError, 'exactly one'):
                release.find_apks(root)

class PackageTests(unittest.TestCase):
    def test_distribution_includes_exact_apks_source_credits_manifest_and_checksums(self):
        import hashlib
        import subprocess
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            for args in [('user.name', 'Release Test'), ('user.email', 'release@example.invalid')]:
                subprocess.run(['git', '-C', str(root), 'config', *args], check=True)
            (root / 'LICENSE').write_text('GPL source license\n')
            subprocess.run(['git', '-C', str(root), 'add', 'LICENSE'], check=True)
            subprocess.run(['git', '-C', str(root), 'commit', '-qm', 'feat: initial home'], check=True)
            sha = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
            apk_dir = root / 'apks'
            apk_dir.mkdir()
            for abi in ('universal', 'arm64-v8a', 'armeabi-v7a'):
                (apk_dir / f'backdrop-launcher-{abi}-release.apk').write_bytes(abi.encode())
            plan = {'schema': 1, 'repository': REPO, 'source_sha': sha, 'version': '1.3.0',
                    'tag': 'v1.3.0', 'android_version_code': 4127, 'checks_run_id': 123,
                    'checks_run_attempt': 1, 'release_run_id': 234, 'notes': '- New home.'}
            certificates = {abi: release.SIGNING_CERTIFICATE_SHA256 for abi in release.ABIS}
            output = root / 'dist'
            release.package_assets(plan, apk_dir, output, root, certificates)
            files = {path.name for path in output.iterdir()}
            self.assertEqual(files, {
                'backdrop-launcher.apk', 'backdrop-launcher-1.3.0.apk',
                'backdrop-launcher-1.3.0-arm64-v8a.apk', 'backdrop-launcher-1.3.0-armeabi-v7a.apk',
                'backdrop-launcher-1.3.0-source.tar.gz', 'LICENSE', 'CREDITS.md',
                'release-manifest.json', 'SHA256SUMS',
            })
            checksums = {}
            for line in (output / 'SHA256SUMS').read_text().splitlines():
                checksum, filename = line.split('  ')
                checksums[filename] = checksum
                self.assertEqual(hashlib.sha256((output / filename).read_bytes()).hexdigest(), checksum)
            self.assertEqual(set(checksums), files - {'SHA256SUMS'})
            manifest = release.read_manifest((output / 'release-manifest.json').read_text(), 'v1.3.0')
            self.assertEqual(manifest['checks_run_id'], 123)
            self.assertEqual(manifest['apks']['backdrop-launcher.apk']['sha256'], checksums['backdrop-launcher.apk'])
            self.assertEqual(manifest['signing_certificate_sha256'], release.SIGNING_CERTIFICATE_SHA256)
            self.assertEqual((output / 'backdrop-launcher.apk').read_bytes(), (output / 'backdrop-launcher-1.3.0.apk').read_bytes())
            self.assertEqual(len(release.verify_distribution(plan, output)), 9)
            checksum_path = output / 'SHA256SUMS'
            original_checksums = checksum_path.read_text()
            checksum_path.write_text(original_checksums + original_checksums.splitlines()[0] + '\n')
            with self.assertRaisesRegex(ValueError, 'duplicate'):
                release.verify_distribution(plan, output)
            checksum_path.write_text(original_checksums)
            alias = output / 'backdrop-launcher.apk'
            original_apk = alias.read_bytes()
            alias.write_bytes(b'tampered')
            with self.assertRaisesRegex(ValueError, 'Checksum'):
                release.verify_distribution(plan, output)
            alias.write_bytes(original_apk)
            manifest_path = output / 'release-manifest.json'
            original_manifest = manifest_path.read_text()
            changed_manifest = json.loads(original_manifest)
            changed_manifest['apks']['backdrop-launcher.apk']['sha256'] = '0' * 64
            manifest_path.write_text(json.dumps(changed_manifest))
            checksum_path.write_text('\n'.join(
                f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}'
                for path in sorted(output.iterdir()) if path.name != 'SHA256SUMS'
            ) + '\n')
            with self.assertRaisesRegex(ValueError, 'metadata'):
                release.verify_distribution(plan, output)
            manifest_path.write_text(original_manifest)
            checksum_path.write_text(original_checksums)
            with self.assertRaisesRegex(ValueError, 'empty'):
                release.package_assets(plan, apk_dir, output, root, certificates)

    def test_signing_certificate_must_match_all_variants(self):
        for certificates in [
            {abi: 'f' * 64 for abi in ('universal', 'arm64-v8a', 'armeabi-v7a')},
            {'universal': 'f' * 64},
            {'universal': 'f' * 64, 'arm64-v8a': 'e' * 64, 'armeabi-v7a': 'f' * 64},
            {abi: 'not-a-sha' for abi in ('universal', 'arm64-v8a', 'armeabi-v7a')},
        ]:
            with self.subTest(certificates=certificates), self.assertRaises(ValueError):
                release.require_certificates(certificates)

class PlanTests(unittest.TestCase):
    def test_plan_uses_exact_checked_source_and_curated_version_notes(self):
        import subprocess
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            for args in [('user.name', 'Release Test'), ('user.email', 'release@example.invalid')]:
                subprocess.run(['git', '-C', str(root), 'config', *args], check=True)
            (root / 'pubspec.yaml').write_text('version: 1.3.0+4127\n')
            (root / 'docs/releases').mkdir(parents=True)
            (root / 'docs/releases/1.3.0.md').write_text('- Welcome to Backdrop Launcher.\n')
            subprocess.run(['git', '-C', str(root), 'add', '.'], check=True)
            subprocess.run(['git', '-C', str(root), 'commit', '-qm', 'chore: prepare branded release'], check=True)
            sha = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
            api = ReadOnlyApi(sha)
            plan = release.make_plan(REPO, sha, 1, 234, root=root, api=api)
            self.assertEqual(plan['version'], '1.3.0')
            self.assertEqual(plan['android_version_code'], 4127)
            self.assertEqual(plan['source_sha'], sha)
            self.assertEqual(plan['checks_run_id'], 123)
            self.assertIn('Welcome to Backdrop Launcher.', plan['notes'])
            subprocess.run(['git', '-C', str(root), 'tag', 'v1.3.0'], check=True)
            with self.assertRaisesRegex(ValueError, 'tag'):
                release.make_plan(REPO, sha, 1, 234, root=root, api=api)

    def test_internal_only_commit_skips_automatic_release_and_manual_can_force(self):
        import subprocess
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            for args in [('user.name', 'Release Test'), ('user.email', 'release@example.invalid')]:
                subprocess.run(['git', '-C', str(root), 'config', *args], check=True)
            (root / 'pubspec.yaml').write_text('version: 1.3.0+4127\n')
            subprocess.run(['git', '-C', str(root), 'add', '.'], check=True)
            subprocess.run(['git', '-C', str(root), 'commit', '-qm', 'docs: clarify developer setup'], check=True)
            sha = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
            with self.assertRaisesRegex(ValueError, 'public'):
                release.make_plan(REPO, sha, 1, 234, root=root, api=ReadOnlyApi(sha))
            plan = release.make_plan(REPO, sha, 1, 234, root=root, api=ReadOnlyApi(sha), force=True)
            self.assertIn('Maintenance update', plan['notes'])


class ReadOnlyApi:
    """A bounded fixture for GitHub data; no network or publication methods."""
    def __init__(self, sha, releases=None, manifests=None):
        self.sha = sha
        self.releases = releases or []
        self.manifests = manifests or {}

    def get(self, endpoint):
        if endpoint == f'repos/{REPO}/git/ref/heads/main':
            return {'object': {'sha': self.sha}}
        raise AssertionError(f'Unexpected API endpoint: {endpoint}')

    def pages(self, endpoint, key=None):
        if '/actions/workflows/checks.yml/runs?' in endpoint:
            return [green_run(head_sha=self.sha)]
        if endpoint == f'repos/{REPO}/releases?per_page=100':
            return self.releases
        raise AssertionError(f'Unexpected API endpoint: {endpoint}')

    def raw(self, endpoint):
        return self.manifests[endpoint]

class HistoryTests(unittest.TestCase):
    def test_history_drives_patch_and_code_and_only_matching_draft_can_resume(self):
        import subprocess
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            for args in [('user.name', 'Release Test'), ('user.email', 'release@example.invalid')]:
                subprocess.run(['git', '-C', str(root), 'config', *args], check=True)
            (root / 'pubspec.yaml').write_text('version: 1.3.0+4127\n')
            subprocess.run(['git', '-C', str(root), 'add', '.'], check=True)
            subprocess.run(['git', '-C', str(root), 'commit', '-qm', 'feat: branded home'], check=True)
            prior = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
            subprocess.run(['git', '-C', str(root), 'tag', 'v1.3.0'], check=True)
            (root / 'focus.txt').write_text('focus fixed\n')
            subprocess.run(['git', '-C', str(root), 'add', '.'], check=True)
            subprocess.run(['git', '-C', str(root), 'commit', '-qm', 'fix: preserve focus'], check=True)
            sha = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
            published = {'tag_name': 'v1.3.0', 'draft': False, 'prerelease': False,
                         'target_commitish': prior, 'assets': [{'name': 'release-manifest.json', 'id': 7}]}
            manifest = {'schema': 1, 'repository': REPO, 'version': '1.3.0', 'source_sha': prior, 'android_version_code': 4127}
            endpoint = f'repos/{REPO}/releases/assets/7'
            api = ReadOnlyApi(sha, [published], {endpoint: json.dumps(manifest)})
            plan = release.make_plan(REPO, sha, 10, 234, root=root, api=api)
            self.assertEqual((plan['version'], plan['android_version_code']), ('1.3.1', 4128))
            self.assertIn('preserve focus', plan['notes'])
            self.assertNotIn('branded home', plan['notes'])
            draft = {'tag_name': 'v1.3.1', 'draft': True, 'prerelease': False,
                     'target_commitish': sha, 'assets': []}
            api.releases.append(draft)
            retry = release.make_plan(REPO, sha, 10, 234, root=root, api=api)
            self.assertEqual((retry['version'], retry['android_version_code']), ('1.3.1', 4128))
            draft['target_commitish'] = 'main'
            with self.assertRaisesRegex(ValueError, 'draft'):
                release.make_plan(REPO, sha, 10, 234, root=root, api=api)
            api.releases.pop()
            published['assets'] = []
            with self.assertRaisesRegex(ValueError, 'missing'):
                release.make_plan(REPO, sha, 10, 234, root=root, api=api)


class UploadedAssetsTests(unittest.TestCase):
    def test_uploaded_assets_must_be_complete_unique_and_for_the_same_draft_source(self):
        import copy
        import hashlib
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'backdrop-launcher.apk'
            path.write_bytes(b'signed APK fixture')
            asset = {'name': path.name, 'state': 'uploaded', 'size': path.stat().st_size,
                     'digest': 'sha256:' + hashlib.sha256(path.read_bytes()).hexdigest()}
            record = {'draft': True, 'target_commitish': SHA, 'assets': [asset]}
            release.verify_uploaded_assets([path], record, SHA)
            for change in [
                {'assets': []}, {'assets': [asset, asset]}, {'draft': False},
                {'target_commitish': 'main'},
                {'assets': [dict(asset, state='starter')]},
                {'assets': [dict(asset, size=0)]},
                {'assets': [dict(asset, digest='sha256:' + '0' * 64)]},
            ]:
                with self.subTest(change=change), self.assertRaises(ValueError):
                    release.verify_uploaded_assets([path], dict(copy.deepcopy(record), **change), SHA)


if __name__ == '__main__':
    unittest.main()
