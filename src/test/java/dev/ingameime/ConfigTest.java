package dev.ingameime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
