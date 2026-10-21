"""Offline launcher contracts. These tests do not launch Minecraft or Gradle."""
import contextlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import runner


class CliTests(unittest.TestCase):
    def parse(self, family, arguments):
        with contextlib.redirect_stderr(io.StringIO()):
            return runner.parse_options(family, arguments)

    def test_every_legacy_fixture_flag_is_exclusive_and_selects_its_marker(self):
        for family, modes in runner.MODES.items():
            for mode in modes:
                with self.subTest(family=family, mode=mode.flag):
                    self.assertEqual(self.parse(family, [mode.flag]).mode, mode)
            with self.assertRaises(SystemExit):
                self.parse(family, [modes[0].flag, modes[1].flag])

    def test_unknown_and_misspelled_flags_fail(self):
        for args in (['--test', '--typo'], ['--font-tset'], ['--font-t'], []):
            with self.assertRaises(SystemExit):
                self.parse('wayfarer-hud', args)

    def test_timeout_must_be_finite_and_positive(self):
        for value in ('0', '-1', 'inf', 'nan', 'tomorrow'):
            with self.assertRaises(SystemExit):
                self.parse('wayfarer', ['--test', '--timeout', value])
        self.assertEqual(self.parse('wayfarer', ['--test', '--timeout', '12.5']).timeout, 12.5)

    def test_keep_open_only_applies_to_explicit_capture(self):
        self.assertTrue(self.parse('wayfarer-hud', ['--capture', '--keep-open']).keep_open)
        with self.assertRaises(SystemExit):
            self.parse('wayfarer-hud', ['--test', '--keep-open'])

    def test_public_compatibility_without_arguments_delegates_without_export(self):
        with patch.object(sys, 'argv', ['launch.py']), patch.object(runner.subprocess, 'call', return_value=0) as call:
            self.assertEqual(runner.compatibility_main('wayfarer-hud'), 0)
        self.assertIn(':pane-demo:runWayfarerHud', call.call_args.args[0])

    def test_invalid_compatibility_arguments_do_not_build(self):
        with patch.object(sys, 'argv', ['launch.py', '--tset']), patch.object(runner, 'export_runtime') as export:
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                runner.compatibility_main('wayfarer')
            export.assert_not_called()


class RuntimeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='panelib-test space-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.classes = self.root / 'classes'
        self.classes.mkdir()
        (self.classes / 'Example.class').write_bytes(b'original class')
        self.resources = self.root / 'resources'
        self.resources.mkdir()
        (self.resources / 'asset.txt').write_text('original asset')
        self.jar = self.root / 'renamed-latest-42.jar'
        self.write_jar()
        self.dependency = self.root / 'immutable dependency.jar'
        self.dependency.write_bytes(b'cached dependency')
        # Prefix-collision path must not accidentally be changed by substring replacement.
        self.collision = self.root / 'classes-extra.jar'
        self.collision.write_bytes(b'another dependency')
        self.logging = self.root / 'generated-log4j.xml'
        self.logging.write_text('<Configuration/>')
        self.config = self.root / 'launch.cfg'
        self.config.write_text(f'commonProperties\n\tlog4j.configurationFile={self.logging}\n'
                               f'\tfabric.classPathGroups={self.classes}{os.pathsep}{self.resources}\n'
                               f'\tfixture.other={self.collision}\n')
        self.manifest = {'schema': 1, 'java': sys.executable, 'javaVersion': 25, 'version': '42-dev',
                         'libraryJar': str(self.jar), 'outputs': [str(self.classes), str(self.resources)],
                         'classpath': [str(self.classes), str(self.resources), str(self.jar),
                                       str(self.dependency), str(self.collision)], 'launchConfig': str(self.config)}
        self.frozen = self.root / 'frozen'
        self.frozen.mkdir()

    def write_jar(self, version='42-dev', mod_id='panelib'):
        with zipfile.ZipFile(self.jar, 'w') as archive:
            archive.writestr('fabric.mod.json', json.dumps({'id': mod_id, 'version': version, 'environment': 'client'}))
            archive.writestr('dev/pane/Example.class', b'class')

    def test_actual_versioned_jar_and_output_paths_are_frozen_and_independent(self):
        cp, record = runner.freeze_runtime(self.manifest, self.frozen)
        self.assertNotIn(str(self.classes), cp.split(os.pathsep))
        self.assertIn(str(self.collision), cp.split(os.pathsep))
        self.assertIn(str(self.dependency), cp.split(os.pathsep))
        (self.classes / 'Example.class').write_bytes(b'new build')
        self.write_jar('43-dev')
        self.assertEqual((self.frozen / '0-classes/Example.class').read_bytes(), b'original class')
        with zipfile.ZipFile(self.frozen / '2-renamed-latest-42.jar') as archive:
            self.assertEqual(json.loads(archive.read('fabric.mod.json'))['version'], '42-dev')
        self.assertEqual(len(record['sha256']), 64)
        self.assertEqual(record['version'], '42-dev')
        frozen_config = (self.frozen / 'launch.cfg').read_text()
        self.assertIn(str(self.frozen / '0-classes'), frozen_config)
        self.assertNotIn(str(self.logging), frozen_config)
        self.assertIn(str(self.collision), frozen_config)
        self.assertEqual((self.frozen / 'log4j.xml').read_text(), '<Configuration/>')

    def test_windows_separator_assembly_is_explicit(self):
        cp, _ = runner.freeze_runtime(self.manifest, self.frozen, path_separator=';')
        self.assertEqual(len(cp.split(';')), 5)
        self.assertIn('test space-', cp)

    def test_wrong_or_stale_jar_is_rejected(self):
        for version, mod_id in (('old', 'panelib'), ('42-dev', 'other')):
            self.write_jar(version, mod_id)
            with self.assertRaises(ValueError):
                runner.validate_manifest(self.manifest)

    def test_missing_and_unlisted_build_outputs_are_rejected(self):
        broken = dict(self.manifest, outputs=[str(self.root / 'not-built')])
        with self.assertRaises(ValueError):
            runner.validate_manifest(broken)
        broken = dict(self.manifest, classpath=[str(self.classes)])
        with self.assertRaises(ValueError):
            runner.validate_manifest(broken)

    def test_platform_flags_and_only_one_fixture_property(self):
        options = runner.parse_options('wayfarer-hud', ['--font-test'])
        for platform in ('darwin', 'win32', 'linux'):
            cmd = runner.client_command(self.manifest, self.frozen, 'cp', 'wayfarer-hud', options, platform)
            self.assertEqual('-XstartOnFirstThread' in cmd, platform == 'darwin')
            self.assertIn('-Dpane.demo.nativeTextTest=true', cmd)
            self.assertIn('-Dpane.demo.wayfarerHud=true', cmd)
            self.assertNotIn('-Dpane.demo.hudNativeTest=true', cmd)

    def test_profile_lock_blocks_overlap_and_releases_on_failure(self):
        profile = self.root / 'profile'
        with self.assertRaises(RuntimeError):
            with runner.profile_lock(profile):
                with self.assertRaises(ValueError):
                    with runner.profile_lock(profile):
                        self.fail('concurrent profile entry')
                raise RuntimeError('simulated launch failure')
        self.assertFalse((profile / '.panelib-fixture.lock').exists())

    def test_options_are_local_and_preserved_without_private_profile_copy(self):
        profile = self.root / 'profile'
        profile.mkdir()
        (profile / 'options.txt').write_text('guiScale:2\nonboardAccessibility:true\n')
        runner.prepare_options(profile)
        self.assertEqual((profile / 'options.txt').read_text(), 'guiScale:2\nonboardAccessibility:false\ntutorialStep:none\n')
        self.assertFalse((profile / 'saves').exists())

    def test_fresh_profiles_are_distinct_and_never_touch_existing_world(self):
        mode = runner.MODES['wayfarer-hud'][0]
        first = runner.profile_path(self.root, mode, True)
        second = runner.profile_path(self.root, mode, True)
        self.assertNotEqual(first, second)
        self.assertEqual(first.parent, runner.profile_path(self.root, mode, False))
        with runner.profile_lock(first), runner.profile_lock(second):
            self.assertFalse((first / 'saves').exists())
            self.assertFalse((second / 'saves').exists())

    def test_failed_process_start_cleans_runtime_and_profile_lock(self):
        make_temporary = tempfile.TemporaryDirectory
        def temporary_here(*args, **kwargs):
            return make_temporary(*args, dir=self.root, **kwargs)
        with patch.object(runner, 'export_runtime', return_value=self.manifest), \
                patch.object(runner.tempfile, 'TemporaryDirectory', side_effect=temporary_here), \
                patch.object(runner, 'run_client', side_effect=OSError('synthetic start failure')), \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(runner.main('wayfarer', ['--test'], self.root), 1)
        self.assertEqual(list(self.root.glob('panelib-wayfarer-*')), [])
        self.assertEqual(list(self.root.rglob('.panelib-fixture.lock')), [])

    def test_bad_toolchain_is_rejected(self):
        with self.assertRaises(ValueError):
            runner.validate_manifest(dict(self.manifest, javaVersion=21))


class ProcessTests(unittest.TestCase):
    def run_code(self, code, timeout=3):
        with tempfile.TemporaryDirectory() as directory, contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            log = io.StringIO()
            code = runner.run_client([sys.executable, '-c', code], Path(directory), 'FixtureTest', timeout, log)
            return code, log.getvalue()

    def test_success_requires_completion_and_zero_exit(self):
        self.assertEqual(self.run_code("print('[FixtureTest] complete PASS checks=4')")[0], 0)
        self.assertEqual(self.run_code("print('[FixtureTest] check PASS')")[0], 1)
        self.assertEqual(self.run_code("print('[FixtureTest] complete PASSING')")[0], 1)
        self.assertEqual(self.run_code("print('[FixtureTest] complete PASS'); raise SystemExit(7)")[0], 1)

    def test_failure_is_sticky_even_with_success_later(self):
        code, log = self.run_code("print('[OtherTest] FAIL bad'); print('[FixtureTest] complete PASS')")
        self.assertEqual(code, 1)
        self.assertIn('FAIL bad', log)

    def test_silent_child_timeout_stops_child(self):
        code, log = self.run_code('import time; time.sleep(60)', timeout=0.15)
        self.assertEqual(code, 1)
        self.assertIn('FAIL timeout', log)

    def test_closed_stdout_does_not_evade_deadline(self):
        code, log = self.run_code('import os, time; os.close(1); os.close(2); time.sleep(60)', timeout=0.15)
        self.assertEqual(code, 1)
        self.assertIn('FAIL timeout', log)

    def test_completed_marker_does_not_excuse_hung_shutdown(self):
        code, log = self.run_code("import time; print('[FixtureTest] complete PASS', flush=True); time.sleep(60)", timeout=0.15)
        self.assertEqual(code, 1)
        self.assertIn('FAIL timeout', log)


if __name__ == '__main__':
    unittest.main()
