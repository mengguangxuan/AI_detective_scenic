"""Regression checks for shared Windows/WSL compiler-output handling."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / 'backend_sbt.py'
SPEC = importlib.util.spec_from_file_location('backend_sbt', SCRIPT)
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


class BackendLauncherTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.backend = Path(self.temp.name) / 'backend'
        self.cache = self.backend / '.build-cache'
        self.patch = patch.multiple(runner, BACKEND=self.backend, BUILD_CACHE=self.cache)
        self.patch.start()
        self.addCleanup(self.patch.stop)
        self.output = self.backend / 'target/out'
        self.output.mkdir(parents=True)
        (self.output / 'old-link.zip').symlink_to('/nonexistent/old-wsl-cache.zip')
        self.database = self.backend / 'target/fixture-demo/db.mv.db'
        self.database.parent.mkdir()
        self.database.write_text('preserve database')

    def test_unknown_outputs_are_archived_including_broken_links_and_database_is_preserved(self):
        runner.prepare_outputs('windows')
        self.assertFalse(self.output.exists())
        backups = list(self.cache.glob('output-backups/*/target/out/old-link.zip'))
        self.assertEqual(len(backups), 1)
        self.assertTrue(backups[0].is_symlink())
        self.assertEqual(self.database.read_text(), 'preserve database')

    def test_same_platform_does_not_move_outputs(self):
        self.cache.mkdir()
        (self.cache / 'output-platform.txt').write_text('linux\n')
        runner.prepare_outputs('linux')
        self.assertTrue(self.output.exists())
        self.assertFalse((self.cache / 'output-backups').exists())

    def test_switching_platform_archives_both_main_and_meta_build_outputs(self):
        self.cache.mkdir()
        (self.cache / 'output-platform.txt').write_text('windows\n')
        meta = self.backend / 'project/target'
        meta.mkdir(parents=True)
        (meta / 'metadata').write_text('old paths')
        runner.prepare_outputs('linux')
        self.assertFalse(meta.exists())
        self.assertEqual(len(list(self.cache.glob('output-backups/*/project/target/metadata'))), 1)
        self.assertEqual((self.cache / 'output-platform.txt').read_text().strip(), 'linux')

    def test_windows_delegates_to_powershell_without_searching_for_python_or_java(self):
        with patch.object(runner.sys, 'platform', 'win32'), patch.object(runner.sys, 'argv', ['backend_sbt.py', 'run']), \
             patch.object(runner.shutil, 'which', return_value='powershell.exe'), \
             patch.object(runner.subprocess, 'run') as run:
            run.return_value.returncode = 0
            self.assertEqual(runner.main(), 0)
            args = run.call_args.args[0]
            self.assertEqual(args[0], 'powershell.exe')
            self.assertEqual(args[-1], 'run')
            self.assertTrue(args[-2].endswith('backend.ps1'))


if __name__ == '__main__':
    unittest.main()
