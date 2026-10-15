package com.trmtgtnh.item;

import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

import com.trmtgtnh.Trmt;

/**
 * This mod's finds, and how they reach a chest at this version.
 *
 * <p>
 * <strong>The same shape as {@link ExtraRecipes}, for the same reason, and the two should be read
 * together.</strong> A loot table is pack content; the loader parses every one of them on a reload
 * and keeps the result; and what each loader offers for editing one is its own. Forge has
 * {@code LootTableLoadEvent}, which is what the 1.12.2 edition uses; Fabric has nothing in vanilla
 * and mods there use Fabric API's loot callback, which is a dependency this edition has turned down
 * everywhere else.
 *
 * <p>
 * So the find is added one step earlier, while the table is still the text a pack wrote - see
 * {@link LootSack} for why that gives nothing up. A mixin per loader hands this the map of parsed
 * text before it is read, and the bodies live apart for the refmap reason {@code MixinBlockArrivals}
 * is already under.
 *
 * <p>
 * <strong>Every table is handed over, not only vanilla's.</strong> {@code ModLoot} decides which
 * ones it wants by name, and the names it is willing to be pointed at are a setting - so this is
 * also the answer to a question the 1.12.2 edition had to work at: whether a table a pack names is
 * real. The map handed over here <em>is</em> every table the packs supply, so the set of known names
 * is simply its keys, and a modded table can be named as readily as one of vanilla's.
 */
public final class ExtraLoot {

    private ExtraLoot() {}

    /**
     * Offers every parsed table to {@code ModLoot}, which adds what belongs in it.
     *
     * <p>
     * In place: the objects in this map are about to be read into tables and nothing else holds
     * them, so there is nothing to copy and nothing that could see a half-edited one. That is the
     * opposite of the recipe side, where the map being handed back is one the manager keeps - the
     * difference is that this runs before the parse and that one runs after it.
     *
     * <p>
     * Nothing thrown out of here would be caught by anything that could do something about it, and a
     * mod that breaks loot breaks every chest in the world - so each table is guarded on its own and
     * one this cannot add to is left exactly as it was.
     */
    public static void offer(Map<ResourceLocation, JsonElement> parsed) {
        if (parsed == null || parsed.isEmpty()) return;

        Set<ResourceLocation> known = parsed.keySet();
        for (Map.Entry<ResourceLocation, JsonElement> each : parsed.entrySet()) {
            JsonElement text = each.getValue();
            if (text == null || !text.isJsonObject()) continue;
            JsonObject table = text.getAsJsonObject();
            try {
                LootSack sack = LootSack.firstPoolOf(table);
                if (sack == null) continue;
                ModLoot.offer(each.getKey(), sack, known);
            } catch (RuntimeException awkwardTable) {
                Trmt.LOG.warn("Could not add this mod's finds to the loot table {}", each.getKey(), awkwardTable);
            }
        }
        ModLoot.announce();
    }
}
