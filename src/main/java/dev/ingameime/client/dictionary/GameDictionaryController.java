package dev.ingameime.client.dictionary;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameData;
import dev.ingameime.InGameIME;
import dev.ingameime.client.ClientIme;
import dev.ingameime.rime.RimeRuntimeConfig;

public final class GameDictionaryController {

    private static final GameDictionaryController INSTANCE = new GameDictionaryController();
    private static final int ITEMS_PER_TICK = 24;
    private static final String GENERATOR_VERSION = "3";

    private volatile GameDictionarySnapshot snapshot;
    private volatile boolean cancelRequested;
    private final File stateFile;
    private List<Item> items = Collections.emptyList();
    private List<String> rawNames = Collections.emptyList();
    private int scanIndex;
    private int failedItems;
    private File target;
    private File flypyTarget;
    private String fingerprint = "";

    private GameDictionaryController() {
        stateFile = new File(Minecraft.getMinecraft().mcDataDir, "config/ingameime-dictionary.properties");
        snapshot = loadState();
    }

    public static GameDictionaryController getInstance() {
        return INSTANCE;
    }

    public GameDictionarySnapshot getSnapshot() {
        return snapshot;
    }

    public synchronized void startGeneration() {
        if (snapshot.isRunning()) {
            return;
        }
        try {
            RimeRuntimeConfig runtime = RimeRuntimeConfig.resolve();
            File rimeIceSchema = new File(runtime.getUserDataDirectory(), "rime_ice.schema.yaml");
            File flypySchema = new File(runtime.getUserDataDirectory(), "double_pinyin_flypy.schema.yaml");
            if (!rimeIceSchema.isFile() && !flypySchema.isFile()) {
                throw new IllegalStateException(
                    "no supported Rime Ice schema was found in the configured user directory");
            }
            target = new File(runtime.getUserDataDirectory(), "ingameime_game_phrase.txt");
            flypyTarget = new File(runtime.getUserDataDirectory(), "ingameime_game_phrase_flypy.txt");
            fingerprint = fingerprint();
            items = new ArrayList<>();
            for (Item item : GameData.getItemRegistry()
                .typeSafeIterable()) {
                items.add(item);
            }
            rawNames = new ArrayList<>();
            scanIndex = 0;
            failedItems = 0;
            cancelRequested = false;
            publish(
                new GameDictionarySnapshot(
                    GameDictionarySnapshot.Phase.SCANNING,
                    0,
                    items.size(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "",
                    target.getAbsolutePath(),
                    fingerprint,
                    System.currentTimeMillis(),
                    false),
                true);
        } catch (RuntimeException failure) {
            fail(message(failure));
        }
    }

    public synchronized void cancelGeneration() {
        if (!snapshot.isRunning() || snapshot.getPhase() == GameDictionarySnapshot.Phase.WRITING) {
            return;
        }
        cancelRequested = true;
        if (snapshot.getPhase() == GameDictionarySnapshot.Phase.SCANNING) {
            finishCancelled(
                snapshot.getProcessedNames(),
                snapshot.getTotalNames(),
                snapshot.getWrittenEntries(),
                snapshot.getSkippedNames(),
                snapshot.getEmptyNames(),
                snapshot.getTranslationKeys(),
                snapshot.getDuplicateNames(),
                snapshot.getNonChineseNames());
        }
    }

    @SubscribeEvent
    public synchronized void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || snapshot.getPhase() != GameDictionarySnapshot.Phase.SCANNING) {
            return;
        }
        if (cancelRequested) {
            finishCancelled(0, 0, 0, 0, 0, 0, 0, 0);
            return;
        }

