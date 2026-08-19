package dev.clientui.demo;

import dev.clientui.api.ClientUiComponents;
import dev.clientui.api.ClientUiDocument;
import dev.clientui.api.ClientUiPage;
import dev.clientui.api.ClientUiScreen;
import dev.clientui.api.ClientUiTheme;
import dev.clientui.api.NativeOverlayContext;
import net.minecraft.world.item.Items;

import static dev.clientui.api.ClientUiComponents.ButtonStyle.GHOST;
import static dev.clientui.api.ClientUiComponents.ButtonStyle.PRIMARY;
import static dev.clientui.api.ClientUiComponents.ButtonStyle.SECONDARY;
import static dev.clientui.api.ClientUiComponents.Shape.PILL;
import static dev.clientui.api.ClientUiComponents.Shape.ROUNDED;
import static dev.clientui.api.ClientUiComponents.Shape.SOFT;

/** Five real mod UI patterns composed with the public library API. */
public final class DemoShowcase {
    private DemoShowcase() {}

    public static ClientUiScreen createScreen() {
        return new ClientUiScreen(document(), DemoShowcase::renderNativeLayer,
            (type, key, value) -> System.out.println("[Client UI demo] " + type + " " + key + " " + value));
    }

    private static ClientUiDocument document() {
        String initialPage = System.getProperty("clientui.demo.page", "discover");
        return ClientUiDocument.builder("Client UI Capability Lab")
            .brand("Client UI Lab")
            .theme(ClientUiTheme.dark())
            .page(discoverPage())
            .page(loadoutPage())
            .page(waypointsPage())
            .page(performancePage())
            .page(settingsPage())
            .initialPage(initialPage)
            .extraCss("""
                .demo-actions{display:flex;align-items:center;gap:7px}.demo-section-title{font-size:11px;font-weight:750}.demo-dim{color:var(--ui-muted)}
                .demo-scroll{overflow:auto;scrollbar-width:thin;scrollbar-color:var(--ui-border) transparent}
                """)
            .build();
    }

