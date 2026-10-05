package com.trmtgtnh.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.Tag;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.trmtgtnh.Trmt;

/**
 * What a pack calls a material, and what this version calls it.
 *
 * <p>
 * Every list of materials in this mod's settings is written in ore-dictionary names - {@code
 * ingotIron}, {@code gemDiamond}, {@code ingotTungstenSteel} - and there are hundreds of them across
 * the tamper grades, the healing costs, the reinforcement costs and the mending lists. The ore
 * dictionary does not exist at this version; tags replaced it, and a tag is spelled another way
 * entirely: {@code forge:ingots/tungsten_steel}.
 *
 * <p>
 * <strong>The settings keep the old spelling and this translates it.</strong> The alternative -
 * rewriting every list into tag names - would have made a pack's settings file version-locked, and a
 * pack moving between the editions of this mod is a thing the config has been built around from the
 * start: there are settings in it that do nothing here and are kept so a file can travel. A material
 * list is far more valuable to carry than those.
 *
 * <p>
 * The translation is mechanical and is the convention Forge itself published when it moved: the
 * leading word is pluralised into a folder, and what follows is split at its capitals and joined
 * with underscores. {@code ingotIron} is {@code forge:ingots/iron}; {@code ingotTungstenSteel} is
 * {@code forge:ingots/tungsten_steel}. A word this does not know is passed through unpluralised
 * rather than refused, because a pack may name something neither Forge nor this mod has heard of and
 * a guess that is close is better than nothing at all.
 */
public final class OreNames {

    /**
     * How each leading word becomes a folder.
     *
     * <p>
     * Forge's own convention list, which is what every mod on this version tags against. The plurals
     * are not all regular - a block of metal is under {@code storage_blocks} and not {@code blocks} -
     * so this is a table and not a rule.
     */
    private static final Map<String, String> FOLDERS = new HashMap<String, String>();

    /**
     * The names whose published tag is not what the rule below would build.
     *
     * <p>
     * The rule turns {@code stickWood} into {@code forge:rods/wood}, and the tag everything actually
     * uses is {@code forge:rods/wooden}. That one name is every tamper's handle, so for as long as
     * it was wrong this edition had tamper recipes nobody could complete: the metal resolved, the
     * handle did not, and a recipe with one empty slot is a recipe that exists and can never be
     * made. Found by a spike that asked whether the ingredients of this mod's own recipes resolve,
     * which is a different question from whether the recipes are there.
     *
     * <p>
     * Written as exceptions rather than by widening the rule, because the rule is right about every
     * other name this mod ships and a looser rule would be wrong more often than it is right.
     */
    private static final Map<String, String> IRREGULAR = new HashMap<String, String>();

    static {
        IRREGULAR.put("stickWood", "forge:rods/wooden");
        IRREGULAR.put("stickTreatedWood", "forge:rods/treated_wood");

        FOLDERS.put("ingot", "ingots");
        FOLDERS.put("nugget", "nuggets");
        FOLDERS.put("gem", "gems");
        FOLDERS.put("dust", "dusts");
        FOLDERS.put("ore", "ores");
        FOLDERS.put("block", "storage_blocks");
        FOLDERS.put("plate", "plates");
        FOLDERS.put("rod", "rods");
        FOLDERS.put("stick", "rods");
        FOLDERS.put("gear", "gears");
        FOLDERS.put("wire", "wires");
        FOLDERS.put("foil", "foils");
        FOLDERS.put("ring", "rings");
        FOLDERS.put("bolt", "bolts");
        FOLDERS.put("screw", "screws");
        FOLDERS.put("stone", "stone");
        FOLDERS.put("cobblestone", "cobblestone");
        FOLDERS.put("sand", "sand");
        FOLDERS.put("gravel", "gravel");
        FOLDERS.put("log", "logs");
        FOLDERS.put("plank", "planks");
        FOLDERS.put("slab", "slabs");
        FOLDERS.put("dye", "dyes");
        FOLDERS.put("sandstone", "sandstone");
    }

    /** Worked out once per name, because these are asked about inside loops over inventories. */
    private static final Map<String, ResourceLocation> TAGS = new HashMap<String, ResourceLocation>();

    private static volatile boolean oddNameSaid;

    private OreNames() {}

