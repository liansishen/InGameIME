package dev.ingameime.rime;

public interface RimeBackend {

    RimeKeyResult processKey(int keycode, int modifiers);

    RimeSnapshot clearComposition();

    RimeKeyResult changeAsciiMode(boolean asciiMode, boolean commitRawInput);

    RimeKeyResult reloadSchema();

    String getDescription();

    String getSchemaId();

    String getSchemaName();

    boolean isAsciiMode();

    void close();
}
