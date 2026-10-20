package com.trmtgtnh.erosion;

import java.util.ArrayList;
import java.util.List;

import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;

import com.trmtgtnh.Trmt;

/**
 * The creatures and things of a chunk saved before 1.18, carried through the game's own upgrade of that chunk
 * (0.9.227).
 *
 * <p>
 * <strong>Found opening a 1.16.5 world with a Golem of Ways in it</strong>: the golem was gone, and with it every
 * other entity in its chunk - the pigs, the item frames, the dropped items - with nothing in the log. Proved by running
 * 1.20.1's own chunk fixer over the chunk as 1.16.5 saved it, and this version's does the same (its test runs it over a
 * chunk of that shape). Carried from 1.21.1; what 26.1 moved is said where it is met. The fix at data version 2842 (1.18's
 * {@code ChunkRenamesFix}, schema V2842) moves {@code Level.Entities} to the chunk's root as {@code entities}. It reads
 * that list with the game's entity type, which knows only vanilla's ids, so one entry with an id of a mod's own fails
 * the whole list; the field is optional, so the failure silently becomes "no list", and the fix then removes the raw
 * {@code Entities} it left behind. The whole list goes, vanilla entries and all. Block entities are read another way
 * and survive, and so does this mod's wear, which sits at the root beside {@code DataVersion}.
 *
 * <p>
 * So the list is copied before the upgrade ({@link #keep}, at the head of {@code SimpleRegionStorage.upgradeChunkTag})
 * and put back after it ({@link #restore}, at its return) when the upgrade has left none - each entity put through the
 * game's fixer on its own, so a vanilla one is upgraded as the chunk's fix would have upgraded it, and one the game
 * cannot read comes back exactly as it was saved, for its own mod to read. The two calls are made by one mixin in each
 * loader module, on one thread for one call of that method, which is why what was kept is held per thread.
 *
 * <p>
 * After 1.21.1 {@code ChunkStorage}'s upgrade moved into {@code SimpleRegionStorage}, which upgrades the entity and
 * point-of-interest files through the same method; neither holds a {@code Level} compound, so nothing is kept for them.
 * And 26.1 upgrades some chunks of a world older than 1.13 in two steps: a file fix ({@code LegacyStructureFileFix})
 * brings each chunk holding old structure data to data version 1493 first, through this method with that target, and
 * the chunk is upgraded the rest of the way when it loads. The first step keeps the old list where it was, so nothing
 * is put back then; the second is the one that drops it, and the one {@link #restore} answers.
 */
public final class LegacyChunkEntities {

    /** The data version whose chunk fix drops the list: 1.18's, the first that keeps entities at the root. */
    static final int ENTITIES_LEAVE_LEVEL = 2842;

    /** What was copied at the head, for the return of the same call on the same thread. */
    private static final ThreadLocal<Kept> KEPT = new ThreadLocal<Kept>();

    private LegacyChunkEntities() {}

    /** The list as it was saved, the version it was saved at, and the chunk it was saved in, for the log line. */
    private record Kept(ListTag entities, int version, int chunkX, int chunkZ) {}

    /**
     * Copies a chunk's entities before the game upgrades it, when it was saved before 1.18 and has any.
     *
     * <p>
     * Anything kept before is dropped first, whatever this chunk holds: an upgrade that threw leaves its copy behind,
     * and it must never be put into another chunk. The copy is a deep one, because the upgrade may change the tag it is
     * handed.
     */
    public static void keep(CompoundTag raw) {
        KEPT.remove();
        try {
            if (raw == null) return;
            // Read as the game reads it before upgrading: a chunk with no version is older than versions, -1.
            int version = NbtUtils.getDataVersion(raw, -1);
            if (version >= ENTITIES_LEAVE_LEVEL) return;
            if (!(raw.get("Level") instanceof CompoundTag level)) return;
            if (!(level.get("Entities") instanceof ListTag entities) || entities.isEmpty()) return;
            // A list holds tags of any type from 1.21.5, so there is no element type to ask: restore takes the
            // compounds in it, which is all a saved entity list ever held. A read with a default from 1.21.5 too.
            KEPT.set(new Kept(entities.copy(), version, level.getIntOr("xPos", 0), level.getIntOr("zPos", 0)));
        } catch (RuntimeException failed) {
            KEPT.remove();
            Trmt.LOG.warn("Could not copy a chunk's entities before upgrading it; it is upgraded as the game would", failed);
        }
    }

