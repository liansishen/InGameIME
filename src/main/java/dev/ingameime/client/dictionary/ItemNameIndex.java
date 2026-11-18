package dev.ingameime.client.dictionary;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import com.github.promeg.pinyinhelper.Pinyin;
import com.google.gson.Gson;

public final class ItemNameIndex {

    public static final ItemNameIndex EMPTY = new ItemNameIndex(Collections.emptyList());

    private static final Gson GSON = new Gson();
    private static final int ASCII_BUCKET_COUNT = 128;
    private static final int[] EMPTY_INDICES = new int[0];

    private final List<Entry> entries;
    private final int[][] fullBuckets;
    private final int[][] flypyBuckets;

    private ItemNameIndex(List<Entry> entries) {
        this.entries = entries;
        this.fullBuckets = buildBuckets(entries, false);
        this.flypyBuckets = buildBuckets(entries, true);
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
        return search(query, flypy, Integer.MAX_VALUE);
    }

    public List<String> search(String query, boolean flypy, int limit) {
        List<String> result = new ArrayList<>();
        if (limit <= 0 || entries.isEmpty() || query == null || query.isEmpty()) {
            return result;
        }
        String[] rawTerms = query.toLowerCase(Locale.ROOT)
            .split("\\+", -1);
        List<String> terms = new ArrayList<>(rawTerms.length);
        for (String term : rawTerms) {
            if (!term.isEmpty()) {
                terms.add(term);
            }
        }
        if (terms.isEmpty()) {
            return result;
        }

        int[][] buckets = flypy ? flypyBuckets : fullBuckets;
        int[] candidateIndices = null;
        for (int i = 0; i < terms.size(); i++) {
            char first = terms.get(i)
                .charAt(0);
            if (first < ASCII_BUCKET_COUNT) {
                int[] bucket = buckets[first];
                if (bucket.length == 0) {
                    return result;
                }
                if (candidateIndices == null || bucket.length < candidateIndices.length) {
                    candidateIndices = bucket;
                }
            }
        }

        if (candidateIndices != null) {
            for (int index : candidateIndices) {
                Entry entry = entries.get(index);
                if (matchesAll(entry, terms, flypy)) {
                    result.add(entry.name);
                    if (result.size() >= limit) {
                        break;
                    }
                }
            }
            return result;
        }

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (matchesAll(entry, terms, flypy)) {
                result.add(entry.name);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    private static boolean matchesAll(Entry entry, List<String> terms, boolean flypy) {
        for (int i = 0; i < terms.size(); i++) {
            if (!entry.matches(terms.get(i), flypy)) {
                return false;
            }
        }
        return true;
    }

    private static int[][] buildBuckets(List<Entry> entries, boolean flypy) {
        int[][] result = new int[ASCII_BUCKET_COUNT][];
        if (entries.isEmpty()) {
            Arrays.fill(result, EMPTY_INDICES);
            return result;
        }

        IntList[] builders = new IntList[ASCII_BUCKET_COUNT];
        int[] lastAddedEntry = new int[ASCII_BUCKET_COUNT];
        Arrays.fill(lastAddedEntry, -1);

        for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
            Entry entry = entries.get(entryIndex);
            String literal = entry.literal;
            for (int i = 0; i < literal.length(); i++) {
                char ch = literal.charAt(i);
                if (ch < ASCII_BUCKET_COUNT && lastAddedEntry[ch] != entryIndex) {
                    lastAddedEntry[ch] = entryIndex;
                    bucketBuilder(builders, ch).add(entryIndex);
                }
            }
            List<String> suffixes = flypy ? entry.flypy : entry.full;
            for (int i = 0; i < suffixes.size(); i++) {
                String suffix = suffixes.get(i);
                if (!suffix.isEmpty()) {
                    char ch = suffix.charAt(0);
                    if (ch < ASCII_BUCKET_COUNT && lastAddedEntry[ch] != entryIndex) {
                        lastAddedEntry[ch] = entryIndex;
                        bucketBuilder(builders, ch).add(entryIndex);
                    }
                }
            }
        }

        for (int ch = 0; ch < ASCII_BUCKET_COUNT; ch++) {
            result[ch] = builders[ch] == null ? EMPTY_INDICES : builders[ch].toArray();
        }
        return result;
    }

    private static IntList bucketBuilder(IntList[] builders, int index) {
        IntList list = builders[index];
        if (list == null) {
            list = new IntList();
            builders[index] = list;
        }
        return list;
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "item-index-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(entries, writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static ItemNameIndex load(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return EMPTY;
        }
        Entry[] values;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            values = GSON.fromJson(reader, Entry[].class);
        }
        if (values == null) {
            throw new IOException("Invalid item name index");
        }
        List<Entry> entries = new ArrayList<>(values.length);
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

    private static final class IntList {

        private int[] values = new int[16];
        private int size;

        void add(int value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = value;
        }

        int[] toArray() {
            return Arrays.copyOf(values, size);
        }
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
            List<String> suffixes = doublePinyin ? flypy : full;
            for (int i = 0; i < suffixes.size(); i++) {
                if (suffixes.get(i)
                    .startsWith(term)) {
                    return true;
                }
            }
            return false;
        }
    }
}
