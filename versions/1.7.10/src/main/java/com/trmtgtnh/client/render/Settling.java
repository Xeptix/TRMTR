package com.trmtgtnh.client.render;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.BlockSnow;
import net.minecraft.util.Facing;
import net.minecraft.world.IBlockAccess;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.GhostBlock;
import com.trmtgtnh.block.GhostRendering;
import com.trmtgtnh.erosion.ISettlingBlock;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Blocks that rest on worn ground and are drawn down with it.
 *
 * <p>
 * A snow layer sitting on a rut used to hang in the air over it: the ground it was lying on had
 * dropped half a block and the snow had not, because a block's shape is its own business and
 * nothing was telling it what had happened underneath. That reads worse than the rut it is meant
 * to be showing off.
 *
 * <p>
 * The shift is written into the renderer's own bounds rather than into the block's. A block's
 * bounds are one static object shared by every instance of it in the world, and in single player
 * the integrated server reads that same object on its own thread - so lowering a snow layer that
 * way would give the server a half-block of collision where it has none, and stop the player
 * placing snow at their own feet. The renderer's bounds are per instance, and both vanilla and
 * Celeritas build one renderer per chunk section they mesh, so writing there cannot escape the
 * section being drawn.
 *
 * <p>
 * Deliberately narrow. Snow and carpet only, because those are the two that lie flat on the ground
 * with nothing underneath them, have no collision to disagree with, and carry a texture uniform
 * enough that the renderer's out-of-range fallback - which stretches a whole sprite over a side
 * face once its bounds leave the block - cannot be seen. A rail or a repeater would settle just as
 * easily and would then be drawn a step below the box the server still holds it at.
 */
@SideOnly(Side.CLIENT)
public final class Settling {

    /** Whether anything at all can settle, so the render guard is one static read when it cannot. */
    private static volatile boolean active;

    private Settling() {}

    public static boolean isActive() {
        return active;
    }

    /**
     * Stamps the blocks that rest on the ground, once, after surfaces are resolved.
     *
     * <p>
     * Same shape as {@code PhysicalDecay.markSinkableBlocks} and for the same reason: the render
     * guard reads a field on the block instance rather than asking a registry, so the question has
     * to have been answered before anything is drawn.
     */
    public static void markSettlingBlocks() {
        boolean any = false;
        for (Object candidate : Block.blockRegistry) {
            if (!(candidate instanceof ISettlingBlock)) continue;
            any |= ((ISettlingBlock) candidate).trmt$isSettling();
        }
        active = any;
    }

    /**
     * How far the ground under this position has dropped, in block units, or zero.
     *
     * <p>
     * Read off the ghost below rather than off the record directly, because what wants matching is
     * what is drawn: a ghost knows both how far it has sunk and how tall the block it is standing
     * in for was, and those are not the same question on a grass path that already stood short.
     */
    public static double dropFor(IBlockAccess world, int x, int y, int z) {
        if (!active || world == null || y <= 0) return 0.0D;
        Block below = world.getBlock(x, y - 1, z);
        if (!(below instanceof GhostBlock)) return 0.0D;
        return GhostRendering.settledDrop((GhostBlock) below, world, x, y - 1, z);
    }

    /** Whether this block is one that settles, as one field read. */
    public static boolean settles(Block block) {
        return active && block instanceof ISettlingBlock && ((ISettlingBlock) block).trmt$isSettling();
    }

    /**
     * Whether the correction below has ever actually reached a face, and how often one was wanted.
     *
     * <p>
     * Here because the six hooks that apply it are injected without a requirement, and an injector
     * that does not apply says nothing whatever. That is not an opinion about log levels: the mixin
     * library this runs under throws on a missing target only when the injector declares a
     * requirement or when its debug option is set, and there is no other branch - no warning, no
     * debug line, nothing. Neither is set here. So a hook that failed to bind, or bound to a method
     * some future mod had replaced, would leave the band exactly as it is and leave no trace at all,
     * which is how two features in this mod have already shipped dead.
     *
     * <p>
     * A requirement is the wrong cure: it would turn a renderer detail into a white screen on a
     * client whose only crime is running a mod that rewrote a vanilla method. So the tell is taken
     * from behaviour instead, and it costs nothing. The flag is written only on a face genuinely
     * being corrected, which happens at a wear seam and nowhere else, and the count is raised only
     * when a mask comes back with something in it. Wanted in quantity and never once applied means
     * the hooks are not there.
     */
    private static volatile boolean cullHookRan;

    private static int masksProduced;

    private static boolean cullHookWarned;

    /** Called from the render hook, once per face it actually corrects. */
    public static void noteCullHookRan() {
        cullHookRan = true;
    }

    private static boolean shiftHookSaid;

    /**
     * Called from the renderer injection the first time it reaches a block that settles.
     *
     * <p>
     * The other half of the tell below, and it exists because that one cannot speak for itself. If
     * the injection that shifts a settled block fails to bind, the mask is never worked out, the
     * count never rises, and the error that would have blamed the culling hooks can never fire - so
     * the failure of one injector would silence the only warning about the six downstream of it.
     * One line, once, on a path only snow and carpet reach.
     */
    public static void noteShiftHookRan() {
        if (shiftHookSaid) return;
        shiftHookSaid = true;
        Trmt.LOG.info("Settling a block onto worn ground; the injection into the standard block renderer is in place");
    }