    private static ClientUiPage discoverPage() {
        String install = ClientUiComponents.button("Install", "install-sodium", PRIMARY, ROUNDED);
        String installed = ClientUiComponents.button("Installed", "installed", SECONDARY, ROUNDED);
        String update = ClientUiComponents.button("Update all", "update-all", PRIMARY, PILL);
        String markup = """
            <div class="ui-page-pad discover-wrap">
              <header class="ui-page-head"><div><div class="ui-kicker">Mod library</div><h1>Discover</h1><p class="ui-page-subtitle">Find, install and update client mods without leaving the game.</p></div><div class="demo-actions"><div class="discover-search">⌕ <span>Search 18,400 projects</span><kbd>⌘ K</kbd></div>%s</div></header>
              <div class="discover-layout">
                <section class="discover-main demo-scroll">
                  <article class="ui-card discover-feature"><div class="project-icon sodium">S</div><div><span class="ui-badge tone-success">Featured</span><h2>Sodium</h2><p>The fastest rendering engine for Minecraft, ready for this version.</p><div class="project-meta"><span>41.2M downloads</span><span>Fabric 26.2</span><span>Client</span></div></div>%s</article>
                  <div class="discover-heading"><span>Recommended for you</span><button class="text-button" data-action="view-all">View all →</button></div>
                  <div class="project-grid">
                    <article class="ui-card project-card"><div class="project-icon iris">I</div><div class="project-copy"><b>Iris Shaders</b><small>Beautiful shaders, compatible with Sodium.</small><span>Graphics · 18.7M</span></div>%s</article>
                    <article class="ui-card project-card"><div class="project-icon zoom">Z</div><div class="project-copy"><b>Zoomify</b><small>A zoom mod with useful customization.</small><span>Utility · 3.1M</span></div>%s</article>
                    <article class="ui-card project-card"><div class="project-icon menu">M</div><div class="project-copy"><b>Mod Menu</b><small>View and configure installed mods.</small><span>Management · 22M</span></div>%s</article>
                    <article class="ui-card project-card"><div class="project-icon light">L</div><div class="project-copy"><b>LambDynamicLights</b><small>Dynamic handheld and entity lighting.</small><span>Graphics · 14M</span></div>%s</article>
                  </div>
                </section>
                <aside class="ui-card discover-updates"><div class="updates-head"><b>Updates</b><span class="ui-badge tone-neutral">3 ready</span></div><div class="update-row"><i class="project-icon tiny iris">I</i><span><b>Iris</b><small>1.9.4 → 1.9.5</small></span><em>12 MB</em></div><div class="update-row"><i class="project-icon tiny menu">M</i><span><b>Mod Menu</b><small>18.0 → 18.1</small></span><em>2 MB</em></div><div class="update-row"><i class="project-icon tiny zoom">Z</i><span><b>Zoomify</b><small>3.0.2 → 3.1</small></span><em>4 MB</em></div><div class="updates-footer">Verified compatible with your profile</div></aside>
              </div>
            </div>
            """.formatted(update, install, installed, install, installed, install);
        String css = """
            #page-discover{--page-green:#1bd96a;background:#101211}.discover-wrap{display:flex;flex-direction:column}.discover-search{height:30px;min-width:210px;padding:0 9px;border:1px solid var(--ui-border);border-radius:var(--ui-r-sm);display:flex;align-items:center;gap:7px;background:#171a18;color:#7f8983;font-size:9px}.discover-search span{flex:1}.discover-search kbd{padding:2px 5px;border:1px solid #343934;border-radius:5px;background:#202420;font-size:7px}.discover-layout{flex:1;min-height:0;display:grid;grid-template-columns:minmax(0,1fr) 205px;gap:10px}.discover-main{padding-right:2px}.discover-feature{min-height:112px;padding:15px;display:grid;grid-template-columns:52px 1fr auto;align-items:center;gap:14px;background:#171a18}.discover-feature h2{margin:6px 0 3px;font-size:17px}.discover-feature p{margin:0;color:var(--ui-muted);font-size:9px}.project-meta{display:flex;gap:12px;margin-top:8px;color:#7f8983;font-size:7px}.project-icon{width:42px;height:42px;border:1px solid #343a35;border-radius:11px;display:grid;place-items:center;background:#222722;font-weight:850;font-style:normal}.project-icon.sodium{color:#c0e6ca;background:#1d3323}.project-icon.iris{color:#b7caff;background:#242b42}.project-icon.zoom{color:#ffe19c;background:#37301d}.project-icon.menu{color:#b9e7db;background:#1f3732}.project-icon.light{color:#fff1a8;background:#37341e}.discover-heading{display:flex;justify-content:space-between;align-items:center;margin:12px 2px 7px;font-size:10px;font-weight:750}.text-button{border:0;background:transparent;color:var(--ui-active-accent);font-size:8px;cursor:pointer}.project-grid{display:grid;grid-template-columns:1fr 1fr;gap:7px}.project-card{min-height:67px;padding:10px;display:grid;grid-template-columns:42px 1fr auto;align-items:center;gap:9px;transition:border-color calc(150ms*var(--ui-motion)) ease,transform calc(150ms*var(--ui-motion)) ease}.project-card:hover{border-color:#465047;transform:translateY(-1px)}.project-copy{min-width:0}.project-copy b,.project-copy small,.project-copy span{display:block}.project-copy b{font-size:10px}.project-copy small{margin:3px 0;color:var(--ui-muted);font-size:7px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.project-copy span{color:#69736c;font-size:7px}.discover-updates{padding:13px;background:#151815}.updates-head{display:flex;align-items:center;justify-content:space-between;padding-bottom:10px;border-bottom:1px solid var(--ui-border);font-size:10px}.update-row{display:grid;grid-template-columns:28px 1fr auto;align-items:center;gap:8px;padding:10px 0;border-bottom:1px solid #252925}.project-icon.tiny{width:28px;height:28px;border-radius:8px;font-size:8px}.update-row b,.update-row small{display:block;font-size:8px}.update-row small{margin-top:2px;color:var(--ui-muted);font-size:7px}.update-row em{color:#707a73;font-size:7px;font-style:normal}.updates-footer{margin-top:11px;color:#6f7b73;font-size:7px;text-align:center}
            """;
        return new ClientUiPage("discover", "Discover", "⌕", "#1bd96a", markup, css);
    }

