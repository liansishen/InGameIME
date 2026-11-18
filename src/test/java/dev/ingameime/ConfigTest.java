package dev.ingameime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cpw.mods.fml.relauncher.FMLInjectionData;

public class ConfigTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Config.Values previousValues;
    private File previousConfigFile;
    private File previousMinecraftHome;

    @Before
    public void rememberConfig() throws Exception {
        previousValues = Config.snapshot();
        previousConfigFile = (File) configFileField().get(null);
        Field minecraftHome = minecraftHomeField();
        previousMinecraftHome = (File) minecraftHome.get(null);
        minecraftHome.set(null, temporaryFolder.getRoot());
    }

    @After
    public void restoreConfig() throws Exception {
        Config.flushPendingSave();
        Method apply = Config.class.getDeclaredMethod("apply", Config.Values.class);
        apply.setAccessible(true);
        apply.invoke(null, previousValues);
        configFileField().set(null, previousConfigFile);
        minecraftHomeField().set(null, previousMinecraftHome);
    }

    @Test
    public void savesSchemaIdWithoutChangingOtherSettings() throws Exception {
        File configFile = temporaryFolder.newFile("ingameime.cfg");
        Config.load(configFile);
        Config.Values values = Config.snapshot();
        values.enabled = false;
        values.modeSwitchKey = Config.MODE_SWITCH_CTRL_SHIFT;
        Config.save(values);

        Config.saveSchemaId("double_pinyin_flypy");
        Config.load(configFile);

        assertEquals("double_pinyin_flypy", Config.schemaId);
        assertFalse(Config.enabled);
        assertEquals(Config.MODE_SWITCH_CTRL_SHIFT, Config.modeSwitchKey);
    }

    @Test
    public void savesSchemaIdAsynchronouslyAndCoalescesUpdates() throws Exception {
        File configFile = temporaryFolder.newFile("ingameime-async.cfg");
        Config.load(configFile);
        Config.Values values = Config.snapshot();
        values.enabled = false;
        values.openInputMode = Config.OPEN_MODE_CHINESE;
        Config.save(values);

        Config.saveSchemaIdAsync("rime_ice");
        Config.saveSchemaIdAsync("double_pinyin_flypy");
        assertEquals("double_pinyin_flypy", Config.schemaId);
        assertEquals("double_pinyin_flypy", Config.snapshot().schemaId);

        long deadline = System.currentTimeMillis() + 3000L;
        while (System.currentTimeMillis() < deadline) {
            Config.flushPendingSave();
            Config.load(configFile);
            if ("double_pinyin_flypy".equals(Config.schemaId)) {
                break;
            }
            Thread.sleep(10L);
        }

        assertEquals("double_pinyin_flypy", Config.schemaId);
        assertFalse(Config.enabled);
        assertEquals(Config.OPEN_MODE_CHINESE, Config.openInputMode);
    }

    @Test
    public void syncSaveIsNotOverwrittenByPendingOrContendedAsyncSave() throws Exception {
        File configFile = temporaryFolder.newFile("ingameime-race.cfg");
        Config.load(configFile);
        Object ioLock = ioLockField().get(null);
        AtomicBoolean asyncWorkerRunning = (AtomicBoolean) asyncWorkerRunningField().get(null);

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        Thread lockHolder = new Thread(() -> {
            synchronized (ioLock) {
                lockAcquired.countDown();
                try {
                    releaseLock.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread()
                        .interrupt();
                }
            }
        }, "test-io-lock-holder");
        lockHolder.start();
        assertTrue(lockAcquired.await(5, TimeUnit.SECONDS));

        try {
            // Memory update must not block even when IO_LOCK is held by slow disk I/O.
            CountDownLatch asyncReturned = new CountDownLatch(1);
            Thread asyncCaller = new Thread(() -> {
                Config.saveSchemaIdAsync("stale_schema");
                asyncReturned.countDown();
            }, "test-async-caller");
            asyncCaller.start();
            assertTrue(asyncReturned.await(1, TimeUnit.SECONDS));
            asyncCaller.join(1000L);
            assertEquals("stale_schema", Config.snapshot().schemaId);

            waitForBlockedOnIoLock("InGameIME config saver");

            Config.Values updated = Config.snapshot();
            updated.enabled = false;
            updated.schemaId = "double_pinyin_flypy";
            updated.modeSwitchKey = Config.MODE_SWITCH_CTRL_SHIFT;
            updated.openInputMode = Config.OPEN_MODE_ENGLISH;
            updated.modeNoticeMillis = 4500;

            Thread syncSaver = new Thread(() -> Config.save(updated), "test-sync-saver");
            syncSaver.start();
            waitForBlockedOnIoLock("test-sync-saver");
            assertEquals("double_pinyin_flypy", Config.snapshot().schemaId);
            assertEquals(Config.MODE_SWITCH_CTRL_SHIFT, Config.snapshot().modeSwitchKey);

            releaseLock.countDown();
            lockHolder.join(5000L);
            syncSaver.join(5000L);
            assertFalse(syncSaver.isAlive());
        } finally {
            releaseLock.countDown();
            lockHolder.join(5000L);
        }

        Config.flushPendingSave();
        long deadline = System.currentTimeMillis() + 3000L;
        while (asyncWorkerRunning.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }

        Config.load(configFile);
        assertFalse(Config.enabled);
        assertEquals("double_pinyin_flypy", Config.schemaId);
        assertEquals(Config.MODE_SWITCH_CTRL_SHIFT, Config.modeSwitchKey);
        assertEquals(Config.OPEN_MODE_ENGLISH, Config.openInputMode);
        assertEquals(4500, Config.modeNoticeMillis);
    }

    @Test
    public void interleavedAsyncSchemaUpdatesNeverRevertSubsequentSyncSave() throws Exception {
        File configFile = temporaryFolder.newFile("ingameime-interleaved.cfg");
        Config.load(configFile);
        AtomicBoolean asyncWorkerRunning = (AtomicBoolean) asyncWorkerRunningField().get(null);

        for (int round = 0; round < 15; round++) {
            Config.saveSchemaIdAsync("stale_" + round);
            Config.Values fresh = Config.snapshot();
            fresh.enabled = (round % 2) == 0;
            fresh.schemaId = "fresh_" + round;
            fresh.modeSwitchKey = Config.MODE_SWITCH_LEFT_SHIFT;
            fresh.openInputMode = Config.OPEN_MODE_CHINESE;
            Config.save(fresh);
            Config.flushPendingSave();
        }

        long deadline = System.currentTimeMillis() + 3000L;
        while (asyncWorkerRunning.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        Config.flushPendingSave();

        Config.load(configFile);
        assertTrue(Config.enabled);
        assertEquals("fresh_14", Config.schemaId);
        assertEquals(Config.MODE_SWITCH_LEFT_SHIFT, Config.modeSwitchKey);
        assertEquals(Config.OPEN_MODE_CHINESE, Config.openInputMode);
    }

    private static void waitForBlockedOnIoLock(String threadName) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000L;
        while (System.currentTimeMillis() < deadline) {
            for (Thread thread : Thread.getAllStackTraces()
                .keySet()) {
                if (threadName.equals(thread.getName()) && thread.getState() == Thread.State.BLOCKED) {
                    return;
                }
            }
            Thread.sleep(5L);
        }
    }

    private static Field ioLockField() throws NoSuchFieldException {
        Field field = Config.class.getDeclaredField("IO_LOCK");
        field.setAccessible(true);
        return field;
    }

    private static Field asyncWorkerRunningField() throws NoSuchFieldException {
        Field field = Config.class.getDeclaredField("ASYNC_WORKER_RUNNING");
        field.setAccessible(true);
        return field;
    }

    private static Field configFileField() throws NoSuchFieldException {
        Field field = Config.class.getDeclaredField("configFile");
        field.setAccessible(true);
        return field;
    }

    private static Field minecraftHomeField() throws NoSuchFieldException {
        Field field = FMLInjectionData.class.getDeclaredField("minecraftHome");
        field.setAccessible(true);
        return field;
    }
}
