package dev.ingameime.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiScreen;

import jds.bibliocraft.gui.GuiBiblioTextField;

final class BiblioInputTarget implements InputTarget {

    private static final Field X_POSITION = textFieldMember("xPos");
    private static final Field Y_POSITION = textFieldMember("yPos");
    private static final Field WIDTH = textFieldMember("width");
    private static final Field HEIGHT = textFieldMember("height");
    private static final Map<Class<?>, Field[]> TEXT_FIELDS = new HashMap<>();

    private final GuiBiblioTextField textField;

    private BiblioInputTarget(GuiBiblioTextField textField) {
        this.textField = textField;
    }

    static InputTarget find(Object screen) {
        if (!(screen instanceof GuiScreen)) {
            return null;
        }
        for (Field field : textFields(screen.getClass())) {
            Object value = getTextField(field, screen);
            if (value instanceof GuiBiblioTextField[]) {
                for (GuiBiblioTextField candidate : (GuiBiblioTextField[]) value) {
                    if (isActive(candidate)) {
                        return new BiblioInputTarget(candidate);
                    }
                }
            } else if (value instanceof GuiBiblioTextField && isActive((GuiBiblioTextField) value)) {
                return new BiblioInputTarget((GuiBiblioTextField) value);
            }
        }
        return null;
    }

    private static boolean isActive(GuiBiblioTextField field) {
        return field != null && field.isFocused() && field.getVisible();
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
        return new InputBounds(value(X_POSITION), value(Y_POSITION), value(WIDTH), value(HEIGHT));
    }

    @Override
    public void insertText(String text) {
        textField.writeText(text);
    }

    private int value(Field field) {
        try {
            return field.getInt(textField);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not inspect BiblioCraft text field bounds", failure);
        }
    }

    private static Field[] textFields(Class<?> screenClass) {
        Field[] cached = TEXT_FIELDS.get(screenClass);
        if (cached != null) {
            return cached;
        }
        List<Field> fields = new ArrayList<>();
        for (Class<?> type = screenClass; GuiScreen.class.isAssignableFrom(type); type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (GuiBiblioTextField.class.isAssignableFrom(field.getType())
                    || GuiBiblioTextField[].class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
        }
        Field[] result = fields.toArray(new Field[fields.size()]);
        TEXT_FIELDS.put(screenClass, result);
        return result;
    }

    private static Object getTextField(Field field, Object screen) {
        try {
            return field.get(screen);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not inspect BiblioCraft text field", failure);
        }
    }

    private static Field textFieldMember(String name) {
        try {
            Field field = GuiBiblioTextField.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException failure) {
            throw new IllegalStateException("Could not locate BiblioCraft text field member " + name, failure);
        }
    }
}
