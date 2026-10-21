"""Anonymous HTTPS endpoint checks with synthetic ZIPs and mocked transport only."""
import io
from pathlib import Path
import ssl
import socket
import sys
import tempfile
import unittest
from unittest import mock
import urllib.error
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import verify_release_consumer as consumer


class Response(io.BytesIO):
    def __init__(self, address, data):
        super().__init__(data)
        self.address = address
        self.bounds = []
        self.status = 200

    def geturl(self):
        return self.address

    def read(self, size=-1):
        self.bounds.append(size)
        return super().read(size)


class EndpointTests(unittest.TestCase):
    version = '1.2.3-beta.4'
    address = 'https://repository.example/PaneLib/maven'

    def setUp(self):
        socket_guard = mock.patch.object(socket, 'getaddrinfo', side_effect=AssertionError('Network forbidden in endpoint tests'))
        socket_guard.start()
        self.addCleanup(socket_guard.stop)
        temporary = tempfile.TemporaryDirectory(prefix='panelib-endpoint-test-')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        coordinate = f'dev/pane/panelib/{self.version}/'
        self.files = {coordinate + f'panelib-{self.version}.jar': b'synthetic runtime',
                      coordinate + f'panelib-{self.version}.pom': b'synthetic pom',
                      coordinate + f'panelib-{self.version}.jar.sha256': b'synthetic checksum'}
        self.archive = self.root / f'panelib-{self.version}-maven.zip'
        with zipfile.ZipFile(self.archive, 'w') as archive:
            prefix = f'panelib-maven-{self.version}/'
            for path, body in self.files.items():
                archive.writestr(prefix + path, body)
            archive.writestr(prefix + 'dev/pane/panelib/maven-metadata.xml', b'old single-version index')
        self.requests = []
        self.responses = []

    def transport(self, opener, request, *, timeout):
        context = next(handler._context for handler in opener.handlers
                       if isinstance(handler, consumer.urllib.request.HTTPSHandler))
        self.requests.append((request, context, timeout))
        relative = request.full_url.removeprefix(self.address + '/')
        response = Response(request.full_url, self.files[relative])
        self.responses.append(response)
        return response

    def test_all_version_files_checked_without_auth_and_with_verified_tls(self):
        with mock.patch.object(consumer.urllib.request.OpenerDirector, 'open', autospec=True, side_effect=self.transport):
            self.assertEqual(consumer.verify_endpoint(self.root, self.version, self.address + '/'), 3)
        self.assertEqual(len(self.requests), 3)
        for (request, context, timeout), response in zip(self.requests, self.responses):
            self.assertEqual(request.get_method(), 'GET')
            self.assertFalse(request.has_header('Authorization'))
            self.assertFalse(request.has_header('Cookie'))
            self.assertFalse(request.has_header('Proxy-Authorization'))
            self.assertTrue(context.check_hostname)
            self.assertEqual(context.verify_mode, ssl.CERT_REQUIRED)
            self.assertEqual(timeout, 30)
            self.assertEqual(len(response.bounds), 1)
            relative = request.full_url.removeprefix(self.address + '/')
            self.assertEqual(response.bounds[0], len(self.files[relative]) + 1)

    def test_credentials_cleartext_and_url_suffixes_rejected_before_network(self):
        invalid = ['http://repository.example/maven', 'file:///tmp/repository',
                   'https://user:secret@repository.example/maven', 'https:///maven',
                   self.address + '?token=secret', self.address + '#fragment']
        with mock.patch.object(consumer.urllib.request.OpenerDirector, 'open', autospec=True) as network:
            for address in invalid:
                with self.subTest(address=address), self.assertRaises(ValueError):
                    consumer.verify_endpoint(self.root, self.version, address)
            network.assert_not_called()

    def test_corrupt_truncated_and_oversized_remote_files_fail(self):
        for body in (b'wrong', b'', b'synthetic runtimeEXTRA'):
            def corrupt(opener, request, **kwargs):
                return Response(request.full_url, body)
            with self.subTest(body=body), mock.patch.object(consumer.urllib.request.OpenerDirector, 'open', autospec=True, side_effect=corrupt):
                with self.assertRaisesRegex(ValueError, 'bytes differ'):
                    consumer.verify_endpoint(self.root, self.version, self.address)

    def test_missing_auth_required_and_unavailable_files_fail(self):
        for code in (404, 401, 403, 503):
            error = urllib.error.HTTPError(self.address, code, 'fixture failure', {}, None)
            with self.subTest(code=code), mock.patch.object(consumer.urllib.request.OpenerDirector, 'open', autospec=True, side_effect=error):
                with self.assertRaises(urllib.error.HTTPError):
                    consumer.verify_endpoint(self.root, self.version, self.address)

    def test_tls_verification_failure_is_not_retried_insecurely(self):
        error = urllib.error.URLError(ssl.SSLCertVerificationError('untrusted fixture certificate'))
        with mock.patch.object(consumer.urllib.request.OpenerDirector, 'open', autospec=True, side_effect=error) as network:
            with self.assertRaises(urllib.error.URLError):
                consumer.verify_endpoint(self.root, self.version, self.address)
            self.assertEqual(network.call_count, 1)

    def test_redirected_response_cannot_count_as_endpoint_proof(self):
        with mock.patch.object(consumer.urllib.request.OpenerDirector, 'open', autospec=True, return_value=Response(
                'https://other.example/file', b'synthetic runtime')):
            with self.assertRaisesRegex(ValueError, 'redirect'):
                consumer.verify_endpoint(self.root, self.version, self.address)

    def test_redirect_handler_rejects_before_opening_redirect_target(self):
        handler = consumer.NoEndpointRedirect()
        parent = mock.Mock()
        handler.add_parent(parent)
        request = consumer.urllib.request.Request(self.address)
        with self.assertRaisesRegex(ValueError, 'redirect'):
            handler.http_error_302(request, io.BytesIO(), 302, 'redirect',
                                   {'location': 'http://other.example/untrusted'})
        parent.open.assert_not_called()

    def test_ca_bundle_is_passed_to_verifying_context(self):
        trusted = ssl.create_default_context()
        with mock.patch.object(consumer.ssl, 'create_default_context', return_value=trusted) as context, \
                mock.patch.object(consumer.urllib.request.OpenerDirector, 'open', autospec=True, side_effect=self.transport):
            consumer.verify_endpoint(self.root, self.version, self.address, Path('/fixture/ca.pem'))
        context.assert_called_once_with(cafile='/fixture/ca.pem')

    def test_empty_version_file_set_is_not_success(self):
        with zipfile.ZipFile(self.archive, 'w') as archive:
            archive.writestr(f'panelib-maven-{self.version}/dev/pane/panelib/maven-metadata.xml', b'index')
        with mock.patch.object(consumer.urllib.request.OpenerDirector, 'open', autospec=True) as network:
            with self.assertRaisesRegex(ValueError, 'No immutable'):
                consumer.verify_endpoint(self.root, self.version, self.address)
            network.assert_not_called()


if __name__ == '__main__':
    unittest.main()
