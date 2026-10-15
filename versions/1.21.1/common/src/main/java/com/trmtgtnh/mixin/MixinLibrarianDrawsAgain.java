package com.trmtgtnh.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.Enchantment;

import com.trmtgtnh.item.UnlockBooks;

/**
 * A librarian's book trade never offers a switched-off unlock.
 *
 * <p>
 * See {@link UnlockBooks#forLibrarian}. The librarian draws from {@code #minecraft:tradeable}, which holds the three
 * because 1.20.1's {@code isTradeable} answered yes while each was switched on; a tag cannot follow the switch, so the
 * draw is wrapped and drawn again where it landed on one that is off - the same hook as 1.7.10's and 1.12.2's
 * {@code MixinLibrarianBooks}, on 1.21's draw. {@code require = 0}: a loader that has rewritten the trade gets a
 * librarian who now and then offers a book its switch has turned off, rather than a game that will not start - and the
 * harness's librarian spike counts the books offered, so that would be seen.
 */
@Mixin(targets = "net.minecraft.world.entity.npc.VillagerTrades$EnchantBookForEmeralds")
public abstract class MixinLibrarianDrawsAgain {

    @WrapOperation(
        method = "getOffer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/Registry;getRandomElementOf(Lnet/minecraft/tags/TagKey;Lnet/minecraft/util/RandomSource;)Ljava/util/Optional;"),
        require = 0)
    private Optional<Holder<Enchantment>> trmt$drawAgainWithoutSwitchedOff(Registry<Enchantment> registry,
        TagKey<Enchantment> tradeable, RandomSource random, Operation<Optional<Holder<Enchantment>>> original) {
        return UnlockBooks.forLibrarian(original.call(registry, tradeable, random), registry, tradeable, random);
    }
}
