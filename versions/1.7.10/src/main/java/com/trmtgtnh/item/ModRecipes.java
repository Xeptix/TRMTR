package com.trmtgtnh.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import com.trmtgtnh.Trmt;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * What a tamper costs, in whichever dialect the pack turns out to speak.
 *
 * <p>
 * Which set is registered is decided by asking the ore dictionary for materials rather than by
 * asking Forge whether GregTech is installed. A pack can have GregTech with a material switched
 * off, or plates from something else entirely, and the question that actually decides a recipe
 * is whether its ingredient resolves - not which mod put it there.
 *
 * <p>
 * Three dialects, best first: a double plate where the pack rolls them, a single plate where it
 * only rolls those, and the raw ingot or gem on plain vanilla. A double plate is two plates
 * pressed together, so on GregTech a tamper costs six of them and a chunk tamper sixteen more -
 * which is the point, on a pack where a plate is a bending machine away from being free.
 *
 * <p>
 * Registered at post-init, which is later than it strictly needs to be and costs nothing: every
 * mod's init has finished by then, so anything registered at any phase is visible.
 */
public final class ModRecipes {

    private static final String TOOL_HAMMER = "craftingToolHardHammer";
    private static final String TOOL_FILE = "craftingToolFile";
    private static final String HANDLE = "stickWood";

    /** Ore-name prefixes a grade's material may be stated with, longest first. */
    private static final String[] PREFIXES = { "nugget", "ingot", "block", "plate", "dust", "gem" };

    private ModRecipes() {}

    // ------------------------------------------------------------------
    // Which form of a material this pack has
    // ------------------------------------------------------------------

    /**
     * The material half of an ore name: {@code ingotStainlessSteel} to {@code StainlessSteel}.
     *
     * <p>
     * Taken from the ore name rather than built from the grade's key, and that is not a detail.
     * The key is lower case because it is an identifier - {@code stainlesssteel} - and
     * {@code plateStainlesssteel} is a name nothing in any pack registers. The ore name is the
     * only place the material's real capitalisation is written down.
     */
    private static String materialOf(String ore) {
        if (ore == null) return null;
        for (String prefix : PREFIXES) {
            if (ore.length() > prefix.length() && ore.startsWith(prefix)
                && Character.isUpperCase(ore.charAt(prefix.length()))) {
                return ore.substring(prefix.length());
            }
        }
        return null;
    }

    /**
     * What one face of a tamper is made of here: a double plate, a plate, or the raw material.
     *
     * <p>
     * Returns null when the pack cannot supply any of the three, which is how a grade whose
     * material was switched off ends up with no recipe rather than an unmakeable one.
     */
    private static String faceOre(String rawOre) {
        String material = materialOf(rawOre);
        // Plates are a GregTech-pack idiom and part of the harder recipes the enhanced switch
        // turns on; with it off the tamper is built from the raw ingot or gem the vanilla way.
        if (material != null && com.trmtgtnh.config.TrmtConfig.gtnhEnhanced) {
            String twin = "plateDouble" + material;
            if (hasOre(twin)) return twin;
            String single = "plate" + material;
            if (hasOre(single)) return single;
        }
        return hasOre(rawOre) ? rawOre : null;
    }

    /** Whether this ore name is one of the rolled forms, which is what decides the shape. */
    private static boolean isPlate(String ore) {
        return ore != null && ore.startsWith("plate");
    }

    // ------------------------------------------------------------------
    // The tools
    // ------------------------------------------------------------------

    /**
     * The hand tamper, one recipe per material the pack can supply.
     *
     * <p>
     * Two grips, a shaft, a flat sole. The shape was picked by taking an occupancy census of
     * every literal recipe in all 233 jars of the pack. This mask appears in five of them and in
     * nothing vanilla or Forge registers, and all five want a non-stick item in the top corners
     * and three different items along the bottom - so no grid can satisfy both, and nothing here
     * can shadow or be shadowed. The mask is also its own mirror, so the mirrored orientation
     * needs no separate argument.
     */
    private static void registerTampers() {
        if (ModItems.gradedTamper() == null) return;

        int made = 0;
        for (TamperGrade grade : TamperGrade.available()) {
            String face = faceOre(grade.ore);
            if (face == null) continue;

            ItemStack result = new ItemStack(ModItems.gradedTamper(), 1, 0);
            ItemChunkTamper.setGrade(result, grade);
            GameRegistry.addRecipe(gregtech(face) ? forged(result, face) : plain(result, face));
            made++;
        }
        Trmt.LOG.info("Registered {} tamper recipes", Integer.valueOf(made));
    }

