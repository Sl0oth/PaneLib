"""Offline synthetic Maven hosting fixtures; no publication or network requests."""
import copy
import io
import json
from pathlib import Path
import stat
import sys
import tempfile
import unittest
from unittest import mock
import urllib.error
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build_maven_site as site
import prepare_release as release
import test_prepare_release as fixtures


class MavenSiteTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory(prefix='panelib-maven-site-test-')
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name).resolve()
        self.assets = self.root / 'assets'
        self.assets.mkdir()
        self.output = self.root / 'site'
        self.first = self.make_asset('1.2.3-beta.4')
        self.catalog = {'schemaVersion': 1, 'releases': [self.first]}

    def make_asset(self, version):
        fixture = fixtures.ReleaseFixture()
        fixture.version = version
        fixture.setUp()
        self.addCleanup(fixture.doCleanups)
        binaries = {fixture.names[role]: (fixture.root / 'pane/build/libs' / fixture.names[role]).read_bytes()
                    for role in ('runtime', 'sources', 'javadoc')}
        data = release.maven_zip(fixture.root, release.release_config(fixture.root), binaries)
        return self.save_asset(version, data)

    def save_asset(self, version, data):
        (self.assets / site.asset_name(version)).write_bytes(data)
        return {'version': version, 'url': site.asset_url(version), 'sha256': release.digest(data), 'size': len(data)}

    def build(self, catalog=None, previous=None, output=None):
        return site.build_site(catalog or self.catalog, output or self.output,
                               previous_catalog=previous or site.EMPTY_CATALOG, asset_dir=self.assets)

    def mutate_zip(self, mutate):
        version = self.first['version']
        entries = release.zip_entries((self.assets / site.asset_name(version)).read_bytes(), 'fixture')
        mutate(entries)
        self.catalog['releases'][0] = self.save_asset(version, release.deterministic_zip(entries))

    def test_multiple_versions_metadata_and_identity_parity(self):
        second = self.make_asset('1.2.3-beta.5')
        catalog = {'schemaVersion': 1, 'releases': [self.first, second]}
        self.build(catalog, previous=self.catalog)
        for entry in catalog['releases']:
            original = site.validate_asset((self.assets / site.asset_name(entry['version'])).read_bytes(), entry['version'])
            for name, body in original.items():
                self.assertEqual((self.output / name).read_bytes(), body)
        metadata = (self.output / site.COORDINATE / 'maven-metadata.xml').read_bytes()
        parsed = release.parse_xml(metadata, 'combined')
        self.assertEqual([node.text for node in parsed.findall('versioning/versions/version')],
                         ['1.2.3-beta.4', '1.2.3-beta.5'])
        self.assertEqual(parsed.findtext('versioning/latest'), second['version'])
        for algorithm in release.HASHES:
            self.assertEqual((self.output / site.COORDINATE / ('maven-metadata.xml.' + algorithm)).read_text().strip(),
                             release.digest(metadata, algorithm))
        self.assertEqual(site.load_catalog(self.output / 'catalog.json'), catalog)

    def test_byte_deterministic_and_single_version_metadata_parity(self):
        self.build()
        other = self.root / 'other'
        self.build(output=other)
        self.assertEqual(release.tree_files(self.output), release.tree_files(other))
        self.assertEqual((self.output / site.COORDINATE / 'maven-metadata.xml').read_bytes(),
                         release.maven_index(site.GROUP, self.first['version']))

    def test_empty_catalog_has_no_coordinate_or_fictional_release(self):
        self.build(site.EMPTY_CATALOG)
        self.assertEqual(set(release.tree_files(self.output)), {'catalog.json', 'index.html'})
        self.assertIn(b'no releases', (self.output / 'index.html').read_bytes())

    def test_append_only_rejects_removal_reordering_or_mutation(self):
        second = self.make_asset('1.2.3-beta.5')
        prior = {'schemaVersion': 1, 'releases': [self.first, second]}
        proposals = [site.EMPTY_CATALOG, self.catalog, {'schemaVersion': 1, 'releases': [second, self.first]}]
        for key, value in [('sha256', '0' * 64), ('size', self.first['size'] + 1), ('url', 'https://example.com/a')]:
            changed = copy.deepcopy(prior)
            changed['releases'][0][key] = value
            proposals.append(changed)
        for proposed in proposals:
            with self.subTest(proposed=proposed), self.assertRaises(release.ReleaseError):
                site.validate_append_only(prior, proposed)

    def test_strict_catalog_validation(self):
        invalid = [{'schemaVersion': True, 'releases': []}, {'schemaVersion': 1, 'releases': [], 'extra': 1},
                   {'schemaVersion': 1, 'releases': [self.first, self.first]}]
        for key, value in [('size', True), ('size', site.MAX_ASSET_BYTES + 1), ('sha256', 'A' * 64),
                           ('version', '1.2.3-SNAPSHOT'), ('version', '../1.2.3'), ('url', 'file:///tmp/a'),
                           ('url', self.first['url'].replace('Sl0oth', 'other'))]:
            changed = copy.deepcopy(self.catalog)
            changed['releases'][0][key] = value
            invalid.append(changed)
        for proposed in invalid:
            with self.subTest(proposed=proposed), self.assertRaises(release.ReleaseError):
                site.validate_catalog(proposed)
        path = self.root / 'duplicate.json'
        path.write_text('{"schemaVersion":1,"schemaVersion":1,"releases":[]}')
        with self.assertRaisesRegex(release.ReleaseError, 'Duplicate JSON'):
            site.load_catalog(path)

    def test_wrong_hash_fails_without_network_fallback_or_output(self):
        self.catalog['releases'][0]['sha256'] = '0' * 64
        with mock.patch.object(site.urllib.request, 'build_opener') as network:
            with self.assertRaisesRegex(release.ReleaseError, 'sha256 mismatch'):
                self.build()
            network.assert_not_called()
        self.assertFalse(self.output.exists())
        self.assertFalse(list(self.root.glob('.panelib-maven-*')))

    def test_network_failure_leaves_no_output(self):
        with mock.patch.object(site.urllib.request, 'build_opener') as factory:
            factory.return_value.open.side_effect = urllib.error.URLError('offline fixture')
            with self.assertRaisesRegex(release.ReleaseError, 'download failed'):
                site.build_site(self.catalog, self.output, previous_catalog=site.EMPTY_CATALOG)
        self.assertFalse(self.output.exists())

    def test_redirect_policy_rejects_untrusted_and_cleartext_targets(self):
        for url in ('http://github.com/a', 'https://example.com/a', 'https://github.com:444/a',
                    'https://user:pass@github.com/a'):
            with self.subTest(url=url), self.assertRaises(release.ReleaseError):
                site.ReleaseRedirect().redirect_request(None, None, 302, '', {}, url)

    def test_unrelated_output_and_symlink_paths_preserved(self):
        self.output.mkdir()
        marker = self.output / 'keep.txt'
        marker.write_text('keep')
        with self.assertRaisesRegex(release.ReleaseError, 'absent or empty'):
            self.build()
        self.assertEqual(marker.read_text(), 'keep')
        linked = self.root / 'linked'
        linked.symlink_to(self.output, target_is_directory=True)
        with self.assertRaisesRegex(release.ReleaseError, 'Symlinked'):
            self.build(output=linked)
        with self.assertRaisesRegex(release.ReleaseError, 'Symlinked'):
            self.build(output=linked / 'child')

    def test_symlink_cache_rejected(self):
        source = self.assets / site.asset_name(self.first['version'])
        real = self.assets / 'real.zip'
        source.rename(real)
        source.symlink_to(real)
        with self.assertRaisesRegex(release.ReleaseError, 'Symlinked'):
            self.build()

    def test_extra_file_and_checksum_corruption_rejected(self):
        self.mutate_zip(lambda entries: entries.update({'extra.txt': b'bad'}))
        with self.assertRaisesRegex(release.ReleaseError, 'unexpected files'):
            self.build()
        self.first = self.make_asset('1.2.3-beta.4')
        self.catalog['releases'][0] = self.first
        def corrupt(entries):
            key = next(key for key in entries if key.endswith('.jar.sha256'))
            entries[key] = b'0' * 64
        self.mutate_zip(corrupt)
        with self.assertRaisesRegex(release.ReleaseError, 'sidecar'):
            self.build()

    def test_zip_traversal_symlinks_and_expansion_rejected(self):
        for name, mode in [('../escape', 0o100644), ('link', stat.S_IFLNK | 0o777)]:
            stream = io.BytesIO()
            with zipfile.ZipFile(stream, 'w') as archive:
                info = zipfile.ZipInfo(name)
                info.external_attr = mode << 16
                archive.writestr(info, b'fixture')
            with self.subTest(name=name), self.assertRaises(release.ReleaseError):
                site.bounded_zip(stream.getvalue(), 'bad fixture')
        with mock.patch.object(site, 'MAX_EXPANDED_BYTES', 1):
            with self.assertRaisesRegex(release.ReleaseError, 'Expanded ZIP'):
                self.build()

    def test_nested_private_path_is_rejected(self):
        def contaminate(entries):
            key = next(key for key in entries if key.endswith('-javadoc.jar'))
            archive = release.zip_entries(entries[key], 'docs fixture')
            # Deliberately use an unrecognized suffix to exercise the exhaustive scan.
            archive['private.dat'] = b'/Users/example/private/project'
            entries[key] = release.deterministic_zip(archive)
        self.mutate_zip(contaminate)
        with self.assertRaisesRegex(release.ReleaseError, 'Private filesystem path'):
            self.build()

    def test_module_hash_and_dependency_mismatch_rejected(self):
        for field in ('hash', 'dependency'):
            self.first = self.make_asset('1.2.3-beta.4')
            self.catalog['releases'][0] = self.first
            def corrupt(entries):
                key = next(key for key in entries if key.endswith('.module'))
                module = json.loads(entries[key])
                if field == 'hash':
                    module['variants'][0]['files'][0]['sha256'] = '0' * 64
                else:
                    module['variants'][1]['dependencies'][0]['version']['requires'] = '0.0.0'
                entries[key] = release.json_bytes(module)
                for algorithm in release.HASHES:
                    entries[key + '.' + algorithm] = release.digest(entries[key], algorithm).encode()
            self.mutate_zip(corrupt)
            with self.subTest(field=field), self.assertRaises(release.ReleaseError):
                self.build()

    def test_atomic_failure_site_budget_and_empty_output(self):
        self.output.mkdir()
        with mock.patch.object(site, 'MAX_SITE_BYTES', 10):
            with self.assertRaisesRegex(release.ReleaseError, 'site exceeds'):
                self.build()
        self.assertEqual(list(self.output.iterdir()), [])
        self.assertFalse(list(self.root.glob('.panelib-maven-*')))
        self.build()
        self.assertTrue((self.output / 'catalog.json').is_file())

    def test_cli_requires_explicit_previous_baseline(self):
        with self.assertRaises(SystemExit) as error:
            site.main(['--catalog', 'absent', '--output', 'absent'])
        self.assertEqual(error.exception.code, 2)

    def test_empty_output_accepts_windows_directory_rename_semantics(self):
        self.output.mkdir()
        original_replace = site.os.replace
        def windows_replace(source, destination):
            if Path(destination).exists():
                raise PermissionError('Windows cannot replace an existing directory')
            self.assertEqual(site.load_catalog(Path(source) / 'catalog.json'), self.catalog)
            return original_replace(source, destination)
        with mock.patch.object(site.os, 'replace', side_effect=windows_replace) as replace:
            self.build()
        replace.assert_called_once()
        self.assertEqual(site.load_catalog(self.output / 'catalog.json'), self.catalog)
        self.assertFalse(list(self.root.glob('.panelib-maven-*')))

    def test_failed_final_rename_preserves_prior_empty_or_absent_output(self):
        for existed in (False, True):
            with self.subTest(existing_empty_output=existed):
                if existed:
                    self.output.mkdir()
                with mock.patch.object(site.os, 'replace', side_effect=PermissionError('rename denied')):
                    with self.assertRaisesRegex(PermissionError, 'rename denied'):
                        self.build()
                self.assertEqual(self.output.exists(), existed)
                if existed:
                    self.assertEqual(list(self.output.iterdir()), [])
                self.assertFalse(list(self.root.glob('.panelib-maven-*')))

    def test_concurrent_content_before_placeholder_removal_is_preserved(self):
        self.output.mkdir()
        original_rmdir = Path.rmdir
        marker = self.output / 'keep.txt'
        def write_before_removal(path):
            if path == self.output:
                marker.write_text('concurrent content')
            return original_rmdir(path)
        with mock.patch.object(Path, 'rmdir', autospec=True, side_effect=write_before_removal):
            with self.assertRaises(OSError):
                self.build()
        self.assertEqual(marker.read_text(), 'concurrent content')
        self.assertFalse(list(self.root.glob('.panelib-maven-*')))


if __name__ == '__main__':
    unittest.main()
