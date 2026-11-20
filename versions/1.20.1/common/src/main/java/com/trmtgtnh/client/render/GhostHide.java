package com.trmtgtnh.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.Client;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.block.BlockGhostStair;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceShape;

/**
 * Whether a worn square hides the face of the block beside it, as the block it stands in for would (0.9.229, spec GF1).
 *
 * <p>
 * The 1.7.10 edition's unsunk, not clear, not window stand-in is an opaque normal cube ({@code isOpaqueCube} is
 * {@code !sunken && !clear && !window}), so its neighbours cull their faces against it, as they did against the
 * grass or the stone it replaced. Every ghost here says it occludes nothing ({@code noOcclusion}), and stays so:
 * it is what lets light into a sunk cell (CS40), and a state's occlusion is cached per state, where a per-square
 * answer once culled every face in the world (2026-10-06). So neighbours here draw every face behind every ghost.
 * This answers per square, and only ever from vanilla's face test, before vanilla's per-state {@code OCCLUSION_CACHE}
 * is reached and never into it: each loader's {@code MixinGhostHidesFaces} at the head of {@code Block.shouldRenderFace},
 * which vanilla's mesher and Fabric's Indigo both ask.
 *
 * <p>
 * <strong>A wrong yes is a hole in the world</strong> (the Rubidium fault of 2026-10-06 looked exactly like that), so
 * every doubt answers no: a square with no record, a sink of any depth, worn ice, a window, a stair or any shape a data
 * file gives, a covered block unknown, or one that is not a full opaque cube on the face it shares; and a settled snow
 * layer or carpet drawn down beside it, whose face is not where the cell's is.
 *
 * <p>
 * <strong>Vanilla's mesher only, on by default</strong> (Xep, 2026-11-09: "Keep, vanilla only"). Measured on 1.21.1 one
 * game at a time, off and on alternated: vanilla's mesher 2 to 5% cheaper with a tenth fewer faces and nothing missing
 * in the photographs; Sodium, Embeddium and Fabric Sodium no gain. So the hide answers only where the renderer is
 * vanilla's ({@link #forSeat}, from {@code MeshTime.seat()}): never under Sodium, Embeddium, Rubidium, Canvas or
 * OptiFine, nor Iris or Oculus riding on them. That is a gate here and not only the hook's seat, because Canvas and
 * Indium (Sodium's renderer for Fabric API models on 1.20.1) call vanilla's positional {@code Block.shouldRenderFace}
 * themselves (read from their jars, {@code tools/gf1_renderer_calls.py}). The ghost declares no
 * {@code hidesNeighborFace}, so Sodium for NeoForge and Embeddium, which ask it, are never answered yes either.
 * {@code -Dtrmt.gf1.hide=false} ({@code MeshTime.HIDE}) turns it off, read once at start, so the measure can be played
 * with the hide off and on on the same jar.
 */
public final class GhostHide {

    private GhostHide() {}

    /** The switch: on unless {@code -Dtrmt.gf1.hide=false}, read once. Off, every face test pays one field read. */
    public static final boolean ON = switchedOn(System.getProperty(com.trmtgtnh.client.MeshTime.HIDE));

    /** The renderer, once known: not yet asked, vanilla's mesher, or another that has taken the faces over. */
    private static final int UNKNOWN = 0, VANILLA = 1, ANOTHER = 2;

    private static volatile int mesher = UNKNOWN;

    /** Whether the switch's word turns the hide on: anything but {@code false}, and on when nothing is said. */
    public static boolean switchedOn(String said) {
        return said == null || !"false".equalsIgnoreCase(said.trim());
    }

    /**
     * Whether the hide answers under this renderer, as {@code MeshTime.seat()} names it: vanilla's mesher alone. A seat
     * of Sodium, Embeddium, Rubidium, Canvas or OptiFine, with or without Iris or Oculus, is no.
     */
    public static boolean forSeat(String seat) {
        return "vanilla".equals(seat);
    }

    /**
     * Whether the hide answers in this game: the switch on and vanilla's mesher drawing the faces. The renderer is
     * asked once, when the loader has said which mods are here; until then nothing is hidden.
     */
    public static boolean active() {
        if (!ON) return false;
        int known = mesher;
        if (known == UNKNOWN) {
            if (!com.trmtgtnh.ModsPresent.wired()) return false;
            known = forSeat(com.trmtgtnh.client.MeshTime.seat()) ? VANILLA : ANOTHER;
            mesher = known;
        }
        return known == VANILLA;
    }

