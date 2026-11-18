package dev.ingameime.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.ingameime.client.dictionary.ItemNameIndex;
import dev.ingameime.rime.RimeSnapshot;

final class ItemSearchSession {

    private static final int SEARCH_LIMIT = 200;

    private String query;
    private boolean direct;
    private int cursor;
    private int selected;
    private ItemNameIndex lastIndex;
    private boolean lastFlypy;
    private List<String> matches = Collections.emptyList();
    private RimeSnapshot cachedSnapshot = RimeSnapshot.EMPTY;

    boolean isActive() {
        return query != null;
    }

    void begin(String query, boolean direct, ItemNameIndex index, boolean flypy) {
        this.query = query;
        this.direct = direct;
        cursor = query.length();
        matches = Collections.emptyList();
        selected = 0;
        refresh(index, flypy);
    }

    String query() {
        return query;
    }

    boolean shouldResumeRime() {
        return !direct && query.indexOf('+') < 0;
    }

    void reset() {
        query = null;
        lastIndex = null;
        lastFlypy = false;
        matches = Collections.emptyList();
        selected = 0;
        cachedSnapshot = RimeSnapshot.EMPTY;
    }

    void sync(ItemNameIndex index, boolean flypy) {
        if (!isActive()) {
            return;
        }
        ItemNameIndex resolved = normalizeIndex(index);
        if (resolved != lastIndex || flypy != lastFlypy) {
            refresh(resolved, flypy);
        }
    }

    String selection() {
        return matches.isEmpty() ? null : matches.get(selected);
    }

    void edit(int key, ItemNameIndex index, boolean flypy) {
        switch (key) {
            case 0xff52:
                sync(index, flypy);
                selected = Math.max(0, selected - 1);
                rebuildSnapshot();
                return;
            case 0xff54:
                sync(index, flypy);
                selected = Math.min(Math.max(0, matches.size() - 1), selected + 1);
                rebuildSnapshot();
                return;
            case 0xff55:
                sync(index, flypy);
                selected = Math.max(0, selected - 5);
                rebuildSnapshot();
                return;
            case 0xff56:
                sync(index, flypy);
                selected = Math.min(Math.max(0, matches.size() - 1), selected + 5);
                rebuildSnapshot();
                return;
            case 0xff51:
                sync(index, flypy);
                cursor = Math.max(0, cursor - 1);
                rebuildSnapshot();
                return;
            case 0xff53:
                sync(index, flypy);
                cursor = Math.min(query.length(), cursor + 1);
                rebuildSnapshot();
                return;
            case 0xff50:
                sync(index, flypy);
                cursor = 0;
                rebuildSnapshot();
                return;
            case 0xff57:
                sync(index, flypy);
                cursor = query.length();
                rebuildSnapshot();
                return;
            case 0xff08:
                if (cursor > 0) {
                    query = query.substring(0, cursor - 1) + query.substring(cursor--);
                }
                break;
            case 0xffff:
                if (cursor < query.length()) {
                    query = query.substring(0, cursor) + query.substring(cursor + 1);
                }
                break;
            default:
                if (key >= 33 && key <= 126 && query.length() < 256) {
                    query = query.substring(0, cursor) + (char) key + query.substring(cursor);
                    cursor++;
                }
                break;
        }
        refresh(index, flypy);
    }

    private void refresh(ItemNameIndex index, boolean flypy) {
        ItemNameIndex resolved = normalizeIndex(index);
        String previous = selection();
        lastIndex = resolved;
        lastFlypy = flypy;
        matches = resolved.search(query, flypy, SEARCH_LIMIT);
        selected = Math.max(0, matches.indexOf(previous));
        rebuildSnapshot();
    }

    private static ItemNameIndex normalizeIndex(ItemNameIndex index) {
        return index == null ? ItemNameIndex.EMPTY : index;
    }

    private void rebuildSnapshot() {
        if (query == null) {
            cachedSnapshot = RimeSnapshot.EMPTY;
            return;
        }
        int start = selected / 5 * 5;
        int end = Math.min(start + 5, matches.size());
        List<RimeSnapshot.Candidate> candidates = end > start ? new ArrayList<>(end - start) : Collections.emptyList();
        for (int i = start; i < end; i++) {
            candidates.add(new RimeSnapshot.Candidate("", matches.get(i), ""));
        }
        String prefix = direct ? ":" : "";
        cachedSnapshot = new RimeSnapshot(
            prefix + query,
            cursor + prefix.length(),
            true,
            candidates,
            matches.isEmpty() ? -1 : selected - start);
    }

    RimeSnapshot snapshot() {
        return cachedSnapshot;
    }
}
