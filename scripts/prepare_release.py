#!/usr/bin/env python3
"""Build and verify immutable local release candidates. Never builds, uploads, or publishes."""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent.parent
STAMP = (2026, 1, 1, 0, 0, 0)
CHECKSUMS = 'SHA256SUMS'
MANIFEST = 'RELEASE_MANIFEST.json'
HASHES = ('md5', 'sha1', 'sha256', 'sha512')
STARTER_FILES = frozenset(('build.gradle', 'settings.gradle', 'gradlew', 'gradlew.bat',
    'README.md', 'LICENSE', '.gitignore', 'gradle/wrapper/gradle-wrapper.jar',
    'gradle/wrapper/gradle-wrapper.properties'))
TEXT_SUFFIXES = {'.json', '.java', '.html', '.txt', '.md', '.gradle', '.properties', '.xml',
                 '.pom', '.module', '.js', '.css', '.mf'}
PRIVATE_PATH = re.compile(rb'(?:/Users/[^/\s]+/|/home/[^/\s]+/|[A-Za-z]:[\\/]Users[\\/]|file:///Users/)')


class ReleaseError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise ReleaseError(message)


def digest(data, algorithm='sha256'):
    return hashlib.new(algorithm, data).hexdigest()


def json_bytes(value):
    return (json.dumps(value, sort_keys=True, indent=2, ensure_ascii=False) + '\n').encode('utf-8')


def safe_name(name):
    require(isinstance(name, str) and bool(name) and '\\' not in name and '\x00' not in name,
            f'Unsafe archive/path name: {name!r}')
    path = PurePosixPath(name)
    require(not path.is_absolute() and '..' not in path.parts and ':' not in name
            and all(part not in ('', '.') for part in name.rstrip('/').split('/')),
            f'Unsafe archive/path name: {name!r}')
    return name


def plain_file(path):
    path = Path(path)
    require(path.is_file() and not path.is_symlink(), f'Missing or symlinked input: {path}')
    # A symlinked parent can redirect an otherwise ordinary file outside the intended tree.
    for ancestor in path.parents:
        require(not ancestor.is_symlink(), f'Symlinked input directory: {ancestor}')
    return path.read_bytes()


def scan_text(name, data):
    if PurePosixPath(name).suffix.lower() in TEXT_SUFFIXES or name in ('README', 'LICENSE', 'gradlew'):
        require(not PRIVATE_PATH.search(data), f'Private filesystem path in distributable {name}')


def tree_files(path):
    require(path.is_dir() and not path.is_symlink(), f'Missing or symlinked directory: {path}')
    result = {}
    for item in sorted(path.rglob('*')):
        require(not item.is_symlink(), f'Symlink in input tree: {item}')
        if item.is_file():
            name = safe_name(item.relative_to(path).as_posix())
            result[name] = plain_file(item)
    return result


def zip_entries(data, label):
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            require(archive.testzip() is None, f'Corrupt archive: {label}')
            result = {}
            seen = set()
            for info in archive.infolist():
                safe_name(info.filename)
                require(info.filename not in seen, f'Duplicate ZIP member in {label}: {info.filename}')
                seen.add(info.filename)
                require(not stat.S_ISLNK(info.external_attr >> 16), f'Symlink in {label}: {info.filename}')
                if info.is_dir():
                    continue
                body = archive.read(info)
                scan_text(info.filename, body)
                result[info.filename] = body
            return result
    except (zipfile.BadZipFile, RuntimeError, EOFError) as exc:
        raise ReleaseError(f'Invalid archive {label}: {exc}') from exc


def deterministic_zip(entries, executable=()):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(entries.items()):
            safe_name(name)
            scan_text(name, data)
            info = zipfile.ZipInfo(name, STAMP)
            info.create_system = 3
            info.external_attr = (0o100755 if name in executable else 0o100644) << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data, compresslevel=9)
    return stream.getvalue()


def verify_zip_layout(data, executable=()):
    # Validate reproducible layout without depending on this machine's zlib encoder version.
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        require(archive.namelist() == sorted(archive.namelist()) and not archive.comment,
                'ZIP ordering/comment is not canonical.')
        for info in archive.infolist():
            mode = 0o100755 if info.filename in executable else 0o100644
            require(info.date_time == STAMP and info.create_system == 3 and info.external_attr >> 16 == mode
                    and info.compress_type == zipfile.ZIP_DEFLATED and not info.extra and not info.comment
                    and not info.is_dir(), f'ZIP member layout is not canonical: {info.filename}')