    /** Two sticks, a shaft, and three of whatever the sole is made of. */
    private static ShapedOreRecipe plain(ItemStack result, String sole) {
        return new ShapedOreRecipe(result, "S S", " S ", "MMM", 'S', HANDLE, 'M', sole);
    }

    /**
     * The same tool built the GregTech way, in the same mask.
     *
     * <p>
     * A hammer and a file in the corners is GregTech's own idiom, and they happen to be the two
     * tools that flatten and smooth metal, which is what this thing does to ground. Neither is
     * consumed: GregTech's tool item answers {@code getContainerItem} with a copy damaged by one
     * craft, which is what vanilla's crafting slot asks for - so a plain Forge recipe behaves
     * exactly like one of GregTech's own without a line of GregTech in the build file.
     */
    private static ShapedOreRecipe forged(ItemStack result, String sole) {
        return new ShapedOreRecipe(
            result,
            "h f",
            " S ",
            "PPP",
            'h',
            TOOL_HAMMER,
            'f',
            TOOL_FILE,
            'S',
            HANDLE,
            'P',
            sole);
    }

    /** Whether to build this the GregTech way: rolled metal, and the two tools that roll it. */
    private static boolean gregtech(String face) {
        return isPlate(face) && hasOre(TOOL_HAMMER) && hasOre(TOOL_FILE);
    }

    /**
     * The chunk tamper: a ring of the grade's own material around a tamper of that same material.
     *
     * <p>
     * Collision-proof by construction rather than by census. The centre slot holds an item only
     * this mod registers, so no recipe anywhere in any pack can match this shape - which is a far
     * better guarantee than checking two hundred jars and hoping. It also reads correctly: a
     * chunk tamper is a tamper with a great deal more of the same metal behind it.
     *
     * <p>
     * The centre has to be the <em>matching</em> tamper, and enforcing that needs
     * {@link RecipeGradedTamper} rather than a plain shaped recipe - see that class for why a
     * plain one would have made all eleven of these into one.
     *
     * <p>
     * Returns the grades it made a recipe for, in resolved order. Those, and not the grades the config
     * names, are the ones a player can hold a chunk tamper of: each one's centre is a plain tamper that
     * registerTampers made from the same face over the same grades. That is what the Wayfarer's recipe
     * needs to know.
     */
    private static List<TamperGrade> registerChunkTampers() {
        List<TamperGrade> made = new ArrayList<TamperGrade>();
        if (ModItems.gradedTamper() == null || ModItems.chunkTamper() == null) return made;

        for (TamperGrade grade : TamperGrade.available()) {
            String face = faceOre(grade.ore);
            if (face == null) continue;

            // Wildcard damage, so a tamper you have actually used is still a tamper. Shaped
            // recipes match on item and damage and nothing else, and a fresh stack here would
            // have meant crafting one only to upgrade it - which is not what "a tamper of that
            // metal" says. The grade still rides along, because NEI draws this stack and it
            // should be the bronze one that appears in the bronze recipe.
            ItemStack core = new ItemStack(ModItems.gradedTamper(), 1, OreDictionary.WILDCARD_VALUE);
            ItemChunkTamper.setGrade(core, grade);

            ItemStack result = new ItemStack(ModItems.chunkTamper(), 1, 0);
            ItemChunkTamper.setGrade(result, grade);

            GameRegistry.addRecipe(
                new RecipeGradedTamper(
                    result,
                    grade.key,
                    "MMM",
                    "MTM",
                    "MMM",
                    Character.valueOf('M'),
                    face,
                    Character.valueOf('T'),
                    core));
            made.add(grade);
        }
        Trmt.LOG.info("Registered {} chunk tamper recipes", Integer.valueOf(made.size()));
        return made;
    }

