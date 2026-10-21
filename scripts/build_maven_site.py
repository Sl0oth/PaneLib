#!/usr/bin/env python3
"""Assemble the public Maven tree from reviewed, hash-pinned release ZIPs.

This command stages files only. It never creates releases or deploys a website.
The previous public catalog must be supplied explicitly, or --initial-empty used
only for the first deployment. Local assets are an offline validation facility.
"""
from __future__ import annotations

import argparse
import html
import io
import json
import os
from pathlib import Path
import re
import shutil
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import zipfile

import prepare_release as release

GROUP = 'dev.pane'
COORDINATE = 'dev/pane/panelib'
ENDPOINT = 'https://sl0oth.github.io/PaneLib/maven'
MAX_ASSET_BYTES = 64 * 1024 * 1024
MAX_EXPANDED_BYTES = 64 * 1024 * 1024
MAX_SITE_BYTES = 900 * 1024 * 1024
MAX_CATALOG_BYTES = 1024 * 1024
MAX_MEMBERS = 10000
EMPTY_CATALOG = {'schemaVersion': 1, 'releases': []}
require = release.require


def asset_name(version):
    return f'panelib-{version}-maven.zip'


def asset_url(version):
    return f'https://github.com/Sl0oth/PaneLib/releases/download/v{version}/{asset_name(version)}'


def validate_catalog(catalog):
    require(isinstance(catalog, dict) and set(catalog) == {'schemaVersion', 'releases'},
            'Catalog must contain only schemaVersion and releases.')
    require(type(catalog['schemaVersion']) is int and catalog['schemaVersion'] == 1,
            'Unsupported catalog schemaVersion.')
    require(isinstance(catalog['releases'], list), 'Catalog releases must be an ordered list.')
    seen = set()
    for entry in catalog['releases']:
        require(isinstance(entry, dict) and set(entry) == {'version', 'url', 'sha256', 'size'},
                'Each release must contain exactly version, url, sha256 and size.')
        version = entry['version']
        require(isinstance(version, str) and len(version) <= 100 and
                re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?', version)
                and 'SNAPSHOT' not in version.upper(), 'Invalid or SNAPSHOT release version.')
        require(version not in seen, f'Duplicate release version: {version}')
        seen.add(version)
        require(entry['url'] == asset_url(version), f'Unapproved release URL for {version}.')
        require(isinstance(entry['sha256'], str) and re.fullmatch(r'[0-9a-f]{64}', entry['sha256']),
                f'Invalid sha256 for {version}.')
        require(type(entry['size']) is int and 0 < entry['size'] <= MAX_ASSET_BYTES,
                f'Invalid or oversized asset size for {version}.')
    return catalog


def immutable_catalog(catalog, previous_catalog):
    validate_catalog(catalog)
    validate_catalog(previous_catalog)
    prior = previous_catalog['releases']
    require(catalog['releases'][:len(prior)] == prior,
            'Catalog must preserve every previous entry, hash, URL, size and publication order.')


def validate_append_only(previous, proposed):
    immutable_catalog(proposed, previous)
    return proposed


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, f'Duplicate JSON key: {key}')
        result[key] = value
    return result


def load_catalog(path):
    data = bounded_file(path, MAX_CATALOG_BYTES)
    return validate_catalog(json.loads(data, object_pairs_hook=unique_object))


def bounded_file(path, maximum):
    path = Path(path)
    check_path(path)
    require(path.is_file() and path.stat().st_size <= maximum, f'Missing or oversized input: {path}')
    with path.open('rb') as stream:
        data = stream.read(maximum + 1)
    require(len(data) <= maximum, f'Oversized input: {path}')
    return data


def check_path(path):
    for item in (path, *path.parents):
        require(not item.is_symlink(), f'Symlinked path is not allowed: {item}')


class ReleaseRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        parsed = urllib.parse.urlsplit(newurl)
        require(parsed.scheme == 'https' and parsed.hostname in
                {'github.com', 'objects.githubusercontent.com', 'release-assets.githubusercontent.com'}
                and parsed.username is None and parsed.password is None and parsed.port in (None, 443),
                'Unapproved release download redirect.')
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def fetch_asset(entry, asset_dir=None):
    if asset_dir is not None:
        data = bounded_file(Path(asset_dir) / asset_name(entry['version']), entry['size'])
    else:
        # urllib's default HTTPS handler verifies certificates and hostnames.
        opener = urllib.request.build_opener(ReleaseRedirect())
        request = urllib.request.Request(entry['url'], headers={'User-Agent': 'PaneLib-Maven-site/1'})
        try:
            with opener.open(request, timeout=60) as response:
                require(response.status == 200, 'Release download did not return HTTP 200.')
                data = response.read(entry['size'] + 1)
        except (OSError, urllib.error.URLError) as exc:
            raise release.ReleaseError(f'Release download failed for {entry["version"]}: {exc}') from exc
    require(len(data) == entry['size'], f'Release asset size mismatch: {entry["version"]}')
    require(release.digest(data) == entry['sha256'], f'Release asset sha256 mismatch: {entry["version"]}')
    return data


