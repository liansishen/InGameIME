package dev.ingameime.client.dictionary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ItemNameIndexTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void matchesSyllableFragmentsAndPreservesModelNumbers() throws Exception {
        String name = "IV 16A \u8d85\u5927\u578b\u52a8\u529b\u4ed3";
        ItemNameIndex index = ItemNameIndex.build(Arrays.asList(name, name, "IV 4A \u52a8\u529b\u4ed3", "Circuit 16"));
        assertEquals(3, index.size());
        assertEquals(Collections.singletonList(name), index.search("dslich+16a+icdaxk", true));
        assertEquals(Collections.singletonList(name), index.search("16A+donglicang+chaodaxing", false));
        assertEquals(
            2,
            index.search("dslich+", true)
                .size());
        assertTrue(
            index.search("slich", true)
                .isEmpty());
        assertEquals(Collections.singletonList("Circuit 16"), index.search("circuit+16", true));
        assertEquals(Collections.singletonList("Circuit 16"), index.search("uit+16", true));
        assertEquals(Collections.singletonList(name), index.search("\u8d85\u5927\u578b", false));
        assertTrue(
            index.search("z", true)
                .isEmpty());
        assertEquals(Collections.singletonList(name), index.search("dslich", true, 1));

        Path file = temporary.getRoot()
            .toPath()
            .resolve("index.json");
        index.save(file);
        ItemNameIndex loaded = ItemNameIndex.load(file);
        assertEquals(index.search("dslich", true), loaded.search("dslich", true));
        assertEquals(Collections.singletonList("Circuit 16"), loaded.search("uit+16", false));
        assertEquals(Collections.singletonList(name), loaded.search("dslich", true, 1));
    }
}
