package com.trmtgtnh.client.gui;

import java.util.Collections;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;

/**
 * Hooks the config screen into Forge's mod list, so the per-client toggle is two clicks from the
 * main menu rather than a file edit.
 *
 * <p>
 * Hand-written rather than carried. The other edition's version of this names the screen's class and
 * lets Forge build it reflectively, and answers a fourth method about runtime option handlers; 1.12.2
 * asks instead whether there is a screen at all and then for one, built here. Four lines either way,
 * and none of them the same four.
 */
public class TrmtGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraftInstance) {}

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parentScreen) {
        return new TrmtConfigGui(parentScreen);
    }

    /**
     * None. An empty set rather than null, which is what the other edition answers: Forge iterates
     * this without checking, and a null here is a crash on opening the mod list rather than on
     * opening this mod's screen.
     */
    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return Collections.emptySet();
    }
}
