package dev.ingameime.client;

import net.minecraft.client.gui.GuiScreen;

import cpw.mods.fml.common.Loader;

final class InputTargets {

    private static final boolean NEI_LOADED = Loader.isModLoaded("NotEnoughItems");
    private static final boolean AE2_LOADED = Loader.isModLoaded("appliedenergistics2");
    private static final boolean MODULAR_UI_LOADED = Loader.isModLoaded("modularui");
    private static final boolean MODULAR_UI_2_LOADED = Loader.isModLoaded("modularui2");
    private static final boolean BIBLIOCRAFT_LOADED = Loader.isModLoaded("BiblioCraft");
    private static final boolean CLIPBOARD_ANYWHERE_LOADED = Loader.isModLoaded("clipboardanywhere");

    private InputTargets() {}

    static InputTarget find(Object screen) {
        if (!(screen instanceof GuiScreen)) {
            return null;
        }

        InputTarget target;
        if (CLIPBOARD_ANYWHERE_LOADED && (target = ClipboardAnywhereInputTarget.find(screen)) != null) {
            return target;
        }
        if (NEI_LOADED && (target = NeiInputTarget.find(screen)) != null) {
            return target;
        }
        if (AE2_LOADED && (target = Ae2InputTarget.find(screen)) != null) {
            return target;
        }
        if (MODULAR_UI_2_LOADED && (target = ModularUi2InputTarget.find()) != null) {
            return target;
        }
        if (MODULAR_UI_LOADED && (target = ModularUiInputTarget.find(screen)) != null) {
            return target;
        }
        if (BIBLIOCRAFT_LOADED && (target = BiblioInputTarget.find(screen)) != null) {
            return target;
        }
        return VanillaInputTarget.find((GuiScreen) screen);
    }
}
