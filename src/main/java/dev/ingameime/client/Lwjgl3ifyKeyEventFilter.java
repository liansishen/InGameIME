package dev.ingameime.client;

import org.lwjgl.input.Keyboard;

final class Lwjgl3ifyKeyEventFilter {

    private Object pendingOwner;
    private char pendingCharacter;

    boolean consumeDuplicate(Object owner, int eventKey, char eventCharacter, boolean pressed) {
        if (!pressed) {
            reset();
            return false;
        }
        if (eventKey == Keyboard.KEY_NONE) {
            boolean duplicate = pendingOwner == owner && pendingCharacter == eventCharacter;
            reset();
            return duplicate;
        }
        reset();
        return false;
    }

    void recordResult(Object owner, int eventKey, char eventCharacter, boolean pressed, boolean consumed) {
        if (pressed && consumed && eventKey != Keyboard.KEY_NONE && isPrintable(eventCharacter)) {
            pendingOwner = owner;
            pendingCharacter = eventCharacter;
        }
    }

    void reset() {
        pendingOwner = null;
        pendingCharacter = 0;
    }

    private static boolean isPrintable(char character) {
        return character >= 0x20 && character != 0x7f;
    }
}