    /**
     * The Wayfarer's tamper: a nether star and a chunk tamper, ringed in the hardest things going.
     *
     * <p>
     * Collision-proof for the same reason the chunk tamper's recipe is - the middle slot holds an
     * item only this mod registers - and expensive for a reason that is not only balance. A tool
     * that mends ground for nothing and never wears out is the end of a ladder, and a ladder
     * wants its last rung to cost something a player will remember climbing to.
     *
     * <p>
     * One grade of chunk tamper rather than any, and which one is {@link WayfarerCore}'s to say,
     * chosen from the grades the chunk tamper loop actually made a recipe for rather than from the
     * grades the config names. Until 0.9.213 this named diamond without asking, so a pack whose list
     * had lost its diamond line got a recipe nobody could complete, drawn in NEI around whichever
     * grade the list began with.
     *
     * <p>
     * Where there is nothing to build it from - no diamond blocks for the ring, or no chunk tamper of
     * any grade - it is left without a recipe and the log says so. A warning rather than silence,
     * because its achievement is earned only by crafting one and was registered long before this runs.
     */
    private static void registerMagicTamper(List<TamperGrade> chunkGrades) {
        if (ModItems.magicTamper() == null || ModItems.chunkTamper() == null) return;
        boolean enhanced = com.trmtgtnh.config.TrmtConfig.gtnhEnhanced;

        if (!hasOre("blockDiamond")) {
            Trmt.LOG.warn(
                "The Wayfarer's tamper has no recipe in this pack: nothing is registered as blockDiamond, which its recipe is ringed in. Anything else that hands one out still does, but no crafting grid will make one, and its achievement is earned only by crafting one.");
            return;
        }

        Map<String, Integer> craftable = new LinkedHashMap<String, Integer>();
        for (TamperGrade each : chunkGrades) {
            craftable.put(each.key, Integer.valueOf(each.declaredUses()));
        }
        String key = WayfarerCore.gradeFor(enhanced, craftable);
        if (key == null) {
            Trmt.LOG.warn(
                "The Wayfarer's tamper has no recipe in this pack: no grade in general.tamperGrades can be made into a chunk tamper here, so there is nothing to build it around. Anything else that hands one out still does, but no crafting grid will make one, and its achievement is earned only by crafting one.");
            return;
        }

        // The core must be a chunk tamper of that grade. Wildcard damage so a used one still counts;
        // the grade is in NBT, which a shaped recipe ignores, so RecipeWayfarer checks the key. The
        // display stack is marked through byKey, the lookup gradeOf makes when the grid is read, and
        // for a key the chunk tamper loop has just used that lookup answers with the same key - the
        // configured grade, the first one, or the iron stood in for an empty list - so what NEI draws
        // in the middle and what the grid accepts cannot come apart again.
        ItemStack core = new ItemStack(ModItems.chunkTamper(), 1, OreDictionary.WILDCARD_VALUE);
        ItemChunkTamper.setGrade(core, TamperGrade.byKey(key));

        GameRegistry.addRecipe(
            new RecipeWayfarer(
                new ItemStack(ModItems.magicTamper(), 1, 0),
                key,
                "ENE",
                "DTD",
                "EDE",
                Character.valueOf('E'),
                new ItemStack(net.minecraft.init.Items.ender_eye),
                Character.valueOf('N'),
                new ItemStack(net.minecraft.init.Items.nether_star),
                Character.valueOf('D'),
                "blockDiamond",
                Character.valueOf('T'),
                core));

        if (WayfarerCore.USUAL.equals(key) || (enhanced && WayfarerCore.ENHANCED.equals(key))) {
            Trmt.LOG.info("Registered the Wayfarer's tamper recipe, built around the {} chunk tamper", key);
        } else {
            Trmt.LOG.info(
                "Registered the Wayfarer's tamper recipe, built around the {} chunk tamper, the longest-lasting this pack can make, because no diamond one{} can be made here",
                key,
                enhanced ? " and no netherite one" : "");
        }
    }

    // ------------------------------------------------------------------
    // The guide books
    // ------------------------------------------------------------------

