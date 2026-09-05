package dev.ingameime.rime;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.ingameime.Config;

public final class RimeRuntimeConfig {

    private final File nativeLibrary;
    private final File sharedDataDirectory;
    private final File userDataDirectory;
    private final String schemaId;
    private final List<String> requiredModules;

    private RimeRuntimeConfig(File nativeLibrary, File sharedDataDirectory, File userDataDirectory, String schemaId,
        List<String> requiredModules) {
        this.nativeLibrary = nativeLibrary;
        this.sharedDataDirectory = sharedDataDirectory;
        this.userDataDirectory = userDataDirectory;
        this.schemaId = schemaId;
        this.requiredModules = requiredModules;
    }

    public static RimeRuntimeConfig resolve() {
        File nativeDirectory = existingDirectory(Config.nativeLibraryDirectory, "nativeLibraryDirectory");
        File nativeLibrary = new File(nativeDirectory, platformLibraryName());
        if (!nativeLibrary.isFile()) {
            throw new IllegalArgumentException("librime was not found at " + nativeLibrary.getAbsolutePath());
        }

        File userDirectory;
        if (!Config.userDataDirectory.trim()
            .isEmpty()) {
            userDirectory = existingDirectory(Config.userDataDirectory, "userDataDirectory");
        } else if (Config.autoDetectSystemData) {
            userDirectory = detectSystemUserDirectory();
            if (userDirectory == null) {
                throw new IllegalArgumentException("no existing system Rime user directory was detected");
            }
        } else {
            throw new IllegalArgumentException("userDataDirectory is empty and automatic detection is disabled");
        }

        File sharedDirectory = Config.sharedDataDirectory.trim()
            .isEmpty() ? userDirectory : existingDirectory(Config.sharedDataDirectory, "sharedDataDirectory");
        List<String> modules = parseModules(Config.requiredModules);
        return new RimeRuntimeConfig(
            nativeLibrary.getAbsoluteFile(),
            sharedDirectory.getAbsoluteFile(),
            userDirectory.getAbsoluteFile(),
            Config.schemaId.trim(),
            Collections.unmodifiableList(modules));
    }

    public File getNativeLibrary() {
        return nativeLibrary;
    }

    public File getSharedDataDirectory() {
        return sharedDataDirectory;
    }

    public File getUserDataDirectory() {
        return userDataDirectory;
    }

    public String getSchemaId() {
        return schemaId;
    }

    public List<String> getRequiredModules() {
        return requiredModules;
    }

    private static File existingDirectory(String configuredPath, String propertyName) {
        String path = configuredPath.trim();
        if (path.isEmpty()) {
            throw new IllegalArgumentException(propertyName + " is empty");
        }
        File directory = new File(path);
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException(
                propertyName + " is not an existing directory: " + directory.getAbsolutePath());
        }
        return directory;
    }

    private static String platformLibraryName() {
        String osName = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return "rime.dll";
        }
        if (osName.contains("mac")) {
            return "librime.dylib";
        }
        if (osName.contains("linux")) {
            return "librime.so";
        }
        throw new IllegalArgumentException("unsupported operating system: " + System.getProperty("os.name"));
    }

    private static File detectSystemUserDirectory() {
        String osName = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT);
        List<File> candidates = new ArrayList<>();
        File home = new File(System.getProperty("user.home", "."));

        if (osName.contains("win")) {
            String appData = System.getenv("APPDATA");
            File roaming = appData == null || appData.trim()
                .isEmpty() ? new File(home, "AppData/Roaming") : new File(appData);
            candidates.add(new File(roaming, "Rime"));
            candidates.add(new File(roaming, "Moqi/Rime"));
        } else if (osName.contains("mac")) {
            candidates.add(new File(home, "Library/Rime"));
        } else if (osName.contains("linux")) {
            String dataHome = environmentPath("XDG_DATA_HOME", new File(home, ".local/share"));
            String configHome = environmentPath("XDG_CONFIG_HOME", new File(home, ".config"));
            candidates.add(new File(dataHome, "fcitx5/rime"));
            candidates.add(new File(configHome, "ibus/rime"));
            candidates.add(new File(home, ".var/app/org.fcitx.Fcitx5/data/fcitx5/rime"));
        }

        for (File candidate : candidates) {
            if (candidate.isDirectory()) {
                return candidate;
            }
        }
        return null;
    }

    private static String environmentPath(String variable, File fallback) {
        String value = System.getenv(variable);
        return value == null || value.trim()
            .isEmpty() ? fallback.getPath() : value;
    }

    private static List<String> parseModules(String configuredModules) {
        Set<String> modules = new LinkedHashSet<>();
        for (String item : configuredModules.split(",")) {
            String module = item.trim();
            if (module.isEmpty()) {
                continue;
            }
            if (!module.matches("[A-Za-z0-9_-]+")) {
                throw new IllegalArgumentException("invalid librime module name: " + module);
            }
            modules.add(module);
        }
        return new ArrayList<>(modules);
    }
}
