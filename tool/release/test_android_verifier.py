import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).with_name('prepare_android_verifier.sh').resolve()


class AndroidVerifierTest(unittest.TestCase):
    def prepare(self, root):
        sdk = root / 'android-sdk'
        (sdk / 'build-tools/36.0.0').mkdir(parents=True)
        (sdk / 'cmdline-tools/latest/bin').mkdir(parents=True)
        return sdk

    def run_preparer(self, sdk):
        # Match the failing runner condition: sdkmanager is absent from PATH.
        return subprocess.run(['/bin/bash', str(SCRIPT)],
                              env={'ANDROID_HOME': str(sdk), 'PATH': ''},
                              capture_output=True, text=True)

    def test_installs_using_absolute_sdk_path_without_path_entry(self):
        with tempfile.TemporaryDirectory() as directory:
            sdk = self.prepare(Path(directory))
            manager = sdk / 'cmdline-tools/latest/bin/sdkmanager'
            manager.write_text("#!/bin/bash\nprintf '%s' \"$1\" > \"$ANDROID_HOME/requested-package\"\nprintf '#!/bin/bash\\nexit 0\\n' > \"$ANDROID_HOME/build-tools/36.0.0/apksigner\"\n/bin/chmod +x \"$ANDROID_HOME/build-tools/36.0.0/apksigner\"\n")
            manager.chmod(0o755)
            result = self.run_preparer(sdk)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual((sdk / 'requested-package').read_text(), 'build-tools;36.0.0')
            self.assertTrue(os.access(sdk / 'build-tools/36.0.0/apksigner', os.X_OK))

    def test_existing_verifier_needs_no_sdk_manager(self):
        with tempfile.TemporaryDirectory() as directory:
            sdk = self.prepare(Path(directory))
            verifier = sdk / 'build-tools/36.0.0/apksigner'
            verifier.write_text('#!/bin/bash\nexit 0\n')
            verifier.chmod(0o755)
            result = self.run_preparer(sdk)
            self.assertEqual(result.returncode, 0, result.stderr)

    def test_missing_sdk_manager_fails_with_actionable_path(self):
        with tempfile.TemporaryDirectory() as directory:
            sdk = self.prepare(Path(directory))
            result = self.run_preparer(sdk)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn(str(sdk / 'cmdline-tools/latest/bin/sdkmanager'), result.stderr)


if __name__ == '__main__':
    unittest.main()
