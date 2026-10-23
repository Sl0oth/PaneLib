"""Verify the assembled starter and Maven payload as separate binary-only consumers.

No publication. --smoke additionally launches two disposable Minecraft clients, one at a time.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import signal
import time
import shutil
import subprocess
import sys
import uuid
import zipfile
import ssl
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parent.parent


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


class NoEndpointRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError('Unexpected redirect while verifying repository bytes.')


def verify_endpoint(candidate, version, address, ca_file=None):
    """Read the exact immutable version payload anonymously; never trust a Gradle cache alone."""
    url = urllib.parse.urlsplit(address)
    if url.scheme != 'https' or not url.hostname or url.username or url.password or url.query or url.fragment:
        raise ValueError('Repository endpoint must be HTTPS without credentials, query or fragment.')
    context = ssl.create_default_context(cafile=str(ca_file) if ca_file else None)
    opener = urllib.request.build_opener(NoEndpointRedirect(), urllib.request.HTTPSHandler(context=context))
    base = address.rstrip('/') + '/'
    prefix = f'panelib-maven-{version}/'
    count = 0
    # The assembled ZIP was verified first. Root metadata may include later published versions,
    # but all files under this version must still match the candidate byte-for-byte.
    with zipfile.ZipFile(candidate / f'panelib-{version}-maven.zip') as archive:
        for entry in archive.infolist():
            path = entry.filename.removeprefix(prefix)
            if f'/{version}/' not in path:
                continue
            expected = archive.read(entry)
            target = base + urllib.parse.quote(path, safe='/')
            request = urllib.request.Request(target, headers={'User-Agent': 'PaneLib-consumer-verification'})
            with opener.open(request, timeout=30) as response:
                if response.geturl() != target:
                    raise ValueError('Unexpected redirect while verifying repository bytes.')
                actual = response.read(len(expected) + 1)
            if actual != expected:
                raise ValueError('Repository bytes differ from candidate: ' + path)
            count += 1
    if not count:
        raise ValueError('No immutable Maven version files were checked.')
    return count


def extract(archive, destination):
    destination.mkdir(parents=True)
    with zipfile.ZipFile(archive) as z:
        for item in z.infolist():
            name = Path(item.filename)
            if name.is_absolute() or '..' in name.parts or '\\' in item.filename:
                raise ValueError('Unsafe archive path: ' + item.filename)
            if (item.external_attr >> 16) & 0o170000 == 0o120000:
                raise ValueError('Symlink in archive: ' + item.filename)
        z.extractall(destination)
        if os.name != 'nt':
            for item in z.infolist():
                if item.filename.endswith('/gradlew'):
                    (destination / item.filename).chmod(0o755)


def run(project, args, log, *, timeout=1200, shutdown_timeout=5):
    wrapper = project / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
    command = [str(wrapper), *args, '--no-daemon', '--max-workers=2']
    print('Consumer ' + log.stem, flush=True)
    # A Gradle wrapper can launch both a daemon JVM and Minecraft. Isolate the complete tree,
    # so cancellation cannot leave that client running or target an unrelated user's game.
    process_options = {'creationflags': getattr(subprocess, 'CREATE_NEW_PROCESS_GROUP', 0x200)} \
        if os.name == 'nt' else {'start_new_session': True}
    with log.open('w', encoding='utf-8') as output:
        process = subprocess.Popen(command, cwd=project, stdout=output, stderr=subprocess.STDOUT,
                                   **process_options)

        def stop_owned_tree():
            if os.name == 'nt':
                # taskkill /T follows only this wrapper's descendants. A new console group alone
                # does not make terminate()/kill() recursive on Windows.
                tree_error = None
                try:
                    killed = subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'],
                                            stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT,
                                            timeout=max(1, shutdown_timeout), check=False)
                    if killed.returncode and process.poll() is None:
                        tree_error = RuntimeError('taskkill could not stop the owned consumer process tree')
                except (OSError, subprocess.SubprocessError) as error:
                    tree_error = error
                finally:
                    if process.poll() is None:
                        process.kill()
                    process.wait(timeout=max(1, shutdown_timeout))
                if tree_error is not None:
                    raise RuntimeError('Owned wrapper stopped, but Windows descendant cleanup could not '
                                       'be confirmed; inspect the consumer log.') from tree_error
            else:
                group = process.pid  # start_new_session made this child the session/group leader.
                try:
                    os.killpg(group, signal.SIGTERM)
                except ProcessLookupError:
                    pass
                deadline = time.monotonic() + shutdown_timeout
                while time.monotonic() < deadline:
                    process.poll()  # reap the direct child while descendants finish gracefully
                    try:
                        os.killpg(group, 0)
                    except ProcessLookupError:
                        break
                    time.sleep(min(.05, max(0, deadline - time.monotonic())))
                # Do not stop at wrapper exit: a child may ignore TERM or outlive its parent.
                try:
                    os.killpg(group, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                process.wait(timeout=max(1, shutdown_timeout))

        try:
            returncode = process.wait(timeout=timeout)
        except BaseException:
            stop_owned_tree()
            raise
        if returncode:
            stop_owned_tree()
            raise RuntimeError(f'Consumer failed with exit {returncode}; see {log}')
    if '-PsmokeTest' in args:
        text = log.read_text(encoding='utf-8', errors='replace')
        passed = re.search(r'\[ConsumerTest\] complete PASS\b', text)
        failed = re.search(r'\[ConsumerTest\][^\r\n]*\bFAIL\b', text)
        if not passed or failed:
            raise RuntimeError('Consumer smoke did not pass: ' + str(log))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('candidate', type=Path)
    parser.add_argument('--smoke', action='store_true', help='Run actual Minecraft in each disposable consumer profile.')
    parser.add_argument('--repository-url', help='Verify and consume an anonymous HTTPS Maven endpoint instead of the local ZIP.')
    parser.add_argument('--ca-file', type=Path, help='Optional trusted CA bundle for HTTPS validation (e.g. an isolated local TLS test).')
    args = parser.parse_args()
    if args.ca_file and not args.repository_url:
        parser.error('--ca-file requires --repository-url')
    candidate = args.candidate.resolve()
    subprocess.run([sys.executable, str(ROOT / 'scripts/prepare_release.py'), '--verify', str(candidate)], check=True)
    # The verified manifest names the runtime; the demo and other archives share its prefix.
    manifest = json.loads((candidate / 'RELEASE_MANIFEST.json').read_text(encoding='utf-8'))
    runtimes = [item['file'] for item in manifest['artifacts'] if item['role'] == 'runtime']
    if len(runtimes) != 1:
        raise ValueError('Expected one runtime JAR')
    runtime = candidate / runtimes[0]
    with zipfile.ZipFile(runtime) as z:
        metadata = json.loads(z.read('fabric.mod.json'))
    version = metadata['version']
    checked_remote_files = verify_endpoint(candidate, version, args.repository_url, args.ca_file) if args.repository_url else 0
    work = ROOT / 'build/release-consumer' / (version + '-' + uuid.uuid4().hex)
    work.mkdir(parents=True)
    extract(candidate / f'panelib-{version}-starter.zip', work / 'file consumer')
    extract(candidate / f'panelib-{version}-starter.zip', work / 'maven consumer')
    extract(candidate / f'panelib-{version}-maven.zip', work / 'repository with spaces')
    file_project = work / 'file consumer/pane-first-screen'
    maven_project = work / 'maven consumer/pane-first-screen'
    # An absent libs directory proves Maven mode cannot silently select the bundled runtime.
    shutil.rmtree(maven_project / 'libs')
    repository = work / 'repository with spaces' / f'panelib-maven-{version}'
    maven_args = ['-PpaneRepository=' + (args.repository_url or repository.as_uri())]
    run(file_project, ['clean', 'build'], work / 'file-build.log')
    run(maven_project, ['clean', 'build', '-PbundlePane', *maven_args], work / 'maven-build.log')
    jar = maven_project / 'build/libs/pane-first-screen-1.0.0.jar'
    with zipfile.ZipFile(jar) as z:
        example_metadata = json.loads(z.read('fabric.mod.json'))
        nested = [n for n in z.namelist() if n.startswith('META-INF/jars/panelib-') and n.endswith('.jar')]
        if len(nested) != 1 or hashlib.sha256(z.read(nested[0])).hexdigest() != digest(runtime):
            raise RuntimeError('Bundled consumer library differs from candidate')
        if example_metadata['depends']['panelib'] != '=' + version:
            raise RuntimeError('Consumer metadata does not require the tested version')
    if args.smoke:
        run(file_project, ['runClient', '-PsmokeTest'], work / 'file-smoke.log')
        run(maven_project, ['runClient', '-PsmokeTest', *maven_args], work / 'maven-smoke.log')
    record = {'version': version, 'runtimeSha256': digest(runtime), 'fileConsumerBuild': True,
              'mavenConsumerBuild': True, 'nestedJarMatches': True, 'exactConsumerDependency': True,
              'smoke': 'PASS' if args.smoke else 'not run', 'workspace': str(work),
              'dependencyCachesReused': True, 'published': False}
    record['repositoryEndpoint'] = args.repository_url or repository.as_uri()
    record['anonymousHttpsFilesVerified'] = checked_remote_files
    (work / 'verification.json').write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps(record, indent=2), flush=True)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, RuntimeError, OSError, subprocess.SubprocessError, zipfile.BadZipFile) as error:
        print('Release consumer verification failed: ' + str(error), file=sys.stderr)
        raise SystemExit(1)
