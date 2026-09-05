package dev.ingameime;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public final class Config {

    public static boolean enabled = true;
    public static boolean autoDetectSystemData = true;
    public static String nativeLibraryDirectory = "";
    public static String sharedDataDirectory = "";
    public static String userDataDirectory = "";
    public static String schemaId = "";
    public static String requiredModules = "";

    private Config() {}

    public static void load(File configFile) {
        Configuration config = new Configuration(configFile);
        enabled = config.getBoolean(
            "enabled",
            Configuration.CATEGORY_GENERAL,
            enabled,
            "Enable InGameIME when all external dependencies are available.");
        autoDetectSystemData = config.getBoolean(
            "autoDetectSystemData",
            Configuration.CATEGORY_GENERAL,
            autoDetectSystemData,
            "Use the first existing system Rime user directory when userDataDirectory is empty.");
        nativeLibraryDirectory = config.getString(
            "nativeLibraryDirectory",
            Configuration.CATEGORY_GENERAL,
            nativeLibraryDirectory,
            "Directory containing rime.dll, librime.so, or librime.dylib and its native dependencies.");
        sharedDataDirectory = config.getString(
            "sharedDataDirectory",
            Configuration.CATEGORY_GENERAL,
            sharedDataDirectory,
            "Existing Rime shared data directory. When empty, the selected user data directory is used.");
        userDataDirectory = config.getString(
            "userDataDirectory",
            Configuration.CATEGORY_GENERAL,
            userDataDirectory,
            "Existing Rime user data directory. InGameIME never creates or populates it.");
        schemaId = config.getString(
            "schemaId",
            Configuration.CATEGORY_GENERAL,
            schemaId,
            "Schema to select after deployment. Leave empty to use Rime's configured default.");
        requiredModules = config.getString(
            "requiredModules",
            Configuration.CATEGORY_GENERAL,
            requiredModules,
            "Comma-separated librime module names required by the selected schema, for example lua or octagram.");

        if (config.hasChanged()) {
            config.save();
        }
    }
}
