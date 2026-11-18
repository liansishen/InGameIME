package dev.ingameime.client.dictionary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GameDictionaryControllerTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void cancelledGenerationDuringAsyncStartupLoadPreservesPreviousIndex() throws Exception {
        File gameDir = temporaryFolder.newFolder("cancel-during-load");
        File userDir = new File(gameDir, "ingameime/user");
        assertTrue(userDir.mkdirs());
        File indexFile = new File(gameDir, "config/ingameime-item-index-v1.json");
        ItemNameIndex.build(Arrays.asList("钻石块", "铁锭"))
            .save(indexFile.toPath());

        CountDownLatch enteredInitialLoad = new CountDownLatch(1);
        CountDownLatch releaseInitialLoad = new CountDownLatch(1);
        GameDictionaryController controller = new GameDictionaryController(gameDir, path -> {
            ItemNameIndex loaded = ItemNameIndex.load(path);
            enteredInitialLoad.countDown();
            try {
                assertTrue(releaseInitialLoad.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread()
                    .interrupt();
            }
            return loaded;
        }, Collections::emptyList, () -> "fp-cancel", () -> userDir, Runnable::run);

        assertTrue(enteredInitialLoad.await(5, TimeUnit.SECONDS));
        assertSame(ItemNameIndex.EMPTY, controller.getIndex());

        controller.startGeneration();
        assertEquals(
            GameDictionarySnapshot.Phase.SCANNING,
            controller.getSnapshot()
                .getPhase());
        controller.cancelGeneration();
        assertEquals(
            GameDictionarySnapshot.Phase.CANCELLED,
            controller.getSnapshot()
                .getPhase());

        releaseInitialLoad.countDown();
        waitForLoaderCompletion();

        assertEquals(
            Collections.singletonList("钻石块"),
            controller.getIndex()
                .search("zuanshikuai", false));
        assertEquals(
            Collections.singletonList("铁锭"),
            controller.getIndex()
                .search("tieding", false));
    }

    @Test
    public void failedGenerationDuringAsyncStartupLoadPreservesPreviousIndex() throws Exception {
        File gameDir = temporaryFolder.newFolder("fail-during-load");
        File userDir = new File(gameDir, "ingameime/user");
        assertTrue(userDir.mkdirs());
        File indexFile = new File(gameDir, "config/ingameime-item-index-v1.json");
        ItemNameIndex.build(Collections.singletonList("金锭"))
            .save(indexFile.toPath());

        CountDownLatch enteredInitialLoad = new CountDownLatch(1);
        CountDownLatch releaseInitialLoad = new CountDownLatch(1);
        GameDictionaryController controller = new GameDictionaryController(gameDir, path -> {
            ItemNameIndex loaded = ItemNameIndex.load(path);
            enteredInitialLoad.countDown();
            try {
                assertTrue(releaseInitialLoad.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread()
                    .interrupt();
            }
            return loaded;
        },
            Collections::emptyList,
            () -> { throw new IllegalStateException("simulated failure"); },
            () -> userDir,
            Runnable::run);

        assertTrue(enteredInitialLoad.await(5, TimeUnit.SECONDS));
        controller.startGeneration();
        assertEquals(
            GameDictionarySnapshot.Phase.FAILED,
            controller.getSnapshot()
                .getPhase());

        releaseInitialLoad.countDown();
        waitForLoaderCompletion();

        assertEquals(
            Collections.singletonList("金锭"),
            controller.getIndex()
                .search("jinding", false));
    }

    @Test
    public void lateInitialAsyncLoadDoesNotOverwriteNewlyPublishedIndex() throws Exception {
        File gameDir = temporaryFolder.newFolder("late-initial-load");
        File userDir = new File(gameDir, "ingameime/user");
        assertTrue(userDir.mkdirs());
        File indexFile = new File(gameDir, "config/ingameime-item-index-v1.json");
        ItemNameIndex.build(Collections.singletonList("旧物品"))
            .save(indexFile.toPath());

        AtomicBoolean firstLoad = new AtomicBoolean(true);
        CountDownLatch parsedOldIndex = new CountDownLatch(1);
        CountDownLatch releaseInitialLoad = new CountDownLatch(1);
        GameDictionaryController controller = new GameDictionaryController(gameDir, path -> {
            if (firstLoad.compareAndSet(true, false)) {
                ItemNameIndex stale = ItemNameIndex.load(path);
                parsedOldIndex.countDown();
                try {
                    assertTrue(releaseInitialLoad.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread()
                        .interrupt();
                }
                return stale;
            }
            return ItemNameIndex.load(path);
        }, Collections::emptyList, () -> "fp-new", () -> userDir, Runnable::run);

        assertTrue(parsedOldIndex.await(5, TimeUnit.SECONDS));
        assertSame(ItemNameIndex.EMPTY, controller.getIndex());

        controller.runGenerator(Collections.singletonList("新物品"), 1, 0, controller.indexFile(), null, "fp-new");
        assertEquals(
            GameDictionarySnapshot.Phase.COMPLETED,
            controller.getSnapshot()
                .getPhase());
        assertEquals(
            Collections.singletonList("新物品"),
            controller.getIndex()
                .search("xinwupin", false));

        releaseInitialLoad.countDown();
        waitForLoaderCompletion();

        assertEquals(
            Collections.singletonList("新物品"),
            controller.getIndex()
                .search("xinwupin", false));
        assertTrue(
            controller.getIndex()
                .search("jiuwupin", false)
                .isEmpty());
    }

    @Test
    public void publishesNewlyWrittenIndexBeforeLegacyCleanupFailure() throws Exception {
        File gameDir = temporaryFolder.newFolder("legacy-cleanup-failure");
        File userDir = new File(gameDir, "ingameime/user");
        assertTrue(userDir.mkdirs());
        File brokenLegacy = new File(userDir, "custom_phrase.txt");
        Files.write(
            brokenLegacy.toPath(),
            "# >>> InGameIME generated game dictionary >>>\nbroken\n".getBytes(StandardCharsets.UTF_8));

        GameDictionaryController controller = new GameDictionaryController(
            gameDir,
            ItemNameIndex::load,
            Collections::emptyList,
            () -> "fp-legacy",
            () -> userDir,
            Runnable::run);

        controller.runGenerator(Collections.singletonList("动力仓"), 1, 0, controller.indexFile(), null, "fp-legacy");

        assertEquals(
            GameDictionarySnapshot.Phase.FAILED,
            controller.getSnapshot()
                .getPhase());
        assertEquals(
            Collections.singletonList("动力仓"),
            controller.getIndex()
                .search("donglicang", false));
    }

    private static void waitForLoaderCompletion() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            boolean running = false;
            for (Thread thread : Thread.getAllStackTraces()
                .keySet()) {
                if ("InGameIME index loader".equals(thread.getName()) && thread.isAlive()) {
                    running = true;
                    break;
                }
            }
            if (!running) {
                return;
            }
            Thread.sleep(10L);
        }
    }
}