    private static ClientUiPage loadoutPage() {
        String save = ClientUiComponents.button("Save loadout", "save-loadout", PRIMARY, ROUNDED);
        String equip = ClientUiComponents.button("Equip best", "equip-best", SECONDARY, ROUNDED);
        String markup = """
            <div class="ui-page-pad loadout-wrap">
              <header class="ui-page-head"><div><div class="ui-kicker">Equipment manager</div><h1>Adventure loadout</h1><p class="ui-page-subtitle">Native items and your live player rendered over a web layout.</p></div><div class="demo-actions">%s%s</div></header>
              <div class="loadout-grid">
                <section class="ui-card player-card"><div class="player-stage" data-native="player"><div class="player-empty">Join a world to render your live player</div></div><div class="player-summary"><div><b>Sl0oth</b><span>Exploration preset</span></div><span class="ui-badge tone-success">Ready</span></div><div class="stat-row"><span>Armor <b>18</b></span><span>Toughness <b>8</b></span><span>Speed <b>100%%</b></span></div></section>
                <section class="ui-card equipment-card"><div class="equipment-head"><b>Equipment</b><span>Native item renderer</span></div><div class="equipment-slots"><div class="native-slot" data-native="item-helmet"><small>Helmet</small></div><div class="native-slot" data-native="item-chest"><small>Chest</small></div><div class="native-slot" data-native="item-legs"><small>Legs</small></div><div class="native-slot" data-native="item-boots"><small>Boots</small></div><div class="native-slot selected" data-native="item-sword"><small>Main hand</small></div><div class="native-slot" data-native="item-totem"><small>Off hand</small></div></div><div class="equipment-note"><i>◆</i><span><b>Native bridge</b><small>Items keep enchant glint, durability and model overrides.</small></span></div></section>
                <section class="ui-card hotbar-card"><div><b>Hotbar preset</b><small>Drag targets can be backed by real ItemStacks.</small></div><div class="hotbar"><span data-native="hotbar-pick"></span><span data-native="hotbar-food"></span><span data-native="hotbar-pearl"></span><span data-native="hotbar-compass"></span><span></span><span></span><span></span><span></span><span></span></div></section>
              </div>
            </div>
            """.formatted(save, equip);
        String css = """
            #page-loadout{background:#101113}.loadout-wrap{display:flex;flex-direction:column}.loadout-grid{flex:1;min-height:0;display:grid;grid-template-columns:.82fr 1.18fr;grid-template-rows:minmax(0,1fr) 72px;gap:9px}.player-card{position:relative;overflow:hidden;background:#17191d}.player-stage{position:absolute;inset:10px 10px 71px;border:1px dashed #343840;border-radius:var(--ui-r-md);display:grid;place-items:center}.player-empty{max-width:110px;color:#59616d;font-size:7px;text-align:center;line-height:1.4}.player-summary{position:absolute;left:14px;right:14px;bottom:35px;display:flex;align-items:center;justify-content:space-between}.player-summary b,.player-summary span{display:block;font-size:10px}.player-summary span{margin-top:2px;color:var(--ui-muted);font-size:7px}.stat-row{position:absolute;left:14px;right:14px;bottom:11px;display:flex;gap:13px;color:var(--ui-muted);font-size:7px}.stat-row b{margin-left:3px;color:var(--ui-text)}.equipment-card{padding:14px}.equipment-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;font-size:10px}.equipment-head span{color:var(--ui-muted);font-size:7px}.equipment-slots{display:grid;grid-template-columns:repeat(3,1fr);gap:7px}.native-slot{position:relative;height:58px;border:1px solid #333740;border-radius:var(--ui-r-sm);background:#1c1f24}.native-slot:hover,.native-slot.selected{border-color:var(--ui-active-accent)}.native-slot small{position:absolute;left:6px;bottom:5px;color:#6f7784;font-size:6px}.equipment-note{display:flex;align-items:center;gap:8px;margin-top:9px;padding:8px;border-radius:var(--ui-r-sm);background:#1b1d22;color:var(--ui-active-accent)}.equipment-note span b,.equipment-note span small{display:block;font-size:8px}.equipment-note span small{margin-top:2px;color:var(--ui-muted);font-size:7px}.hotbar-card{grid-column:1/-1;padding:10px 12px;display:flex;align-items:center;justify-content:space-between}.hotbar-card b,.hotbar-card small{display:block;font-size:9px}.hotbar-card small{margin-top:3px;color:var(--ui-muted);font-size:7px}.hotbar{display:flex;gap:4px}.hotbar span{width:36px;height:36px;border:1px solid #333740;border-radius:7px;background:#1c1f24}.hotbar span:first-child{border-color:var(--ui-active-accent)}
            """;
        return new ClientUiPage("loadout", "Loadout", "◇", "#e7ad4b", markup, css);
    }

