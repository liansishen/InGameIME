package dev.ingameime.rime;

public final class RimeKeyResult {

    private final boolean consumed;
    private final String commitText;
    private final RimeSnapshot snapshot;
    private final String schemaId;
    private final String schemaName;
    private final boolean asciiMode;

    public RimeKeyResult(boolean consumed, String commitText, RimeSnapshot snapshot, String schemaId, String schemaName,
        boolean asciiMode) {
        this.consumed = consumed;
        this.commitText = commitText;
        this.snapshot = snapshot;
        this.schemaId = schemaId;
        this.schemaName = schemaName;
        this.asciiMode = asciiMode;
    }

    public boolean isConsumed() {
        return consumed;
    }

    public String getCommitText() {
        return commitText;
    }

    public RimeSnapshot getSnapshot() {
        return snapshot;
    }

    public String getSchemaId() {
        return schemaId;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public boolean isAsciiMode() {
        return asciiMode;
    }
}
