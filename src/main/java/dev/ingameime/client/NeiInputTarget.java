package dev.ingameime.client;

import java.lang.reflect.Field;

import net.minecraft.client.gui.GuiTextField;

import codechicken.nei.LayoutManager;
import codechicken.nei.RecipeSearchField;
import codechicken.nei.SearchField;
import codechicken.nei.TextField;
import codechicken.nei.recipe.GuiRecipe;

final class NeiInputTarget implements InputTarget {

    private static final Field RECIPE_SEARCH_FIELD = locateField(GuiRecipe.class, "searchField");
    private static final Field INNER_FIELD = locateField(TextField.class, "field");

    private final TextField searchField;

    private NeiInputTarget(TextField searchField) {
        this.searchField = searchField;
    }

    static InputTarget find(Object screen) {
        if (screen instanceof GuiRecipe<?>) {
            RecipeSearchField recipeSearchField = recipeSearchField();
            if (recipeSearchField != null && recipeSearchField.isVisible() && recipeSearchField.focused()) {
                return new NeiInputTarget(recipeSearchField);
            }
        }

        SearchField searchField = LayoutManager.searchField;
        return searchField != null && searchField.isVisible() && searchField.focused() ? new NeiInputTarget(searchField)
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
        if (allowed.isEmpty() || !searchField.focused()) {
            return;
        }
        GuiTextField inner = innerField();
        if (inner == null) {
            return;
        }
        GuiTextFields.normalizeSelection(inner);
        if (lwjgl3ify) {
            String bufferedText = Lwjgl3ifyTextInput.replaceBufferedText(allowed);
            try {
                searchField.handleKeyPress(0, allowed.charAt(0));
                if (!Lwjgl3ifyTextInput.isBufferEmpty()) {
                    for (int index = 1; index < allowed.length(); index++) {
                        char character = allowed.charAt(index);
                        Lwjgl3ifyTextInput.replaceBufferedText(Character.toString(character));
                        searchField.handleKeyPress(0, character);
                    }
                }
            } finally {
                if (bufferedText != null) {
                    Lwjgl3ifyTextInput.restoreBufferedText(bufferedText);
                }
            }
            return;
        }
        String oldText = searchField.text();
        if (GuiTextFields.isEnabled(inner)) {
            inner.writeText(allowed);
        }
        searchField.onTextChange(oldText);
    }

    private GuiTextField innerField() {
        try {
            return (GuiTextField) INNER_FIELD.get(searchField);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not inspect NEI internal text field", failure);
        }
    }

    private static RecipeSearchField recipeSearchField() {
        try {
            return (RecipeSearchField) RECIPE_SEARCH_FIELD.get(null);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not inspect NEI recipe search field", failure);
        }
    }

    private static Field locateField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException failure) {
            throw new IllegalStateException("Could not locate NEI field " + owner.getName() + "." + name, failure);
        }
    }
}