def properties(path):
    values = {}
    for line in plain_file(path).decode('utf-8').splitlines():
        line = line.strip()
        if not line or line.startswith(('#', '!')) or '=' not in line:
            continue
        key, value = line.split('=', 1)
        require(key.strip() not in values, f'Duplicate property {key.strip()} in {path.name}')
        values[key.strip()] = value.strip()
    return values


def release_config(root):
    config = properties(root / 'gradle.properties')
    version = config.get('version', '')
    require(re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]*', version) and '..' not in version,
            'Root gradle.properties must specify one safe version.')
    require(re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*', config.get('group', '')),
            'Root gradle.properties must specify a Maven group.')
    for key in ('minecraft_version', 'loader_version', 'fabric_api_version'):
        require(config.get(key), f'Root gradle.properties is missing {key}.')
    return config


def artifact_names(version):
    return {'runtime': f'panelib-{version}.jar', 'sources': f'panelib-{version}-sources.jar',
            'javadoc': f'panelib-{version}-javadoc.jar', 'starter': f'panelib-{version}-starter.zip',
            'api-reference': f'panelib-{version}-api-reference.zip',
            'maven-repository': f'panelib-{version}-maven.zip', 'release-notes': 'RELEASE_NOTES.md',
            'demo': f'panelib-demo-{version}.jar'}


# The only files attached to a GitHub release; everything else stays local for verification.
UPLOAD_ROLES = ('runtime', 'demo', 'maven-repository')


def validate_library(archives, version, license_data):
    for role, entries in archives.items():
        require(entries.get('LICENSE') == license_data, f'{role} license mismatch.')
        require(not any(name.startswith(('dev/pane/demo/', 'dev/pane/mcef/')) for name in entries),
                f'Excluded package in {role}.')
    common = {'META-INF/MANIFEST.MF', 'LICENSE', 'fabric.mod.json', 'panelib.mixins.json'}
    for role, suffix in (('runtime', '.class'), ('sources', '.java')):
        require(all(name in common or (name.startswith(('dev/pane/ui/', 'dev/pane/mixin/'))
                    and name.endswith(suffix)) for name in archives[role]),
                f'Unexpected private/package content in {role}.')
    runtime = archives['runtime']
    require('dev/pane/ui/UiScreen.class' in runtime, 'Runtime is missing UiScreen.')
    metadata = json.loads(runtime.get('fabric.mod.json', b'{}'))
    require(metadata.get('id') == 'panelib' and metadata.get('version') == version,
            'Runtime ID/version does not match root version.')
    require(metadata.get('environment') == 'client', 'Runtime must remain client-only.')
    require(metadata.get('license') == 'MIT', 'Runtime must declare MIT license.')
    require('index.html' in archives['javadoc'] and 'dev/pane/ui/UiScreen.html' in archives['javadoc'],
            'Javadoc index or UiScreen documentation missing.')
    require('dev/pane/ui/UiScreen.java' in archives['sources'], 'Source archive is missing UiScreen.java.')
    source_meta = json.loads(archives['sources'].get('fabric.mod.json', b'{}'))
    require(source_meta.get('id') == 'panelib' and source_meta.get('version') in ('${version}', version),
            'Source archive metadata is inconsistent.')
    source_meta['version'] = version
    require(source_meta == metadata, 'Source/runtime metadata differs beyond the version template.')
    return metadata


