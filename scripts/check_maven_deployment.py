"""Read-only deployment guards for PaneLib's public Pages Maven repository."""
import argparse
import json
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.request

from build_maven_site import validate_catalog, validate_append_only, load_catalog

CATALOG_URL = 'https://sl0oth.github.io/PaneLib/maven/catalog.json'
MAIN_URL = 'https://api.github.com/repos/Sl0oth/PaneLib/git/ref/heads/main'
LIMIT = 4 * 1024 * 1024


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Reject before urllib can copy an API Authorization header to another origin.
        raise ValueError('Deployment guard refuses redirects.')


def fetch_json(url, token=None):
    headers = {'User-Agent': 'PaneLib-release-validation', 'Accept': 'application/json',
               'Cache-Control': 'no-cache'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.build_opener(NoRedirect()).open(request, timeout=30) as response:
        if response.geturl() != url:
            raise ValueError('Deployment guard refuses unexpected redirects.')
        data = response.read(LIMIT + 1)
        if len(data) > LIMIT:
            raise ValueError('Deployment metadata exceeds size limit.')
    def unique_pairs(pairs):
        value = {}
        for key, item in pairs:
            if key in value:
                raise ValueError('Duplicate deployment metadata key: ' + key)
            value[key] = item
        return value
    return json.loads(data, object_pairs_hook=unique_pairs)


def baseline(proposed):
    validate_catalog(proposed)
    try:
        previous = fetch_json(CATALOG_URL)
    except urllib.error.HTTPError as error:
        if error.code != 404 or proposed['releases']:
            raise ValueError('Cannot establish published history. A missing catalog permits only an '
                             'empty bootstrap deployment; retained releases must never be dropped.') from error
        previous = {'schemaVersion': 1, 'releases': []}
    validate_append_only(previous, proposed)
    return previous


def current_main():
    if os.environ.get('GITHUB_REPOSITORY') != 'Sl0oth/PaneLib' or os.environ.get('GITHUB_REF') != 'refs/heads/main':
        raise ValueError('Deployment must run from Sl0oth/PaneLib main.')
    expected = os.environ.get('GITHUB_SHA', '')
    if not re.fullmatch(r'[0-9a-f]{40}', expected):
        raise ValueError('Missing exact workflow commit SHA.')
    reference = fetch_json(MAIN_URL, os.environ.get('GITHUB_TOKEN'))
    if reference.get('ref') != 'refs/heads/main' or reference.get('object', {}).get('sha') != expected:
        raise ValueError('This workflow is stale. Run deployment from the current main commit.')
    return expected


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['baseline', 'check-live', 'current-main'])
    parser.add_argument('--catalog', type=Path, default=Path('distribution/maven/releases.json'))
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if args.command == 'current-main':
        print('Current main verified: ' + current_main())
        return
    previous = baseline(load_catalog(args.catalog))
    if args.command == 'baseline':
        if not args.output:
            parser.error('baseline requires --output')
        if args.output.is_symlink() or any(parent.is_symlink() for parent in args.output.absolute().parents):
            raise ValueError('Baseline output must not traverse a symlink.')
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(previous, indent=2) + '\n', encoding='utf-8')
    print(f'Published history preserved: {len(previous["releases"])} versions. No upload performed.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError) as error:
        print('Maven deployment guard failed: ' + str(error), file=sys.stderr)
        raise SystemExit(1)