    /**
     * The tag an ore-dictionary name means at this version, or null for a name that cannot be one.
     *
     * <p>
     * A name already written as a tag - anything holding a colon or a slash - is taken as written, so
     * a pack that would rather say {@code forge:ingots/iron} outright may.
     */
    public static ResourceLocation tagFor(String oreName) {
        if (oreName == null || oreName.isEmpty()) return null;
        ResourceLocation held = TAGS.get(oreName);
        if (held != null) return held;

        String irregular = IRREGULAR.get(oreName);
        if (irregular != null) {
            ResourceLocation known = ResourceLocation.tryParse(irregular);
            if (known != null) TAGS.put(oreName, known);
            return known;
        }

        ResourceLocation made;
        if (oreName.indexOf(':') >= 0 || oreName.indexOf('/') >= 0) {
            made = ResourceLocation.tryParse(oreName);
        } else {
            made = fromOreName(oreName);
        }
        if (made != null) TAGS.put(oreName, made);
        return made;
    }

    private static ResourceLocation fromOreName(String oreName) {
        int split = 0;
        while (split < oreName.length() && !Character.isUpperCase(oreName.charAt(split))) split++;
        if (split == 0 || split >= oreName.length()) {
            // No capital to split at: a whole-word name like "treeWood" written without one, or
            // something that is not an ore name at all. Passed through as its own tag under forge.
            return ResourceLocation.tryParse("forge:" + snake(oreName));
        }
        String head = oreName.substring(0, split);
        String tail = oreName.substring(split);
        String folder = FOLDERS.get(head);
        if (folder == null) {
            folder = head + "s";
            if (!oddNameSaid) {
                oddNameSaid = true;
                Trmt.LOG.info(
                    "A material is named {} and this version has no published tag folder for \"{}\", so "
                        + "it is being read as forge:{}/{}. If the pack has that material under another "
                        + "tag, write the tag itself in the setting instead - anything with a colon or a "
                        + "slash in it is taken as written. This is said once.",
                    new Object[] { oreName, head, folder, snake(tail) });
            }
        }
        return ResourceLocation.tryParse("forge:" + folder + "/" + snake(tail));
    }

    /** {@code TungstenSteel} as {@code tungsten_steel}, which is how a tag path is spelled. */
    private static String snake(String camel) {
        StringBuilder out = new StringBuilder(camel.length() + 4);
        for (int at = 0; at < camel.length(); at++) {
            char here = camel.charAt(at);
            if (Character.isUpperCase(here)) {
                if (out.length() > 0 && out.charAt(out.length() - 1) != '_') out.append('_');
                out.append(Character.toLowerCase(here));
            } else if (here == ' ' || here == '-') {
                out.append('_');
            } else {
                out.append(Character.toLowerCase(here));
            }
        }
        return out.toString()
            .toLowerCase(Locale.ROOT);
    }

    /**
     * How a loader makes a tag that resolves when it is asked rather than when it is made.
     *
     * <p>
     * <strong>This seam exists because looking a tag up in the live collection is right for a
     * question and wrong for a recipe.</strong> A recipe's ingredient outlives the moment it was
     * built: it is matched against a grid later, serialised to clients later still, and the tag it
     * names may not even have arrived when it is made. Handing it a tag object fetched from the
     * collection of the moment gives one of two failures - an empty tag, so the recipe can never be
     * made, or an {@code IllegalStateException: Unrecognized tag} when the serialiser is asked for
     * the id of a tag its own collection does not hold. This port met both, and between them they
     * cost this edition every tamper recipe it had.
     *
     * <p>
     * What both loaders have is a <em>named</em> tag: a handle that holds the name and resolves
     * against whatever collection is current each time it is asked. Forge calls it
     * {@code ItemTags.createOptional}; Fabric calls it {@code TagRegistry.item}. Neither can be
     * named from this module, which is what makes it a seam rather than a method - the same
     * arrangement {@code ModsPresent} and {@code Trmt.Host} are under.
     */
    public interface LazyTags {

        Tag<Item> named(ResourceLocation name);
    }

    private static volatile LazyTags lazy;

    /** Told once by each loader as the mod starts. */
    public static void use(LazyTags loaders) {
        lazy = loaders;
    }

    /** The named tags this has already made, so a name costs one handle however often it is asked. */
    private static final Map<String, Tag<Item>> NAMED = new HashMap<String, Tag<Item>>();