def validate_demo(entries, version, license_data, runtime):
    """The demo is a separate mod that needs PaneLib installed; it never carries library classes."""
    require(entries.get('LICENSE') == license_data, 'demo license mismatch.')
    metadata = json.loads(entries.get('fabric.mod.json', b'{}'))
    require(metadata.get('id') == 'pane_demo' and metadata.get('version') == version,
            'Demo ID/version does not match root version.')
    require(metadata.get('environment') == 'client', 'Demo must remain client-only.')
    require(metadata.get('depends', {}).get('panelib') == '>=' + version, 'Demo must require this PaneLib version.')
    require('dev/pane/demo/PaneDemoClient.class' in entries, 'Demo is missing its client entrypoint.')
    for name in entries:
        require(name in ('META-INF/MANIFEST.MF', 'LICENSE', 'fabric.mod.json')
                or (name.startswith('dev/pane/') and name.endswith('.class'))
                or name.startswith('assets/pane_demo/'), f'Unexpected demo content: {name}')
        require(not name.startswith('dev/pane/mcef/'), f'Excluded package in demo: {name}')
        require(name not in runtime or name in ('META-INF/MANIFEST.MF', 'LICENSE', 'fabric.mod.json'),
                f'Demo bundles a library class: {name}')


def check_exact(actual, expected, label, ignored=()):
    actual = {key: value for key, value in actual.items() if key not in ignored}
    require(set(actual) == set(expected), f'{label} file set differs: '
            f'missing={sorted(set(expected)-set(actual))[:8]}, extra={sorted(set(actual)-set(expected))[:8]}')
    wrong = [key for key in expected if actual[key] != expected[key]]
    require(not wrong, f'{label} is stale or mismatched: {wrong[:8]}')


def verify_build_inputs(root, config, archives, paths):
    version = config['version']
    java = {key: value for key, value in tree_files(root / 'pane/src/main/java').items()
            if not key.startswith('dev/pane/mcef/')}
    require(all(key.endswith('.java') and key.startswith(('dev/pane/ui/', 'dev/pane/mixin/')) for key in java),
            'Unexpected source package/file; review the release allowlist.')
    resources = tree_files(root / 'pane/src/main/resources')
    require(set(resources) <= {'fabric.mod.json', 'panelib.mixins.json'},
            'Unexpected library resource; review the release allowlist before adding it.')
    expected_sources = {**java, **resources, 'LICENSE': plain_file(root / 'LICENSE')}
    check_exact(archives['sources'], expected_sources, 'Sources JAR', ('META-INF/MANIFEST.MF',))
    processed = tree_files(root / 'pane/build/resources/main')
    expected_processed = dict(resources)
    expected_processed['fabric.mod.json'] = resources['fabric.mod.json'].replace(b'${version}', version.encode())
    check_exact(processed, expected_processed, 'Processed resources')
    classes = tree_files(root / 'pane/build/classes/java/main')
    require(all(key.endswith('.class') and key.startswith(('dev/pane/ui/', 'dev/pane/mixin/')) for key in classes),
            'Unexpected compiled package/file; clean and rebuild the library.')
    for name in java:
        if name.endswith(('package-info.java', 'module-info.java')):
            continue
        require(name[:-5] + '.class' in classes, f'Missing compiled source: {name}')
    check_exact(archives['runtime'], {**classes, **processed, 'LICENSE': plain_file(root / 'LICENSE')},
                'Runtime JAR', ('META-INF/MANIFEST.MF',))
    docs = tree_files(root / 'pane/build/docs/javadoc')
    check_exact(archives['javadoc'], {**docs, 'LICENSE': plain_file(root / 'LICENSE')},
                'Javadoc JAR', ('META-INF/MANIFEST.MF',))
    public_docs = []
    for name, body in java.items():
        if re.search(rb'(?m)^public\s+(?:(?:final|abstract|sealed|non-sealed|static)\s+)*(?:class|interface|record|enum)\b', body):
            target = name[:-5] + '.html'
            require(target in docs, f'Javadoc is missing current public type {name}.')
            public_docs.append(target)
    # Exact byte comparisons catch stale build dirs/JARs. Freshness also catches source edits made
    # after compilation while separately rebuilding only the source archive. Rebuild on uncertainty.
    source_paths = [root / 'pane/src/main/java' / key for key in java]
    resource_paths = [root / 'pane/src/main/resources' / key for key in resources]
    latest_input = max(path.stat().st_mtime_ns for path in [*source_paths, *resource_paths, root / 'build.gradle',
                       root / 'pane/build.gradle', root / 'gradle.properties'])
    for role in ('runtime', 'sources', 'javadoc'):
        require(paths[role].stat().st_mtime_ns >= latest_input,
                f'{role} JAR predates current source/build inputs. Rebuild the release artifacts.')
    for name in java:
        outer = root / 'pane/build/classes/java/main' / (name[:-5] + '.class')
        if outer.exists():
            require(outer.stat().st_mtime_ns >= (root / 'pane/src/main/java' / name).stat().st_mtime_ns,
                    f'Compiled class predates {name}. Recompile before staging.')
    return {'sourceFiles': {name: digest(body) for name, body in sorted(expected_sources.items())},
            'publicJavadoc': sorted(public_docs), 'runtimeClassCount': len(classes)}


