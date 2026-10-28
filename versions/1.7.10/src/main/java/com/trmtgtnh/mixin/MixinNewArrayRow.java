package com.trmtgtnh.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.client.gui.ArrayRows;

import cpw.mods.fml.client.config.GuiEditArray;
import cpw.mods.fml.client.config.GuiEditArrayEntries;
import cpw.mods.fml.client.config.IConfigElement;

/**
 * A row added in Forge's array editor is one of the setting's own rows, so it draws its picture at once (0.9.228).
 *
 * <p>
 * Forge makes the rows a list holds when it opens from the setting's own row class, and every row added with "+" a
 * plain text row - so a mob typed into a new row of {@code multipliers.mobs}, or a block into a new row of a block
 * list, showed no picture until the list was closed and opened again (Xep, 2026-10-20, in GT New Horizons: "adding a
 * new mob doesn't show the spawn egg once you type in the correct mob name"). At the end of Forge's add, the plain row
 * it put in is swapped for one of the setting's own class, made as Forge makes the others. Only a list of this mod's
 * ({@link ArrayRows#ownRowClass}); any other mod's list is left as Forge builds it. FML's own class, so nothing is
 * remapped; and not required, so a Forge whose editor differs only goes without it.
 */
@Mixin(value = GuiEditArrayEntries.class, remap = false)
public abstract class MixinNewArrayRow {

    @Shadow
    private GuiEditArray owningGui;

    @SuppressWarnings("rawtypes")
    @Shadow
    public IConfigElement configElement;

    @Shadow
    public List<GuiEditArrayEntries.IArrayEntry> listEntries;

    @Inject(method = "addNewEntry", at = @At("RETURN"), require = 0)
    private void trmt$addOwnRow(int index, CallbackInfo info) {
        Class<?> own = ArrayRows.ownRowClass(configElement.getArrayEntryClass(), configElement.getType());
        if (own == null || index < 0 || index >= listEntries.size()) return;
        try {
            GuiEditArrayEntries.IArrayEntry made = (GuiEditArrayEntries.IArrayEntry) own
                .getConstructor(GuiEditArray.class, GuiEditArrayEntries.class, IConfigElement.class, Object.class)
                .newInstance(owningGui, (GuiEditArrayEntries) (Object) this, configElement, "");
            listEntries.set(index, made);
        } catch (Throwable unmade) {
            // Forge's plain row stays: a row without its picture, as before, rather than no row at all.
        }
    }
}
