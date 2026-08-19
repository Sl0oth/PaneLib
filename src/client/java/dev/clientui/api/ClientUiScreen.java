package dev.clientui.api;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.CefSettings;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.lwjgl.glfw.GLFW;

/**
 * Reusable browser-backed screen. Chromium repaints only invalidated dirty rectangles;
 * Minecraft receives one cached texture blit per visible frame.
 */
public class ClientUiScreen extends Screen {
    private final ClientUiDocument document;
    private final NativeOverlayRenderer nativeOverlay;
    private final ClientUiEventListener eventListener;
    private final Map<String, NativeBounds> nativeBounds = new ConcurrentHashMap<>();
    private final CefMessageRouterHandlerAdapter queryHandler = new QueryHandler();
    private final CefDisplayHandlerAdapter consoleBridge = new ConsoleBridge();

    private MCEFBrowser browser;
    private CefMessageRouter messageRouter;
    private String pageId;
    private boolean closed;
    private boolean initialPageSent;

    public ClientUiScreen(ClientUiDocument document) {
        this(document, NativeOverlayRenderer.NONE, ClientUiEventListener.NONE);
    }

    public ClientUiScreen(ClientUiDocument document, NativeOverlayRenderer nativeOverlay,
                          ClientUiEventListener eventListener) {
        super(Component.literal("Client UI"));
        this.document = document;
        this.nativeOverlay = nativeOverlay;
        this.eventListener = eventListener;
        this.pageId = document.initialPageId();
    }

    @Override
    protected void init() {
        if (browser != null) {
            resizeBrowser();
            return;
        }
        installMessageRouter();
        browser = MCEF.createBrowser(document.dataUrl(documentScale()), true);
        browser.setFocus(true);
        resizeBrowser();
    }

    private void installMessageRouter() {
        CefMessageRouter.CefMessageRouterConfig config =
            new CefMessageRouter.CefMessageRouterConfig("clientUiQuery", "clientUiCancel");
        messageRouter = CefMessageRouter.create(config);
        messageRouter.addHandler(queryHandler, true);
        MCEF.getClient().getHandle().addMessageRouter(messageRouter);
        MCEF.getClient().addDisplayHandler(consoleBridge);
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        resizeBrowser();
        executeJavaScript("window.ClientUI?.invalidate()");
    }

    private void resizeBrowser() {
        if (browser != null && width > 0 && height > 0) {
            browser.resize((int) Math.round(width * guiScale()), (int) Math.round(height * guiScale()));
        }
    }

    public final String pageId() { return pageId; }

    public final void showPage(String id) {
        if (id == null || !id.matches("[a-z][a-z0-9_-]{0,31}")) return;
        executeJavaScript("window.ClientUI?.showPage('" + id + "')");
    }

    public final void setToken(String name, String value) {
        if (name == null || !name.matches("[a-z][a-z0-9-]{0,31}") || value == null) return;
        String safeValue = value.replace("\\", "\\\\").replace("'", "\\'")
            .replace("\n", "").replace("\r", "");
        executeJavaScript("window.ClientUI?.setToken('" + name + "','" + safeValue + "')");
    }

