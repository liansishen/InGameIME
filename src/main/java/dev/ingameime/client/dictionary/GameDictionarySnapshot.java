package dev.ingameime.client.dictionary;

public final class GameDictionarySnapshot {

    public enum Phase {
        IDLE,
        SCANNING,
        CONVERTING,
        WRITING,
        COMPLETED,
        CANCELLED,
        FAILED
    }

    public static final GameDictionarySnapshot IDLE = new GameDictionarySnapshot(
        Phase.IDLE,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        "",
        "",
        "",
        0L,
        false);

    private final Phase phase;
    private final int scannedItems;
    private final int totalItems;
    private final int collectedNames;
    private final int processedNames;
    private final int totalNames;
    private final int writtenEntries;
    private final int skippedNames;
    private final int emptyNames;
    private final int translationKeys;
    private final int duplicateNames;
    private final int nonChineseNames;
    private final int failedItems;
    private final String detail;
    private final String targetPath;
    private final String fingerprint;
    private final long updatedAt;
    private final boolean restartRequired;

    GameDictionarySnapshot(Phase phase, int scannedItems, int totalItems, int collectedNames, int processedNames,
        int totalNames, int writtenEntries, int skippedNames, int failedItems, String detail, String targetPath,
        String fingerprint, long updatedAt, boolean restartRequired) {
        this(
            phase,
            scannedItems,
            totalItems,
            collectedNames,
            processedNames,
            totalNames,
            writtenEntries,
            skippedNames,
            0,
            0,
            0,
            0,
            failedItems,
            detail,
            targetPath,
            fingerprint,
            updatedAt,
            restartRequired);
    }

    GameDictionarySnapshot(Phase phase, int scannedItems, int totalItems, int collectedNames, int processedNames,
        int totalNames, int writtenEntries, int skippedNames, int emptyNames, int translationKeys, int duplicateNames,
        int nonChineseNames, int failedItems, String detail, String targetPath, String fingerprint, long updatedAt,
        boolean restartRequired) {
        this.phase = phase;
        this.scannedItems = scannedItems;
        this.totalItems = totalItems;
        this.collectedNames = collectedNames;
        this.processedNames = processedNames;
        this.totalNames = totalNames;
        this.writtenEntries = writtenEntries;
        this.skippedNames = skippedNames;
        this.emptyNames = emptyNames;
        this.translationKeys = translationKeys;
        this.duplicateNames = duplicateNames;
        this.nonChineseNames = nonChineseNames;
        this.failedItems = failedItems;
        this.detail = detail;
        this.targetPath = targetPath;
        this.fingerprint = fingerprint;
        this.updatedAt = updatedAt;
        this.restartRequired = restartRequired;
    }

    public Phase getPhase() {
        return phase;
    }

    public int getScannedItems() {
        return scannedItems;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public int getCollectedNames() {
        return collectedNames;
    }

    public int getProcessedNames() {
        return processedNames;
    }

    public int getTotalNames() {
        return totalNames;
    }

    public int getWrittenEntries() {
        return writtenEntries;
    }

    public int getSkippedNames() {
        return skippedNames;
    }

    public int getEmptyNames() {
        return emptyNames;
    }

    public int getTranslationKeys() {
        return translationKeys;
    }

    public int getDuplicateNames() {
        return duplicateNames;
    }

    public int getNonChineseNames() {
        return nonChineseNames;
    }

    public int getClassifiedSkippedNames() {
        return emptyNames + translationKeys + duplicateNames + nonChineseNames;
    }

    public int getFailedItems() {
        return failedItems;
    }

    public String getDetail() {
        return detail;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public boolean isRestartRequired() {
        return restartRequired;
    }

    public boolean isRunning() {
        return phase == Phase.SCANNING || phase == Phase.CONVERTING || phase == Phase.WRITING;
    }

    public float getProgress() {
        if (phase == Phase.SCANNING) {
            return ratio(scannedItems, totalItems) * 0.45F;
        }
        if (phase == Phase.CONVERTING) {
            return 0.45F + ratio(processedNames, totalNames) * 0.45F;
        }
        if (phase == Phase.WRITING) {
            return 0.95F;
        }
        if (phase == Phase.COMPLETED) {
            return 1.0F;
        }
        return 0.0F;
    }

    private static float ratio(int value, int total) {
        return total <= 0 ? 0.0F : Math.min(1.0F, (float) value / total);
    }
}
