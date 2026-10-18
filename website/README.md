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

Open `http://127.0.0.1:4173/`. All eleven guides appear below the introduction in one continuous document. Menu and search links jump to section anchors; old guide URLs still jump to the matching section. The site works under a GitHub Pages subdirectory.
Generated `dist/`, the environment and copied Claude reference packet are ignored. Local fonts
retain their included OFL license. Original screenshots/captures are from PaneLib's actual client.

The `Documentation` workflow builds and deploys approved changes on `main`. The builder only writes
local files; it does not upload JARs or publish library releases.
