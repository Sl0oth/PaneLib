package dev.pane.mcef;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Builds a self-contained, asset-free HTML application for the client renderer. */
public final class ClientUiDocument {
    private final String title;
    private final String brand;
    private final ClientUiTheme theme;
    private final List<ClientUiPage> pages;
    private final String extraCss;
    private final String extraScript;
    private final String initialPageId;

    private ClientUiDocument(Builder builder) {
        title = ClientUiPage.html(builder.title);
        brand = ClientUiPage.html(builder.brand);
        theme = builder.theme;
        pages = List.copyOf(builder.pages);
        extraCss = builder.extraCss;
        extraScript = builder.extraScript;
        if (pages.isEmpty()) throw new IllegalStateException("A UI document needs at least one page");
        initialPageId = builder.initialPageId == null ? pages.getFirst().id() : builder.initialPageId;
        if (pages.stream().noneMatch(page -> page.id().equals(initialPageId))) {
            throw new IllegalStateException("Initial page is not part of this document: " + initialPageId);
        }
    }

    public static Builder builder(String title) { return new Builder(title); }
    public String initialPageId() { return initialPageId; }
    public int pageCount() { return pages.size(); }
    public String pageIdAt(int index) { return pages.get(index).id(); }

    public String dataUrl(double uiScale) {
        double scale = Math.max(1.0, uiScale);
        String html = buildHtml()
            .replace("__UI_SCALE__", Double.toString(scale))
            .replace("__UI_PERCENT__", Double.toString(100.0 / scale) + "%");
        return "data:text/html;base64," + Base64.getEncoder().encodeToString(html.getBytes(StandardCharsets.UTF_8));
    }

