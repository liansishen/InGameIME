package dev.ingameime.client;

import net.minecraft.client.gui.GuiChat;

import org.lwjgl.input.Keyboard;

public final class KeyboardHook {

    private KeyboardHook() {}

    public static boolean handleKeyboardInput(Object screen) {
        if (!Keyboard.getEventKeyState() || !(screen instanceof GuiChat)) {
            return false;
        }
        return ClientIme.getInstance()
            .handleKeyboardInput((GuiChat) screen);
    }
}
