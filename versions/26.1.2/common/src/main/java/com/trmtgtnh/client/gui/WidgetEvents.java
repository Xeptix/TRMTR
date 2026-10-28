package com.trmtgtnh.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/**
 * A press handed on to one of the game's own fields, as the 1.21.9 and later games hand a press to a widget: one event
 * holding where and which button, where 1.21.1 passed three numbers (0.9.228). The settings screens decide which field
 * a press is for themselves, from the shared layout, and hand it on with this.
 */
final class WidgetEvents {

    private WidgetEvents() {}

    /** A press of {@code button} at a point, with no modifier key: what a field reads to place its cursor. */
    static MouseButtonEvent click(double x, double y, int button) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
    }

    /**
     * Keeps the typed characters coming while one of the settings screens is open - on 26.3, where the game hears typed
     * text only while SDL's text input is started, and its {@code EditBox} starts and stops it with its own focus, so a
     * list line of this mod's ({@link LineBox}) typed into after a box had lost the focus heard no characters. Before
     * 26.3 the window is GLFW's, which hands every character to the screen whatever is started, and this game's
     * {@code TextInputManager} is only the input method's: starting it here would open the input method's composing
     * for keys that need none. So on this game there is nothing to hold, and the screens' ticks ask for nothing - the
     * call stays so the three 26.x trees keep one shape, and 26.3's starts the text input here.
     */
    static void hearTyping(Screen screen) {
        // Nothing to start: GLFW hears characters already.
    }
}
