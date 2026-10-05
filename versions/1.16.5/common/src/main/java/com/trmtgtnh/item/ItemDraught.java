package com.trmtgtnh.item;

import java.util.List;


import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.chat.TextComponent;
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
        super(new Item.Properties().stacksTo(1)
            .tab(CreativeModeTab.TAB_MISC));
        this.draught = draught;
    }

    public Draughts draught() {
        return draught;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return new InteractionResultHolder<ItemStack>(InteractionResult.SUCCESS, player.getItemInHand(hand));
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
        if (!world.isClientSide()) {
            MobEffect effect = draught.effect();
            if (effect == null) {
                // Nothing was registered for this one. Said to the player rather than swallowed,
                // because a bottle that does nothing and says nothing is indistinguishable from a bug.
                say(player, "trmtgtnh.draught.unclaimed");
            } else if (!draught.enabled()) {
                say(player, "trmtgtnh.draught.disabled");
            } else {
                drinker.addEffect(new MobEffectInstance(effect, draught.seconds() * 20, 0));
            }
        }

        if (player == null || !player.abilities.instabuild) {
            stack.shrink(1);
            if (stack.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
            if (player != null && !player.inventory.add(new ItemStack(Items.GLASS_BOTTLE))) {
                player.drop(new ItemStack(Items.GLASS_BOTTLE), false);
            }
        }
        return stack;
    }

    private static void say(Player player, String key) {
        if (player == null) return;
        player.sendMessage(
            new TextComponent(ChatFormatting.GRAY + com.trmtgtnh.util.Translate.get(key)),
            net.minecraft.Util.NIL_UUID);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // The enchantment shimmer, so a draught reads as something more than a coloured bottle.
        return true;
    }

    /** See {@link ItemTamper#addInformation} for why this one says which side it is on. */
    @Override
    public void appendHoverText(ItemStack stack, Level world,
        List<net.minecraft.network.chat.Component> given, TooltipFlag advanced) {
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
        // The effect line vanilla would have drawn on a brewed potion, in the colour vanilla uses
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
