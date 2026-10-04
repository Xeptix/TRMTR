package com.trmtgtnh.item;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.entity.EntityGolemOfWays;

/**
 * A Golem of Ways in the hand, waiting to be put down.
 *
 * <p>
 * The mod's own item rather than a vanilla spawn egg, because a vanilla egg needs a global entity
 * id and those are worth more than this. It also means the rarest find in the mod gets to look like
 * the thing it makes.
 *
 * <p>
 * Right-click a block to set one on top of it. It remembers who put it there, the same as one that
 * was built.
 */
public class ItemGolemEgg extends Item {

    public ItemGolemEgg() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.MISC);
    }

    /** It glints. It is the rarest thing here. */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack) {
        return true;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing,
        float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (world.isRemote) return EnumActionResult.SUCCESS;
        if (!TrmtConfig.golemEnabled) {
            player.sendMessage(
                new TextComponentString(TextFormatting.RED + I18n.translateToLocal("trmtgtnh.golem.disabled")));
            return EnumActionResult.SUCCESS;
        }

        EntityGolemOfWays golem = new EntityGolemOfWays(world);
        golem.setLocationAndAngles(x + 0.5D, y + 1.0D, z + 0.5D, world.rand.nextFloat() * 360f, 0f);
        golem.setAnchor(x, y + 1, z);
        golem.setSummonedBy(player.getName());
        ModAchievements.onGolemBuilt(player);
        world.spawnEntity(golem);

        if (!player.capabilities.isCreativeMode) stack.shrink(1);
        return EnumActionResult.SUCCESS;
    }

    /** See {@link ItemTamper#addInformation} for why this one says which side it is on. */
    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag advanced) {
        // Substituted before anything is written, so every line below - and every line
        // anybody adds later - stops at the edge of a column rather than the screen.
        tooltip = Tooltips.wrapping(tooltip);
        tooltip.add(TextFormatting.GOLD + I18n.translateToLocal("trmtgtnh.golem.egg.desc"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.translateToLocal("trmtgtnh.golem.egg.tip"));
    }
}
