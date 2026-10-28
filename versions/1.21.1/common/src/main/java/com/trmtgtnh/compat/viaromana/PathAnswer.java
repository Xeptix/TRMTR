package com.trmtgtnh.compat.viaromana;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.Client;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.ModBlocks;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.erosion.ErosionEntry;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.erosion.ErosionStore;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * Whether a square is road to Via Romana when its own answer was no: the first part of the support (0.9.228).
 *
 * <p>
 * Via Romana asks {@code util.PathUtils.isBlockValidPath(LevelAccessor, BlockPos)} of every block around a player who is
 * charting - from the client, in {@code client.core.ChartingHandler.chartPath} through
 * {@code PathUtils.calculateInfrastructureQuality}, and again to draw the blocks it refuses - and it answers from its
 * block tag alone. Two things this mod does make that answer wrong, and {@code MixinViaRomanaPathBlock} hands any no
 * here to be asked again:
 *
 * <ul>
 * <li>A worn square is a ghost in the client's copy of the world, so a worn square of a block Via Romana counts as road -
 * a cobbled road, a dirt path - stopped counting the moment it wore. A ghost answers here as the block it covers.
 * <li>A desire path is grass to Via Romana however worn it is. A square worn to integration.viaRomanaRoadGradation
 * counts ({@link RoadRules#counts}), so a path walked into the world can be charted and linked like a built one.
 * </ul>
 *
 * <p>
 * Only ever a yes added to a no, never the reverse, and nothing at all without Via Romana. The client asks its own
 * record of the square, the one the ghost is drawn from; a server, which Via Romana 2.2.3 never asks, would answer
 * from the store.
 */
public final class PathAnswer {

    /** Whether the log has been told a worn square was counted, which it is told once a session. */
    private static volatile boolean said;

    private PathAnswer() {}

    /** Whether Via Romana should count this square as road, where its own tag said it should not. */
    /**
     * The server's road gradation as its rules packet said it, or -1 where none came (0.9.228; Xep: "Send the server's
     * value"). Via Romana charts roads on the client and the server never checks, so the server's own setting decides
     * here, where it sent one, and the client's otherwise - singleplayer included, whose server is the client's own.
     */
    public static volatile int serverRoadGradation = -1;

    /** The road gradation in force: the server's, where it sent one, else this game's own setting. */
    static int roadGradation() {
        int server = serverRoadGradation;
        return server >= 0 ? server : TrmtConfig.viaRomanaRoadGradation;
    }

    public static boolean isRoad(LevelAccessor level, BlockPos pos) {
        if (level == null || pos == null || !ViaRomana.present()) return false;
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        BlockState here = level.getBlockState(pos);
        BlockState covered = null;
        short record;
        if (level.isClientSide()) {
            if (ModBlocks.isGhost(here.getBlock())) {
                int origin = Client.ghostOriginAt(x, y, z);
                covered = origin < 0 ? null : Block.stateById(origin);
            }
            record = Client.ghostRecordAt(level, x, y, z);
        } else if (level instanceof Level) {
            ErosionEntry entry = ErosionStore.get()
                .getEntry((Level) level, x, y, z);
            record = entry == null || !entry.isVisible() ? ErosionState.NONE : entry.packState();
        } else {
            return false;
        }
        if (covered != null && ViaRomana.isPathBlock(covered)) return true;
        SurfaceFamily base = SurfaceRegistry.familyOf(covered != null ? covered : here);
        if (base == null || !base.staged) return false;
        boolean road = RoadRules.counts(
            ErosionChain.forBase(base),
            ErosionChain.cappedLength(base),
            roadGradation(),
            record);
        if (road && !said) {
            said = true;
            Trmt.LOG.info(
                "Via Romana asked whether the worn {} at {} is road, and it is: worn to gradation {} or past it "
                    + "(integration.viaRomanaRoadGradation {}, the server's where it sent one). Said once a session.",
                base.key(),
                pos,
                Integer.valueOf(
                    RoadRules.roadGradation(
                        ErosionChain.forBase(base),
                        ErosionChain.cappedLength(base),
                        roadGradation())),
                Integer.valueOf(roadGradation()));
        }
        return road;
    }
}
