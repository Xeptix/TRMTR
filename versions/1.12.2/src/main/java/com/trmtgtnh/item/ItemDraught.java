package com.trmtgtnh.item;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

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
        this.draught = draught;
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.MISC);
    }

    public Draughts draught() {
        return draught;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 32;
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.DRINK;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        player.setActiveHand(hand);
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    /**
     * What happens when the drinking finishes.
     *
     * <p>
     * Called for anything that can drink rather than for a player alone, which is 1.12.2's shape for
     * this hook; the chat lines below are a player's, so they are asked for only where there is one.
     */
    @Override
    public ItemStack onItemUseFinish(ItemStack stack, World world, EntityLivingBase drinker) {
        EntityPlayer player = drinker instanceof EntityPlayer ? (EntityPlayer) drinker : null;
        if (!world.isRemote) {
            Potion effect = draught.effect();
            if (effect == null) {
                // Nothing was registered for this one. Said to the player rather than swallowed,
                // because a bottle that does nothing and says nothing is indistinguishable from a bug.
                say(player, "trmtgtnh.draught.unclaimed");
            } else if (!draught.enabled()) {
                say(player, "trmtgtnh.draught.disabled");
            } else {
                drinker.addPotionEffect(new PotionEffect(effect, draught.seconds() * 20, 0));
            }
        }

        if (player == null || !player.capabilities.isCreativeMode) {
            stack.shrink(1);
            if (stack.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
            if (player != null && !player.inventory.addItemStackToInventory(new ItemStack(Items.GLASS_BOTTLE))) {
                player.dropItem(new ItemStack(Items.GLASS_BOTTLE), false);
            }
        }
        return stack;
    }

    private static void say(EntityPlayer player, String key) {
        if (player == null) return;
        player.sendMessage(new TextComponentString(TextFormatting.GRAY + I18n.translateToLocal(key)));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack) {
        // The enchantment shimmer, so a draught reads as something more than a colored bottle.
        return true;
    }

    /** See {@link ItemTamper#addInformation} for why this one says which side it is on. */
    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> lines, ITooltipFlag advanced) {
        lines.add(TextFormatting.GRAY + I18n.translateToLocal(draught.tooltipKey()));

        Potion effect = draught.effect();
        if (effect == null) {
            lines.add(TextFormatting.RED + I18n.translateToLocal("trmtgtnh.draught.unclaimed"));
            return;
        }
        if (!draught.enabled()) {
            lines.add(TextFormatting.RED + I18n.translateToLocal("trmtgtnh.draught.disabled"));
            return;
        }
        // The effect line vanilla would have drawn on a brewed potion, in the color vanilla uses
        // for one that helps and one that does not.
        String name = I18n.translateToLocal(effect.getName());
        lines.add(
            (effect.isBadEffect() ? TextFormatting.RED : TextFormatting.BLUE) + name
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