    /**
     * The four books.
     *
     * <p>
     * The first is a book and quill written over a handful of every surface the mod is about -
     * earth, stone, gravel, sand, snow - bound with bone meal for the growing and an ender pearl
     * for the going. The other three are that book studied further, each with the thing its
     * subject needs: an eye and a diamond for the technical one, paper and redstone for the list
     * of commands, a player's head and packed earth for the golem's.
     *
     * <p>
     * Shapeless, because none of them is a shape - they are a pile of things read together. Where
     * the enhancements are on and the pack has compressed earth, the harder variants ask for that
     * instead of the plain block, so a GregTech pack pays a GregTech price.
     */
    private static void registerGuides() {
        ItemGuideBook mk1 = ModItems.guide(GuideBook.MK1);
        if (mk1 == null) return;
        boolean harder = com.trmtgtnh.config.TrmtConfig.gtnhEnhanced;

        // Mk I: a book and quill, bone meal, and one of every ground it talks about.
        GameRegistry.addRecipe(
            new ShapelessOreRecipe(
                new ItemStack(mk1),
                new ItemStack(net.minecraft.init.Items.writable_book),
                new ItemStack(net.minecraft.init.Items.dye, 1, 15),
                new ItemStack(net.minecraft.init.Blocks.dirt),
                new ItemStack(net.minecraft.init.Blocks.cobblestone),
                new ItemStack(net.minecraft.init.Blocks.stone),
                new ItemStack(net.minecraft.init.Blocks.gravel),
                new ItemStack(net.minecraft.init.Blocks.sand),
                new ItemStack(net.minecraft.init.Items.snowball),
                new ItemStack(net.minecraft.init.Items.ender_pearl)));

        registerSubGuide(
            GuideBook.MK2,
            mk1,
            new ItemStack(net.minecraft.init.Items.ender_eye),
            new ItemStack(net.minecraft.init.Items.diamond),
            new ItemStack(net.minecraft.init.Items.glowstone_dust),
            harder ? compressed(3) : null);

        registerSubGuide(
            GuideBook.COMMANDS,
            mk1,
            new ItemStack(net.minecraft.init.Items.writable_book),
            new ItemStack(net.minecraft.init.Items.paper),
            new ItemStack(net.minecraft.init.Items.redstone),
            null);

        registerSubGuide(
            GuideBook.GOLEM,
            mk1,
            new ItemStack(net.minecraft.init.Items.skull, 1, 3),
            harder ? compressed(10) : new ItemStack(net.minecraft.init.Blocks.dirt),
            new ItemStack(net.minecraft.init.Blocks.cobblestone),
            null);

        Trmt.LOG.info("Registered the guide book recipes");
    }

    /** One sub-book: the first book plus whatever its subject needs. Nulls are simply skipped. */
    private static void registerSubGuide(GuideBook book, ItemGuideBook mk1, ItemStack... extras) {
        ItemGuideBook result = ModItems.guide(book);
        if (result == null) return;

        List<Object> parts = new ArrayList<Object>();
        parts.add(new ItemStack(mk1));
        for (ItemStack extra : extras) {
            if (extra != null && extra.getItem() != null) parts.add(extra);
        }
        GameRegistry.addRecipe(new ShapelessOreRecipe(new ItemStack(result), parts.toArray()));
    }

    /**
     * A compressed earth or cobble block by metadata, or null where the pack has none.
     *
     * <p>
     * Looked up by name rather than depended on, so the harder recipes simply do not appear on a
     * pack without them and the plain ones still do.
     */
    private static ItemStack compressed(int meta) {
        net.minecraft.block.Block block = net.minecraft.block.Block
            .getBlockFromName("ExtraUtilities:cobblestone_compressed");
        if (block == null || block == net.minecraft.init.Blocks.air) return null;
        net.minecraft.item.Item item = net.minecraft.item.Item.getItemFromBlock(block);
        return item == null ? null : new ItemStack(item, 1, meta);
    }

    // ------------------------------------------------------------------
    // The enchant books
    // ------------------------------------------------------------------

