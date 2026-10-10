package com.trmtgtnh.entity;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The golem's storage, its upgrade slot, and the buttons that give it its orders.
 *
 * <p>
 * The slots here enforce the player's rule on the way in: tampers and the ground it mends with into
 * the storage, and an upgrade into its socket. Everything that reaches the golem another way - a
 * hopper, a dropper, a mod that asks before it puts - is held to a narrower rule by the golem's own
 * {@code canPlaceItem} and {@code canPlaceItemThroughFace}: the ground it mends with and nothing else,
 * and never into its last empty slot, which it keeps for a tamper fetched after one breaks.
 *
 * <p>
 * Taking things out through this screen is unrestricted - a golem that has picked up seeds must be
 * able to hand them back - while the golem's {@code canTakeItemThroughFace} refuses every hopper, so the
 * tool it depends on and the part it wears stay where a player put them.
 *
 * <p>
 * The orders travel on the container's own button channel rather than a packet of their own. It
 * costs nothing, it is already synchronised with the window, and it cannot be sent by somebody who
 * does not have the screen open - which for a thing that rewrites ground is worth having for free.
 */
public class ContainerGolem extends AbstractContainerMenu {

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

    private final Player viewer;

    /**
     * The player's own inventory, kept because the pockets are rebuilt against it.
     *
     * <p>
     * The other edition does not need this: it moves its slots and never makes another. See
     * {@link #replace}.
     */
    private final Inventory pocketsFrom;

    /** Every storage slot the golem could ever have; how many are shown follows the upgrade. */
    private final Slot[] storage = new Slot[EntityGolemOfWays.DEEP_SLOTS];

    private final Slot[] pockets = new Slot[36];

    private Slot upgrade;

    /** How many rows the slots are currently placed for, so a change can be noticed. */
    private int shownRows;

    public ContainerGolem(int windowId, Inventory playerInventory, EntityGolemOfWays golem) {
        super(GolemMenu.type(), windowId);
        this.golem = golem;
        this.viewer = playerInventory == null ? null : playerInventory.player;
        this.pocketsFrom = playerInventory;

        // Every slot the golem could ever have is built now, whatever is fitted to it at this
        // moment, and the layout decides which of them are anywhere the player can reach. Building
        // only the ones in use would mean rebuilding the window whenever an upgrade went in or
        // came out, and a container cannot change its slot count under an open screen without the
        // two sides disagreeing about what index means what.
        for (int index = 0; index < storage.length; index++) {
            storage[index] = new SlotWorkOnly(golem, index, STOWED, STOWED);
            addSlot(storage[index]);
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
        addSlot(upgrade);

        // Below the orders, whose height is fixed so that the slots and the screen cannot disagree.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int at = column + row * 9;
                pockets[at] = new Slot(playerInventory, at + 9, STOWED, STOWED);
                addSlot(pockets[at]);
            }
        }
        for (int column = 0; column < 9; column++) {
            pockets[27 + column] = new Slot(playerInventory, column, STOWED, STOWED);
            addSlot(pockets[27 + column]);
        }

        // The orders, as data slots, in the order BAR_HELD and BAR_FAMILY_BASE name. Each one's
        // `get` is read on the server when the window is swept and its `set` is called on the
        // client when the number arrives - so the two halves of one number sit next to each other
        // here, where the older edition had them ninety lines apart in a send and a receive.
        //
        // They exist at all because the golem's orders are not watched entity data: before this the
        // screen read them off the client's own copy of the entity, which is a different object that
        // had never been told any of it, so every family read as ignored however it had been set.
        addDataSlot(new net.minecraft.world.inventory.DataSlot() {

            @Override
            public int get() {
                return heldValue();
            }

            @Override
            public void set(int value) {
                heldTarget = value;
            }
        });
        for (final SurfaceFamily family : SurfaceFamily.values()) {
            addDataSlot(new net.minecraft.world.inventory.DataSlot() {

                @Override
                public int get() {
                    return golem.targetFor(family);
                }

                @Override
                public void set(int value) {
                    familyTargets[family.ordinal()] = value;
                }
            });
        }

