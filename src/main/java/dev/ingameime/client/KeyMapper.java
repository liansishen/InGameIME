package dev.ingameime.client;

import org.lwjgl.input.Keyboard;

final class KeyMapper {

    static final int SHIFT_MASK = 1 << 0;
    static final int LOCK_MASK = 1 << 1;
    static final int CONTROL_MASK = 1 << 2;
    static final int ALT_MASK = 1 << 3;

    private static boolean capsLock;

    private KeyMapper() {}

    static KeyStroke current(boolean composing) {
        int eventKey = Keyboard.getEventKey();
        char eventCharacter = Keyboard.getEventCharacter();

        if (eventKey == Keyboard.KEY_CAPITAL) {
            capsLock = !capsLock;
        }
        int modifiers = currentModifiers();
        if (!composing && isVanillaEditingShortcut(eventKey, modifiers)) {
            return null;
        }

        int keysym = specialKeysym(eventKey);
        boolean modifiedHotkey = (modifiers & (CONTROL_MASK | ALT_MASK)) != 0
            && !isAltGrCharacter(modifiers, eventCharacter);
        if (keysym == 0 && modifiedHotkey) {
            keysym = physicalPrintableKeysym(eventKey);
        }
        if (keysym == 0 && isPrintable(eventCharacter)) {
            keysym = eventCharacter;
        }
        if (keysym == 0) {
            keysym = physicalPrintableKeysym(eventKey);
        }
        return keysym == 0 ? null : new KeyStroke(keysym, modifiers);
    }

    private static int currentModifiers() {
        int modifiers = 0;
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            modifiers |= SHIFT_MASK;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
            modifiers |= CONTROL_MASK;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)) {
            modifiers |= ALT_MASK;
        }
        if (capsLock) {
            modifiers |= LOCK_MASK;
        }
        return modifiers;
    }

    private static boolean isVanillaEditingShortcut(int eventKey, int modifiers) {
        return (modifiers & CONTROL_MASK) != 0 && (eventKey == Keyboard.KEY_A || eventKey == Keyboard.KEY_C
            || eventKey == Keyboard.KEY_V
            || eventKey == Keyboard.KEY_X);
    }

    private static boolean isAltGrCharacter(int modifiers, char eventCharacter) {
        return (modifiers & (CONTROL_MASK | ALT_MASK)) == (CONTROL_MASK | ALT_MASK)
            && Keyboard.isKeyDown(Keyboard.KEY_RMENU)
            && isPrintable(eventCharacter);
    }

    private static boolean isPrintable(char character) {
        return character >= 0x20 && character != 0x7f;
    }

    private static int physicalPrintableKeysym(int eventKey) {
        String keyName = Keyboard.getKeyName(eventKey);
        if (keyName != null && keyName.length() == 1) {
            return Character.toLowerCase(keyName.charAt(0));
        }
        switch (eventKey) {
            case Keyboard.KEY_SPACE:
                return ' ';
            case Keyboard.KEY_MINUS:
                return '-';
            case Keyboard.KEY_EQUALS:
                return '=';
            case Keyboard.KEY_LBRACKET:
                return '[';
            case Keyboard.KEY_RBRACKET:
                return ']';
            case Keyboard.KEY_BACKSLASH:
                return '\\';
            case Keyboard.KEY_SEMICOLON:
                return ';';
            case Keyboard.KEY_APOSTROPHE:
                return '\'';
            case Keyboard.KEY_GRAVE:
                return '`';
            case Keyboard.KEY_COMMA:
                return ',';
            case Keyboard.KEY_PERIOD:
                return '.';
            case Keyboard.KEY_SLASH:
                return '/';
            default:
                return 0;
        }
    }

    private static int specialKeysym(int eventKey) {
        switch (eventKey) {
            case Keyboard.KEY_BACK:
                return 0xff08;
            case Keyboard.KEY_TAB:
                return 0xff09;
            case Keyboard.KEY_RETURN:
            case Keyboard.KEY_NUMPADENTER:
                return 0xff0d;
            case Keyboard.KEY_PAUSE:
                return 0xff13;
            case Keyboard.KEY_SCROLL:
                return 0xff14;
            case Keyboard.KEY_ESCAPE:
                return 0xff1b;
            case Keyboard.KEY_HOME:
                return 0xff50;
            case Keyboard.KEY_LEFT:
                return 0xff51;
            case Keyboard.KEY_UP:
                return 0xff52;
            case Keyboard.KEY_RIGHT:
                return 0xff53;
            case Keyboard.KEY_DOWN:
                return 0xff54;
            case Keyboard.KEY_PRIOR:
                return 0xff55;
            case Keyboard.KEY_NEXT:
                return 0xff56;
            case Keyboard.KEY_END:
                return 0xff57;
            case Keyboard.KEY_INSERT:
                return 0xff63;
            case Keyboard.KEY_NUMLOCK:
                return 0xff7f;
            case Keyboard.KEY_NUMPAD0:
                return 0xffb0;
            case Keyboard.KEY_NUMPAD1:
                return 0xffb1;
            case Keyboard.KEY_NUMPAD2:
                return 0xffb2;
            case Keyboard.KEY_NUMPAD3:
                return 0xffb3;
            case Keyboard.KEY_NUMPAD4:
                return 0xffb4;
            case Keyboard.KEY_NUMPAD5:
                return 0xffb5;
            case Keyboard.KEY_NUMPAD6:
                return 0xffb6;
            case Keyboard.KEY_NUMPAD7:
                return 0xffb7;
            case Keyboard.KEY_NUMPAD8:
                return 0xffb8;
            case Keyboard.KEY_NUMPAD9:
                return 0xffb9;
            case Keyboard.KEY_MULTIPLY:
                return 0xffaa;
            case Keyboard.KEY_ADD:
                return 0xffab;
            case Keyboard.KEY_SUBTRACT:
                return 0xffad;
            case Keyboard.KEY_DECIMAL:
                return 0xffae;
            case Keyboard.KEY_DIVIDE:
                return 0xffaf;
            case Keyboard.KEY_F1:
                return 0xffbe;
            case Keyboard.KEY_F2:
                return 0xffbf;
            case Keyboard.KEY_F3:
                return 0xffc0;
            case Keyboard.KEY_F4:
                return 0xffc1;
            case Keyboard.KEY_F5:
                return 0xffc2;
            case Keyboard.KEY_F6:
                return 0xffc3;
            case Keyboard.KEY_F7:
                return 0xffc4;
            case Keyboard.KEY_F8:
                return 0xffc5;
            case Keyboard.KEY_F9:
                return 0xffc6;
            case Keyboard.KEY_F10:
                return 0xffc7;
            case Keyboard.KEY_F11:
                return 0xffc8;
            case Keyboard.KEY_F12:
                return 0xffc9;
            case Keyboard.KEY_CAPITAL:
                return 0xffe5;
            case Keyboard.KEY_DELETE:
                return 0xffff;
            default:
                return 0;
        }
    }

    static final class KeyStroke {

        final int keysym;
        final int modifiers;

        KeyStroke(int keysym, int modifiers) {
            this.keysym = keysym;
            this.modifiers = modifiers;
        }
    }
}
