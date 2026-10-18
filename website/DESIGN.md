# PaneLib docs site: design notes

A dark charcoal **field manual** for a native Minecraft UI toolkit, keeping the navy masthead, code panels and
bright blue accents. Dark is the only theme: there is no toggle. Vanilla HTML/CSS/JS in `src/`;
the parent builder copies it to `dist/` and supplies `content.json`, `pygments.css` and `assets/`.
Every path is relative, so the same files work from `localhost/` and from GitHub Pages `/PaneLib/`.

## Visual system

Background and foreground variables are kept separate: no colour serves as both text and a surface.

| Token | Value | Use |
| --- | --- | --- |
| bg | `#1C2024` | charcoal page surface |
| surface / surface-2 | `#24292E` / `#2D3339` | rail, plates, specs, pager, search panel / inline code, table headers |
| hover / active | `#2A3540` / `#17324A` | rail hover; current rail item and focused search result |
| navy / navy-deep / navy-line | `#102E45` / `#0B2436` / `#2D5571` | masthead, footer, plate tab, all code panels (backgrounds only) |
| text / text-2 | `#E7EAEC` / `#A8B4BD` | body text / secondary text (foreground only) |
| on-navy | `#FFFEF8` | text and logo strokes on navy |
| frame | `#6E8291` | button, plate, pager and search frames |
| rule | `#3A434B` | ruled lines, grid patch |
| blue | `#1685D9` | active markers, tabs, section rules, offset shadows |
| blue-text / blue-btn | `#6CBCF5` / `#1170BF` | links on charcoal / primary button with white text |
| cyan | `#6AD7FF` | focus ring, accents on navy |

- Code: article and home code blocks are both navy panels. `build.py` generates Pygments `github-dark`,
  whose light tokens suit them. `.codeblock .codehilite` clears that theme's own background so the navy shows.
  Fallback token colours are light and apply only where the Pygments CSS defines none.
- Screenshots and GIFs are shown unaltered on a `surface` frame.

- Type: local `assets/Roboto-Variable.ttf` only. Display 800 weight, slightly narrowed (`font-stretch`, ignored if
  the file has no width axis), tight tracking; nav 450/650; body 400 at 17px / 1.65 (16px on mobile);
  system monospace for code and the small uppercase eyebrows.
- Details: navy masthead with a blue rule, stacked-window SVG mark, fine offset borders on plates and the code
  panel, a ruled-grid patch only behind screenshots, one cut corner (the "Plate 1" tab), `§NN` section numbers.
- No hero gradients, card grids, emoji, counters or theme switcher.

## Structure and behaviour

The introduction and all eleven Markdown guides form one continuous scrolling document.
The desktop rail and mobile drawer jump to section anchors. Search also jumps within the
same document; there are no guide routes, previous/next pages, or hidden article views.
Old `#/docs/{slug}?anchor={id}` URLs resolve to the corresponding section anchor.

Each guide has a numbered heading and a source link. Markdown heading levels are shifted
below the guide title, and ids are prefixed with the guide slug to prevent collisions.
The rail tracks the current section while scrolling. The dark palette, copy controls,
search keyboard controls, responsive drawer and existing client captures are retained.
Guide animations play on request rather than all running along the long document.

## Verification and limitations

- Browser checks covered the dark theme, search, copy, mobile layout, eleven visible guide sections, unique ids, and section links. Main screenshot dimensions are reserved before loading.
- Footer MIT link points to the repository license.
- "Browse the API" links to the UI basics guide; there is no hosted Javadoc.
- Dark-theme contrast is estimated, not measured. In the browser, check `text-2` on `surface`, `blue-text` links
  and `github-dark` comment tokens on navy.
