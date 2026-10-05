package com.trmtgtnh.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * One loot pool, while it is still the text a pack wrote it as.
 *
 * <p>
 * <strong>This is what Forge's mutable {@code LootPool} became, and it is a replacement rather than
 * a carry.</strong> 1.12.2 adds a find by handing Forge's {@code LootTableLoadEvent} a new
 * {@code LootEntryItem} - but the entry list, the pool array and a table's pools are all private and
 * final in vanilla, and the methods that edit them are Forge's own patches. Fabric has nothing of
 * the sort; what mods there use is Fabric API's own loot callback, which is a dependency this
 * edition has turned down everywhere else.
 *
 * <p>
 * So the find is added a moment earlier, while a table is still a {@code JsonObject} on its way into
 * the loader. Everything vanilla can express in a loot table it can express in that text, by
 * definition, so nothing is given up by writing the entry rather than constructing it - and it costs
 * neither loader an API, which is the whole of why it is done this way. {@code ExtraLoot} is what
 * hands these out.
 *
 * <h2>Which pool</h2>
 *
 * <p>
 * The first, which is what the older edition's {@code getPool("main")} asks for: a pool has no name
 * in vanilla's own files and Forge invents one for each as it reads them, {@code main} being the name
 * it gives the first. So "the main pool" and "the first pool" are the same pool, and this is the
 * second phrasing of it.
 *
 * <h2>What a reload does</h2>
 *
 * <p>
 * Nothing that needs guarding against. The older edition has to name its entries so a second load of
 * the same table replaces this mod's entry rather than stacking a second beside it; a reload here
 * re-reads every table from disk, so what this writes into is always a fresh copy of the pack's own
 * text and there is nothing of ours in it yet.
 */
public final class LootSack {

    /** The entry list this writes into - the pool's own, in place. */
    private final JsonArray entries;

    private LootSack(JsonArray entries) {
        this.entries = entries;
    }

    /**
     * The first pool of one table, or null for a table that has no pool to add to.
     *
     * <p>
     * A table with no pools is a legitimate thing - {@code minecraft:empty} is one - and so is a
     * table a pack has written in some shape this does not recognise. Both are left exactly as they
     * are rather than repaired.
     */
    public static LootSack firstPoolOf(JsonObject table) {
        if (table == null || !table.has("pools")) return null;
        if (!table.get("pools")
            .isJsonArray()) return null;
        JsonArray pools = table.getAsJsonArray("pools");
        if (pools.size() == 0) return null;
        if (!pools.get(0)
            .isJsonObject()) return null;
        JsonObject first = pools.get(0)
            .getAsJsonObject();
        if (!first.has("entries")) {
            // A pool is allowed to have been written without one; giving it an empty list is the
            // same table it already was and gives this something to append to.
            first.add("entries", new JsonArray());
        }
        if (!first.get("entries")
            .isJsonArray()) return null;
        return new LootSack(first.getAsJsonArray("entries"));
    }

    /**
     * Adds one stack as an entry of the given weight, and says whether it went in.
     *
     * <p>
     * The stack's own NBT travels with it where it has any - a tamper's grade, or the enchantment on
     * a book - as vanilla's {@code set_nbt} function, whose argument is the tag written out as text.
     * That is the same function the other edition hands its entry, and it is the whole of why that
     * edition needed no {@code PreparedBook} either: the stack described is the stack handed over.
     */
    public boolean addItem(ItemStack stack, int weight) {
        if (stack == null || stack.isEmpty() || weight <= 0) return false;
        ResourceLocation named = Registry.ITEM.getKey(stack.getItem());
        if (named == null) return false;

        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", named.toString());
        entry.addProperty("weight", Integer.valueOf(weight));

        if (stack.hasTag()) {
            JsonObject function = new JsonObject();
            function.addProperty("function", "minecraft:set_nbt");
            function.addProperty(
                "tag",
                stack.getTag()
                    .toString());
            JsonArray functions = new JsonArray();
            functions.add(function);
            entry.add("functions", functions);
        }

        entries.add(entry);
        return true;
    }
}