def bounded_zip(data, label):
    # Bound declared expansion before shared validation calls testzip/read.
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            members = archive.infolist()
            require(len(members) <= MAX_MEMBERS, f'Too many ZIP members: {label}')
            require(sum(item.file_size for item in members) <= MAX_EXPANDED_BYTES,
                    f'Expanded ZIP exceeds limit: {label}')
            require(all(not item.flag_bits & 1 for item in members), f'Encrypted ZIP: {label}')
        return release.zip_entries(data, label)
    except (zipfile.BadZipFile, RuntimeError, EOFError) as exc:
        raise release.ReleaseError(f'Invalid ZIP {label}: {exc}') from exc


def validate_asset(data, version):
    entries = bounded_zip(data, asset_name(version))
    release.verify_zip_layout(data)
    prefix = f'panelib-maven-{version}/{COORDINATE}/'
    version_prefix = prefix + version + '/'
    names = release.artifact_names(version)
    main = [names[role] for role in ('runtime', 'sources', 'javadoc')]
    main += [f'panelib-{version}.pom', f'panelib-{version}.module']
    files = set(main) | {name + '.' + alg for name in main for alg in release.HASHES}
    metadata_files = {'maven-metadata.xml', *('maven-metadata.xml.' + alg for alg in release.HASHES)}
    require(set(entries) == {version_prefix + name for name in files} |
            {prefix + name for name in metadata_files}, 'Maven ZIP contains missing or unexpected files.')
    artifacts = {name: entries[version_prefix + name] for name in files}
    binaries = {names[role]: artifacts[names[role]] for role in ('runtime', 'sources', 'javadoc')}
    archives = {role: bounded_zip(binaries[names[role]], role) for role in ('runtime', 'sources', 'javadoc')}
    require(sum(len(body) for archive in archives.values() for body in archive.values()) <= MAX_EXPANDED_BYTES,
            'Combined nested JAR expansion exceeds limit.')
    for role, archive in archives.items():
        for name, body in archive.items():
            require(not release.PRIVATE_PATH.search(body), f'Private filesystem path in {role}: {name}')
    metadata = release.validate_library(archives, version, archives['runtime'].get('LICENSE'))
    require(archives['runtime'].get('LICENSE'), 'Runtime license is missing.')
    dependencies = metadata.get('depends', {})
    loader = dependencies.get('fabricloader', '')
    minecraft = dependencies.get('minecraft', '')
    require(isinstance(loader, str) and re.fullmatch(r'>=[0-9]+(?:\.[0-9]+)+', loader)
            and isinstance(minecraft, str) and re.fullmatch(r'~[0-9]+(?:\.[0-9]+)+', minecraft)
            and dependencies.get('java') == '>=25' and dependencies.get('fabric-api') == '*',
            'Runtime dependency metadata is invalid.')
    pom = release.parse_xml(artifacts[f'panelib-{version}.pom'], 'POM')
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    pom_dependencies = {(node.findtext('m:groupId', namespaces=ns), node.findtext('m:artifactId', namespaces=ns)):
                        node.findtext('m:version', namespaces=ns)
                        for node in pom.findall('m:dependencies/m:dependency', ns)}
    api = pom_dependencies.get(('net.fabricmc.fabric-api', 'fabric-api'), '')
    require(isinstance(api, str) and re.fullmatch(r'[0-9A-Za-z.+_-]+', api), 'Invalid Fabric API version.')
    config = {'version': version, 'group': GROUP, 'loader_version': loader[2:],
              'minecraft_version': minecraft[1:], 'fabric_api_version': api}
    release.validate_maven(artifacts, config, binaries)
    expected_index = release.maven_index(GROUP, version)
    require(entries[prefix + 'maven-metadata.xml'] == expected_index, 'Single-version Maven metadata mismatch.')
    for algorithm in release.HASHES:
        require(entries[prefix + 'maven-metadata.xml.' + algorithm].decode().strip() ==
                release.digest(expected_index, algorithm), 'Maven metadata checksum mismatch.')
    return {f'{COORDINATE}/{version}/{name}': body for name, body in artifacts.items()}