    private static ClientUiPage waypointsPage() {
        String add = ClientUiComponents.button("＋ New waypoint", "new-waypoint", PRIMARY, PILL);
        String markup = """
            <div class="ui-page-pad waypoint-wrap">
              <header class="ui-page-head"><div><div class="ui-kicker">World navigation</div><h1>Waypoints</h1><p class="ui-page-subtitle">Plan routes, group locations and share exact coordinates.</p></div>%s</header>
              <div class="waypoint-layout">
                <section class="ui-card waypoint-list"><div class="waypoint-filter"><button class="active">All</button><button>Overworld</button><button>Nether</button></div><div class="waypoint-row selected"><i class="pin home"></i><span><b>Main base</b><small>124, 68, −340 · 182m</small></span><em>⌁</em></div><div class="waypoint-row"><i class="pin portal"></i><span><b>Nether portal</b><small>18, 64, −42 · 36m</small></span><em>⌁</em></div><div class="waypoint-row"><i class="pin mine"></i><span><b>Deep mine</b><small>201, −42, −511 · 390m</small></span><em>⌁</em></div><div class="waypoint-row"><i class="pin village"></i><span><b>Trading hall</b><small>−88, 71, 640 · 712m</small></span><em>⌁</em></div></section>
                <section class="ui-card map-card"><div class="map-grid"></div><div class="map-route"></div><button class="map-marker player-marker" aria-label="Player">◆</button><button class="map-marker base-marker" data-action="focus-base" onclick="ClientUI.animate(this,[{transform:'scale(1)'},{transform:'scale(1.35)'},{transform:'scale(1)'}],{duration:420})">⌂</button><button class="map-marker portal-marker">◈</button><div class="map-controls"><button>＋</button><button>−</button><button>⌖</button></div><div class="map-coordinates">X 124&nbsp;&nbsp; Z −340&nbsp;&nbsp; 1:4</div></section>
              </div>
            </div>
            """.formatted(add);
        String css = """
            #page-waypoints{background:#0e1115}.waypoint-wrap{display:flex;flex-direction:column}.waypoint-layout{flex:1;min-height:0;display:grid;grid-template-columns:215px minmax(0,1fr);gap:9px}.waypoint-list{padding:11px}.waypoint-filter{display:flex;gap:3px;margin-bottom:9px;padding:3px;border-radius:9px;background:#1b1e23}.waypoint-filter button{flex:1;height:22px;padding:0 5px;border:0;border-radius:6px;background:transparent;color:var(--ui-muted);font-size:7px;cursor:pointer}.waypoint-filter button.active{background:#292e35;color:var(--ui-text)}.waypoint-row{height:49px;padding:0 8px;display:grid;grid-template-columns:12px 1fr auto;align-items:center;gap:8px;border:1px solid transparent;border-radius:9px;cursor:pointer}.waypoint-row:hover{background:#1a1e23}.waypoint-row.selected{border-color:#2f455d;background:#172230}.waypoint-row b,.waypoint-row small{display:block;font-size:8px}.waypoint-row small{margin-top:3px;color:var(--ui-muted);font-size:7px}.waypoint-row em{color:#647083;font-style:normal}.pin{width:7px;height:7px;border:2px solid currentColor;border-radius:50% 50% 50% 0;transform:rotate(-45deg);color:var(--ui-active-accent)}.pin.portal{color:#a779ef}.pin.mine{color:#d6a858}.pin.village{color:#72cc99}.map-card{position:relative;overflow:hidden;background:#14191f}.map-grid{position:absolute;inset:0;background-image:linear-gradient(#27313c 1px,transparent 1px),linear-gradient(90deg,#27313c 1px,transparent 1px);background-size:28px 28px;opacity:.46}.map-grid::before,.map-grid::after{content:"";position:absolute;background:#1a2922}.map-grid::before{left:9%;top:13%;width:50%;height:52%;clip-path:polygon(8% 23%,26% 4%,55% 10%,82% 1%,96% 28%,84% 50%,97% 76%,72% 94%,46% 82%,20% 98%,2% 68%)}.map-grid::after{right:7%;bottom:8%;width:37%;height:43%;background:#1b2732;clip-path:polygon(3% 26%,24% 4%,54% 15%,79% 2%,98% 34%,82% 59%,91% 88%,58% 96%,38% 75%,11% 86%)}.map-route{position:absolute;left:39%;top:38%;width:30%;height:28%;border-top:2px dashed var(--ui-active-accent);border-right:2px dashed var(--ui-active-accent);transform:rotate(-12deg)}.map-marker{position:absolute;width:25px;height:25px;border:1px solid #42505f;border-radius:8px;background:#202731;cursor:pointer}.player-marker{left:42%;top:54%;color:#fff;background:var(--ui-active-accent)}.base-marker{left:67%;top:30%;color:var(--ui-active-accent)}.portal-marker{left:27%;top:24%;color:#a779ef}.map-controls{position:absolute;right:10px;top:10px;display:grid;gap:3px}.map-controls button{width:25px;height:25px;border:1px solid #343d47;border-radius:7px;background:#1b2128}.map-coordinates{position:absolute;right:10px;bottom:9px;padding:5px 7px;border:1px solid #303944;border-radius:7px;background:#171d24;color:#8794a3;font:7px ui-monospace,monospace}
            """;
        return new ClientUiPage("waypoints", "Waypoints", "⌖", "#5aa7ff", markup, css);
    }

