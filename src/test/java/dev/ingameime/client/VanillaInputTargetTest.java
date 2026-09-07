package dev.ingameime.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatAllowedCharacters;

import org.junit.Before;
import org.junit.Test;

import cpw.mods.fml.common.ObfuscationReflectionHelper;
import me.eigenraven.lwjgl3ify.client.TextFieldHandler;

public class VanillaInputTargetTest {

    @Before
    public void clearLwjgl3ifyBuffer() {
        TextFieldHandler.textBuffer.setLength(0);
    }

    @Test
    public void commitsThroughTheFocusedScreensNativeKeyHandler() throws Exception {
        TestScreen screen = new TestScreen();
        screen.textField.setText("ab");
        screen.textField.setCursorPosition(1);

        InputTarget target = VanillaInputTarget.find(screen);
        assertNotNull(target);
        assertTrue(target.isSameTarget(VanillaInputTarget.find(screen)));
        InputBounds bounds = target.bounds();
        assertEquals(0, bounds.x);
        assertEquals(0, bounds.y);
        assertEquals(100, bounds.width);
        assertEquals(20, bounds.height);

        target.insertText("中文");

        assertEquals("a中文b", screen.textField.getText());
        assertEquals(2, screen.changeCount);
    }

    @Test
    public void insertsCommittedTextWithoutConsumingQueuedText() throws Exception {
        TestScreen screen = new TestScreen();
        TextFieldHandler.textBuffer.append("cc");
        InputTarget target = VanillaInputTarget.find(screen);

        target.insertText("草");

        assertEquals("草", screen.textField.getText());
        assertEquals(1, screen.changeCount);
        assertEquals("cc", TextFieldHandler.textBuffer.toString());
    }

    @Test
    public void discardsOnlyTheCurrentBufferedCharacter() {
        TextFieldHandler.textBuffer.append("cc x");

        Lwjgl3ifyTextInput.discardBufferedCharacter('c');
        Lwjgl3ifyTextInput.discardBufferedCharacter('c');
        Lwjgl3ifyTextInput.discardBufferedCharacter(' ');

        assertEquals("x", TextFieldHandler.textBuffer.toString());
    }

    @Test
    public void ignoresUnfocusedAndHiddenFields() {
        TestScreen screen = new TestScreen();
        screen.textField.setFocused(false);
        assertNull(VanillaInputTarget.find(screen));

        screen.textField.setFocused(true);
        screen.textField.setVisible(false);
        assertNull(VanillaInputTarget.find(screen));
    }

    @Test
    public void recognizesWritableBookPagesAndTitle() {
        GuiScreenBook screen = new GuiScreenBook(null, new ItemStack(new Item()), true);
        assertNotNull(VanillaInputTarget.find(screen));

        ObfuscationReflectionHelper
            .setPrivateValue(GuiScreenBook.class, screen, true, "bookIsSigning", "field_146480_s");
        InputTarget title = VanillaInputTarget.find(screen);
        assertNotNull(title);
        assertEquals(2, title.kind());
    }

    private static final class TestScreen extends GuiScreen {

        private final BufferedTextField textField = new BufferedTextField();
        private int changeCount;

        private TestScreen() {
            textField.setFocused(true);
        }

        @Override
        protected void keyTyped(char character, int keyCode) {
            if (textField.textboxKeyTyped(character, keyCode)) {
                changeCount++;
            }
        }
    }

    private static final class BufferedTextField extends GuiTextField {

        private BufferedTextField() {
            super(null, 0, 0, 100, 20);
        }

        @Override
        public boolean textboxKeyTyped(char character, int keyCode) {
            if (isFocused() && ChatAllowedCharacters.isAllowedCharacter(character)) {
                writeText(TextFieldHandler.textBuffer.toString());
                TextFieldHandler.textBuffer.setLength(0);
                return true;
            }
            return super.textboxKeyTyped(character, keyCode);
        }
    }
}
