package com.trmtgtnh.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.network.TrmtNetwork;

/**
 * A creative-only tool for looking at one setting two ways.
 *
 * <p>
 * Its whole job is before-and-after. In its first mode a left-click shows the wear patterns the
 * hard surfaces used to be drawn with and a right-click shows the ones they are drawn with now, so
 * the difference can be flipped between on the same piece of ground rather than remembered across
 * a config edit and a restart.
 *
 * <p>
 * Nothing it does is written to disk and nothing it does reaches another player: the swap happens
 * in the holder's own client, and the next resource reload puts the configured values back. That is
 * what makes it safe to hand to somebody mid-session, and why it is a preview rather than an edit -
 * committing a comparison is what the snapshot tool is for.
 *
 * <p>
 * The mode is kept on the stack so more comparisons can be added later without another item;
 * sneak and right-click cycles it. Creative only, checked on both gestures, because a tool that
 * rewrites what the world looks like is not something to leave lying in a survival chest.
 */
public class ItemDevTool extends Item implements AirSwingTool {

    /** The wear patterns the hard surfaces were drawn with before the newer ones were added. */
    public static final int PREVIEW_OLD = 0;

    /** The wear patterns they are drawn with now. */
    public static final int PREVIEW_NEW = 1;

    private static final String TAG_ROOT = "trmt";
    private static final String TAG_MODE = "devMode";

    /** How many comparisons this tool knows. One for now; the stack already carries the number. */
    private static final int MODES = 1;

    public ItemDevTool() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
        setTextureName(Trmt.MODID + ":dev_tool");
    }

    // ------------------------------------------------------------------
    // Which comparison this tool is set to
    // ------------------------------------------------------------------

    public static int modeOf(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return 0;
        int mode = stack.getTagCompound()
            .getCompoundTag(TAG_ROOT)
            .getByte(TAG_MODE);
        return mode < 0 || mode >= MODES ? 0 : mode;
    }

    private static void setMode(ItemStack stack, int mode) {
        if (stack == null) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound root = stack.getTagCompound();
        if (!root.hasKey(TAG_ROOT)) root.setTag(TAG_ROOT, new NBTTagCompound());
        root.getCompoundTag(TAG_ROOT)
            .setByte(TAG_MODE, (byte) (mode % MODES));
    }

    // ------------------------------------------------------------------
    // Gestures
    // ------------------------------------------------------------------

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        return right(player, stack);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) return stack;
        right(player, stack);
        return stack;
    }

    /** Right-click shows the current patterns; sneak and right-click cycles the comparison. */
    private boolean right(EntityPlayer player, ItemStack stack) {
        if (!allowed(player)) return true;
        if (player.isSneaking()) {
            setMode(stack, modeOf(stack) + 1);
            say(player, EnumChatFormatting.GRAY, StatCollector.translateToLocal("trmtgtnh.devtool.mode.patterns"));
            return true;
        }
        preview(player, PREVIEW_NEW);
        return true;
    }

    /** Left-click shows the old patterns. */
    @Override
    public boolean leftClick(World world, int x, int y, int z, EntityPlayer player, ItemStack stack, boolean sneaking) {
        if (world == null || world.isRemote) return false;
        if (!allowed(player)) return true;
        preview(player, PREVIEW_OLD);
        return true;
    }

    private static void preview(EntityPlayer player, int mode) {
        if (!(player instanceof EntityPlayerMP)) return;
        TrmtNetwork.sendDevPreview((EntityPlayerMP) player, mode);
        say(
            player,
            EnumChatFormatting.AQUA,
            StatCollector.translateToLocal(
                mode == PREVIEW_OLD ? "trmtgtnh.devtool.showing.old" : "trmtgtnh.devtool.showing.new"));
    }

    /** Creative only. A tool that rewrites what the world looks like is not survival equipment. */
    private static boolean allowed(EntityPlayer player) {
        if (player != null && player.capabilities.isCreativeMode) return true;
        say(player, EnumChatFormatting.RED, StatCollector.translateToLocal("trmtgtnh.devtool.creativeOnly"));
        return false;
    }

    private static void say(EntityPlayer player, EnumChatFormatting color, String message) {
        if (player != null) player.addChatMessage(new ChatComponentText(color + message));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        // Substituted before anything is written, so every line below - and every line
        // anybody adds later - stops at the edge of a column rather than the screen.
        tooltip = Tooltips.wrapping(tooltip);
        tooltip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("trmtgtnh.devtool.desc"));
        tooltip.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("trmtgtnh.devtool.mode")
                + " "
                + EnumChatFormatting.WHITE
                + StatCollector.translateToLocal("trmtgtnh.devtool.mode.patterns"));
        tooltip.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("trmtgtnh.devtool.tip.left"));
        tooltip.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("trmtgtnh.devtool.tip.right"));
        tooltip.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("trmtgtnh.devtool.tip.preview"));
    }

    /**
     * The same gesture when it hits nothing.
     *
     * <p>
     * A left-click at sky posts no event at all in 1.7.10 - {@code LEFT_CLICK_BLOCK} comes from
     * {@code ItemInWorldManager.onBlockClicked} and nowhere else - and this tool's left-click never
     * looked at the block anyway, so half the gesture was simply unavailable unless the player
     * happened to be facing ground. This is the client noticing that and telling the server; the
     * sender decides whether the crosshair was actually empty, so a swing at a block still goes the
     * ordinary way and only the ordinary way.
     *
     * <p>
     * False, always: cancelling here would cancel the arm swing with it.
     */
    @Override
    public boolean onEntitySwing(net.minecraft.entity.EntityLivingBase entity, ItemStack stack) {
        if (entity instanceof EntityPlayer) TrmtNetwork.sendAirSwingIfMissed((EntityPlayer) entity);
        return false;
    }
}
