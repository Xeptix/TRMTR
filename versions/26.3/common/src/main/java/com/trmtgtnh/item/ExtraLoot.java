package com.trmtgtnh.item;

import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

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
    public static void offer(Map<Identifier, JsonElement> parsed) {
        if (parsed == null || parsed.isEmpty()) return;

        Set<Identifier> known = parsed.keySet();
        for (Map.Entry<Identifier, JsonElement> each : parsed.entrySet()) {
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

    /**
     * Whether a registry's folder is the loot tables' own - {@code loot_table}, singular, the folder the registry's name
     * gives (Registries.elementsDirPath), which is what every edition from 1.20.5 has asked.
     */
    public static boolean isLootTables(net.minecraft.resources.FileToIdConverter lister) {
        return lister != null && net.minecraft.core.registries.Registries
            .elementsDirPath(net.minecraft.world.level.storage.loot.LootDataType.TABLE.registryKey())
            .equals(lister.prefix());
    }

    /**
     * The loot tables' folder read into text and offered whole, as 26.2's scan offered it: every table's text by its id,
     * the finds added in place, announced once.
     *
     * <p>
     * From 26.3 the tables are a reloadable registry read by the game's registry loader
     * (resources/ResourceManagerRegistryLoadTask, read from 26.3): the folder is listed into a map of files, then each
     * file is read and parsed into a table on its own, in parallel. So each loader's mixin hands this the listed files
     * as they arrive, before any is parsed - the whole folder known, no table read - and then hands each file's parse
     * its text from what this returns ({@link #asResource}) in place of the file. A file that does not read here is left
     * out, and the game's own parse reads it again and says why, as it always has.
     *
     * @param files the folder's files by their path, as the loader listed them
     * @return the text offered, by table id, with this mod's finds in it
     */
    public static Map<Identifier, JsonElement> offerFolder(net.minecraft.resources.FileToIdConverter lister,
        Map<Identifier, net.minecraft.server.packs.resources.Resource> files) {
        return offerFolder(lister, files, java.util.function.UnaryOperator.identity());
    }

    /**
     * The same, each file's text first put through a loader's own reading of it: what it answers is what is offered,
     * and a file it answers null for is left out - not offered, not known - and left to the game's parse as it is. The
     * NeoForge module passes its conditions here, as 26.2's scan weighed them before anything was offered.
     */
    public static Map<Identifier, JsonElement> offerFolder(net.minecraft.resources.FileToIdConverter lister,
        Map<Identifier, net.minecraft.server.packs.resources.Resource> files,
        java.util.function.UnaryOperator<JsonElement> kept) {
        Map<Identifier, JsonElement> parsed = new java.util.HashMap<Identifier, JsonElement>();
        if (files == null) return parsed;
        for (Map.Entry<Identifier, net.minecraft.server.packs.resources.Resource> each : files.entrySet()) {
            try (java.io.Reader reader = each.getValue()
                .openAsReader()) {
                JsonElement text = kept.apply(net.minecraft.util.StrictJsonParser.parse(reader));
                if (text != null) parsed.put(lister.fileToId(each.getKey()), text);
            } catch (Exception unread) {
                // The game's own parse reads it again and reports it.
            }
        }
        offer(parsed);
        return parsed;
    }

    /**
     * A file whose text is the offered text, from the same pack - its id, its known pack and its metadata the file's own
     * - for the game's parse to read in place of the file it listed.
     */
    public static net.minecraft.server.packs.resources.Resource asResource(
        final net.minecraft.server.packs.resources.Resource file, JsonElement text) {
        final byte[] bytes = text.toString()
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new net.minecraft.server.packs.resources.Resource(file.source(),
            () -> new java.io.ByteArrayInputStream(bytes), file::metadata);
    }
}
