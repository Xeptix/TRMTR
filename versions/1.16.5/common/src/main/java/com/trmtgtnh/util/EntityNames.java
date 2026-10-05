package com.trmtgtnh.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

/**
 * Every registered entity's name, in the one spelling this mod compares them by.
 *
 * <p>
 * Asked when somebody types a mob's name into the settings and it has to be resolved against what
 * the pack actually contains - exactly, if possible, and ignoring case if not.
 *
 * <p>
 * <strong>Both older editions ask Forge for this and get a slightly different thing.</strong> There
 * the walk is over {@code ForgeRegistries.ENTITIES} reading {@code EntityEntry.getName()}, while the
 * lookup that eventually uses the answer is fed {@code EntityList.getEntityString(entity)} - two
 * sources that usually agree and are not guaranteed to. Here both come from the same registry and
 * the same {@code toString()}, so a name that resolves is a name that will match.
 *
 * <p>
 * No loader is involved: the entity registry is vanilla's at this version, and Forge's own is a view
 * onto it.
 */
public final class EntityNames {

    private EntityNames() {}

    /** Every registered entity's name, as {@code namespace:path}. */
    public static List<String> all() {
        List<String> names = new ArrayList<String>();
        for (ResourceLocation known : Registry.ENTITY_TYPE.keySet()) {
            if (known != null) names.add(known.toString());
        }
        return Collections.unmodifiableList(names);
    }
}
