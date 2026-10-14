package com.trmtgtnh.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;

/**
 * Where the square being drawn is kept, for the one loader that is not told.
 *
 * <p>
 * <strong>This whole class is the cost of Forge's half of the rendering fork.</strong> Fabric hands
 * a model the {@code BlockPos} it is drawing and nothing like this is needed there. Forge at this
 * version hands a model {@code IModelData}, and nothing supplies model data for a block that is not
 * a block entity - a tile entity per worn square is not an option, there are thousands of them on a
 * road. So the position is taken from the renderer on its way past, by a mixin, and left here for
 * the model to pick up a moment later.
 *
 * <p>
 * Per thread, because chunks are meshed on several at once and each is drawing a different square.
 * Set and cleared around one call, so nothing is held between blocks - a stale seat would draw one
 * square's wear onto another, which is the kind of fault that looks like a caching bug for a week.
 */
public final class GhostSeat {

    private static final ThreadLocal<Seat> SEATED = new ThreadLocal<Seat>();

    private static final class Seat {

        BlockAndTintGetter level;

        BlockPos pos;
    }

    private GhostSeat() {}

    /** Takes the seat, from the mixin, as the renderer enters a block. */
    public static void sit(BlockAndTintGetter level, BlockPos pos) {
        Seat seat = SEATED.get();
        if (seat == null) {
            seat = new Seat();
            SEATED.set(seat);
        }
        seat.level = level;
        // Copied, because the renderer walks a chunk with one mutable position and would otherwise
        // leave this pointing at wherever it has got to by the time the model reads it.
        seat.pos = pos == null ? null : pos.immutable();
    }

    /** Leaves it again, as that block is done with. */
    public static void stand() {
        Seat seat = SEATED.get();
        if (seat != null) {
            seat.level = null;
            seat.pos = null;
        }
    }

    /** The level being drawn into, or null when nothing is seated. */
    public static BlockAndTintGetter level() {
        Seat seat = SEATED.get();
        return seat == null ? null : seat.level;
    }

    /** The square being drawn, or null when nothing is seated. */
    public static BlockPos pos() {
        Seat seat = SEATED.get();
        return seat == null ? null : seat.pos;
    }
}
