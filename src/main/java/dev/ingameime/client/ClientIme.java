package dev.ingameime.client;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import dev.ingameime.Config;
import dev.ingameime.InGameIME;
import dev.ingameime.rime.RimeBackend;
import dev.ingameime.rime.RimeKeyResult;
import dev.ingameime.rime.RimePatchInstaller;
import dev.ingameime.rime.RimeRuntimeConfig;
import dev.ingameime.rime.RimeSnapshot;

public final class ClientIme {

    private static final ClientIme INSTANCE = new ClientIme();
    private static final long SCHEMA_NOTICE_MILLIS = 3000L;

    private State state = State.UNINITIALIZED;
    private RimeBackend backend;
    private RimeSnapshot snapshot = RimeSnapshot.EMPTY;
    private String activeSchemaId = "";
    private String activeSchemaName = "";
    private boolean asciiMode;
    private long schemaNoticeUntil;
    private long modeNoticeUntil;
    private String disableReason = "";
    private boolean disableReasonLogged;
    private Object activeScreen;
    private InputTarget activeTarget;
    private boolean inputModeInitialized;

    private ClientIme() {}

    public static ClientIme getInstance() {
        return INSTANCE;
    }

    public synchronized void start() {
        if (state != State.UNINITIALIZED) {
            return;
        }
        if (!Config.enabled) {
            disable("disabled by configuration", null);
            return;
        }

        try {
            RimeRuntimeConfig runtimeConfig = RimeRuntimeConfig.resolve();
            try {
                RimePatchInstaller.install(runtimeConfig.getUserDataDirectory());
            } catch (IOException failure) {
                InGameIME.LOG.warn("Could not install the InGameIME game dictionary Rime patch", failure);
            }
            ClassLoader loader = ClientIme.class.getClassLoader();
            Class.forName("com.sun.jna.Native", false, loader);
            Class<? extends RimeBackend> backendClass = Class
                .forName("dev.ingameime.rime.jna.JnaRimeBackend", true, loader)
                .asSubclass(RimeBackend.class);
            RimeBackend initializedBackend = backendClass.getConstructor(RimeRuntimeConfig.class)
                .newInstance(runtimeConfig);
            backend = initializedBackend;
            activeSchemaId = initializedBackend.getSchemaId();
            activeSchemaName = initializedBackend.getSchemaName();
            asciiMode = initializedBackend.isAsciiMode();
            disableReason = "";
            state = State.ACTIVE;
            InGameIME.LOG.info("InGameIME active with {}", initializedBackend.getDescription());
            Runtime.getRuntime()
                .addShutdownHook(new Thread(this::shutdown, "InGameIME shutdown"));
        } catch (Throwable failure) {
            disable(failureMessage(failure), unwrap(failure));
        }
    }

    public synchronized boolean handleKeyboardInput(InputTarget target) {
        if (state != State.ACTIVE) {
            return false;
        }
        return handleKeyboardInput(target, KeyMapper.current(snapshot.isComposing()));
    }

    synchronized boolean handleKeyboardInput(InputTarget target, KeyMapper.KeyStroke key) {
        if (state != State.ACTIVE || key == null) {
            return false;
        }

        try {
            RimeKeyResult result = backend.processKey(key.keysym, key.modifiers);
            applyResult(target, result, false);
            return result.isConsumed();
        } catch (Throwable failure) {
            disable("librime input failed: " + failureMessage(failure), unwrap(failure));
            return false;
        }
    }

    public synchronized void onGuiOpened(Object screen) {
        clearComposition();
        activeScreen = screen;
        activeTarget = null;
        inputModeInitialized = false;
    }

    public synchronized void updateInputTarget(Object screen, InputTarget target) {
        if (screen != activeScreen) {
            clearComposition();
            activeScreen = screen;
            activeTarget = null;
            inputModeInitialized = false;
        }

        boolean sameTarget = activeTarget == null ? target == null : activeTarget.isSameTarget(target);
        if (sameTarget) {
            return;
        }
        if (activeTarget != null) {
            clearComposition();
        }
        activeTarget = target;
        InGameIME.LOG.debug(
            "Input target: screen={}, target={}",
            screen == null ? "none"
                : screen.getClass()
                    .getName(),
            target == null ? "none"
                : target.getClass()
                    .getSimpleName());
        if (state != State.ACTIVE || target == null) {
            return;
        }

        try {
            if (!inputModeInitialized) {
                inputModeInitialized = true;
                if (Config.OPEN_MODE_CHINESE.equals(Config.openInputMode) && asciiMode) {
                    applyResult(target, backend.changeAsciiMode(false, false), true);
                } else if (Config.OPEN_MODE_ENGLISH.equals(Config.openInputMode) && !asciiMode) {
                    applyResult(target, backend.changeAsciiMode(true, false), true);
                } else {
                    showModeNotice();
                }
            } else {
                showModeNotice();
            }
        } catch (Throwable failure) {
            disable("librime mode setup failed: " + failureMessage(failure), unwrap(failure));
        }
    }

