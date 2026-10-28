package com.trmtgtnh.client.gui;

import net.minecraft.client.Minecraft;
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
     * Keeps the typed characters coming while one of the settings screens is open. From 26.3 the game hears typed text
     * (a {@code charTyped}) only while its text input is started, which the game's own {@code EditBox} starts when it
     * takes the focus and stops when it loses it; the settings screens keep their own focus, and their list lines are
     * fields of their own ({@link LineBox}), so a line typed into after a box had lost the focus would hear no
     * characters at all. So the screen holds the text input on for as long as it is open, as every 1.21.1 screen had
     * it - its keys and its characters both. Harmless before 26.3, where the window hears characters whatever is
     * started. Asked each tick and as the screen opens; the game stops it when the screen closes.
     */
    static void hearTyping(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.textInputManager() == null) return;
        mc.textInputManager()
            .startTextInput(screen);
    }
}