    private String buildHtml() {
        StringBuilder navigation = new StringBuilder();
        StringBuilder content = new StringBuilder();
        StringBuilder pageCss = new StringBuilder();
        for (ClientUiPage page : pages) {
            boolean initial = page.id().equals(initialPageId);
            navigation.append("<button class=\"ui-nav-item").append(initial ? " active" : "")
                .append("\" data-page-target=\"").append(page.id()).append("\"><span class=\"ui-nav-icon\">")
                .append(page.navigationIcon()).append("</span><span>").append(page.navigationLabel()).append("</span></button>");
            content.append("<section class=\"ui-page").append(initial ? " active" : "")
                .append("\" id=\"page-").append(page.id()).append("\" data-page=\"").append(page.id())
                .append("\" data-accent=\"").append(page.accent()).append("\">").append(page.markup()).append("</section>");
            pageCss.append(page.scopedCss()).append('\n');
        }

        return """
            <!doctype html><html lang="en"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1"><title>__TITLE__</title>
            <style>
              :root{__TOKENS__--ui-active-accent:var(--ui-accent);font-family:Inter,ui-sans-serif,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;color-scheme:dark;font-synthesis:none}
              *{box-sizing:border-box}html,body{width:100%;height:100%;margin:0;overflow:hidden}body{color:var(--ui-text);background:var(--ui-bg);user-select:none}button,input{font:inherit}button{color:inherit}
              .ui-stage{width:__UI_PERCENT__;height:__UI_PERCENT__;transform:scale(__UI_SCALE__);transform-origin:top left;padding:12px;background:var(--ui-bg);container:stage/size}
              .ui-shell{width:100%;height:100%;display:grid;grid-template-columns:184px minmax(0,1fr);overflow:hidden;border:1px solid var(--ui-border);border-radius:var(--ui-r-lg);background:var(--ui-bg)}
              .ui-sidebar{display:flex;flex-direction:column;padding:18px 14px;border-right:1px solid var(--ui-border);background:var(--ui-surface)}
              .ui-brand{display:flex;align-items:center;gap:10px;padding:0 7px 20px}.ui-brand-mark{width:30px;height:30px;border:1px solid var(--ui-border);border-radius:var(--ui-r-sm);display:grid;place-items:center;background:var(--ui-elevated);color:var(--ui-active-accent);font-weight:800}.ui-brand b{display:block;font-size:13px}.ui-brand small{display:block;margin-top:2px;color:var(--ui-muted);font-size:8px;letter-spacing:.12em;text-transform:uppercase}
              .ui-nav{display:grid;gap:4px}.ui-nav-label{padding:0 9px 8px;color:var(--ui-muted);font-size:8px;font-weight:750;letter-spacing:.15em;text-transform:uppercase}.ui-nav-item{position:relative;width:100%;height:38px;padding:0 10px;border:1px solid transparent;border-radius:var(--ui-r-sm);background:transparent;display:flex;align-items:center;gap:10px;color:var(--ui-muted);font-size:11px;font-weight:650;text-align:left;cursor:pointer;transition:color calc(160ms*var(--ui-motion)) ease,background calc(160ms*var(--ui-motion)) ease,border-color calc(160ms*var(--ui-motion)) ease,transform calc(160ms*var(--ui-motion)) ease}.ui-nav-item:hover{color:var(--ui-text);background:var(--ui-elevated)}.ui-nav-item:active{transform:scale(.985)}.ui-nav-item.active{color:var(--ui-text);background:var(--ui-elevated);border-color:var(--ui-border)}.ui-nav-item.active::before{content:"";position:absolute;left:-15px;width:3px;height:18px;border-radius:3px;background:var(--ui-active-accent)}.ui-nav-icon{width:18px;text-align:center;font-size:12px}
              .ui-sidebar-footer{margin-top:auto;padding:10px 7px 0;color:var(--ui-muted);font-size:8px;display:flex;align-items:center;justify-content:space-between}.ui-idle{display:flex;align-items:center;gap:6px}.ui-idle::before{content:"";width:5px;height:5px;border-radius:50%;background:#48d994}.ui-idle.busy::before{background:var(--ui-active-accent)}
              .ui-content{position:relative;min-width:0;min-height:0;overflow:hidden}.ui-page{position:absolute;inset:0;display:none;overflow:hidden;background:var(--ui-bg)}.ui-page.active{display:block}
              .ui-page-pad{height:100%;padding:22px 25px}.ui-page-head{display:flex;align-items:flex-start;justify-content:space-between;gap:18px;margin-bottom:16px}.ui-kicker{margin-bottom:6px;color:var(--ui-active-accent);font-size:8px;font-weight:800;letter-spacing:.15em;text-transform:uppercase}.ui-page h1{margin:0;font-size:28px;line-height:1.05;letter-spacing:-.035em}.ui-page-subtitle{margin:6px 0 0;color:var(--ui-muted);font-size:10px;line-height:1.45}
              .ui-card{border:1px solid var(--ui-border);border-radius:var(--ui-r-md);background:var(--ui-surface)}.ui-card-raised{background:var(--ui-elevated)}
              .ui-button{height:30px;padding:0 12px;border:1px solid var(--ui-border);border-radius:var(--ui-r-sm);font-size:9px;font-weight:750;cursor:pointer;transition:transform calc(150ms*var(--ui-motion)) ease,filter calc(150ms*var(--ui-motion)) ease,background calc(150ms*var(--ui-motion)) ease}.ui-button:hover{filter:brightness(1.1)}.ui-button:active{transform:scale(.965)}.button-primary{border-color:color-mix(in srgb,var(--ui-active-accent),white 16%);background:var(--ui-active-accent);color:#07111b}.button-secondary{background:var(--ui-elevated)}.button-ghost{background:transparent}.button-danger{border-color:#7b3439;background:#3a1d20;color:#ffb9bd}.shape-square{border-radius:2px}.shape-rounded{border-radius:var(--ui-r-sm)}.shape-soft{border-radius:var(--ui-r-md)}.shape-pill{border-radius:999px}
              .ui-badge{display:inline-flex;align-items:center;height:20px;padding:0 7px;border:1px solid var(--ui-border);border-radius:999px;font-size:7px;font-weight:800;letter-spacing:.06em;text-transform:uppercase}.tone-success{border-color:#24543b;background:#152a20;color:#75d69c}.tone-neutral{color:var(--ui-muted);background:var(--ui-elevated)}.tone-accent{border-color:color-mix(in srgb,var(--ui-active-accent),transparent 55%);color:var(--ui-active-accent)}
              .ui-toggle-row{display:flex;align-items:center;justify-content:space-between;gap:12px}.ui-toggle{width:31px;height:18px;padding:2px;border:0;border-radius:999px;background:#343941;cursor:pointer;transition:background calc(180ms*var(--ui-motion)) ease}.ui-toggle i{display:block;width:14px;height:14px;border-radius:50%;background:#e9edf3;transition:transform calc(220ms*var(--ui-motion)) cubic-bezier(.2,.9,.25,1.15)}.ui-toggle.on{background:var(--ui-active-accent)}.ui-toggle.on i{transform:translateX(13px)}
              .ui-slider{-webkit-appearance:none;appearance:none;height:4px;border-radius:999px;background:#343941;outline:none}.ui-slider::-webkit-slider-thumb{-webkit-appearance:none;appearance:none;width:13px;height:13px;border:2px solid #eef2f7;border-radius:50%;background:var(--ui-active-accent);cursor:pointer;transition:transform calc(150ms*var(--ui-motion)) ease}.ui-slider::-webkit-slider-thumb:hover{transform:scale(1.15)}
              .ui-field,.ui-select{height:30px;padding:0 10px;border:1px solid var(--ui-border);border-radius:var(--ui-r-sm);outline:0;background:var(--ui-elevated);color:var(--ui-text);font-size:9px}.ui-field::placeholder{color:var(--ui-muted)}.ui-field:focus,.ui-select:focus{border-color:var(--ui-active-accent)}
              .ui-button:focus-visible,.ui-toggle:focus-visible,.ui-nav-item:focus-visible,.ui-slider:focus-visible,.ui-field:focus-visible,.ui-select:focus-visible{outline:2px solid var(--ui-active-accent);outline-offset:2px}
              @container stage (max-width:1150px){.ui-shell{grid-template-columns:155px minmax(0,1fr)}.ui-sidebar{padding-inline:10px}.ui-page-pad{padding:17px 19px}.ui-nav-item.active::before{left:-11px}}
              @container stage (max-height:720px){.ui-stage{padding:7px}.ui-sidebar{padding-top:12px}.ui-brand{padding-bottom:11px}.ui-nav-item{height:32px}.ui-page-pad{padding-top:14px;padding-bottom:13px}.ui-page-head{margin-bottom:10px}.ui-page h1{font-size:24px}}
              @media(prefers-reduced-motion:reduce){:root{--ui-motion:0!important}}
              __PAGE_CSS__
              __EXTRA_CSS__
            </style></head><body>
              <div class="ui-stage"><div class="ui-shell">
                <aside class="ui-sidebar"><div class="ui-brand"><span class="ui-brand-mark">◇</span><span><b>__BRAND__</b><small>Client UI library</small></span></div><div class="ui-nav-label">Showcase</div><nav class="ui-nav">__NAV__</nav><div class="ui-sidebar-footer"><span>H · open</span><span class="ui-idle" id="ui-status">cached</span></div></aside>
                <main class="ui-content">__PAGES__</main>
              </div></div>
              <script>
                (()=>{
                  const state={page:'__INITIAL__',values:{},activeAnimations:0};
                  const query=request=>{if(typeof window.clientUiQuery==='function')window.clientUiQuery({request,onSuccess:()=>{},onFailure:()=>console.debug(`CLIENTUI|${request}`)});else console.debug(`CLIENTUI|${request}`)};
                  const status=document.querySelector('#ui-status');
                  const setBusy=busy=>{status.classList.toggle('busy',busy);status.textContent=busy?'painting':'cached'};
                  const animate=(element,keyframes,options={})=>{
                    if(!element||matchMedia('(prefers-reduced-motion: reduce)').matches||Number(getComputedStyle(document.documentElement).getPropertyValue('--ui-motion'))===0)return;
                    state.activeAnimations++;setBusy(true);const animation=element.animate(keyframes,{duration:260,easing:'cubic-bezier(.2,.82,.2,1)',...options});
                    animation.finished.catch(()=>{}).finally(()=>{state.activeAnimations=Math.max(0,state.activeAnimations-1);if(!state.activeAnimations)setBusy(false)});return animation;
                  };
                  const reportBounds=()=>{
                    document.querySelectorAll('.ui-page.active [data-native]').forEach(el=>{const r=el.getBoundingClientRect();query(`bound|${el.dataset.native}|${r.x}|${r.y}|${r.width}|${r.height}`)});
                  };
                  let boundsFrame=0;const queueBounds=()=>{cancelAnimationFrame(boundsFrame);boundsFrame=requestAnimationFrame(()=>requestAnimationFrame(reportBounds))};
                  const showPage=id=>{
                    const next=document.querySelector(`[data-page="${CSS.escape(id)}"]`);if(!next||state.page===id)return;
                    document.querySelectorAll('.ui-page').forEach(x=>x.classList.remove('active'));document.querySelectorAll('.ui-nav-item').forEach(x=>x.classList.toggle('active',x.dataset.pageTarget===id));
                    next.classList.add('active');state.page=id;document.documentElement.style.setProperty('--ui-active-accent',next.dataset.accent);query(`page|${id}`);animate(next,[{opacity:0,transform:'translateY(7px)'},{opacity:1,transform:'none'}]);queueBounds();
                  };
                  document.querySelectorAll('[data-page-target]').forEach(x=>x.addEventListener('click',()=>showPage(x.dataset.pageTarget)));
                  document.addEventListener('click',event=>{const action=event.target.closest('[data-action]');if(action){query(`action|${action.dataset.action}`);animate(action,[{transform:'scale(.96)'},{transform:'scale(1)'}],{duration:180})}const toggle=event.target.closest('[data-toggle]');if(toggle){toggle.classList.toggle('on');toggle.setAttribute('aria-checked',toggle.classList.contains('on'));state.values[toggle.dataset.toggle]=toggle.classList.contains('on');query(`value|${toggle.dataset.toggle}|${state.values[toggle.dataset.toggle]}`)}});
                  document.addEventListener('input',event=>{if(event.target.matches('[data-value]')){state.values[event.target.dataset.value]=event.target.value;query(`value|${event.target.dataset.value}|${event.target.value}`)}});
                  let resizeFrame=0;addEventListener('resize',()=>{cancelAnimationFrame(resizeFrame);resizeFrame=requestAnimationFrame(queueBounds)});
                  addEventListener('keydown',event=>{if(event.key>='1'&&event.key<='9'){const nav=document.querySelectorAll('[data-page-target]')[Number(event.key)-1];if(nav)showPage(nav.dataset.pageTarget)}});
                  window.ClientUI={showPage,animate,snapshot:()=>structuredClone(state),setToken:(name,value)=>document.documentElement.style.setProperty(`--ui-${name}`,value),invalidate:queueBounds};
                  const first=document.querySelector('.ui-page.active');document.documentElement.style.setProperty('--ui-active-accent',first.dataset.accent);query(`page|${state.page}`);queueBounds();
                })();
                __EXTRA_SCRIPT__
              </script>
            </body></html>
            """
            .replace("__TITLE__", title)
            .replace("__BRAND__", brand)
            .replace("__TOKENS__", theme.cssVariables())
            .replace("__NAV__", navigation)
            .replace("__PAGES__", content)
            .replace("__PAGE_CSS__", pageCss)
            .replace("__EXTRA_CSS__", extraCss)
            .replace("__EXTRA_SCRIPT__", extraScript)
            .replace("__INITIAL__", initialPageId());
    }

    public static final class Builder {
        private final String title;
        private String brand = "Client UI";
        private ClientUiTheme theme = ClientUiTheme.dark();
        private final List<ClientUiPage> pages = new ArrayList<>();
        private String extraCss = "";
        private String extraScript = "";
        private String initialPageId;

        private Builder(String title) { this.title = title; }
        public Builder brand(String brand) { this.brand = brand; return this; }
        public Builder theme(ClientUiTheme theme) { this.theme = theme; return this; }
        public Builder page(ClientUiPage page) { pages.add(page); return this; }
        public Builder initialPage(String pageId) { initialPageId = pageId; return this; }
        public Builder extraCss(String css) { extraCss = css == null ? "" : css; return this; }
        /** Adds document-owned JavaScript after the ClientUI runtime is installed. */
        public Builder extraScript(String script) { extraScript = script == null ? "" : script; return this; }
        public ClientUiDocument build() { return new ClientUiDocument(this); }
    }
}
