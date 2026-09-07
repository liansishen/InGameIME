package dev.ingameime.client.dictionary;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.promeg.pinyinhelper.Pinyin;

final class GameDictionaryGenerator {

    private static final String BLOCK_START = "# >>> InGameIME generated game dictionary >>>";
    private static final String BLOCK_END = "# <<< InGameIME generated game dictionary <<<";
    private static final Pattern DATABASE_NAME = Pattern.compile("(?m)^#@/db_name\\t[^\\r\\n]*");
    private static final Pattern FORMATTING = Pattern.compile("\\u00a7[0-9A-FK-ORa-fk-or]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern TRANSLATION_KEY = Pattern.compile("(?:item|tile)\\..+\\.name");

    private GameDictionaryGenerator() {}

    static Result generateIndex(List<String> rawNames, File target, BooleanSupplier cancelled, IntConsumer progress,
        Runnable beforeWrite) throws IOException {
        Set<String> names = new java.util.TreeSet<>();
        int empty = 0;
        int keys = 0;
        int duplicates = 0;
        for (int i = 0; i < rawNames.size(); i++) {
            if (cancelled.getAsBoolean()) {
                return new Result(true, i, names.size(), empty, keys, duplicates, 0);
            }
            String name = clean(rawNames.get(i));
            if (name.isEmpty()) {
                empty++;
            } else if (TRANSLATION_KEY.matcher(name)
                .matches()) {
                    keys++;
                } else if (!names.add(name)) {
                    duplicates++;
                }
            progress.accept(i + 1);
        }
        ItemNameIndex index = ItemNameIndex.build(new java.util.ArrayList<>(names));
        if (cancelled.getAsBoolean()) {
            return new Result(true, rawNames.size(), names.size(), empty, keys, duplicates, 0);
        }
        beforeWrite.run();
        index.save(target.toPath());
        return new Result(false, rawNames.size(), names.size(), empty, keys, duplicates, 0);
    }

    static Result generate(List<String> rawNames, File target, File flypyTarget, String fingerprint,
        BooleanSupplier cancelled, IntConsumer progress, Runnable beforeWrite) throws IOException {
        Map<String, String> entries = new TreeMap<>();
        Map<String, String> flypyEntries = flypyTarget == null ? null : new TreeMap<>();
        Set<String> seen = new HashSet<>();
        int emptyNames = 0;
        int translationKeys = 0;
        int duplicateNames = 0;
        int nonChineseNames = 0;
        for (int index = 0; index < rawNames.size(); index++) {
            if (cancelled.getAsBoolean()) {
                return new Result(
                    true,
                    index,
                    entries.size(),
                    emptyNames,
                    translationKeys,
                    duplicateNames,
                    nonChineseNames);
            }
            String name = clean(rawNames.get(index));
            boolean translationKey = TRANSLATION_KEY.matcher(name)
                .matches();
            if (name.isEmpty()) {
                emptyNames++;
            } else if (translationKey) {
                translationKeys++;
            } else if (!seen.add(name)) {
                duplicateNames++;
            } else {
                String code = pinyinCode(name);
                if (code.isEmpty()) {
                    nonChineseNames++;
                } else {
                    entries.put(name, code.replace(" ", ""));
                    if (flypyEntries != null) {
                        flypyEntries.put(name, flypyCode(code));
                    }
                }
            }
            if ((index & 31) == 31 || index + 1 == rawNames.size()) {
                progress.accept(index + 1);
            }
        }

        if (cancelled.getAsBoolean()) {
            return new Result(
                true,
                rawNames.size(),
                entries.size(),
                emptyNames,
                translationKeys,
                duplicateNames,
                nonChineseNames);
        }
        beforeWrite.run();
        if (cancelled.getAsBoolean()) {
            return new Result(
                true,
                rawNames.size(),
                entries.size(),
                emptyNames,
                translationKeys,
                duplicateNames,
                nonChineseNames);
        }
        write(target.toPath(), entries, fingerprint);
        if (flypyTarget != null) {
            write(flypyTarget.toPath(), flypyEntries, fingerprint);
        }
        return new Result(
            false,
            rawNames.size(),
            entries.size(),
            emptyNames,
            translationKeys,
            duplicateNames,
            nonChineseNames);
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String unformatted = FORMATTING.matcher(value)
            .replaceAll("");
        return WHITESPACE.matcher(unformatted)
            .replaceAll(" ")
            .trim();
    }

    private static String pinyinCode(String name) {
        StringBuilder code = new StringBuilder();
        StringBuilder latin = new StringBuilder();
        boolean hasChinese = false;
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (Pinyin.isChinese(character)) {
                appendToken(code, latin);
                appendToken(
                    code,
                    Pinyin.toPinyin(character)
                        .toLowerCase(Locale.ROOT));
                hasChinese = true;
            } else if (character < 128 && Character.isLetter(character)) {
                latin.append(Character.toLowerCase(character));
            } else {
                appendToken(code, latin);
            }
        }
        appendToken(code, latin);
        return hasChinese ? code.toString() : "";
    }

