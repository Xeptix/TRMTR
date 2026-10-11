package com.trmtgtnh.compat;

import java.util.List;


import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.client.ClientErosionCache;
import com.trmtgtnh.client.InspectionCache;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionKey;
import com.trmtgtnh.erosion.ErosionState;


/**
 * Shows what this mod knows about a block in Waila's tooltip: how worn it is and how long it has left to
 * recover, and - on any block at all, worn or not - its reinforcement and spawn ward.
 *
 * <p>
 * Waila already reports the block itself correctly - a ghost hands back the block it covers, so
 * the tooltip says "Grass Block" and offers the right tool. What it cannot know is the part that
 * only exists as data: which stage the wear has reached and when it will start growing back.
 *
 * <p>
 * An earlier version asked for that through Waila's server-NBT channel, which produced an empty
 * tooltip: Waila only fetches NBT for blocks that have a tile entity, and a ghost deliberately
 * has none. So nothing here goes through Waila at all. The surface and the stage are read
 * straight off the client's own overlay - the client is already drawing them, so it necessarily
 * knows them - and what exists only in the server's record, the progress, the idle time, the recovery,
 * the reinforcement and the ward, arrives through {@link InspectionCache}, which asks about the one block
 * under the crosshair.
 *
 * <p>
 * Every reference to Waila is confined to this class, and it is only ever loaded in response to
 * Waila answering the registration message below, so a pack without Waila never touches it.
 */
/**
 * The lines this mod writes into a block tooltip, as text and nothing else.
 *
 * <p>
 * <strong>No tooltip type is named here, deliberately.</strong> The mod that draws the tooltip is
 * Jade on one loader and WTHIT on the other; they share a package and almost an interface, and each
 * loader module has only its own on the compile path. So this holds the whole of what is said and
 * each loader holds the small class that says it - which also means these lines can be read by
 * something that has neither mod, and are.
 *
 * <p>
 * The toggle is the caller's. Both tooltip mods write their own config entry for it and hand the
 * answer in; this is asked only once the answer is yes.
 *
 * <p>
 * The lines themselves are worded in {@link TooltipLines}, which words them for The One Probe as well; this
 * hands it what the client holds.
 */
public final class WailaCompat {

    private WailaCompat() {}

    /**
     * Every line for one square, appended in the order they are said.
     *
     * @param everyBlock whether this is the provider named against every block rather than the one
     *                   named against a ghost, which is what decides whether it speaks at all
     * @param ghostHere  whether what is being looked at is one of this mod's own worn squares
     */
    public static void ground(List<String> into, boolean everyBlock, boolean ghostHere, int x, int y, int z) {
        if (!com.trmtgtnh.util.InspectionReach.speaks(everyBlock, ghostHere)) return;

        // The client's own copies: the overlay it is drawing from, the light it is drawing, and the server's
        // reply to the crosshair's inspection of exactly this position. The lines themselves are TooltipLines',
        // which words them for The One Probe too.
        TooltipLines.Reply reply = InspectionCache.has(x, y, z) ? new TooltipLines.Reply(
            InspectionCache.reinforce(),
            InspectionCache.ward(),
            InspectionCache.chainLength(),
            InspectionCache.chainIndex(),
            InspectionCache.tellsProgress(),
            InspectionCache.progress(),
            InspectionCache.untouchedSeconds(),
            InspectionCache.recoverySeconds()) : null;
        int glow = ghostHere && TrmtConfig.lightEnabled ? com.trmtgtnh.client.ClientLightCache.get()
            .at(x, y, z) : 0;
        into.addAll(
            TooltipLines.block(
                ghostHere,
                ghostHere ? stateFromOverlay(x, y, z) : ErosionState.NONE,
                glow,
                reply,
                com.trmtgtnh.util.Translate::get));
    }

    /** The wear recorded at a position, or {@link ErosionState#NONE} when there is none. */
    private static short stateFromOverlay(int x, int y, int z) {
        ClientErosionCache.ChunkOverlay overlay = ClientErosionCache.get()
            .overlay(x >> 4, z >> 4);
        if (overlay == null) return ErosionState.NONE;
        return overlay.stateAt(ErosionKey.packWorld(x, y, z));
    }
}
