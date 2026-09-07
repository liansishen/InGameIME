package dev.ingameime.client;

import java.lang.reflect.Field;

import codechicken.nei.LayoutManager;
import codechicken.nei.RecipeSearchField;
import codechicken.nei.SearchField;
import codechicken.nei.TextField;
import codechicken.nei.recipe.GuiRecipe;

final class NeiInputTarget implements InputTarget {

    private static final Field RECIPE_SEARCH_FIELD = locateRecipeSearchField();

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
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            String bufferedText = Lwjgl3ifyTextInput.replaceBufferedText(Character.toString(character));
            try {
                searchField.handleKeyPress(0, character);
            } finally {
                if (bufferedText != null) {
                    Lwjgl3ifyTextInput.restoreBufferedText(bufferedText);
                }
            }
        }
    }

    private static RecipeSearchField recipeSearchField() {
        try {
            return (RecipeSearchField) RECIPE_SEARCH_FIELD.get(null);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not inspect NEI recipe search field", failure);
        }
    }

    private static Field locateRecipeSearchField() {
        try {
            Field field = GuiRecipe.class.getDeclaredField("searchField");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException failure) {
            throw new IllegalStateException("Could not locate NEI recipe search field", failure);
        }
    }
}