    protected final void executeJavaScript(String script) {
        if (browser != null) browser.executeJavaScript(script, "clientui://runtime", 1);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        // Browser document owns the background.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        if (browser == null || !browser.isTextureReady()) return;
        if (!initialPageSent) {
            initialPageSent = true;
            showPage(document.initialPageId());
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, browser.getTextureIdentifier(),
            0, 0, 0.0F, 0.0F, width, height, width, height);
        graphics.nextStratum();
        nativeOverlay.render(new NativeOverlayContext(
            graphics, pageId, Map.copyOf(nativeBounds), guiScale(), mouseX, mouseY
        ));
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (browser != null) browser.sendMouseMove(px(mouseX), px(mouseY));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (browser == null) return false;
        browser.sendMousePress(px(event.x()), px(event.y()), event.button());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (browser == null) return false;
        browser.sendMouseRelease(px(event.x()), px(event.y()), event.button());
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (browser == null) return false;
        browser.sendMouseMove(px(event.x()), px(event.y()));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (browser == null) return false;
        browser.sendMouseWheel(px(mouseX), px(mouseY), vertical * 80.0, 0);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) return true;
        int pageIndex = event.key() - GLFW.GLFW_KEY_1;
        if (pageIndex >= 0 && pageIndex < document.pageCount()) {
            pageId = document.pageIdAt(pageIndex);
            nativeBounds.clear();
            showPage(pageId);
            return true;
        }
        if (browser == null) return false;
        browser.sendKeyPress(event.key(), event.scancode(), event.modifiers());
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (browser == null) return false;
        browser.sendKeyRelease(event.key(), event.scancode(), event.modifiers());
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (browser == null) return false;
        String value = event.codepointAsString();
        for (int i = 0; i < value.length(); i++) browser.sendKeyTyped(value.charAt(i), 0);
        return true;
    }

    @Override
    public void onClose() {
        closeRuntime();
        super.onClose();
    }

    @Override
    public void removed() {
        closeRuntime();
        super.removed();
    }

    private void closeRuntime() {
        if (closed) return;
        closed = true;
        if (browser != null) {
            browser.setFocus(false);
            browser.close();
            browser = null;
        }
        if (messageRouter != null && MCEF.isInitialized()) {
            MCEF.getClient().removeDisplayHandler(consoleBridge);
            MCEF.getClient().getHandle().removeMessageRouter(messageRouter);
            messageRouter.removeHandler(queryHandler);
            messageRouter.dispose();
            messageRouter = null;
        }
        nativeBounds.clear();
    }

    private void handleQuery(String request) {
        String[] parts = request.split("\\|", 7);
        if (parts.length == 0) return;
        switch (parts[0]) {
            case "page" -> {
                if (parts.length < 2) return;
                pageId = parts[1];
                nativeBounds.clear();
                eventListener.onEvent("page", pageId, "");
            }
            case "bound" -> {
                if (parts.length < 6) return;
                try {
                    nativeBounds.put(parts[1], new NativeBounds(
                        Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                        Double.parseDouble(parts[4]), Double.parseDouble(parts[5])
                    ));
                } catch (NumberFormatException ignored) {}
            }
            case "action", "value" -> eventListener.onEvent(
                parts[0], parts.length > 1 ? parts[1] : "", parts.length > 2 ? parts[2] : ""
            );
            default -> eventListener.onEvent("message", parts[0], request);
        }
    }

    private final class QueryHandler extends CefMessageRouterHandlerAdapter {
        @Override
        public boolean onQuery(CefBrowser source, CefFrame frame, long queryId, String request,
                               boolean persistent, CefQueryCallback callback) {
            Minecraft.getInstance().execute(() -> handleQuery(request));
            callback.success("ok");
            return true;
        }
    }

    /** Console fallback for preloaded MCEF browsers where the query function was not injected. */
    private final class ConsoleBridge extends CefDisplayHandlerAdapter {
        @Override
        public boolean onConsoleMessage(CefBrowser source, CefSettings.LogSeverity severity,
                                        String message, String sourceName, int line) {
            if (message == null || !message.startsWith("CLIENTUI|")) return false;
            String request = message.substring("CLIENTUI|".length());
            Minecraft.getInstance().execute(() -> handleQuery(request));
            return true;
        }
    }

    private static int px(double logical) { return (int) Math.round(logical * guiScale()); }
    private static double guiScale() { return Minecraft.getInstance().getWindow().getGuiScale(); }

    private double documentScale() {
        double gui = guiScale();
        double physicalWidth = width * gui;
        double physicalHeight = height * gui;
        return Math.max(1.0, Math.min(gui, Math.min(physicalWidth / 920.0, physicalHeight / 520.0)));
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
}
