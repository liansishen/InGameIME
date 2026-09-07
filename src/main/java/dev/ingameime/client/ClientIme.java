package dev.ingameime.client;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import dev.ingameime.Config;
import dev.ingameime.InGameIME;
import dev.ingameime.client.dictionary.GameDictionaryController;
import dev.ingameime.client.dictionary.ItemNameIndex;
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
    private final ItemSearchSession itemSearch = new ItemSearchSession();
    private List<String> itemMatches = new ArrayList<>();
    private int itemSelection = -1;
    private String lastItemQuery = "";
    private ItemNameIndex lastIndex = ItemNameIndex.EMPTY;

    private final Supplier<ItemNameIndex> itemIndex;

    private ClientIme() {
        this(
            () -> GameDictionaryController.getInstance()
                .getIndex());
    }

    ClientIme(Supplier<ItemNameIndex> itemIndex) {
        this.itemIndex = itemIndex;
    }

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
        return handleKeyboardInput(target, KeyMapper.current(itemSearch.isActive() || snapshot.isComposing()));
    }

    synchronized boolean handleKeyboardInput(InputTarget target, KeyMapper.KeyStroke key) {
        if (state != State.ACTIVE || key == null) {
            return false;
        }

        try {
            if (!asciiMode && supportedSearch()
                && (itemSearch.isActive() || snapshot.isComposing())
                && (key.modifiers & (KeyMapper.CONTROL_MASK | KeyMapper.ALT_MASK)) == 0
                && (key.keysym == '[' || key.keysym == ']')) {
                key = new KeyMapper.KeyStroke(key.keysym == '[' ? 0xff52 : 0xff54, key.modifiers);
            }
            if (handleItemSearch(target, key)) {
                return true;
            }
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
            if (itemSearch.isActive()) {
                clearComposition();
            }
            applyResult(target, backend.changeAsciiMode(!asciiMode, true), true);
            InGameIME.LOG.info("InGameIME input mode switched to {}", asciiMode ? "English" : "Chinese");
        } catch (Throwable failure) {
            disable("librime mode switch failed: " + failureMessage(failure), unwrap(failure));
        }
    }

    public synchronized void clearComposition() {
        resetItemSearch();
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

    private void resetItemSearch() {
        itemSearch.reset();
        itemMatches = new ArrayList<>();
        itemSelection = -1;
        lastItemQuery = "";
    }

    private boolean flypy() {
        return "double_pinyin_flypy".equals(activeSchemaId);
    }

    private boolean supportedSearch() {
        return flypy() || "rime_ice".equals(activeSchemaId);
    }

    private void updateItemMatches() {
        if (asciiMode || !supportedSearch() || !snapshot.isComposing()) {
            resetItemSearch();
            return;
        }
        ItemNameIndex index = itemIndex.get();
        String raw = backend.getRawInput();
        if (!raw.equals(lastItemQuery) || index != lastIndex) {
            itemMatches = index.search(raw, flypy());
            for (RimeSnapshot.Candidate candidate : snapshot.getCandidates()) {
                itemMatches.remove(candidate.getText());
            }
            itemSelection = -1;
            lastItemQuery = raw;
            lastIndex = index;
        }
    }

    private boolean handleItemSearch(InputTarget target, KeyMapper.KeyStroke key) throws Exception {
        if (asciiMode || !supportedSearch()) {
            return false;
        }
        int symbol = key.keysym;
        boolean shortcut = (key.modifiers & (KeyMapper.CONTROL_MASK | KeyMapper.ALT_MASK)) != 0;
        if (itemSearch.isActive()) {
            if (symbol == 0xff1b) {
                clearComposition();
            } else if (!shortcut && (symbol == ' ' || symbol == 0xff0d)) {
                String selected = itemSearch.selection();
                if (selected != null) {
                    target.insertText(selected);
                    clearComposition();
                }
            } else if (!shortcut) {
                itemSearch.edit(symbol, itemIndex.get(), flypy());
                if (itemSearch.shouldResumeRime()) {
                    String raw = itemSearch.query();
                    itemSearch.reset();
                    for (int i = 0; i < raw.length(); i++) {
                        applyResult(target, backend.processKey(raw.charAt(i), 0), false);
                    }
                    if (raw.isEmpty()) {
                        clearComposition();
                    }
                }
            }
            return true;
        }
        if (shortcut) {
            return false;
        }
        String raw = backend.getRawInput();
        if ((symbol == ':' && raw.isEmpty()) || (symbol == '+' && !raw.isEmpty())) {
            boolean direct = symbol == ':';
            backend.clearComposition();
            itemSearch.begin(direct ? "" : raw + "+", direct, itemIndex.get(), flypy());
            snapshot = RimeSnapshot.EMPTY;
            return true;
        }
        if (!itemMatches.isEmpty()) {
            if (symbol == 0xff54
                && (itemSelection >= 0 || snapshot.getHighlightedCandidate() >= snapshot.getCandidates()
                    .size() - 1)) {
                itemSelection = Math.min(itemMatches.size() - 1, itemSelection + 1);
                return true;
            }
            if (symbol == 0xff52 && itemSelection >= 0) {
                itemSelection--;
                return true;
            }
            if (symbol == ' ' && itemSelection >= 0) {
                target.insertText(itemMatches.get(itemSelection));
                clearComposition();
                return true;
            }
        }
        itemSelection = -1;
        return false;
    }

    public synchronized RimeSnapshot getSnapshot() {
        if (itemSearch.isActive()) {
            return itemSearch.snapshot();
        }
        updateItemMatches();
        if (itemMatches.isEmpty()) {
            return snapshot;
        }
        List<RimeSnapshot.Candidate> candidates = new ArrayList<>(snapshot.getCandidates());
        int nativeCount = candidates.size();
        int start = itemSelection < 0 ? 0 : itemSelection / 5 * 5;
        for (int i = start; i < Math.min(start + 5, itemMatches.size()); i++) {
            String text = itemMatches.get(i);
            candidates.add(new RimeSnapshot.Candidate("", text, ""));
        }
        return new RimeSnapshot(
            snapshot.getPreedit(),
            snapshot.getCursorPosition(),
            snapshot.isComposing(),
            candidates,
            itemSelection < 0 ? snapshot.getHighlightedCandidate() : nativeCount + itemSelection - start);
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
        if (itemSearch.isActive()) {
            String key = itemIndex.get()
                .size() == 0 ? "ingameime.search.build_first"
                    : itemSearch.selection() == null ? "ingameime.search.no_matches" : "ingameime.search.active";
            return net.minecraft.util.StatCollector.translateToLocal(key);
        }
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
        updateItemMatches();
    }

    private void showModeNotice() {
        modeNoticeUntil = System.currentTimeMillis() + Config.modeNoticeMillis;
    }

    private synchronized void shutdown() {
        RimeBackend current = backend;
        resetItemSearch();
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
        resetItemSearch();
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
