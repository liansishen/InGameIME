package dev.ingameime.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Field;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.hepdd.clipboardanywhere.client.gui.ClipboardOverlay;
import com.hepdd.clipboardanywhere.client.gui.OverlayGeometry;

public class ClipboardAnywhereInputTargetTest {

    private static final Field RENAME_FIELD = overlayField("renameField");
    private static final Field TASK_EDIT_FIELD = overlayField("taskEditField");

    private double originalScale;

    @Before
    public void setUp() throws Exception {
        originalScale = com.hepdd.clipboardanywhere.Config.scale;
        com.hepdd.clipboardanywhere.Config.scale = 1.5;
        clearFields();
    }

    @After
    public void tearDown() throws Exception {
        clearFields();
        com.hepdd.clipboardanywhere.Config.scale = originalScale;
    }

    @Test
    public void findsFocusedRenameFieldAndUsesOverlayGeometry() throws Exception {
        TestScreen screen = new TestScreen();
        GuiTextField field = new GuiTextField(null, 10, 20, 100, 12);
        field.setText("ab");
        field.setCursorPosition(1);
        field.setFocused(true);
        RENAME_FIELD.set(ClipboardOverlay.INSTANCE, field);

        InputTarget target = ClipboardAnywhereInputTarget.find(screen);

        assertNotNull(target);
        OverlayGeometry geometry = ClipboardOverlay.INSTANCE.geometry(screen.width, screen.height);
        assertEquals(geometry.getLeft() + scaled(field.xPosition, geometry.getScale()), target.bounds().x);
        assertEquals(geometry.getTop() + scaled(field.yPosition, geometry.getScale()), target.bounds().y);
        assertEquals(scaled(field.width, geometry.getScale()), target.bounds().width);
        assertEquals(scaled(field.height, geometry.getScale()), target.bounds().height);
        target.insertText("中文");
        assertEquals("a中文b", field.getText());
    }

    @Test
    public void findsTaskFieldAndIgnoresInactiveFields() throws Exception {
        TestScreen screen = new TestScreen();
        GuiTextField rename = new GuiTextField(null, 0, 0, 10, 10);
        GuiTextField task = new GuiTextField(null, 0, 0, 10, 10);
        task.setFocused(true);
        RENAME_FIELD.set(ClipboardOverlay.INSTANCE, rename);
        TASK_EDIT_FIELD.set(ClipboardOverlay.INSTANCE, task);

        assertEquals(
            task,
            ClipboardAnywhereInputTarget.find(screen)
                .owner());

        task.setVisible(false);
        assertNull(ClipboardAnywhereInputTarget.find(screen));
    }

    private static void clearFields() throws Exception {
        RENAME_FIELD.set(ClipboardOverlay.INSTANCE, null);
        TASK_EDIT_FIELD.set(ClipboardOverlay.INSTANCE, null);
    }

    private static int scaled(int value, double scale) {
        return (int) Math.round(value * scale);
    }

    private static Field overlayField(String name) {
        try {
            Field field = ClipboardOverlay.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class TestScreen extends GuiScreen {

        private TestScreen() {
            width = 800;
            height = 600;
        }
    }
}
