package com.trmtgtnh.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

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
 * So the recipes are built in code, as they always were, and added to the manager after it has
 * parsed the files. Each loader mixes into {@code RecipeManager} and calls {@link #added} with the
 * manager and the world's registries it was made with, which an unlock book's enchantment is looked up
 * in from 1.21.
 *
 * <p>
 * <strong>From 26.3 recipes are a registry</strong>, loaded with the loot tables by the reloadable registries
 * (resources/RegistryDataLoader.RELOADABLE_REGISTRIES), and the manager no longer reads files: it is made from that
 * registry, once per reload, and keeps what it was made with ({@code RecipeManager(HolderLookup.Provider)}, read from
 * 26.3). So each loader's mixin calls {@link #added} at the end of that constructor - the moment, as before, the
 * manager holds the pack's recipes and the game has not yet worked out the book's pages from them
 * ({@code finalizeRecipeLoading}, which the server still calls after). What the manager holds is still what the game
 * asks for its recipes, its book and {@code /recipe}; the registry itself is asked only for the names {@code /recipe}
 * suggests, where this mod's own do not appear.
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
    private static volatile Map<Identifier, Recipe<?>> ours;

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
     * A {@code RecipeManager} that can be handed the whole of what it is to hold.
     *
     * <p>
     * 1.21.1's manager had {@code replaceRecipes} for this, and 1.21.2 took it away: the manager keeps one
     * {@code RecipeMap} in a private field, set at the end of its own load. So each loader's mixin on the manager
     * sets that field to a map made of what it is handed, which is what {@code replaceRecipes} did - the same
     * recipes, found by type and by id - and this asks for it through this.
     */
    public interface Replaceable {

        void trmt$replaceRecipes(java.util.Collection<RecipeHolder<?>> all);
    }

    /**
     * Sets what a manager holds, as 1.21.1's {@code replaceRecipes} did, or says why it could not.
     *
     * @param finish whether to work out the recipe book's pages and the grid's placements again. The game does that
     *               itself once a reload is over, from 1.21.2, so a change made inside a reload needs none; a change
     *               made afterwards, as {@link #again} makes on the first tick, does, or the book would lack every
     *               recipe added.
     */
    private static void replace(RecipeManager manager, java.util.Collection<RecipeHolder<?>> all, boolean finish) {
        if (!(manager instanceof Replaceable)) {
            Trmt.LOG.warn("The recipe manager cannot be handed this mod's recipes on this loader, so none are added");
            return;
        }
        ((Replaceable) manager).trmt$replaceRecipes(all);
        if (!finish) return;
        net.minecraft.server.MinecraftServer server = Trmt.runningServer();
        manager.finalizeRecipeLoading(server == null ? net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS
            : server.getWorldData()
                .enabledFeatures());
    }

    /**
     * Builds this mod's recipes again and puts them back, replacing whatever the last build added.
     *
     * <p>
     * The ids this mod last added are taken out first. Without that, {@link #added} would find them
     * already in the manager, read them as a datapack's own and keep them - which is right for a pack's
     * recipe and wrong for the mod's own stale one.
     */
    public static void again(RecipeManager manager, HolderLookup.Provider registries) {
        if (manager == null) return;
        Map<Identifier, Recipe<?>> stale = ours;
        List<RecipeHolder<?>> without = new ArrayList<RecipeHolder<?>>();
        for (RecipeHolder<?> each : manager.getRecipes()) {
            // A holder names its recipe by a registry key from 1.21.2; the id inside it is the one this mod files under.
            if (stale == null || !stale.containsKey(each.id()
                .identifier())) without.add(each);
        }
        List<RecipeHolder<?>> out = added(without, registries);
        // After the reload, so the book's pages and the grid's placements are worked out again here.
        replace(manager, out == null ? without : out, true);
    }

    /**
     * Puts this mod's recipes into a manager that has just read the pack's files.
     *
     * <p>
     * From 1.20.2 a manager keeps each recipe in a holder with its id beside it, by type and by id, and offers
     * {@code replaceRecipes} to set the whole of what it holds - which builds both of those afresh, unmodifiable, as
     * the manager's own load does. So this hands it everything it already had and this mod's recipes with it, rather
     * than reaching into its maps as the older editions' copy of this did.
     */
    public static void added(RecipeManager manager, HolderLookup.Provider registries) {
        if (manager == null) return;
        List<RecipeHolder<?>> out = added(manager.getRecipes(), registries);
        // Inside the reload: the game works the book's pages out itself once the reload is over.
        if (out != null) replace(manager, out, false);
    }

    /**
     * What the manager should hold: what it has, and this mod's recipes with it - or null to leave it as it is.
     *
     * @param parsed     what the manager has loaded from files
     * @param registries the world's, which an unlock book's enchantment is looked up in
     */
    static List<RecipeHolder<?>> added(java.util.Collection<RecipeHolder<?>> parsed,
        HolderLookup.Provider registries) {
        if (parsed == null) return null;
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
            return null;
        }
        Map<Identifier, Recipe<?>> mine = build(registries);
        if (mine.isEmpty()) return null;

        List<RecipeHolder<?>> out = new ArrayList<RecipeHolder<?>>(parsed);
        java.util.Set<Identifier> claimed = new java.util.HashSet<Identifier>();
        for (RecipeHolder<?> each : parsed) {
            claimed.add(each.id()
                .identifier());
        }

        int added = 0;
        int kept = 0;
        for (Map.Entry<Identifier, Recipe<?>> each : mine.entrySet()) {
            // A datapack that has claimed this id keeps it. See the class note. An id is one recipe
            // whatever its type at this version, so the question is asked of the id alone.
            if (claimed.contains(each.getKey())) {
                kept++;
                continue;
            }
            out.add(new RecipeHolder<Recipe<?>>(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, each.getKey()),
                each.getValue()));
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
        return out;
    }

    /**
     * Builds them, once per reload.
     *
     * <p>
     * Rebuilt rather than cached across reloads on purpose: what the pack contains is exactly what a
     * reload may have changed, and a tamper grade that has just appeared should bring its recipe
     * with it.
     */
    private static Map<Identifier, Recipe<?>> build(HolderLookup.Provider registries) {
        // Filled, then published whole (0.9.228): a reload builds on its background thread from 26.3, and the
        // suggestions below read the last build on the server's, so nobody reads a map while it is filled.
        Map<Identifier, Recipe<?>> built = new LinkedHashMap<Identifier, Recipe<?>>();
        try {
            ModRecipes.register((id, recipe) -> built.put(id, recipe), registries);
        } catch (RuntimeException awkward) {
            // One bad recipe must not cost a world its whole recipe book. Whatever was collected
            // before the throw is kept, and the rest is named.
            Trmt.error("Stopped building this mod's recipes part way through", awkward);
        }
        ours = built;
        return built;
    }

    /** The ids of this mod's recipes as last built, read only: empty before the first build. */
    public static java.util.Set<Identifier> builtIds() {
        Map<Identifier, Recipe<?>> built = ours;
        return built == null ? java.util.Collections.<Identifier>emptySet()
            : java.util.Collections.unmodifiableSet(built.keySet());
    }

    /**
     * What {@code /recipe} suggests for a recipe argument, with this mod's own recipes in it - or null to leave the
     * game's answer as it is (0.9.228; Xep: "Add a small hook").
     *
     * <p>
     * From 26.3 a recipe argument is completed from the recipe registry ({@code CommandSourceStack
     * .suggestRegistryElements}, five arguments, asking {@code getLookup}), which holds a pack's files and not what this
     * mod adds to the manager; 26.2's answered from the manager. So the ids this mod last built that the server's
     * manager holds are added to the very builder the game filled, and the two come back as one answer, a name the
     * game suggested too counted once. Only for the recipe registry, and only where elements are asked for. A client
     * asks the server for these, so single player and multiplayer are answered alike; a lost hook costs a suggestion,
     * never a recipe.
     */
    public static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestions(
        net.minecraft.resources.ResourceKey<?> registry, net.minecraft.commands.SharedSuggestionProvider.ElementSuggestionType type,
        com.mojang.brigadier.suggestion.SuggestionsBuilder builder, net.minecraft.server.MinecraftServer server) {
        if (!net.minecraft.core.registries.Registries.RECIPE.equals(registry) || builder == null || server == null) return null;
        if (type != null && !type.shouldSuggestElements()) return null;
        RecipeManager manager = server.getRecipeManager();
        List<Identifier> held = new ArrayList<Identifier>();
        for (Identifier id : builtIds()) {
            if (manager.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id))
                .isPresent()) held.add(id);
        }
        if (held.isEmpty()) return null;
        return net.minecraft.commands.SharedSuggestionProvider.suggestResource(held.stream(), builder);
    }

    /**
     * A recipe map of these holders, by type and by key, made by the game's own {@code RecipeMap.create} (0.9.228).
     *
     * <p>
     * 26.3's {@code create} takes the recipe registry's lookup, where 26.2's took the holders. It reads only the
     * lookup's elements, and makes each holder from an element's key and value (read from 26.3), so it is handed a
     * lookup over the holders in hand ({@link Held}). Until 0.9.228 the map's private constructor was reached instead
     * (RecipeMapMaker), which skipped every mixin a mod puts on {@code create}: Fabric API attaches its index of
     * recipes by synchronized serializer there and reads it with no null check, so a Fabric server whose client asked
     * for a synchronized serializer - a recipe viewer's request - threw inside the sync after this mod's recipes were
     * added. The same two builders, filled in the same order, as before.
     */
    public static net.minecraft.world.item.crafting.RecipeMap mapOf(java.util.Collection<RecipeHolder<?>> all) {
        return net.minecraft.world.item.crafting.RecipeMap.create(new Held(all));
    }

    /**
     * The holders in hand as a recipe registry's lookup, which is what 26.3's {@code RecipeMap.create} is handed: their
     * elements, in order, and nothing else - no element is found by key and there are no tags.
     */
    private static final class Held implements HolderLookup<Recipe<?>> {

        private final List<net.minecraft.core.Holder.Reference<Recipe<?>>> elements;

        Held(java.util.Collection<RecipeHolder<?>> all) {
            elements = new ArrayList<net.minecraft.core.Holder.Reference<Recipe<?>>>(all.size());
            for (RecipeHolder<?> each : all) elements.add(new Element(this, each.id(), each.value()));
        }

        @Override
        public java.util.stream.Stream<net.minecraft.core.Holder.Reference<Recipe<?>>> listElements() {
            return elements.stream();
        }

        @Override
        public java.util.stream.Stream<net.minecraft.core.HolderSet.Named<Recipe<?>>> listTags() {
            return java.util.stream.Stream.empty();
        }

        @Override
        public java.util.Optional<net.minecraft.core.Holder.Reference<Recipe<?>>> get(
            net.minecraft.resources.ResourceKey<Recipe<?>> key) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<net.minecraft.core.HolderSet.Named<Recipe<?>>> get(
            net.minecraft.tags.TagKey<Recipe<?>> tag) {
            return java.util.Optional.empty();
        }
    }

    /**
     * One holder's recipe as a stand-alone registry element, bound to its value as it is made. The game's own
     * {@code Holder.Reference} constructor, protected, reached by a subclass - where a stand-alone reference made by
     * {@code createStandAlone} would need its protected {@code bindValue} reached as well.
     */
    private static final class Element extends net.minecraft.core.Holder.Reference<Recipe<?>> {

        Element(net.minecraft.core.HolderOwner<Recipe<?>> owner, net.minecraft.resources.ResourceKey<Recipe<?>> key,
            Recipe<?> value) {
            super(net.minecraft.core.Holder.Reference.Type.STAND_ALONE, owner, key, value);
        }
    }

    /**
     * The recipes a player can be given, as 26.3's manager works them out when it is made: every one that is not
     * special, unmodifiable ({@code RecipeManager}'s constructor, read from 26.3). {@code /recipe give *} asks for these.
     */
    public static java.util.Collection<RecipeHolder<?>> learnable(net.minecraft.world.item.crafting.RecipeMap map) {
        return map.values()
            .stream()
            .filter(each -> !each.value()
                .isSpecial())
            .collect(java.util.stream.Collectors.toUnmodifiableList());
    }

    /** Where a built recipe goes. */
    public interface Collector {

        void accept(Identifier id, Recipe<?> recipe);
    }
}