def parse_xml(data, label):
    require(b'<!DOCTYPE' not in data.upper() and b'<!ENTITY' not in data.upper(), f'Unsafe XML in {label}')
    try:
        return ET.fromstring(data)
    except ET.ParseError as exc:
        raise ReleaseError(f'Invalid XML {label}: {exc}') from exc


def maven_dependencies(config):
    return {('net.fabricmc', 'fabric-loader'): config['loader_version'],
            ('net.fabricmc.fabric-api', 'fabric-api'): config['fabric_api_version']}


def validate_maven(entries, config, binary_payload):
    version, group = config['version'], config['group']
    base = f'panelib-{version}'
    pom = parse_xml(entries[base + '.pom'], 'POM')
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    def val(key):
        return pom.findtext('m:' + key, namespaces=ns)
    require((val('groupId'), val('artifactId'), val('version')) == (group, 'panelib', version),
            'POM coordinate/version mismatch.')
    dependency_nodes = pom.findall('m:dependencies/m:dependency', ns)
    require(len(dependency_nodes) == 2 and all(node.findtext('m:scope', namespaces=ns) == 'runtime'
            for node in dependency_nodes), 'POM dependency count/scope mismatch.')
    deps = {(node.findtext('m:groupId', namespaces=ns), node.findtext('m:artifactId', namespaces=ns)):
             node.findtext('m:version', namespaces=ns) for node in pom.findall('m:dependencies/m:dependency', ns)}
    require(deps == maven_dependencies(config), 'POM dependencies differ from root version configuration.')
    module = json.loads(entries[base + '.module'])
    component = module.get('component', {})
    require((component.get('group'), component.get('module'), component.get('version')) == (group, 'panelib', version),
            'Gradle module coordinate/version mismatch.')
    require(len(module.get('variants', [])) == 4, 'Duplicate or unexpected Maven module variants.')
    variants = {variant.get('name'): variant for variant in module.get('variants', [])}
    required = {'apiElements': base + '.jar', 'runtimeElements': base + '.jar',
                'sourcesElements': base + '-sources.jar', 'javadocElements': base + '-javadoc.jar'}
    require(set(variants) == set(required), 'Unexpected or missing Maven variants.')
    for name, filename in required.items():
        variant = variants[name]
        files = variant.get('files', [])
        require(len(files) == 1 and files[0].get('name') == filename and files[0].get('url') == filename,
                f'Unexpected Maven artifact URL in {name}.')
        require(entries[filename] == binary_payload[filename], f'Maven artifact bytes differ: {filename}')
        file_meta = files[0]
        require(file_meta.get('size') == len(entries[filename]), f'Maven size mismatch: {filename}')
        for algorithm in HASHES:
            require(file_meta.get(algorithm) == digest(entries[filename], algorithm),
                    f'Maven module {algorithm} mismatch: {filename}')
        if name in ('apiElements', 'runtimeElements'):
            require(variant.get('attributes', {}).get('org.gradle.jvm.version') == 25, 'Maven Java target mismatch.')
    require(len(variants['runtimeElements'].get('dependencies', [])) == 2, 'Maven runtime dependency count mismatch.')
    runtime_dependencies = {(dep.get('group'), dep.get('module')): dep.get('version', {}).get('requires')
                            for dep in variants['runtimeElements'].get('dependencies', [])}
    require(runtime_dependencies == maven_dependencies(config), 'Gradle module dependencies mismatch.')
    for filename, data in binary_payload.items():
        require(entries.get(filename) == data, f'Maven/runtime parity failed: {filename}')
    for filename in [*binary_payload, base + '.pom', base + '.module']:
        for algorithm in HASHES:
            require(entries.get(filename + '.' + algorithm, b'').decode().strip() == digest(entries[filename], algorithm),
                    f'Missing or invalid Maven sidecar: {filename}.{algorithm}')


