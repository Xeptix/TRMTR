package com.trmtgtnh.entity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The golem's storage, its upgrade slot, and the buttons that give it its orders.
 *
 * <p>
 * The slots here enforce the player's rule on the way in: tampers and the ground it mends with into
 * the storage, and an upgrade into its socket. Everything that reaches the golem another way - a
 * hopper, a dropper, a mod that asks before it puts - is held to a narrower rule by the golem's own
 * {@code isItemValidForSlot} and {@code canInsertItem}: the ground it mends with and nothing else,
 * and never into its last empty slot, which it keeps for a tamper fetched after one breaks.
 *
 * <p>
 * Taking things out through this screen is unrestricted - a golem that has picked up seeds must be
 * able to hand them back - while the golem's {@code canExtractItem} refuses every hopper, so the
 * tool it depends on and the part it wears stay where a player put them.
 *
 * <p>
 * The orders travel on the container's own button channel rather than a packet of their own. It
 * costs nothing, it is already synchronised with the window, and it cannot be sent by somebody who
 * does not have the screen open - which for a thing that rewrites ground is worth having for free.
 */
public class ContainerGolem extends Container {

    /**
     * Button ids, and every one of them under a hundred and twenty-seven.
     *
     * <p>
     * Not a style rule: the channel these ride is vanilla's enchanting-table button, and that
     * packet writes the id with {@code writeByte} and reads it back with {@code readByte}. An id
     * of two hundred goes out as two hundred and arrives as minus fifty-six, matches nothing, and
     * does nothing at all - which is what had quietly happened to the whole held-block row and to
     * the order buttons of every family past the seventh. They are numbered tightly now, and
     * anything added here has to stay inside the same hundred and twenty-seven.
     */
    public static final int BUTTON_RADIUS_DOWN = 0;
    public static final int BUTTON_RADIUS_UP = 1;

    /** Move the golem's home to where the player standing at its screen is. */
    public static final int BUTTON_HOME_HERE = 2;

    /** The four buttons on the held-block row, which orders whatever the player is holding. */
    public static final int HELD_BASE = 4;

    public static final int FAMILY_BASE = 8;
    public static final int FAMILY_STRIDE = 4;
    public static final int FAMILY_LESS = 0;
    public static final int FAMILY_MORE = 1;
    public static final int FAMILY_OFF = 2;
    public static final int FAMILY_ZERO = 3;
    public static final int HELD_LESS = 0;
    public static final int HELD_MORE = 1;
    public static final int HELD_OFF = 2;
    public static final int HELD_CLEAR = 3;

    // ------------------------------------------------------------------
    // The screen's vertical measurements
    //
    // They live here rather than in the screen because the slots are placed from them too. When
    // they lived in two places the orders block was laid out ninety-odd pixels tall in one and
    // forty-six in the other, and every family row after the first was drawn over the player's
    // inventory.
    // ------------------------------------------------------------------

    /** Columns in the golem's own storage; its slot count is always a multiple of this. */
    public static final int STORAGE_COLUMNS = 8;

    /** The left margin every column of slots and every line of text is set against. */
    public static final int MARGIN = 8;

    /**
     * Where the first storage row sits, measured from the top of the panel.
     *
     * <p>
     * Ten lower than the title bar needs, and the ten are a line of their own: what the golem is
     * holding, or why it is holding nothing. The title bar was tried first and would not do it -
     * measured against the real font, "Golem of Sparing Ways" leaves forty-two pixels, which is
     * not enough for the shortest thing that line ever has to say.
     */
    public static final int STORAGE_TOP = 30;

    /**
     * Family rows on one page of orders.
     *
     * <p>
     * Three rather than four, and the fourth is what the home row is standing on. The panel cannot
     * grow: at eight storage rows it is already as tall as a screen at the larger interface scales
     * will hold, and fourteen more pixels is exactly what puts its title bar off the top. A page
     * is a cheap thing to spend - ten staged families become four pages instead of three, and the
     * arrows were already there - where a panel that does not fit is not.
     */
    public static final int ROWS_PER_PAGE = 3;

    /** Breathing room above and below the orders block. */
    public static final int ORDERS_GAP = 4;

