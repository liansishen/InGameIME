package dev.ingameime.rime;

import java.util.Collections;
import java.util.List;

public final class RimeSnapshot {

    public static final RimeSnapshot EMPTY = new RimeSnapshot("", 0, false, Collections.<Candidate>emptyList(), -1);

    private final String preedit;
    private final int cursorPosition;
    private final boolean composing;
    private final List<Candidate> candidates;
    private final int highlightedCandidate;

    public RimeSnapshot(String preedit, int cursorPosition, boolean composing, List<Candidate> candidates,
        int highlightedCandidate) {
        this.preedit = preedit;
        this.cursorPosition = cursorPosition;
        this.composing = composing;
        this.candidates = Collections.unmodifiableList(candidates);
        this.highlightedCandidate = highlightedCandidate;
    }

    public String getPreedit() {
        return preedit;
    }

    public int getCursorPosition() {
        return cursorPosition;
    }

    public boolean isComposing() {
        return composing;
    }

    public List<Candidate> getCandidates() {
        return candidates;
    }

    public int getHighlightedCandidate() {
        return highlightedCandidate;
    }

    public boolean isVisible() {
        return composing || !preedit.isEmpty() || !candidates.isEmpty();
    }

    public static final class Candidate {

        private final String label;
        private final String text;
        private final String comment;

        public Candidate(String label, String text, String comment) {
            this.label = label;
            this.text = text;
            this.comment = comment;
        }

        public String getLabel() {
            return label;
        }

        public String getText() {
            return text;
        }

        public String getComment() {
            return comment;
        }
    }
}