def maven_index(group, version):
    # Deliberately omit lastUpdated: a local single-version repository is content-addressed here.
    return (f'<?xml version="1.0" encoding="UTF-8"?>\n<metadata>\n  <groupId>{group}</groupId>\n'
            f'  <artifactId>panelib</artifactId>\n  <versioning>\n    <latest>{version}</latest>\n'
            f'    <release>{version}</release>\n    <versions><version>{version}</version></versions>\n'
            '  </versioning>\n</metadata>\n').encode()


def maven_zip(root, config, binary_payload):
    group, version = config['group'], config['version']
    coordinate = group.replace('.', '/') + '/panelib'
    source = root / 'build/maven' / coordinate
    entries = tree_files(source / version)
    main_files = [*binary_payload, f'panelib-{version}.pom', f'panelib-{version}.module']
    expected = set(main_files) | {name + '.' + algorithm for name in main_files for algorithm in HASHES}
    require(set(entries) == expected, 'Unexpected/missing files in current-version Maven publication.')
    validate_maven(entries, config, binary_payload)
    index = plain_file(source / 'maven-metadata.xml')
    parsed = parse_xml(index, 'Maven index')
    require(parsed.findtext('groupId') == group and parsed.findtext('artifactId') == 'panelib'
            and version in [node.text for node in parsed.findall('versioning/versions/version')],
            'Maven publication index does not contain the current coordinate.')
    for algorithm in HASHES:
        require(plain_file(source / ('maven-metadata.xml.' + algorithm)).decode().strip() == digest(index, algorithm),
                f'Maven index checksum mismatch: {algorithm}')
    prefix = f'panelib-maven-{version}/{coordinate}/'
    packed = {prefix + version + '/' + name: data for name, data in entries.items()}
    index = maven_index(group, version)
    packed[prefix + 'maven-metadata.xml'] = index
    for algorithm in HASHES:
        packed[prefix + 'maven-metadata.xml.' + algorithm] = (digest(index, algorithm) + '\n').encode()
    return deterministic_zip(packed)


def starter_entries(root, version, runtime):
    starter = root / 'examples/first-screen'
    entries = {name: plain_file(starter / name) for name in STARTER_FILES}
    sources = tree_files(starter / 'src')
    allowed = ('main/java/example/pane/', 'main/resources/assets/pane_example/')
    for name, data in sources.items():
        require((name.startswith(allowed) and PurePosixPath(name).suffix in ('.java', '.json'))
                or name == 'main/resources/fabric.mod.json', f'Unexpected starter source/resource: {name}')
        entries['src/' + name] = data
    require('src/main/java/example/pane/FirstScreenClient.java' in entries, 'Starter entrypoint missing.')
    entries['README.md'] = entries['README.md'].replace(b'{{pane_version}}', version.encode())
    entries['panelib-release.properties'] = f'pane_version={version}\n'.encode()
    entries[f'libs/panelib-{version}.jar'] = runtime
    return {'pane-first-screen/' + name: data for name, data in entries.items()}