    /**
     * A book for each mode unlock, so the enchantment can be earned rather than gambled for.
     *
     * <p>
     * An enchanting table will offer these, but only by luck - on a chunk tamper, or on a plain book at a
     * table level between nine and eighteen - and a librarian sells one now and then. A book you can make
     * is the difference between a mode being a feature and being a rumour - and it is deliberately
     * expensive, because what it unlocks is permanent and works on any block in the world.
     *
     * <p>
     * Each is registered only while its feature is switched on, and that is settled here, once, at startup,
     * as the chest finds are: a switch changed later reaches the recipe only after a restart, while the
     * table and a librarian follow it at once (see UnlockBooks).
     *
     * <p>
     * Priced in what the mode is about: obsidian and diamond for making a block harder to break,
     * an eye and a pearl and the ward's own materials for keeping things away from it. Where the
     * enhancements are on, the harder variant replaces the plain one rather than sitting beside
     * it, so a GregTech pack pays a GregTech price instead of getting a choice of two.
     */
    private static void registerEnchantBooks() {
        boolean harder = com.trmtgtnh.config.TrmtConfig.gtnhEnhanced;

        if (com.trmtgtnh.config.TrmtConfig.reinforceEnabled && EnchReinforce.INSTANCE != null) {
            registerEnchantBook(
                EnchReinforce.INSTANCE,
                new ItemStack(net.minecraft.init.Items.book),
                new ItemStack(net.minecraft.init.Items.diamond),
                new ItemStack(net.minecraft.init.Blocks.obsidian),
                new ItemStack(net.minecraft.init.Blocks.obsidian),
                new ItemStack(net.minecraft.init.Items.dye, 1, 4),
                new ItemStack(net.minecraft.init.Items.dye, 1, 4),
                harder ? compressed(3) : null);
        }

        if (com.trmtgtnh.config.TrmtConfig.lightEnabled && EnchLight.INSTANCE != null) {
            registerEnchantBook(
                EnchLight.INSTANCE,
                new ItemStack(net.minecraft.init.Items.book),
                new ItemStack(net.minecraft.init.Blocks.glowstone),
                new ItemStack(net.minecraft.init.Items.glowstone_dust),
                new ItemStack(net.minecraft.init.Items.glowstone_dust),
                new ItemStack(net.minecraft.init.Items.dye, 1, 4),
                new ItemStack(net.minecraft.init.Items.dye, 1, 4),
                harder ? compressed(7) : null);
        }

        if (com.trmtgtnh.config.TrmtConfig.wardEnabled && EnchWard.INSTANCE != null) {
            registerEnchantBook(
                EnchWard.INSTANCE,
                new ItemStack(net.minecraft.init.Items.book),
                new ItemStack(net.minecraft.init.Items.ender_eye),
                new ItemStack(net.minecraft.init.Items.ender_pearl),
                new ItemStack(net.minecraft.init.Items.golden_carrot),
                new ItemStack(net.minecraft.init.Items.dye, 1, 4),
                new ItemStack(net.minecraft.init.Items.dye, 1, 4),
                harder ? compressed(11) : null);
        }
    }

    /** One enchanted book carrying one of the mod's enchantments. Nulls among the parts are skipped. */
    private static void registerEnchantBook(net.minecraft.enchantment.Enchantment enchantment, ItemStack... parts) {
        ItemStack result = new ItemStack(net.minecraft.init.Items.enchanted_book);
        net.minecraft.init.Items.enchanted_book
            .addEnchantment(result, new net.minecraft.enchantment.EnchantmentData(enchantment, 1));

        List<Object> ingredients = new ArrayList<Object>();
        for (ItemStack part : parts) {
            if (part != null && part.getItem() != null) ingredients.add(part);
        }
        if (ingredients.isEmpty()) return;
        GameRegistry.addRecipe(new ShapelessOreRecipe(result, ingredients.toArray()));
        Trmt.LOG.info("Registered the {} enchant book recipe", enchantment.getName());
    }

    // ------------------------------------------------------------------
    // The golem's upgrades
    // ------------------------------------------------------------------

