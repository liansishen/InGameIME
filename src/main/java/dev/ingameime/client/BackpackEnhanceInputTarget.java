package dev.ingameime.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import dev.ingameime.InGameIME;

final class BackpackEnhanceInputTarget implements InputTarget {

    private final Api api;
    private final Object panel;

    private BackpackEnhanceInputTarget(Api api, Object panel) {
        this.api = api;
        this.panel = panel;
    }

    static InputTarget find(Object screen) {
        return ApiHolder.API == null ? null : ApiHolder.API.find(screen);
    }

    @Override
    public Object owner() {
        // Layout changes recreate the search field while preserving the focused panel.
        return panel;
    }

    @Override
    public int kind() {
        return 0;
    }

    @Override
    public InputBounds bounds() {
        try {
            GuiTextField field = (GuiTextField) api.searchField.get(panel);
            return new InputBounds(field.xPosition, field.yPosition, field.width, field.height);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not inspect BackpackEnhance search field", failure);
        }
    }

    @Override
    public void insertText(String text) throws ReflectiveOperationException {
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            String bufferedText = Lwjgl3ifyTextInput.replaceBufferedText(Character.toString(character));
            try {
                // The panel updates its query and invalidates filtered slots after each key.
                api.keyTyped.invoke(panel, character, 0);
            } finally {
                if (bufferedText != null) {
                    Lwjgl3ifyTextInput.restoreBufferedText(bufferedText);
                }
            }
        }
    }

    static final class Api {

        private final Method isActiveFor;
        private final Method getPanel;
        private final Method isSearchFocused;
        private final Method keyTyped;
        private final Field searchField;

        Api(Class<?> controllerClass) throws ReflectiveOperationException {
            isActiveFor = controllerClass.getMethod("isActiveFor", GuiScreen.class);
            getPanel = controllerClass.getMethod("getPanel");
            Class<?> panelClass = getPanel.getReturnType();
            isSearchFocused = panelClass.getMethod("isSearchFocused");
            keyTyped = panelClass.getMethod("keyTyped", char.class, int.class);
            searchField = panelClass.getDeclaredField("searchField");
            searchField.setAccessible(true);
        }

        InputTarget find(Object screen) {
            if (!(screen instanceof GuiScreen)) {
                return null;
            }
            try {
                if (!(Boolean) isActiveFor.invoke(null, screen)) {
                    return null;
                }
                Object panel = getPanel.invoke(null);
                if (panel == null || !(Boolean) isSearchFocused.invoke(panel)) {
                    return null;
                }
                GuiTextField field = (GuiTextField) searchField.get(panel);
                return field != null && field.getVisible() ? new BackpackEnhanceInputTarget(this, panel) : null;
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not inspect BackpackEnhance search input", failure);
            }
        }
    }

    private static final class ApiHolder {

        private static final Api API = loadApi();

        private static Api loadApi() {
            try {
                return new Api(
                    Class.forName(
                        "com.hepdd.backpackenhance.client.overlay.OverlayController",
                        false,
                        BackpackEnhanceInputTarget.class.getClassLoader()));
            } catch (ReflectiveOperationException failure) {
                InGameIME.LOG.warn("BackpackEnhance search input integration is unavailable", failure);
                return null;
            }
        }
    }
}