    public synchronized void switchInputMode(InputTarget target) {
        if (state != State.ACTIVE) {
            return;
        }
        try {
            applyResult(target, backend.changeAsciiMode(!asciiMode, true), true);
            InGameIME.LOG.info("InGameIME input mode switched to {}", asciiMode ? "English" : "Chinese");
        } catch (Throwable failure) {
            disable("librime mode switch failed: " + failureMessage(failure), unwrap(failure));
        }
    }

    public synchronized void clearComposition() {
        schemaNoticeUntil = 0;
        modeNoticeUntil = 0;
        if (state != State.ACTIVE || !snapshot.isVisible()) {
            snapshot = RimeSnapshot.EMPTY;
            return;
        }
        try {
            snapshot = backend.clearComposition();
        } catch (Throwable failure) {
            disable("librime composition reset failed: " + failureMessage(failure), unwrap(failure));
        }
    }

    public synchronized boolean reloadSchema() {
        if (state != State.ACTIVE) {
            return false;
        }
        try {
            applyResult(activeTarget, backend.reloadSchema(), false);
            InGameIME.LOG.info("Reloaded Rime schema {} after game dictionary generation", activeSchemaId);
            return true;
        } catch (Throwable failure) {
            disable("librime schema reload failed: " + failureMessage(failure), unwrap(failure));
            return false;
        }
    }

    public synchronized RimeSnapshot getSnapshot() {
        return snapshot;
    }

    public synchronized boolean isActive() {
        return state == State.ACTIVE;
    }

    public synchronized boolean isAsciiMode() {
        return asciiMode;
    }

    public synchronized String getStateName() {
        return state.name();
    }

    public synchronized String getDisableReason() {
        return disableReason;
    }

    public synchronized String getActiveSchemaId() {
        return activeSchemaId;
    }

    public synchronized String getActiveSchemaName() {
        return activeSchemaName;
    }

    public synchronized String getBackendDescription() {
        return backend == null ? "" : backend.getDescription();
    }

    public synchronized String getSchemaNotice() {
        return Config.showSchemaNotice && state == State.ACTIVE && System.currentTimeMillis() < schemaNoticeUntil
            ? activeSchemaName
            : "";
    }

    public synchronized String getModeNotice() {
        return Config.showModeIndicator && state == State.ACTIVE && System.currentTimeMillis() < modeNoticeUntil
            ? asciiMode ? "EN" : "中"
            : "";
    }

    private void applyResult(InputTarget target, RimeKeyResult result, boolean forceModeNotice) throws Exception {
        snapshot = result.getSnapshot();
        updateSchema(result);
        boolean modeChanged = asciiMode != result.isAsciiMode();
        asciiMode = result.isAsciiMode();
        if (modeChanged || forceModeNotice) {
            showModeNotice();
        }
        if (!result.getCommitText()
            .isEmpty()) {
            target.insertText(result.getCommitText());
        }
    }

    private void showModeNotice() {
        modeNoticeUntil = System.currentTimeMillis() + Config.modeNoticeMillis;
    }

    private synchronized void shutdown() {
        RimeBackend current = backend;
        backend = null;
        snapshot = RimeSnapshot.EMPTY;
        activeSchemaId = "";
        activeSchemaName = "";
        asciiMode = false;
        schemaNoticeUntil = 0;
        modeNoticeUntil = 0;
        activeScreen = null;
        activeTarget = null;
        inputModeInitialized = false;
        state = State.DISABLED;
        if (current != null) {
            try {
                current.close();
            } catch (Throwable ignored) {
                // The process is already terminating.
            }
        }
    }

    private void disable(String reason, Throwable failure) {
        RimeBackend current = backend;
        backend = null;
        snapshot = RimeSnapshot.EMPTY;
        activeSchemaId = "";
        activeSchemaName = "";
        asciiMode = false;
        schemaNoticeUntil = 0;
        modeNoticeUntil = 0;
        activeScreen = null;
        activeTarget = null;
        inputModeInitialized = false;
        disableReason = reason;
        state = State.DISABLED;
        if (current != null) {
            try {
                current.close();
            } catch (Throwable ignored) {
                // Keep the original disable reason.
            }
        }
        if (!disableReasonLogged) {
            disableReasonLogged = true;
            if (failure == null) {
                InGameIME.LOG.warn("InGameIME disabled: {}", reason);
            } else {
                InGameIME.LOG.warn("InGameIME disabled: " + reason, failure);
            }
        }
    }

    private void updateSchema(RimeKeyResult result) {
        if (!activeSchemaId.equals(result.getSchemaId())) {
            Config.saveSchemaId(result.getSchemaId());
            schemaNoticeUntil = System.currentTimeMillis() + SCHEMA_NOTICE_MILLIS;
            InGameIME.LOG.info("InGameIME switched schema to {} ({})", result.getSchemaName(), result.getSchemaId());
        }
        activeSchemaId = result.getSchemaId();
        activeSchemaName = result.getSchemaName();
    }

    private static String failureMessage(Throwable failure) {
        Throwable cause = unwrap(failure);
        String message = cause.getMessage();
        return message == null || message.trim()
            .isEmpty() ? cause.getClass()
                .getSimpleName() : message;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current instanceof InvocationTargetException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private enum State {
        UNINITIALIZED,
        ACTIVE,
        DISABLED
    }
}
