package dev.ingameime.rime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RimePatchInstallerTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void installsSeparateCompletionTranslatorsForFullPinyinAndFlypy() throws Exception {
        File userDirectory = temporaryFolder.newFolder("user");

        RimePatchInstaller.install(userDirectory);

        String fullPinyin = read(new File(userDirectory, "rime_ice.custom.yaml"));
        String flypy = read(new File(userDirectory, "double_pinyin_flypy.custom.yaml"));
        assertPatch(fullPinyin, "ingameime_game_phrase");
        assertPatch(flypy, "ingameime_game_phrase_flypy");
    }

    @Test
    public void preservesAnExistingUnmanagedPatch() throws Exception {
        File userDirectory = temporaryFolder.newFolder("unmanaged");
        File target = new File(userDirectory, "rime_ice.custom.yaml");
        String existing = "patch:\n  menu/page_size: 9\n";
        Files.write(target.toPath(), existing.getBytes(StandardCharsets.UTF_8));

        RimePatchInstaller.install(userDirectory);

        assertEquals(existing, read(target));
        assertTrue(new File(userDirectory, "double_pinyin_flypy.custom.yaml").isFile());
    }

    @Test
    public void upgradesAnExistingManagedPatch() throws Exception {
        File userDirectory = temporaryFolder.newFolder("managed");
        File target = new File(userDirectory, "rime_ice.custom.yaml");
        Files.write(
            target.toPath(),
            "# Managed by InGameIME game dictionary patch v1.\nold\n".getBytes(StandardCharsets.UTF_8));

        RimePatchInstaller.install(userDirectory);

        assertPatch(read(target), "ingameime_game_phrase");
    }

    private static void assertPatch(String content, String userDictionary) {
        assertTrue(content.contains("\"engine/translators/+\":"));
        assertTrue(content.contains("table_translator@ingameime_game_dictionary"));
        assertTrue(content.contains("user_dict: " + userDictionary));
        assertTrue(content.contains("enable_completion: true"));
        assertTrue(content.contains("enable_sentence: false"));
        assertTrue(content.contains("initial_quality: 1.0"));
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
