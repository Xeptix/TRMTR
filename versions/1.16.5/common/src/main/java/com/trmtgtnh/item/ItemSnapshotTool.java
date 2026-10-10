package com.trmtgtnh.item;

import java.util.List;


import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;

import com.trmtgtnh.Client;
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
        // A properties object rather than two setters, which is where everything an item is
        // settled at this version.
        super(new Item.Properties().stacksTo(1)
            .tab(CreativeModeTab.TAB_TOOLS));
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
        if (world.isClientSide()) {
            if (TamperModifiers.held(player)) Client.openSnapshotScreen(0);
            return InteractionResult.SUCCESS;
        }
        if (TamperModifiers.held(player)) return InteractionResult.SUCCESS;
        act(player, SnapshotStore.RIGHT, player.isShiftKeyDown());
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (world.isClientSide()) {
            if (TamperModifiers.held(player)) Client.openSnapshotScreen(0);
        } else if (!TamperModifiers.held(player)) {
            act(player, SnapshotStore.RIGHT, player.isShiftKeyDown());
        }
        return new InteractionResultHolder<ItemStack>(InteractionResult.SUCCESS, stack);
    }

    @Override
    public boolean leftClick(Level world, int x, int y, int z, Player player, ItemStack stack, boolean sneaking) {
        if (world == null || world.isClientSide()) return false;
        act(player, SnapshotStore.LEFT, sneaking);
        return true;
    }

    /** Sneaking stores this side; not sneaking puts it back. */
    private static void act(Player player, int which, boolean store) {
        if (!allowed(player)) return;
        if (store) {
            capture(player, which);
        } else {
            restore(player, which);
        }
    }

    private static void capture(Player player, int which) {
        String snapshot = ConfigSnapshot.capture();
        if (snapshot == null) {
            say(player, ChatFormatting.RED, "trmtgtnh.snapshot.noConfig");
            return;
        }
        SnapshotStore.get()
            .store(player.getUUID(), which, snapshot);
        say(
            player,
            ChatFormatting.AQUA,
            which == SnapshotStore.LEFT ? "trmtgtnh.snapshot.stored.left" : "trmtgtnh.snapshot.stored.right");
        pushState(player);
    }

    private static void restore(Player player, int which) {
        String snapshot = SnapshotStore.get()
            .slot(player.getUUID(), which);
        if (snapshot == null) {
            say(
                player,
                ChatFormatting.YELLOW,
                which == SnapshotStore.LEFT ? "trmtgtnh.snapshot.empty.left" : "trmtgtnh.snapshot.empty.right");
            return;
        }
        // Announced only once it has taken: a put-back that failed has said so already, and until 0.9.222 it went
        // on to say the side was loaded regardless (spec CF84).
        if (!apply(player, snapshot)) return;
        say(
            player,
            ChatFormatting.AQUA,
            which == SnapshotStore.LEFT ? "trmtgtnh.snapshot.loaded.left" : "trmtgtnh.snapshot.loaded.right");
    }

    /**
     * Applies a snapshot, remembering where the player was first so undo has somewhere to go. Answers whether it took,
     * having told the player if it did not.
     */
    public static boolean apply(Player player, String snapshot) {
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
                .rememberBaseline(player.getUUID(), before);
        }
        pushState(player);
        return true;
    }

    /** Tells a player a snapshot could not be put in place. */
    public static void failed(Player player) {
        say(player, ChatFormatting.RED, "trmtgtnh.snapshot.failed");
    }

    public static void pushState(Player player) {
        if (!(player instanceof ServerPlayer)) return;
        SnapshotStore store = SnapshotStore.get();
        java.util.UUID id = player.getUUID();
        int flags = (store.has(id, SnapshotStore.LEFT) ? 0x1 : 0) | (store.has(id, SnapshotStore.RIGHT) ? 0x2 : 0)
            | (store.baseline(id) != null ? 0x4 : 0);
        TrmtNetwork.sendSnapshotState((ServerPlayer) player, flags);
    }

    /** The same bar a config push has to clear: this rewrites the server's settings. */
    public static boolean allowed(Player player) {
        if (player instanceof ServerPlayer && player.hasPermissions(2)) return true;
        say(player, ChatFormatting.RED, "trmtgtnh.snapshot.noPermission");
        return false;
    }

    private static void say(Player player, ChatFormatting color, String key) {
        if (player == null) return;
        player.sendMessage(
            new TextComponent(color + com.trmtgtnh.util.Translate.get(key)),
            net.minecraft.Util.NIL_UUID);
    }

    /** See {@link ItemTamper#addInformation} for why this one says which side it is on. */
    @Override
    public void appendHoverText(ItemStack stack, Level world,
        List<net.minecraft.network.chat.Component> lines, TooltipFlag advanced) {
        // Substituted before anything is written, so every line below - and every line
        // anybody adds later - stops at the edge of a column rather than the screen.
        List<String> tooltip = Tooltips.lines(lines);
        tooltip.add(ChatFormatting.GOLD + com.trmtgtnh.util.Translate.get("trmtgtnh.snapshot.desc"));
        tooltip.add(ChatFormatting.DARK_GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.snapshot.tip.store"));
        tooltip.add(ChatFormatting.DARK_GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.snapshot.tip.load"));
        tooltip.add(ChatFormatting.DARK_GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.snapshot.tip.screen"));
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