    /**
     * One recipe per upgrade, each priced in what it is about.
     *
     * <p>
     * Every one is built on packed stone, because it is a part fitted to a thing made of packed
     * stone. Past that they are what they do: pearls for reach, sugar and redstone for haste,
     * obsidian for a tool that lasts, emerald for spending less, a chest for holding more, bone
     * meal and wheat for growing, iron banded with obsidian for standing longer, and a diamond
     * edge on flint for hitting harder.
     *
     * <p>
     * The last is every other one at once, and there are nine of those - which is the whole of a
     * shapeless recipe and leaves nowhere to lay the nether star that used to bind them. So the
     * nine bind into the loose version on their own, and the star settles that afterwards. Two
     * steps rather than one, and the second is the one worth having: what the star buys is not
     * power but knowing which golem you have.
     */
    private static void registerGolemUpgrades() {
        if (!com.trmtgtnh.config.TrmtConfig.golemEnabled || !com.trmtgtnh.config.TrmtConfig.golemUpgrades) return;
        ItemStack stone = new ItemStack(net.minecraft.init.Blocks.stonebrick);
        boolean harder = com.trmtgtnh.config.TrmtConfig.gtnhEnhanced;

        upgrade(
            com.trmtgtnh.entity.GolemUpgrade.RANGE,
            stone,
            new ItemStack(net.minecraft.init.Items.ender_pearl),
            new ItemStack(net.minecraft.init.Items.ender_eye),
            new ItemStack(net.minecraft.init.Items.redstone));
        upgrade(
            com.trmtgtnh.entity.GolemUpgrade.SPEED,
            stone,
            new ItemStack(net.minecraft.init.Items.sugar),
            new ItemStack(net.minecraft.init.Items.sugar),
            new ItemStack(net.minecraft.init.Items.redstone),
            new ItemStack(net.minecraft.init.Items.redstone));
        upgrade(
            com.trmtgtnh.entity.GolemUpgrade.SPARING,
            stone,
            new ItemStack(net.minecraft.init.Blocks.obsidian),
            new ItemStack(net.minecraft.init.Items.iron_ingot),
            new ItemStack(net.minecraft.init.Items.iron_ingot));
        upgrade(
            com.trmtgtnh.entity.GolemUpgrade.FRUGAL,
            stone,
            new ItemStack(net.minecraft.init.Items.emerald),
            new ItemStack(net.minecraft.init.Items.gold_ingot),
            new ItemStack(net.minecraft.init.Items.gold_ingot));
        upgrade(
            com.trmtgtnh.entity.GolemUpgrade.DEEP,
            stone,
            new ItemStack(net.minecraft.init.Blocks.chest),
            new ItemStack(net.minecraft.init.Items.iron_ingot),
            new ItemStack(net.minecraft.init.Items.iron_ingot));
        upgrade(
            com.trmtgtnh.entity.GolemUpgrade.GREEN,
            stone,
            new ItemStack(net.minecraft.init.Items.dye, 1, 15),
            new ItemStack(net.minecraft.init.Items.dye, 1, 15),
            new ItemStack(net.minecraft.init.Items.wheat),
            new ItemStack(net.minecraft.init.Items.wheat));
        upgrade(
            com.trmtgtnh.entity.GolemUpgrade.STOUT,
            stone,
            new ItemStack(net.minecraft.init.Blocks.iron_block),
            new ItemStack(net.minecraft.init.Blocks.obsidian),
            new ItemStack(net.minecraft.init.Blocks.obsidian));
        upgrade(
            com.trmtgtnh.entity.GolemUpgrade.FIERCE,
            stone,
            new ItemStack(net.minecraft.init.Items.diamond),
            new ItemStack(net.minecraft.init.Items.flint),
            new ItemStack(net.minecraft.init.Items.flint));

        // Reaching into a chest that is not in front of it: a chest for the idea, a hopper for the
        // moving, pearls for the reach and gold for what a hopper and an ender chest are both made
        // of. Twice the parts the storage upgrade costs, because holding four times as much is a
        // bigger box and working out of somebody else's boxes is a different thing entirely.
        upgrade(
            com.trmtgtnh.entity.GolemUpgrade.SETTLED,
            stone,
            new ItemStack(net.minecraft.init.Blocks.chest),
            new ItemStack(net.minecraft.init.Blocks.hopper),
            new ItemStack(net.minecraft.init.Items.ender_pearl),
            new ItemStack(net.minecraft.init.Items.ender_pearl),
            new ItemStack(net.minecraft.init.Items.gold_ingot),
            new ItemStack(net.minecraft.init.Items.gold_ingot),
            new ItemStack(net.minecraft.init.Items.diamond),
            harder ? compressed(3) : null);

        // Every upgrade at once. Nine of them is a full grid, so what they bind into first is the
        // loose version, and the star that settles it comes afterwards - which is why the star is
        // not in this list. Turn the loose one off and the nine bind straight into the settled one
        // and the star is not asked for at all, because there is nowhere left in the grid to put it.
        ItemGolemUpgrade omni = ModItems.upgrade(com.trmtgtnh.entity.GolemUpgrade.OMNI);
        ItemGolemUpgrade unstable = ModItems.upgrade(com.trmtgtnh.entity.GolemUpgrade.UNSTABLE);
        if (omni == null) return;
        List<Object> parts = new ArrayList<Object>();
        for (com.trmtgtnh.entity.GolemUpgrade each : com.trmtgtnh.entity.GolemUpgrade.real()) {
            if (each.carriesTheSet()) continue;
            ItemGolemUpgrade item = ModItems.upgrade(each);
            if (item == null) return;
            parts.add(new ItemStack(item));
        }

        boolean loose = com.trmtgtnh.config.TrmtConfig.golemUnstableOmni && unstable != null;
        GameRegistry.addRecipe(new ShapelessOreRecipe(new ItemStack(loose ? unstable : omni), parts.toArray()));
        if (loose) {
            GameRegistry.addRecipe(
                new ShapelessOreRecipe(
                    new ItemStack(omni),
                    new ItemStack(unstable),
                    new ItemStack(net.minecraft.init.Items.nether_star)));
        }
        Trmt.LOG.info("Registered the golem upgrade recipes");
    }

