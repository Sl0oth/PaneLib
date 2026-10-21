# PaneLib docs website

Source for the [PaneLib documentation](https://sl0oth.github.io/PaneLib/), deployed with GitHub Pages. The existing Markdown in
`../docs` remains the source of truth. The website builder generates local HTML/content data;
there is no web runtime in the Minecraft library and no account/analytics service in this site.

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python build.py
.venv/bin/python -m http.server 4173 --bind 127.0.0.1 --directory dist
```

Open `http://127.0.0.1:4173/`. All thirteen guides appear below the introduction in one continuous document. Menu and search links jump to section anchors; old guide URLs still jump to the matching section. The site works under a GitHub Pages subdirectory.
Generated `dist/`, the environment and copied Claude reference packet are ignored. Local fonts
retain their included OFL license. Original screenshots/captures are from PaneLib's actual client.

The `Documentation` workflow builds documentation and the complete approved Maven catalog together
from current `main`. It checks the live catalog before building and again before deployment, and
rejects stale revisions, removed versions, changed hashes or unavailable release assets. Every
documentation deploy preserves all approved Maven versions. It uses the workflow's read-only
repository token and Pages/OIDC deployment permissions, with no personal access token.

`build.py` only writes the documentation files locally. The separate Maven assembler writes
`dist/maven/`; it downloads pinned release ZIPs and never rebuilds library binaries. Local docs-only
preview is fine, but do not manually deploy that directory without assembling the complete catalog.
The workflow is the deployment path for both. See [public Maven hosting](../docs/public-maven.md).

The planned Maven endpoint is not published yet. The committed catalog is empty; local publication
staging is not approval or evidence of a public download. Public Maven examples must use a version
from the approved catalog once populated, never assume the working version is already published.
