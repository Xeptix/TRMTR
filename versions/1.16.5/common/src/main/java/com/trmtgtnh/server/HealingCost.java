package com.trmtgtnh.server;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.util.OreNames;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * What mending worn ground costs: a block of each kind of ground mended.
 *
 * <p>
 * Free repair is fine while a path is decoration and wrong the moment it is terrain somebody
 * has to maintain: one handful undoes a season of traffic and the ground stops meaning
 * anything. Charging a block of what is being mended puts the repair on the same footing as
 * laying the ground in the first place.
 *
 * <p>
 * Bone meal, the chunk tamper and the golem all pay by this rule, so none of them can accept
 * something the others would refuse. The hand tamper does not: it pays in what the ground drops,
 * matched exactly, by a rule of its own in {@code TamperActions}, and never asks this class. How
 * much is owed, and when, is not decided here either - that is the gesture's ledger - so this
 * class answers only what would pay and takes it when told to.
 *
 * <p>
 * Server side, and deliberately outside the erosion engine. The engine has never looked at a
 * player or at an inventory and this is about nothing else, so keeping it here leaves
 * {@link com.trmtgtnh.erosion.ErosionEngine#restoreOneStage} the same single seam it was.
 *
 * <p>
 * The whole thing rests on one fact the rest of the mod already depends on: the server's copy
 * of the world never holds a ghost, so the block at a worn position is still exactly the block
 * it always was. However far down its chain a position has been walked - a grass square seventy
 * gradations in is showing bare earth and a rut - the world still answers grass. There is no
 * appearance to see through and no stage to take account of.
 */
public final class HealingCost {

    /** Marks an entry as an ore dictionary group rather than a block name. */
    private static final String ORE_PREFIX = "ore:";

    private static final Substitute[] NO_SUBSTITUTES = new Substitute[0];

    /** Whose inventory pays, or null when there is no inventory to look in. */
    private final Player player;

    /** The surface the payment was worked out for. */
    private final SurfaceFamily family;

    /** Item form of the block that is really there, or null when it has none. */
    private final Item own;

    /**
     * What else this family takes, from families.&lt;name&gt;.repairBlocks, looked up once when this
     * cost was made rather than read out of the strings again for every slot of every square.
     */
    private final Substitute[] substitutes;

    private HealingCost(Player player, SurfaceFamily family, Item own, String[] entries) {
        this.player = player;
        this.family = family;
        this.own = own;
        this.substitutes = resolve(entries);
    }

    /**
     * What mending this block costs the player standing over it, or null when the ground does not
     * wear at all.
     *
     * <p>
     * It knows nothing of whether the cost is switched on, and nothing of creative mode. Those are
     * the gesture's to decide, once, when it chooses its ledger: a free ledger never asks what would
     * pay, and a price that also answered those questions here would be two places to keep in step.
     * A null player is allowed. It carries nothing, so it can pay for nothing.
     */
    public static HealingCost forPlayer(Player player, Block covered, int meta) {
        if (covered == null) return null;
        // The metadata is still taken and still ignored: every caller has one to give because the
        // engine's own signatures kept the shape, and there is nothing here for it to mean.
        SurfaceFamily aimedAt = SurfaceRegistry.familyOf(covered);
        if (aimedAt == null || !aimedAt.staged) return null;

        FamilySettings settings = TrmtConfig.family(aimedAt);
        // The block's own item. Null is ordinary rather than a fault: a double slab or a side block
        // has no item form, and then only the family's list can pay. Both older editions pair it with
        // the damage the block drops at, which is how that version names a placed block; here an item
        // names one on its own.
        return new HealingCost(
            player,
            aimedAt,
            covered.asItem(),
            settings == null ? null : settings.repairBlocks);
    }

    /**
     * The same question asked about a block rather than about a player standing over one.
     *
     * <p>
     * No player, no inventory - only the rule for what would pay. The golem needs exactly that and
     * nothing else: it has its own store to search and its own price, but what counts as the right
     * block is not its business to decide differently. It
     * asked differently for a long time, and demanded the very block it was mending down to the
     * metadata, which meant a golem could not mend a lawn without a stack of turf - the one thing
     * the world does not hand you, and the exact case repairBlocks exists to answer.
     *
     * <p>
     * Like {@link #forPlayer}, this ignores the bone-meal setting. That switch is about whether a
     * player's handful or chunk tamper is charged for. A golem is always charged, at the price its
     * stroke ledger sets (GolemWork.strokeLedger), and neither this setting nor a free tool reaches
     * it.
     */
    public static HealingCost forBlock(Block covered, int meta) {
        return forPlayer(null, covered, meta);
    }

    /**
     * Whether the player is carrying at least this many things that would pay. Stops counting as
     * soon as the figure is reached, since a large cube asks this for every block it buys.
     *
     * <p>
     * Asked fresh every time rather than answered from a slot remembered when this cost was made.
     * A slot remembered at construction can empty between a check and a take - the walk that asks
     * spends from the same inventory square after square - and trusting it would take whatever had
     * landed there since.
     *
     * @return false with no player, who carries nothing
     */
    public boolean has(int count) {
        if (player == null) return false;
        if (count <= 0) return true;
        List<ItemStack> inventory = player.inventory.items;
        int found = 0;
        // The hotbar comes first only because it is the front of this list, which is also the
        // order vanilla searches in. Armour lives in its own list and is never reached.
        for (int i = 0; i < inventory.size(); i++) {
            if (!matches(inventory.get(i))) continue;
            found += inventory.get(i)
                .getCount();
            if (found >= count) return true;
        }
        return false;
    }

    /**
     * Takes this many out of the inventory, or nothing at all.
     *
     * <p>
     * All or nothing, because a take that stops halfway has spent stock on a gradation it then
     * refuses. It counts first with {@link #has}, and takes only when the whole count is carried.
     * For the same reason as {@code has}, nothing is remembered from an earlier look: a slot
     * remembered at construction can empty between a check and a take.
     *
     * <p>
     * The block's own kind goes first, wherever it is carried: if you have the exact turf that was
     * worn, that is what fills the rut, not a family cousin that merely counts. Only when that runs
     * short does the second pass fall to anything else the family accepts.
     *
     * <p>
     * An emptied slot is nulled rather than left holding a stack of zero, because a zero-size stack
     * is not an absent item: it is one the container keeps describing to the client. Vanilla nulls
     * the slot for you only when it is the one in hand, and this one rarely is. The change is sent to
     * the client from here, straight away, for the reason given in the body.
     *
     * @return the distinct kinds taken, as made by {@link MendPurse#kindOf}; an empty list for a
     *         count of nought; null when the count was not carried and nothing was taken
     */
    public List<Object> take(int count) {
        if (count <= 0) return new ArrayList<Object>(0);
        if (!has(count)) return null;

        List<ItemStack> inventory = player.inventory.items;
        List<Object> kinds = new ArrayList<Object>(2);
        int remaining = takeFrom(inventory, count, true, kinds);
        if (remaining > 0) takeFrom(inventory, remaining, false, kinds);
        // Sent now rather than left to the container. This runs inside a right click, and vanilla ends
        // every right click by syncing the inventory as changing in quantity only, which records these
        // emptied slots as already sent while sending none of them - so the client went on showing, for
        // good, blocks the server had taken, a chunk tamper's four hundred and fifty as surely as bone
        // meal's one. A fake player has nobody to send them to.
        // "A fake player has nobody to send them to" asked without naming a loader's class for one:
        // a player with nothing connected to them is the same answer and a wider net. See Notices,
        // which asks it the same way and says why.
        if (player instanceof net.minecraft.server.level.ServerPlayer
            && ((net.minecraft.server.level.ServerPlayer) player).connection != null) {
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != null && player.containerMenu != player.inventoryMenu) {
                player.containerMenu.broadcastChanges();
            }
        }
        return kinds;
    }

    /** One pass of {@link #take}, returning what is still owed. */
    private int takeFrom(List<ItemStack> inventory, int remaining, boolean ownOnly, List<Object> kinds) {
        for (int i = 0; i < inventory.size() && remaining > 0; i++) {
            ItemStack held = inventory.get(i);
            if (ownOnly ? !paidByOwn(held) : !matches(held)) continue;
            Object kind = MendPurse.kindOf(held);
            if (!kinds.contains(kind)) kinds.add(kind);
            int taken = Math.min(remaining, held.getCount());
            held.shrink(taken);
            remaining -= taken;
            // Cleared to the sentinel rather than left as a stack of nothing, which is what vanilla's
            // own inventory does and what the rest of the game expects to find in an empty slot.
            if (held.isEmpty()) inventory.set(i, ItemStack.EMPTY);
        }
        return remaining;
    }

    /**
     * Whether this stack would pay. The same question {@link #matches} asks, opened up so a
     * caller can count what is carried before deciding how much work to do.
     */
    public boolean paidBy(ItemStack stack) {
        return matches(stack);
    }

    /**
     * Whether this stack is the very block that is worn, rather than something that merely counts.
     *
     * <p>
     * Opened up so a caller with a store of its own can spend the exact turf before it spends a
     * cousin, which is the order a player's own search already uses.
     */
    public boolean paidByOwn(ItemStack stack) {
        return stack != null && !stack.isEmpty() && own != null && stack.getItem() == own;
    }

    /**
     * Whether a stack will pay for this position.
     *
     * <p>
     * Three things do. A block of exactly what is there, which is the honest answer and the one
     * that lets a Biomes O' Plenty lawn be mended with Biomes O' Plenty turf; whatever the
     * family's repairBlocks names, which is where grass gets to be mended with earth; and, while
     * healing.repairAnyInFamily is on, any block of the same family.
     */
    private boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (own != null && stack.getItem() == own) return true;
        if (named(stack)) return true;
        return sameFamily(stack);
    }

    /**
     * Whether this stack is a block of the same family as the ground being mended.
     *
     * <p>
     * The default that lets a family mend itself: one modded turf fills another's ruts because
     * both are grass, with nothing listed by hand. A non-block item, or one of a different
     * family, is not it.
     */
    private boolean sameFamily(ItemStack stack) {
        if (!TrmtConfig.repairAnyInFamily || family == null) return false;
        Block block = Block.byItem(stack.getItem());
        if (block == null || block == Blocks.AIR) return false;
        return SurfaceRegistry.familyOf(block) == family;
    }

    /** Whether the stack is one of the things this family says it will also take. */
    private boolean named(ItemStack stack) {
        for (Substitute substitute : substitutes) {
            if (substitute.ore != null) {
                if (OreNames.matches(stack, substitute.ore)) return true;
                continue;
            }
            if (stack.getItem() == substitute.item) return true;
        }
        return false;
    }

    /**
     * Reads a family's repairBlocks entries into what they name.
     *
     * <p>
     * Done once per cost rather than once per slot. A cube priced by the gradation asks what pays
     * some thousands of times in one tick, and parsing the same few strings for every slot of
     * every one of them was most of what that cost.
     */
    private static Substitute[] resolve(String[] entries) {
        if (entries == null || entries.length == 0) return NO_SUBSTITUTES;
        List<Substitute> found = new ArrayList<Substitute>(entries.length);
        for (String raw : entries) {
            if (raw == null) continue;
            String entry = raw.trim();
            if (entry.isEmpty()) continue;

            // Ore groups are marked rather than inferred from a missing mod id. Every other
            // block list in this config reads a bare name as a vanilla block, and the same
            // string meaning two different things in adjacent settings is a trap.
            if (entry.regionMatches(true, 0, ORE_PREFIX, 0, ORE_PREFIX.length())) {
                String oreName = entry.substring(ORE_PREFIX.length());
                // Kept whatever the pack happens to hold, unlike both older editions, which skip a
                // group nothing provides. There it is an id lookup and asking registers the group; a
                // tag is only a name until something has it, and a pack that adds the mod supplying
                // it later should not need its settings read again.
                if (oreName.isEmpty()) continue;
                found.add(new Substitute(null, oreName));
                continue;
            }

            String name = entry;
            int lastColon = entry.lastIndexOf(':');
            // "modid:block:meta" - still parsed, still dropped. The suffix meant a metadata, which
            // this version does not have, and a settings file written for an older edition is full of
            // them; refusing one would quietly take a block off a family's repair list.
            if (lastColon > 0 && entry.indexOf(':') != lastColon) {
                name = entry.substring(0, lastColon);
            }

            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(name);
            if (id == null) continue;
            Block block = net.minecraft.core.Registry.BLOCK.getOptional(id)
                .orElse(null);
            // A stale entry is skipped in silence: pack composition changes.
            if (block == null || block == Blocks.AIR) continue;
            Item item = block.asItem();
            if (item == null || item == net.minecraft.world.item.Items.AIR) continue;
            found.add(new Substitute(item, null));
        }
        return found.toArray(new Substitute[found.size()]);
    }

    /** One repairBlocks entry, looked up: an item, or the name of a group of them. */
    private static final class Substitute {

        /** The item named, or null for a group. */
        private final Item item;

        /**
         * The group named, or null when this names an item.
         *
         * <p>
         * A name rather than the integer id both older editions hold, because a group is a tag here
         * and a tag is asked by name. The id those editions look up is also why they check first that
         * the group exists: asking for an id registers the group if nobody else has, so a name
         * nothing provides would be quietly invented. Nothing is invented by asking about a tag.
         */
        private final String ore;

        Substitute(Item item, String ore) {
            this.item = item;
            this.ore = ore;
        }
    }
}