def assemble(root):
    config = release_config(root)
    version = config['version']
    names = artifact_names(version)
    coordinates = set(re.findall(rb'`(' + re.escape(config['group'].encode()) + rb':panelib:[^`]+)`', plain_file(root / 'README.md')))
    require(coordinates == {f'{config["group"]}:panelib:{version}'.encode()},
            'README coordinate differs from root version.')
    paths = {role: root / 'pane/build/libs' / names[role] for role in ('runtime', 'sources', 'javadoc')}
    payload = {names[role]: plain_file(path) for role, path in paths.items()}
    archives = {role: zip_entries(payload[names[role]], role) for role in paths}
    metadata = validate_library(archives, version, plain_file(root / 'LICENSE'))
    require(metadata.get('depends', {}).get('minecraft') == '~' + config['minecraft_version'], 'Minecraft metadata mismatch.')
    require(metadata.get('depends', {}).get('fabricloader') == '>=' + config['loader_version'], 'Loader metadata mismatch.')
    require(metadata.get('depends', {}).get('java') == '>=25' and metadata.get('depends', {}).get('fabric-api') == '*',
            'Java or Fabric API metadata mismatch.')
    provenance = verify_build_inputs(root, config, archives, paths)
    payload[names['maven-repository']] = maven_zip(root, config, dict(payload))
    payload[names['starter']] = deterministic_zip(starter_entries(root, version, payload[names['runtime']]),
                                                   ('pane-first-screen/gradlew',))
    payload[names['api-reference']] = payload[names['javadoc']]
    demo_path = root / 'pane-demo/build/libs' / names['demo']
    payload[names['demo']] = plain_file(demo_path)
    validate_demo(zip_entries(payload[names['demo']], 'demo'), version, plain_file(root / 'LICENSE'), archives['runtime'])
    demo_inputs = [path for path in (root / 'pane-demo/src/main').rglob('*') if path.is_file()]
    latest_demo = max(path.stat().st_mtime_ns for path in [*demo_inputs, root / 'pane-demo/build.gradle'])
    require(demo_path.stat().st_mtime_ns >= latest_demo, 'Demo JAR predates current demo sources. Rebuild it.')
    notes = plain_file(root / 'docs/release-notes.md')
    require(b'{{version}}' in notes, 'Release notes template must contain {{version}}.')
    notes = notes.replace(b'{{version}}', version.encode())
    require(b'{{' not in notes, 'Unresolved token in release notes.')
    scan_text(names['release-notes'], notes)
    payload[names['release-notes']] = notes
    manifest = {'schemaVersion': 1, 'version': version, 'coordinate': f'{config["group"]}:panelib:{version}',
        'environment': 'client', 'status': 'local-candidate-not-published', 'license': 'MIT',
        'requires': metadata['depends'], 'buildDependencies': {key: config[key] for key in
            ('minecraft_version', 'loader_version', 'fabric_api_version')}, 'buildParity': provenance,
        'artifacts': [{'role': role, 'file': name, 'size': len(payload[name]), 'sha256': digest(payload[name])}
                      for role, name in sorted(names.items())]}
    payload[MANIFEST] = json_bytes(manifest)
    payload[CHECKSUMS] = ''.join(f'{digest(data)}  {name}\n' for name, data in sorted(payload.items())).encode()
    return version, payload


