package com.trmtgtnh.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;

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
 * Registered at post-init, in both editions and for the same reason: the ingredients are a question
 * about the whole pack, and only by then has every mod answered. 1.12.2 would rather have recipes in
 * their registry event, which fires before any mod's pre-init - with the ore dictionary empty, which
 * would make every question below answer no. Forge freezes its registries after post-init, not
 * before, so there is room to register there, and that is where these go.
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
        if (material != null && TrmtConfig.gtnhEnhanced) {
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

            ItemStack result = new ItemStack(ModItems.gradedTamper());
            ItemChunkTamper.setGrade(result, grade);
            java.util.function.Function<net.minecraft.resources.ResourceLocation, net.minecraft.world.item.crafting.Recipe<?>> recipe = gregtech(face) ? forged(result, face) : plain(result, face);
            add(recipe, "tamper_" + grade.key);
            made++;
        }
        Trmt.LOG.info("Registered {} tamper recipes", Integer.valueOf(made));
    }

    /** Two sticks, a shaft, and three of whatever the sole is made of. */
    private static java.util.function.Function<net.minecraft.resources.ResourceLocation, net.minecraft.world.item.crafting.Recipe<?>> plain(ItemStack result, String sole) {
        return OreRecipes.shaped(null, result, "S S", " S ", "MMM", 'S', HANDLE, 'M', sole);
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
    private static java.util.function.Function<net.minecraft.resources.ResourceLocation, net.minecraft.world.item.crafting.Recipe<?>> forged(ItemStack result, String sole) {
        return OreRecipes.shaped(
            null,
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
            // metal" says. The grade still rides along, because the recipe list draws this stack
            // and it should be the bronze one that appears in the bronze recipe.
            ItemStack core = new ItemStack(ModItems.gradedTamper());
            ItemChunkTamper.setGrade(core, grade);

            ItemStack result = new ItemStack(ModItems.chunkTamper());
            ItemChunkTamper.setGrade(result, grade);

            add(
                OreRecipes.gradedTamper(
                    null,
                    result,
                    grade.key,
                    "MMM",
                    "MTM",
                    "MMM",
                    Character.valueOf('M'),
                    face,
                    Character.valueOf('T'),
                    core),
                "chunk_tamper_" + grade.key);
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
     * grades the config names. Until 0.9.213 the other edition named diamond without asking, so a pack
     * whose list had lost its diamond line got a recipe nobody could complete.
     *
     * <p>
     * Where there is nothing to build it from - no diamond blocks for the ring, or no chunk tamper of
     * any grade - it is left without a recipe and the log says so.
     */
    private static void registerMagicTamper(List<TamperGrade> chunkGrades) {
        if (ModItems.magicTamper() == null || ModItems.chunkTamper() == null) return;
        boolean enhanced = TrmtConfig.gtnhEnhanced;

        if (!hasOre("blockDiamond")) {
            Trmt.LOG.warn(
                "The Wayfarer's tamper has no recipe in this pack: nothing is registered as blockDiamond, which its recipe is ringed in. Anything else that hands one out still does, but no crafting grid will make one.");
            return;
        }

        Map<String, Integer> craftable = new LinkedHashMap<String, Integer>();
        for (TamperGrade each : chunkGrades) {
            craftable.put(each.key, Integer.valueOf(each.declaredUses()));
        }
        String key = WayfarerCore.gradeFor(enhanced, craftable);
        if (key == null) {
            Trmt.LOG.warn(
                "The Wayfarer's tamper has no recipe in this pack: no grade in general.tamperGrades can be made into a chunk tamper here, so there is nothing to build it around. Anything else that hands one out still does, but no crafting grid will make one.");
            return;
        }

        // The core must be a chunk tamper of that grade. Wildcard damage so a used one still counts;
        // the grade is in NBT, which a shaped recipe ignores, so RecipeWayfarer checks the key. The
        // display stack is marked through byKey, the lookup gradeOf makes when the grid is read, and
        // for a key the chunk tamper loop has just used that lookup answers with the same key - so
        // what the recipe list draws in the middle and what the grid accepts cannot come apart.
        ItemStack core = new ItemStack(ModItems.chunkTamper());
        ItemChunkTamper.setGrade(core, TamperGrade.byKey(key));

        add(
            OreRecipes.wayfarer(
                null,
                new ItemStack(ModItems.magicTamper()),
                key,
                "ENE",
                "DTD",
                "EDE",
                Character.valueOf('E'),
                new ItemStack(net.minecraft.world.item.Items.ENDER_EYE),
                Character.valueOf('N'),
                new ItemStack(net.minecraft.world.item.Items.NETHER_STAR),
                Character.valueOf('D'),
                "blockDiamond",
                Character.valueOf('T'),
                core),
            "magic_tamper");

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

    public static void register(ExtraRecipes.Collector into) {
        collecting = into;
        try {
            buildEverything();
        } finally {
            // Cleared whatever happened, so a throw cannot leave a stale collector for the next
            // reload to write into.
            collecting = null;
        }
    }

    private static void buildEverything() {
        TamperGrade.resolve();
        registerTampers();
        registerMagicTamper(registerChunkTampers());
        registerDraughts();
        registerGolemUpgrades();
        registerGuides();
        registerEnchantBooks();
    }

    // ------------------------------------------------------------------
    // The guide books
    // ------------------------------------------------------------------

    /**
     * The four books.
     *
     * <p>
     * The first is a book and quill written over a handful of every surface the mod is about - earth,
     * stone, gravel, sand, snow - bound with bone meal for the growing and an ender pearl for the
     * going. The other three are that book studied further, each with the thing its subject needs: an
     * eye and a diamond for the technical one, paper and redstone for the list of commands, a player's
     * head and packed earth for the golem's.
     *
     * <p>
     * Shapeless, because none of them is a shape - they are a pile of things read together. Where the
     * enhancements are on and the pack has compressed earth, the harder variants ask for that instead
     * of the plain block, so a GregTech pack pays a GregTech price.
     */
    private static void registerGuides() {
        ItemGuideBook mk1 = ModItems.guide(GuideBook.MK1);
        if (mk1 == null) return;
        boolean harder = TrmtConfig.gtnhEnhanced;

        // Mk I: a book and quill, bone meal, and one of every ground it talks about.
        add(
            OreRecipes.shapeless(
                null,
                new ItemStack(mk1),
                new ItemStack(net.minecraft.world.item.Items.WRITABLE_BOOK),
                new ItemStack(net.minecraft.world.item.Items.BONE_MEAL),
                new ItemStack(net.minecraft.world.level.block.Blocks.DIRT),
                new ItemStack(net.minecraft.world.level.block.Blocks.COBBLESTONE),
                new ItemStack(net.minecraft.world.level.block.Blocks.STONE),
                new ItemStack(net.minecraft.world.level.block.Blocks.GRAVEL),
                new ItemStack(net.minecraft.world.level.block.Blocks.SAND),
                new ItemStack(net.minecraft.world.item.Items.SNOWBALL),
                new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL)),
            GuideBook.MK1.itemName());

        registerSubGuide(
            GuideBook.MK2,
            mk1,
            new ItemStack(net.minecraft.world.item.Items.ENDER_EYE),
            new ItemStack(net.minecraft.world.item.Items.DIAMOND),
            new ItemStack(net.minecraft.world.item.Items.GLOWSTONE_DUST),
            harder ? compressed(3) : null);

        registerSubGuide(
            GuideBook.COMMANDS,
            mk1,
            new ItemStack(net.minecraft.world.item.Items.WRITABLE_BOOK),
            new ItemStack(net.minecraft.world.item.Items.PAPER),
            new ItemStack(net.minecraft.world.item.Items.REDSTONE),
            null);

        registerSubGuide(
            GuideBook.GOLEM,
            mk1,
            new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD),
            harder ? compressed(10) : new ItemStack(net.minecraft.world.level.block.Blocks.DIRT),
            new ItemStack(net.minecraft.world.level.block.Blocks.COBBLESTONE),
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
            if (extra != null && !extra.isEmpty()) parts.add(extra);
        }
        add(OreRecipes.shapeless(null, new ItemStack(result), parts.toArray()), book.itemName());
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
        boolean harder = TrmtConfig.gtnhEnhanced;

        if (TrmtConfig.reinforceEnabled && EnchReinforce.INSTANCE != null) {
            registerEnchantBook(
                EnchReinforce.INSTANCE,
                new ItemStack(net.minecraft.world.item.Items.BOOK),
                new ItemStack(net.minecraft.world.item.Items.DIAMOND),
                new ItemStack(net.minecraft.world.level.block.Blocks.OBSIDIAN),
                new ItemStack(net.minecraft.world.level.block.Blocks.OBSIDIAN),
                new ItemStack(net.minecraft.world.item.Items.LAPIS_LAZULI),
                new ItemStack(net.minecraft.world.item.Items.LAPIS_LAZULI),
                harder ? compressed(3) : null);
        }

        if (TrmtConfig.lightEnabled && EnchLight.INSTANCE != null) {
            registerEnchantBook(
                EnchLight.INSTANCE,
                new ItemStack(net.minecraft.world.item.Items.BOOK),
                new ItemStack(net.minecraft.world.level.block.Blocks.GLOWSTONE),
                new ItemStack(net.minecraft.world.item.Items.GLOWSTONE_DUST),
                new ItemStack(net.minecraft.world.item.Items.GLOWSTONE_DUST),
                new ItemStack(net.minecraft.world.item.Items.LAPIS_LAZULI),
                new ItemStack(net.minecraft.world.item.Items.LAPIS_LAZULI),
                harder ? compressed(7) : null);
        }

        if (TrmtConfig.wardEnabled && EnchWard.INSTANCE != null) {
            registerEnchantBook(
                EnchWard.INSTANCE,
                new ItemStack(net.minecraft.world.item.Items.BOOK),
                new ItemStack(net.minecraft.world.item.Items.ENDER_EYE),
                new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL),
                new ItemStack(net.minecraft.world.item.Items.GOLDEN_CARROT),
                new ItemStack(net.minecraft.world.item.Items.LAPIS_LAZULI),
                new ItemStack(net.minecraft.world.item.Items.LAPIS_LAZULI),
                harder ? compressed(11) : null);
        }
    }

    /**
     * One enchanted book carrying one of the mod's enchantments. Nulls among the parts are skipped.
     *
     * <p>
     * Two small differences from the other edition, both 1.12.2's: putting an enchantment on a book is
     * a static call on the class rather than one on the item, and a recipe has to say what it is
     * called. The name comes from the enchantment's own registry name, which exists here and did not
     * there - the other edition had nothing to call one but a number.
     */
    private static void registerEnchantBook(net.minecraft.world.item.enchantment.Enchantment enchantment, ItemStack... parts) {
        ItemStack result = new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK);
        net.minecraft.world.item.EnchantedBookItem
            .addEnchantment(result, new net.minecraft.world.item.enchantment.EnchantmentInstance(enchantment, 1));

        List<Object> ingredients = new ArrayList<Object>();
        for (ItemStack part : parts) {
            if (part != null && !part.isEmpty()) ingredients.add(part);
        }
        if (ingredients.isEmpty()) return;
        ResourceLocation named = net.minecraft.core.Registry.ENCHANTMENT.getKey(enchantment);
        if (named == null) return;
        add(OreRecipes.shapeless(null, result, ingredients.toArray()), "enchant_book_" + named.getPath());
        Trmt.LOG.info("Registered the {} enchant book recipe", named.getPath());
    }

    // ------------------------------------------------------------------
    // The golem's upgrades
    // ------------------------------------------------------------------

    /**
     * One recipe per upgrade, each priced in what it is about.
     *
     * <p>
     * Every one is built on packed stone, because it is a part fitted to a thing made of packed stone.
     * Past that they are what they do: pearls for reach, sugar and redstone for haste, obsidian for a
     * tool that lasts, emerald for spending less, a chest for holding more, bone meal and wheat for
     * growing, iron banded with obsidian for standing longer, and a diamond edge on flint for hitting
     * harder.
     *
     * <p>
     * The last is every other one at once, and there are nine of those - which is the whole of a
     * shapeless recipe and leaves nowhere to lay the nether star that used to bind them. So the nine
     * bind into the loose version on their own, and the star settles that afterwards. Two steps rather
     * than one, and the second is the one worth having: what the star buys is not power but knowing
     * which golem you have.
     *
     * <p>
     * The egg has no recipe in either edition and wants none: it is the rarest find in the mod, and
     * what it is for is being found. A golem is otherwise built, out of blocks and a head, which needs
     * no grid at all.
     */
    private static void registerGolemUpgrades() {
        if (!TrmtConfig.golemEnabled || !TrmtConfig.golemUpgrades) return;
        ItemStack stone = new ItemStack(net.minecraft.world.level.block.Blocks.STONE_BRICKS);
        boolean harder = TrmtConfig.gtnhEnhanced;

        upgrade(
            com.trmtgtnh.entity.GolemUpgrade.RANGE,
            stone,
            new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL),
            new ItemStack(net.minecraft.world.item.Items.ENDER_EYE),
            new ItemStack(net.minecraft.world.item.Items.REDSTONE));
        upgrade(
            com.trmtgtnh.entity.GolemUpgrade.SPEED,
            stone,
            new ItemStack(net.minecraft.world.item.Items.SUGAR),
            new ItemStack(net.minecraft.world.item.Items.SUGAR),
            new ItemStack(net.minecraft.world.item.Items.REDSTONE),
            new ItemStack(net.minecraft.world.item.Items.REDSTONE));
        upgrade(
            com.trmtgtnh.entity.GolemUpgrade.SPARING,
            stone,
            new ItemStack(net.minecraft.world.level.block.Blocks.OBSIDIAN),
            new ItemStack(net.minecraft.world.item.Items.IRON_INGOT),
            new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
        upgrade(
            com.trmtgtnh.entity.GolemUpgrade.FRUGAL,
            stone,
            new ItemStack(net.minecraft.world.item.Items.EMERALD),
            new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT),
            new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT));
        upgrade(
            com.trmtgtnh.entity.GolemUpgrade.DEEP,
            stone,
            new ItemStack(net.minecraft.world.level.block.Blocks.CHEST),
            new ItemStack(net.minecraft.world.item.Items.IRON_INGOT),
            new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
        upgrade(
            com.trmtgtnh.entity.GolemUpgrade.GREEN,
            stone,
            new ItemStack(net.minecraft.world.item.Items.BONE_MEAL),
            new ItemStack(net.minecraft.world.item.Items.BONE_MEAL),
            new ItemStack(net.minecraft.world.item.Items.WHEAT),
            new ItemStack(net.minecraft.world.item.Items.WHEAT));
        upgrade(
            com.trmtgtnh.entity.GolemUpgrade.STOUT,
            stone,
            new ItemStack(net.minecraft.world.level.block.Blocks.IRON_BLOCK),
            new ItemStack(net.minecraft.world.level.block.Blocks.OBSIDIAN),
            new ItemStack(net.minecraft.world.level.block.Blocks.OBSIDIAN));
        upgrade(
            com.trmtgtnh.entity.GolemUpgrade.FIERCE,
            stone,
            new ItemStack(net.minecraft.world.item.Items.DIAMOND),
            new ItemStack(net.minecraft.world.item.Items.FLINT),
            new ItemStack(net.minecraft.world.item.Items.FLINT));

        // Reaching into a chest that is not in front of it: a chest for the idea, a hopper for the
        // moving, pearls for the reach and gold for what a hopper and an ender chest are both made of.
        // Twice the parts the storage upgrade costs, because holding four times as much is a bigger
        // box and working out of somebody else's boxes is a different thing entirely.
        upgrade(
            com.trmtgtnh.entity.GolemUpgrade.SETTLED,
            stone,
            new ItemStack(net.minecraft.world.level.block.Blocks.CHEST),
            new ItemStack(net.minecraft.world.level.block.Blocks.HOPPER),
            new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL),
            new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL),
            new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT),
            new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT),
            new ItemStack(net.minecraft.world.item.Items.DIAMOND),
            harder ? compressed(3) : null);

        // Every upgrade at once. Nine of them is a full grid, so what they bind into first is the loose
        // version, and the star that settles it comes afterwards - which is why the star is not in this
        // list. Turn the loose one off and the nine bind straight into the settled one and the star is
        // not asked for at all, because there is nowhere left in the grid to put it.
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

        boolean loose = TrmtConfig.golemUnstableOmni && unstable != null;
        add(
            OreRecipes.shapeless(null, new ItemStack(loose ? unstable : omni), parts.toArray()),
            loose ? com.trmtgtnh.entity.GolemUpgrade.UNSTABLE.itemName()
                : com.trmtgtnh.entity.GolemUpgrade.OMNI.itemName());
        if (loose) {
            add(
                OreRecipes.shapeless(
                    null,
                    new ItemStack(omni),
                    new ItemStack(unstable),
                    new ItemStack(net.minecraft.world.item.Items.NETHER_STAR)),
                com.trmtgtnh.entity.GolemUpgrade.OMNI.itemName());
        }
        Trmt.LOG.info("Registered the golem upgrade recipes");
    }

    /** One upgrade, built on packed stone plus whatever it is about. */
    private static void upgrade(com.trmtgtnh.entity.GolemUpgrade which, ItemStack... parts) {
        ItemGolemUpgrade result = ModItems.upgrade(which);
        if (result == null) return;
        List<Object> ingredients = new ArrayList<Object>();
        for (ItemStack part : parts) {
            if (part != null && !part.isEmpty()) ingredients.add(part);
        }
        add(OreRecipes.shapeless(null, new ItemStack(result), ingredients.toArray()), which.itemName());
    }

    /**
     * A block of compressed cobblestone, where the pack has one, or nothing where it does not.
     *
     * <p>
     * Looked up by name rather than depended on, so the harder recipes simply do not appear on a pack
     * without them and the plain ones still do.
     *
     * <p>
     * The name is the other edition's, and which name Extra Utilities registers on 1.12.2 is the compat
     * milestone's to settle. Until then this resolves to nothing on a 1.12.2 pack and the settled
     * upgrade is built the plain way - which is the same thing that happens on any 1.7.10 pack without
     * the mod, and the recipe below is written to expect it.
     */
    private static ItemStack compressed(int tier) {
        // Nothing, at this version, and the honest answer is to say so rather than to guess.
        //
        // Both older editions ask Extra Utilities for its compressed cobblestone at a metadata
        // value - tier three, seven, ten, eleven. Two things stop that here: metadata is gone, so a
        // tier would have to be a separate registry name; and Extra Utilities has no 1.16.5 release,
        // so there is no name to use. A tier is not a thing that can be worked out from a
        // convention either - it is one mod's own ladder.
        //
        // Null is the path these recipes already take on a pack that has not got the mod: the
        // harder form is skipped and the plain one is registered instead. So a 1.16.5 pack gets the
        // plain recipes, which is right, and the day something fills that role here this is the one
        // method that has to learn its name.
        return null;
    }

    /**
     * The two draughts.
     *
     * <p>
     * Lightness keeps the original mod's own ingredients exactly - an awkward potion and a feather -
     * and moves only the apparatus. Upstream brews it in a stand; neither edition has a brewing
     * recipe, so both mix it in a grid instead. Where the enhancements are on and the pack rolls
     * something lighter than a feather, the harder form asks for that as well.
     *
     * <p>
     * The awkward potion is the one ingredient 1.12.2 changed the nature of. There it is a damage
     * value, which every recipe compares; here it is NBT, which no ordinary ingredient looks at - so
     * written the plain way this recipe would take a water bottle. The ingredient cannot be
     * asked here either, so the recipe asks instead; see {@link RecipeDraught}.
     */
    private static void registerDraughts() {
        if (!TrmtConfig.potionsEnabled) return;

        ItemDraught light = ModItems.draught(Draughts.LIGHTNESS);
        ItemDraught heavy = ModItems.draught(Draughts.HEAVYFOOT);
        boolean harder = TrmtConfig.gtnhEnhanced;

        if (light != null && TrmtConfig.potionLightness) {
            // A plain bottle as the ingredient, because an ingredient cannot be asked what is
            // in it at this version; the recipe asks instead. See RecipeDraught.
            ItemStack awkward = net.minecraft.world.item.alchemy.PotionUtils
                .setPotion(new ItemStack(net.minecraft.world.item.Items.POTION),
                    net.minecraft.world.item.alchemy.Potions.AWKWARD);
            String feathery = lightestOre();
            if (harder && feathery != null) {
                // Harder where the pack can be asked for something lighter than a feather. Three
                // feathers rather than one, a ghast tear for the part of it that is not weight but
                // buoyancy, and whatever rolled metal this pack considers light.
                add(
                    OreRecipes.draught(
                        null,
                        new ItemStack(light),
                        net.minecraft.world.item.alchemy.Potions.AWKWARD,
                        awkward,
                        new ItemStack(net.minecraft.world.item.Items.FEATHER),
                        new ItemStack(net.minecraft.world.item.Items.FEATHER),
                        new ItemStack(net.minecraft.world.item.Items.FEATHER),
                        new ItemStack(net.minecraft.world.item.Items.GHAST_TEAR),
                        feathery),
                    "draught_lightness");
            } else {
                add(
                    OreRecipes.draught(
                        null,
                        new ItemStack(light),
                        net.minecraft.world.item.alchemy.Potions.AWKWARD,
                        awkward,
                        new ItemStack(net.minecraft.world.item.Items.FEATHER)),
                    "draught_lightness");
            }
        }

        // Gated on lightness as well, because the light bottle is this recipe's own first
        // ingredient and nothing else in any pack makes one. Without this the heavy recipe went on
        // being registered with lightness switched off, which is a recipe the list draws and a
        // bench will never fulfil.
        if (heavy != null && light != null && TrmtConfig.potionHeavyFoot && TrmtConfig.potionLightness) {
            String weighty = heaviestOre();
            if (harder && weighty != null) {
                add(
                    OreRecipes.shapeless(
                        null,
                        new ItemStack(heavy),
                        new ItemStack(light),
                        new ItemStack(net.minecraft.world.item.Items.FERMENTED_SPIDER_EYE),
                        weighty,
                        weighty),
                    "draught_heavyfoot");
            } else {
                add(
                    OreRecipes.shapeless(
                        null,
                        new ItemStack(heavy),
                        new ItemStack(light),
                        new ItemStack(net.minecraft.world.item.Items.FERMENTED_SPIDER_EYE)),
                    "draught_heavyfoot");
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
     * One recipe, under a name of its own.
     *
     * <p>
     * The name is what 1.12.2 adds and 1.7.10 had no use for: a recipe is a registry entry here, and
     * two entries may not share a name. Every one of these is a grade or a tool, so the name is that,
     * and a pack that adds a grade gets a name nobody else has used.
     */
    /**
     * Hands one finished recipe to whatever asked for them.
     *
     * <p>
     * Both older editions put it in a registry here. There is no registry for recipes at this
     * version and no event for adding one, so {@code ExtraRecipes} collects them and puts them in
     * front of the game itself; see its javadoc for why JSON is not open to this mod.
     *
     * <p>
     * The id is made here rather than passed in, exactly as it was, so the names on the wire are
     * the names the other editions use and a pack's override still lands on the right recipe.
     */
    private static void add(java.util.function.Function<net.minecraft.resources.ResourceLocation, net.minecraft.world.item.crafting.Recipe<?>> make, String name) {
        if (collecting == null) {
            throw new IllegalStateException(
                "A recipe was built outside ModRecipes.register, so there is nowhere to put it. "
                    + "Whatever called this has to go through register(Collector).");
        }
        ResourceLocation id = new ResourceLocation(Trmt.MODID, name);
        collecting.accept(id, make.apply(id));
    }

    /**
     * Where recipes are being collected this pass, or null outside one.
     *
     * <p>
     * A field rather than an argument threaded through nine private methods, and it is allowed to be
     * one because it cannot fail quietly: {@code add} throws when it is not set, and recipes are
     * built in one call on one thread. That is the whole difference between this and the hidden
     * global that cost this port a month on the ghost seat - that one answered null and drew
     * nothing.
     */
    private static ExtraRecipes.Collector collecting;

    private static boolean hasOre(String name) {
        // One question rather than two: a tag that nothing is in and a tag that does not exist are
        // the same answer here, and OreNames already knows which tag an ore name means.
        return com.trmtgtnh.util.OreNames.present(name);
    }
}