        relayout();
        // So the golem knows who to hand anything back to. Server side only - the client's copy of
        // a golem gives nothing up, and a viewer set there would be a second answer to a question
        // that has exactly one.
        if (this.viewer != null && golem.level != null && !golem.level.isClientSide()) {
            golem.setViewer(this.viewer);
        }
    }

    /**
     * The client's end: the golem arrives as an id in the buffer beside the open.
     *
     * <p>
     * Both older editions smuggle the entity id through the {@code x} of Forge's gui handler and
     * look the entity up on the far side. This is the same trick said properly - the id travels as
     * extra data alongside the menu's own open packet - and the lookup is the same lookup.
     *
     * <p>
     * It refuses rather than carrying on with no golem, and says why. A menu is only ever opened for
     * an entity the player is standing next to, so the client has it; but every slot in this window
     * is a slot <em>on</em> the golem, so one built without it would be a window of thirty-seven
     * slots onto nothing, and the first null would surface somewhere that says nothing about how it
     * got there.
     */
    public ContainerGolem(int windowId, Inventory playerInventory, net.minecraft.network.FriendlyByteBuf extra) {
        this(windowId, playerInventory, found(playerInventory, extra.readVarInt()));
    }

    private static EntityGolemOfWays found(Inventory playerInventory, int entityId) {
        EntityGolemOfWays golem = playerInventory == null ? null
            : GolemMenu.golemAt(playerInventory.player.level, entityId);
        if (golem == null) {
            throw new IllegalStateException(
                "A Golem of Ways' window was opened for entity " + entityId
                    + ", which this client has no golem for. Every slot in the window belongs to the "
                    + "golem, so there is nothing to show.");
        }
        return golem;
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
    public void removed(Player player) {
        super.removed(player);
        if (golem != null && golem.level != null && !golem.level.isClientSide()) golem.setViewer(null);
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
            boolean inUse = index < shown;
            storage[index] = replace(
                storage[index],
                new SlotWorkOnly(
                    golem,
                    index,
                    inUse ? MARGIN + (index % STORAGE_COLUMNS) * 18 : STOWED,
                    inUse ? STORAGE_TOP + (index / STORAGE_COLUMNS) * 18 : STOWED));
        }
        boolean allowed = golem.upgradesAllowed();
        upgrade = replace(
            upgrade,
            new SlotUpgradeOnly(
                golem,
                EntityGolemOfWays.UPGRADE_SLOT,
                allowed ? UPGRADE_X : STOWED,
                allowed ? upgradeSlotY(rows) : STOWED));

        int inventoryTop = playerTop(rows);
        for (int index = 0; index < pockets.length; index++) {
            pockets[index] = replace(
                pockets[index],
                new Slot(
                    pocketsFrom,
                    index < 27 ? index + 9 : index - 27,
                    MARGIN + (index % 9) * 18,
                    index < 27 ? inventoryTop + (index / 9) * 18 : inventoryTop + 58));
        }
        return true;
    }

    /**
     * Puts a fresh slot where an old one was, which is how a slot is moved here.
     *
     * <p>
     * The index is carried over rather than recomputed, and the menu's list is written at that
     * index rather than appended to. Both halves matter: the index is what a click arriving from a
     * client names, so a slot that changed its number mid-window would take the click meant for
     * its neighbour.
     */
    private Slot replace(Slot old, Slot fresh) {
        if (old == null) return null;
        fresh.index = old.index;
        slots.set(old.index, fresh);
        return fresh;
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
        ItemStack held = viewer.getMainHandItem();
        if (held == null) return null;
        net.minecraft.world.level.block.Block block = net.minecraft.world.level.block.Block.byItem(held.getItem());
        if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) return null;
        int meta = held.getDamageValue();
        SurfaceFamily family = com.trmtgtnh.surface.SurfaceRegistry.familyOf(block);
        // Only ground the golem could actually work; ordering it about a torch means nothing.
        if (family == null || !family.staged) return null;
        return EntityGolemOfWays.blockKey(block, meta);
    }

    private int heldValue() {
        String key = heldKey();
        return key == null ? HELD_NONE : golem.blockTargetFor(key);
    }

    @Override
    public void broadcastChanges() {
        // Before the sweep, so a slot that has just come into use is sent with the rest, and so
        // anything recovered out of a slot the golem no longer has is sent in the same one.
        relayout();
        golem.tidyStorage();
        // Which now also sends whichever of the orders have moved; see the data slots in the
        // constructor. The other edition's second half of this method, and the push it called, are
        // what that replaced.
        super.broadcastChanges();
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
    public boolean stillValid(Player player) {
        return golem != null && golem.stillValid(player);
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
    public boolean clickMenuButton(Player player, int id) {
        if (golem == null || !stillValid(player)) return false;

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
                net.minecraft.util.Mth.floor(player.getX()),
                net.minecraft.util.Mth.floor(player.getY()),
                net.minecraft.util.Mth.floor(player.getZ()));
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
    private void noteWho(Player player) {
        if (player != null) golem.setConfiguredBy(player.getGameProfile()
            .getName());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = (Slot) slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack held = slot.getItem();
        ItemStack copy = held.copy();
        // The upgrade slot always exists now, allowed or not; see the constructor.
        int golemSlots = storage.length + 1;

        if (index < golemSlots) {
            // Out of the golem and into the player, which is always allowed.
            if (!moveItemStackTo(held, golemSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (upgrade != null && upgrade.mayPlace(held)) {
            // An upgrade goes to the socket, not into the tool rack. Without this it went to
            // storage, where the golem could neither fit it nor mend with it.
            if (!moveItemStackTo(held, storage.length, storage.length + 1, false)) return ItemStack.EMPTY;
        } else {
            // In from the player. Two limits, and both have to be here: the merge is a straight
            // scan that never asks a slot whether it would have accepted the stack, so neither
            // what may go in nor how far in reaches it any other way. Without the first, a
            // shift-click put a sword or a stack of rotten flesh into a golem that refuses both
            // from an ordinary click - and the rule that only tampers and mending stock go in was
            // only ever true of the slower way of doing it.
            // EMPTY, never null: the game's quick move asks what came back whether it is empty (0.9.222, spec GO45).
            if (!EntityGolemOfWays.acceptsIntoStorage(held)) return ItemStack.EMPTY;
            if (!moveItemStackTo(held, 0, Math.min(golem.slotCount(), storage.length), false)) return ItemStack.EMPTY;
        }

        if (held.isEmpty()) {
            // EMPTY, never null: a whole stack moved in leaves the player's own slot empty, and the player's inventory
            // refuses null at this version (0.9.222, spec GO45).
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
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
        public boolean mayPlace(ItemStack stack) {
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
        public boolean isActive() {
            return inUse();
        }
    }

    /** One upgrade, and only an upgrade. */
    private static final class SlotUpgradeOnly extends Slot {

        SlotUpgradeOnly(EntityGolemOfWays golem, int index, int x, int y) {
            super(golem, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            // Refused rather than absent when upgrades are off, so the slot can stay in the layout.
            return stack != null && stack.getItem() instanceof com.trmtgtnh.item.ItemGolemUpgrade
                && ((EntityGolemOfWays) container).upgradesAllowed();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
