package dev.ingameime.client;

interface InputTarget {

    Object owner();

    int kind();

    InputBounds bounds();

    void insertText(String text) throws Exception;

    default boolean isSameTarget(InputTarget other) {
        return other != null && getClass() == other.getClass() && owner() == other.owner() && kind() == other.kind();
    }
}
