package dev.ingameime.client;

import org.lwjgl.input.Keyboard;

import dev.ingameime.Config;
import dev.ingameime.InGameIME;

public final class KeyboardHook {

    private static final long SHIFT_TAP_NANOS = 350_000_000L;
    private static final Lwjgl3ifyKeyEventFilter DUPLICATE_TEXT_EVENTS = new Lwjgl3ifyKeyEventFilter();

    private static boolean leftShiftCandidate;
    private static boolean rightShiftCandidate;
    private static long leftShiftPressedAt;
    private static long rightShiftPressedAt;
    private static boolean ctrlShiftHandled;

    private KeyboardHook() {}

    public static void observeKeyboardEvent(Object screen) {
        InputTarget target = InputTargets.find(screen);
        ClientIme ime = ClientIme.getInstance();
        ime.updateInputTarget(screen, target);
        if (target == null || !ime.isActive()) {
            DUPLICATE_TEXT_EVENTS.reset();
            resetSwitchState();
            return;
        }

        int key = Keyboard.getEventKey();
        boolean pressed = Keyboard.getEventKeyState();
        if (key == Keyboard.KEY_LSHIFT || key == Keyboard.KEY_RSHIFT) {
            InGameIME.LOG.debug(
                "Input event: key={}, pressed={}, screen={}, target={}",
                key,
                pressed,
                screen.getClass()
                    .getName(),
                target.getClass()
                    .getSimpleName());
        }
        String shortcut = Config.modeSwitchKey;
        if (Config.MODE_SWITCH_CTRL_SHIFT.equals(shortcut)) {
            observeCtrlShift(target, key, pressed);
        } else if (Config.MODE_SWITCH_SHIFT.equals(shortcut) || Config.MODE_SWITCH_LEFT_SHIFT.equals(shortcut)) {
            observeShiftTap(target, key, pressed, shortcut);
        } else {
            resetSwitchState();
        }
    }

    public static boolean handleKeyboardInput(Object screen) {
        InputTarget target = InputTargets.find(screen);
        ClientIme ime = ClientIme.getInstance();
        ime.updateInputTarget(screen, target);
        if (target == null || !ime.isActive()) {
            DUPLICATE_TEXT_EVENTS.reset();
            return false;
        }

        int eventKey = Keyboard.getEventKey();
        char eventCharacter = Keyboard.getEventCharacter();
        boolean pressed = Keyboard.getEventKeyState();
        boolean lwjgl3ify = Lwjgl3ifyTextInput.isAvailable();
        if (lwjgl3ify && DUPLICATE_TEXT_EVENTS.consumeDuplicate(target.owner(), eventKey, eventCharacter, pressed)) {
            Lwjgl3ifyTextInput.discardBufferedCharacter(eventCharacter);
            return true;
        }
        if (!pressed) {
            return false;
        }

        boolean consumed = ime.handleKeyboardInput(target);
        if (lwjgl3ify) {
            DUPLICATE_TEXT_EVENTS.recordResult(target.owner(), eventKey, eventCharacter, true, consumed);
            if (consumed && eventKey == Keyboard.KEY_NONE) {
                Lwjgl3ifyTextInput.discardBufferedCharacter(eventCharacter);
            }
        }
        return consumed;
    }

    private static void observeShiftTap(InputTarget target, int key, boolean pressed, String shortcut) {
        boolean leftShift = key == Keyboard.KEY_LSHIFT;
        boolean rightShift = key == Keyboard.KEY_RSHIFT;
        if (!leftShift && !rightShift) {
            if (pressed) {
                leftShiftCandidate = false;
                rightShiftCandidate = false;
            }
            return;
        }

        boolean supported = leftShift || Config.MODE_SWITCH_SHIFT.equals(shortcut);
        if (pressed) {
            boolean eligible = supported && !isControlDown()
                && !isAltDown()
                && !(leftShift ? Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) : Keyboard.isKeyDown(Keyboard.KEY_LSHIFT));
            long now = System.nanoTime();
            if (leftShift) {
                if (eligible && !leftShiftCandidate) {
                    leftShiftPressedAt = now;
                }
                leftShiftCandidate = eligible;
                rightShiftCandidate = false;
                rightShiftPressedAt = 0L;
            } else {
                if (eligible && !rightShiftCandidate) {
                    rightShiftPressedAt = now;
                }
                rightShiftCandidate = eligible;
                leftShiftCandidate = false;
                leftShiftPressedAt = 0L;
            }
            return;
        }

        long pressedAt = leftShift ? leftShiftPressedAt : rightShiftPressedAt;
        boolean shouldSwitch = supported && (leftShift ? leftShiftCandidate : rightShiftCandidate)
            && pressedAt > 0L
            && System.nanoTime() - pressedAt <= SHIFT_TAP_NANOS;
        if (leftShift) {
            leftShiftCandidate = false;
            leftShiftPressedAt = 0L;
        } else {
            rightShiftCandidate = false;
            rightShiftPressedAt = 0L;
        }
        if (shouldSwitch) {
            ClientIme.getInstance()
                .switchInputMode(target);
        }
    }

    private static void observeCtrlShift(InputTarget target, int key, boolean pressed) {
        leftShiftCandidate = false;
        rightShiftCandidate = false;
        boolean modifier = key == Keyboard.KEY_LSHIFT || key == Keyboard.KEY_RSHIFT
            || key == Keyboard.KEY_LCONTROL
            || key == Keyboard.KEY_RCONTROL;
        if (pressed && modifier && isControlDown() && isShiftDown() && !ctrlShiftHandled) {
            ctrlShiftHandled = true;
            ClientIme.getInstance()
                .switchInputMode(target);
        }
        if (!isControlDown() || !isShiftDown()) {
            ctrlShiftHandled = false;
        }
    }

    private static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private static boolean isControlDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    private static boolean isAltDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
    }

    private static void resetSwitchState() {
        leftShiftCandidate = false;
        rightShiftCandidate = false;
        leftShiftPressedAt = 0L;
        rightShiftPressedAt = 0L;
        ctrlShiftHandled = false;
    }
}
