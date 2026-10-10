package com.trmtgtnh.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

import com.trmtgtnh.Trmt;

/**
 * Where the three tamper enchantments are made, and the seam that lets two loaders register them.
 *
 * <p>
 * The 1.12.2 edition keeps this as a Forge registry event with three lines in it. There is no such
 * event in a module two loaders share, so this is the arrangement {@code ModBlocks}, {@code
 * ModPotions} and {@code ModItems} are already under: the enchantments are made here and handed to
 * whatever the loader supplies for putting them away.
 *
 * <p>
 * Each is made once however often this is called, and it is called more than once - Forge fires a
 * separate event per registry and each comes back through here. A second instance would leave the
 * registry holding one and every {@code has()} test asking about another, which reads in game as an
 * enchantment that applies and then does nothing.
 */
public final class ModEnchantments {

    /** What a loader module supplies: somewhere to put an enchantment under a name. */
    public interface Registrar {

        void enchantment(ResourceLocation name, Enchantment enchantment);
    }

    private ModEnchantments() {}

    /**
     * Makes the three and hands them over.
     *
     * <p>
     * The registry name is given here rather than set on the enchantment, which is the whole of what
     * changed at this version: an enchantment used to carry its own name and is now told it by
     * whoever registers it. Its translation key is still its own - see {@link TamperEnchantment} for
     * why that one did not move.
     */
    public static void register(Registrar into) {
        into.enchantment(new ResourceLocation(Trmt.MODID, "reinforce"), reinforce());
        into.enchantment(new ResourceLocation(Trmt.MODID, "ward"), ward());
        into.enchantment(new ResourceLocation(Trmt.MODID, "light"), light());
    }

    private static Enchantment reinforce() {
        return EnchReinforce.INSTANCE == null ? EnchReinforce.create() : EnchReinforce.INSTANCE;
    }

    private static Enchantment ward() {
        return EnchWard.INSTANCE == null ? EnchWard.create() : EnchWard.INSTANCE;
    }

    private static Enchantment light() {
        return EnchLight.INSTANCE == null ? EnchLight.create() : EnchLight.INSTANCE;
    }

    /**
     * Puts the three where an enchanting table should offer them, and only there (spec RL2): Fabric's table asks an
     * enchantment's category alone, and the category is DIGGER, which takes every digging tool and no chunk tamper -
     * so it offered the unlocks on a pickaxe and never on the one tool they are for. Taken off any other tool the
     * table put them on (a plain book keeps them: the table offers a book everything discoverable, and
     * {@code isDiscoverable} is the feature switch), then added to a chunk tamper, asked the questions the table asks
     * every other enchantment - treasure, discoverable, the tool, and the cost window for each level from the top - and
     * the question the other editions' {@code canApplyAtEnchantingTable} asks, through {@code canEnchant}. Forge's
     * table asks that question itself (see TamperEnchantment), so only Fabric's {@code MixinTableOffersUnlocks} calls
     * this, with the list the table has just built.
     */
    public static void offerAtTable(int power, net.minecraft.world.item.ItemStack stack, boolean treasure,
        java.util.List<net.minecraft.world.item.enchantment.EnchantmentInstance> offered) {
        if (offered == null || stack == null) return;
        if (stack.getItem() != net.minecraft.world.item.Items.BOOK) {
            java.util.Iterator<net.minecraft.world.item.enchantment.EnchantmentInstance> each = offered.iterator();
            while (each.hasNext()) {
                Enchantment one = each.next().enchantment;
                if (one instanceof TamperEnchantment && !one.canEnchant(stack)) each.remove();
            }
        }
        if (!(stack.getItem() instanceof ItemChunkTamper)) return;
        for (Enchantment each : new Enchantment[] { EnchReinforce.INSTANCE, EnchWard.INSTANCE, EnchLight.INSTANCE }) {
            if (each == null || each.isTreasureOnly() && !treasure || !each.isDiscoverable() || !each.canEnchant(stack)) {
                continue;
            }
            boolean already = false;
            for (net.minecraft.world.item.enchantment.EnchantmentInstance one : offered) {
                if (one.enchantment == each) already = true;
            }
            if (already) continue;
            for (int level = each.getMaxLevel(); level >= each.getMinLevel(); level--) {
                if (power >= each.getMinCost(level) && power <= each.getMaxCost(level)) {
                    offered.add(new net.minecraft.world.item.enchantment.EnchantmentInstance(each, level));
                    break;
                }
            }
        }
    }

    /**
     * A chest book drawn again if vanilla's draw landed on one of the three (0.9.222, spec BP29).
     *
     * <p>
     * The 1.7.10 edition's chests never hand out an unlock book: their enchanted book is rolled at level thirty, past
     * anything the three can reach, so this mod's own loot entry is the only chest that gives one. This version's
     * random enchant draws evenly from every enchantment that answers {@code isDiscoverable}, and an unlock answers
     * yes while its feature is on, because a table asks the same question before it offers one. So the book is drawn
     * again the way the game drew it - evenly, at any level the enchantment has - from what the game would have drawn
     * from, less the three, and every other book's odds stand as they were. Called by each loader's
     * {@code MixinChestBooksDrawAgain} with what the draw made.
     */
    public static net.minecraft.world.item.ItemStack withoutUnlocks(net.minecraft.world.item.ItemStack book,
        java.util.Random rand) {
        if (book == null || book.getItem() != net.minecraft.world.item.Items.ENCHANTED_BOOK || rand == null) return book;
        boolean unlock = false;
        for (Enchantment each : net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(book)
            .keySet()) {
            if (each instanceof TamperEnchantment) unlock = true;
        }
        if (!unlock) return book;
        java.util.List<Enchantment> others = new java.util.ArrayList<Enchantment>();
        for (Enchantment each : net.minecraft.core.Registry.ENCHANTMENT) {
            if (each.isDiscoverable() && !(each instanceof TamperEnchantment)) others.add(each);
        }
        if (others.isEmpty()) return new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK);
        Enchantment drawn = others.get(rand.nextInt(others.size()));
        return net.minecraft.world.item.EnchantedBookItem.createForEnchantment(
            new net.minecraft.world.item.enchantment.EnchantmentInstance(
                drawn,
                net.minecraft.util.Mth.nextInt(rand, drawn.getMinLevel(), drawn.getMaxLevel())));
    }
}
