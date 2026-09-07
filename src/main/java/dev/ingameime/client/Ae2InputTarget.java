package dev.ingameime.client;

import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.widgets.MEGuiTextField;
import cpw.mods.fml.common.ObfuscationReflectionHelper;

final class Ae2InputTarget implements InputTarget {

    private final MEGuiTextField searchField;

    private Ae2InputTarget(MEGuiTextField searchField) {
        this.searchField = searchField;
    }

    static InputTarget find(Object screen) {
        if (!(screen instanceof GuiMEMonitorable)) {
            return null;
        }
        MEGuiTextField searchField = ObfuscationReflectionHelper
            .getPrivateValue(GuiMEMonitorable.class, (GuiMEMonitorable) screen, "searchField");
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
        for (int index = 0; index < text.length(); index++) {
            insertCharacter(text.charAt(index));
        }
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
}
