package com.trmtgtnh.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.erosion.ErosionEngine;
import com.trmtgtnh.erosion.ErosionEntry;
import com.trmtgtnh.erosion.ErosionStore;
import com.trmtgtnh.erosion.MendLedger;
import com.trmtgtnh.server.MendPurse;
import com.trmtgtnh.server.Notices;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * What a tamper does, and what each gesture costs. Server side only.
 *
 * <p>
 * Both halves of the item come through here — the right-click gestures from
 * {@link ItemTamper#onItemUse} and the left-click ones from {@link TamperEvents} — so the rules
 * are written once rather than twice with a chance of drifting apart.
 *
 * <p>
 * Every method answers one question, did this change anything, and that is the same question as
 * whether the tool should be charged for it. Nothing here spends a use on a gesture that found
 * nothing to do, so brushing the tool along ordinary ground is free and the durability bar only
 * ever moves for work. Material is held to the same rule more strictly still: a block is taken
 * only once the gradation it pays for is back, so a mend that is refused, or that finds less to do
 * than it might have, never costs a block for work that did not happen.
 *
 * <p>
 * Positions are identified from the erosion record and never from the block. The server's copy
 * of the world holds no ghost — that is the whole design — so what is under the crosshair here
 * is the ordinary ground the overlay is painted over, and whether it is worn is a question only
 * the record can answer.
 */
public final class TamperActions {

    /**
     * Handed to blocks that want one when asked what they drop. Nothing decided here depends on
     * what it returns beyond one gesture, and each gesture asks once per kind of block, so it is
     * shared and never seeded.
     */
    private static final Random SAMPLE = new Random();

    private TamperActions() {}

    // ------------------------------------------------------------------
    // Gestures
    // ------------------------------------------------------------------

    /**
     * Sneak + right-click: pin a square against wear and time, or hand it back.
     *
     * <p>
     * Through the engine's seam rather than the flag on the entry, because releasing has to
     * restamp the inactivity clock. A square pinned for a fortnight has a fortnight of healing
     * arrears waiting for it otherwise, and collapses to bare ground in the next sweep.
     */
    public static boolean pin(World world, int x, int y, int z, EntityPlayer player, ItemStack tool) {
        if (!TrmtConfig.enabled || !TrmtConfig.tamperCanPin) return false;

        ErosionEntry entry = ErosionStore.get()
            .getEntry(world, x, y, z);
        if (entry == null || !entry.isVisible()) return false;

        boolean wanted = !entry.isFrozen();
        if (!ErosionEngine.get()
            .setFrozen(world, x, y, z, wanted)) return false;

        // Two pitches of the same click, so pinning and releasing are told apart by ear without
        // anybody having to read the line.
        world.playEvent(wanted ? 1001 : 1000, new net.minecraft.util.math.BlockPos(x, y, z), 0);
        say(
            player,
            wanted ? TextFormatting.AQUA + "Pinned. This square will not wear and will not recover."
                : TextFormatting.GRAY + "Released. Wear and recovery start again from now.");
        spend(player, tool);
        return true;
    }

    /**
     * Right-click: mend a scattered patch, the way a handful of bone meal does.
     *
     * <p>
     * The blotchy patch rather than one tidy square is what the higher price buys, and what
     * makes this the gesture for filling a track in roughly before tidying its edges with the
     * single-square mend.
     *
     * <p>
     * Paid for in what the ground drops, as the patch is walked. Each kind of ground the patch
     * meets pays tamperPatchCost once, as the first gradation of it goes back, so a patch across a
     * grass verge and a cobble road costs that in earth and that again in cobble. A square is
     * covered only by material it would take itself - cobble bought for a stone square never pays
     * for the mossy cobble or the granite beside it - and a square whose material is not carried is
     * left exactly as it was, with a line in chat to say so.
     */
    public static boolean mendPatch(World world, int x, int y, int z, EntityPlayer player, ItemStack tool, int reach) {
        if (!workable(world, x, y, z, player)) return false;

        boolean free = player.capabilities.isCreativeMode || TrmtConfig.tamperPatchCost <= 0;
        MendLedger ledger = free ? MendLedger.free() : MendLedger.perGesture(TrmtConfig.tamperPatchCost);
        MendPurse purse = new MendPurse(player, ledger, new ByDrop(player));

        int mended = ErosionEngine.get()
            .mendPatch(world, x, y, z, reach, purse.patchWork());
        if (mended <= 0) {
            purse.reportNothing(player, TrmtConfig.tamperPatchCost, true);
            return false;
        }
        // Only what was paid for is worth anything. A free patch mends as much and earns nothing.
        com.trmtgtnh.server.HealingXp.award(player, tool, ledger.paidGradations());
        purse.reportShort(player, true);
        // One block up. Played at the ground itself the particles spawn inside it and most of
        // them are never seen.
        world.playEvent(2005, new net.minecraft.util.math.BlockPos(x, y + 1, z), 0);
        spend(player, tool);
        return true;
    }

    /**
     * Sneak + left-click: one gradation back on one square, and nothing either side of it.
     *
     * <p>
     * Paid for with tamperMendCost of what the ground drops, taken once the gradation is back. A
     * refusal for want of material says what was wanted; a refusal for any other reason says
     * nothing here, because the player's pocket was not the reason.
     */
    public static boolean mendOne(World world, int x, int y, int z, EntityPlayer player, ItemStack tool) {
        if (!workable(world, x, y, z, player)) return false;

        boolean free = player.capabilities.isCreativeMode || TrmtConfig.tamperMendCost <= 0;
        MendLedger ledger = free ? MendLedger.free() : MendLedger.perGesture(TrmtConfig.tamperMendCost);
        MendPurse purse = new MendPurse(player, ledger, new ByDrop(player));

        if (purse.mendSquare(world, x, y, z, 1, true) <= 0) {
            purse.reportNothing(player, TrmtConfig.tamperMendCost, true);
            return false;
        }
        com.trmtgtnh.server.HealingXp.award(player, tool, ledger.paidGradations());
        world.playEvent(2005, new net.minecraft.util.math.BlockPos(x, y + 1, z), 0);
        spend(player, tool);
        return true;
    }

    /**
     * Left-click: one more gradation of wear, for nothing but the tool.
     *
     * <p>
     * Free because it is the one gesture that takes something away rather than putting it back.
     * There is nothing to buy — the ground it wears in is the ground that was already there —
     * and charging for it would make laying out a path cost more than the path.
     */
    public static boolean wearOne(World world, int x, int y, int z, EntityPlayer player, ItemStack tool) {
        if (!wearAt(world, x, y, z, player, true)) return false;
        spend(player, tool);
        return true;
    }

    /**
     * One more gradation of wear at one position, without spending anything.
     *
     * <p>
     * Split out of {@link #wearOne} so that the chunk tamper can do the same thing across a cube
     * without a second copy of the rules going quietly out of step with this one. The tool is
     * spent by the caller, once per gesture rather than once per square, which is the same way
     * the mending gestures are priced.
     *
     * @param noisy whether to play the scuff and say why a refusal happened; false across a
     *              sweep, where one refusal per square would be a wall of chat
     */
    static boolean wearAt(World world, int x, int y, int z, EntityPlayer player, boolean noisy) {
        if (!TrmtConfig.enabled || !TrmtConfig.tamperCanWear) return false;
        if (!trackable(world, x, y, z)) return false;

        Block block = com.trmtgtnh.util.Worlds.blockAt(world, x, y, z);
        SurfaceFamily base = SurfaceRegistry.familyOf(block, com.trmtgtnh.util.Worlds.metaAt(world, x, y, z));
        if (base == null || !base.staged) return false;

        ErosionEntry entry = ErosionStore.get()
            .getEntry(world, x, y, z);
        // A pin holds against everyone, this tool included. forceStage writes the flag rather
        // than inheriting it, so pushing through would leave the square quietly unpinned with a
        // tooltip line as the only clue — which is worse than a gesture that refuses out loud.
        if (entry != null && entry.isFrozen()) {
            if (noisy) say(player, TextFormatting.AQUA + "Pinned. Release it before working it.");
            return false;
        }

        // -1 both for a position with nothing showing yet and for one whose appearance no longer
        // belongs to this block's chain, and +1 is right for both — the same arithmetic the
        // engine's own advance does with the same value.
        int index = entry == null || !entry.isVisible() ? -1
            : ErosionChain.indexOf(base, entry.getFamily(), entry.getStage(), entry.getSink());
        int next = index + 1;
        // Asked here rather than left to forceStage, which clamps. Clamping would rewrite the
        // last gradation onto itself, report success, and charge the tool for work it did not do.
        // The same ceiling traffic obeys. A tool that could wear ground past the point the
        // world allows would be a way round a setting rather than a use of one.
        if (next >= ErosionChain.cappedLength(base)) return false;

        int sankFrom = entry == null ? 0 : entry.getSink();
        if (!ErosionEngine.get()
            .forceStage(world, x, y, z, base, next, false, true)) return false;

        com.trmtgtnh.erosion.WearDrops.roll(world, x, y, z, base);
        ErosionEntry after = ErosionStore.get()
            .getEntry(world, x, y, z);
        // A gradation worked in by hand is still a gradation, and the plant standing on the
        // square has no way of telling a boot from a tamper. Quiet across a sweep for the same
        // reason the scuff is: one break sound per square of a chunk is a wall of noise.
        com.trmtgtnh.erosion.GroundCover.breakAbove(world, x, y, z, noisy, after != null && after.getSink() > sankFrom);

        // The sound a boot makes on it, at the volume the client uses for a footfall. A break
        // sound would be far too loud for one more scuff.
        if (noisy) {
            net.minecraft.block.SoundType step = block.getSoundType(
                com.trmtgtnh.util.Worlds.stateAt(world, x, y, z),
                world,
                new net.minecraft.util.math.BlockPos(x, y, z),
                null);
            world.playSound(
                null,
                x + 0.5D,
                y + 0.5D,
                z + 0.5D,
                step.getStepSound(),
                net.minecraft.util.SoundCategory.BLOCKS,
                (step.getVolume() + 1.0F) / 8.0F,
                step.getPitch() * 0.5F);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // What may be touched
    // ------------------------------------------------------------------

    /**
     * Whether there is worn ground here this tool may mend — having said why, when there is not.
     *
     * <p>
     * Saying why matters for exactly one case. Both mending gestures fail silently on a pinned
     * square otherwise, because the engine simply declines, and the only clue would be a tooltip
     * line the player has to go and look for.
     */
    private static boolean workable(World world, int x, int y, int z, EntityPlayer player) {
        if (!TrmtConfig.enabled) return false;
        ErosionEntry entry = ErosionStore.get()
            .getEntry(world, x, y, z);
        if (entry == null || !entry.isVisible()) return false;
        if (entry.isFrozen()) {
            say(player, TextFormatting.AQUA + "Pinned. Release it before working it.");
            return false;
        }
        return true;
    }

    /**
     * Whether this is a square the engine would ever have tracked by itself.
     *
     * <p>
     * The same questions {@code step} asks before it banks anything, because {@code forceStage}
     * asks none of them. A tool that could write records outside them would leave paths inside
     * walls and under floors, in dimensions that were switched off and at heights nothing
     * sweeps — and nothing else in the mod would ever come along and clear them.
     */
    private static boolean trackable(World world, int x, int y, int z) {
        if (!TrmtConfig.dimensionAllowed(world.provider.getDimension())) return false;
        if (y < TrmtConfig.minY || y > TrmtConfig.maxY) return false;
        if (!com.trmtgtnh.util.Worlds.loaded(world, x, y, z)) return false;
        return !com.trmtgtnh.util.Worlds.isOpaque(world, x, y + 1, z);
    }

    // ------------------------------------------------------------------
    // Material
    // ------------------------------------------------------------------

    /**
     * A stack of what a rut in this ground is filled back in with.
     *
     * <p>
     * What the block drops rather than the block itself, which is both the thematic answer and
     * the practical one: grass drops earth, and earth is what a rut in a lawn is actually filled
     * with, where charging grass blocks would mean no path could be mended without silk touch.
     * Stone drops cobble on the same principle.
     *
     * <p>
     * Gravel's drop is sometimes flint, and no rut was ever filled with flint. Anything that is
     * not a block in its own right means the question came back wrong, so the block itself is
     * the fallback.
     */
    private static ItemStack fillFor(World world, int x, int y, int z, Block block, int meta) {
        net.minecraft.block.state.IBlockState state = block.getStateFromMeta(meta);
        Item dropped = block.getItemDropped(state, SAMPLE, 0);
        if (dropped instanceof ItemBlock) return new ItemStack(dropped, 1, block.damageDropped(state));

        Item self = Item.getItemFromBlock(block);
        return self == null ? ItemStack.EMPTY
            : new ItemStack(self, 1, block.damageDropped(com.trmtgtnh.util.Worlds.stateAt(world, x, y, z)));
    }

    /** How much of the fill is carried, counting no further than {@code enough}. */
    private static int count(EntityPlayer player, ItemStack fill, int enough) {
        int found = 0;
        List<ItemStack> slots = player.inventory.mainInventory;
        for (int i = 0; i < slots.size() && found < enough; i++) {
            if (matches(slots.get(i), fill)) found += slots.get(i)
                .getCount();
        }
        return found;
    }

    private static void take(EntityPlayer player, ItemStack fill, int amount) {
        List<ItemStack> slots = player.inventory.mainInventory;
        for (int i = 0; i < slots.size() && amount > 0; i++) {
            ItemStack held = slots.get(i);
            if (!matches(held, fill)) continue;
            int taken = Math.min(amount, held.getCount());
            held.shrink(taken);
            amount -= taken;
            if (held.isEmpty()) slots.set(i, ItemStack.EMPTY);
        }
        player.inventoryContainer.detectAndSendChanges();
    }

    /**
     * Exact item and damage, and deliberately not the ore dictionary.
     *
     * <p>
     * Two blocks that share an ore name are routinely different ground to look at — GregTech's
     * stone types all answer to stone — and a repair that quietly ate the wrong one out of the
     * bag is worse than a repair that refuses and says what it wanted.
     */
    private static boolean matches(ItemStack held, ItemStack fill) {
        return held != null && held.getItem() == fill.getItem() && held.getItemDamage() == fill.getItemDamage();
    }

    /**
     * The hand tamper's rule for what pays: what the ground drops, matched by exact item and damage.
     *
     * <p>
     * Kept apart from bone meal's rule on purpose. That one would take stone for stone where this
     * takes cobble, would spend carried grass before earth, and would let one GregTech stone pay for
     * another, which {@link #matches} refuses. The walk and the arithmetic are shared with bone meal
     * and the chunk tamper; only the answer to what pays is this tool's own.
     *
     * <p>
     * The drop is worked out once per block and metadata for the gesture. Some blocks roll their
     * drop at random, and asking twice could find a square affordable in one item and then charge it
     * in another.
     */
    private static final class ByDrop implements MendPurse.Payer {

        private final EntityPlayer player;
        private final Map<Long, MendPurse.Quote> quotes = new HashMap<Long, MendPurse.Quote>();

        ByDrop(EntityPlayer player) {
            this.player = player;
        }

        @Override
        public MendPurse.Quote quote(World world, int x, int y, int z, Block block, int meta) {
            Long key = Long.valueOf(((long) Block.getIdFromBlock(block) << 32) | (meta & 0xFFFFFFFFL));
            if (quotes.containsKey(key)) return quotes.get(key);

            final ItemStack fill = fillFor(world, x, y, z, block, meta);
            // A block with no item form has nothing a player could carry to fill it, so it is
            // never priced and never named as short.
            MendPurse.Quote quote = fill == null ? null : new MendPurse.Quote() {

                /** Whether each kind met so far is this fill, so a pool is judged once per kind. */
                private final Map<Object, Boolean> answers = new HashMap<Object, Boolean>(4);

                @Override
                public boolean accepts(Object kind) {
                    Boolean known = answers.get(kind);
                    if (known == null) {
                        known = Boolean.valueOf(TamperActions.matches(MendPurse.stackOf(kind), fill));
                        answers.put(kind, known);
                    }
                    return known.booleanValue();
                }

                @Override
                public boolean has(int count) {
                    return count <= 0 || TamperActions.count(player, fill, count) >= count;
                }

                @Override
                public List<Object> take(int count) {
                    if (count <= 0) return new ArrayList<Object>(0);
                    // All or nothing: a take that stopped halfway would have spent material on a
                    // gradation it then refused.
                    if (!has(count)) return null;
                    TamperActions.take(player, fill, count);
                    List<Object> kinds = new ArrayList<Object>(1);
                    kinds.add(MendPurse.kindOf(fill));
                    return kinds;
                }

                @Override
                public String wanted() {
                    return fill.getDisplayName();
                }
            };
            quotes.put(key, quote);
            return quote;
        }
    }

    // ------------------------------------------------------------------
    // Wear on the tool, and on the player's patience
    // ------------------------------------------------------------------

    /**
     * One point off the tool, and only ever after something actually changed.
     *
     * <p>
     * {@code damageItem} empties the stack rather than the slot, so the slot has to be cleared
     * here. The right-click path would get away without it — the block placement packet tidies a
     * zero-sized held stack on its way out — but the left-click path has no such packet, and a
     * zero-sized stack left in a hotbar slot draws as an item nobody can use or drop.
     */
    private static void spend(EntityPlayer player, ItemStack tool) {
        if (tool == null || player.capabilities.isCreativeMode) return;
        // The Wayfarer's comes through here on a sneak left-click, borrowed from the hand tamper,
        // and every other path that damages a tool asks first whether it wears at all. This one did
        // not, so the tool that never wears out wore out a point a click, and broke at its fallback
        // grade's figure with the nether star inside it.
        if (tool.getItem() instanceof ItemChunkTamper && !((ItemChunkTamper) tool.getItem()).wearsOut(tool)) {
            return;
        }
        tool.damageItem(1, player);
        // The other edition follows this by taking a broken tool out of the hand. damageItem does that
        // itself now: it shrinks the stack where it lies, and a stack of nothing is empty everywhere.
    }

    /**
     * One line with the mod's mark in front, at most once a second per player.
     *
     * <p>
     * The limit, and the map it needs, live in {@link Notices} now, shared with every other line
     * about a gesture. A player standing on the wrong ground with right-click held is told once a
     * second however many things there are to say, rather than once a second per thing.
     */
    private static void say(EntityPlayer player, String message) {
        Notices.say(player, Notices.line(message, null, true));
    }
}
