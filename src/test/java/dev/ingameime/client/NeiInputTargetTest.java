package dev.ingameime.client;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Test;
import org.lwjgl.input.Keyboard;

import codechicken.nei.FormattedTextField;
import codechicken.nei.TextField;
import me.eigenraven.lwjgl3ify.client.TextFieldHandler;

public class NeiInputTargetTest {

    @After
    public void clearBuffer() {
        TextFieldHandler.textBuffer.setLength(0);
    }

    @Test
    public void commitsCandidateOnceAndPreservesSearchCallbacksAndQueuedText() throws Exception {
        RecordingField field = new RecordingField();
        TextFieldHandler.textBuffer.setLength(0);
        TextFieldHandler.textBuffer.append(" x");
        NeiInputTarget target = target(field);

        target.insertText("\u6d4b\u8bd5");

        assertEquals("\u6d4b\u8bd5", field.text());
        assertEquals(Collections.singletonList(""), field.oldTexts);
        assertEquals(" x", TextFieldHandler.textBuffer.toString());

        Lwjgl3ifyKeyEventFilter filter = new Lwjgl3ifyKeyEventFilter();
        filter.recordResult(field, Keyboard.KEY_SPACE, ' ', true, true);
        if (filter.consumeDuplicate(field, Keyboard.KEY_NONE, ' ', true)) {
            Lwjgl3ifyTextInput.discardBufferedCharacter(' ');
        } else {
            field.handleKeyPress(0, ' ');
        }
        assertEquals("\u6d4b\u8bd5", field.text());
        assertEquals("x", TextFieldHandler.textBuffer.toString());
    }

    @Test
    public void replacesSelectionAtCursorAndHandlesReverseSelection() throws Exception {
        RecordingField field = new RecordingField();
        FormattedTextField textField = field.field();
        textField.setText("abcd");
        textField.setCursorPosition(1);
        textField.setSelectionPos(3);

        target(field).insertText("\u6d4b\u8bd5");

        assertEquals("a\u6d4b\u8bd5d", field.text());
        assertEquals(3, textField.getCursorPosition());

        textField.setCursorPosition(3);
        textField.setSelectionPos(1);
        target(field).insertText("XYZ");
        assertEquals("aXYZd", field.text());
        assertEquals(4, textField.getCursorPosition());
    }

    @Test
    public void commitsWithoutLwjgl3ifyBufferAndPreservesTruncationCallback() throws Exception {
        RecordingField field = new RecordingField();
        FormattedTextField textField = field.field();
        textField.setMaxStringLength(5);
        textField.setText("abc");
        textField.setCursorPositionEnd();
        field.oldTexts.clear();

        target(field).insertText("\u52a8\u529b\u4ed3", false);

        assertEquals("abc\u52a8\u529b", field.text());
        assertEquals(5, textField.getCursorPosition());
        assertEquals(Collections.singletonList("abc"), field.oldTexts);
    }

    private static NeiInputTarget target(TextField field) throws Exception {
        Constructor<NeiInputTarget> constructor = NeiInputTarget.class.getDeclaredConstructor(TextField.class);
        constructor.setAccessible(true);
        return constructor.newInstance(field);
    }

    private static final class RecordingField extends TextField {

        private final List<String> oldTexts = new ArrayList<>();

        private RecordingField() {
            super("test");
        }

        @Override
        protected void initInternalTextField() {
            field = new BufferedField();
            field.setFocused(true);
        }

        @Override
        public void onTextChange(String oldText) {
            oldTexts.add(oldText);
        }

        private FormattedTextField field() {
            return (FormattedTextField) field;
        }
    }

    private static final class BufferedField extends FormattedTextField {

        private BufferedField() {
            super(null, 0, 0, 100, 20);
        }

        @Override
        public boolean textboxKeyTyped(char character, int keyCode) {
            // Emulate LWJGL3ify's redirect inside GuiTextField.textboxKeyTyped.
            writeText(TextFieldHandler.textBuffer.toString());
            TextFieldHandler.textBuffer.setLength(0);
            return true;
        }
    }
}
