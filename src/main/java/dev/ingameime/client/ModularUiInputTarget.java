package dev.ingameime.client;

import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.math.Size;
import com.gtnewhorizons.modularui.api.widget.Widget;
import com.gtnewhorizons.modularui.common.internal.wrapper.ModularGui;
import com.gtnewhorizons.modularui.common.widget.textfield.BaseTextFieldWidget;

final class ModularUiInputTarget implements InputTarget {

    private final BaseTextFieldWidget textField;

    private ModularUiInputTarget(BaseTextFieldWidget textField) {
        this.textField = textField;
    }

    static InputTarget find(Object screen) {
        if (!(screen instanceof ModularGui)) {
            return null;
        }
        Widget focused = ((ModularGui) screen).getCursor()
            .getFocused();
        return focused instanceof BaseTextFieldWidget && focused.isEnabled()
            ? new ModularUiInputTarget((BaseTextFieldWidget) focused)
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
        Pos2d position = textField.getAbsolutePos();
        Size size = textField.getSize();
        return new InputBounds(position.x, position.y, size.width, size.height);
    }

    @Override
    public void insertText(String text) {
        for (int index = 0; index < text.length(); index++) {
            textField.onKeyPressed(text.charAt(index), 0);
        }
    }
}
