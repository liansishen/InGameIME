package dev.ingameime.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import net.minecraft.client.gui.GuiScreen;

import org.junit.Test;

import jds.bibliocraft.gui.GuiBiblioTextField;

public class BiblioInputTargetTest {

    @Test
    public void findsFocusedFieldsAndInsertsAtTheSelection() throws Exception {
        TestScreen screen = new TestScreen();
        screen.textField.setText("ab");
        screen.textField.setCursorPosition(1);

        InputTarget target = BiblioInputTarget.find(screen);

        assertNotNull(target);
        assertEquals(11, target.bounds().x);
        assertEquals(12, target.bounds().y);
        assertEquals(100, target.bounds().width);
        assertEquals(10, target.bounds().height);
        target.insertText("中文");
        assertEquals("a中文b", screen.textField.getText());
    }

    @Test
    public void ignoresUnfocusedAndHiddenFields() {
        TestScreen screen = new TestScreen();
        screen.textField.setFocused(false);
        assertNull(BiblioInputTarget.find(screen));

        screen.textField.setFocused(true);
        screen.textField.setVisible(false);
        assertNull(BiblioInputTarget.find(screen));
    }

    @Test
    public void findsFocusedArrayLineAndTracksFocusChanges() throws Exception {
        ArrayScreen screen = new ArrayScreen();
        GuiBiblioTextField first = new GuiBiblioTextField(null, 11, 12, 100, 10);
        GuiBiblioTextField second = new GuiBiblioTextField(null, 11, 24, 100, 10);
        screen.lines = new GuiBiblioTextField[] { null, first, second };
        first.setFocused(true);
        InputTarget target = BiblioInputTarget.find(screen);
        assertNotNull(target);
        assertSame(first, target.owner());
        target.insertText("测试");
        assertEquals("测试", first.getText());
        assertEquals("", second.getText());

        first.setFocused(false);
        second.setFocused(true);
        InputTarget next = BiblioInputTarget.find(screen);
        assertNotNull(next);
        assertSame(second, next.owner());
        assertFalse(target.isSameTarget(next));
        assertEquals(24, next.bounds().y);
        next.insertText("中文");
        assertEquals("中文", second.getText());
        second.setVisible(false);
        assertNull(BiblioInputTarget.find(screen));
    }

    @Test
    public void ignoresNullAndEmptyArrays() {
        ArrayScreen screen = new ArrayScreen();
        assertNull(BiblioInputTarget.find(screen));
        screen.lines = new GuiBiblioTextField[0];
        assertNull(BiblioInputTarget.find(screen));
    }

    private static final class ArrayScreen extends GuiScreen {

        private GuiBiblioTextField[] lines;
    }

    private static final class TestScreen extends GuiScreen {

        private final GuiBiblioTextField textField = new GuiBiblioTextField(null, 11, 12, 100, 10);

        private TestScreen() {
            textField.setFocused(true);
        }
    }
}
