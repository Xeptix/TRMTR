package com.trmtgtnh.client.settings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The surfaces the Presets tab's Advanced fold gives rows to (0.9.229, W4 T17; Xep, 2026-11-09: "Rows only for held
 * families"). Each remembered preset has a row per surface that wears in stages, and a surface the running pack holds
 * no block of has nothing there to remember a number for: so a family's rows show only when the pack holds a block of
 * it. Flesh, taint and cloud come by other mods' data files alone, so a vanilla player's fold never grows by their rows
 * and stays whole in one frame at the smallest GUI scale (AdvancedFoldIsPhotographedWholeTest); sculk is vanilla's from
 * 1.19, so a vanilla game there holds it and its fold has four rows more (0.9.229, W4). A hidden family's
 * remembered numbers are kept as written: an edit to another row writes its own entry and leaves the rest alone.
 *
 * <p>
 * The same file in every edition; each edition's surface registry says which families its table holds a block of.
 */
public final class FoldFamilies {

    private FoldFamilies() {}

    /** The families that wear in stages, in the enum's order, that {@code held} holds a block of. */
    public static List<SurfaceFamily> shown(Collection<SurfaceFamily> held) {
        List<SurfaceFamily> out = new ArrayList<SurfaceFamily>();
        for (SurfaceFamily family : SurfaceFamily.staged()) {
            if (held.contains(family)) out.add(family);
        }
        return Collections.unmodifiableList(out);
    }
}
