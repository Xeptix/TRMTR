package com.trmtgtnh.client.render;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.mixin.OculusGate;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * What a shader pack thinks worn ground is made of.
 *
 * <p>
 * A shader pack decides how a block behaves under light - how it shines, whether it glows, how rough
 * it reads - by looking the block up by name in its own table. A ghost is not in anybody's table, so
 * under a shader pack a worn stone road did not merely lose its polish: it lost every material
 * property the unworn block had and fell back on whatever the pack does with a stranger.
 *
 * <p>
 * The other edition says otherwise through a pair of static methods Angelica added for exactly this
 * - {@code Iris.setShaderMaterialOverride(Block, int)} and {@code resetShaderMaterialOverride} - and
 * that pair is Angelica's own. It is in no Iris and in no Oculus. What every member of that family
 * does have is the thing those two methods are a convenience for, and on 1.12.2 it is reachable one
 * step further in.
 *
 * <p>
 * Oculus resolves a block's shader id once per block, in {@code BlockContextHolder.set}, out of the
 * table the loaded pack built; the vertex writer then reads the answer off that holder as it writes.
 * So the holder is the seat of the claim, and the window between the holder being set and the
 * vertices being written is exactly where a ghost gets to say it is something else. A one-line mixin
 * into that holder hands the holder over whenever the block it was set for is one of ours, and
 * {@code GhostBakedModel} - which runs inside that window, and which already knows the position's
 * whole record - says what to claim.
 *
 * <p>
 * <strong>OptiFine has the same window, on a different object</strong> - see {@link OptiFineMaterial}.
 * It keeps a block's shader id on a stack carried by the chunk's own buffer, pushed as the block's model
 * starts and popped as it ends, and {@code GhostBakedModel} runs in between. So the one claim this class
 * works out is handed to whichever of the two seats this thread holds. Neither is named at compile time,
 * and the two never meet on one client.
 *
 * <p>
 * Nothing of Oculus is named at compile time and nothing is added to the mod's dependencies. The
 * holder arrives as {@code Object}, its one method is reached by reflection, and the mixin that
 * provides it is refused outright by {@link com.trmtgtnh.mixin.OculusGate} on a client that has no
 * Oculus. With Oculus but no shader pack the pack's table is empty, every id in it is -1, and a claim
 * of -1 over an id of -1 is the no-op it looks like - so this is self-detecting in the same way the
 * other edition's is, rather than configured.
 *
 * <p>
 * <strong>What a ghost claims is the 1.7.10 edition's answer</strong>, from its {@code ShaderMaterial}:
 * the block it covers while it still wears as that block's own family, so a worn granite road is
 * granite as far as the pack is concerned, and once its wear has reached another material, that
 * material's own block - cobblestone, then gravel, then earth - so the shine goes as the road breaks up,
 * not by fading a number nobody authored but by becoming what the pack already has real values for.
 * Until 0.9.219 this edition claimed the covered block until the picture ran out and earth after it,
 * so a stone road worn to its cobble stage still told the pack it was stone; the family test was the
 * one clause the port had left out. Where the picture is the bare earth, the claim is earth.
 *
 * <p>
 * <strong>Not yet seen working under Oculus.</strong> Every shape named for it was read from Oculus's
 * own source for 1.12.2, but Oculus has never released a 1.12.2 build. Both ways it can be wrong are
 * ways it switches itself off: the gate refuses the mixin when the holder is absent, the inject is
 * {@code require = 0} so a holder that has moved its method simply never seats one, and the first
 * reflective failure gives up for the session. None of them reach the renderer.
 */
@SideOnly(Side.CLIENT)
public final class ShaderMaterial {

    private static Method set;

    private static Field renderType;

    private static boolean looked;

    /**
     * The holder for the ghost this thread is drawing, and what has already been claimed for it.
     *
     * <p>
     * Per thread because chunk meshing is not: every worker builds into buffers of its own and so
     * holds a holder of its own, and a seat shared between them would have one thread claiming
     * another's ground. One object rather than two thread-locals, so the hot path is a single lookup.
     */
    private static final ThreadLocal<Seat> SEAT = new ThreadLocal<Seat>();

    private static final class Seat {

        Object holder;

        IBlockState claimed;
    }

    private ShaderMaterial() {}

    /** Whether there is anything to say and anybody to say it to, under Oculus. */
    public static boolean available() {
        if (!TrmtConfig.inheritShaderMaterial) return false;
        if (!looked) look();
        return set != null;
    }

    private static synchronized void look() {
        if (looked) return;
        looked = true;
        try {
            Class<?> holder = Class.forName(OculusGate.HOLDER);
            set = holder.getMethod("set", IBlockState.class, short.class);
            // Read rather than chosen: re-resolving the id must not quietly re-label which pass this
            // block is being drawn in, so the claim goes back in carrying the render type it found.
            renderType = holder.getField("renderType");
            Trmt.LOG.info("Worn ground will keep the shader material of the block it covers");
        } catch (Throwable notThere) {
            // A version of Oculus that moved it, or the gate let the mixin through on something that
            // only looks like it. Nothing is lost that was not already lost, so this is worth one
            // line rather than a stack trace.
            set = null;
            renderType = null;
            Trmt.LOG.debug("Oculus is present but has no block context to claim: {}", notThere.toString());
        }
    }

