package com.trmtgtnh.item;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.trmtgtnh.Trmt;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

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
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.tabMisc);
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
        return EnumAction.drink;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        player.setItemInUse(stack, getMaxItemUseDuration(stack));
        return stack;
    }

    @Override
    public ItemStack onEaten(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            Potion effect = draught.effect();
            if (effect == null) {
                // Nothing was claimed for this one at startup. Said to the player rather than
                // swallowed, because a bottle that does nothing and says nothing is indisting-
                // uishable from a bug, and the log line explaining it was printed hours ago.
                say(player, "trmtgtnh.draught.unclaimed");
            } else if (!draught.enabled()) {
                say(player, "trmtgtnh.draught.disabled");
            } else {
                player.addPotionEffect(new PotionEffect(effect.getId(), draught.seconds() * 20, 0));
            }
        }

        if (!player.capabilities.isCreativeMode) {
            stack.stackSize--;
            if (stack.stackSize <= 0) return new ItemStack(Items.glass_bottle);
            if (!player.inventory.addItemStackToInventory(new ItemStack(Items.glass_bottle))) {
                player.dropPlayerItemWithRandomChoice(new ItemStack(Items.glass_bottle), false);
            }
        }
        return stack;
    }

    private static void say(EntityPlayer player, String key) {
        player.addChatMessage(
            new net.minecraft.util.ChatComponentText(EnumChatFormatting.GRAY + StatCollector.translateToLocal(key)));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack, int pass) {
        // The enchantment shimmer, so a draught reads as something more than a colored bottle.
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(draught.tooltipKey()));

        Potion effect = draught.effect();
        if (effect == null) {
            lines.add(EnumChatFormatting.RED + StatCollector.translateToLocal("trmtgtnh.draught.unclaimed"));
            return;
        }
        if (!draught.enabled()) {
            lines.add(EnumChatFormatting.RED + StatCollector.translateToLocal("trmtgtnh.draught.disabled"));
            return;
        }
        // The effect line vanilla would have drawn on a brewed potion, in the color vanilla uses
        // for one that helps and one that does not.
        String name = StatCollector.translateToLocal(effect.getName());
        lines.add(
            (effect.isBadEffect() ? EnumChatFormatting.RED : EnumChatFormatting.BLUE) + name
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

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        itemIcon = register.registerIcon(Trmt.MODID + ":" + draught.itemName());
    }
}