    static String flypyCode(String fullPinyin) {
        StringBuilder code = new StringBuilder();
        for (String token : fullPinyin.split(" ")) {
            code.append(flypyToken(token));
        }
        return code.toString();
    }

    private static String flypyToken(String syllable) {
        String initial = "";
        if (syllable.startsWith("zh") || syllable.startsWith("ch") || syllable.startsWith("sh")) {
            initial = syllable.substring(0, 2);
        } else if (!syllable.isEmpty() && "bpmfdtnlgkhjqxrzcsyw".indexOf(syllable.charAt(0)) >= 0) {
            initial = syllable.substring(0, 1);
        }
        String finalPart = syllable.substring(initial.length());
        String finalKey = flypyFinal(finalPart);
        if (finalKey == null) {
            return syllable;
        }
        if (initial.isEmpty()) {
            if (syllable.length() == 1) {
                return syllable + syllable;
            }
            if (syllable.length() == 2) {
                return syllable;
            }
            return syllable.charAt(0) + finalKey;
        }
        if ("zh".equals(initial)) {
            initial = "v";
        } else if ("ch".equals(initial)) {
            initial = "i";
        } else if ("sh".equals(initial)) {
            initial = "u";
        }
        return initial + finalKey;
    }

    private static String flypyFinal(String finalPart) {
        switch (finalPart) {
            case "a":
            case "e":
            case "i":
            case "o":
            case "u":
            case "v":
                return finalPart;
            case "ai":
                return "d";
            case "an":
                return "j";
            case "ang":
                return "h";
            case "ao":
                return "c";
            case "ei":
                return "w";
            case "en":
                return "f";
            case "eng":
                return "g";
            case "er":
                return "r";
            case "ia":
            case "ua":
                return "x";
            case "ian":
                return "m";
            case "iang":
            case "uang":
                return "l";
            case "iao":
                return "n";
            case "ie":
                return "p";
            case "in":
                return "b";
            case "ing":
            case "uai":
                return "k";
            case "iong":
            case "ong":
                return "s";
            case "iu":
                return "q";
            case "ou":
                return "z";
            case "uan":
                return "r";
            case "ue":
            case "ve":
                return "t";
            case "ui":
                return "v";
            case "un":
                return "y";
            case "uo":
                return "o";
            default:
                return null;
        }
    }

    private static void appendToken(StringBuilder destination, CharSequence token) {
        if (token.length() == 0) {
            return;
        }
        if (destination.length() > 0) {
            destination.append(' ');
        }
        destination.append(token);
        if (token instanceof StringBuilder) {
            ((StringBuilder) token).setLength(0);
        }
    }

    private static void write(Path target, Map<String, String> entries, String fingerprint) throws IOException {
        byte[] previous = Files.exists(target) ? Files.readAllBytes(target) : new byte[0];
        String existing = new String(previous, StandardCharsets.UTF_8);
        String replacement = replaceGeneratedBlock(
            existing,
            generatedBlock(entries, fingerprint),
            target.getFileName()
                .toString());
        writeAtomically(target, previous, replacement);
    }

