package dev.ingameime.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.lwjgl.input.Keyboard;

public class Lwjgl3ifyKeyEventFilterTest {

    private final Lwjgl3ifyKeyEventFilter filter = new Lwjgl3ifyKeyEventFilter();
    private final Object owner = new Object();

    @Test
    public void consumesTextEventAfterConsumedPhysicalCharacter() {
        assertFalse(filter.consumeDuplicate(owner, Keyboard.KEY_C, 'c', true));
        filter.recordResult(owner, Keyboard.KEY_C, 'c', true, true);

        assertTrue(filter.consumeDuplicate(owner, Keyboard.KEY_NONE, 'c', true));
        assertFalse(filter.consumeDuplicate(owner, Keyboard.KEY_NONE, 'c', true));
    }

    @Test
    public void consumesTextEventAfterConsumedPhysicalSpace() {
        filter.recordResult(owner, Keyboard.KEY_SPACE, ' ', true, true);

        assertTrue(filter.consumeDuplicate(owner, Keyboard.KEY_NONE, ' ', true));
    }

    @Test
    public void preservesTextEventAfterUnconsumedPhysicalCharacter() {
        filter.recordResult(owner, Keyboard.KEY_C, 'c', true, false);

        assertFalse(filter.consumeDuplicate(owner, Keyboard.KEY_NONE, 'c', true));
    }

    @Test
    public void preservesIndependentMismatchedAndDifferentTargetsTextEvents() {
        assertFalse(filter.consumeDuplicate(owner, Keyboard.KEY_NONE, 'c', true));

        filter.recordResult(owner, Keyboard.KEY_C, 'c', true, true);
        assertFalse(filter.consumeDuplicate(owner, Keyboard.KEY_NONE, 'x', true));

        filter.recordResult(owner, Keyboard.KEY_C, 'c', true, true);
        assertFalse(filter.consumeDuplicate(new Object(), Keyboard.KEY_NONE, 'c', true));
    }

    @Test
    public void preservesPhysicalOnlyInputAndDeduplicatesEachRepeatPair() {
        filter.recordResult(owner, Keyboard.KEY_C, 'c', true, true);
        assertFalse(filter.consumeDuplicate(owner, Keyboard.KEY_LSHIFT, '\0', false));
        assertFalse(filter.consumeDuplicate(owner, Keyboard.KEY_NONE, 'c', true));

        for (int repeat = 0; repeat < 2; repeat++) {
            assertFalse(filter.consumeDuplicate(owner, Keyboard.KEY_C, 'c', true));
            filter.recordResult(owner, Keyboard.KEY_C, 'c', true, true);
            assertTrue(filter.consumeDuplicate(owner, Keyboard.KEY_NONE, 'c', true));
        }
    }
}
