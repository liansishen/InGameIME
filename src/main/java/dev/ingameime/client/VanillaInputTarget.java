package dev.ingameime.client;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiEditSign;

import cpw.mods.fml.common.ObfuscationReflectionHelper;

final class VanillaInputTarget implements InputTarget {

    private static final int TEXT_FIELD = 0;
    private static final int BOOK_PAGE = 1;
    private static final int BOOK_TITLE = 2;
    private static final int SIGN = 3;
    private static final int BATTLESIGN = 4;
    private static final String TINKERS_BATTLESIGN = "tconstruct.tools.gui.BattlesignGui";
    private static final String[] KEY_TYPED_NAMES = { "keyTyped", "func_73869_a", "a" };
    private static final Map<Class<?>, Field[]> TEXT_FIELDS = new HashMap<>();
    private static final Map<Class<?>, Method> KEY_TYPED_METHODS = new HashMap<>();

    private final GuiScreen screen;
    private final Object owner;
    private final int kind;

    private VanillaInputTarget(GuiScreen screen, Object owner, int kind) {
        this.screen = screen;
        this.owner = owner;
        this.kind = kind;
    }

    static InputTarget find(GuiScreen screen) {
        if (screen instanceof GuiScreenBook) {
            GuiScreenBook book = (GuiScreenBook) screen;
            boolean unsigned = ObfuscationReflectionHelper
                .getPrivateValue(GuiScreenBook.class, book, "bookIsUnsigned", "field_146475_i");
            if (!unsigned) {
                return null;
            }
            boolean signing = ObfuscationReflectionHelper
                .getPrivateValue(GuiScreenBook.class, book, "bookIsSigning", "field_146480_s");
            return new VanillaInputTarget(screen, screen, signing ? BOOK_TITLE : BOOK_PAGE);
        }
        if (screen instanceof GuiEditSign) {
            return new VanillaInputTarget(screen, screen, SIGN);
        }
        if (TINKERS_BATTLESIGN.equals(
            screen.getClass()
                .getName())) {
            return new VanillaInputTarget(screen, screen, BATTLESIGN);
        }

        for (Field field : textFields(screen.getClass())) {
            GuiTextField textField = getTextField(field, screen);
            if (textField != null && textField.isFocused() && textField.getVisible()) {
                return new VanillaInputTarget(screen, textField, TEXT_FIELD);
            }
        }
        return null;
    }

    @Override
    public Object owner() {
        return owner;
    }

    @Override
    public int kind() {
        return kind;
    }

    @Override
    public InputBounds bounds() {
        if (kind == TEXT_FIELD) {
            GuiTextField textField = (GuiTextField) owner;
            return new InputBounds(textField.xPosition, textField.yPosition, textField.width, textField.height);
        }
        int bookLeft = (screen.width - 192) / 2;
        if (kind == BOOK_PAGE) {
            return new InputBounds(bookLeft + 36, 32, 116, 128);
        }
        if (kind == BOOK_TITLE) {
            return new InputBounds(bookLeft + 36, 48, 116, 40);
        }
        return new InputBounds(screen.width / 2 - 75, screen.height / 2 - 30, 150, 60);
    }

    @Override
    public void insertText(String text) throws IllegalAccessException, InvocationTargetException {
        Method keyTyped = keyTypedMethod(screen.getClass());
        for (int index = 0; index < text.length(); index++) {
            insertCharacter(keyTyped, text.charAt(index));
        }
    }

    private void insertCharacter(Method keyTyped, char character)
        throws IllegalAccessException, InvocationTargetException {
        String bufferedText = kind == TEXT_FIELD ? Lwjgl3ifyTextInput.replaceBufferedText(Character.toString(character))
            : null;
        try {
            keyTyped.invoke(screen, character, 0);
        } finally {
            if (bufferedText != null) {
                Lwjgl3ifyTextInput.restoreBufferedText(bufferedText);
            }
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
                if (GuiTextField.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
        }
        Field[] result = fields.toArray(new Field[fields.size()]);
        TEXT_FIELDS.put(screenClass, result);
        return result;
    }

    private static GuiTextField getTextField(Field field, GuiScreen screen) {
        try {
            return (GuiTextField) field.get(screen);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not inspect GUI text field", failure);
        }
    }

    private static Method keyTypedMethod(Class<?> screenClass) {
        Method cached = KEY_TYPED_METHODS.get(screenClass);
        if (cached != null) {
            return cached;
        }

        for (Class<?> type = screenClass; GuiScreen.class.isAssignableFrom(type); type = type.getSuperclass()) {
            for (String name : KEY_TYPED_NAMES) {
                try {
                    Method method = type.getDeclaredMethod(name, char.class, int.class);
                    method.setAccessible(true);
                    KEY_TYPED_METHODS.put(screenClass, method);
                    return method;
                } catch (NoSuchMethodException ignored) {}
            }
        }
        throw new IllegalStateException("Could not locate GuiScreen.keyTyped for " + screenClass.getName());
    }
}