        int end = Math.min(items.size(), scanIndex + ITEMS_PER_TICK);
        while (scanIndex < end) {
            scanItem(items.get(scanIndex));
            scanIndex++;
        }
        publish(
            new GameDictionarySnapshot(
                GameDictionarySnapshot.Phase.SCANNING,
                scanIndex,
                items.size(),
                rawNames.size(),
                0,
                0,
                0,
                0,
                failedItems,
                "",
                target.getAbsolutePath(),
                fingerprint,
                System.currentTimeMillis(),
                false),
            false);
        if (scanIndex == items.size()) {
            startWorker();
        }
    }

    private void scanItem(Item item) {
        try {
            Map<Integer, ItemStack> variants = new LinkedHashMap<>();
            CreativeTabs[] tabs = item.getCreativeTabs();
            if (tabs != null) {
                for (CreativeTabs tab : tabs) {
                    if (tab == null) {
                        continue;
                    }
                    List<ItemStack> subItems = new ArrayList<>();
                    item.getSubItems(item, tab, subItems);
                    for (ItemStack stack : subItems) {
                        if (stack != null && stack.getItem() == item) {
                            variants.putIfAbsent(stack.getItemDamage(), stack);
                        }
                    }
                }
            }
            if (variants.isEmpty()) {
                variants.put(0, new ItemStack(item, 1, 0));
            }
            for (ItemStack stack : variants.values()) {
                rawNames.add(stack.getDisplayName());
            }
        } catch (RuntimeException | LinkageError failure) {
            failedItems++;
            InGameIME.LOG.warn(
                "Skipping item {} while generating the game dictionary",
                GameData.getItemRegistry()
                    .getNameForObject(item),
                failure);
        }
    }

    private void startWorker() {
        List<String> names = new ArrayList<>(rawNames);
        int scannedItems = items.size();
        int scanFailures = failedItems;
        File output = target;
        File flypyOutput = flypyTarget;
        String runFingerprint = fingerprint;
        items = Collections.emptyList();
        rawNames = Collections.emptyList();
        publish(
            new GameDictionarySnapshot(
                GameDictionarySnapshot.Phase.CONVERTING,
                scannedItems,
                scannedItems,
                names.size(),
                0,
                names.size(),
                0,
                0,
                scanFailures,
                "",
                output.getAbsolutePath(),
                runFingerprint,
                System.currentTimeMillis(),
                false),
            true);
        Thread worker = new Thread(
            () -> runGenerator(names, scannedItems, scanFailures, output, flypyOutput, runFingerprint),
            "InGameIME game dictionary");
        worker.setDaemon(true);
        worker.start();
    }

    private void runGenerator(List<String> names, int scannedItems, int scanFailures, File output, File flypyOutput,
        String runFingerprint) {
        try {
            GameDictionaryGenerator.Result result = GameDictionaryGenerator.generate(
                names,
                output,
                flypyOutput,
                runFingerprint,
                () -> cancelRequested,
                processed -> updateConverting(
                    processed,
                    names.size(),
                    scannedItems,
                    scanFailures,
                    output,
                    runFingerprint),
                () -> beginWriting(names.size(), scannedItems, scanFailures, output, runFingerprint));
            if (result.cancelled) {
                finishCancelled(
                    result.processed,
                    names.size(),
                    result.entries,
                    result.skipped,
                    result.emptyNames,
                    result.translationKeys,
                    result.duplicateNames,
                    result.nonChineseNames);
            } else {
                GameDictionaryGenerator.removeGeneratedBlock(new File(output.getParentFile(), "custom_phrase.txt"));
                GameDictionaryGenerator
                    .removeGeneratedBlock(new File(output.getParentFile(), "custom_phrase_double.txt"));
                scheduleCompletion(names.size(), scannedItems, scanFailures, result, output, runFingerprint);
            }
        } catch (IOException | RuntimeException failure) {
            fail(message(failure));
        }
    }

    private void scheduleCompletion(int total, int scannedItems, int scanFailures,
        GameDictionaryGenerator.Result result, File output, String runFingerprint) {
        Minecraft.getMinecraft()
            .func_152344_a(
                () -> complete(
                    total,
                    scannedItems,
                    scanFailures,
                    result,
                    output,
                    runFingerprint,
                    !ClientIme.getInstance()
                        .reloadSchema()));
    }

    private synchronized void updateConverting(int processed, int total, int scannedItems, int scanFailures,
        File output, String runFingerprint) {
        if (snapshot.getPhase() != GameDictionarySnapshot.Phase.CONVERTING) {
            return;
        }
        publish(
            new GameDictionarySnapshot(
                GameDictionarySnapshot.Phase.CONVERTING,
                scannedItems,
                scannedItems,
                total,
                processed,
                total,
                0,
                0,
                scanFailures,
                "",
                output.getAbsolutePath(),
                runFingerprint,
                System.currentTimeMillis(),
                false),
            false);
    }

    private synchronized void beginWriting(int total, int scannedItems, int scanFailures, File output,
        String runFingerprint) {
        publish(
            new GameDictionarySnapshot(
                GameDictionarySnapshot.Phase.WRITING,
                scannedItems,
                scannedItems,
                total,
                total,
                total,
                0,
                0,
                scanFailures,
                "",
                output.getAbsolutePath(),
                runFingerprint,
                System.currentTimeMillis(),
                false),
            true);
    }

    private synchronized void complete(int total, int scannedItems, int scanFailures,
        GameDictionaryGenerator.Result result, File output, String runFingerprint, boolean restartRequired) {
        cancelRequested = false;
        publish(
            new GameDictionarySnapshot(
                GameDictionarySnapshot.Phase.COMPLETED,
                scannedItems,
                scannedItems,
                total,
                total,
                total,
                result.entries,
                result.skipped,
                result.emptyNames,
                result.translationKeys,
                result.duplicateNames,
                result.nonChineseNames,
                scanFailures,
                "",
                output.getAbsolutePath(),
                runFingerprint,
                System.currentTimeMillis(),
                restartRequired),
            true);
    }

    private synchronized void finishCancelled(int processed, int total, int entries, int skipped, int emptyNames,
        int translationKeys, int duplicateNames, int nonChineseNames) {
        GameDictionarySnapshot current = snapshot;
        cancelRequested = false;
        items = Collections.emptyList();
        rawNames = Collections.emptyList();
        publish(
            new GameDictionarySnapshot(
                GameDictionarySnapshot.Phase.CANCELLED,
                current.getScannedItems(),
                current.getTotalItems(),
                current.getCollectedNames(),
                processed,
                total,
                entries,
                skipped,
                emptyNames,
                translationKeys,
                duplicateNames,
                nonChineseNames,
                current.getFailedItems(),
                "",
                current.getTargetPath(),
                current.getFingerprint(),
                System.currentTimeMillis(),
                false),
            true);
    }

    private synchronized void fail(String detail) {
        GameDictionarySnapshot current = snapshot == null ? GameDictionarySnapshot.IDLE : snapshot;
        cancelRequested = false;
        items = Collections.emptyList();
        rawNames = Collections.emptyList();
        publish(
            new GameDictionarySnapshot(
                GameDictionarySnapshot.Phase.FAILED,
                current.getScannedItems(),
                current.getTotalItems(),
                current.getCollectedNames(),
                current.getProcessedNames(),
                current.getTotalNames(),
                current.getWrittenEntries(),
                current.getSkippedNames(),
                current.getEmptyNames(),
                current.getTranslationKeys(),
                current.getDuplicateNames(),
                current.getNonChineseNames(),
                current.getFailedItems(),
                detail,
                current.getTargetPath(),
                current.getFingerprint(),
                System.currentTimeMillis(),
                false),
            true);
        InGameIME.LOG.warn("Game dictionary generation failed: {}", detail);
    }

    private void publish(GameDictionarySnapshot value, boolean persist) {
        snapshot = value;
        if (persist) {
            saveState(value);
        }
    }

    private GameDictionarySnapshot loadState() {
        if (!stateFile.isFile()) {
            return GameDictionarySnapshot.IDLE;
        }
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(stateFile)) {
            properties.load(input);
            GameDictionarySnapshot.Phase phase = GameDictionarySnapshot.Phase.valueOf(
                properties.getProperty("phase", "IDLE")
                    .toUpperCase(Locale.ROOT));
            if (phase == GameDictionarySnapshot.Phase.SCANNING || phase == GameDictionarySnapshot.Phase.CONVERTING
                || phase == GameDictionarySnapshot.Phase.WRITING) {
                phase = GameDictionarySnapshot.Phase.FAILED;
                properties.setProperty("detail", "the previous dictionary generation was interrupted");
            }
            return new GameDictionarySnapshot(
                phase,
                integer(properties, "scannedItems"),
                integer(properties, "totalItems"),
                integer(properties, "collectedNames"),
                integer(properties, "processedNames"),
                integer(properties, "totalNames"),
                integer(properties, "writtenEntries"),
                integer(properties, "skippedNames"),
                integer(properties, "emptyNames"),
                integer(properties, "translationKeys"),
                integer(properties, "duplicateNames"),
                integer(properties, "nonChineseNames"),
                integer(properties, "failedItems"),
                properties.getProperty("detail", ""),
                properties.getProperty("targetPath", ""),
                properties.getProperty("fingerprint", ""),
                longValue(properties, "updatedAt"),
                Boolean.parseBoolean(properties.getProperty("restartRequired", "false")));
        } catch (IOException | IllegalArgumentException failure) {
            InGameIME.LOG.warn("Could not read the game dictionary state", failure);
            return GameDictionarySnapshot.IDLE;
        }
    }

    private void saveState(GameDictionarySnapshot value) {
        Properties properties = new Properties();
        properties.setProperty(
            "phase",
            value.getPhase()
                .name());
        properties.setProperty("scannedItems", Integer.toString(value.getScannedItems()));
        properties.setProperty("totalItems", Integer.toString(value.getTotalItems()));
        properties.setProperty("collectedNames", Integer.toString(value.getCollectedNames()));
        properties.setProperty("processedNames", Integer.toString(value.getProcessedNames()));
        properties.setProperty("totalNames", Integer.toString(value.getTotalNames()));
        properties.setProperty("writtenEntries", Integer.toString(value.getWrittenEntries()));
        properties.setProperty("skippedNames", Integer.toString(value.getSkippedNames()));
        properties.setProperty("emptyNames", Integer.toString(value.getEmptyNames()));
        properties.setProperty("translationKeys", Integer.toString(value.getTranslationKeys()));
        properties.setProperty("duplicateNames", Integer.toString(value.getDuplicateNames()));
        properties.setProperty("nonChineseNames", Integer.toString(value.getNonChineseNames()));
        properties.setProperty("failedItems", Integer.toString(value.getFailedItems()));
        properties.setProperty("detail", value.getDetail());
        properties.setProperty("targetPath", value.getTargetPath());
        properties.setProperty("fingerprint", value.getFingerprint());
        properties.setProperty("updatedAt", Long.toString(value.getUpdatedAt()));
        properties.setProperty("restartRequired", Boolean.toString(value.isRestartRequired()));
        try (FileOutputStream output = new FileOutputStream(stateFile)) {
            properties.store(output, "InGameIME game dictionary status");
        } catch (IOException failure) {
            InGameIME.LOG.warn("Could not persist the game dictionary state", failure);
        }
    }

    private static String fingerprint() {
        List<String> mods = new ArrayList<>();
        for (ModContainer mod : Loader.instance()
            .getActiveModList()) {
            mods.add(mod.getModId() + "=" + mod.getVersion());
        }
        Collections.sort(mods);
        StringBuilder source = new StringBuilder("generator=").append(GENERATOR_VERSION)
            .append("\nlanguage=")
            .append(
                FMLCommonHandler.instance()
                    .getCurrentLanguage())
            .append('\n');
        for (String mod : mods) {
            source.append(mod)
                .append('\n');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(
                    source.toString()
                        .getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static int integer(Properties properties, String key) {
        return Integer.parseInt(properties.getProperty(key, "0"));
    }

    private static long longValue(Properties properties, String key) {
        return Long.parseLong(properties.getProperty(key, "0"));
    }

    private static String message(Throwable failure) {
        String value = failure.getMessage();
        return value == null || value.trim()
            .isEmpty() ? failure.getClass()
                .getSimpleName() : value;
    }
}