def verify_directory(directory):
    require(directory.is_dir() and not directory.is_symlink(), f'Not a plain release directory: {directory}')
    entries = tree_files(directory)
    require(all('/' not in name for name in entries), 'Unexpected subdirectory in release candidate.')
    require(CHECKSUMS in entries and MANIFEST in entries, 'Candidate is missing checksum/manifest files.')
    expected = {}
    for line in entries[CHECKSUMS].decode('utf-8').splitlines():
        match = re.fullmatch(r'([a-f0-9]{64})  ([^/\\]+)', line)
        require(match is not None, 'Malformed SHA256SUMS line.')
        checksum, name = match.groups()
        safe_name(name)
        require(name not in expected and name != CHECKSUMS, 'Duplicate/self-referencing checksum entry.')
        expected[name] = checksum
    require(set(expected) == set(entries) - {CHECKSUMS}, 'Checksums do not cover every payload exactly once.')
    for name, checksum in expected.items():
        require(digest(entries[name]) == checksum, f'Checksum mismatch: {name}')
    manifest = json.loads(entries[MANIFEST])
    require(manifest.get('schemaVersion') == 1, 'Unsupported release manifest schema.')
    version = manifest['version']
    require(re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]*', version) and '..' not in version, 'Unsafe release version.')
    names = artifact_names(version)
    artifacts = manifest.get('artifacts', [])
    require(len(artifacts) == len(names), 'Missing/extra manifest artifacts.')
    require({item['role']: item['file'] for item in artifacts} == names, 'Manifest roles/files mismatch.')
    require(set(entries) == {*names.values(), MANIFEST, CHECKSUMS}, 'Unexpected candidate payload.')
    for item in artifacts:
        body = entries[item['file']]
        require(item['size'] == len(body) and item['sha256'] == digest(body), f'Manifest digest mismatch: {item["file"]}')
    scan_text(MANIFEST, entries[MANIFEST])
    scan_text(names['release-notes'], entries[names['release-notes']])
    require(b'{{' not in entries[names['release-notes']], 'Unresolved release note token.')
    archives = {role: zip_entries(entries[names[role]], role) for role in ('runtime', 'sources', 'javadoc')}
    license_data = archives['runtime'].get('LICENSE')
    require(bool(license_data), 'Runtime license absent.')
    metadata = validate_library(archives, version, license_data)
    validate_demo(zip_entries(entries[names['demo']], 'demo'), version, license_data, archives['runtime'])
    require(manifest.get('requires') == metadata['depends'] and manifest.get('environment') == 'client'
            and manifest.get('license') == 'MIT' and manifest.get('status') == 'local-candidate-not-published',
            'Manifest runtime metadata mismatch.')
    source_files = {name: digest(data) for name, data in archives['sources'].items() if name != 'META-INF/MANIFEST.MF'}
    require(manifest['buildParity']['sourceFiles'] == source_files, 'Source provenance mismatch.')
    for name in source_files:
        if name.endswith('.java') and not name.endswith(('package-info.java', 'module-info.java')):
            require(name[:-5] + '.class' in archives['runtime'], f'Runtime/source mismatch: {name}')
    require(sum(name.endswith('.class') for name in archives['runtime']) == manifest['buildParity']['runtimeClassCount'],
            'Runtime class count differs from manifest.')
    for name in manifest['buildParity']['publicJavadoc']:
        require(name in archives['javadoc'], f'Missing public Javadoc: {name}')
    require(entries[names['api-reference']] == entries[names['javadoc']], 'API ZIP differs from Javadoc JAR.')
    starter = zip_entries(entries[names['starter']], 'starter')
    prefix = 'pane-first-screen/'
    require(all(name.startswith(prefix) for name in starter), 'Unexpected starter ZIP root.')
    inner = {name[len(prefix):]: data for name, data in starter.items()}
    require(STARTER_FILES <= set(inner), 'Starter build/wrapper files missing.')
    for name in inner:
        require(name in STARTER_FILES or name in ('panelib-release.properties', 'libs/' + names['runtime'])
                or (name.startswith('src/main/java/example/pane/') and name.endswith('.java'))
                or (name.startswith('src/main/resources/assets/pane_example/') and name.endswith('.json'))
                or name == 'src/main/resources/fabric.mod.json', f'Unexpected private starter content: {name}')
    require(inner.get('panelib-release.properties') == f'pane_version={version}\n'.encode(), 'Starter descriptor mismatch.')
    require(inner.get('libs/' + names['runtime']) == entries[names['runtime']], 'Starter runtime parity mismatch.')
    require(inner.get('LICENSE') == license_data, 'Starter license mismatch.')
    require('src/main/java/example/pane/FirstScreenClient.java' in inner, 'Starter client source missing.')
    config = {'version': version, 'group': manifest['coordinate'].split(':')[0], **manifest['buildDependencies']}
    require(manifest['coordinate'] == f'{config["group"]}:panelib:{version}', 'Manifest coordinate mismatch.')
    require(metadata['depends'].get('minecraft') == '~' + config['minecraft_version'] and
            metadata['depends'].get('fabricloader') == '>=' + config['loader_version'] and
            metadata['depends'].get('java') == '>=25' and metadata['depends'].get('fabric-api') == '*',
            'Dependency metadata mismatch.')
    repo = zip_entries(entries[names['maven-repository']], 'Maven repository')
    prefix = f'panelib-maven-{version}/{config["group"].replace(".", "/")}/panelib/'
    artifact_prefix = prefix + version + '/'
    repository_files = {name[len(artifact_prefix):]: data for name, data in repo.items() if name.startswith(artifact_prefix)}
    binaries = {names[role]: entries[names[role]] for role in ('runtime', 'sources', 'javadoc')}
    main_files = [*binaries, f'panelib-{version}.pom', f'panelib-{version}.module']
    expected_files = set(main_files) | {name + '.' + alg for name in main_files for alg in HASHES}
    require(set(repository_files) == expected_files, 'Maven ZIP contains missing/unexpected current-version files.')
    expected_repo = {artifact_prefix + name for name in expected_files} | {prefix + 'maven-metadata.xml'} | {
        prefix + 'maven-metadata.xml.' + alg for alg in HASHES}
    require(set(repo) == expected_repo, 'Maven ZIP contains other versions, coordinates or private files.')
    validate_maven(repository_files, config, binaries)
    index = maven_index(config['group'], version)
    require(repo[prefix + 'maven-metadata.xml'] == index, 'Maven single-version index mismatch.')
    for algorithm in HASHES:
        require(repo[prefix + 'maven-metadata.xml.' + algorithm].decode().strip() == digest(index, algorithm),
                'Maven index sidecar mismatch.')
    # Our assembled ZIPs have reproducible order, timestamps, permissions and compression settings.
    verify_zip_layout(entries[names['starter']], ('pane-first-screen/gradlew',))
    verify_zip_layout(entries[names['maven-repository']])
    return manifest


