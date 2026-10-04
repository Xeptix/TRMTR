package com.trmtgtnh.client.gui;

import java.util.Set;

import net.minecraft.client.gui.GuiScreen;

import cpw.mods.fml.client.IModGuiFactory;

/**
 * Hooks the config screen into Forge's mod list, so the per-client toggle is two clicks from
 * the main menu rather than a file edit.
 */
public class TrmtGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(net.minecraft.client.Minecraft minecraft) {}

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return TrmtConfigGui.class;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        return null;
    }
}
