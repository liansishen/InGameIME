package dev.ingameime.client;

import java.lang.reflect.Field;

import net.minecraft.client.gui.GuiTextField;

import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.widgets.MEGuiTextField;
import dev.ingameime.InGameIME;

final class Ae2InputTarget implements InputTarget {

    private static final Field SEARCH_FIELD = locateField(GuiMEMonitorable.class, "searchField");
    private static final Field INNER_FIELD = locateField(MEGuiTextField.class, "field");

    private final MEGuiTextField searchField;

    Ae2InputTarget(MEGuiTextField searchField) {
        this.searchField = searchField;
    }

    static InputTarget find(Object screen) {
        if (SEARCH_FIELD == null || !(screen instanceof GuiMEMonitorable)) {
            return null;
        }
        MEGuiTextField searchField = getField(SEARCH_FIELD, screen, MEGuiTextField.class);
        return searchField != null && searchField.isVisible() && searchField.isFocused()
            ? new Ae2InputTarget(searchField)
            : null;
    }

    @Override
    public Object owner() {
        return searchField;
    }

    @Override
    public int kind() {
        return 0;
    }

    @Override
    public InputBounds bounds() {
        return new InputBounds(searchField.x, searchField.y, searchField.w, searchField.h);
    }

    @Override
    public void insertText(String text) {
        insertText(text, Lwjgl3ifyTextInput.isAvailable());
    }

    void insertText(String text, boolean lwjgl3ify) {
        String allowed = GuiTextFields.filterAllowed(text);
        if (allowed.isEmpty() || !searchField.isFocused()) {
            return;
        }
        GuiTextField inner = INNER_FIELD == null ? null : getField(INNER_FIELD, searchField, GuiTextField.class);
        if (inner == null) {
            for (int index = 0; index < allowed.length(); index++) {
                insertCharacter(allowed.charAt(index));
            }
            return;
        }
        GuiTextFields.normalizeSelection(inner);
        if (lwjgl3ify) {
            String bufferedText = Lwjgl3ifyTextInput.replaceBufferedText(allowed);
            try {
                searchField.textboxKeyTyped(allowed.charAt(0), 0);
                if (!Lwjgl3ifyTextInput.isBufferEmpty()) {
                    for (int index = 1; index < allowed.length(); index++) {
                        char character = allowed.charAt(index);
                        Lwjgl3ifyTextInput.replaceBufferedText(Character.toString(character));
                        searchField.textboxKeyTyped(character, 0);
                    }
                }
            } finally {
                if (bufferedText != null) {
                    Lwjgl3ifyTextInput.restoreBufferedText(bufferedText);
                }
            }
            return;
        }
        String oldText = searchField.getText();
        if (GuiTextFields.isEnabled(inner)) {
            inner.writeText(allowed);
        }
        searchField.onTextChange(oldText);
    }

    private void insertCharacter(char character) {
        String bufferedText = Lwjgl3ifyTextInput.replaceBufferedText(Character.toString(character));
        try {
            searchField.textboxKeyTyped(character, 0);
        } finally {
            if (bufferedText != null) {
                Lwjgl3ifyTextInput.restoreBufferedText(bufferedText);
            }
        }
    }

    private static <T> T getField(Field field, Object instance, Class<T> type) {
        try {
            return type.cast(field.get(instance));
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not inspect AE2 search field", failure);
        }
    }

    private static Field locateField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException failure) {
            InGameIME.LOG.warn("Could not locate AE2 field {}.{}", owner.getName(), name, failure);
            return null;
        }
    }
}
