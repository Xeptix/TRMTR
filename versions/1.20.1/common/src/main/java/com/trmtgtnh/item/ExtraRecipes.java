package com.trmtgtnh.item;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

import com.trmtgtnh.Trmt;

/**
 * This mod's own recipes, and how they reach a game that loads recipes from files.
 *
 * <p>
 * <strong>This is the "different road" the port plan names, and it is the only part of the recipes
 * that is not a carry.</strong> Both older editions push recipes into a registry: 1.7.10 calls
 * {@code GameRegistry.addRecipe} and 1.12.2 registers into {@code ForgeRegistries.RECIPES}. Neither
 * exists here. Recipes are datapack content at this version, parsed into {@code RecipeManager} on
 * every reload, and there is no event either loader offers for adding one.
 *
 * <p>
 * Shipping them as JSON instead is not open to this mod, and the reason is the whole character of
 * the thing: <strong>its recipes are built from what the pack actually contains.</strong> A tamper
 * exists at every grade whose metal the pack supplies, the GregTech shape is used only where the
 * pack has the tools to forge it, and the compressed-earth variants appear only where compressed
 * earth does. None of that can be written down in advance, because none of it is known until the
 * pack is assembled.
 *
 * <p>
 * So the recipes are built in code, as they always were, and added to the manager's map after it has
 * parsed the files. Each loader mixes into {@code RecipeManager} and calls {@link #added}; the
 * bodies are identical and live apart only because a mixin naming a method is written into a refmap
 * in one loader's names and the other refuses it - the same arrangement {@code MixinBlockArrivals}
 * is under.
 *
 * <p>
 * <strong>A datapack still wins.</strong> Anything already parsed under a given id is left exactly
 * as it is, so a pack that ships its own {@code trmtgtnh:tamper_iron} keeps it, and this adds only
 * what nothing has claimed. That is the behaviour the older editions have by accident - a registry
 * refuses a duplicate name - and it is worth keeping on purpose.
 */
public final class ExtraRecipes {

    private ExtraRecipes() {}

    /** What this mod wants added, built once when the recipes are first asked for. */
    private static Map<ResourceLocation, Recipe<?>> ours;

    /**
     * A {@code RecipeManager} that can be asked to take this mod's recipes again.
     *
     * <p>
     * <strong>Because the first time it is asked is too early, and that cost this edition every
     * tamper recipe it has.</strong> The recipes are built from what the pack contains, and what the
     * pack contains is read from tags - {@code ingotIron} means {@code forge:ingots/iron} here. At
     * the tail of {@code RecipeManager.apply}, where the mixin injects, those tags are not bound
     * yet: the collections are published after the whole datapack reload finishes. So every grade
     * reported that the pack had no metal for it, nought tamper recipes were built, and the mod said
     * so in a line written for a pack that genuinely has no metals. Measured rather than reasoned
     * about in the end - the same question asked at build time read nought items under
     * {@code ingotIron} and, with the world open, one.
     *
     * <p>
     * {@code ServerEvents.serverStarted} is where it is asked again, and that method's own javadoc
     * had the answer in it all along: the trophy and quest files are written there rather than at
     * the end of loading precisely because "which tamper grades this pack can make is decided by
     * tags, which are not loaded until a server starts". The recipes wanted the same moment.
     *
     * <p>
     * Only the first reload of a session needs it. A later {@code /reload} runs {@code apply} with
     * the previous binding still in place, so the build finds its metals and this changes nothing.
     */
    public interface Rebuildable {

        void trmt$rebuildTrmtRecipes();
    }

    /**
     * Builds this mod's recipes again and puts them back, replacing whatever the last build added.
     *
     * <p>
     * The ids this mod last added are taken out first. Without that, {@link #added} would find them
     * already in the map, read them as a datapack's own and keep them - which is right for a pack's
     * recipe and wrong for the mod's own stale one.
     */
    public static Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> again(
        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> current) {
        if (current == null) return current;
        Map<ResourceLocation, Recipe<?>> stale = ours;
        if (stale == null || stale.isEmpty()) return added(current);

        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> without = new HashMap<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>();
        for (Map.Entry<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> each : current.entrySet()) {
            Map<ResourceLocation, Recipe<?>> row = new LinkedHashMap<ResourceLocation, Recipe<?>>(each.getValue());
            row.keySet()
                .removeAll(stale.keySet());
            without.put(each.getKey(), row);
        }
        return added(without);
    }

