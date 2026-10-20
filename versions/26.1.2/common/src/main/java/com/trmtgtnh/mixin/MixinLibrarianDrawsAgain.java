package com.trmtgtnh.mixin;

import java.util.List;
import java.util.Optional;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import com.trmtgtnh.item.UnlockBooks;

/**
 * A librarian's book trade never offers a switched-off unlock.
 *
 * <p>
 * See {@link UnlockBooks#forLibrarian}. The librarian draws from {@code #minecraft:tradeable}, which holds the three
 * because 1.20.1's {@code isTradeable} answered yes while each was switched on; a tag cannot follow the switch, so the
 * draw is wrapped and drawn again where it landed on one that is off - the same hook as 1.7.10's and 1.12.2's
 * {@code MixinLibrarianBooks}, on this version's draw. {@code require = 0}: a loader that has rewritten the trade gets
 * a librarian who now and then offers a book its switch has turned off, rather than a game that will not start - and
 * the harness's librarian spike counts the books offered, so that would be seen.
 *
 * <p>
 * <strong>Where the draw is from 26.1.</strong> 1.21.1 wrapped {@code Registry.getRandomElementOf(tag, random)} in
 * {@code VillagerTrades$EnchantBookForEmeralds.getOffer}. From 26.1 villager trades are data
 * ({@code data/minecraft/villager_trade/librarian/*}, read through the {@code TRADE_SET} registry) and that class is
 * gone: each librarian book trade gives an enchanted book through the {@code minecraft:enchant_randomly} loot function
 * with {@code "options": "#minecraft:tradeable"} (the trade rebalance pack's name their biome's tag instead), and the
 * function draws the enchantment with {@code Util.getRandomSafe(list, random)} before it draws the level and the price
 * from the same random. So that draw is the one wrapped, with 1.21.1's rule: only while a trade is being rolled - the
 * game hands {@code ADDITIONAL_COST_COMPONENT_ALLOWED} to a loot context only there, and in 26.1.2's own data only the
 * librarian's trades enchant at random - and only where the function names a tag, which is what 1.21.1's trade drew
 * from and redrew from. A chest's book is {@code MixinChestBooksDrawAgain}'s, and a function that names its
 * enchantments one by one names them on purpose.
 */
@Mixin(EnchantRandomlyFunction.class)
public abstract class MixinLibrarianDrawsAgain {

    @Shadow
    @Final
    private Optional<HolderSet<Enchantment>> options;

    @WrapOperation(
        method = "run",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/Util;getRandomSafe(Ljava/util/List;Lnet/minecraft/util/RandomSource;)Ljava/util/Optional;"),
        require = 0)
    private Optional<Holder<Enchantment>> trmt$drawAgainWithoutSwitchedOff(List<Holder<Enchantment>> offered,
        RandomSource random, Operation<Optional<Holder<Enchantment>>> original,
        @Local(argsOnly = true) LootContext context) {
        Optional<Holder<Enchantment>> drawn = original.call(offered, random);
        if (context == null || !context.hasParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED)) return drawn;
        if (options == null || options.isEmpty() || !(options.get() instanceof HolderSet.Named<Enchantment> tag)) {
            return drawn;
        }
        return UnlockBooks.forLibrarian(
            drawn,
            context.getLevel()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT),
            tag.key(),
            random);
    }
}
