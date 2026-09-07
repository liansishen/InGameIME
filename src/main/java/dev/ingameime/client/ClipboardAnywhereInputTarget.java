package dev.ingameime.client;

import java.lang.reflect.Field;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import com.hepdd.clipboardanywhere.client.gui.ClipboardOverlay;
import com.hepdd.clipboardanywhere.client.gui.OverlayGeometry;

final class ClipboardAnywhereInputTarget implements InputTarget {

    private static final Field RENAME_FIELD = overlayField("renameField");
    private static final Field TASK_EDIT_FIELD = overlayField("taskEditField");

    private final GuiScreen screen;
    private final ClipboardOverlay overlay;
    private final GuiTextField textField;

    private ClipboardAnywhereInputTarget(GuiScreen screen, ClipboardOverlay overlay, GuiTextField textField) {
        this.screen = screen;
        this.overlay = overlay;
        this.textField = textField;
    }

    static InputTarget find(Object screen) {
        if (!(screen instanceof GuiScreen)) {
            return null;
        }

        ClipboardOverlay overlay = ClipboardOverlay.INSTANCE;
        GuiTextField textField = textField(RENAME_FIELD, overlay);
        if (!isActive(textField)) {
            textField = textField(TASK_EDIT_FIELD, overlay);
        }
        return isActive(textField) ? new ClipboardAnywhereInputTarget((GuiScreen) screen, overlay, textField) : null;
    }

    @Override
    public Object owner() {
        return textField;
    }

    @Override
    public int kind() {
        return 0;
    }

    @Override
    public InputBounds bounds() {
        OverlayGeometry geometry = overlay.geometry(screen.width, screen.height);
        double scale = geometry.getScale();
        return new InputBounds(
            geometry.getLeft() + scaled(textField.xPosition, scale),
            geometry.getTop() + scaled(textField.yPosition, scale),
            scaled(textField.width, scale),
            scaled(textField.height, scale));
    }

    @Override
    public void insertText(String text) {
        textField.writeText(text);
    }

    private static boolean isActive(GuiTextField textField) {
        return textField != null && textField.isFocused() && textField.getVisible();
    }

    private static int scaled(int value, double scale) {
        return (int) Math.round(value * scale);
    }

    private static GuiTextField textField(Field field, ClipboardOverlay overlay) {
        try {
            return (GuiTextField) field.get(overlay);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not inspect ClipboardAnywhere text field", failure);
        }
    }

    private static Field overlayField(String name) {
        try {
            Field field = ClipboardOverlay.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException failure) {
            throw new IllegalStateException("Could not locate ClipboardAnywhere text field " + name, failure);
        }
    }
}