    /**
     * The manager's map with this mod's recipes added to it.
     *
     * <p>
     * A fresh map rather than a mutation, because the one the manager has just built is immutable
     * and the one it keeps must stay so: a recipe book iterating a map something else is writing to
     * is a crash a long way from here.
     *
     * @param parsed what the manager has loaded from files, by type and then by id
     * @return the map it should keep
     */
    public static Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> added(
        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> parsed) {
        if (parsed == null) return parsed;
        // Not before the tags are readable, and this is the only moment in the mod that has to say
        // so: what is built here is built from what the pack contains, and at the tail of a reload
        // that cannot be asked. A named tag refuses to be read at all before it is bound - "Tag
        // forge:ingots/iron used before it was bound" - so a build attempted here does not merely
        // answer wrongly, it throws part way through and leaves the recipe book short of everything
        // after the first metal. ServerEvents rebuilds on the tick the tags turn up, which is before
        // any player can have joined and therefore before anybody is sent a recipe book at all.
        if (!com.trmtgtnh.util.OreNames.tagsArrived()) {
            Trmt.LOG.info(
                "Holding this mod's recipes back until the tags are bound, which is a moment after this "
                    + "one; they are built on the first tick that can read them.");
            return parsed;
        }
        Map<ResourceLocation, Recipe<?>> mine = build();
        if (mine.isEmpty()) return parsed;

        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> out = new HashMap<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>();
        for (Map.Entry<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> each : parsed.entrySet()) {
            out.put(each.getKey(), new LinkedHashMap<ResourceLocation, Recipe<?>>(each.getValue()));
        }

        int added = 0;
        int kept = 0;
        for (Map.Entry<ResourceLocation, Recipe<?>> each : mine.entrySet()) {
            RecipeType<?> type = each.getValue()
                .getType();
            Map<ResourceLocation, Recipe<?>> row = out.get(type);
            if (row == null) {
                row = new LinkedHashMap<ResourceLocation, Recipe<?>>();
                out.put(type, row);
            }
            // A datapack that has claimed this id keeps it. See the class note.
            if (row.containsKey(each.getKey())) {
                kept++;
                continue;
            }
            row.put(each.getKey(), each.getValue());
            added++;
        }

        Trmt.LOG.info(
            "Added {} recipe(s) the pack's own contents decide, and left {} that a datapack had already "
                + "claimed. Nought added where a pack supplies no metal this mod can make a tamper from, "
                + "which is a quiet answer rather than a fault - /trmt says what it found. (built on {}, "
                + "with ingotIron reading {} item(s))",
            new Object[] { Integer.valueOf(added), Integer.valueOf(kept), Thread.currentThread()
                .getName(),
                Integer.valueOf(
                    com.trmtgtnh.util.OreNames.itemsFor("ingotIron")
                        .size()) });

        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> sealed = new HashMap<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>();
        for (Map.Entry<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> each : out.entrySet()) {
            sealed.put(each.getKey(), java.util.Collections.unmodifiableMap(each.getValue()));
        }
        return java.util.Collections.unmodifiableMap(sealed);
    }

    /**
     * Builds them, once per reload.
     *
     * <p>
     * Rebuilt rather than cached across reloads on purpose: what the pack contains is exactly what a
     * reload may have changed, and a tamper grade that has just appeared should bring its recipe
     * with it.
     */
    private static Map<ResourceLocation, Recipe<?>> build() {
        ours = new LinkedHashMap<ResourceLocation, Recipe<?>>();
        try {
            ModRecipes.register((id, recipe) -> ours.put(id, recipe));
        } catch (RuntimeException awkward) {
            // One bad recipe must not cost a world its whole recipe book. Whatever was collected
            // before the throw is kept, and the rest is named.
            Trmt.error("Stopped building this mod's recipes part way through", awkward);
        }
        return ours;
    }

    /** Where a built recipe goes. */
    public interface Collector {

        void accept(ResourceLocation id, Recipe<?> recipe);
    }
}
