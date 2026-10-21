#!/usr/bin/env python3
"""Internal frozen-client runner. Public examples continue to use Loom's supported runs."""
from __future__ import annotations

import argparse
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import queue
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[2]


@dataclass(frozen=True)
class Mode:
    flag: str
    property: str
    marker: str
    directory: str
    extra: tuple[str, ...] = ()


MODES = {
    'wayfarer': (
        Mode('--examples-test', 'exampleTest', 'ExamplesTest', 'run-example-regression'),
        Mode('--test', 'wayfarerTest', 'WayfarerTest', 'run-wayfarer'),
        Mode('--menu-only', 'wayfarerTest', 'WayfarerTest', 'run-wayfarer', ('wayfarerMenuOnly',)),
        Mode('--player-preview-test', 'playerPreviewTest', 'PlayerPreviewTest', 'run-wayfarer'),
        Mode('--dialog-typography-test', 'dialogTypographyTest', 'DialogTypographyTest', 'run-dialog-typography'),
        Mode('--dialog-test', 'dialogTest', 'DialogTest', 'run-dialog'),
        Mode('--image-test', 'resourceTest', 'ResourceTest', 'run-image-options'),
        Mode('--adoption-test', 'adoptionTest', 'AdoptionTest', 'run-adoption'),
        Mode('--editing-test', 'editingTest', 'EditingTest', 'run-editing'),
        Mode('--editor-test', 'editorTest', 'EditorTest', 'run-editor'),
        Mode('--mail-test', 'gmailTest', 'GmailTest', 'run-mail'),
    ),
    'discord-inbox': (
        Mode('--test', 'discordTest', 'DiscordTest', 'run-discord'),
        Mode('--scroll-end-test', 'scrollEndTest', 'ScrollEndTest', 'run-scroll-end'),
        Mode('--popup-trigger-test', 'popupTriggerTest', 'PopupTriggerTest', 'run-popup-triggers'),
        Mode('--popup-test', 'popupTest', 'PopupTest', 'run-popup'),
    ),
    'wayfarer-hud': (
        Mode('--test', 'hudNativeTest', 'HudNativeTest', 'run-hud-native-regression'),
        Mode('--restoration-test', 'hudRestorationTest', 'HudRestorationTest', 'run-hud-restoration-regression'),
        Mode('--placement-test', 'hudPlacementTest', 'HudPlacementTest', 'run-hud-placement'),
        Mode('--quality-test', 'hudQualityTest', 'HudQualityTest', 'run-hud-quality'),
        Mode('--efficiency-test', 'hudEfficiencyTest', 'HudEfficiencyTest', 'run-hud-efficiency'),
        Mode('--font-test', 'nativeTextTest', 'NativeTextTest', 'run-native-font'),
        Mode('--capture', 'wayfarerHud', 'WayfarerHudCapture', 'run-wayfarerHud'),
    ),
}
PUBLIC_TASKS = {'wayfarer': 'runWayfarer', 'discord-inbox': 'runGather', 'wayfarer-hud': 'runWayfarerHud'}
FAILURE = re.compile(r'\[(?:[^]\r\n]*(?:Test|Capture)|Fixture|ReviewFix)\][^\r\n]*\bFAIL\b')