    /**
     * Takes the seat for a ghost that is about to be drawn.
     *
     * <p>
     * Called from the mixin, and only for one of our own blocks - so a chunk of ordinary ground pays
     * one {@code instanceof} per block and nothing else. Whatever was claimed is forgotten here
     * rather than when the claim is made, because the holder has just been set to the ghost's own id
     * and a claim that still thought it was in force would leave the ghost a stranger again.
     */
    public static void seat(Object holder) {
        Seat seat = SEAT.get();
        if (seat == null) {
            seat = new Seat();
            SEAT.set(seat);
        }
        seat.holder = holder;
        seat.claimed = null;
    }

    /**
     * Gives the seat up, because the block now being drawn is not one of ours.
     *
     * <p>
     * Without this a seat outlives the square it was taken for. The holder is handed over once per
     * ghost and the claim is made from the model, and in between the mesher may have moved on to
     * ordinary ground - or left the chunk build altogether and be drawing this model into an item
     * frame or a tooltip, where asking a holder to be something else would be writing over whatever
     * that holder is in the middle of. A thread with no ghost in front of it holds no seat.
     */
    public static void unseat() {
        Seat seat = SEAT.get();
        if (seat != null) {
            seat.holder = null;
            seat.claimed = null;
        }
    }

    /**
     * Says what the ghost being drawn should be taken for.
     *
     * <p>
     * Asked once per face, and the faces of one square all want the same answer, so the second ask
     * onwards is a reference compare. Called with the family the square's wear has reached, or null
     * where the model is drawing the bare earth.
     *
     * @param origin     the covered block's state id, or -1 when nothing was recorded
     * @param appearance the family the square wears as, or null where the picture is the bare earth
     */
    public static void claim(int origin, @Nullable SurfaceFamily appearance) {
        if (!TrmtConfig.inheritShaderMaterial) return;
        Seat seat = SEAT.get();
        boolean oculus = seat != null && seat.holder != null;
        if (!oculus && !OptiFineMaterial.seated()) return;

        IBlockState covered = null;
        if (origin >= 0) {
            try {
                covered = Block.getStateById(origin);
            } catch (RuntimeException awkwardId) {
                covered = null;
            }
        }
        SurfaceFamily coveredFamily = null;
        if (covered != null && covered.getBlock() != Blocks.AIR) {
            Block block = covered.getBlock();
            coveredFamily = SurfaceRegistry.familyOf(block, block.getMetaFromState(covered));
        }
        IBlockState claim = claimed(covered, coveredFamily, appearance);

        OptiFineMaterial.claim(claim);
        if (!oculus || !available() || seat.claimed == claim) return;
        try {
            set.invoke(seat.holder, claim, Short.valueOf(renderType.getShort(seat.holder)));
            seat.claimed = claim;
        } catch (Throwable awkward) {
            // One failure is enough: something has changed under us and asking again every face for
            // the rest of the session would be the expensive way to keep finding that out.
            set = null;
            renderType = null;
            Trmt.LOG.warn("Giving up on the shader material override: {}", awkward.toString());
        }
    }

    /**
     * The block a ghost should be taken for, by the 1.7.10 edition's rule: the block it covers while it
     * still wears as that block's own family, and the block of the family its wear has reached once it
     * does not. Earth where the picture is the bare earth.
     *
     * @param covered       the block underneath, or null when nothing was recorded
     * @param coveredFamily the family that block belongs to, or null
     * @param appearance    the family the square wears as, or null where the picture is the bare earth
     */
    static IBlockState claimed(@Nullable IBlockState covered, @Nullable SurfaceFamily coveredFamily,
        @Nullable SurfaceFamily appearance) {
        if (appearance == null) return Blocks.DIRT.getDefaultState();
        if (covered != null && coveredFamily == appearance) return covered;
        return counterpartOf(appearance).getDefaultState();
    }

    /**
     * The vanilla block a family looks like - the 1.7.10 edition's {@code GhostLogic.vanillaCounterpart},
     * which the material a road has worn into is claimed as. Earth for anything without one of its own.
     */
    static Block counterpartOf(SurfaceFamily appearance) {
        switch (appearance) {
            case GRASS:
                return Blocks.GRASS;
            case SAND:
                return Blocks.SAND;
            case GRAVEL:
                return Blocks.GRAVEL;
            case STONE:
                return Blocks.STONE;
            case COBBLE:
                return Blocks.COBBLESTONE;
            case NETHER:
                return Blocks.NETHERRACK;
            case END:
                return Blocks.END_STONE;
            case SNOW:
                return Blocks.SNOW;
            case ICE:
                return Blocks.ICE;
            default:
                return Blocks.DIRT;
        }
    }
}
