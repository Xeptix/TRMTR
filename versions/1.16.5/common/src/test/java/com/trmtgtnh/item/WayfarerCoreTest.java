package com.trmtgtnh.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which chunk tamper the Wayfarer's tamper is built around.
 *
 * <p>
 * Wrong in either direction, the failure is silent. Naming a grade the pack cannot make leaves a
 * recipe NEI draws and no grid completes, which is what 0.9.211 did with a list lacking diamond.
 * Moving a pack that has diamond onto another metal changes the price of the last tool on every
 * world already playing it. So both are pinned: the two named metals wherever they can be made,
 * exactly as before, and a grade the pack can make wherever it can make any.
 *
 * <p>
 * The figures are the ones the shipped general.tamperGrades writes against each grade, except in
 * the one test about a pack's own entry. Nothing is looked up; each map stands in for what
 * ModRecipes hands over once it knows which chunk tampers got a recipe.
 */
class WayfarerCoreTest {

    private static Map<String, Integer> grades(Object... keysAndUses) {
        Map<String, Integer> map = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < keysAndUses.length; i += 2) {
            map.put((String) keysAndUses[i], (Integer) keysAndUses[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("netherite where the enhancements are on and a netherite chunk tamper can be made")
    void netheriteWhenEnhanced() {
        assertEquals(
            "netherite",
            WayfarerCore.gradeFor(true, grades("iron", 512, "gold", 256, "diamond", 2048, "netherite", 4096)));
    }

    @Test
    @DisplayName("diamond with the enhancements off, even where netherite can be made and lasts longer")
    void diamondWhenNotEnhanced() {
        assertEquals(
            "diamond",
            WayfarerCore.gradeFor(false, grades("iron", 512, "gold", 256, "diamond", 2048, "netherite", 4096)));
    }

    @Test
    @DisplayName("diamond where there is no netherite, however much longer another metal lasts")
    void diamondIsNotOutrankedByDurability() {
        // A GregTech pack on the shipped list without netherite keeps the recipe it has always had.
        assertEquals(
            "diamond",
            WayfarerCore.gradeFor(
                true,
                grades("iron", 512, "diamond", 2048, "titanium", 8192, "tungstensteel", 16384, "neutronium", 32767)));
    }

    @Test
    @DisplayName("netherite with the enhancements on and no diamond, however much longer another metal lasts")
    void netheriteIsNotOutrankedByDurability() {
        assertEquals(
            "netherite",
            WayfarerCore.gradeFor(true, grades("iron", 512, "netherite", 4096, "titanium", 8192)));
    }

    @Test
    @DisplayName("a list without a craftable diamond is built from its longest-lasting grade, not left uncraftable")
    void noDiamondFallsBackToTheLongestLasting() {
        // Plain Forge with the diamond line taken out: iron and gold are all it can make.
        assertEquals("iron", WayfarerCore.gradeFor(false, grades("iron", 512, "gold", 256)));
        assertEquals("iron", WayfarerCore.gradeFor(true, grades("iron", 512, "gold", 256)));
        assertEquals("steel", WayfarerCore.gradeFor(true, grades("iron", 512, "bronze", 768, "steel", 1536)));
    }

    @Test
    @DisplayName("with the enhancements off, netherite past diamond is one grade among the rest")
    void netheriteHasNoStandingWhenNotEnhanced() {
        assertEquals("netherite", WayfarerCore.gradeFor(false, grades("iron", 512, "netherite", 4096)));
        assertEquals("titanium", WayfarerCore.gradeFor(false, grades("netherite", 4096, "titanium", 8192)));
    }

    @Test
    @DisplayName("of grades that last equally long, the one the list names first")
    void firstOfEquals() {
        assertEquals("stainlesssteel", WayfarerCore.gradeFor(false, grades("stainlesssteel", 4096, "netherite", 4096)));
        assertEquals("netherite", WayfarerCore.gradeFor(false, grades("netherite", 4096, "stainlesssteel", 4096)));
        assertEquals("silver", WayfarerCore.gradeFor(false, grades("copper", 384, "silver", 512, "brass", 512)));
    }

    @Test
    @DisplayName("a figure written past the short ceiling still outranks the ceiling itself")
    void declaredFiguresPastTheCeiling() {
        // A pack's own entry: the list takes any figure and only a tool in use is capped at 32767.
        // Compared after the cap these two would be equal, and neutronium would win on list order.
        assertEquals("infinity", WayfarerCore.gradeFor(false, grades("neutronium", 32767, "infinity", 50000)));
    }

    @Test
    @DisplayName("nothing to build from gives no grade, rather than a name nobody can make")
    void nothingCraftable() {
        assertNull(WayfarerCore.gradeFor(true, grades()));
        assertNull(WayfarerCore.gradeFor(false, null));
    }
}
