package com.trmtgtnh.item;

import java.util.List;


import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;

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
        // A properties object rather than two setters, which is where everything an item is
        // settled at this version.
        super(new Item.Properties().stacksTo(1)
            .tab(CreativeModeTab.TAB_TOOLS));
    }

    // ------------------------------------------------------------------
    // Which comparison this tool is set to
    // ------------------------------------------------------------------

    public static int modeOf(ItemStack stack) {
        if (stack == null || !stack.hasTag()) return 0;
        int mode = stack.getTag()
            .getCompound(TAG_ROOT)
            .getByte(TAG_MODE);
        return mode < 0 || mode >= MODES ? 0 : mode;
    }

    private static void setMode(ItemStack stack, int mode) {
        if (stack == null) return;
        CompoundTag root = stack.getOrCreateTag();
        if (!root.contains(TAG_ROOT)) root.put(TAG_ROOT, new CompoundTag());
        root.getCompound(TAG_ROOT)
            .putByte(TAG_MODE, (byte) (mode % MODES));
    }

    // ------------------------------------------------------------------
    // Gestures
    // ------------------------------------------------------------------

    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        Player player = context.getPlayer();
        Level world = context.getLevel();
        if (player == null || world == null) return InteractionResult.PASS;
        BlockPos pos = context.getClickedPos();
        InteractionHand hand = context.getHand();
        ItemStack stack = player.getItemInHand(hand);
        if (world.isClientSide()) return InteractionResult.SUCCESS;
        right(player, stack);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!world.isClientSide()) right(player, stack);
        return new InteractionResultHolder<ItemStack>(InteractionResult.SUCCESS, stack);
    }

    /** Right-click shows the current patterns; sneak and right-click cycles the comparison. */
    private boolean right(Player player, ItemStack stack) {
        if (!allowed(player)) return true;
        if (player.isShiftKeyDown()) {
            setMode(stack, modeOf(stack) + 1);
            say(player, ChatFormatting.GRAY, com.trmtgtnh.util.Translate.get("trmtgtnh.devtool.mode.patterns"));
            return true;
        }
        preview(player, PREVIEW_NEW);
        return true;
    }

    /** Left-click shows the old patterns. */
    @Override
    public boolean leftClick(Level world, int x, int y, int z, Player player, ItemStack stack, boolean sneaking) {
        if (world == null || world.isClientSide()) return false;
        if (!allowed(player)) return true;
        preview(player, PREVIEW_OLD);
        return true;
    }

    private static void preview(Player player, int mode) {
        if (!(player instanceof ServerPlayer)) return;
        TrmtNetwork.sendDevPreview((ServerPlayer) player, mode);
        say(
            player,
            ChatFormatting.AQUA,
            com.trmtgtnh.util.Translate.get(
                mode == PREVIEW_OLD ? "trmtgtnh.devtool.showing.old" : "trmtgtnh.devtool.showing.new"));
    }

    /** Creative only. A tool that rewrites what the world looks like is not survival equipment. */
    private static boolean allowed(Player player) {
        if (player != null && player.abilities.instabuild) return true;
        say(player, ChatFormatting.RED, com.trmtgtnh.util.Translate.get("trmtgtnh.devtool.creativeOnly"));
        return false;
    }

    private static void say(Player player, ChatFormatting color, String message) {
        if (player == null) return;
        player.sendMessage(new TextComponent(color + message), net.minecraft.Util.NIL_UUID);
    }

    /** See {@link ItemTamper#addInformation} for why this one says which side it is on. */
    @Override
    public void appendHoverText(ItemStack stack, Level world,
        List<net.minecraft.network.chat.Component> lines, TooltipFlag advanced) {
        // Substituted before anything is written, so every line below - and every line
        // anybody adds later - stops at the edge of a column rather than the screen.
        List<String> tooltip = Tooltips.lines(lines);
        tooltip.add(ChatFormatting.GOLD + com.trmtgtnh.util.Translate.get("trmtgtnh.devtool.desc"));
        tooltip.add(
            ChatFormatting.GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.devtool.mode")
                + " "
                + ChatFormatting.WHITE
                + com.trmtgtnh.util.Translate.get("trmtgtnh.devtool.mode.patterns"));
        tooltip.add(ChatFormatting.DARK_GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.devtool.tip.left"));
        tooltip.add(ChatFormatting.DARK_GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.devtool.tip.right"));
        tooltip.add(ChatFormatting.DARK_GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.devtool.tip.preview"));
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
}
