"""Synthetic release fixtures: no Gradle, network, personal files or publication."""
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import time
import unittest
import warnings
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import prepare_release as release


class ReleaseFixture(unittest.TestCase):
    version = '1.2.3-beta.4'

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='panelib-release-test-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.license = b'MIT License\nSynthetic fixture\n'
        self.put('LICENSE', self.license)
        self.put('gradle.properties', f'version={self.version}\ngroup=dev.pane\nminecraft_version=26.2\n'
                 'loader_version=0.19.3\nfabric_api_version=0.157.0+26.2\n')
        self.put('README.md', f'Use `dev.pane:panelib:{self.version}` from Maven.\n')
        self.put('build.gradle', '// synthetic build configuration\n')
        self.put('pane/build.gradle', '// synthetic library configuration\n')
        self.put('docs/release-notes.md', '# PaneLib {{version}}\nLocal candidate, not published.\n')
        source = b'package dev.pane.ui;\npublic class UiScreen {}\n'
        self.put('pane/src/main/java/dev/pane/ui/UiScreen.java', source)
        self.put('pane/src/main/java/dev/pane/mcef/Excluded.java', b'ignored browser prototype')
        self.meta = {'schemaVersion': 1, 'id': 'panelib', 'version': '${version}', 'license': 'MIT',
                     'environment': 'client', 'depends': {'java': '>=25', 'minecraft': '~26.2',
                         'fabricloader': '>=0.19.3', 'fabric-api': '*'}}
        raw_meta = release.json_bytes(self.meta)
        self.put('pane/src/main/resources/fabric.mod.json', raw_meta)
        self.put('pane/build/resources/main/fabric.mod.json', raw_meta.replace(b'${version}', self.version.encode()))
        self.put('pane/build/classes/java/main/dev/pane/ui/UiScreen.class', b'synthetic bytecode')
        docs = {'index.html': b'<html>API reference</html>',
                'dev/pane/ui/UiScreen.html': b'<html>UiScreen</html>'}
        for name, data in docs.items():
            self.put('pane/build/docs/javadoc/' + name, data)
        runtime = {'dev/pane/ui/UiScreen.class': b'synthetic bytecode', 'LICENSE': self.license,
                   'fabric.mod.json': raw_meta.replace(b'${version}', self.version.encode())}
        sources = {'dev/pane/ui/UiScreen.java': source, 'LICENSE': self.license, 'fabric.mod.json': raw_meta}
        self.archives = {'runtime': runtime, 'sources': sources, 'javadoc': {**docs, 'LICENSE': self.license}}
        self.names = release.artifact_names(self.version)
        for role, entries in self.archives.items():
            self.put('pane/build/libs/' + self.names[role], release.deterministic_zip(entries))
        self.put('pane-demo/build.gradle', '// synthetic demo configuration\n')
        self.put('pane-demo/src/main/java/dev/pane/demo/PaneDemoClient.java', b'package dev.pane.demo; class PaneDemoClient {}')
        self.demo_meta = {'schemaVersion': 1, 'id': 'pane_demo', 'version': self.version, 'environment': 'client',
                          'depends': {'panelib': '>=' + self.version}}
        self.write_demo({})
        starter = 'examples/first-screen/'
        for name in release.STARTER_FILES:
            self.put(starter + name, b'fixture\n')
        self.put(starter + 'LICENSE', self.license)
        self.put(starter + 'README.md', b'# Starter {{pane_version}}\n')
        self.put(starter + 'src/main/java/example/pane/FirstScreenClient.java', b'package example.pane; class FirstScreenClient {}')
        self.put(starter + 'src/main/resources/fabric.mod.json', b'{"id":"pane_example"}')
        self.put(starter + 'libs/panelib-OLD.jar', b'preserve this convenience file')
        self.put(starter + 'build/private-compile.txt', b'not distributable')
        self.put(starter + '.env', b'SECRET=never include')
        self.make_maven()
        self.freshen()

    def put(self, relative, data):
        target = self.root / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data.encode() if isinstance(data, str) else data)
        return target

    def write_demo(self, extra):
        entries = {'dev/pane/demo/PaneDemoClient.class': b'synthetic demo bytecode', 'LICENSE': self.license,
                   'fabric.mod.json': release.json_bytes(self.demo_meta),
                   'assets/pane_demo/lang/en_us.json': b'{}', **extra}
        self.put('pane-demo/build/libs/' + self.names['demo'], release.deterministic_zip(entries))

    def freshen(self):
        when = time.time_ns() + 1_000_000_000
        for base in ('pane/build/classes', 'pane/build/libs', 'pane-demo/build/libs'):
            for path in (self.root / base).rglob('*'):
                if path.is_file():
                    os.utime(path, ns=(when, when))

    def make_maven(self):
        coordinate = 'build/maven/dev/pane/panelib/'
        directory = coordinate + self.version + '/'
        base = 'panelib-' + self.version
        pom = f'''<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>dev.pane</groupId><artifactId>panelib</artifactId><version>{self.version}</version>
<dependencies><dependency><groupId>net.fabricmc</groupId><artifactId>fabric-loader</artifactId><version>0.19.3</version><scope>runtime</scope></dependency>
<dependency><groupId>net.fabricmc.fabric-api</groupId><artifactId>fabric-api</artifactId><version>0.157.0+26.2</version><scope>runtime</scope></dependency></dependencies></project>'''.encode()
        module = {'formatVersion': '1.1', 'component': {'group': 'dev.pane', 'module': 'panelib', 'version': self.version}, 'variants': []}
        files = {self.names[role]: (self.root / 'pane/build/libs' / self.names[role]).read_bytes()
                 for role in ('runtime', 'sources', 'javadoc')}
        for name, role in [('apiElements', 'runtime'), ('runtimeElements', 'runtime'),
                           ('sourcesElements', 'sources'), ('javadocElements', 'javadoc')]:
            filename = self.names[role]
            data = files[filename]
            variant = {'name': name, 'attributes': {'org.gradle.jvm.version': 25},
                       'files': [{'name': filename, 'url': filename, 'size': len(data),
                                  **{alg: release.digest(data, alg) for alg in release.HASHES}}]}
            if name == 'runtimeElements':
                variant['dependencies'] = [{'group': 'net.fabricmc', 'module': 'fabric-loader', 'version': {'requires': '0.19.3'}},
                    {'group': 'net.fabricmc.fabric-api', 'module': 'fabric-api', 'version': {'requires': '0.157.0+26.2'}}]
            module['variants'].append(variant)
        files[base + '.pom'] = pom
        files[base + '.module'] = release.json_bytes(module)
        for name, data in files.items():
            self.put(directory + name, data)
            for algorithm in release.HASHES:
                self.put(directory + name + '.' + algorithm, release.digest(data, algorithm))
        index = release.maven_index('dev.pane', self.version)
        self.put(coordinate + 'maven-metadata.xml', index)
        for algorithm in release.HASHES:
            self.put(coordinate + 'maven-metadata.xml.' + algorithm, release.digest(index, algorithm))
        self.put(coordinate + 'old/private.txt', b'not distributable')

    def package(self):
        return release.stage(self.root)[0]

    def checksum(self, directory):
        entries = release.tree_files(directory)
        (directory / release.CHECKSUMS).write_text(''.join(f'{release.digest(body)}  {name}\n'
            for name, body in sorted(entries.items()) if name != release.CHECKSUMS))

    def test_stage_verify_and_identical_rerun(self):
        directory, created = release.stage(self.root)
        self.assertTrue(created)
        before = {name: (directory / name).stat().st_mtime_ns for name in release.tree_files(directory)}
        manifest = release.verify_directory(directory)
        self.assertEqual(manifest['version'], self.version)
        self.assertEqual(manifest['status'], 'local-candidate-not-published')
        self.assertFalse(release.stage(self.root)[1])
        self.assertEqual(before, {name: (directory / name).stat().st_mtime_ns for name in before})

    def test_changed_same_version_cannot_overwrite_but_fresh_output_allowed(self):
        original = self.package()
        old = release.tree_files(original)
        self.put('docs/release-notes.md', '# {{version}}\nUpdated release notes\n')
        with self.assertRaisesRegex(release.ReleaseError, 'Immutable candidate differs'):
            release.stage(self.root)
        candidate, created = release.stage(self.root, self.root / 'build/release-second-candidate')
        self.assertTrue(created)
        self.assertEqual(release.tree_files(original), old)
        self.assertNotEqual(release.tree_files(candidate), old)

    def test_demo_ships_beside_the_library_without_bundling_it(self):
        output = self.package()
        self.assertTrue((output / self.names['demo']).is_file())
        release.verify_directory(output)
        for extra, message in (({'dev/pane/ui/UiScreen.class': b'copy'}, 'bundles a library class'),
                               ({'secrets/.env': b'x'}, 'Unexpected demo content')):
            with self.subTest(message=message):
                self.write_demo(extra)
                self.freshen()
                with self.assertRaisesRegex(release.ReleaseError, message):
                    release.assemble(self.root)
        self.write_demo({})
        self.demo_meta['depends'] = {'panelib': '*'}
        self.write_demo({})
        self.freshen()
        with self.assertRaisesRegex(release.ReleaseError, 'require this PaneLib version'):
            release.assemble(self.root)

    def test_upload_set_holds_only_attached_files_and_their_checksums(self):
        output = self.package()
        upload = release.upload_set(output, self.root / 'upload')
        attached = {self.names[role] for role in release.UPLOAD_ROLES}
        self.assertEqual(set(release.tree_files(upload)), attached | {release.CHECKSUMS})
        sums = (upload / release.CHECKSUMS).read_text().splitlines()
        self.assertEqual(len(sums), len(attached))
        for line in sums:
            checksum, name = line.split('  ')
            self.assertEqual(release.digest((output / name).read_bytes()), checksum)
        with self.assertRaisesRegex(release.ReleaseError, 'must not exist'):
            release.upload_set(output, upload)

    def test_starter_allowlist_descriptor_runtime_and_source_no_mutation(self):
        before = release.tree_files(self.root / 'examples/first-screen')
        output = self.package()
        self.assertEqual(before, release.tree_files(self.root / 'examples/first-screen'))
        entries = release.zip_entries((output / self.names['starter']).read_bytes(), 'starter')
        self.assertEqual(entries['pane-first-screen/panelib-release.properties'], f'pane_version={self.version}\n'.encode())
        self.assertEqual(entries['pane-first-screen/libs/' + self.names['runtime']], (output / self.names['runtime']).read_bytes())
        self.assertFalse(any('OLD' in name or 'private' in name or '.env' in name or '/build/' in name for name in entries))
        self.assertEqual(entries['pane-first-screen/README.md'], f'# Starter {self.version}\n'.encode())

    def test_maven_zip_contains_only_current_coordinate_and_single_version_index(self):
        output = self.package()
        entries = release.zip_entries((output / self.names['maven-repository']).read_bytes(), 'repo')
        prefix = f'panelib-maven-{self.version}/dev/pane/panelib/'
        self.assertTrue(all(name.startswith(prefix) for name in entries))
        self.assertFalse(any('old/' in name for name in entries))
        self.assertEqual(entries[prefix + 'maven-metadata.xml'], release.maven_index('dev.pane', self.version))

    def test_deterministic_zip_order_time_permissions(self):
        output = self.package()
        for role in ('starter', 'maven-repository'):
            with zipfile.ZipFile(output / self.names[role]) as archive:
                self.assertEqual(archive.namelist(), sorted(archive.namelist()))
                self.assertTrue(all(info.date_time == release.STAMP for info in archive.infolist()))
                if role == 'starter':
                    self.assertEqual(archive.getinfo('pane-first-screen/gradlew').external_attr >> 16, 0o100755)

    def test_corrupt_archive_is_rejected_before_output_created(self):
        self.put('pane/build/libs/' + self.names['runtime'], b'not a ZIP')
        with self.assertRaises(release.ReleaseError):
            release.stage(self.root)
        self.assertFalse((self.root / 'build/release').exists())

    def test_readme_coordinate_drift_is_rejected(self):
        self.put('README.md', f'Use `dev.pane:panelib:{self.version}`, not `dev.pane:panelib:old`.\n')
        with self.assertRaisesRegex(release.ReleaseError, 'README coordinate'):
            release.stage(self.root)

    def test_build_freshness_rejects_source_newer_than_compilation(self):
        source = self.root / 'pane/src/main/java/dev/pane/ui/UiScreen.java'
        future = time.time_ns() + 10_000_000_000
        os.utime(source, ns=(future, future))
        with self.assertRaisesRegex(release.ReleaseError, 'predates'):
            release.stage(self.root)

    def test_unexpected_library_resource_is_rejected(self):
        self.put('pane/src/main/resources/private.env', b'SECRET=1')
        with self.assertRaisesRegex(release.ReleaseError, 'Unexpected library resource'):
            release.stage(self.root)

    def test_runtime_version_mismatch_is_rejected(self):
        entries = dict(self.archives['runtime'])
        entries['fabric.mod.json'] = entries['fabric.mod.json'].replace(self.version.encode(), b'older')
        self.put('pane/build/libs/' + self.names['runtime'], release.deterministic_zip(entries))
        with self.assertRaisesRegex(release.ReleaseError, 'version'):
            release.stage(self.root)

    def test_stale_source_jar_and_stale_compiled_classes_are_rejected(self):
        self.put('pane/src/main/java/dev/pane/ui/UiScreen.java', 'package dev.pane.ui; public class UiScreen { int added; }')
        with self.assertRaisesRegex(release.ReleaseError, 'Sources JAR'):
            release.stage(self.root)
        self.put('pane/src/main/java/dev/pane/ui/UiScreen.java', self.archives['sources']['dev/pane/ui/UiScreen.java'])
        self.put('pane/build/classes/java/main/dev/pane/ui/UiScreen.class', b'new bytecode not packaged')
        with self.assertRaisesRegex(release.ReleaseError, 'Runtime JAR'):
            release.stage(self.root)

    def test_stale_generated_javadoc_is_rejected(self):
        self.put('pane/build/docs/javadoc/dev/pane/ui/UiScreen.html', b'<html>Changed API</html>')
        with self.assertRaisesRegex(release.ReleaseError, 'Javadoc JAR'):
            release.stage(self.root)

    def test_unexpected_starter_source_content_is_rejected(self):
        self.put('examples/first-screen/src/main/resources/private.env', b'PRIVATE=true')
        with self.assertRaisesRegex(release.ReleaseError, 'Unexpected starter'):
            release.stage(self.root)

    def test_symlinked_allowed_starter_input_is_rejected(self):
        target = self.root / 'examples/first-screen/README.md'
        target.unlink()
        target.symlink_to(self.root / 'LICENSE')
        with self.assertRaisesRegex(release.ReleaseError, 'symlink'):
            release.stage(self.root)

    def test_zip_traversal_duplicate_and_symlink_rejected(self):
        for dangerous in ('../escape', '/absolute', 'x\\escape', 'C:/private'):
            with self.subTest(name=dangerous), self.assertRaises(release.ReleaseError):
                release.deterministic_zip({dangerous: b'bad'})
        duplicates = io.BytesIO()
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(duplicates, 'w') as archive:
                archive.writestr('same.txt', 'first')
                archive.writestr('same.txt', 'second')
        with self.assertRaisesRegex(release.ReleaseError, 'Duplicate ZIP member'):
            release.zip_entries(duplicates.getvalue(), 'duplicate fixture')
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, 'w') as archive:
            info = zipfile.ZipInfo('safe-name')
            info.external_attr = 0o120777 << 16
            archive.writestr(info, 'target')
        with self.assertRaisesRegex(release.ReleaseError, 'Symlink'):
            release.zip_entries(stream.getvalue(), 'malicious')

    def test_private_paths_in_distributable_text_are_rejected(self):
        self.put('examples/first-screen/README.md', '/Users/privateperson/secrets/config')
        with self.assertRaisesRegex(release.ReleaseError, 'Private filesystem path'):
            release.stage(self.root)

    def test_checksum_tamper_and_unlisted_payload_rejected(self):
        output = self.package()
        notes = output / 'RELEASE_NOTES.md'
        original = notes.read_bytes()
        notes.write_bytes(original + b'changed')
        with self.assertRaisesRegex(release.ReleaseError, 'Checksum mismatch'):
            release.verify_directory(output)
        notes.write_bytes(original)
        (output / 'extra-private.txt').write_text('unexpected')
        with self.assertRaisesRegex(release.ReleaseError, 'cover every payload'):
            release.verify_directory(output)

    def test_manifest_hash_tamper_rejected_even_with_recomputed_checksums(self):
        output = self.package()
        manifest = json.loads((output / release.MANIFEST).read_bytes())
        manifest['artifacts'][0]['sha256'] = '0' * 64
        (output / release.MANIFEST).write_bytes(release.json_bytes(manifest))
        self.checksum(output)
        with self.assertRaisesRegex(release.ReleaseError, 'Manifest digest mismatch'):
            release.verify_directory(output)

    def test_maven_runtime_byte_or_checksum_mismatch_rejected(self):
        name = f'build/maven/dev/pane/panelib/{self.version}/' + self.names['runtime']
        self.put(name, b'wrong artifact')
        with self.assertRaisesRegex(release.ReleaseError, 'Maven artifact bytes'):
            release.stage(self.root)
        self.make_maven()
        self.put(name + '.sha256', '0' * 64)
        with self.assertRaisesRegex(release.ReleaseError, 'Maven sidecar'):
            release.stage(self.root)

    def test_maven_version_and_unexpected_private_files_rejected(self):
        name = f'build/maven/dev/pane/panelib/{self.version}/panelib-{self.version}.pom'
        data = (self.root / name).read_bytes().replace(self.version.encode(), b'stale')
        self.put(name, data)
        with self.assertRaisesRegex(release.ReleaseError, 'POM coordinate/version'):
            release.stage(self.root)
        self.make_maven()
        self.put(f'build/maven/dev/pane/panelib/{self.version}/private.env', b'secret')
        with self.assertRaisesRegex(release.ReleaseError, 'Unexpected/missing'):
            release.stage(self.root)

    def test_protected_output_locations_are_never_written(self):
        for path in ('release-snapshots/new', 'examples/first-screen/candidate', 'pane/build/candidate', 'build/release'):
            with self.subTest(path=path), self.assertRaises(release.ReleaseError):
                release.stage(self.root, self.root / path)

    def test_failed_assembly_leaves_existing_release_untouched(self):
        output = self.package()
        before = release.tree_files(output)
        self.put('docs/release-notes.md', '# no required version token\n')
        with self.assertRaises(release.ReleaseError):
            release.stage(self.root)
        self.assertEqual(before, release.tree_files(output))

    def test_verify_is_self_contained_after_checkout_inputs_removed(self):
        output = self.package()
        (self.root / 'gradle.properties').unlink()
        (self.root / 'pane/build/libs' / self.names['runtime']).unlink()
        self.assertEqual(release.verify_directory(output)['version'], self.version)


if __name__ == '__main__':
    unittest.main()