    /** One upgrade, built on packed stone plus whatever it is about. */
    private static void upgrade(com.trmtgtnh.entity.GolemUpgrade which, ItemStack... parts) {
        ItemGolemUpgrade result = ModItems.upgrade(which);
        if (result == null) return;
        List<Object> ingredients = new ArrayList<Object>();
        for (ItemStack part : parts) {
            if (part != null && part.getItem() != null) ingredients.add(part);
        }
        GameRegistry.addRecipe(new ShapelessOreRecipe(new ItemStack(result), ingredients.toArray()));
    }

    public static void register() {
        TamperGrade.resolve();
        registerTampers();
        registerMagicTamper(registerChunkTampers());
        registerGuides();
        registerEnchantBooks();
        registerGolemUpgrades();
        registerDraughts();
    }

    /**
     * The two draughts.
     *
     * <p>
     * Lightness keeps the original mod's own ingredients exactly - an awkward potion and a feather -
     * and moves only the apparatus. Upstream brews it in a stand; 1.7.10 has no brewing API at all,
     * and vanilla's own brewing decides what comes out of a stand from four bits of a damage value
     * of which only three patterns are unclaimed across an entire pack, with no cheap way to learn
     * whether another mod has taken one and no failure worse than quietly handing somebody the wrong
     * drink. So the bench does it instead. The ingredients still gate it behind nether wart, a
     * brewing stand and a bottle of water, which is upstream's whole progression.
     *
     * <p>
     * Its opposite follows vanilla's own convention for reversing a potion rather than inventing a
     * second line: a fermented spider eye is how swiftness becomes slowness and healing becomes
     * harm, and it is how lightness becomes its opposite here. Which also means the heavy one cannot
     * be made without making the light one first, and that is a piece of progression for free.
     */
    private static void registerDraughts() {
        if (!com.trmtgtnh.config.TrmtConfig.potionsEnabled) return;

        ItemDraught light = ModItems.draught(Draughts.LIGHTNESS);
        ItemDraught heavy = ModItems.draught(Draughts.HEAVYFOOT);
        boolean harder = com.trmtgtnh.config.TrmtConfig.gtnhEnhanced;

        if (light != null && com.trmtgtnh.config.TrmtConfig.potionLightness) {
            // Damage sixteen is what vanilla's bitfield means by an awkward potion. Named by its
            // number because that is the only name it has - there is no ore entry for a potion.
            ItemStack awkward = new ItemStack(net.minecraft.init.Items.potionitem, 1, 16);
            String feathery = lightestOre();
            if (harder && feathery != null) {
                // Harder where the pack can be asked for something lighter than a feather. Three
                // feathers rather than one, a ghast tear for the part of it that is not weight but
                // buoyancy, and whatever rolled metal this pack considers light.
                GameRegistry.addRecipe(
                    new ShapelessOreRecipe(
                        new ItemStack(light),
                        awkward,
                        new ItemStack(net.minecraft.init.Items.feather),
                        new ItemStack(net.minecraft.init.Items.feather),
                        new ItemStack(net.minecraft.init.Items.feather),
                        new ItemStack(net.minecraft.init.Items.ghast_tear),
                        feathery));
            } else {
                GameRegistry.addRecipe(
                    new ShapelessOreRecipe(
                        new ItemStack(light),
                        awkward,
                        new ItemStack(net.minecraft.init.Items.feather)));
            }
        }

        // Gated on lightness as well, because the light bottle is this recipe's own first
        // ingredient and nothing else in any pack makes one. Without this the heavy recipe
        // went on being registered with lightness switched off, which is a recipe NEI draws
        // and a bench will never fulfil. The heavyFoot setting's own text already says that
        // turning lightness off takes this away; now it does.
        if (heavy != null && light != null
            && com.trmtgtnh.config.TrmtConfig.potionHeavyFoot
            && com.trmtgtnh.config.TrmtConfig.potionLightness) {
            String weighty = heaviestOre();
            if (harder && weighty != null) {
                GameRegistry.addRecipe(
                    new ShapelessOreRecipe(
                        new ItemStack(heavy),
                        new ItemStack(light),
                        new ItemStack(net.minecraft.init.Items.fermented_spider_eye),
                        weighty,
                        weighty));
            } else {
                GameRegistry.addRecipe(
                    new ShapelessOreRecipe(
                        new ItemStack(heavy),
                        new ItemStack(light),
                        new ItemStack(net.minecraft.init.Items.fermented_spider_eye)));
            }
        }

        Trmt.LOG.info("Registered the draught recipes ({} form)", harder ? "enhanced" : "plain");
    }