    private static ClientUiPage performancePage() {
        String sample = ClientUiComponents.button("Run 3-second sample", "run-sample", PRIMARY, PILL);
        String export = ClientUiComponents.button("Export", "export-metrics", GHOST, ROUNDED);
        String markup = """
            <div class="ui-page-pad performance-wrap">
              <header class="ui-page-head"><div><div class="ui-kicker">Performance telemetry</div><h1>Frame analyzer</h1><p class="ui-page-subtitle">Measure first. Change settings second.</p></div><div class="demo-actions">%s%s</div></header>
              <div class="metric-grid"><article class="ui-card metric-card"><span>Average FPS</span><b>143</b><small>Example sampler output</small></article><article class="ui-card metric-card"><span>Frame time</span><b>6.9<em> ms</em></b><small>99th percentile 11.2ms</small></article><article class="ui-card metric-card"><span>UI renderer</span><b>Idle</b><small>Cached texture only</small></article><article class="ui-card metric-card"><span>Memory</span><b>2.8<em> GB</em></b><small>Example allocation reading</small></article></div>
              <div class="performance-lower"><article class="ui-card chart-card"><div class="chart-head"><div><b>Frame time</b><small>Last 60 seconds</small></div><div class="chart-legend"><span>Frame</span><span>Target</span></div></div><svg viewBox="0 0 600 150" preserveAspectRatio="none"><g class="chart-grid"><path d="M0 30H600M0 75H600M0 120H600"/></g><path class="target-line" d="M0 92H600"/><path id="perf-line" class="frame-line" pathLength="140" d="M0 102C24 98 38 93 60 96S91 72 116 84s35 11 57 4 33-20 58-7 43 18 66 2 39-23 65-7 38 20 63 4 42-14 65-5 38 16 61 2 28-18 49-10"/></svg><div class="chart-axis"><span>60s</span><span>45s</span><span>30s</span><span>15s</span><span>now</span></div></article><aside class="ui-card process-card"><div class="process-head"><b>Cost by system</b><small>6.9 ms total</small></div><div class="process-row"><span>World render</span><div><i style="width:72%%"></i></div><em>4.1</em></div><div class="process-row"><span>Entities</span><div><i style="width:31%%"></i></div><em>1.2</em></div><div class="process-row"><span>Particles</span><div><i style="width:18%%"></i></div><em>0.7</em></div><div class="process-row"><span>Client UI</span><div><i style="width:5%%"></i></div><em>0.1</em></div><div class="efficiency-note"><span class="ui-idle"></span><div><b>UI is idle</b><small>No DOM animation or texture upload</small></div></div></aside></div>
            </div>
            """.formatted(sample, export);
        String css = """
            #page-performance{background:#0c0d0f}.performance-wrap{display:flex;flex-direction:column}.metric-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:7px;margin-bottom:9px}.metric-card{padding:11px 12px}.metric-card span,.metric-card b,.metric-card small{display:block}.metric-card span{color:var(--ui-muted);font-size:7px}.metric-card b{margin:4px 0 2px;font-size:20px;letter-spacing:-.04em}.metric-card b em{font-size:8px;font-style:normal;color:var(--ui-muted)}.metric-card small{color:#66706c;font-size:6px}.performance-lower{flex:1;min-height:0;display:grid;grid-template-columns:minmax(0,1fr) 210px;gap:9px}.chart-card{padding:13px;display:flex;flex-direction:column}.chart-head,.process-head{display:flex;justify-content:space-between}.chart-head b,.chart-head small,.process-head b,.process-head small{display:block;font-size:9px}.chart-head small,.process-head small{margin-top:2px;color:var(--ui-muted);font-size:7px}.chart-legend{display:flex;gap:11px;color:var(--ui-muted);font-size:7px}.chart-legend span::before{content:"";display:inline-block;width:6px;height:2px;margin-right:4px;background:var(--ui-active-accent)}.chart-legend span+span::before{background:#555d67}.chart-card svg{flex:1;min-height:90px;margin-top:8px;overflow:visible}.chart-grid path{fill:none;stroke:#24272c;stroke-width:1}.target-line{fill:none;stroke:#545b65;stroke-width:1;stroke-dasharray:4 5}.frame-line{fill:none;stroke:var(--ui-active-accent);stroke-width:2;stroke-linecap:round;stroke-linejoin:round;stroke-dasharray:140;stroke-dashoffset:0}.chart-axis{display:flex;justify-content:space-between;color:#5d6570;font-size:6px}.process-card{padding:13px}.process-row{display:grid;grid-template-columns:58px 1fr 20px;align-items:center;gap:7px;margin-top:13px;font-size:7px}.process-row div{height:4px;border-radius:4px;background:#2b2f35;overflow:hidden}.process-row div i{display:block;height:100%;border-radius:inherit;background:var(--ui-active-accent)}.process-row em{color:var(--ui-muted);font-style:normal;text-align:right}.efficiency-note{display:flex;gap:8px;align-items:center;margin-top:16px;padding:9px;border:1px solid #2c3531;border-radius:9px;background:#151b18}.efficiency-note b,.efficiency-note small{display:block;font-size:8px}.efficiency-note small{margin-top:2px;color:var(--ui-muted);font-size:6px}
            """;
        return new ClientUiPage("performance", "Performance", "⌁", "#6de6aa", markup, css);
    }

