package com.trmtgtnh.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

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
        setCreativeTab(CreativeTabs.tabTools);
        setTextureName(Trmt.MODID + ":snapshot_tool");
    }

    // ------------------------------------------------------------------
    // Gestures
    // ------------------------------------------------------------------

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            if (TamperModifiers.held(player)) Trmt.proxy.requestSnapshotScreen();
            return true;
        }
        if (TamperModifiers.held(player)) return true;
        act(player, SnapshotStore.RIGHT, player.isSneaking());
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            if (TamperModifiers.held(player)) Trmt.proxy.requestSnapshotScreen();
            return stack;
        }
        if (TamperModifiers.held(player)) return stack;
        act(player, SnapshotStore.RIGHT, player.isSneaking());
        return stack;
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
            say(player, EnumChatFormatting.RED, "trmtgtnh.snapshot.noConfig");
            return;
        }
        SnapshotStore.get()
            .store(player.getUniqueID(), which, snapshot);
        say(
            player,
            EnumChatFormatting.AQUA,
            which == SnapshotStore.LEFT ? "trmtgtnh.snapshot.stored.left" : "trmtgtnh.snapshot.stored.right");
        pushState(player);
    }

    private static void restore(EntityPlayer player, int which) {
        String snapshot = SnapshotStore.get()
            .slot(player.getUniqueID(), which);
        if (snapshot == null) {
            say(
                player,
                EnumChatFormatting.YELLOW,
                which == SnapshotStore.LEFT ? "trmtgtnh.snapshot.empty.left" : "trmtgtnh.snapshot.empty.right");
            return;
        }
        apply(player, snapshot);
        say(
            player,
            EnumChatFormatting.AQUA,
            which == SnapshotStore.LEFT ? "trmtgtnh.snapshot.loaded.left" : "trmtgtnh.snapshot.loaded.right");
    }

    /** Applies a snapshot, remembering where the player was first so undo has somewhere to go. */
    public static void apply(EntityPlayer player, String snapshot) {
        // Taken first, because afterwards the file is the snapshot - but remembered only once the
        // snapshot has actually taken. Remembering it up front meant a commit that failed replaced
        // the way back from the last one that worked with a copy of settings nothing had changed.
        String before = ConfigSnapshot.capture();
        if (!ConfigSnapshot.apply(snapshot)) {
            failed(player);
            return;
        }
        if (before != null) {
            SnapshotStore.get()
                .rememberBaseline(player.getUniqueID(), before);
        }
        pushState(player);
    }

    /** Tells a player a snapshot could not be put in place. */
    public static void failed(EntityPlayer player) {
        say(player, EnumChatFormatting.RED, "trmtgtnh.snapshot.failed");
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
        if (player instanceof EntityPlayerMP && player.canCommandSenderUseCommand(2, PERMISSION_NODE)) return true;
        say(player, EnumChatFormatting.RED, "trmtgtnh.snapshot.noPermission");
        return false;
    }

    private static void say(EntityPlayer player, EnumChatFormatting color, String key) {
        if (player != null) player.addChatMessage(new ChatComponentText(color + StatCollector.translateToLocal(key)));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        // Substituted before anything is written, so every line below - and every line
        // anybody adds later - stops at the edge of a column rather than the screen.
        tooltip = Tooltips.wrapping(tooltip);
        tooltip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("trmtgtnh.snapshot.desc"));
        tooltip.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("trmtgtnh.snapshot.tip.store"));
        tooltip.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("trmtgtnh.snapshot.tip.load"));
        tooltip.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("trmtgtnh.snapshot.tip.screen"));
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
