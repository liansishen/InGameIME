package dev.ingameime.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.ingameime.client.dictionary.ItemNameIndex;
import dev.ingameime.rime.RimeSnapshot;

final class ItemSearchSession {

    private String query;
    private boolean direct;
    private int cursor;
    private int selected;
    private List<String> matches = Collections.emptyList();

    boolean isActive() {
        return query != null;
    }

    void begin(String query, boolean direct, ItemNameIndex index, boolean flypy) {
        this.query = query;
        this.direct = direct;
        cursor = query.length();
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
        matches = Collections.emptyList();
        selected = 0;
    }

    String selection() {
        return matches.isEmpty() ? null : matches.get(selected);
    }

    void edit(int key, ItemNameIndex index, boolean flypy) {
        switch (key) {
            case 0xff52:
                selected = Math.max(0, selected - 1);
                return;
            case 0xff54:
                selected = Math.min(Math.max(0, matches.size() - 1), selected + 1);
                return;
            case 0xff55:
                selected = Math.max(0, selected - 5);
                return;
            case 0xff56:
                selected = Math.min(Math.max(0, matches.size() - 1), selected + 5);
                return;
            case 0xff51:
                cursor = Math.max(0, cursor - 1);
                return;
            case 0xff53:
                cursor = Math.min(query.length(), cursor + 1);
                return;
            case 0xff50:
                cursor = 0;
                return;
            case 0xff57:
                cursor = query.length();
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
        String previous = selection();
        matches = index.search(query, flypy);
        selected = Math.max(0, matches.indexOf(previous));
    }

    RimeSnapshot snapshot() {
        List<RimeSnapshot.Candidate> candidates = new ArrayList<>();
        int start = selected / 5 * 5;
        for (int i = start; i < Math.min(start + 5, matches.size()); i++) {
            candidates.add(new RimeSnapshot.Candidate("", matches.get(i), ""));
        }
        String prefix = direct ? ":" : "";
        return new RimeSnapshot(
            prefix + query,
            cursor + prefix.length(),
            true,
            candidates,
            matches.isEmpty() ? -1 : selected - start);
    }
}
