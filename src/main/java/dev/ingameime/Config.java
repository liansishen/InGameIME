package dev.ingameime;

import java.io.File;
import java.util.Arrays;

import net.minecraftforge.common.config.Configuration;

public final class Config {

    public static final String MODE_SWITCH_SHIFT = "shift";
    public static final String MODE_SWITCH_LEFT_SHIFT = "left_shift";
    public static final String MODE_SWITCH_CTRL_SHIFT = "ctrl_shift";
    public static final String MODE_SWITCH_DISABLED = "disabled";
    public static final String OPEN_MODE_REMEMBER = "remember";
    public static final String OPEN_MODE_CHINESE = "chinese";
    public static final String OPEN_MODE_ENGLISH = "english";

    public static boolean enabled = true;
    public static boolean autoDetectSystemData = false;
    public static String nativeLibraryDirectory = "";
    public static String sharedDataDirectory = "";
    public static String userDataDirectory = "";
    public static String schemaId = "";
    public static String requiredModules = "";
    public static String modeSwitchKey = MODE_SWITCH_SHIFT;
    public static String openInputMode = OPEN_MODE_REMEMBER;
    public static boolean showModeIndicator = true;
    public static int modeNoticeMillis = 3000;
    public static boolean showSchemaNotice = true;
    public static boolean showCandidateComments = true;

    private static File configFile;

    private Config() {}

    public static synchronized void load(File file) {
        configFile = file;
        Configuration config = new Configuration(file);
        Values values = read(config);
        apply(values);
        if (config.hasChanged()) {
            config.save();
        }
    }

    public static synchronized Values snapshot() {
        return new Values(
            enabled,
            autoDetectSystemData,
            nativeLibraryDirectory,
            sharedDataDirectory,
            userDataDirectory,
            schemaId,
            requiredModules,
            modeSwitchKey,
            openInputMode,
            showModeIndicator,
            modeNoticeMillis,
            showSchemaNotice,
            showCandidateComments);
    }

    public static synchronized void saveSchemaId(String requestedSchemaId) {
        Values values = snapshot();
        values.schemaId = requestedSchemaId;
        save(values);
    }

    public static synchronized void save(Values requested) {
        Values values = normalize(requested);
        Configuration config = new Configuration(configFile);
        config
            .get(
                Configuration.CATEGORY_GENERAL,
                "enabled",
                true,
                "Enable InGameIME when all external dependencies are available.")
            .set(values.enabled);
        config.get(
            Configuration.CATEGORY_GENERAL,
            "autoDetectSystemData",
            false,
            "Use a detected system Rime user directory instead of the game instance directory when userDataDirectory is empty.")
            .set(values.autoDetectSystemData);
        config.get(
            Configuration.CATEGORY_GENERAL,
            "nativeLibraryDirectory",
            "",
            "Directory containing librime and its native dependencies. Empty uses ingameime/native in the game instance.")
            .set(values.nativeLibraryDirectory);
        config
            .get(
                Configuration.CATEGORY_GENERAL,
                "sharedDataDirectory",
                "",
                "Rime shared data directory. Empty uses ingameime/shared in the game instance.")
            .set(values.sharedDataDirectory);
        config
            .get(
                Configuration.CATEGORY_GENERAL,
                "userDataDirectory",
                "",
                "Rime user data directory. Empty uses ingameime/user in the game instance.")
            .set(values.userDataDirectory);
        config
            .get(
                Configuration.CATEGORY_GENERAL,
                "schemaId",
                "",
                "Schema to select after deployment. Leave empty to use Rime's configured default.")
            .set(values.schemaId);
        config
            .get(
                Configuration.CATEGORY_GENERAL,
                "requiredModules",
                "",
                "Comma-separated librime module names required by the selected schema, for example lua or octagram.")
            .set(values.requiredModules);
        config
            .get(
                Configuration.CATEGORY_GENERAL,
                "modeSwitchKey",
                MODE_SWITCH_SHIFT,
                "Shortcut used to switch Chinese and English input modes.")
            .set(values.modeSwitchKey);
        config
            .get(
                Configuration.CATEGORY_GENERAL,
                "openInputMode",
                OPEN_MODE_REMEMBER,
                "Input mode selected when an input screen opens.")
            .set(values.openInputMode);
        config
            .get(
                Configuration.CATEGORY_GENERAL,
                "showModeIndicator",
                true,
                "Show a temporary Chinese or English mode indicator.")
            .set(values.showModeIndicator);
        config
            .get(
                Configuration.CATEGORY_GENERAL,
                "modeNoticeMillis",
                3000,
                "Duration of the input mode notice in milliseconds.")
            .set(values.modeNoticeMillis);
        config
            .get(
                Configuration.CATEGORY_GENERAL,
                "showSchemaNotice",
                true,
                "Show the active schema name after switching schemas.")
            .set(values.showSchemaNotice);
        config
            .get(
                Configuration.CATEGORY_GENERAL,
                "showCandidateComments",
                true,
                "Show comments returned with Rime candidates.")
            .set(values.showCandidateComments);
        config.save();
        apply(values);
    }