    /**
     * The whole orders block: a header row, three family rows on a fourteen pitch, the held block's
     * name on a line of its own, the held block's row, and the home row under both. Fixed, because
     * the slots underneath it are fixed - and unchanged by the home row, which was paid for out of
     * the fourth family row rather than out of the panel's height.
     */
    public static final int ORDERS_HEIGHT = 94;

    /** The top of the orders block, in panel coordinates. */
    public static int ordersTop(int storageRows) {
        return STORAGE_TOP + storageRows * 18 + ORDERS_GAP;
    }

    /** The top of the player's inventory, which is whatever the orders leave. */
    public static int playerTop(int storageRows) {
        return ordersTop(storageRows) + ORDERS_HEIGHT + ORDERS_GAP;
    }

    /**
     * Where the upgrade slot sits, which is deliberately off the storage grid's rhythm.
     *
     * <p>
     * It used to sit at the top right, one slot-pitch from the last column and level with the first
     * row, which made it read as a ninth storage slot that happened to be set apart. Centring it
     * against the whole block breaks the alignment instead: nothing else in the panel shares that
     * y, so the eye stops treating it as part of the grid. The screen draws a rule beside it and
     * gives it a warmer frame to finish the job.
     */
    public static int upgradeSlotY(int storageRows) {
        return STORAGE_TOP + (storageRows * 18 - 16) / 2;
    }

    /** The column the upgrade sits in, clear of the eight storage columns. */
    public static final int UPGRADE_X = 152;

    /** How tall the screen must be: the inventory is seventy-four deep, with a margin after it. */
    public static int panelHeight(int storageRows) {
        return playerTop(storageRows) + 74 + 7;
    }

    /** Progress bar 0 carries the held block's override; the family targets follow it. */
    private static final int BAR_HELD = 0;
    private static final int BAR_FAMILY_BASE = 1;

    /** What bar 0 says when the player is not holding anything the golem could be told about. */
    public static final int HELD_NONE = -2;

    /**
     * Where a slot goes when the golem is not currently wide enough to have it.
     *
     * <p>
     * Off the screen rather than flagged hidden, because {@code getSlotAtPosition} - the thing
     * that decides what a click landed on - never asks whether a slot is enabled. It compares
     * coordinates and nothing else, so the only place a slot cannot be clicked is somewhere the
     * cursor cannot reach. The flag is set as well, for the tooltip and the highlight, but this is
     * what actually holds.
     */
    private static final int STOWED = -4000;

    private final EntityGolemOfWays golem;

    private final EntityPlayer viewer;

    /** Every storage slot the golem could ever have; how many are shown follows the upgrade. */
    private final Slot[] storage = new Slot[EntityGolemOfWays.DEEP_SLOTS];

    private final Slot[] pockets = new Slot[36];

    private Slot upgrade;

    /** How many rows the slots are currently placed for, so a change can be noticed. */
    private int shownRows;

    /**
     * The last values sent, so a bar is only resent when it changes.
     *
     * <p>
     * These exist because the golem's orders are not watched entity data: before this the screen
     * read them off the client's own copy of the entity, which is a different object that had never
     * been told any of it, so every family read as ignored however it had been set.
     */
    private final int[] lastSent = new int[SurfaceFamily.values().length + 1];