    /**
     * The tag itself, or null where this name cannot be one.
     *
     * <p>
     * Through the loader's named-tag handle where there is one, because that is the only form that
     * is still correct a moment later; through the live collection otherwise, which is the answer
     * this had before either loader was asked and is what a unit test with no game sees.
     */
    private static Tag<Item> tag(String oreName) {
        ResourceLocation name = tagFor(oreName);
        if (name == null) return null;

        LazyTags loaders = lazy;
        if (loaders != null) {
            synchronized (NAMED) {
                Tag<Item> held = NAMED.get(oreName);
                if (held != null) return held;
                Tag<Item> made = loaders.named(name);
                if (made != null) NAMED.put(oreName, made);
                return made;
            }
        }

        try {
            return ItemTags.getAllTags()
                .getTag(name);
        } catch (RuntimeException tagsNotLoadedYet) {
            // Tags arrive with a datapack reload and are gone between worlds. Asked too early, the
            // honest answer is that nothing has this tag.
            return null;
        }
    }

    /**
     * The tag this name means, as a handle a recipe may hold.
     *
     * <p>
     * Null where the name cannot be a tag at all. A handle that resolves to nothing is still a
     * handle and is returned - {@link #present} is the question about contents, and a caller that
     * builds an ingredient has to ask it first: an ingredient over an empty tag is where several of
     * this port's recipe failures came from.
     */
    public static Tag<Item> tagOf(String oreName) {
        return tag(oreName);
    }

    /** Whether this pack supplies anything at all under this name. */
    /**
     * Whether any item tags have arrived yet, which decides when any of the questions below can be
     * trusted.
     *
     * <p>
     * <strong>A tag question asked too early answers "nothing has that", which is indistinguishable
     * from a pack that really has not got it.</strong> That is how this edition shipped with no
     * tamper recipe at all: they are built at the tail of {@code RecipeManager.apply}, every grade
     * was asked whether the pack had its metal, the collections were not bound yet, every answer was
     * no, and the mod reported it in a line written for a pack with no metals in it.
     *
     * <p>
     * So anything that decides something lasting from a tag asks this first and waits. Nought tags
     * is a state that only ever exists before they arrive: a game with no tags at all is not a game
     * this mod can run in, since every vanilla block and item is in several.
     */
    public static boolean tagsArrived() {
        Tag<Item> probe = tag(PROBE);
        if (probe == null) return false;
        try {
            // Not merely readable: holding something. A named tag goes through a moment where it
            // reads as empty rather than refusing, and an ingredient built from an empty tag is
            // where the last of these failures came from - Forge puts a barrier in its place and
            // asks the serialiser for the tag's name to label it, which throws "Unrecognized tag"
            // for a tag the serialiser has never heard of. Empty is also what an unbound tag looks
            // like, so waiting for contents covers both.
            return !probe.getValues()
                .isEmpty();
        } catch (RuntimeException notBoundYet) {
            // A named tag refuses to be read before it is bound, and says so: "Tag forge:ingots/iron
            // used before it was bound". That refusal is the answer this method wants, and it is a
            // better one than counting what is in the collection - the collection can hold plenty
            // while the named handles are still unbound, which is exactly the state that threw.
            return false;
        }
    }

    /**
     * The name this asks about, which is any name this mod would ask about anyway.
     *
     * <p>
     * Iron, because every shipped grade list begins with it and the handle is therefore one that
     * would be made in any case. What is being asked is whether tags are readable at all, not
     * whether this pack has iron - a pack without it answers "readable" with an empty tag, which is
     * the right answer to a different question.
     */
    private static final String PROBE = "ingotIron";

    public static boolean present(String oreName) {
        Tag<Item> held = tag(oreName);
        if (held == null) return false;
        try {
            return !held.getValues()
                .isEmpty();
        } catch (RuntimeException notBoundYet) {
            // Asked before the tags are bound. "Nothing has this" is the honest answer, and the
            // callers that must not settle anything on it ask tagsArrived() first.
            return false;
        }
    }

    /** Whether this stack is one of the things this name covers. */
    public static boolean matches(ItemStack stack, String oreName) {
        if (stack == null || stack.isEmpty()) return false;
        Tag<Item> held = tag(oreName);
        if (held == null) return false;
        try {
            return held.contains(stack.getItem());
        } catch (RuntimeException notBoundYet) {
            return false;
        }
    }

    /** Everything this name covers, for a recipe or a readout. Never null. */
    public static List<Item> itemsFor(String oreName) {
        Tag<Item> held = tag(oreName);
        if (held == null) return Collections.emptyList();
        try {
            return new ArrayList<Item>(held.getValues());
        } catch (RuntimeException notBoundYet) {
            return Collections.emptyList();
        }
    }
}
