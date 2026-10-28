package com.trmtgtnh.client.gui;

import cpw.mods.fml.client.config.ConfigGuiType;

/**
 * Which rows of Forge's array editor are this mod's own, so a row added there is one of them (0.9.228).
 *
 * <p>
 * Forge's array editor builds the rows a list holds when it opens from the setting's own row class - for this mod's
 * block lists {@link BlockIconArrayEntry}, for the mobs list {@link MobIconArrayEntry}, each drawing its picture - and
 * a row added with its "+" always as a plain text row, which draws none. So a block or a mob typed into a new row had
 * no picture until the list was closed and opened again (Xep, 2026-10-20, in GT New Horizons). The mixin on that add
 * ({@code MixinNewArrayRow}) asks this which class the new row should be, and is otherwise Forge's own: a list of
 * another mod's, or of any kind but words, is left exactly as Forge builds it.
 */
public final class ArrayRows {

    private ArrayRows() {}

    /**
     * The row class a row added to this list should be: the setting's own where it is one of this mod's and the list
     * is of words, else null for Forge's plain row.
     */
    public static Class<?> ownRowClass(Class<?> entryClass, ConfigGuiType type) {
        if (entryClass == null || type != ConfigGuiType.STRING) return null;
        return entryClass.getName()
            .startsWith("com.trmtgtnh.") ? entryClass : null;
    }
}