    /**
     * The lightest rolled metal this pack has a name for, or null where it has none.
     *
     * <p>
     * Asked of the ore dictionary rather than of any mod, in the same shape as {@code faceOre} above
     * and for the same reason: naming a mod would put it on the compile classpath, and the whole
     * arrangement here is that a pack is asked what it has rather than told what it must be.
     */
    private static String lightestOre() {
        if (hasOre("foilAluminium")) return "foilAluminium";
        if (hasOre("plateAluminium")) return "plateAluminium";
        if (hasOre("ingotAluminium")) return "ingotAluminium";
        if (hasOre("foilTin")) return "foilTin";
        if (hasOre("plateTin")) return "plateTin";
        return null;
    }

    /** And the heaviest, on the same terms. Lead where a pack has it, iron where it does not. */
    private static String heaviestOre() {
        if (hasOre("plateDenseLead")) return "plateDenseLead";
        if (hasOre("plateLead")) return "plateLead";
        if (hasOre("ingotLead")) return "ingotLead";
        if (hasOre("plateDenseIron")) return "plateDenseIron";
        if (hasOre("blockIron")) return "blockIron";
        return null;
    }

    /**
     * Whether anything is actually registered under an ore name.
     *
     * <p>
     * Not {@code doesOreNameExist} on its own, and not {@code getOres} on its own either: asking
     * for an ore id registers the name, so a name some other mod merely enquired about answers
     * yes with an empty list behind it. Both questions, in that order.
     */
    private static boolean hasOre(String name) {
        if (!OreDictionary.doesOreNameExist(name)) return false;
        List<ItemStack> ores = OreDictionary.getOres(name, false);
        return ores != null && !ores.isEmpty();
    }
}