def output_path(root, version, requested):
    output = Path(requested).absolute() if requested else root / 'build/release' / version
    require(not output.is_symlink(), 'Release output cannot be a symlink.')
    for ancestor in output.parents:
        require(not ancestor.is_symlink(), f'Release output has symlinked ancestor: {ancestor}')
    resolved = output.resolve()
    protected = [(root / 'release-snapshots').resolve(), (root / 'examples').resolve(),
                 (root / 'pane').resolve(), (root / 'pane-demo').resolve()]
    require(not any(resolved == path or path in resolved.parents for path in protected), 'Protected release output location.')
    require(resolved != root.resolve() and resolved != (root / 'build').resolve()
            and resolved != (root / 'build/release').resolve(), 'Output must be a dedicated candidate directory.')
    return output


def stage(root=ROOT, output=None):
    root = Path(root).absolute()
    version, payload = assemble(root)
    destination = output_path(root, version, output)
    if destination.exists():
        verify_directory(destination)
        existing = tree_files(destination)
        require(existing == payload, f'Immutable candidate differs: {destination}. Use --output with a fresh candidate directory.')
        return destination, False
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = Path(tempfile.mkdtemp(prefix='.' + destination.name + '-staging-', dir=destination.parent))
    try:
        for name, data in payload.items():
            (temporary / name).write_bytes(data)
        verify_directory(temporary)
        # An exclusive sidecar serializes this script's atomic publication of a local directory.
        lock = destination.parent / ('.' + destination.name + '.staging-lock')
        try:
            descriptor = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        except FileExistsError as exc:
            raise ReleaseError('Another staging process owns this output; retry after it finishes.') from exc
        try:
            os.close(descriptor)
            require(not destination.exists(), 'Output appeared during staging; retry to compare it.')
            temporary.rename(destination)
        finally:
            lock.unlink(missing_ok=True)
    finally:
        if temporary.exists():
            shutil.rmtree(temporary)
    return destination, True


def upload_set(candidate, output):
    """Copy the attached release files and a SHA256SUMS covering exactly them into a fresh directory."""
    manifest = verify_directory(candidate)
    names = artifact_names(manifest['version'])
    output = Path(output).absolute()
    require(not output.exists() and not output.is_symlink(), f'Upload directory must not exist yet: {output}')
    output.mkdir(parents=True)
    files = {names[role]: (candidate / names[role]).read_bytes() for role in UPLOAD_ROLES}
    for name, data in files.items():
        (output / name).write_bytes(data)
    (output / CHECKSUMS).write_bytes(''.join(f'{digest(data)}  {name}\n' for name, data in sorted(files.items())).encode())
    return output


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--output', type=Path, help='Stage to a fresh dedicated candidate directory.')
    group.add_argument('--verify', type=Path, help='Verify an existing candidate without touching it or rebuilding.')
    group.add_argument('--upload', nargs=2, type=Path, metavar=('CANDIDATE', 'OUTPUT'),
                       help='Verify a candidate and copy only the files to attach to the release into OUTPUT.')
    options = parser.parse_args(argv)
    try:
        if options.upload:
            path = upload_set(options.upload[0].absolute(), options.upload[1])
            print(f'Release files ready to attach: {path} (not published)')
        elif options.verify:
            manifest = verify_directory(options.verify.absolute())
            print(f'Verified PaneLib {manifest["version"]}: {options.verify} (not published)')
        else:
            path, created = stage(output=options.output)
            print(f'{"Staged" if created else "Already identical"}: {path} (not published)')
        return 0
    except (ReleaseError, OSError, ValueError, KeyError, TypeError) as exc:
        print(f'Release preparation failed: {exc}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