    private static Values read(Configuration config) {
        return normalize(
            new Values(
                config.getBoolean(
                    "enabled",
                    Configuration.CATEGORY_GENERAL,
                    true,
                    "Enable InGameIME when all external dependencies are available."),
                config.getBoolean(
                    "autoDetectSystemData",
                    Configuration.CATEGORY_GENERAL,
                    false,
                    "Use a detected system Rime user directory instead of the game instance directory when userDataDirectory is empty."),
                config.getString(
                    "nativeLibraryDirectory",
                    Configuration.CATEGORY_GENERAL,
                    "",
                    "Directory containing librime and its native dependencies. Empty uses ingameime/native in the game instance."),
                config.getString(
                    "sharedDataDirectory",
                    Configuration.CATEGORY_GENERAL,
                    "",
                    "Rime shared data directory. Empty uses ingameime/shared in the game instance."),
                config.getString(
                    "userDataDirectory",
                    Configuration.CATEGORY_GENERAL,
                    "",
                    "Rime user data directory. Empty uses ingameime/user in the game instance."),
                config.getString(
                    "schemaId",
                    Configuration.CATEGORY_GENERAL,
                    "",
                    "Schema to select after deployment. Leave empty to use Rime's configured default."),
                config.getString(
                    "requiredModules",
                    Configuration.CATEGORY_GENERAL,
                    "",
                    "Comma-separated librime module names required by the selected schema, for example lua or octagram."),
                config.getString(
                    "modeSwitchKey",
                    Configuration.CATEGORY_GENERAL,
                    MODE_SWITCH_SHIFT,
                    "Shortcut used to switch Chinese and English input modes."),
                config.getString(
                    "openInputMode",
                    Configuration.CATEGORY_GENERAL,
                    OPEN_MODE_REMEMBER,
                    "Input mode selected when an input screen opens."),
                config.getBoolean(
                    "showModeIndicator",
                    Configuration.CATEGORY_GENERAL,
                    true,
                    "Show a temporary Chinese or English mode indicator."),
                config.getInt(
                    "modeNoticeMillis",
                    Configuration.CATEGORY_GENERAL,
                    3000,
                    1000,
                    10000,
                    "Duration of the input mode notice in milliseconds."),
                config.getBoolean(
                    "showSchemaNotice",
                    Configuration.CATEGORY_GENERAL,
                    true,
                    "Show the active schema name after switching schemas."),
                config.getBoolean(
                    "showCandidateComments",
                    Configuration.CATEGORY_GENERAL,
                    true,
                    "Show comments returned with Rime candidates.")));
    }

    private static Values normalize(Values values) {
        return new Values(
            values.enabled,
            values.autoDetectSystemData,
            trim(values.nativeLibraryDirectory),
            trim(values.sharedDataDirectory),
            trim(values.userDataDirectory),
            trim(values.schemaId),
            trim(values.requiredModules),
            choice(
                values.modeSwitchKey,
                MODE_SWITCH_SHIFT,
                MODE_SWITCH_LEFT_SHIFT,
                MODE_SWITCH_CTRL_SHIFT,
                MODE_SWITCH_DISABLED),
            choice(values.openInputMode, OPEN_MODE_REMEMBER, OPEN_MODE_CHINESE, OPEN_MODE_ENGLISH),
            values.showModeIndicator,
            Math.max(1000, Math.min(10000, values.modeNoticeMillis)),
            values.showSchemaNotice,
            values.showCandidateComments);
    }

    private static void apply(Values values) {
        enabled = values.enabled;
        autoDetectSystemData = values.autoDetectSystemData;
        nativeLibraryDirectory = values.nativeLibraryDirectory;
        sharedDataDirectory = values.sharedDataDirectory;
        userDataDirectory = values.userDataDirectory;
        schemaId = values.schemaId;
        requiredModules = values.requiredModules;
        modeSwitchKey = values.modeSwitchKey;
        openInputMode = values.openInputMode;
        showModeIndicator = values.showModeIndicator;
        modeNoticeMillis = values.modeNoticeMillis;
        showSchemaNotice = values.showSchemaNotice;
        showCandidateComments = values.showCandidateComments;
    }

    private static String choice(String value, String fallback, String... allowed) {
        String normalized = trim(value);
        return Arrays.asList(allowed)
            .contains(normalized) ? normalized : fallback;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Values {

        public boolean enabled;
        public boolean autoDetectSystemData;
        public String nativeLibraryDirectory;
        public String sharedDataDirectory;
        public String userDataDirectory;
        public String schemaId;
        public String requiredModules;
        public String modeSwitchKey;
        public String openInputMode;
        public boolean showModeIndicator;
        public int modeNoticeMillis;
        public boolean showSchemaNotice;
        public boolean showCandidateComments;

        Values(boolean enabled, boolean autoDetectSystemData, String nativeLibraryDirectory, String sharedDataDirectory,
            String userDataDirectory, String schemaId, String requiredModules, String modeSwitchKey,
            String openInputMode, boolean showModeIndicator, int modeNoticeMillis, boolean showSchemaNotice,
            boolean showCandidateComments) {
            this.enabled = enabled;
            this.autoDetectSystemData = autoDetectSystemData;
            this.nativeLibraryDirectory = nativeLibraryDirectory;
            this.sharedDataDirectory = sharedDataDirectory;
            this.userDataDirectory = userDataDirectory;
            this.schemaId = schemaId;
            this.requiredModules = requiredModules;
            this.modeSwitchKey = modeSwitchKey;
            this.openInputMode = openInputMode;
            this.showModeIndicator = showModeIndicator;
            this.modeNoticeMillis = modeNoticeMillis;
            this.showSchemaNotice = showSchemaNotice;
            this.showCandidateComments = showCandidateComments;
        }

        public static Values defaults() {
            return new Values(
                true,
                false,
                "",
                "",
                "",
                "",
                "",
                MODE_SWITCH_SHIFT,
                OPEN_MODE_REMEMBER,
                true,
                3000,
                true,
                true);
        }
    }
}