    private static ClientUiPage settingsPage() {
        String done = ClientUiComponents.button("Save changes", "save-settings", PRIMARY, PILL);
        String markup = """
            <div class="ui-page-pad settings-wrap">
              <header class="ui-page-head"><div><div class="ui-kicker">Preferences</div><h1>Interface settings</h1><p class="ui-page-subtitle">Every token and component can be overridden by the consuming mod.</p></div>%s</header>
              <div class="settings-layout">
                <nav class="ui-card settings-index"><button class="active">Appearance</button><button>Motion</button><button>Accessibility</button><button>Input</button><button>Advanced</button></nav>
                <section class="settings-content demo-scroll"><article class="ui-card settings-group"><div class="group-head"><b>Shape language</b><small>Choose globally or override per component.</small></div><div class="shape-options"><button class="shape-choice square" data-action="shape-square"><i></i><span>Square</span></button><button class="shape-choice rounded active" data-action="shape-rounded"><i></i><span>Rounded</span></button><button class="shape-choice soft" data-action="shape-soft"><i></i><span>Soft</span></button><button class="shape-choice pill" data-action="shape-pill"><i></i><span>Pill</span></button></div></article>
                  <article class="ui-card settings-group"><div class="group-head"><b>Motion</b><small>Animations are event-driven and stop when complete.</small></div><div class="setting-row"><span><b>Interface motion</b><small>Page, hover and state transitions</small></span><button class="ui-toggle on" role="switch" aria-checked="true" data-toggle="interface-motion"><i></i></button></div><div class="setting-row"><span><b>Motion intensity</b><small>Scales duration and distance</small></span><div class="setting-slider"><input class="ui-slider" type="range" min="0" max="100" value="65" data-value="motion-intensity"><em>65%%</em></div></div><div class="setting-row"><span><b>Reduced motion fallback</b><small>Also honors the operating system preference</small></span><button class="ui-toggle" role="switch" aria-checked="false" data-toggle="reduced-motion"><i></i></button></div></article>
                  <article class="ui-card settings-group"><div class="group-head"><b>Component preview</b><small>Styles can coexist within the same document.</small></div><div class="component-preview"><button class="ui-button button-primary shape-rounded">Primary</button><button class="ui-button button-secondary shape-soft">Secondary</button><button class="ui-button button-ghost shape-pill">Ghost pill</button><span class="ui-badge tone-success">Online</span><input class="ui-slider" type="range" value="48"></div></article>
                </section>
              </div>
            </div>
            """.formatted(done);
        String css = """
            #page-settings{background:#101116}.settings-wrap{display:flex;flex-direction:column}.settings-layout{flex:1;min-height:0;display:grid;grid-template-columns:155px minmax(0,1fr);gap:9px}.settings-index{padding:7px;height:max-content}.settings-index button{width:100%;height:31px;padding:0 9px;border:0;border-radius:8px;background:transparent;color:var(--ui-muted);font-size:8px;text-align:left;cursor:pointer}.settings-index button:hover{background:#1d2026}.settings-index button.active{background:#232733;color:var(--ui-text)}.settings-content{display:grid;gap:6px;padding-right:2px}.settings-group{padding:10px 13px}.group-head{margin-bottom:8px}.group-head b,.group-head small{display:block;font-size:10px}.group-head small{margin-top:2px;color:var(--ui-muted);font-size:7px}.shape-options{display:grid;grid-template-columns:repeat(4,1fr);gap:7px}.shape-choice{height:48px;border:1px solid var(--ui-border);background:#1a1d23;color:var(--ui-muted);display:grid;place-items:center;align-content:center;gap:5px;font-size:7px;cursor:pointer}.shape-choice i{width:31px;height:14px;border:2px solid currentColor}.shape-choice.square,.shape-choice.square i{border-radius:1px}.shape-choice.rounded,.shape-choice.rounded i{border-radius:7px}.shape-choice.soft,.shape-choice.soft i{border-radius:13px}.shape-choice.pill,.shape-choice.pill i{border-radius:999px}.shape-choice:hover,.shape-choice.active{color:var(--ui-active-accent);border-color:var(--ui-active-accent)}.setting-row{min-height:36px;padding:6px 0;border-top:1px solid var(--ui-border);display:flex;align-items:center;justify-content:space-between;gap:14px}.setting-row>span b,.setting-row>span small{display:block;font-size:8px}.setting-row>span small{margin-top:2px;color:var(--ui-muted);font-size:7px}.setting-slider{display:flex;align-items:center;gap:7px}.setting-slider em{width:25px;color:var(--ui-muted);font-size:7px;font-style:normal}.component-preview{display:flex;align-items:center;gap:8px}.component-preview .ui-slider{margin-left:auto;width:90px}
            """;
        return new ClientUiPage("settings", "Settings", "⚙", "#8290ff", markup, css);
    }

    private static void renderNativeLayer(NativeOverlayContext context) {
        if (!"loadout".equals(context.pageId())) return;
        var player = context.localPlayer();
        // In 26.2 item data-component holders are not bound on the title screen.
        // Native entities/items become available as soon as a client world exists.
        if (player == null) return;
        context.player("player", player);
        context.item("item-helmet", Items.DIAMOND_HELMET.getDefaultInstance());
        context.item("item-chest", Items.ELYTRA.getDefaultInstance());
        context.item("item-legs", Items.DIAMOND_LEGGINGS.getDefaultInstance());
        context.item("item-boots", Items.DIAMOND_BOOTS.getDefaultInstance());
        context.item("item-sword", Items.DIAMOND_SWORD.getDefaultInstance());
        context.item("item-totem", Items.TOTEM_OF_UNDYING.getDefaultInstance());
        context.item("hotbar-pick", Items.DIAMOND_PICKAXE.getDefaultInstance());
        context.item("hotbar-food", Items.GOLDEN_APPLE.getDefaultInstance());
        context.item("hotbar-pearl", Items.ENDER_PEARL.getDefaultInstance());
        context.item("hotbar-compass", Items.RECOVERY_COMPASS.getDefaultInstance());
    }
}
