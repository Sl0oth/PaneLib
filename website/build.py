"""Build PaneLib's local/GitHub Pages static site from the existing repo docs."""
from pathlib import Path
from html.parser import HTMLParser
from html import escape, unescape
from urllib.parse import urlsplit, quote
import json
import posixpath
import re
import shutil
import markdown
from pygments.formatters import HtmlFormatter

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
DIST = HERE / 'dist'
REPOSITORY = 'https://github.com/Sl0oth/PaneLib'
GUIDES = [
    ('getting-started', 'Build your first screen', 'Start here', 'Create a native screen, paint controls, and handle your first action.'),
    ('migrating-to-panelib', 'Move an existing mod to PaneLib', 'Start here', 'Keep your config and game logic while replacing one screen or HUD at a time.'),
    ('native-minecraft-text', 'Minecraft fonts', 'Build interfaces', 'Use resource-pack-aware Minecraft text beside custom typography.'),
    ('installation', 'Installation', 'Start here', 'Run the standalone starter and choose how to deliver the library with your mod.'),
    ('ui-basics', 'Controls & layout', 'Build interfaces', 'Compose controls, layouts, scrolling, text inputs, menus, and dialogs.'),
    ('input-and-focus', 'Inputs & focus', 'Build interfaces', 'Style text inputs, undo edits, and keep modal focus where it belongs.'),
    ('drawing-and-theming', 'Drawing & theming', 'Build interfaces', 'Draw with Java, choose typography, and make the interface your own.'),
    ('huds-and-composable-motion', 'HUDs & motion', 'In the game', 'Create retained in-world HUDs and composable, finite motion.'),
    ('minecraft-integration', 'Minecraft integration', 'In the game', 'Work with native layers, backdrops, events, and asynchronous updates.'),
    ('layered-ui-and-motion', 'Layers & rendering', 'Go deeper', 'Understand retained layers, opacity, and bounded uploads.'),
    ('paper-craft', 'Paper-craft recipe', 'Go deeper', 'Build a distinctive paper-and-field-notes interface with the public API.'),
    ('performance', 'Performance', 'Go deeper', 'Measure raster work, uploads, idle behavior, and real frame-time limits.'),
    ('status', 'Status & limits', 'Go deeper', 'See what is verified and which capabilities remain outstanding.'),
]
SLUGS = {g[0] for g in GUIDES}
ASSETS = {
    'atlas-console.png': HERE / 'assets/atlas-console.png',
    'inputs.png': HERE / 'assets/inputs.png',
    'atlas-motion.gif': ROOT / 'docs/previews/atlas-motion.gif',
    'hud.gif': ROOT / 'docs/previews/atlas-hud.gif',
    'Roboto-Variable.ttf': ROOT / 'pane-demo/src/main/resources/assets/pane_demo/fonts/Roboto-Variable.ttf',
    'OFL.txt': ROOT / 'pane-demo/src/main/resources/assets/pane_demo/fonts/OFL.txt',
}

class ContentParser(HTMLParser):
    def __init__(self, slug):
        super().__init__(convert_charrefs=False)
        self.slug = slug
        self.output = []
        self.text = []
    def local_url(self, url):
        if url.startswith('#/'): return url
        parts = urlsplit(url)
        if parts.scheme or parts.netloc or url.startswith('/'): return url
        if not parts.path and parts.fragment:
            return '#guide-' + self.slug + '--' + quote(parts.fragment)
        normalized = posixpath.normpath(parts.path)
        if normalized.endswith('.md'):
            slug = normalized[:-3]
            if slug in SLUGS:
                return '#guide-' + slug + ('--' + quote(parts.fragment) if parts.fragment else '')
            return REPOSITORY + '/blob/main/' + posixpath.normpath('docs/' + normalized) + ('#' + parts.fragment if parts.fragment else '')
        if normalized.startswith('previews/'):
            name = Path(normalized).name
            return 'assets/' + ('hud.gif' if name == 'atlas-hud.gif' else name)
        if normalized.startswith('../'):
            return REPOSITORY + '/blob/main/' + posixpath.normpath('docs/' + normalized)
        return url
    def handle_starttag(self, tag, attrs):
        values = []
        for key, value in attrs:
            if value is not None and key in ('href', 'src'): value = self.local_url(value)
            values.append(key if value is None else key + '="' + escape(value, quote=True) + '"')
        self.output.append('<' + tag + (' ' + ' '.join(values) if values else '') + '>')
    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)
    def handle_endtag(self, tag): self.output.append('</' + tag + '>')
    def handle_data(self, data): self.output.append(data); self.text.append(data)
    def handle_entityref(self, name): self.output.append('&' + name + ';')
    def handle_charref(self, name): self.output.append('&#' + name + ';')
    def handle_comment(self, data): self.output.append('<!--' + data + '-->')

def flatten_headings(items):
    out = []
    for item in items:
        if item['level'] >= 2:
            out.append({'id': item['id'], 'title': unescape(re.sub('<[^>]+>', '', item['name'])), 'level': item['level']})
        out.extend(flatten_headings(item.get('children', [])))
    return out

def build():
    version = next(line.split('=', 1)[1].strip() for line in (ROOT / 'gradle.properties').read_text().splitlines()
        if line.startswith('version='))
    if not re.fullmatch(r'[0-9A-Za-z][0-9A-Za-z._-]*', version):
        raise SystemExit('Invalid release version in root gradle.properties')
    required = [HERE / 'src' / name for name in ('index.html', 'site.css', 'site.js')]
    missing = [str(p) for p in required if not p.is_file()]
    if missing: raise SystemExit('Waiting for site implementation: ' + ', '.join(missing))
    DIST.mkdir(exist_ok=True)
    for path in (HERE / 'src').iterdir():
        if path.is_file():
            if path.suffix == '.html':
                (DIST / path.name).write_text(path.read_text().replace('{{pane_version}}', version))
            else:
                shutil.copy2(path, DIST / path.name)
    (DIST / 'assets').mkdir(exist_ok=True)
    for name, source in ASSETS.items(): shutil.copy2(source, DIST / 'assets' / name)
    pages = []
    for slug, title, section, description in GUIDES:
        source = (ROOT / 'docs' / (slug + '.md')).read_text().replace('{{pane_version}}', version)
        converter = markdown.Markdown(extensions=['fenced_code', 'tables', 'toc', 'sane_lists', 'codehilite'],
            extension_configs={'codehilite': {'guess_lang': False, 'css_class': 'codehilite'}})
        html = converter.convert(source)
        parser = ContentParser(slug); parser.feed(html)
        pages.append({'slug': slug, 'title': title, 'section': section, 'description': description,
            'html': ''.join(parser.output), 'text': ' '.join(parser.text), 'headings': flatten_headings(converter.toc_tokens)})
    (DIST / 'content.json').write_text(json.dumps({'version': version, 'repository': REPOSITORY, 'pages': pages}, ensure_ascii=False))
    (DIST / 'pygments.css').write_text(HtmlFormatter(style='github-dark').get_style_defs('.codehilite'))
    (DIST / '.nojekyll').write_text('')
    print(f'Built {len(pages)} guides and {len(ASSETS)} local assets in {DIST}')

if __name__ == '__main__': build()