def positive_seconds(value):
    try:
        seconds = float(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError('timeout must be a positive number of seconds') from exc
    if not math.isfinite(seconds) or seconds <= 0:
        raise argparse.ArgumentTypeError('timeout must be a finite positive number of seconds')
    return seconds


def parse_options(family, argv):
    parser = argparse.ArgumentParser(description=f'Run one isolated {family} fixture (builds first).',
                                     allow_abbrev=False)
    modes = parser.add_mutually_exclusive_group(required=True)
    for mode in MODES[family]:
        modes.add_argument(mode.flag, dest='mode', action='store_const', const=mode,
                           help=f'Run {mode.marker}.')
    parser.add_argument('--timeout', type=positive_seconds, default=600,
                        help='Client wall-clock timeout in seconds (default: 600).')
    parser.add_argument('--fresh', action='store_true', help='Use a new isolated profile; never copy a world.')
    if family == 'wayfarer-hud':
        parser.add_argument('--keep-open', action='store_true',
                            help='Leave capture open after completion, bounded by --timeout; only with --capture.')
    options = parser.parse_args(argv)
    if getattr(options, 'keep_open', False) and options.mode.flag != '--capture':
        parser.error('--keep-open is only supported with --capture')
    return options


def gradle_command(root, *arguments):
    wrapper = root / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
    return [str(wrapper), *arguments, '--no-daemon', '--max-workers=2']


def export_runtime(root):
    # This task builds the exact library and demo first. A stale cached manifest is never consumed.
    subprocess.run(gradle_command(root, '-I', str(root / 'tools/fixtures/runtime.init.gradle'),
                                 ':pane-demo:writeFixtureRuntime'), cwd=root, check=True, timeout=1200)
    return json.loads((root / 'pane-demo/build/fixture-runtime.json').read_text(encoding='utf-8'))


def validate_manifest(manifest):
    if manifest.get('schema') != 1:
        raise ValueError('Unsupported fixture runtime manifest; regenerate with writeFixtureRuntime.')
    if manifest.get('javaVersion') != 25:
        raise ValueError('The fixture requires the configured Java 25 toolchain.')
    for field in ('java', 'libraryJar', 'launchConfig'):
        if not Path(manifest[field]).is_file():
            raise ValueError(f'Runtime {field} is missing: {manifest[field]}')
    classpath = manifest.get('classpath', [])
    if not classpath or not isinstance(classpath, list):
        raise ValueError('Gradle exported an empty classpath.')
    if manifest['libraryJar'] not in classpath:
        raise ValueError('The configured library JAR is absent from the runtime classpath.')
    for entry in classpath:
        if not Path(entry).exists():
            raise ValueError(f'Runtime classpath entry is missing: {entry}')
    if not manifest.get('outputs'):
        raise ValueError('Gradle exported no demo outputs.')
    for output in manifest['outputs']:
        if output not in classpath:
            raise ValueError(f'Demo output absent from runtime classpath: {output}')
    validate_jar(Path(manifest['libraryJar']), manifest['version'])


def validate_jar(path, version):
    with zipfile.ZipFile(path) as archive:
        if archive.testzip():
            raise ValueError('PaneLib runtime JAR failed its ZIP integrity check.')
        metadata = json.loads(archive.read('fabric.mod.json'))
        if metadata.get('id') != 'panelib' or metadata.get('version') != version:
            raise ValueError('PaneLib JAR metadata does not match the configured build.')
        if metadata.get('environment') != 'client':
            raise ValueError('Expected the client-only PaneLib runtime JAR.')


def freeze_runtime(manifest, destination, path_separator=os.pathsep):
    """Copy mutable project outputs, retaining exact classpath entry boundaries."""
    validate_manifest(manifest)
    replacements = {}
    for index, name in enumerate(dict.fromkeys([*manifest['outputs'], manifest['libraryJar']])):
        source = Path(name)
        target = destination / f'{index}-{source.name}'
        if source.is_dir():
            shutil.copytree(source, target)
        else:
            shutil.copy2(source, target)
        replacements[name] = str(target)
    config = Path(manifest['launchConfig']).read_text(encoding='utf-8')
    # Loom's generated log configuration is mutable too, and can disappear during clean.
    match = re.search(r'(?m)^\s*log4j.configurationFile=(.+)$', config)
    if match:
        logging = Path(match.group(1).strip())
        if not logging.is_file():
            raise ValueError(f'Loom logging configuration is missing: {logging}')
        target = destination / 'log4j.xml'
        shutil.copy2(logging, target)
        replacements[str(logging)] = str(target)
    for old, new in sorted(replacements.items(), key=lambda item: -len(item[0])):
        config = re.sub(re.escape(old) + r'(?=$|[\r\n:;])', lambda match: new, config, flags=re.MULTILINE)
    (destination / 'launch.cfg').write_text(config, encoding='utf-8')
    classpath = path_separator.join(replacements.get(entry, entry) for entry in manifest['classpath'])
    frozen_jar = Path(replacements[manifest['libraryJar']])
    validate_jar(frozen_jar, manifest['version'])
    digest = hashlib.sha256(frozen_jar.read_bytes()).hexdigest()
    # Preserve the authoritative input and digest as evidence outside the disposable runtime later.
    record = {**manifest, 'sha256': digest, 'frozenClasspath': classpath}
    (destination / 'runtime.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    return classpath, record


def client_command(manifest, runtime, classpath, family, options, platform=sys.platform):
    flags = ['-XstartOnFirstThread'] if platform == 'darwin' else []
    properties = [options.mode.property, *options.mode.extra]
    if family == 'wayfarer-hud' and 'wayfarerHud' not in properties:
        properties.append('wayfarerHud')
    if getattr(options, 'keep_open', False):
        properties.append('wayfarerHudKeepOpen')
    return [manifest['java'], *flags, '--sun-misc-unsafe-memory-access=allow',
            '--enable-native-access=ALL-UNNAMED', '-Dfile.encoding=UTF-8',
            f'-Dfabric.dli.config={runtime / "launch.cfg"}', '-Dfabric.dli.env=client',
            '-Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient',
            *(f'-Dpane.demo.{value}=true' for value in properties),
            '-cp', classpath, 'net.fabricmc.devlaunchinjector.Main']


@contextmanager
def profile_lock(profile):
    profile.mkdir(parents=True, exist_ok=True)
    lock = profile / '.panelib-fixture.lock'
    try:
        descriptor = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    except FileExistsError as exc:
        raise ValueError(f'Fixture profile is locked: {profile}. Use --fresh, or remove {lock.name} '
                         'only after checking its recorded process has exited.') from exc
    try:
        with os.fdopen(descriptor, 'w') as stream:
            stream.write(f'runner PID: {os.getpid()}\n')
        yield
    finally:
        lock.unlink(missing_ok=True)


def prepare_options(profile):
    target = profile / 'options.txt'
    lines = target.read_text(encoding='utf-8').splitlines() if target.exists() else []
    lines = [line for line in lines if not line.startswith(('onboardAccessibility:', 'tutorialStep:'))]
    target.write_text('\n'.join([*lines, 'onboardAccessibility:false', 'tutorialStep:none']) + '\n', encoding='utf-8')


def stop_owned_process(process):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)


def run_client(command, profile, marker, timeout, log, capture=False):
    """Drain stdout on a reader so a silent or hung JVM cannot evade the deadline."""
    process = subprocess.Popen(command, cwd=profile, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                               text=True, encoding='utf-8', errors='replace')
    lines = queue.Queue()

    def read_output():
        try:
            for line in process.stdout:
                lines.put(line)
        finally:
            lines.put(None)

    reader = threading.Thread(target=read_output, daemon=True)
    reader.start()
    deadline = time.monotonic() + timeout
    success = re.compile(r'\[' + re.escape(marker) + r'\] complete' + (r'\b' if capture else r' PASS\b'))
    passed = failed = timed_out = False
    try:
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                timed_out = True
                break
            try:
                line = lines.get(timeout=min(remaining, 0.2))
            except queue.Empty:
                continue
            if line is None:
                # A program can close stdout while staying alive: the process deadline still applies.
                try:
                    process.wait(timeout=max(0.01, deadline - time.monotonic()))
                except subprocess.TimeoutExpired:
                    timed_out = True
                break
            print(line, end='', flush=True)
            log.write(line)
            log.flush()
            passed |= bool(success.search(line))
            failed |= bool(FAILURE.search(line))
    finally:
        stop_owned_process(process)
        reader.join(timeout=2)
        process.stdout.close()
        # Retain shutdown diagnostics, including failures printed just as the deadline expires.
        while not lines.empty():
            line = lines.get_nowait()
            if line is not None:
                log.write(line)
                failed |= bool(FAILURE.search(line))
    if timed_out:
        print(f'Fixture timed out after {timeout:g}s; owned client stopped.', file=sys.stderr)
        log.write(f'\n[Fixture] FAIL timeout after {timeout:g}s\n')
    if not passed:
        print(f'Missing completion marker: [{marker}] complete', file=sys.stderr)
    return 1 if timed_out or failed or not passed or process.returncode else 0


def profile_path(root, mode, fresh):
    base = root / 'pane-demo' / mode.directory
    return base / ('fresh-' + uuid.uuid4().hex) if fresh else base


def main(family, argv=None, root=ROOT):
    argv = list(sys.argv[1:] if argv is None else argv)
    # Old capture helper accepted --keep-open by itself. Keep that documented behavior explicit.
    if family == 'wayfarer-hud' and not any(arg == mode.flag for mode in MODES[family] for arg in argv):
        if not argv or '--keep-open' in argv:
            argv.insert(0, '--capture')
    options = parse_options(family, argv)
    try:
        manifest = export_runtime(root)
        profile = profile_path(root, options.mode, options.fresh)
        with profile_lock(profile), tempfile.TemporaryDirectory(prefix=f'panelib-{family}-') as folder:
            runtime = Path(folder)
            classpath, record = freeze_runtime(manifest, runtime)
            prepare_options(profile)
            timestamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S-%fZ')
            evidence = profile / f'fixture-{timestamp}'
            evidence.with_suffix('.json').write_text(json.dumps({**record, 'profile': str(profile),
                'mode': options.mode.flag, 'timeout': options.timeout}, indent=2) + '\n', encoding='utf-8')
            print(f'Frozen PaneLib {manifest["version"]} SHA256: {record["sha256"]}', flush=True)
            print(f'Fixture profile: {profile}\nFixture log: {evidence.with_suffix(".log")}', flush=True)
            command = client_command(manifest, runtime, classpath, family, options)
            with evidence.with_suffix('.log').open('w', encoding='utf-8') as log:
                return run_client(command, profile, options.mode.marker, options.timeout, log,
                                  capture=options.mode.flag == '--capture')
    except (OSError, ValueError, KeyError, zipfile.BadZipFile, subprocess.SubprocessError) as exc:
        print(f'Fixture could not run: {exc}', file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        return 130


def compatibility_main(family):
    if len(sys.argv) == 1:
        return subprocess.call(gradle_command(ROOT, ':pane-demo:' + PUBLIC_TASKS[family]), cwd=ROOT)
    return main(family)
