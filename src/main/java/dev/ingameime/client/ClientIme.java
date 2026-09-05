package dev.ingameime.client;

import java.lang.reflect.InvocationTargetException;

import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;

import cpw.mods.fml.common.ObfuscationReflectionHelper;
import dev.ingameime.Config;
import dev.ingameime.InGameIME;
import dev.ingameime.rime.RimeBackend;
import dev.ingameime.rime.RimeKeyResult;
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
    private long schemaNoticeUntil;
    private boolean disableReasonLogged;

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
            state = State.ACTIVE;
            InGameIME.LOG.info("InGameIME active with {}", initializedBackend.getDescription());
            Runtime.getRuntime()
                .addShutdownHook(new Thread(this::shutdown, "InGameIME shutdown"));
        } catch (Throwable failure) {
            disable(failureMessage(failure), unwrap(failure));
        }
    }

    public synchronized boolean handleKeyboardInput(GuiChat chat) {
        if (state != State.ACTIVE) {
            return false;
        }

        KeyMapper.KeyStroke key = KeyMapper.current(snapshot.isComposing());
        if (key == null) {
            return false;
        }

        try {
            RimeKeyResult result = backend.processKey(key.keysym, key.modifiers);
            snapshot = result.getSnapshot();
            updateSchema(result);
            if (!result.getCommitText()
                .isEmpty()) {
                inputField(chat).writeText(result.getCommitText());
            }
            return result.isConsumed();
        } catch (Throwable failure) {
            disable("librime input failed: " + failureMessage(failure), unwrap(failure));
            return false;
        }
    }

    public synchronized void clearComposition() {
        schemaNoticeUntil = 0;
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

    public synchronized RimeSnapshot getSnapshot() {
        return snapshot;
    }

    public synchronized boolean isActive() {
        return state == State.ACTIVE;
    }

    public synchronized String getSchemaNotice() {
        return state == State.ACTIVE && System.currentTimeMillis() < schemaNoticeUntil ? activeSchemaName : "";
    }

    private static GuiTextField inputField(GuiChat chat) {
        return ObfuscationReflectionHelper.getPrivateValue(GuiChat.class, chat, "inputField", "field_146415_a");
    }

    private synchronized void shutdown() {
        RimeBackend current = backend;
        backend = null;
        snapshot = RimeSnapshot.EMPTY;
        activeSchemaId = "";
        activeSchemaName = "";
        schemaNoticeUntil = 0;
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
        schemaNoticeUntil = 0;
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
