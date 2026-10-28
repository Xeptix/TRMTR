package com.trmtgtnh.client.gui;

import java.util.Set;

import net.minecraft.client.gui.GuiScreen;

import cpw.mods.fml.client.IModGuiFactory;

/**
 * Hooks the settings screen into Forge's mod list, so the per-client toggle is two clicks from
 * the main menu rather than a file edit.
 */
public class TrmtGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(net.minecraft.client.Minecraft minecraft) {}

    /**
     * The screen Config opens: the mod's own, or Forge's where {@code client.settingsScreen} says {@code other}
     * (0.9.228). Forge asks this at every press of Config and builds the class with its {@code (GuiScreen)}
     * constructor, so a change to the setting takes at the next press without a restart.
     */
    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return ConfigScreens.screenClass();
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