    /** Forgets which renderer was found. For tests, which hand {@code ModsPresent} answers of their own. */
    static void forget() {
        mesher = UNKNOWN;
    }

    /**
     * The decision itself, from the square's facts: the 1.7.10 edition's {@code !sunken && !clear && !window} on a
     * stand-in that is an opaque normal cube, with the switch in front.
     *
     * @param on              the hide answers here: its switch on and vanilla's mesher drawing
     * @param recorded        the square has a record that says it is worn
     * @param sunk            the record carries a sink of any depth
     * @param clear           the square is worn as ice, which is seen through
     * @param window          the covered block is a window, drawn through its own holes
     * @param shaped          the square is a stair, or a data file gives the covered block a shape of its own
     * @param coveredFaceFull the covered block is a full opaque cube, and full on the face the neighbour touches
     */
    public static boolean decide(boolean on, boolean recorded, boolean sunk, boolean clear, boolean window,
        boolean shaped, boolean coveredFaceFull) {
        return on && recorded && !sunk && !clear && !window && !shaped && coveredFaceFull;
    }

    /**
     * What vanilla's face test asks: whether the face of {@code owner} at {@code pos} toward {@code face} is hidden by a
     * ghost standing at {@code neighbour}. Vanilla's and Indigo's test, which are handed the neighbour's position.
     */
    public static boolean hidden(BlockState owner, BlockGetter level, BlockPos pos, Direction face, BlockPos neighbour) {
        // The cheap exit first: this runs for every face of every block in a chunk.
        if (!active() || level == null || pos == null || face == null || neighbour == null) return false;
        BlockState ghost = level.getBlockState(neighbour);
        if (!(ghost.getBlock() instanceof BlockGhost)) return false;
        return hides(level, neighbour, ghost, face.getOpposite(), owner, pos);
    }

    /**
     * Whether the ghost at {@code at} hides the face of {@code owner}, the block on its side {@code toward}: the
     * square's facts read from the client's records and handed to {@link #decide}.
     */
    static boolean hides(BlockGetter level, BlockPos at, BlockState ghost, Direction toward, BlockState owner,
        BlockPos ownerAt) {
        boolean on = active();
        if (!on || level == null || at == null || ghost == null || toward == null) return false;
        // A worn stair is a block of its own that occludes by its shape, which vanilla already reads (spec GF8).
        if (ghost.getBlock() instanceof BlockGhostStair) return false;
        // Snow or a carpet drawn down onto worn ground: its face is not where its cell's is (Settling).
        if (owner != null && ownerAt != null && Settling.settles(owner.getBlock()) && Settling.dropFor(level, ownerAt) > 0.0D) {
            return false;
        }
        short record = Client.ghostRecordAt(level, at.getX(), at.getY(), at.getZ());
        boolean recorded = BlockGhost.shows(record);
        boolean sunk = ErosionState.sinkOf(record) > 0;
        // Read no further for a square the record alone rules out, which is most of a worn road.
        if (!recorded || sunk) return false;
        boolean clear = ErosionState.familyOf(record) == SurfaceFamily.ICE;
        int origin = Client.ghostOriginAt(at.getX(), at.getY(), at.getZ());
        BlockState covered = coveredOf(origin);
        boolean window = com.trmtgtnh.client.model.GhostWindows.windowOf(origin);
        boolean shaped = covered != null && SurfaceShape.of(covered)
            .isPartial();
        return decide(on, recorded, sunk, clear, window, shaped, fullOn(covered, level, at, toward));
    }

    /**
     * Whether the covered block is a full opaque cube, and full on this face: vanilla's own reading of a block that
     * hides what touches it. Unknown, a ghost, or a block that cannot answer here, is no.
     */
    static boolean fullOn(BlockState covered, BlockGetter level, BlockPos at, Direction toward) {
        if (covered == null || covered.getBlock() instanceof BlockGhost) return false;
        try {
            return covered.canOcclude() && covered.isSolidRender(level, at)
                && Block.isFaceFull(covered.getFaceOcclusionShape(level, at, toward), toward);
        } catch (RuntimeException awkwardBlock) {
            return false;
        }
    }

    private static BlockState coveredOf(int origin) {
        if (origin < 0) return null;
        try {
            return Block.stateById(origin);
        } catch (RuntimeException awkwardBlock) {
            return null;
        }
    }
}
