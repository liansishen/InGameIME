package dev.ingameime.client;

import java.lang.reflect.Field;

import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.ChatAllowedCharacters;

import cpw.mods.fml.relauncher.ReflectionHelper;

final class GuiTextFields {

    private static final Field ENABLED_FIELD = ReflectionHelper
        .findField(GuiTextField.class, "field_146226_p", "isEnabled");

    private GuiTextFields() {}

    static String filterAllowed(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int firstInvalid = -1;
        for (int index = 0; index < text.length(); index++) {
            if (!ChatAllowedCharacters.isAllowedCharacter(text.charAt(index))) {
                firstInvalid = index;
                break;
            }
        }
        if (firstInvalid < 0) {
            return text;
        }
        StringBuilder builder = new StringBuilder(text.length());
        builder.append(text, 0, firstInvalid);
        for (int index = firstInvalid + 1; index < text.length(); index++) {
            char character = text.charAt(index);
            if (ChatAllowedCharacters.isAllowedCharacter(character)) {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    static boolean isEnabled(GuiTextField field) {
        try {
            return ENABLED_FIELD.getBoolean(field);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not inspect GUI text field enabled state", failure);
        }
    }

    static void normalizeSelection(GuiTextField field) {
        int cursor = field.getCursorPosition();
        int selectionEnd = field.getSelectionEnd();
        if (cursor > selectionEnd) {
            field.setCursorPosition(selectionEnd);
            field.setSelectionPos(cursor);
        }
    }
}