    static void removeGeneratedBlock(File target) throws IOException {
        Path path = target.toPath();
        if (!Files.exists(path)) {
            return;
        }
        byte[] previous = Files.readAllBytes(path);
        String existing = new String(previous, StandardCharsets.UTF_8);
        int start = existing.indexOf(BLOCK_START);
        int end = existing.indexOf(BLOCK_END);
        if ((start < 0) != (end < 0) || end < start) {
            throw new IOException(target.getName() + " contains an incomplete InGameIME generated block");
        }
        if (start < 0) {
            return;
        }
        int afterEnd = end + BLOCK_END.length();
        if (afterEnd < existing.length() && existing.charAt(afterEnd) == '\r') {
            afterEnd++;
        }
        if (afterEnd < existing.length() && existing.charAt(afterEnd) == '\n') {
            afterEnd++;
        }
        writeAtomically(path, previous, existing.substring(0, start) + existing.substring(afterEnd));
    }

    private static void writeAtomically(Path target, byte[] previous, String replacement) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), "ingameime-game-dictionary.", ".tmp");
        try {
            Files.write(temporary, replacement.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            Files.deleteIfExists(temporary);
            restore(target, previous);
            throw failure;
        }
    }

    private static void restore(Path target, byte[] previous) throws IOException {
        if (previous.length == 0) {
            Files.deleteIfExists(target);
        } else {
            Files.write(target, previous);
        }
    }

    private static String replaceGeneratedBlock(String existing, String block, String fileName) throws IOException {
        int start = existing.indexOf(BLOCK_START);
        int end = existing.indexOf(BLOCK_END);
        if ((start < 0) != (end < 0) || end < start) {
            throw new IOException(fileName + " contains an incomplete InGameIME generated block");
        }
        if (start >= 0) {
            int afterEnd = end + BLOCK_END.length();
            if (afterEnd < existing.length() && existing.charAt(afterEnd) == '\r') {
                afterEnd++;
            }
            if (afterEnd < existing.length() && existing.charAt(afterEnd) == '\n') {
                afterEnd++;
            }
            return correctDatabaseName(existing.substring(0, start) + block + existing.substring(afterEnd), fileName);
        }
        String prefix = existing;
        if (prefix.isEmpty()) {
            prefix = "# Rime table\n# coding: utf-8\n#@/db_name\t" + fileName + "\n#@/db_type\ttabledb\n\n";
        } else if (!prefix.endsWith("\n")) {
            prefix += "\n";
        }
        return correctDatabaseName(prefix + "\n" + block, fileName);
    }

    private static String correctDatabaseName(String content, String fileName) {
        Matcher matcher = DATABASE_NAME.matcher(content);
        return matcher.find() ? matcher.replaceFirst(Matcher.quoteReplacement("#@/db_name\t" + fileName)) : content;
    }

    private static String generatedBlock(Map<String, String> entries, String fingerprint) {
        StringBuilder block = new StringBuilder();
        block.append(BLOCK_START)
            .append('\n')
            .append("# generator: InGameIME game dictionary v3\n")
            .append("# fingerprint: ")
            .append(fingerprint)
            .append('\n');
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            block.append(entry.getKey())
                .append('\t')
                .append(entry.getValue())
                .append("\t10\n");
        }
        return block.append(BLOCK_END)
            .append('\n')
            .toString();
    }

    static final class Result {

        final boolean cancelled;
        final int processed;
        final int entries;
        final int skipped;
        final int emptyNames;
        final int translationKeys;
        final int duplicateNames;
        final int nonChineseNames;

        Result(boolean cancelled, int processed, int entries, int emptyNames, int translationKeys, int duplicateNames,
            int nonChineseNames) {
            this.cancelled = cancelled;
            this.processed = processed;
            this.entries = entries;
            this.emptyNames = emptyNames;
            this.translationKeys = translationKeys;
            this.duplicateNames = duplicateNames;
            this.nonChineseNames = nonChineseNames;
            this.skipped = emptyNames + translationKeys + duplicateNames + nonChineseNames;
        }
    }
}
