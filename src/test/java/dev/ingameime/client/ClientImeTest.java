package dev.ingameime.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.util.StatCollector;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import dev.ingameime.Config;
import dev.ingameime.client.dictionary.ItemNameIndex;
import dev.ingameime.rime.RimeBackend;
import dev.ingameime.rime.RimeKeyResult;
import dev.ingameime.rime.RimeSnapshot;

public class ClientImeTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private ClientIme ime;
    private FakeBackend backend;

    @Before
    public void createIme() throws Exception {
        Constructor<ClientIme> constructor = ClientIme.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        ime = constructor.newInstance();
        backend = new FakeBackend();
        setField("backend", backend);
    }

    @Test
    public void reloadSchemaUpdatesStateWithoutInsertingText() throws Exception {
        RimeSnapshot snapshot = new RimeSnapshot("", 0, false, Collections.<RimeSnapshot.Candidate>emptyList(), -1);
        backend.reloadResult = new RimeKeyResult(true, "", snapshot, "rime_ice", "Rime Ice", true);
        RecordingTarget target = new RecordingTarget();
        setField("activeTarget", target);
        setField("activeSchemaId", "rime_ice");
        setField("activeSchemaName", "Old name");
        setState("ACTIVE");

        assertTrue(ime.reloadSchema());

        assertEquals(1, backend.reloadCount);
        assertSame(snapshot, ime.getSnapshot());
        assertEquals("rime_ice", ime.getActiveSchemaId());
        assertEquals("Rime Ice", ime.getActiveSchemaName());
        assertTrue(ime.isAsciiMode());
        assertEquals("", target.insertedText);
    }

    @Test
    public void reloadSchemaReturnsFalseWhenImeIsInactive() {
        assertFalse(ime.reloadSchema());
        assertEquals(0, backend.reloadCount);
    }

    @Test
    public void reloadSchemaFailureDisablesImeAndClosesBackend() throws Exception {
        backend.reloadFailure = new IllegalStateException("reload rejected");
        setState("ACTIVE");

        assertFalse(ime.reloadSchema());

        assertEquals("DISABLED", ime.getStateName());
        assertTrue(
            ime.getDisableReason()
                .contains("reload rejected"));
        assertTrue(backend.closed);
    }

    @Test
    public void asyncStartPassesThroughInputWhileInitializingAndAppliesDeferredReload() throws Exception {
        File gameDir = createFakeRimeLayout("async-init");
        ClientIme asyncIme = new ClientIme(() -> ItemNameIndex.EMPTY);
        CountDownLatch enteredFactory = new CountDownLatch(1);
        CountDownLatch releaseFactory = new CountDownLatch(1);
        FakeBackend initializedBackend = new FakeBackend();
        initializedBackend.schemaId = "rime_ice";
        initializedBackend.schemaName = "Rime Ice";
        initializedBackend.reloadResult = new RimeKeyResult(
            true,
            "",
            RimeSnapshot.EMPTY,
            "rime_ice",
            "Rime Ice Reloaded",
            false);

        boolean previousEnabled = Config.enabled;
        Config.enabled = true;
        try {
            asyncIme.startAsync(gameDir, config -> {
                enteredFactory.countDown();
                assertTrue(releaseFactory.await(5, TimeUnit.SECONDS));
                return initializedBackend;
            }, false);

            assertTrue(enteredFactory.await(5, TimeUnit.SECONDS));
            assertEquals("INITIALIZING", asyncIme.getStateName());
            assertFalse(asyncIme.isActive());
            assertFalse(asyncIme.handleKeyboardInput(new RecordingTarget(), new KeyMapper.KeyStroke('a', 0)));
            assertTrue(asyncIme.reloadSchema());

            releaseFactory.countDown();
            long deadline = System.currentTimeMillis() + 5000L;
            while (!asyncIme.isActive() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10L);
            }

            assertTrue(asyncIme.isActive());
            assertEquals("ACTIVE", asyncIme.getStateName());
            assertEquals(1, initializedBackend.reloadCount);
            assertEquals("Rime Ice Reloaded", asyncIme.getActiveSchemaName());
            asyncIme.shutdown();
            assertTrue(initializedBackend.closed);
        } finally {
            Config.enabled = previousEnabled;
        }
    }

    @Test
    public void shutdownDuringAsyncInitializationClosesBackendSafely() throws Exception {
        File gameDir = createFakeRimeLayout("async-shutdown");
        ClientIme asyncIme = new ClientIme(() -> ItemNameIndex.EMPTY);
        CountDownLatch enteredFactory = new CountDownLatch(1);
        CountDownLatch releaseFactory = new CountDownLatch(1);
        FakeBackend initializedBackend = new FakeBackend();

        boolean previousEnabled = Config.enabled;
        Config.enabled = true;
        try {
            asyncIme.startAsync(gameDir, config -> {
                enteredFactory.countDown();
                assertTrue(releaseFactory.await(5, TimeUnit.SECONDS));
                return initializedBackend;
            }, false);

            assertTrue(enteredFactory.await(5, TimeUnit.SECONDS));
            Thread shutdownCaller = new Thread(asyncIme::shutdown, "test-shutdown");
            shutdownCaller.start();
            Thread.sleep(25L);
            releaseFactory.countDown();
            shutdownCaller.join(5000L);

            assertFalse(shutdownCaller.isAlive());
            assertEquals("DISABLED", asyncIme.getStateName());
            assertFalse(asyncIme.isActive());
            assertTrue(initializedBackend.closed);
        } finally {
            Config.enabled = previousEnabled;
        }
    }

    @Test
    public void cachesComposedViewSnapshotWithoutRepeatedRawInputReads() throws Exception {
        ItemNameIndex index = ItemNameIndex.build(Arrays.asList("16A 动力仓", "4A 动力仓"));
        ime = new ClientIme(() -> index);
        backend.rawInput = "dslich";
        RimeSnapshot rimeSnap = new RimeSnapshot(
            "dslich",
            6,
            true,
            Collections.singletonList(new RimeSnapshot.Candidate("1", "东里", "")),
            0);
        backend.processResults.add(new RimeKeyResult(true, "", rimeSnap, "double_pinyin_flypy", "小鹤双拼", false));
        setField("backend", backend);
        setField("activeSchemaId", "double_pinyin_flypy");
        setState("ACTIVE");

        assertTrue(ime.handleKeyboardInput(new RecordingTarget(), new KeyMapper.KeyStroke('d', 0)));
        int rawReadsAfterKey = backend.rawInputReads;

        RimeSnapshot firstView = ime.getSnapshot();
        RimeSnapshot secondView = ime.getSnapshot();
        assertSame(firstView, secondView);
        assertEquals(
            3,
            firstView.getCandidates()
                .size());
        assertEquals(rawReadsAfterKey, backend.rawInputReads);
    }

    @Test
    public void processesPhysicalCharactersOnceAndCommitsCandidateOnSpace() throws Exception {
        RimeKeyResult composing = new RimeKeyResult(true, "", RimeSnapshot.EMPTY, "", "", false);
        RimeKeyResult commit = new RimeKeyResult(true, "草", RimeSnapshot.EMPTY, "", "", false);
        backend.processResults.add(composing);
        backend.processResults.add(composing);
        backend.processResults.add(commit);
        RecordingTarget target = new RecordingTarget();
        setState("ACTIVE");

        assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke('c', 0)));
        assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke('c', 0)));
        assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(' ', 0)));

        assertEquals(Arrays.asList((int) 'c', (int) 'c', (int) ' '), backend.processedKeys);
        assertEquals("草", target.insertedText);
    }

    @Test
    public void directSearchConsumesDigitsAndSpaceWithoutCallingRime() throws Exception {
        ItemNameIndex index = ItemNameIndex.build(Arrays.asList("16A 动力仓", "4A 动力仓"));
        ime = new ClientIme(() -> index);
        setField("backend", backend);
        setField("activeSchemaId", "double_pinyin_flypy");
        setState("ACTIVE");
        RecordingTarget target = new RecordingTarget();
        for (char character : ":dslich+16a".toCharArray()) {
            assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(character, 0)));
        }
        assertTrue(
            ime.getSnapshot()
                .isComposing());
        assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(' ', 0)));
        assertEquals("16A 动力仓", target.insertedText);
        assertTrue(backend.processedKeys.isEmpty());
        assertFalse(
            ime.getSnapshot()
                .isVisible());
    }

    @Test
    public void searchCancelsOnGuiChangeAndEnglishColonStaysWithRime() throws Exception {
        ime = new ClientIme(() -> ItemNameIndex.EMPTY);
        setField("backend", backend);
        setField("activeSchemaId", "double_pinyin_flypy");
        setState("ACTIVE");
        RecordingTarget target = new RecordingTarget();
        assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(':', 0)));
        ime.onGuiOpened(new Object());
        assertFalse(
            ime.getSnapshot()
                .isVisible());
        setField("asciiMode", true);
        backend.processResults.add(new RimeKeyResult(false, "", RimeSnapshot.EMPTY, "double_pinyin_flypy", "", true));
        assertFalse(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(':', 0)));
        assertEquals(Arrays.asList((int) ':'), backend.processedKeys);
    }

    @Test
    public void chineseVIsPassedToRime() throws Exception {
        setField("activeSchemaId", "double_pinyin_flypy");
        setState("ACTIVE");
        backend.processResults.add(new RimeKeyResult(true, "", RimeSnapshot.EMPTY, "double_pinyin_flypy", "", false));
        assertTrue(ime.handleKeyboardInput(new RecordingTarget(), new KeyMapper.KeyStroke('v', 0)));
        assertEquals(Arrays.asList((int) 'v'), backend.processedKeys);
    }

    @Test
    public void bracketsAndArrowsSelectWithoutChangingTheQuery() throws Exception {
        ItemNameIndex index = ItemNameIndex.build(Arrays.asList("16A 动力仓", "4A 动力仓"));
        ime = new ClientIme(() -> index);
        setField("backend", backend);
        setField("activeSchemaId", "double_pinyin_flypy");
        setState("ACTIVE");
        RecordingTarget target = new RecordingTarget();
        for (char character : ":dslich+".toCharArray()) {
            assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(character, 0)));
        }
        for (int symbol : new int[] { ']', '[', 0xff54, 0xff52, ']' }) {
            assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(symbol, 0)));
            RimeSnapshot view = ime.getSnapshot();
            assertEquals(":dslich+", view.getPreedit());
            assertEquals(symbol == '[' || symbol == 0xff52 ? 0 : 1, view.getHighlightedCandidate());
        }
        assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(' ', 0)));
        assertEquals("4A 动力仓", target.insertedText);
        assertTrue(backend.processedKeys.isEmpty());
    }

    @Test
    public void keepsCompositionForSameTargetAndClearsItOnFocusLoss() throws Exception {
        Object screen = new Object();
        RecordingTarget target = new RecordingTarget();
        ime.updateInputTarget(screen, target);
        setState("ACTIVE");
        RimeSnapshot composing = new RimeSnapshot("cao", 3, false, Collections.emptyList(), -1);
        setField("snapshot", composing);

        ime.updateInputTarget(screen, target);
        assertSame(composing, ime.getSnapshot());
        assertEquals(0, backend.clearCount);
        ime.updateInputTarget(screen, null);
        assertEquals(1, backend.clearCount);
        assertFalse(
            ime.getSnapshot()
                .isVisible());
        assertEquals("", target.insertedText);
    }

    @Test
    public void clearsCompositionWhenSwitchingTargetsOrClosingGui() throws Exception {
        Object screen = new Object();
        RecordingTarget first = new RecordingTarget();
        ime.updateInputTarget(screen, first);
        setState("ACTIVE");
        setField("snapshot", new RimeSnapshot("cao", 3, false, Collections.emptyList(), -1));
        ime.updateInputTarget(screen, new RecordingTarget());
        assertEquals(1, backend.clearCount);
        assertFalse(
            ime.getSnapshot()
                .isVisible());
        setField("snapshot", new RimeSnapshot("cao", 3, false, Collections.emptyList(), -1));
        ime.onGuiOpened(null);
        assertEquals(2, backend.clearCount);
        assertFalse(
            ime.getSnapshot()
                .isVisible());
        assertEquals("", first.insertedText);
    }

    @Test
    public void forwardsRimeEnterAndEscapeAndInsertsOnlyCommittedText() throws Exception {
        RecordingTarget target = new RecordingTarget();
        setState("ACTIVE");
        backend.processResults.add(new RimeKeyResult(true, "草", RimeSnapshot.EMPTY, "", "", false));
        backend.processResults.add(new RimeKeyResult(true, "", RimeSnapshot.EMPTY, "", "", false));
        assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(0xff0d, 0)));
        assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(0xff1b, 0)));
        assertEquals(Arrays.asList(0xff0d, 0xff1b), backend.processedKeys);
        assertEquals("草", target.insertedText);
    }

    @Test
    public void itemSearchEnterCommitsAndEscapeOrFocusLossCancels() throws Exception {
        ime = new ClientIme(() -> ItemNameIndex.build(Arrays.asList("动力仓")));
        setField("backend", backend);
        setField("activeSchemaId", "double_pinyin_flypy");
        setState("ACTIVE");
        Object screen = new Object();
        RecordingTarget target = new RecordingTarget();
        ime.updateInputTarget(screen, target);
        for (char character : ":dslich".toCharArray()) {
            assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(character, 0)));
        }
        assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(0xff0d, 0)));
        assertEquals("动力仓", target.insertedText);
        assertFalse(
            ime.getSnapshot()
                .isVisible());

        assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(':', 0)));
        assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(0xff1b, 0)));
        assertFalse(
            ime.getSnapshot()
                .isVisible());
        assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(':', 0)));
        ime.updateInputTarget(screen, null);
        assertFalse(
            ime.getSnapshot()
                .isVisible());
        assertEquals("动力仓", target.insertedText);
        assertTrue(backend.processedKeys.isEmpty());
    }

    @Test
    public void colonSearchRefreshesAutomaticallyWhenStartupIndexPublishes() throws Exception {
        AtomicReference<ItemNameIndex> activeIndex = new AtomicReference<>(null);
        ime = new ClientIme(activeIndex::get);
        setField("backend", backend);
        setField("activeSchemaId", "double_pinyin_flypy");
        setState("ACTIVE");
        RecordingTarget target = new RecordingTarget();

        for (char character : ":dslich".toCharArray()) {
            assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(character, 0)));
        }

        RimeSnapshot beforeLoad = ime.getSnapshot();
        assertTrue(beforeLoad.isComposing());
        assertEquals(":dslich", beforeLoad.getPreedit());
        assertTrue(
            beforeLoad.getCandidates()
                .isEmpty());
        assertSame(beforeLoad, ime.getSnapshot());
        assertEquals(StatCollector.translateToLocal("ingameime.search.build_first"), ime.getModeNotice());

        activeIndex.set(ItemNameIndex.EMPTY);
        assertSame(beforeLoad, ime.getSnapshot());

        activeIndex.set(ItemNameIndex.build(Arrays.asList("16A 动力仓", "4A 动力仓")));
        RimeSnapshot afterLoad = ime.getSnapshot();
        assertNotSame(beforeLoad, afterLoad);
        assertEquals(
            2,
            afterLoad.getCandidates()
                .size());
        assertEquals(
            "16A 动力仓",
            afterLoad.getCandidates()
                .get(0)
                .getText());
        assertEquals(0, afterLoad.getHighlightedCandidate());
        assertSame(afterLoad, ime.getSnapshot());
        assertEquals(StatCollector.translateToLocal("ingameime.search.active"), ime.getModeNotice());

        assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(']', 0)));
        assertEquals(
            1,
            ime.getSnapshot()
                .getHighlightedCandidate());
        assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(' ', 0)));
        assertEquals("4A 动力仓", target.insertedText);

        target.insertedText = "";
        activeIndex.set(ItemNameIndex.EMPTY);
        for (char character : ":dslich".toCharArray()) {
            assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(character, 0)));
        }
        activeIndex.set(ItemNameIndex.build(Collections.singletonList("16A 动力仓")));
        assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(0xff0d, 0)));
        assertEquals("16A 动力仓", target.insertedText);
        assertTrue(backend.processedKeys.isEmpty());
    }

    @Test
    public void colonSearchPreservesSessionAndRefreshesAcrossDictionaryRebuildAndSchemaReload() throws Exception {
        AtomicReference<ItemNameIndex> activeIndex = new AtomicReference<>(
            ItemNameIndex.build(Collections.singletonList("4A 动力仓")));
        ime = new ClientIme(activeIndex::get);
        setField("backend", backend);
        setField("activeSchemaId", "double_pinyin_flypy");
        setState("ACTIVE");
        RecordingTarget target = new RecordingTarget();

        for (char character : ":dslich+16a".toCharArray()) {
            assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(character, 0)));
        }
        assertTrue(
            ime.getSnapshot()
                .getCandidates()
                .isEmpty());
        assertEquals(StatCollector.translateToLocal("ingameime.search.no_matches"), ime.getModeNotice());

        activeIndex.set(ItemNameIndex.build(Arrays.asList("16A 动力仓", "4A 动力仓")));
        backend.reloadResult = new RimeKeyResult(true, "", RimeSnapshot.EMPTY, "double_pinyin_flypy", "小鹤双拼", false);
        assertTrue(ime.reloadSchema());

        RimeSnapshot rebuiltView = ime.getSnapshot();
        assertTrue(rebuiltView.isComposing());
        assertEquals(":dslich+16a", rebuiltView.getPreedit());
        assertEquals(
            1,
            rebuiltView.getCandidates()
                .size());
        assertEquals(
            "16A 动力仓",
            rebuiltView.getCandidates()
                .get(0)
                .getText());
        assertEquals(StatCollector.translateToLocal("ingameime.search.active"), ime.getModeNotice());

        assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(' ', 0)));
        assertEquals("16A 动力仓", target.insertedText);
        assertFalse(
            ime.getSnapshot()
                .isVisible());

        for (char character : ":dslich".toCharArray()) {
            assertTrue(ime.handleKeyboardInput(target, new KeyMapper.KeyStroke(character, 0)));
        }
        backend.reloadResult = new RimeKeyResult(true, "", RimeSnapshot.EMPTY, "double_pinyin_flypy", "小鹤双拼", true);
        assertTrue(ime.reloadSchema());
        assertFalse(
            ime.getSnapshot()
                .isVisible());
    }

    private File createFakeRimeLayout(String name) throws Exception {
        File gameDir = temporaryFolder.newFolder(name);
        File nativeDir = new File(gameDir, "ingameime/native");
        File sharedDir = new File(gameDir, "ingameime/shared");
        File userDir = new File(gameDir, "ingameime/user");
        assertTrue(nativeDir.mkdirs());
        assertTrue(sharedDir.mkdirs());
        assertTrue(userDir.mkdirs());
        String osName = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT);
        String libName = osName.contains("win") ? "rime.dll" : osName.contains("mac") ? "librime.dylib" : "librime.so";
        assertTrue(new File(nativeDir, libName).createNewFile());
        return gameDir;
    }

    private void setField(String name, Object value) throws Exception {
        Field field = ClientIme.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(ime, value);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void setState(String name) throws Exception {
        Field field = ClientIme.class.getDeclaredField("state");
        field.setAccessible(true);
        Class<? extends Enum> stateType = (Class<? extends Enum>) field.getType();
        field.set(ime, Enum.valueOf(stateType, name));
    }

    private static final class RecordingTarget implements InputTarget {

        private String insertedText = "";

        @Override
        public Object owner() {
            return this;
        }

        @Override
        public int kind() {
            return 0;
        }

        @Override
        public InputBounds bounds() {
            return new InputBounds(0, 0, 0, 0);
        }

        @Override
        public void insertText(String text) {
            insertedText += text;
        }
    }

    private static final class FakeBackend implements RimeBackend {

        private String schemaId = "";
        private String schemaName = "";
        private String rawInput = "";
        private int rawInputReads;
        private RimeKeyResult reloadResult;
        private RuntimeException reloadFailure;
        private int reloadCount;
        private int clearCount;
        private volatile boolean closed;
        private final Queue<RimeKeyResult> processResults = new ArrayDeque<>();
        private final List<Integer> processedKeys = new ArrayList<>();

        @Override
        public RimeKeyResult processKey(int keycode, int modifiers) {
            processedKeys.add(keycode);
            return processResults.remove();
        }

        @Override
        public String getRawInput() {
            rawInputReads++;
            return rawInput;
        }

        @Override
        public RimeSnapshot clearComposition() {
            clearCount++;
            return RimeSnapshot.EMPTY;
        }

        @Override
        public RimeKeyResult changeAsciiMode(boolean asciiMode, boolean commitRawInput) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RimeKeyResult reloadSchema() {
            reloadCount++;
            if (reloadFailure != null) {
                throw reloadFailure;
            }
            return reloadResult;
        }

        @Override
        public String getDescription() {
            return "fake";
        }

        @Override
        public String getSchemaId() {
            return schemaId;
        }

        @Override
        public String getSchemaName() {
            return schemaName;
        }

        @Override
        public boolean isAsciiMode() {
            return false;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
