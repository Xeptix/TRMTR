package com.trmtgtnh.item;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
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

import com.trmtgtnh.Trmt;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.server.ConfigSnapshot;
import com.trmtgtnh.server.SnapshotStore;

/**
 * Two config snapshots in the hand, for flipping between one set of settings and another.
 *
 * <p>
 * Sneak and click stores the config as it stands into that side; clicking without sneaking puts
 * that side back. So the way to compare is: set things one way, sneak-left to keep it, change
 * them, sneak-right to keep that, and then left and right to swap between the two on the same
 * ground. The settings key opens a screen showing what each side holds, with the buttons for
 * committing, clearing, and undoing.
 *
 * <p>
 * Every apply first records where you were, so undo always means "before the last change" rather
 * than "before the first one". The snapshots live on the server, one set per player, and outlive a
 * restart - see {@link SnapshotStore}.
 *
 * <p>
 * Gated on the same permission a config push needs, because that is what this is: it rewrites the
 * server's settings, and a tool that does that is not something a passer-by should be able to use.
 */
public class ItemSnapshotTool extends Item implements AirSwingTool {

    /** The permission a config-changing gesture needs, matching the operator config push. */
    private static final String PERMISSION_NODE = "trmtgtnh.config.push";

    public ItemSnapshotTool() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.TOOLS);
    }

    // ------------------------------------------------------------------
    // Gestures
    // ------------------------------------------------------------------

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing,
        float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            if (TamperModifiers.held(player)) Trmt.proxy.requestSnapshotScreen();
            return EnumActionResult.SUCCESS;
        }
        if (TamperModifiers.held(player)) return EnumActionResult.SUCCESS;
        act(player, SnapshotStore.RIGHT, player.isSneaking());
        return EnumActionResult.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            if (TamperModifiers.held(player)) Trmt.proxy.requestSnapshotScreen();
        } else if (!TamperModifiers.held(player)) {
            act(player, SnapshotStore.RIGHT, player.isSneaking());
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public boolean leftClick(World world, int x, int y, int z, EntityPlayer player, ItemStack stack, boolean sneaking) {
        if (world == null || world.isRemote) return false;
        act(player, SnapshotStore.LEFT, sneaking);
        return true;
    }

    /** Sneaking stores this side; not sneaking puts it back. */
    private static void act(EntityPlayer player, int which, boolean store) {
        if (!allowed(player)) return;
        if (store) {
            capture(player, which);
        } else {
            restore(player, which);
        }
    }

    private static void capture(EntityPlayer player, int which) {
        String snapshot = ConfigSnapshot.capture();
        if (snapshot == null) {
            say(player, TextFormatting.RED, "trmtgtnh.snapshot.noConfig");
            return;
        }
        SnapshotStore.get()
            .store(player.getUniqueID(), which, snapshot);
        say(
            player,
            TextFormatting.AQUA,
            which == SnapshotStore.LEFT ? "trmtgtnh.snapshot.stored.left" : "trmtgtnh.snapshot.stored.right");
        pushState(player);
    }

    private static void restore(EntityPlayer player, int which) {
        String snapshot = SnapshotStore.get()
            .slot(player.getUniqueID(), which);
        if (snapshot == null) {
            say(
                player,
                TextFormatting.YELLOW,
                which == SnapshotStore.LEFT ? "trmtgtnh.snapshot.empty.left" : "trmtgtnh.snapshot.empty.right");
            return;
        }
        // Announced only once it has taken: a put-back that failed has said so already, and until 0.9.222 it went
        // on to say the side was loaded regardless (spec CF84).
        if (!apply(player, snapshot)) return;
        say(
            player,
            TextFormatting.AQUA,
            which == SnapshotStore.LEFT ? "trmtgtnh.snapshot.loaded.left" : "trmtgtnh.snapshot.loaded.right");
    }

    /**
     * Applies a snapshot, remembering where the player was first so undo has somewhere to go. Answers whether it took,
     * having told the player if it did not.
     */
    public static boolean apply(EntityPlayer player, String snapshot) {
        // Taken first, because afterwards the file is the snapshot - but remembered only once the
        // snapshot has actually taken. Remembering it up front meant a commit that failed replaced
        // the way back from the last one that worked with a copy of settings nothing had changed.
        String before = ConfigSnapshot.capture();
        if (!ConfigSnapshot.apply(snapshot)) {
            failed(player);
            return false;
        }
        if (before != null) {
            SnapshotStore.get()
                .rememberBaseline(player.getUniqueID(), before);
        }
        pushState(player);
        return true;
    }

    /** Tells a player a snapshot could not be put in place. */
    public static void failed(EntityPlayer player) {
        say(player, TextFormatting.RED, "trmtgtnh.snapshot.failed");
    }

    public static void pushState(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return;
        SnapshotStore store = SnapshotStore.get();
        java.util.UUID id = player.getUniqueID();
        int flags = (store.has(id, SnapshotStore.LEFT) ? 0x1 : 0) | (store.has(id, SnapshotStore.RIGHT) ? 0x2 : 0)
            | (store.baseline(id) != null ? 0x4 : 0);
        TrmtNetwork.sendSnapshotState((EntityPlayerMP) player, flags);
    }

    /** The same bar a config push has to clear: this rewrites the server's settings. */
    public static boolean allowed(EntityPlayer player) {
        if (player instanceof EntityPlayerMP && player.canUseCommand(2, PERMISSION_NODE)) return true;
        say(player, TextFormatting.RED, "trmtgtnh.snapshot.noPermission");
        return false;
    }

    private static void say(EntityPlayer player, TextFormatting color, String key) {
        if (player != null) player.sendMessage(new TextComponentString(color + I18n.translateToLocal(key)));
    }

    /** See {@link ItemTamper#addInformation} for why this one says which side it is on. */
    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag advanced) {
        // Substituted before anything is written, so every line below - and every line
        // anybody adds later - stops at the edge of a column rather than the screen.
        tooltip = Tooltips.wrapping(tooltip);
        tooltip.add(TextFormatting.GOLD + I18n.translateToLocal("trmtgtnh.snapshot.desc"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.translateToLocal("trmtgtnh.snapshot.tip.store"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.translateToLocal("trmtgtnh.snapshot.tip.load"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.translateToLocal("trmtgtnh.snapshot.tip.screen"));
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
