package dev.ingameime.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import dev.ingameime.client.dictionary.ItemNameIndex;
import dev.ingameime.rime.RimeSnapshot;

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

    @Test
    public void syncRefreshesOnIndexTransitionWithoutRedundantSnapshotRebuilds() {
        ItemSearchSession session = new ItemSearchSession();
        session.begin("dslich", true, null, true);

        assertNull(session.selection());
        RimeSnapshot initialEmpty = session.snapshot();
        assertTrue(
            initialEmpty.getCandidates()
                .isEmpty());

        session.sync(null, true);
        assertSame(initialEmpty, session.snapshot());
        session.sync(ItemNameIndex.EMPTY, true);
        assertSame(initialEmpty, session.snapshot());

        ItemNameIndex index1 = ItemNameIndex.build(Arrays.asList("16A \u52a8\u529b\u4ed3", "4A \u52a8\u529b\u4ed3"));
        session.sync(index1, true);
        assertEquals("16A \u52a8\u529b\u4ed3", session.selection());
        RimeSnapshot loadedSnapshot = session.snapshot();
        assertNotSame(initialEmpty, loadedSnapshot);
        assertEquals(
            2,
            loadedSnapshot.getCandidates()
                .size());

        session.sync(index1, true);
        assertSame(loadedSnapshot, session.snapshot());

        session.edit(0xff54, index1, true);
        assertEquals("4A \u52a8\u529b\u4ed3", session.selection());
        assertEquals(
            1,
            session.snapshot()
                .getHighlightedCandidate());

        ItemNameIndex index2 = ItemNameIndex
            .build(Arrays.asList("16A \u52a8\u529b\u4ed3", "1A \u52a8\u529b\u4ed3", "4A \u52a8\u529b\u4ed3"));
        session.sync(index2, true);
        assertEquals("4A \u52a8\u529b\u4ed3", session.selection());
        assertEquals(
            3,
            session.snapshot()
                .getCandidates()
                .size());
        assertEquals(
            2,
            session.snapshot()
                .getHighlightedCandidate());
    }
}
