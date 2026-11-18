package dev.ingameime.client;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;

import dev.ingameime.Config;
import dev.ingameime.InGameIME;
import dev.ingameime.asm.GuiScreenTransformer;
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
    private static final long SHUTDOWN_JOIN_MILLIS = 5000L;
    private static final int ITEM_CANDIDATE_LIMIT = 64;

    private volatile State state = State.UNINITIALIZED;
    private RimeBackend backend;
    private Thread initThread;
    private boolean shutdownHookRegistered;
    private boolean shutdownRequested;
    private boolean pendingSchemaReload;
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
    private List<String> itemMatches = Collections.emptyList();
    private int itemSelection = -1;
    private String lastItemQuery = "";
    private ItemNameIndex lastIndex = ItemNameIndex.EMPTY;
    private RimeSnapshot lastMatchedSnapshot = RimeSnapshot.EMPTY;
    private RimeSnapshot cachedBaseSnapshot = RimeSnapshot.EMPTY;
    private List<String> cachedItemMatches = Collections.emptyList();
    private int cachedItemSelection = -1;
    private RimeSnapshot cachedViewSnapshot = RimeSnapshot.EMPTY;

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

    public void start() {
        File gameDirectory;
        synchronized (this) {
            if (state != State.UNINITIALIZED) {
                return;
            }
            if (!Config.enabled) {
                disable("disabled by configuration", null);
                return;
            }
            if (GuiScreenTransformer.hasInjectionFailed()) {
                disable("GuiScreen keyboard hook injection failed", null);
                return;
            }
            gameDirectory = Minecraft.getMinecraft().mcDataDir;
        }
        startAsync(gameDirectory, ClientIme::createJnaBackend, true);
    }

    void startAsync(File gameDirectory, BackendFactory backendFactory, boolean registerShutdownHook) {
        Thread initializer;
        synchronized (this) {
            if (state != State.UNINITIALIZED) {
                return;
            }
            if (!Config.enabled) {
                disable("disabled by configuration", null);
                return;
            }
            if (GuiScreenTransformer.hasInjectionFailed()) {
                disable("GuiScreen keyboard hook injection failed", null);
                return;
            }
            shutdownRequested = false;
            pendingSchemaReload = false;
            state = State.INITIALIZING;
            if (registerShutdownHook && !shutdownHookRegistered) {
                shutdownHookRegistered = true;
                Runtime.getRuntime()
                    .addShutdownHook(new Thread(this::shutdown, "InGameIME shutdown"));
            }
            initializer = new Thread(
                () -> initializeInBackground(gameDirectory, backendFactory),
                "InGameIME initializer");
            initializer.setDaemon(true);
            initThread = initializer;
        }
        initializer.start();
    }

    private void initializeInBackground(File gameDirectory, BackendFactory backendFactory) {
        RimeBackend initializedBackend = null;
        try {
            RimeRuntimeConfig runtimeConfig = RimeRuntimeConfig.resolve(gameDirectory);
            try {
                RimePatchInstaller.install(runtimeConfig.getUserDataDirectory());
            } catch (IOException failure) {
                InGameIME.LOG.warn("Could not install the InGameIME game dictionary Rime patch", failure);
            }
            initializedBackend = backendFactory.create(runtimeConfig);

            boolean closeImmediately = false;
            synchronized (this) {
                if (shutdownRequested || state != State.INITIALIZING) {
                    closeImmediately = true;
                } else {
                    backend = initializedBackend;
                    activeSchemaId = initializedBackend.getSchemaId();
                    activeSchemaName = initializedBackend.getSchemaName();
                    asciiMode = initializedBackend.isAsciiMode();
                    disableReason = "";
                    state = State.ACTIVE;
                    InGameIME.LOG.info("InGameIME active with {}", initializedBackend.getDescription());
                    if (pendingSchemaReload) {
                        pendingSchemaReload = false;
                        reloadSchema();
                    }
                }
            }
            if (closeImmediately) {
                closeQuietly(initializedBackend);
            }
        } catch (Throwable failure) {
            if (initializedBackend != null) {
                closeQuietly(initializedBackend);
            }
            synchronized (this) {
                if (!shutdownRequested && state == State.INITIALIZING) {
                    disable(failureMessage(failure), unwrap(failure));
                }
            }
        } finally {
            synchronized (this) {
                if (initThread == Thread.currentThread()) {
                    initThread = null;
                }
            }
        }
    }

    private static RimeBackend createJnaBackend(RimeRuntimeConfig runtimeConfig) throws Exception {
        ClassLoader loader = ClientIme.class.getClassLoader();
        Class.forName("com.sun.jna.Native", false, loader);
        Class<? extends RimeBackend> backendClass = Class.forName("dev.ingameime.rime.jna.JnaRimeBackend", true, loader)
            .asSubclass(RimeBackend.class);
        return backendClass.getConstructor(RimeRuntimeConfig.class)
            .newInstance(runtimeConfig);
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
        if (state == State.INITIALIZING) {
            pendingSchemaReload = true;
            return true;
        }
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
        clearInlineItemMatches();
    }

    private void clearInlineItemMatches() {
        itemMatches = Collections.emptyList();
        itemSelection = -1;
        lastItemQuery = "";
        lastIndex = ItemNameIndex.EMPTY;
        lastMatchedSnapshot = RimeSnapshot.EMPTY;
        cachedBaseSnapshot = RimeSnapshot.EMPTY;
        cachedItemMatches = Collections.emptyList();
        cachedItemSelection = -1;
        cachedViewSnapshot = RimeSnapshot.EMPTY;
    }

    private boolean flypy() {
        return "double_pinyin_flypy".equals(activeSchemaId);
    }

    private boolean supportedSearch() {
        return flypy() || "rime_ice".equals(activeSchemaId);
    }

    private ItemNameIndex currentIndex() {
        ItemNameIndex index = itemIndex.get();
        return index == null ? ItemNameIndex.EMPTY : index;
    }

    private void syncItemSearch() {
        itemSearch.sync(currentIndex(), flypy());
    }

    private void updateItemMatches() {
        if (asciiMode || !supportedSearch()) {
            if (itemSearch.isActive() || !itemMatches.isEmpty()
                || !lastItemQuery.isEmpty()
                || lastIndex != ItemNameIndex.EMPTY) {
                resetItemSearch();
            }
            return;
        }
        if (itemSearch.isActive()) {
            clearInlineItemMatches();
            syncItemSearch();
            return;
        }
        if (!snapshot.isComposing()) {
            if (!itemMatches.isEmpty() || !lastItemQuery.isEmpty() || lastIndex != ItemNameIndex.EMPTY) {
                clearInlineItemMatches();
            }
            return;
        }
        ItemNameIndex index = currentIndex();
        if (snapshot == lastMatchedSnapshot && index == lastIndex) {
            return;
        }
        lastMatchedSnapshot = snapshot;
        String raw = backend.getRawInput();
        if (!raw.equals(lastItemQuery) || index != lastIndex) {
            List<RimeSnapshot.Candidate> nativeCandidates = snapshot.getCandidates();
            List<String> matches = index.search(raw, flypy(), ITEM_CANDIDATE_LIMIT + nativeCandidates.size());
            if (!matches.isEmpty() && !nativeCandidates.isEmpty()) {
                Set<String> nativeTexts = new HashSet<>(nativeCandidates.size() * 2);
                for (int i = 0; i < nativeCandidates.size(); i++) {
                    nativeTexts.add(
                        nativeCandidates.get(i)
                            .getText());
                }
                matches.removeIf(nativeTexts::contains);
            }
            itemMatches = matches.isEmpty() ? Collections.emptyList() : matches;
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
                syncItemSearch();
                String selected = itemSearch.selection();
                if (selected != null) {
                    target.insertText(selected);
                    clearComposition();
                }
            } else if (!shortcut) {
                itemSearch.edit(symbol, currentIndex(), flypy());
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
            clearInlineItemMatches();
            itemSearch.begin(direct ? "" : raw + "+", direct, currentIndex(), flypy());
            snapshot = RimeSnapshot.EMPTY;
            return true;
        }
        updateItemMatches();
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
            syncItemSearch();
            return itemSearch.snapshot();
        }
        updateItemMatches();
        if (itemMatches.isEmpty()) {
            return snapshot;
        }
        if (cachedBaseSnapshot == snapshot && cachedItemMatches == itemMatches
            && cachedItemSelection == itemSelection) {
            return cachedViewSnapshot;
        }
        List<RimeSnapshot.Candidate> nativeCandidates = snapshot.getCandidates();
        int nativeCount = nativeCandidates.size();
        int start = itemSelection < 0 ? 0 : itemSelection / 5 * 5;
        int end = Math.min(start + 5, itemMatches.size());
        List<RimeSnapshot.Candidate> candidates = new ArrayList<>(nativeCount + (end - start));
        candidates.addAll(nativeCandidates);
        for (int i = start; i < end; i++) {
            candidates.add(new RimeSnapshot.Candidate("", itemMatches.get(i), ""));
        }
        cachedBaseSnapshot = snapshot;
        cachedItemMatches = itemMatches;
        cachedItemSelection = itemSelection;
        cachedViewSnapshot = new RimeSnapshot(
            snapshot.getPreedit(),
            snapshot.getCursorPosition(),
            snapshot.isComposing(),
            candidates,
            itemSelection < 0 ? snapshot.getHighlightedCandidate() : nativeCount + itemSelection - start);
        return cachedViewSnapshot;
    }

    public boolean isActive() {
        return state == State.ACTIVE;
    }

    public synchronized boolean isAsciiMode() {
        return asciiMode;
    }

    public String getStateName() {
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
            ItemNameIndex index = currentIndex();
            itemSearch.sync(index, flypy());
            String key = index.size() == 0 ? "ingameime.search.build_first"
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
            .isEmpty() && target != null) {
            target.insertText(result.getCommitText());
        }
        updateItemMatches();
    }

    private void showModeNotice() {
        modeNoticeUntil = System.currentTimeMillis() + Config.modeNoticeMillis;
    }

    void shutdown() {
        Thread pendingInit;
        synchronized (this) {
            shutdownRequested = true;
            pendingInit = initThread;
        }
        if (pendingInit != null && pendingInit != Thread.currentThread()) {
            try {
                pendingInit.join(SHUTDOWN_JOIN_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread()
                    .interrupt();
            }
        }
        RimeBackend current;
        synchronized (this) {
            current = backend;
            resetRuntimeLocked("");
            state = State.DISABLED;
        }
        if (current != null) {
            closeQuietly(current);
        }
    }

    private void disable(String reason, Throwable failure) {
        RimeBackend current = backend;
        resetRuntimeLocked(reason);
        state = State.DISABLED;
        if (current != null) {
            closeQuietly(current);
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

    private void resetRuntimeLocked(String reason) {
        resetItemSearch();
        backend = null;
        pendingSchemaReload = false;
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
    }

    private static void closeQuietly(RimeBackend current) {
        try {
            current.close();
        } catch (Throwable ignored) {
            // Keep original state/reason during cleanup.
        }
    }

    private void updateSchema(RimeKeyResult result) {
        if (!activeSchemaId.equals(result.getSchemaId())) {
            Config.saveSchemaIdAsync(result.getSchemaId());
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

    interface BackendFactory {

        RimeBackend create(RimeRuntimeConfig runtimeConfig) throws Exception;
    }

    private enum State {
        UNINITIALIZED,
        INITIALIZING,
        ACTIVE,
        DISABLED
    }
}
