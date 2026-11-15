package dev.pane.ui;

import org.lwjgl.glfw.GLFW;

/**
 * Names for the codes a key binding holds, and whether the key is held. A code is a GLFW key code,
 * {@link #UNBOUND}, or a mouse button from {@link #mouse}.
 */
public final class UiKeyNames {
    private UiKeyNames() { }

    /** The code a key binding holds when no key is bound. */
    public static final int UNBOUND = -1;

    /** The code for mouse button {@code button}, where 0 is left, 1 is right and 2 is middle. */
    public static int mouse(int button) { return -100 - Math.max(0, button); }
    /** Whether {@code code} is a mouse button. */
    public static boolean isMouse(int code) { return code <= -100; }
    /** The GLFW button of a mouse code. */
    public static int mouseButton(int code) { return -100 - code; }

    /**
     * Whether the bound key or mouse button is held right now; never for {@link #UNBOUND}. Call on the
     * client thread, such as from a tick event.
     */
    public static boolean isDown(int code) {
        if (code == UNBOUND) return false;
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client == null) return false;
        var window = client.getWindow();
        if (isMouse(code)) return GLFW.glfwGetMouseButton(window.handle(), mouseButton(code)) == GLFW.GLFW_PRESS;
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, code);
    }

    /**
     * The name of a key or mouse button as Minecraft's own controls show it, in the player's language
     * and keyboard layout: "H", "F5", "Left Shift", "Left Button". "Not bound" for {@link #UNBOUND}.
     */
    public static String name(int code) {
        if (code == UNBOUND) return "Not bound";
        try {
            if (net.minecraft.client.Minecraft.getInstance() != null) {
                var type = isMouse(code) ? com.mojang.blaze3d.platform.InputConstants.Type.MOUSE
                    : com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM;
                String shown = type.getOrCreate(isMouse(code) ? mouseButton(code) : code).getDisplayName().getString();
                if (!shown.isBlank()) return shown.length() == 1 ? shown.toUpperCase(java.util.Locale.ROOT) : shown;
            }
        } catch (RuntimeException | LinkageError outsideTheGame) {
            // Unit tests and tools run without Minecraft; the English names below serve there.
        }
        return english(code);
    }

    /** English names on a US layout, used when the game is not running. */
    static String english(int code) {
        if (code == UNBOUND) return "Not bound";
        if (isMouse(code)) return switch (mouseButton(code)) {
            case 0 -> "Left Button";
            case 1 -> "Right Button";
            case 2 -> "Middle Button";
            default -> "Mouse " + (mouseButton(code) + 1);
        };
        if (code >= GLFW.GLFW_KEY_A && code <= GLFW.GLFW_KEY_Z || code >= GLFW.GLFW_KEY_0 && code <= GLFW.GLFW_KEY_9)
            return String.valueOf((char) code);
        if (code >= GLFW.GLFW_KEY_F1 && code <= GLFW.GLFW_KEY_F25) return "F" + (code - GLFW.GLFW_KEY_F1 + 1);
        if (code >= GLFW.GLFW_KEY_KP_0 && code <= GLFW.GLFW_KEY_KP_9) return "Keypad " + (code - GLFW.GLFW_KEY_KP_0);
        return switch (code) {
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_APOSTROPHE -> "'";
            case GLFW.GLFW_KEY_COMMA -> ",";
            case GLFW.GLFW_KEY_MINUS -> "-";
            case GLFW.GLFW_KEY_PERIOD -> ".";
            case GLFW.GLFW_KEY_SLASH -> "/";
            case GLFW.GLFW_KEY_SEMICOLON -> ";";
            case GLFW.GLFW_KEY_EQUAL -> "=";
            case GLFW.GLFW_KEY_LEFT_BRACKET -> "[";
            case GLFW.GLFW_KEY_BACKSLASH -> "\\";
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> "]";
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> "`";
            case GLFW.GLFW_KEY_ESCAPE -> "Escape";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_INSERT -> "Insert";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_RIGHT -> "Right";
            case GLFW.GLFW_KEY_LEFT -> "Left";
            case GLFW.GLFW_KEY_DOWN -> "Down";
            case GLFW.GLFW_KEY_UP -> "Up";
            case GLFW.GLFW_KEY_PAGE_UP -> "Page Up";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "Page Down";
            case GLFW.GLFW_KEY_HOME -> "Home";
            case GLFW.GLFW_KEY_END -> "End";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "Caps Lock";
            case GLFW.GLFW_KEY_SCROLL_LOCK -> "Scroll Lock";
            case GLFW.GLFW_KEY_NUM_LOCK -> "Num Lock";
            case GLFW.GLFW_KEY_PRINT_SCREEN -> "Print Screen";
            case GLFW.GLFW_KEY_PAUSE -> "Pause";
            case GLFW.GLFW_KEY_KP_DECIMAL -> "Keypad .";
            case GLFW.GLFW_KEY_KP_DIVIDE -> "Keypad /";
            case GLFW.GLFW_KEY_KP_MULTIPLY -> "Keypad *";
            case GLFW.GLFW_KEY_KP_SUBTRACT -> "Keypad -";
            case GLFW.GLFW_KEY_KP_ADD -> "Keypad +";
            case GLFW.GLFW_KEY_KP_ENTER -> "Keypad Enter";
            case GLFW.GLFW_KEY_KP_EQUAL -> "Keypad =";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "Left Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "Left Control";
            case GLFW.GLFW_KEY_LEFT_ALT -> "Left Alt";
            case GLFW.GLFW_KEY_LEFT_SUPER -> "Left Super";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "Right Shift";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "Right Control";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "Right Alt";
            case GLFW.GLFW_KEY_RIGHT_SUPER -> "Right Super";
            case GLFW.GLFW_KEY_MENU -> "Menu";
            default -> "Key " + code;
        };
    }
}