    /**
     * The faces of this block that must be drawn whatever the culling rule concluded, a bit per side
     * in the order the game numbers them.
     *
     * <p>
     * The shift this class exists for cannot move a culling decision: every face test asks the
     * block's own bounds, stamped before the shift, about an unshifted neighbour cell. Vanilla does
     * not care, because it never culls the face between two snow layers at all - its test falls
     * through to "is the neighbour an opaque cube" and a snow layer is not one. A client running the
     * better face culling that comes with Angelica hands the question to the neighbour, which
     * compares the caller's height against its own; two layers of equal depth find them equal and
     * cull the face they share from both sides. Then the shift moves one of them down, and the band
     * between the two drawn heights has no face from either. That is the hole.
     *
     * <p>
     * What closes it is the block that has <em>not</em> come down. Its side face already spans the
     * band, and it was culled only because the block it asked about had not moved yet. Which is why
     * this is worked out for every settling block and not only for the ones standing in a rut: a
     * block whose own drop is nought is exactly the one that has to draw. Forcing faces on the block
     * that sank would fix nothing, because its face is below the band rather than across it.
     *
     * <p>
     * The gate on the drops is what makes this safe rather than merely cheap. Where two neighbours
     * have worn the same amount every arm below reduces to precisely the comparison the culling rule
     * already makes - a side face wanted when one stands taller, a top face when what is above does
     * not reach down, a bottom face when what is below does not reach up - so agreeing to differ
     * only where the drops differ cannot overturn a decision that was right to begin with. It is
     * also where the hot path leaves, because both drops are nought everywhere nothing has worn,
     * which is nearly everywhere.
     *
     * <p>
     * Nothing here can remove a face; a set bit only ever turns a no into a yes. So the worst this
     * can do is draw a quad something else already covers, and a quad drawn into solid ground faces
     * the wrong way to be seen.
     *
     * @param ownBottom the bottom of the box this block will be drawn as, in world units, dropped
     * @param ownTop    the top of that same box
     * @param ownDrop   how far the box was moved down, which is what a neighbour has to match
     */
    public static int forcedFaces(IBlockAccess world, int x, int y, int z, double ownBottom, double ownTop,
        double ownDrop) {
        if (!active || world == null) return 0;

        int mask = 0;
        for (int side = 0; side < 6; side++) {
            int nx = x + Facing.offsetsXForSide[side];
            int ny = y + Facing.offsetsYForSide[side];
            int nz = z + Facing.offsetsZForSide[side];

            // Whatever stands above rests on this one, and this one is snow or carpet rather than a
            // ghost - so asking would spend a lookup to be told nought.
            double neighbourDrop = side == 1 ? 0.0D : dropFor(world, nx, ny, nz);
            if (neighbourDrop == ownDrop) continue;

            // Only reached where the two disagree, which is what earns the dearer test.
            Block neighbour = world.getBlock(nx, ny, nz);
            if (!settles(neighbour)) continue;

            double neighbourBottom = ny - neighbourDrop;
            double neighbourTop = ny + restingHeight(world, neighbour, nx, ny, nz) - neighbourDrop;

            boolean open;
            if (side == 0) open = neighbourTop < ownBottom;
            else if (side == 1) open = neighbourBottom > ownTop;
            else open = ownTop > neighbourTop || ownBottom < neighbourBottom;

            if (open) mask |= 1 << side;
        }

        if (mask != 0) {
            // Racy on purpose. It counts how often a correction was wanted, read by the check below
            // and by nothing that matters; a lost increment on a mesher thread costs nothing and a
            // lock on this path would cost everything.
            masksProduced++;
            if (!cullHookRan && !cullHookWarned && masksProduced > 4096) {
                cullHookWarned = true;
                Trmt.error(
                    "Settling has wanted a face correction {} times and the six culling hooks that would apply it have never run, so worn snow and carpet will show a gap along a wear seam on a client with better face culling. Those six did not take; the injection that shifts the block did, or this line could not have been reached.",
                    Integer.valueOf(masksProduced));
            }
        }
        return mask;
    }

    /**
     * How tall a settling block stands before anything moved it, read the way the culling rule reads
     * it.
     *
     * <p>
     * From metadata rather than from the block's own bounds, for both of the obvious reasons: those
     * bounds are one object shared by every instance in the world and re-stamped by whichever mesher
     * thread is drawing next, and metadata is what the rule on the other side of the comparison
     * uses. The arithmetic is kept in float and widened afterwards so that the two cannot part
     * company in the last bit. The fallback is the shared field on purpose - a modded settling block
     * is modelled through that same field over there, and agreeing is the point.
     */
    private static double restingHeight(IBlockAccess world, Block block, int x, int y, int z) {
        if (block instanceof BlockSnow) {
            return (float) (2 * ((world.getBlockMetadata(x, y, z) & 7) + 1)) * 0.0625F;
        }
        if (block instanceof BlockCarpet) return 0.0625D;
        return block.getBlockBoundsMaxY();
    }

}
