package com.trmtgtnh.mixin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.ResourceKey;

/**
 * Offers this mod's recipes to {@code /recipe}'s tab completion again (0.9.228; Xep: "Add a small hook").
 *
 * <p>
 * 26.3 completes a recipe argument from the recipe registry: {@code ResourceKeyArgument.listSuggestions} goes through
 * {@code SharedSuggestionProvider.listSuggestions} to the five-argument {@code CommandSourceStack
 * .suggestRegistryElements}, which gained a {@code Predicate} in 26.3 and asks only {@code getLookup(key)} - a pack's
 * files, not what this mod adds to the manager ({@code ExtraRecipes}). 26.2's answered from the manager, which holds
 * them, and so does 26.1.2's: this is 26.3's alone. A client has no recipe registry and asks the server, so the hook on
 * the server's source serves single player and multiplayer alike; {@code /recipe give <player> trmtgtnh:<id>} and
 * {@code *} work without it. At the return, so the game's own answer is kept and only added to; optional, because a
 * loss costs a suggestion, never a start. The body only calls out, for the reason {@link MixinPackRepository} gives.
 * The filter is the argument's own, asked of a registry element; this mod's recipes are none, so they are left in.
 */
@Mixin(CommandSourceStack.class)
public abstract class MixinRecipeSuggestions {

    @Inject(
        method = "suggestRegistryElements(Lnet/minecraft/resources/ResourceKey;"
            + "Lnet/minecraft/commands/SharedSuggestionProvider$ElementSuggestionType;"
            + "Lcom/mojang/brigadier/suggestion/SuggestionsBuilder;Lcom/mojang/brigadier/context/CommandContext;"
            + "Ljava/util/function/Predicate;)Ljava/util/concurrent/CompletableFuture;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0)
    private void trmt$suggestOurRecipes(ResourceKey<?> registry, SharedSuggestionProvider.ElementSuggestionType type,
        SuggestionsBuilder builder, CommandContext<?> context, Predicate<?> filter,
        CallbackInfoReturnable<CompletableFuture<Suggestions>> callback) {
        CompletableFuture<Suggestions> with = com.trmtgtnh.item.ExtraRecipes
            .suggestions(registry, type, builder, ((CommandSourceStack) (Object) this).getServer());
        if (with != null) callback.setReturnValue(with);
    }
}
