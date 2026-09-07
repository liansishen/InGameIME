package dev.ingameime.rime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Locale;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import dev.ingameime.Config;

public class RimeRuntimeConfigTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private boolean autoDetectSystemData;
    private String nativeLibraryDirectory;
    private String sharedDataDirectory;
    private String userDataDirectory;

    @Before
    public void rememberConfig() {
        autoDetectSystemData = Config.autoDetectSystemData;
        nativeLibraryDirectory = Config.nativeLibraryDirectory;
        sharedDataDirectory = Config.sharedDataDirectory;
        userDataDirectory = Config.userDataDirectory;
    }

    @After
    public void restoreConfig() {
        Config.autoDetectSystemData = autoDetectSystemData;
        Config.nativeLibraryDirectory = nativeLibraryDirectory;
        Config.sharedDataDirectory = sharedDataDirectory;
        Config.userDataDirectory = userDataDirectory;
    }

    @Test
    public void emptyPathsResolveInsideTheGameInstance() throws Exception {
        File gameDirectory = temporaryFolder.newFolder("game");
        File nativeDirectory = new File(gameDirectory, "ingameime/native");
        File sharedDirectory = new File(gameDirectory, "ingameime/shared");
        File userDirectory = new File(gameDirectory, "ingameime/user");
        assertTrue(nativeDirectory.mkdirs());
        assertTrue(sharedDirectory.mkdirs());
        assertTrue(userDirectory.mkdirs());
        assertTrue(new File(nativeDirectory, platformLibraryName()).createNewFile());
        Config.autoDetectSystemData = false;
        Config.nativeLibraryDirectory = "";
        Config.sharedDataDirectory = "";
        Config.userDataDirectory = "";

        RimeRuntimeConfig config = RimeRuntimeConfig.resolve(gameDirectory);

        assertEquals(
            nativeDirectory.getAbsolutePath(),
            config.getNativeLibrary()
                .getParentFile()
                .getAbsolutePath());
        assertEquals(
            sharedDirectory.getAbsolutePath(),
            config.getSharedDataDirectory()
                .getAbsolutePath());
        assertEquals(
            userDirectory.getAbsolutePath(),
            config.getUserDataDirectory()
                .getAbsolutePath());
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
        return "librime.so";
    }
}
