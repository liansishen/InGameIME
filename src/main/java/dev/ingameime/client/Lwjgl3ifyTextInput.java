package dev.ingameime.client;

import java.lang.reflect.Field;

final class Lwjgl3ifyTextInput {

    private static final String HANDLER_CLASS = "me.eigenraven.lwjgl3ify.client.TextFieldHandler";
    private static final StringBuilder TEXT_BUFFER = findTextBuffer();

    private Lwjgl3ifyTextInput() {}

    static boolean isAvailable() {
        return TEXT_BUFFER != null;
    }

    static String replaceBufferedText(String text) {
        if (TEXT_BUFFER == null) {
            return null;
        }
        String previous = TEXT_BUFFER.toString();
        TEXT_BUFFER.setLength(0);
        TEXT_BUFFER.append(text);
        return previous;
    }

    static void restoreBufferedText(String text) {
        if (TEXT_BUFFER == null) {
            return;
        }
        TEXT_BUFFER.setLength(0);
        TEXT_BUFFER.append(text);
    }

    static void discardBufferedCharacter(char character) {
        if (TEXT_BUFFER != null && TEXT_BUFFER.length() > 0 && TEXT_BUFFER.charAt(0) == character) {
            TEXT_BUFFER.deleteCharAt(0);
        }
    }

    private static StringBuilder findTextBuffer() {
        try {
            Class<?> handler = Class.forName(HANDLER_CLASS, false, Lwjgl3ifyTextInput.class.getClassLoader());
            Field field = handler.getField("textBuffer");
            Object value = field.get(null);
            return value instanceof StringBuilder ? (StringBuilder) value : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}