    /**
     * Puts the kept entities back into the upgraded chunk, if the upgrade left it none.
     *
     * <p>
     * Nothing is done when the upgraded chunk holds entities of its own - the upgrade kept them, as it does for a chunk
     * of vanilla entities alone - or still holds the old list, or has not reached 1.18's version yet (a partial upgrade,
     * which the next one finishes, and which keeps the list itself until then). Each entity is upgraded from the
     * version it was saved at to the version the chunk was upgraded to, which is this game's.
     */
    public static void restore(CompoundTag fixed) {
        Kept kept = KEPT.get();
        KEPT.remove();
        if (kept == null || fixed == null) return;
        try {
            if (holdsAList(fixed.get("entities"))) return;
            if (fixed.get("Level") instanceof CompoundTag level && holdsAList(level.get("Entities"))) return;
            int target = NbtUtils.getDataVersion(fixed, currentVersion());
            if (target < ENTITIES_LEAVE_LEVEL) return;

            DataFixer fixer = DataFixers.getDataFixer();
            Type<?> saved = fixer.getSchema(DataFixUtils.makeKey(kept.version()))
                .getType(References.ENTITY_TREE);
            ListTag back = new ListTag();
            List<String> ids = new ArrayList<String>();
            for (Tag element : kept.entities()) {
                if (!(element instanceof CompoundTag entity)) continue;
                ids.add(entity.getStringOr("id", ""));
                // One the game cannot read - an id it does not know - is put back exactly as it was saved, for its own
                // mod to read. The fixer would answer it the same way, with its input, but only after logging the failed
                // read at error level in words that name nothing ("Index mismatch: 2 != 4" in 1.20.1's test),
                // once per such entity per chunk; the read it would fail is asked here first, with the same type.
                if (saved.readTyped(new Dynamic<Tag>(NbtOps.INSTANCE, entity)).result().isEmpty()) {
                    back.add(entity);
                    continue;
                }
                // The game's own upgrade for one entity and its passengers, as the chunk's would have given it.
                Dynamic<Tag> upgraded = fixer.update(References.ENTITY_TREE, new Dynamic<Tag>(NbtOps.INSTANCE, entity),
                    kept.version(), target);
                back.add(upgraded.getValue() instanceof CompoundTag compound ? compound : entity);
            }
            fixed.put("entities", back);
            Trmt.LOG.info(
                "Kept {} entities of chunk {}, {} through its upgrade from data version {}, which would have dropped them "
                    + "all: {}",
                Integer.valueOf(back.size()),
                Integer.valueOf(kept.chunkX()),
                Integer.valueOf(kept.chunkZ()),
                Integer.valueOf(kept.version()),
                named(ids));
        } catch (RuntimeException failed) {
            // Put back only once every entity is upgraded, so a failure leaves the chunk as the game upgraded it.
            Trmt.LOG.warn(
                "Could not put back the entities of chunk {}, {} after its upgrade; it loads as the game upgraded it",
                Integer.valueOf(kept.chunkX()),
                Integer.valueOf(kept.chunkZ()),
                failed);
        }
    }

    /** How many ids the log line names before it counts the rest: a chunk under an item farm can hold thousands. */
    static final int NAMED = 24;

    /** The ids for the log line, every one up to {@link #NAMED} and a count of the rest. */
    static String named(List<String> ids) {
        String shown = String.join(", ", ids.subList(0, Math.min(ids.size(), NAMED)));
        return ids.size() > NAMED ? shown + " and " + (ids.size() - NAMED) + " more" : shown;
    }

    /** Whether a tag is a list with anything in it. */
    private static boolean holdsAList(Tag tag) {
        return tag instanceof ListTag list && !list.isEmpty();
    }

    /**
     * This game's data version, read as the game reads it when it upgrades a chunk: {@code dataVersion().version()} from
     * 1.21.6, where {@code WorldVersion} became plain accessors; the same number.
     */
    static int currentVersion() {
        return SharedConstants.getCurrentVersion()
            .dataVersion()
            .version();
    }
}
