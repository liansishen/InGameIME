package dev.ingameime.client.dictionary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import com.github.promeg.pinyinhelper.Pinyin;
import com.google.gson.Gson;

public final class ItemNameIndex {

    public static final ItemNameIndex EMPTY = new ItemNameIndex(Collections.emptyList());
    private final List<Entry> entries;

    private ItemNameIndex(List<Entry> entries) {
        this.entries = entries;
    }

    public static ItemNameIndex build(List<String> names) {
        List<Entry> entries = new ArrayList<>();
        for (String name : new TreeSet<>(names)) {
            entries.add(new Entry(name));
        }
        return new ItemNameIndex(entries);
    }

    public int size() {
        return entries.size();
    }

    public List<String> search(String query, boolean flypy) {
        List<String> result = new ArrayList<>();
        String[] terms = query.toLowerCase(Locale.ROOT)
            .split("\\+", -1);
        boolean hasTerm = false;
        for (String term : terms) {
            hasTerm |= !term.isEmpty();
        }
        if (!hasTerm) {
            return result;
        }
        for (Entry entry : entries) {
            boolean matches = true;
            for (String term : terms) {
                if (!term.isEmpty() && !entry.matches(term, flypy)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                result.add(entry.name);
            }
        }
        return result;
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "item-index-", ".tmp");
        try {
            Files.write(
                temporary,
                new Gson().toJson(entries)
                    .getBytes(StandardCharsets.UTF_8));
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static ItemNameIndex load(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return EMPTY;
        }
        Entry[] values = new Gson()
            .fromJson(new String(Files.readAllBytes(path), StandardCharsets.UTF_8), Entry[].class);
        List<Entry> entries = new ArrayList<>();
        if (values == null) {
            throw new IOException("Invalid item name index");
        }
        for (Entry entry : values) {
            if (entry == null || entry.name == null
                || entry.literal == null
                || entry.full == null
                || entry.flypy == null) {
                throw new IOException("Invalid item name index entry");
            }
            entries.add(entry);
        }
        return new ItemNameIndex(entries);
    }

    private static final class Entry {

        private final String name;
        private final String literal;
        private final List<String> full = new ArrayList<>();
        private final List<String> flypy = new ArrayList<>();

        private Entry(String name) {
            this.name = name;
            literal = name.toLowerCase(Locale.ROOT);
            for (int start = 0; start < name.length(); start++) {
                if (!Pinyin.isChinese(name.charAt(start))) {
                    continue;
                }
                StringBuilder pinyin = new StringBuilder();
                StringBuilder doublePinyin = new StringBuilder();
                for (int end = start; end < name.length() && Pinyin.isChinese(name.charAt(end)); end++) {
                    String syllable = Pinyin.toPinyin(name.charAt(end))
                        .toLowerCase(Locale.ROOT);
                    pinyin.append(syllable);
                    doublePinyin.append(GameDictionaryGenerator.flypyCode(syllable));
                }
                full.add(pinyin.toString());
                flypy.add(doublePinyin.toString());
            }
        }

        private boolean matches(String term, boolean doublePinyin) {
            if (literal.contains(term)) {
                return true;
            }
            for (String suffix : doublePinyin ? flypy : full) {
                if (suffix.startsWith(term)) {
                    return true;
                }
            }
            return false;
        }
    }
}
