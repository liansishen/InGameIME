package dev.ingameime.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

import org.junit.Before;
import org.junit.Test;

import dev.ingameime.client.dictionary.ItemNameIndex;
import dev.ingameime.rime.RimeBackend;
import dev.ingameime.rime.RimeKeyResult;
import dev.ingameime.rime.RimeSnapshot;

public class ClientImeTest {

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

        private RimeKeyResult reloadResult;
        private RuntimeException reloadFailure;
        private int reloadCount;
        private boolean closed;
        private final Queue<RimeKeyResult> processResults = new ArrayDeque<>();
        private final List<Integer> processedKeys = new ArrayList<>();

        @Override
        public RimeKeyResult processKey(int keycode, int modifiers) {
            processedKeys.add(keycode);
            return processResults.remove();
        }

        @Override
        public RimeSnapshot clearComposition() {
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
            return "";
        }

        @Override
        public String getSchemaName() {
            return "";
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
