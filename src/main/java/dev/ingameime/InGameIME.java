package dev.ingameime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = InGameIME.MOD_ID,
    version = Tags.VERSION,
    name = InGameIME.MOD_NAME,
    acceptedMinecraftVersions = "[1.7.10]",
    guiFactory = InGameIME.GUI_FACTORY,
    acceptableRemoteVersions = "*")
public final class InGameIME {

    public static final String MOD_ID = "ingameime";
    public static final String MOD_NAME = "InGameIME";
    public static final String GUI_FACTORY = "dev.ingameime.client.gui.InGameImeGuiFactory";
    public static final Logger LOG = LogManager.getLogger(MOD_ID);

    @SidedProxy(clientSide = "dev.ingameime.ClientProxy", serverSide = "dev.ingameime.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
