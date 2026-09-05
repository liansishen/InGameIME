package dev.ingameime.rime;

public interface RimeBackend {

    RimeKeyResult processKey(int keycode, int modifiers);

    RimeSnapshot clearComposition();

    String getDescription();

    String getSchemaId();

    String getSchemaName();

    void close();
}
