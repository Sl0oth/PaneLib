(() => {
  'use strict';

  const GROUPS = [
    ['Start here', ['getting-started', 'installation']],
    ['Build interfaces', ['ui-basics', 'input-and-focus', 'drawing-and-theming']],
    ['In the game', ['huds-and-composable-motion', 'minecraft-integration']],
    ['Go deeper', ['layered-ui-and-motion', 'paper-craft', 'performance', 'status']],
  ];
  const FALLBACK = {
    'getting-started': 'Build your first screen', installation: 'Install locally', 'ui-basics': 'UI basics',
    'input-and-focus': 'Styled inputs and modal focus', 'drawing-and-theming': 'Drawing, themes and animation',
    'huds-and-composable-motion': 'HUDs and composable motion', 'minecraft-integration': 'Minecraft integration',
    'layered-ui-and-motion': 'Layered UI and motion', 'paper-craft': 'Paper-craft recipe',
    performance: 'Performance diagnostics', status: 'Status',
  };
  const SLUG = /^[a-z0-9][a-z0-9-]{0,80}$/;
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');

  const state = { pages: new Map(), seq: [], repo: 'https://github.com/Sl0oth/PaneLib', loaded: false, error: null, slug: null };
  const homeView = $('#site-home'), docView = $('#site-doc'), main = $('#site-main');

  /* ---------- content ---------- */
  function buildSequence() {
    const seq = [];
    let n = 1;
    for (const [group, slugs] of GROUPS) {
      for (const slug of slugs) {
        const p = state.pages.get(slug);
        if (state.loaded && !p) continue;
        seq.push({ slug, group, num: String(n++).padStart(2, '0'), title: p?.title || FALLBACK[slug], description: p?.description || '' });
      }
    }
    const known = new Set(seq.map((s) => s.slug));
    for (const p of state.pages.values()) {
      if (!known.has(p.slug)) seq.push({ slug: p.slug, group: 'More', num: String(n++).padStart(2, '0'), title: p.title, description: p.description || '' });
    }
    state.seq = seq;
  }

  async function load() {
    try {
      const res = await fetch('./content.json', { cache: 'no-cache' });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      if (!Array.isArray(data.pages)) throw new Error('missing pages');
      for (const p of data.pages) {
        if (p && typeof p.slug === 'string' && SLUG.test(p.slug) && typeof p.html === 'string') {
          state.pages.set(p.slug, { ...p, title: String(p.title || p.slug), text: String(p.text || '') });
        }
      }
      if (typeof data.repository === 'string' && data.repository.startsWith('https://github.com/')) state.repo = data.repository.replace(/\/$/, '');
      if (typeof data.version === 'string') $$('[data-version]').forEach((e) => { e.textContent = data.version; });
      $$('[data-repo]').forEach((a) => { a.href = state.repo; });
      $$('[data-repo-path]').forEach((a) => { a.href = state.repo + a.dataset.repoPath; });
      state.loaded = true;
    } catch (err) {
      state.error = err;
    }
    buildSequence();
    renderNav();
    renderManual();
  }

  /* ---------- navigation ---------- */
  function groupsOf(seq) {
    const m = new Map();
    for (const s of seq) { if (!m.has(s.group)) m.set(s.group, []); m.get(s.group).push(s); }
    return m;
  }

  function renderNav() {
    let rail = '', contents = '';
    for (const [group, items] of groupsOf(state.seq)) {
      rail += `<div class="rail__group"><h2>${esc(group)}</h2><ul>${items.map((s) =>
        `<li><a href="#guide-${s.slug}" data-slug="${s.slug}"><span class="rail__num">${s.num}</span><span>${esc(s.title)}</span></a></li>`).join('')}</ul></div>`;
      contents += `<div><h3>${esc(group)}</h3><ol>${items.map((s) =>
        `<li><a href="#guide-${s.slug}"><span class="num">${s.num}</span>${esc(s.title)}</a>${s.description ? `<p>${esc(s.description)}</p>` : ''}</li>`).join('')}</ol></div>`;
    }
    $('#site-rail-nav').innerHTML = rail;
    $('#site-contents').innerHTML = contents;
    markRail();
  }

  function markRail() {
    $$('#site-rail-nav a').forEach((a) => {
      if (a.dataset.slug === state.slug) a.setAttribute('aria-current', 'location'); else a.removeAttribute('aria-current');
    });
  }

  /* ---------- one continuous manual ---------- */
  function guideAnchor(slug, heading) {
    return 'guide-' + slug + (heading ? '--' + heading : '');
  }

  // Keep existing shared links working while every guide stays in the document.
  function localAnchor(href, slug) {
    const legacy = /^#\/docs\/([a-z0-9-]+)(?:\?(.*))?$/.exec(href);
    if (legacy) return '#' + guideAnchor(legacy[1], new URLSearchParams(legacy[2] || '').get('anchor'));
    if (href === '#/' || href === '#') return '#site-home-title';
    if (href.startsWith('#') && !href.startsWith('#guide-') && !href.startsWith('#site-')) {
      return '#' + guideAnchor(slug, href.slice(1));
    }
    return href;
  }

  function renderManual() {
    docView.hidden = false;
    if (state.error) {
      docView.innerHTML = `<div class="status-view"><h2>Guides could not be loaded.</h2><p>Reload to try again, or <a href="${esc(state.repo)}/tree/main/docs">read the Markdown on GitHub</a>.</p></div>`;
      return;
    }
    const manual = document.createDocumentFragment();
    for (const meta of state.seq) {
      const page = state.pages.get(meta.slug);
      const tpl = document.createElement('template');
      tpl.innerHTML = page.html;
      sanitize(tpl.content);
      tpl.content.querySelector('h1')?.remove();
      // Heading ids are local to each Markdown source; namespace them in the combined page.
      $$('[id]', tpl.content).forEach((el) => { el.id = guideAnchor(meta.slug, el.id); });
      $$('h2, h3, h4, h5', tpl.content).forEach((old) => {
        const level = +old.tagName[1];
        const heading = document.createElement('h' + (level + 1));
        for (const attr of old.attributes) heading.setAttribute(attr.name, attr.value);
        heading.classList.add('manual-heading-' + level);
        while (old.firstChild) heading.appendChild(old.firstChild);
        old.replaceWith(heading);
      });
      enhance(tpl.content, meta.slug);
      const article = document.createElement('article');
      article.className = 'manual-section';
      article.id = guideAnchor(meta.slug);
      article.dataset.slug = meta.slug;
      article.setAttribute('aria-labelledby', article.id + '-title');
      article.innerHTML = `<header class="doc-head">
        <p class="eyebrow"><span class="num">${meta.num}</span>${esc(meta.group)}</p>
        <h2 id="${article.id}-title" tabindex="-1">${esc(page.title)}</h2>
        <p class="desc">${esc(page.description || '')}</p>
        <a class="src" href="${esc(state.repo)}/blob/main/docs/${meta.slug}.md">View source on GitHub</a>
      </header><div class="prose"></div>`;
      $('.prose', article).appendChild(tpl.content);
      manual.appendChild(article);
    }
    docView.replaceChildren(manual);
    $$('.gif', docView).forEach(setupGif);
    $$('a[href^="#"]', homeView).forEach((a) => { a.href = localAnchor(a.getAttribute('href'), 'getting-started'); });
    $$('.masthead a[href="#/"], .footer a[href^="#/"]').forEach((a) => {
      a.href = localAnchor(a.getAttribute('href'), 'getting-started');
    });
    updateActiveSection();
  }

  async function jumpToAnchor() {
    await ready;
    closeDrawer(false);
    let hash = location.hash || '#site-home-title';
    const canonical = localAnchor(hash, 'getting-started');
    if (canonical !== hash) history.replaceState(null, '', canonical);
    let id;
    try { id = decodeURIComponent(canonical.slice(1)); } catch { return; }
    const target = document.getElementById(id);
    if (!target) return;
    (target.matches('article') ? $('.doc-head', target) : target).scrollIntoView();
    const focus = target.matches('article') ? $('h2', target) : target;
    focus.setAttribute('tabindex', '-1');
    focus.focus({ preventScroll: true });
    updateActiveSection();
  }

  function sanitize(root) {
    $$('script, iframe, object, embed, base, meta, link, style', root).forEach((e) => e.remove());
    for (const el of $$('*', root)) {
      for (const at of Array.from(el.attributes)) {
        const v = at.value.replace(/[\s\u0000-\u001f]/g, '').toLowerCase();
        if (at.name.startsWith('on') || ((at.name === 'href' || at.name === 'src') && v.startsWith('javascript:'))) el.removeAttribute(at.name);
      }
    }
  }

  function enhance(root, slug) {
    for (const a of $$('a[href]', root)) {
      const href = a.getAttribute('href');
      if (href.startsWith('#')) a.setAttribute('href', localAnchor(href, slug));
      else if (/^(?!\w+:|\/|#)[^?#]*\.md(#.*)?$/.test(href)) a.setAttribute('href', `${state.repo}/blob/main/docs/${href.replace(/^\.\//, '')}`);
    }
    for (const h of $$('h3[id], h4[id], h5[id], h6[id]', root)) {
      if (h.querySelector('a')) continue;
      const a = document.createElement('a');
      a.className = 'self';
      a.href = `#${h.id}`;
      while (h.firstChild) a.appendChild(h.firstChild);
      h.appendChild(a);
    }
    $$('table', root).forEach((t, n) => {
      const wrap = document.createElement('div');
      wrap.className = 'table-wrap';
      wrap.tabIndex = 0;
      wrap.setAttribute('role', 'region');
      wrap.setAttribute('aria-label', `Table ${n + 1}`);
      t.replaceWith(wrap); wrap.appendChild(t);
    });
    for (const pre of $$('pre', root)) {
      const host = pre.closest('.codehilite') || pre;
      if (host.parentElement?.classList.contains('codeblock')) continue;
      const lang = (/language-([\w+-]+)/.exec(pre.querySelector('code')?.className || '') || [])[1] || host.dataset?.lang || 'Code';
      const box = document.createElement('div');
      box.className = 'codeblock codeblock--dark';
      box.innerHTML = `<div class="codeblock__bar"><span>${esc(lang)}</span><button class="copy" type="button">Copy<span class="vh"> code</span></button></div>`;
      host.replaceWith(box); box.appendChild(host);
      pre.tabIndex = 0;
    }
    for (const img of $$('img', root)) {
      img.loading = 'lazy'; img.decoding = 'async';
      if (!img.hasAttribute('alt')) img.alt = '';
      if (/\.gif($|\?)/i.test(img.getAttribute('src') || '')) {
        const slot = document.createElement('div');
        slot.className = 'gif';
        slot.dataset.gif = img.getAttribute('src');
        slot.dataset.alt = img.alt;
        slot.dataset.width = img.getAttribute('width') || '';
        slot.dataset.height = img.getAttribute('height') || '';
        img.replaceWith(slot);
      }
    }
  }

  /* ---------- section tracking ---------- */
  let scrollTick = false;
  function updateActiveSection() {
    scrollTick = false;
    let active = null;
    for (const section of $$('.manual-section', docView)) {
      if (section.getBoundingClientRect().top <= 140) active = section.dataset.slug;
      else break;
    }
    state.slug = active;
    markRail();
  }
  addEventListener('scroll', () => {
    if (!scrollTick) { scrollTick = true; requestAnimationFrame(updateActiveSection); }
  }, { passive: true });

  /* ---------- animated captures ---------- */
  function setupGif(slot) {
    const show = () => {
      slot.innerHTML = `<img src="${esc(slot.dataset.gif)}" alt="${esc(slot.dataset.alt)}"${slot.dataset.width && slot.dataset.height ? ` width="${esc(slot.dataset.width)}" height="${esc(slot.dataset.height)}"` : ''}><button class="gif-stop" type="button">Stop animation</button>`;
      $('button', slot).addEventListener('click', () => { gate(); $('button', slot).focus(); });
    };
    const gate = () => {
      slot.innerHTML = `<div class="gate"><span class="eyebrow">Animated capture</span><p>${esc(slot.dataset.alt)}</p><button class="btn" type="button">Play animation</button></div>`;
      $('button', slot).addEventListener('click', () => { show(); $('button', slot).focus(); });
    };
    if ('onOpen' in slot.dataset) {
      slot.closest('details').addEventListener('toggle', (e) => { if (e.target.open && !$('img', slot)) show(); });
      return;
    }
    (reduced.matches || slot.closest('#site-doc')) ? gate() : show();
  }

  /* ---------- modal helpers ---------- */
  const background = () => [$('.masthead'), main, $('.footer'), $('.skip-link')];
  function trap(container, e) {
    if (e.key !== 'Tab') return;
    const items = $$('a[href], button, input, [tabindex]:not([tabindex="-1"])', container).filter((el) => el.offsetParent !== null || el === document.activeElement);
    if (!items.length) return;
    const first = items[0], last = items[items.length - 1];
    if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
    else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
  }

  /* drawer */
  const drawer = $('#site-drawer'), backdrop = $('.backdrop'), menuBtn = $('[data-drawer-open]');
  const isMobile = matchMedia('(max-width: 959px)');
  function openDrawer() {
    drawer.classList.add('is-open');
    drawer.setAttribute('role', 'dialog'); drawer.setAttribute('aria-modal', 'true');
    backdrop.hidden = false; menuBtn.setAttribute('aria-expanded', 'true');
    background().forEach((el) => { el.inert = true; });
    const current = $('a[aria-current]', drawer) || $('a, button', drawer);
    setTimeout(() => current?.focus(), 30);
  }
  function closeDrawer(restore = true) {
    if (!drawer.classList.contains('is-open')) return;
    drawer.classList.remove('is-open');
    drawer.removeAttribute('role'); drawer.removeAttribute('aria-modal');
    backdrop.hidden = true; menuBtn.setAttribute('aria-expanded', 'false');
    background().forEach((el) => { el.inert = false; });
    if (restore) menuBtn.focus();
  }
  menuBtn.addEventListener('click', openDrawer);
  $$('[data-drawer-close]').forEach((b) => b.addEventListener('click', () => closeDrawer()));
  drawer.addEventListener('keydown', (e) => { if (drawer.classList.contains('is-open')) trap(drawer, e); });
  isMobile.addEventListener('change', () => { if (!isMobile.matches) closeDrawer(false); });

  /* search */
  const search = $('#site-search'), input = $('#site-search-input'), results = $('#site-search-results'), status = $('#site-search-status');
  let searchTrigger = null;
  function openSearch() {
    if (!search.hidden) return;
    closeDrawer(false);
    searchTrigger = document.activeElement;
    search.hidden = false;
    [...background(), $('.shell')].forEach((el) => { el.inert = true; });
    input.focus(); input.select();
    runSearch();
  }
  function closeSearch(restore = true) {
    if (search.hidden) return;
    search.hidden = true;
    [...background(), $('.shell')].forEach((el) => { el.inert = false; });
    if (restore && searchTrigger?.isConnected) searchTrigger.focus();
  }

  function highlight(text, terms) {
    const re = new RegExp(`(${terms.map((t) => t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')})`, 'gi');
    return text.split(re).map((part, i) => (i % 2 ? `<mark>${esc(part)}</mark>` : esc(part))).join('');
  }

  function runSearch() {
    const q = input.value.trim().toLowerCase().slice(0, 100);
    const terms = q.split(/\s+/).filter(Boolean).slice(0, 8);
    if (!state.loaded) { status.textContent = state.error ? 'Search is unavailable: content.json could not be loaded.' : 'Loading the search index…'; results.innerHTML = ''; return; }
    if (!terms.length) { status.textContent = `Type to search ${state.pages.size} guides.`; results.innerHTML = ''; return; }
    const hits = [];
    for (const s of state.seq) {
      const p = state.pages.get(s.slug);
      const title = p.title.toLowerCase(), text = p.text.replace(/\s+/g, ' '), lower = text.toLowerCase();
      const heads = Array.isArray(p.headings) ? p.headings : [];
      let score = 0, ok = true;
      for (const t of terms) {
        const inTitle = title.includes(t), inHead = heads.some((h) => String(h.title).toLowerCase().includes(t));
        const count = lower.split(t).length - 1;
        if (!inTitle && !inHead && !count) { ok = false; break; }
        score += (inTitle ? 10 : 0) + (inHead ? 5 : 0) + Math.min(count, 10);
      }
      if (!ok) continue;
      const head = heads.find((h) => terms.some((t) => String(h.title).toLowerCase().includes(t)));
      const at = Math.max(lower.indexOf(q), lower.indexOf(terms[0]));
      const start = Math.max(0, at - 60);
      const excerpt = (start ? '…' : '') + text.slice(start, start + 170).trim() + (start + 170 < text.length ? '…' : '');
      hits.push({ s, score, head, excerpt });
    }
    hits.sort((a, b) => b.score - a.score);
    status.textContent = hits.length ? `${hits.length} result${hits.length === 1 ? '' : 's'}` : `No guides match “${input.value.trim()}”. Try a class name such as UiScreen.`;
    results.innerHTML = hits.slice(0, 12).map(({ s, head, excerpt }) => `<li><a href="#${guideAnchor(s.slug, head?.id)}">
      <small>${s.num} · ${esc(s.group)}${head ? ` · ${esc(head.title)}` : ''}</small><b>${highlight(s.title, terms)}</b><span>${highlight(excerpt, terms)}</span></a></li>`).join('');
  }

  input.addEventListener('input', runSearch);
  $$('[data-search-open]').forEach((b) => b.addEventListener('click', openSearch));
  $$('[data-search-close]').forEach((b) => b.addEventListener('click', () => closeSearch()));
  results.addEventListener('click', (e) => { if (e.target.closest('a')) closeSearch(false); });
  search.addEventListener('keydown', (e) => {
    trap(search, e);
    const links = $$('a', results);
    const idx = links.indexOf(document.activeElement);
    if (e.key === 'ArrowDown' && links.length) { e.preventDefault(); links[Math.min(idx + 1, links.length - 1)].focus(); }
    if (e.key === 'ArrowUp' && links.length) { e.preventDefault(); (idx <= 0 ? input : links[idx - 1]).focus(); }
    if (e.key === 'Enter' && document.activeElement === input && links[0]) { e.preventDefault(); links[0].click(); }
  });
  const mac = /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent);
  $$('[data-shortcut]').forEach((k) => { k.textContent = mac ? '⌘K' : 'Ctrl K'; });

  /* ---------- global keys & clicks ---------- */
  document.addEventListener('keydown', (e) => {
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); search.hidden ? openSearch() : closeSearch(); }
    else if (e.key === 'Escape') { if (!search.hidden) closeSearch(); else closeDrawer(); }
  });

  const announcer = $('#site-announcer');
  const announce = (msg) => { announcer.textContent = ''; setTimeout(() => { announcer.textContent = msg; }, 50); };

  document.addEventListener('click', async (e) => {
    const skip = e.target.closest('.skip-link');
    if (skip) { e.preventDefault(); main.focus(); return; }
    const link = e.target.closest('a[href^="#"]');
    if (link && link.getAttribute('href') === location.hash) { e.preventDefault(); jumpToAnchor(); return; }
    const btn = e.target.closest('.copy');
    if (!btn) return;
    const code = btn.closest('.codeblock').querySelector('pre');
    const text = code.textContent.replace(/\n$/, '');
    const label = btn.firstChild;
    try {
      if (!navigator.clipboard?.writeText) throw new Error('unavailable');
      await navigator.clipboard.writeText(text);
      label.textContent = 'Copied';
      announce('Code copied to clipboard.');
    } catch {
      const range = document.createRange();
      range.selectNodeContents(code);
      getSelection().removeAllRanges(); getSelection().addRange(range);
      label.textContent = 'Copy failed';
      announce(`Copying is not available here. The code is selected; press ${mac ? 'Command' : 'Control'} C to copy it.`);
    }
    setTimeout(() => { label.textContent = 'Copy'; }, 2200);
  });

  /* ---------- boot ---------- */
  document.body.dataset.view = 'single';
  buildSequence();
  renderNav();
  $$('.gif', homeView).forEach(setupGif);
  reduced.addEventListener('change', () => $$('.gif:not([data-on-open])').forEach(setupGif));
  const ready = load();
  addEventListener('hashchange', jumpToAnchor);
  // Initial deep links can point to sections that arrive with content.json.
  if (location.hash) jumpToAnchor();
})();
