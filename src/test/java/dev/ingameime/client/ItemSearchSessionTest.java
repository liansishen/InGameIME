package dev.ingameime.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import dev.ingameime.client.dictionary.ItemNameIndex;

public class ItemSearchSessionTest {

    @Test
    public void filtersWithDigitsAndResumesOrdinaryInputAfterRemovingPlus() {
        ItemNameIndex index = ItemNameIndex.build(Arrays.asList("16A \u52a8\u529b\u4ed3", "4A \u52a8\u529b\u4ed3"));
        ItemSearchSession session = new ItemSearchSession();
        session.begin("dslich+", false, index, true);
        for (char character : "16a".toCharArray()) {
            session.edit(character, index, true);
        }
        assertEquals("16A \u52a8\u529b\u4ed3", session.selection());
        assertFalse(session.shouldResumeRime());
        for (int i = 0; i < 4; i++) {
            session.edit(0xff08, index, true);
        }
        assertTrue(session.shouldResumeRime());
        assertEquals("dslich", session.query());
    }

    @Test
    public void directModeStaysActiveAndSupportsSelectionAndNoMatches() {
        ItemNameIndex index = ItemNameIndex.build(Arrays.asList("16A", "4A"));
        ItemSearchSession session = new ItemSearchSession();
        session.begin("a", true, index, true);
        session.edit(0xff54, index, true);
        assertEquals("4A", session.selection());
        session.edit('+', index, true);
        assertEquals("4A", session.selection());
        session.edit('z', index, true);
        assertNull(session.selection());
        assertFalse(session.shouldResumeRime());
        session.reset();
        assertFalse(session.isActive());
    }
}
