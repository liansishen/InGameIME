package dev.ingameime.client.gui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class InGameImeConfigScreenTest {

    @Test
    public void unchangedImplicitDirectoryRemainsEmptyInConfiguration() {
        String implicit = "D:\\Games\\GTNH\\ingameime\\native";

        String displayed = InGameImeConfigScreen.displayedDirectory("", implicit);

        assertEquals(implicit, displayed);
        assertEquals("", InGameImeConfigScreen.capturedDirectory(displayed, implicit));
    }

    @Test
    public void editedImplicitDirectoryBecomesExplicitConfiguration() {
        String implicit = "D:\\Games\\GTNH\\ingameime\\user";
        String edited = "D:\\Rime\\user";

        assertEquals(edited, InGameImeConfigScreen.capturedDirectory(edited, implicit));
    }

    @Test
    public void explicitDirectoryIsPreservedEvenWhenItMatchesTheDefault() {
        String explicit = "D:\\Games\\GTNH\\ingameime\\shared";

        String displayed = InGameImeConfigScreen.displayedDirectory(explicit, null);

        assertEquals(explicit, displayed);
        assertEquals(explicit, InGameImeConfigScreen.capturedDirectory(displayed, null));
    }
}
