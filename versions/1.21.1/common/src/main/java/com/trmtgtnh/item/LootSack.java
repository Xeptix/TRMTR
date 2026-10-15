package com.trmtgtnh.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.trmtgtnh.util.StackData;

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
     * What the stack remembers travels with it where it has any - a tamper's grade - as vanilla's
     * {@code set_custom_data} function, whose argument is the custom data written out as text. 1.20.5 split the old
     * {@code set_nbt} by what it set; the custom data is the part this mod writes (see {@code StackData}), so the
     * stack described is still the stack handed over, as in every edition, and none of them needs a
     * {@code PreparedBook}. An enchanted book goes in by {@link #addBook} instead.
     */
    public boolean addItem(ItemStack stack, int weight) {
        if (stack == null || stack.isEmpty() || weight <= 0) return false;
        ResourceLocation named = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (named == null) return false;

        JsonObject entry = entry(named.toString(), weight);
        CompoundTag remembered = StackData.read(stack);
        if (remembered != null) {
            JsonObject function = new JsonObject();
            function.addProperty("function", "minecraft:set_custom_data");
            function.addProperty("tag", remembered.toString());
            functions(entry).add(function);
        }

        entries.add(entry);
        return true;
    }

    /**
     * Adds an enchanted book teaching one enchantment at level one, named by the enchantment's registry name, as
     * vanilla's {@code set_enchantments} function.
     *
     * <p>
     * By name, because an enchantment is data at this version: the book cannot be made as a stack before a world has
     * loaded the enchantment it teaches, and the table names it for the game to find as the table is read. On an
     * enchanted book that function sets what the book teaches rather than what it wears, which is the book every other
     * edition's entry describes.
     */
    public boolean addBook(ResourceLocation enchantment, int weight) {
        if (enchantment == null || weight <= 0) return false;
        JsonObject entry = entry("minecraft:enchanted_book", weight);
        JsonObject levels = new JsonObject();
        levels.addProperty(enchantment.toString(), Integer.valueOf(1));
        JsonObject function = new JsonObject();
        function.addProperty("function", "minecraft:set_enchantments");
        function.add("enchantments", levels);
        functions(entry).add(function);
        entries.add(entry);
        return true;
    }

    private static JsonObject entry(String item, int weight) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", item);
        entry.addProperty("weight", Integer.valueOf(weight));
        return entry;
    }

    private static JsonArray functions(JsonObject entry) {
        if (!entry.has("functions")) entry.add("functions", new JsonArray());
        return entry.getAsJsonArray("functions");
    }
}
