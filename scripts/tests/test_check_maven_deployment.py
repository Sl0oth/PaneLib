import os
from pathlib import Path
import sys
import unittest
import urllib.request
from unittest.mock import patch
from urllib.error import HTTPError, URLError

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_maven_deployment as guard


def catalog(version='1.0.0'):
    return {'schemaVersion': 1, 'releases': [{'version': version,
        'url': f'https://github.com/Sl0oth/PaneLib/releases/download/v{version}/panelib-{version}-maven.zip',
        'sha256': 'a' * 64, 'size': 100}]}


class DeploymentGuardTests(unittest.TestCase):
    def test_redirect_stops_before_authorization_can_be_forwarded(self):
        request = urllib.request.Request(guard.MAIN_URL, headers={'Authorization': 'Bearer synthetic-test'})
        handler = guard.NoRedirect()
        with self.assertRaisesRegex(ValueError, 'refuses redirects'):
            handler.redirect_request(request, None, 302, 'Found', {}, 'https://unrelated.invalid/')

    def test_only_empty_bootstrap_can_accept_missing_live_catalog(self):
        missing = HTTPError(guard.CATALOG_URL, 404, 'missing', {}, None)
        with patch.object(guard, 'fetch_json', side_effect=missing):
            self.assertEqual(guard.baseline({'schemaVersion': 1, 'releases': []})['releases'], [])
            with self.assertRaisesRegex(ValueError, 'empty bootstrap'):
                guard.baseline(catalog())

    def test_network_failure_never_becomes_empty_history(self):
        for error in [URLError('offline'), HTTPError(guard.CATALOG_URL, 503, 'failed', {}, None)]:
            with self.subTest(error=error), patch.object(guard, 'fetch_json', side_effect=error):
                with self.assertRaises((ValueError, URLError)):
                    guard.baseline({'schemaVersion': 1, 'releases': []})

    def test_live_release_cannot_be_deleted_or_changed(self):
        old = catalog()
        changed = catalog(); changed['releases'][0]['sha256'] = 'b' * 64
        with patch.object(guard, 'fetch_json', return_value=old):
            for proposed in [{'schemaVersion': 1, 'releases': []}, changed]:
                with self.subTest(proposed=proposed), self.assertRaises((ValueError, RuntimeError)):
                    guard.baseline(proposed)
            self.assertEqual(guard.baseline(catalog()), old)

    def test_stale_or_non_main_workflow_is_rejected(self):
        sha = 'a' * 40
        env = {'GITHUB_REPOSITORY': 'Sl0oth/PaneLib', 'GITHUB_REF': 'refs/heads/main', 'GITHUB_SHA': sha}
        with patch.dict(os.environ, env, clear=True), patch.object(guard, 'fetch_json', return_value={
                'ref': 'refs/heads/main', 'object': {'sha': sha}}) as fetch:
            self.assertEqual(guard.current_main(), sha)
            fetch.return_value['object']['sha'] = 'b' * 40
            with self.assertRaisesRegex(ValueError, 'stale'):
                guard.current_main()
        env['GITHUB_REF'] = 'refs/heads/old'
        with patch.dict(os.environ, env, clear=True), self.assertRaisesRegex(ValueError, 'main'):
            guard.current_main()


if __name__ == '__main__':
    unittest.main()
