package com.trmtgtnh.item;

import java.util.List;


import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;

/**
 * A bottle that is drunk rather than thrown, carrying one of the two draughts.
 *
 * <p>
 * Drunk the way vanilla drinks anything: held for thirty-two ticks with the drinking animation, and
 * the empty bottle handed back afterwards, which is what makes the cost of one a bottle rather than
 * a bottle and the glass. The effect is applied on the server and nowhere else - a client that
 * granted itself the effect would show it in the inventory panel while the server went on wearing
 * the ground underneath, which is the exact shape of quiet failure this mod has shipped behind
 * twice, and it looks like the feature working.
 */
public class ItemDraught extends Item {

    private final Draughts draught;

    public ItemDraught(Draughts draught) {
        // The registry id first, which 1.21.2 requires. The drink component is how the game sounds a drink from
        // 1.21.2: 1.21.1's entity played the drinking sound itself for anything drunk with the drinking animation,
        // every fourth tick after the first seven and once more at the end, and from 1.21.2 that is the
        // consumable component's work. Vanilla's plain drink is thirty-two ticks of the same sound with no
        // particles, which is 1.21.1's to the tick; the gestures and the effect stay this class's own, below.
        super(ModItems.properties(draught.itemName()).stacksTo(1)
            .component(net.minecraft.core.component.DataComponents.CONSUMABLE,
                net.minecraft.world.item.component.Consumables.DEFAULT_DRINK));
        this.draught = draught;
    }

    public Draughts draught() {
        return draught;
    }

    @Override
    public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity drinker) {
        return 32;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.DRINK;
    }

    @Override
    public InteractionResult use(Level world, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        // A plain result from 1.21.2, which dropped the holder; the stack in the hand is unchanged, as in 1.21.1.
        return InteractionResult.SUCCESS;
    }

    /**
     * What happens when the drinking finishes.
     *
     * <p>
     * Called for anything that can drink rather than for a player alone, which is 1.12.2's shape for
     * this hook; the chat lines below are a player's, so they are asked for only where there is one.
     */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity drinker) {
        Player player = drinker instanceof Player ? (Player) drinker : null;
        // The last swallow's sound, which 1.21.1's entity played just before this was called; from 1.21.2 it is the
        // consumable component's to play, and this class does not hand the drink to that component.
        net.minecraft.world.item.component.Consumable drink = stack.get(net.minecraft.core.component.DataComponents.CONSUMABLE);
        if (drink != null) drink.emitParticlesAndSounds(drinker.getRandom(), drinker, stack, 16);
        if (!world.isClientSide()) {
            MobEffect effect = draught.effect();
            if (effect == null) {
                // Nothing was registered for this one. Said to the player rather than swallowed,
                // because a bottle that does nothing and says nothing is indistinguishable from a bug.
                say(player, "trmtgtnh.draught.unclaimed");
            } else if (!draught.enabled()) {
                say(player, "trmtgtnh.draught.disabled");
            } else {
                drinker.addEffect(new MobEffectInstance(ModPotions.holder(effect), draught.seconds() * 20, 0));
            }
        }

        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
            if (stack.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
            if (player != null && !player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) {
                // From 26.3 a drop says whether the client predicted it; a drink is played on both sides, and the
                // game's own bottle after one is dropped as predicted (ItemUtils.createFilledResult, 26.3).
                player.drop(new ItemStack(Items.GLASS_BOTTLE), false, net.minecraft.util.Prediction.PREDICTED);
            }
        }
        return stack;
    }

    private static void say(Player player, String key) {
        if (player == null) return;
        player.sendSystemMessage(Component.literal(ChatFormatting.GRAY + com.trmtgtnh.util.Translate.get(key)));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // The enchantment shimmer, so a draught reads as something more than a colored bottle.
        return true;
    }

    /** See {@link ItemTamper#addInformation} for why this one says which side it is on. */
    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
        net.minecraft.world.item.component.TooltipDisplay display,
        java.util.function.Consumer<net.minecraft.network.chat.Component> given, TooltipFlag advanced) {
        List<String> lines = Tooltips.lines(given);
        lines.add(ChatFormatting.GRAY + com.trmtgtnh.util.Translate.get(draught.tooltipKey()));

        MobEffect effect = draught.effect();
        if (effect == null) {
            lines.add(ChatFormatting.RED + com.trmtgtnh.util.Translate.get("trmtgtnh.draught.unclaimed"));
            return;
        }
        if (!draught.enabled()) {
            lines.add(ChatFormatting.RED + com.trmtgtnh.util.Translate.get("trmtgtnh.draught.disabled"));
            return;
        }
        // The effect line vanilla would have drawn on a brewed potion, in the color vanilla uses
        // for one that helps and one that does not.
        String name = com.trmtgtnh.util.Translate.get(effect.getDescriptionId());
        lines.add(
            (effect.getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL ? ChatFormatting.RED
                : ChatFormatting.BLUE) + name
                + " ("
                + clock(draught.seconds())
                + ")");
    }

    /** Minutes and seconds, the way the effect panel writes them. */
    private static String clock(int seconds) {
        int minutes = seconds / 60;
        int rest = seconds % 60;
        return minutes + (rest < 10 ? ":0" : ":") + rest;
    }
}