    public ContainerGolem(InventoryPlayer playerInventory, EntityGolemOfWays golem) {
        this.golem = golem;
        this.viewer = playerInventory == null ? null : playerInventory.player;
        java.util.Arrays.fill(lastSent, Integer.MIN_VALUE);

        // Every slot the golem could ever have is built now, whatever is fitted to it at this
        // moment, and the layout decides which of them are anywhere the player can reach. Building
        // only the ones in use would mean rebuilding the window whenever an upgrade went in or
        // came out, and a container cannot change its slot count under an open screen without the
        // two sides disagreeing about what index means what.
        for (int index = 0; index < storage.length; index++) {
            storage[index] = new SlotWorkOnly(golem, index, STOWED, STOWED);
            addSlotToContainer(storage[index]);
        }

        // The upgrade sits apart from the row of tools, because it is not one. Built whether or not
        // upgrades are allowed, and put out of sight when they are not, for the reason the storage
        // above is built whole: a container's slot count is the numbering both sides share, and
        // whether upgrades are allowed is a setting each side reads from its own file. A client
        // that disagreed with its server had one slot more or fewer, so every index past the
        // golem's own meant a different slot on each side, and a window of more stacks than the
        // client had slots crashed it.
        boolean allowed = golem.upgradesAllowed();
        upgrade = new SlotUpgradeOnly(
            golem,
            EntityGolemOfWays.UPGRADE_SLOT,
            allowed ? UPGRADE_X : STOWED,
            allowed ? STORAGE_TOP : STOWED);
        addSlotToContainer(upgrade);

        // Below the orders, whose height is fixed so that the slots and the screen cannot disagree.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int at = column + row * 9;
                pockets[at] = new Slot(playerInventory, at + 9, STOWED, STOWED);
                addSlotToContainer(pockets[at]);
            }
        }
        for (int column = 0; column < 9; column++) {
            pockets[27 + column] = new Slot(playerInventory, column, STOWED, STOWED);
            addSlotToContainer(pockets[27 + column]);
        }

        relayout();
        // So the golem knows who to hand anything back to. Server side only - the client's copy of
        // a golem gives nothing up, and a viewer set there would be a second answer to a question
        // that has exactly one.
        if (this.viewer != null && golem.world != null && !golem.world.isRemote) {
            golem.setViewer(this.viewer);
        }
    }

    /**
     * Closing the window takes the viewer back off the golem.
     *
     * <p>
     * Otherwise a golem asked to give something up would try to hand it to somebody who walked
     * away an hour ago. The golem checks again on its own account before using it, because a
     * player can stop being a viewer without ever closing anything.
     */
    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        if (golem != null && golem.world != null && !golem.world.isRemote) golem.setViewer(null);
    }

    /**
     * Puts every slot where the golem's current width says it goes.
     *
     * <p>
     * The one place the layout is decided, and it is asked again every time the server sweeps the
     * window, so fitting the storage upgrade widens the grid and moves the inventory down it while
     * the screen is open rather than the next time it is opened. Returns whether anything actually
     * moved, which is the screen's cue to re-measure itself and lay its buttons out again.
     */
    public boolean relayout() {
        int rows = Math.max(1, golem.slotCount() / STORAGE_COLUMNS);
        if (rows == shownRows) return false;
        shownRows = rows;

        int shown = rows * STORAGE_COLUMNS;
        for (int index = 0; index < storage.length; index++) {
            place(
                storage[index],
                index < shown,
                MARGIN + (index % STORAGE_COLUMNS) * 18,
                STORAGE_TOP + (index / STORAGE_COLUMNS) * 18);
        }
        place(upgrade, golem.upgradesAllowed(), UPGRADE_X, upgradeSlotY(rows));

        int inventoryTop = playerTop(rows);
        for (int index = 0; index < pockets.length; index++) {
            place(
                pockets[index],
                true,
                MARGIN + (index % 9) * 18,
                index < 27 ? inventoryTop + (index / 9) * 18 : inventoryTop + 58);
        }
        return true;
    }

    private static void place(Slot slot, boolean shown, int x, int y) {
        if (slot == null) return;
        slot.xPos = shown ? x : STOWED;
        slot.yPos = shown ? y : STOWED;
    }

    /** How many storage rows the slots are currently placed for. */
    public int shownRows() {
        return shownRows;
    }

    public EntityGolemOfWays golem() {
        return golem;
    }

    // ------------------------------------------------------------------
    // Telling the screen what the golem actually thinks
    // ------------------------------------------------------------------

    /** The key for whatever block the viewer is holding, or null when it is not one. */
    private String heldKey() {
        if (viewer == null) return null;
        ItemStack held = viewer.getHeldItemMainhand();
        if (held == null) return null;
        net.minecraft.block.Block block = net.minecraft.block.Block.getBlockFromItem(held.getItem());
        if (block == null || block == net.minecraft.init.Blocks.AIR) return null;
        int meta = held.getItemDamage();
        SurfaceFamily family = com.trmtgtnh.surface.SurfaceRegistry.familyOf(block, meta);
        // Only ground the golem could actually work; ordering it about a torch means nothing.
        if (family == null || !family.staged) return null;
        return EntityGolemOfWays.blockKey(block, meta);
    }

    private int heldValue() {
        String key = heldKey();
        return key == null ? HELD_NONE : golem.blockTargetFor(key);
    }

    @Override
    public void addListener(net.minecraft.inventory.IContainerListener crafting) {
        super.addListener(crafting);
        crafting.sendWindowProperty(this, BAR_HELD, heldValue());
        for (SurfaceFamily family : SurfaceFamily.values()) {
            crafting.sendWindowProperty(this, BAR_FAMILY_BASE + family.ordinal(), golem.targetFor(family));
        }
    }

    @Override
    public void detectAndSendChanges() {
        // Before the sweep, so a slot that has just come into use is sent with the rest, and so
        // anything recovered out of a slot the golem no longer has is sent in the same one.
        relayout();
        golem.tidyStorage();
        super.detectAndSendChanges();
        push(BAR_HELD, heldValue());
        for (SurfaceFamily family : SurfaceFamily.values()) {
            push(BAR_FAMILY_BASE + family.ordinal(), golem.targetFor(family));
        }
    }

    private void push(int bar, int value) {
        if (bar < 0 || bar >= lastSent.length || lastSent[bar] == value) return;
        lastSent[bar] = value;
        for (Object listener : listeners) {
            ((net.minecraft.inventory.IContainerListener) listener).sendWindowProperty(this, bar, value);
        }
    }

    /** Client side: takes one number the server just sent and files it for the screen. */
    @Override
    public void updateProgressBar(int bar, int value) {
        if (bar == BAR_HELD) {
            heldTarget = value;
            return;
        }
        int ordinal = bar - BAR_FAMILY_BASE;
        if (ordinal >= 0 && ordinal < familyTargets.length) familyTargets[ordinal] = value;
    }

    /** What the screen draws. Filled by {@link #updateProgressBar}, never read on the server. */
    private int heldTarget = HELD_NONE;

    private final int[] familyTargets = new int[SurfaceFamily.values().length];

    public int shownHeldTarget() {
        return heldTarget;
    }

    public int shownTargetFor(SurfaceFamily family) {
        if (family == null) return -1;
        int ordinal = family.ordinal();
        return ordinal < 0 || ordinal >= familyTargets.length ? -1 : familyTargets[ordinal];
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return golem != null && golem.isUsableByPlayer(player);
    }

    /**
     * A button on the screen was pressed.
     *
     * <p>
     * Named for enchanting because that is the channel vanilla happens to have left open; what it
     * carries here is which order was given. Everything is clamped by the golem's own setters, so a
     * forged number can only ever set a value the screen could have set.
     */
    @Override
    public boolean enchantItem(EntityPlayer player, int id) {
        if (golem == null || !canInteractWith(player)) return false;

        // Stepped from what it was told rather than from what it is currently allowed, so a cap
        // that comes and goes cannot ratchet the stored number down a notch at a time.
        if (id == BUTTON_RADIUS_DOWN) {
            golem.setWorkRadius(golem.storedRadius() - 4);
            noteWho(player);
            return true;
        }
        if (id == BUTTON_RADIUS_UP) {
            golem.setWorkRadius(golem.storedRadius() + 4);
            noteWho(player);
            return true;
        }
        if (id == BUTTON_HOME_HERE) {
            // Where the player is, rounded down, which is the block they are standing on rather
            // than the one their eyes are in.
            golem.setAnchor(
                net.minecraft.util.math.MathHelper.floor(player.posX),
                net.minecraft.util.math.MathHelper.floor(player.posY),
                net.minecraft.util.math.MathHelper.floor(player.posZ));
            noteWho(player);
            return true;
        }

        if (id >= HELD_BASE && id < HELD_BASE + 4) {
            String key = heldKey();
            if (key == null) return false;
            int current = golem.blockTargetFor(key);
            switch (id - HELD_BASE) {
                case HELD_LESS:
                    golem.setBlockTarget(
                        key,
                        current == EntityGolemOfWays.NO_BLOCK_TARGET || current < 0 ? 0 : Math.max(0, current - 10));
                    break;
                case HELD_MORE:
                    golem.setBlockTarget(
                        key,
                        current == EntityGolemOfWays.NO_BLOCK_TARGET || current < 0 ? 0 : Math.min(100, current + 10));
                    break;
                case HELD_OFF:
                    golem.setBlockTarget(key, -1);
                    break;
                case HELD_CLEAR:
                    golem.setBlockTarget(key, EntityGolemOfWays.NO_BLOCK_TARGET);
                    break;
                default:
                    return false;
            }
            noteWho(player);
            return true;
        }

        if (id >= FAMILY_BASE) {
            int offset = id - FAMILY_BASE;
            int ordinal = offset / FAMILY_STRIDE;
            int action = offset % FAMILY_STRIDE;
            SurfaceFamily family = SurfaceFamily.byOrdinal(ordinal);
            if (family == null) return false;

            int current = golem.targetFor(family);
            switch (action) {
                case FAMILY_LESS:
                    golem.setTargetFor(family, current < 0 ? 0 : Math.max(0, current - 10));
                    break;
                case FAMILY_MORE:
                    golem.setTargetFor(family, current < 0 ? 0 : Math.min(100, current + 10));
                    break;
                case FAMILY_OFF:
                    golem.setTargetFor(family, -1);
                    break;
                case FAMILY_ZERO:
                    golem.setTargetFor(family, 0);
                    break;
                default:
                    return false;
            }
            noteWho(player);
            return true;
        }
        return false;
    }

    /** WAILA reports who last gave it orders, which is the useful half of "whose golem is this". */
    private void noteWho(EntityPlayer player) {
        if (player != null) golem.setConfiguredBy(player.getName());
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;

        ItemStack held = slot.getStack();
        ItemStack copy = held.copy();
        // The upgrade slot always exists now, allowed or not; see the constructor.
        int golemSlots = storage.length + 1;

        if (index < golemSlots) {
            // Out of the golem and into the player, which is always allowed.
            if (!mergeItemStack(held, golemSlots, inventorySlots.size(), true)) return ItemStack.EMPTY;
        } else if (upgrade != null && upgrade.isItemValid(held)) {
            // An upgrade goes to the socket, not into the tool rack. Without this it went to
            // storage, where the golem could neither fit it nor mend with it.
            if (!mergeItemStack(held, storage.length, storage.length + 1, false)) return ItemStack.EMPTY;
        } else {
            // In from the player. Two limits, and both have to be here: the merge is a straight
            // scan that never asks a slot whether it would have accepted the stack, so neither
            // what may go in nor how far in reaches it any other way. Without the first, a
            // shift-click put a sword or a stack of rotten flesh into a golem that refuses both
            // from an ordinary click - and the rule that only tampers and mending stock go in was
            // only ever true of the slower way of doing it.
            // EMPTY, never null: the game's quick move asks what came back whether it is empty (0.9.222, spec GO45).
            if (!EntityGolemOfWays.acceptsIntoStorage(held)) return ItemStack.EMPTY;
            if (!mergeItemStack(held, 0, Math.min(golem.slotCount(), storage.length), false)) return ItemStack.EMPTY;
        }

        if (held.isEmpty()) {
            // EMPTY, never null: a whole stack moved in leaves the player's own slot empty, and the player's inventory
            // refuses null at this version (0.9.222, spec GO45).
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        return copy;
    }

    /** Storage: the tools it works with and the ground it mends with, in; anything out. */
    private static final class SlotWorkOnly extends Slot {

        private final EntityGolemOfWays golem;

        private final int index;

        SlotWorkOnly(EntityGolemOfWays golem, int index, int x, int y) {
            super(golem, index, x, y);
            this.golem = golem;
            this.index = index;
        }

        /** Whether this one is part of the storage the golem currently has. */
        private boolean inUse() {
            return index < golem.slotCount();
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return inUse() && EntityGolemOfWays.acceptsIntoStorage(stack);
        }

        /**
         * Hidden while the golem is too narrow to have it.
         *
         * <p>
         * The screen uses this for the highlight; the position is what stops the click. Taking
         * things out is deliberately not blocked, so anything left stranded in a slot by removing
         * the storage upgrade comes back the moment it is fitted again rather than being lost.
         */
        @Override
        public boolean isEnabled() {
            return inUse();
        }
    }

    /** One upgrade, and only an upgrade. */
    private static final class SlotUpgradeOnly extends Slot {

        SlotUpgradeOnly(EntityGolemOfWays golem, int index, int x, int y) {
            super(golem, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            // Refused rather than absent when upgrades are off, so the slot can stay in the layout.
            return stack != null && stack.getItem() instanceof com.trmtgtnh.item.ItemGolemUpgrade
                && ((EntityGolemOfWays) inventory).upgradesAllowed();
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }
    }
}
