package dev.ingameime.rime;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import dev.ingameime.InGameIME;

public final class RimePatchInstaller {

    private static final String MANAGED_HEADER = "# Managed by InGameIME game dictionary patch v1.\n";
    private static final String COMPONENT = "ingameime_game_dictionary";

    private RimePatchInstaller() {}

    public static void install(File userDataDirectory) throws IOException {
        install(new File(userDataDirectory, "rime_ice.custom.yaml").toPath(), "ingameime_game_phrase");
        install(new File(userDataDirectory, "double_pinyin_flypy.custom.yaml").toPath(), "ingameime_game_phrase_flypy");
    }

    private static void install(Path target, String userDictionary) throws IOException {
        String replacement = patch(userDictionary);
        if (Files.exists(target)) {
            if (!Files.isRegularFile(target)) {
                throw new IOException(target.getFileName() + " is not a regular file");
            }
            String existing = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
            if (!existing.startsWith(MANAGED_HEADER)) {
                InGameIME.LOG.warn(
                    "Keeping existing unmanaged Rime patch {}; add the InGameIME game dictionary translator manually",
                    target.toAbsolutePath());
                return;
            }
            if (existing.equals(replacement)) {
                return;
            }
        }
        writeAtomically(target, replacement);
    }

    private static String patch(String userDictionary) {
        return MANAGED_HEADER + "# Remove the managed header before editing this file manually.\n"
            + "patch:\n"
            + "  \"engine/translators/+\":\n"
            + "    - table_translator@"
            + COMPONENT
            + "\n"
            + "  "
            + COMPONENT
            + ":\n"
            + "    dictionary: \"\"\n"
            + "    user_dict: "
            + userDictionary
            + "\n"
            + "    db_class: stabledb\n"
            + "    enable_completion: true\n"
            + "    enable_sentence: false\n"
            + "    initial_quality: 1.0\n";
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), "ingameime-rime-patch.", ".tmp");
        try {
            Files.write(temporary, content.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
