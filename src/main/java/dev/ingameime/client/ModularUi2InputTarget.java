package dev.ingameime.client;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widgets.textfield.BaseTextFieldWidget;

import cpw.mods.fml.common.Loader;

final class ModularUi2InputTarget implements InputTarget {

    private final BaseTextFieldWidget<?> textField;

    private ModularUi2InputTarget(BaseTextFieldWidget<?> textField) {
        this.textField = textField;
    }

    static InputTarget find() {
        ModularScreen modularScreen = ModularScreen.getCurrent();
        if (modularScreen == null) {
            return null;
        }
        IWidget focused = modularScreen.getContext()
            .getFocusedWidget()
            .getElement();
        return focused instanceof BaseTextFieldWidget && focused.isEnabled() && focused.areAncestorsEnabled()
            ? new ModularUi2InputTarget((BaseTextFieldWidget<?>) focused)
            : null;
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
        Area area = textField.getArea();
        return new InputBounds(area.x, area.y, area.width, area.height);
    }

    @Override
    public void insertText(String text) {
        if (Loader.isModLoaded("lwjgl3ify")) {
            Lwjgl3ifyInput.insertText(textField, text);
            return;
        }
        for (int index = 0; index < text.length(); index++) {
            textField.onKeyPressed(text.charAt(index), 0);
        }
    }

    private static final class Lwjgl3ifyInput {

        private Lwjgl3ifyInput() {}

        static void insertText(BaseTextFieldWidget<?> textField, String text) {
            textField.onTextInput(new me.eigenraven.lwjgl3ify.api.InputEvents.TextEvent(text));
        }
    }
}
