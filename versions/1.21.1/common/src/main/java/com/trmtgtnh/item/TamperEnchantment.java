package com.trmtgtnh.item;

import java.util.function.BooleanSupplier;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;

/**
 * One of the three enchantments that unlock a big tamper's modes, and nothing else.
 *
 * <p>
 * <strong>1.21 made enchantments data</strong>, and that is the whole difference from 1.20.1, where this was a
 * subclass of {@code Enchantment} answering questions. {@code Enchantment} is a final record now, read from
 * {@code data/<namespace>/enchantment/<name>.json} when a world loads, so there is nothing to subclass and nothing to
 * register: the three are {@code data/trmtgtnh/enchantment/reinforce.json}, {@code ward.json} and {@code light.json},
 * and what this class keeps is the name each is looked up by, its translation key and the feature switch it answers to.
 *
 * <p>
 * What 1.20.1's methods answered is answered in those files and their tags, with the same values:
 * <ul>
 * <li>Rare: weight 2, and an anvil cost of 4, which is what a rare enchantment cost at an anvil before the cost was
 * written down per enchantment. One level. The costs are what 1.20.1's inherited {@code getMinCost} and
 * {@code getMaxCost} gave: 1 + 10 per level, and five above that.</li>
 * <li>{@code canEnchant} - the chunk tamper alone, the magic tamper by inheritance: {@code supported_items} is
 * {@code #trmtgtnh:enchantable/unlocks}, which names exactly those two.</li>
 * <li>{@code isDiscoverable} and {@code isTradeable} - the three are in {@code #minecraft:in_enchanting_table} and
 * {@code #minecraft:tradeable}, and not in {@code #minecraft:on_random_loot}: vanilla's chests draw their books from
 * that tag, so 1.7.10's rule (spec BP29: no chest book is an unlock, but this mod's own entry) holds for vanilla's
 * tables with nothing done at all.</li>
 * <li>{@code getDescriptionId} - the key is the older editions' own, {@code enchantment.trmtReinforce}, written in
 * each file's {@code description}, so one language file still serves every edition.</li>
 * </ul>
 *
 * <p>
 * The registry names are 1.20.1's, {@code trmtgtnh:reinforce} and the rest, so a tool enchanted in a 1.20.1 world
 * still carries its unlock when that world is opened here: the game's own data fixer turns the old enchantment list
 * into the enchantments component under the same names.
 *
 * <p>
 * <strong>What data cannot say is the feature switch</strong>, which 1.20.1 read live in each of those methods. A tag
 * is read once, when the world loads; a switch is changed on the settings screen. So the switch is asked where the game
 * decides - {@code MixinUnlocksAnswerTheirSwitch} at the anvil, {@code MixinTableOffersUnlocks} at the table and
 * {@code MixinLibrarianDrawsAgain} at the librarian - each through {@link #offered}. Turned off, an unlock stays registered, so a tool that carries one
 * still reads as carrying it, as in every edition.
 */
public final class TamperEnchantment {

    private static int LESSONS;

    public static final TamperEnchantment REINFORCE = new TamperEnchantment("reinforce", "trmtReinforce",
        () -> TrmtConfig.reinforceEnabled);
    public static final TamperEnchantment WARD = new TamperEnchantment("ward", "trmtWard", () -> TrmtConfig.wardEnabled);
    public static final TamperEnchantment LIGHT = new TamperEnchantment("light", "trmtLight",
        () -> TrmtConfig.lightEnabled);

    private static final TamperEnchantment[] ALL = { REINFORCE, WARD, LIGHT };

    /** The name it is looked up by in the enchantment registry: {@code trmtgtnh:reinforce}, as in 1.20.1. */
    public final ResourceKey<Enchantment> key;

    /** The translation key, the older editions' own. */
    public final String descriptionId;

    /**
     * The number the quest book knows it by: 0, 1 or 2. The older editions use the enchantment's registry number, which
     * a quest book stores; at this version that number is whatever a world's registry gave a piece of data, so the
     * chapter numbers each lesson itself and names the enchantment where it writes a book.
     */
    public final int lesson;

    private final BooleanSupplier enabled;

    private TamperEnchantment(String path, String name, BooleanSupplier enabled) {
        this.lesson = LESSONS++;
        this.key = ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(Trmt.MODID, path));
        this.descriptionId = "enchantment." + name;
        this.enabled = enabled;
    }

    /** The feature switch this one answers to. Read live. */
    public boolean enabled() {
        return enabled.getAsBoolean();
    }

    /**
     * Whether a stack carries this one, at any level. Read off the stack's own enchantments, which name each by its
     * registry entry, so no registry has to be at hand - a screen asks this as readily as the server does.
     */
    public boolean on(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return in(stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
    }

    /**
     * Whether an enchanted book teaches this one. A book keeps what it teaches apart from what it is enchanted with,
     * in its stored enchantments, as every edition's book does, so {@link #on} answers no for one.
     */
    public boolean taughtBy(ItemStack book) {
        if (book == null || book.isEmpty()) return false;
        return in(book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY));
    }

    private boolean in(ItemEnchantments carried) {
        for (Holder<Enchantment> each : carried.keySet()) {
            if (each.is(key) && carried.getLevel(each) > 0) return true;
        }
        return false;
    }

    /** This one's registry entry in a world's registries, or null where a data pack has taken it out. */
    public Holder<Enchantment> holder(HolderLookup.Provider registries) {
        if (registries == null) return null;
        return registries.lookupOrThrow(Registries.ENCHANTMENT)
            .get(key)
            .orElse(null);
    }

    /** The unlock a quest lesson names, or null for a number that is none of the three. */
    public static TamperEnchantment byLesson(int lesson) {
        return lesson >= 0 && lesson < ALL.length ? ALL[lesson] : null;
    }

    /**
     * Which of the three this entry is, or null for any other enchantment. By its registry name, and where the entry
     * has none - a holder made directly round an enchantment, which a mod may hand about - by the enchantment's own.
     */
    public static TamperEnchantment of(Holder<Enchantment> holder) {
        if (holder == null) return null;
        for (TamperEnchantment each : ALL) {
            if (holder.is(each.key)) return each;
        }
        return holder.unwrapKey()
            .isPresent() ? null : of(holder.value());
    }

    /**
     * Which of the three this is, asked of the enchantment alone - for the questions the game asks an
     * {@code Enchantment} without its registry entry beside it, as the anvil does. By the translation key, which is
     * this mod's own and the one thing an enchantment carries that names it.
     */
    public static TamperEnchantment of(Enchantment enchantment) {
        if (enchantment == null || !(enchantment.description()
            .getContents() instanceof TranslatableContents named)) return null;
        for (TamperEnchantment each : ALL) {
            if (each.descriptionId.equals(named.getKey())) return each;
        }
        return null;
    }

    /** False only for one of the three whose feature is switched off. Every other enchantment is offered. */
    public static boolean offered(Holder<Enchantment> holder) {
        TamperEnchantment ours = of(holder);
        return ours == null || ours.enabled();
    }

    /** {@link #offered(Holder)}, asked of the enchantment alone. */
    public static boolean offered(Enchantment enchantment) {
        TamperEnchantment ours = of(enchantment);
        return ours == null || ours.enabled();
    }
}