def combined_metadata(versions):
    # Publication order, not lexical version order, defines latest/release.
    last = versions[-1]
    version_xml = ''.join(f'<version>{version}</version>' for version in versions)
    return (f'<?xml version="1.0" encoding="UTF-8"?>\n<metadata>\n  <groupId>{GROUP}</groupId>\n'
            f'  <artifactId>panelib</artifactId>\n  <versioning>\n    <latest>{last}</latest>\n'
            f'    <release>{last}</release>\n    <versions>{version_xml}</versions>\n'
            '  </versioning>\n</metadata>\n').encode()


def index_html(versions):
    current = html.escape(versions[-1]) if versions else 'None — no releases in this catalog'
    links = ''.join(f'<li><a href="{COORDINATE}/{version}/panelib-{version}.pom">{version}</a></li>' for version in versions)
    example = f'implementation("dev.pane:panelib:{versions[-1]}")' if versions else '// Add a dependency after the first release is published.'
    return (f'<!doctype html>\n<html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width">'
            f'<title>PaneLib Maven repository</title><body><h1>PaneLib Maven repository</h1>'
            f'<p>Current catalog version: {current}</p><p>Repository endpoint: <code>{ENDPOINT}</code></p>'
            '<p>This generated tree may be staged locally. Building it does not publish releases or deploy this endpoint.</p>'
            f'<pre>repositories {{ maven {{ url = uri("{ENDPOINT}") }} }}\n{html.escape(example)}</pre>'
            f'<ul>{links}</ul><p><a href="catalog.json">Pinned release catalog</a></p></body></html>\n').encode()


def build_site(catalog, output, *, previous_catalog, asset_dir=None):
    immutable_catalog(catalog, previous_catalog)
    output = Path(os.path.abspath(output))
    check_path(output)
    require(output.parent.is_dir(), 'Output parent directory must already exist.')
    require(not output.exists() or (output.is_dir() and not any(output.iterdir())),
            'Output must be absent or empty; refusing to replace an existing tree.')
    total = 0
    staging = Path(tempfile.mkdtemp(prefix='.panelib-maven-', dir=output.parent))
    try:
        def write(name, body):
            nonlocal total
            total += len(body)
            require(total <= MAX_SITE_BYTES, 'Maven site exceeds 900 MiB limit.')
            target = staging / release.safe_name(name)
            target.parent.mkdir(parents=True, exist_ok=True)
            require(not target.exists(), f'Duplicate site file: {name}')
            target.write_bytes(body)
        for entry in catalog['releases']:
            files = validate_asset(fetch_asset(entry, asset_dir), entry['version'])
            for name, body in sorted(files.items()):
                write(name, body)
        versions = [entry['version'] for entry in catalog['releases']]
        if versions:
            metadata = combined_metadata(versions)
            write(COORDINATE + '/maven-metadata.xml', metadata)
            for algorithm in release.HASHES:
                write(COORDINATE + '/maven-metadata.xml.' + algorithm,
                      (release.digest(metadata, algorithm) + '\n').encode())
        write('catalog.json', release.json_bytes(catalog))
        write('index.html', index_html(versions))
        check_path(output)
        require(not output.exists() or (output.is_dir() and not any(output.iterdir())),
                'Output changed during assembly; refusing to replace it.')
        # Windows cannot rename a directory over an existing directory, even an empty one.
        # Remove only the validated empty placeholder after all assembly checks have passed;
        # the complete staged tree still becomes visible in one same-filesystem rename.
        had_empty_output = output.exists()
        if had_empty_output:
            output.rmdir()  # Also rejects a concurrent writer that made the directory nonempty.
        try:
            os.replace(staging, output)
        except BaseException:
            if had_empty_output:
                output.mkdir(exist_ok=True)
            raise
    finally:
        if staging.exists():
            shutil.rmtree(staging)
    return output


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--catalog', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    prior = parser.add_mutually_exclusive_group(required=True)
    prior.add_argument('--previous-catalog', type=Path)
    prior.add_argument('--initial-empty', action='store_true', help='Explicit first-deployment empty baseline')
    parser.add_argument('--asset-dir', type=Path, help='Offline ZIP directory; every file still requires its pinned hash')
    args = parser.parse_args(argv)
    try:
        previous = load_catalog(args.previous_catalog) if args.previous_catalog else EMPTY_CATALOG
        output = build_site(load_catalog(args.catalog), args.output, previous_catalog=previous, asset_dir=args.asset_dir)
        print(f'Staged Maven site: {output} (not deployed)')
        return 0
    except (release.ReleaseError, OSError, ValueError, KeyError, TypeError, AttributeError) as exc:
        print(f'Maven site assembly failed: {exc}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
