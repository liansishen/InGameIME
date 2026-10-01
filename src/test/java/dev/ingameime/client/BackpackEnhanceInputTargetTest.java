package dev.ingameime.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.ChatAllowedCharacters;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import me.eigenraven.lwjgl3ify.client.TextFieldHandler;

public class BackpackEnhanceInputTargetTest {

    private BackpackEnhanceInputTarget.Api api;
    private GuiScreen screen;
    private TestPanel panel;

    @Before
    public void setUp() throws Exception {
        api = new BackpackEnhanceInputTarget.Api(TestController.class);
        screen = new GuiScreen();
        panel = new TestPanel();
        TestController.screen = screen;
        TestController.panel = panel;
        TextFieldHandler.textBuffer.setLength(0);
    }

    @After
    public void tearDown() {
        TestController.screen = null;
        TestController.panel = null;
        TextFieldHandler.textBuffer.setLength(0);
    }

    @Test
    public void findsFocusedSearchWithItsOwnBounds() {
        InputTarget target = api.find(screen);
        assertNotNull(target);
        assertSame(panel, target.owner());
        assertEquals(0, target.kind());
        assertEquals(10, target.bounds().x);
        assertEquals(20, target.bounds().y);
        assertEquals(120, target.bounds().width);
        assertEquals(14, target.bounds().height);
    }

    @Test
    public void rejectsInactiveUnfocusedHiddenMinimizedAndEmptyOverlays() {
        assertNull(api.find(new GuiScreen()));
        assertNull(api.find(new Object()));
        assertNull(api.find(null));
        TestController.panel = null;
        assertNull(api.find(screen));
        TestController.panel = panel;
        panel.searchField.setFocused(false);
        assertNull(api.find(screen));
        panel.searchField.setFocused(true);
        panel.searchField.setVisible(false);
        assertNull(api.find(screen));
        panel.searchField.setVisible(true);
        panel.minimized = true;
        assertNull(api.find(screen));
        panel.minimized = false;
        panel.hasTabs = false;
        assertNull(api.find(screen));
    }

    @Test
    public void commitsThroughPanelAndPreservesQueuedText() throws Exception {
        panel.searchField.setText("ab");
        panel.searchField.setCursorPosition(1);
        TextFieldHandler.textBuffer.append("cc");
        InputTarget target = api.find(screen);
        target.insertText("中文");
        assertEquals("a中文b", panel.searchField.getText());
        assertEquals("a中文b", panel.searchText);
        assertEquals(2, panel.filterUpdates);
        assertEquals("cc", TextFieldHandler.textBuffer.toString());
        assertTrue(panel.searchField.isFocused());
    }

    @Test
    public void replacingFieldForLayoutKeepsTargetAndUpdatesBounds() throws Exception {
        InputTarget target = api.find(screen);
        panel.searchField = focusedField(50, 60, 160);
        assertTrue(target.isSameTarget(api.find(screen)));
        assertEquals(50, target.bounds().x);
        assertEquals(60, target.bounds().y);
        assertEquals(160, target.bounds().width);
        target.insertText("草");
        assertEquals("草", panel.searchText);
        TestController.panel = new TestPanel();
        assertFalse(target.isSameTarget(api.find(screen)));
    }

    @Test
    public void restoresQueuedTextWhenPanelInputFails() throws Exception {
        TextFieldHandler.textBuffer.append("queued");
        panel.failInput = true;
        try {
            api.find(screen)
                .insertText("草");
            throw new AssertionError("Expected panel input failure");
        } catch (InvocationTargetException failure) {
            assertTrue(failure.getCause() instanceof IllegalStateException);
        }
        assertEquals("queued", TextFieldHandler.textBuffer.toString());
    }

    @Test(expected = NoSuchMethodException.class)
    public void detectsVersionsWithoutSearchSupport() throws Exception {
        new BackpackEnhanceInputTarget.Api(LegacyController.class);
    }

    private static GuiTextField focusedField(int x, int y, int width) {
        GuiTextField field = new BufferedTextField(x, y, width);
        field.setFocused(true);
        return field;
    }

    public static final class TestController {

        private static GuiScreen screen;
        private static TestPanel panel;

        public static boolean isActiveFor(GuiScreen gui) {
            return gui == screen;
        }

        public static TestPanel getPanel() {
            return panel;
        }
    }

    public static final class LegacyController {

        public static boolean isActiveFor(GuiScreen gui) {
            return true;
        }

        public static Object getPanel() {
            return null;
        }
    }

    public static final class TestPanel {

        private GuiTextField searchField = focusedField(10, 20, 120);
        private String searchText = "";
        private int filterUpdates;
        private boolean minimized;
        private boolean hasTabs = true;
        private boolean failInput;

        public boolean isSearchFocused() {
            return !minimized && hasTabs && searchField != null && searchField.isFocused();
        }

        public boolean keyTyped(char character, int key) {
            if (failInput) {
                throw new IllegalStateException("panel input rejected");
            }
            if (!isSearchFocused()) {
                return false;
            }
            searchField.textboxKeyTyped(character, key);
            searchText = searchField.getText();
            filterUpdates++;
            return true;
        }
    }

    private static final class BufferedTextField extends GuiTextField {

        private BufferedTextField(int x, int y, int width) {
            super(null, x, y, width, 14);
        }

        @Override
        public boolean textboxKeyTyped(char character, int key) {
            if (isFocused() && ChatAllowedCharacters.isAllowedCharacter(character)) {
                writeText(TextFieldHandler.textBuffer.toString());
                TextFieldHandler.textBuffer.setLength(0);
                return true;
            }
            return false;
        }
    }
}
