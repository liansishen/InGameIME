package dev.ingameime.client.dictionary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GameDictionaryGeneratorTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void writesDeduplicatedChineseNamesAndPreservesUserContent() throws Exception {
        File target = temporaryFolder.newFile("custom_phrase.txt");
        write(target, "# Rime table\n\n用户短语\tyong hu\t20\n");
        AtomicInteger progress = new AtomicInteger();

        GameDictionaryGenerator.Result result = GameDictionaryGenerator.generate(
            Arrays.asList("钻石块", "钻石块", "Diamond", "\u00a7a铁锭"),
            target,
            null,
            "fingerprint-one",
            () -> false,
            progress::set,
            () -> {});

        String content = read(target);
        assertFalse(result.cancelled);
        assertEquals(4, result.processed);
        assertEquals(2, result.entries);
        assertEquals(2, result.skipped);
        assertEquals(0, result.emptyNames);
        assertEquals(0, result.translationKeys);
        assertEquals(1, result.duplicateNames);
        assertEquals(1, result.nonChineseNames);
        assertEquals(4, progress.get());
        assertTrue(content.contains("用户短语\tyong hu\t20"));
        assertTrue(content.contains("钻石块\tzuanshikuai\t10"));
        assertTrue(content.contains("铁锭\ttieding\t10"));
        assertEquals(1, occurrences(content, "# >>> InGameIME generated game dictionary >>>"));
    }

    @Test
    public void reportsEachSkipReasonSeparately() throws Exception {
        File target = temporaryFolder.newFile("skip-reasons.txt");

        GameDictionaryGenerator.Result result = GameDictionaryGenerator.generate(
            Arrays.asList("", "item.example.name", "钻石块", "钻石块", "Diamond", "ME接口"),
            target,
            null,
            "skip-reasons",
            () -> false,
            ignored -> {},
            () -> {});

        assertEquals(6, result.processed);
        assertEquals(2, result.entries);
        assertEquals(4, result.skipped);
        assertEquals(1, result.emptyNames);
        assertEquals(1, result.translationKeys);
        assertEquals(1, result.duplicateNames);
        assertEquals(1, result.nonChineseNames);
        assertTrue(read(target).contains("ME接口\tmejiekou\t10"));
    }

    @Test
    public void replacesOnlyThePreviousGeneratedBlock() throws Exception {
        File target = temporaryFolder.newFile("custom_phrase.txt");
        write(target, "# Rime table\n\n用户短语\tyong hu\t20\n");
        generate(target, "钻石块", "first");
        generate(target, "金锭", "second");

        String content = read(target);
        assertTrue(content.contains("用户短语\tyong hu\t20"));
        assertTrue(content.contains("金锭\tjinding\t10"));
        assertFalse(content.contains("钻石块\tzuanshikuai\t10"));
        assertEquals(1, occurrences(content, "# >>> InGameIME generated game dictionary >>>"));
        assertEquals(1, occurrences(content, "# <<< InGameIME generated game dictionary <<<"));
    }

    @Test
    public void correctsAnExistingDatabaseNameForTheDedicatedDictionary() throws Exception {
        File target = temporaryFolder.newFile("ingameime_game_phrase.txt");
        write(target, "# Rime table\n#@/db_name\tcustom_phrase.txt\n#@/db_type\ttabledb\n");

        generate(target, "钻石块", "renamed");

        String content = read(target);
        assertTrue(content.contains("#@/db_name\tingameime_game_phrase.txt"));
        assertFalse(content.contains("#@/db_name\tcustom_phrase.txt"));
    }

    @Test
    public void cancellationLeavesThePreviousFileUntouched() throws Exception {
        File target = temporaryFolder.newFile("custom_phrase.txt");
        String previous = "# Rime table\n\n用户短语\tyong hu\t20\n";
        write(target, previous);

        GameDictionaryGenerator.Result result = GameDictionaryGenerator.generate(
            Collections.singletonList("钻石块"),
            target,
            null,
            "cancelled",
            () -> true,
            ignored -> {},
            () -> fail("writing must not start after cancellation"));

        assertTrue(result.cancelled);
        assertEquals(0, result.processed);
        assertEquals(previous, read(target));
    }

    @Test
    public void incompleteGeneratedMarkersLeaveThePreviousFileUntouched() throws Exception {
        File target = temporaryFolder.newFile("custom_phrase.txt");
        String previous = "# Rime table\n# >>> InGameIME generated game dictionary >>>\n旧词\tjiu ci\t10\n";
        write(target, previous);

        try {
            generate(target, "金锭", "broken");
            fail("expected an incomplete marker failure");
        } catch (IOException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("incomplete"));
        }
        assertEquals(previous, read(target));
    }

    @Test
    public void writesFlypyCodesToTheDoublePinyinDictionary() throws Exception {
        File fullPinyin = temporaryFolder.newFile("ingameime_game_phrase.txt");
        File flypy = temporaryFolder.newFile("ingameime_game_phrase_flypy.txt");

        GameDictionaryGenerator.generate(
            Arrays.asList("你好", "小鹤双拼", "ME接口", "1024k-ME存储元件"),
            fullPinyin,
            flypy,
            "flypy",
            () -> false,
            ignored -> {},
            () -> {});

        String content = read(flypy);
        assertTrue(content.contains("#@/db_name\tingameime_game_phrase_flypy.txt"));
        assertTrue(content.contains("你好\tnihc\t10"));
        assertTrue(content.contains("小鹤双拼\txnheulpb\t10"));
        assertTrue(content.contains("ME接口\tmejpkz\t10"));
        assertTrue(content.contains("1024k-ME存储元件\tkmecyiuyrjm\t10"));
        assertTrue(read(fullPinyin).contains("1024k-ME存储元件\tkmecunchuyuanjian\t10"));
    }

    @Test
    public void removesOnlyTheGeneratedBlockFromLegacyCustomPhrases() throws Exception {
        File legacy = temporaryFolder.newFile("custom_phrase.txt");
        write(
            legacy,
            "# Rime table\n\n用户短语\tyong hu\t20\n# >>> InGameIME generated game dictionary >>>\n"
                + "钻石块\tzuanshikuai\t10\n# <<< InGameIME generated game dictionary <<<\n保留短语\tbao liu\t20\n");

        GameDictionaryGenerator.removeGeneratedBlock(legacy);

        String content = read(legacy);
        assertTrue(content.contains("用户短语\tyong hu\t20"));
        assertTrue(content.contains("保留短语\tbao liu\t20"));
        assertFalse(content.contains("钻石块\tzuanshikuai\t10"));
        assertFalse(content.contains("InGameIME generated game dictionary"));
    }

    private static void generate(File target, String name, String fingerprint) throws IOException {
        GameDictionaryGenerator
            .generate(Collections.singletonList(name), target, null, fingerprint, () -> false, ignored -> {}, () -> {});
    }

    private static void write(File file, String content) throws IOException {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
