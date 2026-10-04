package com.trmtgtnh.client.texture;

import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Slides a side texture down as the ground it belongs to sinks.
 *
 * <p>
 * A sunken ghost is drawn shorter than a full block, and the standard renderer nails a side
 * face's texture to the bottom of the cell: the window it samples is {@code [16 - 16*top, 16]}
 * in sprite rows, so shortening a block from the top throws away the rows at the top of the
 * sprite. For a surface whose sides are one uniform texture that is right — a rut cut into
 * sand should show sand all the way down the wall. For a made surface it is wrong. A dirt
 * path's side is soil with a pale cap along its top edge, and cropping from the top eats the
 * cap, so a worn path ends up looking like plain dirt from the side.
 *
 * <p>
 * The fix is to shift the sampled window down by however far erosion has taken the block, so
 * the cap rides the new surface. The shift is
 * {@code 16 * (originTop - ghostTop)} — the distance the ground has actually dropped, not the
 * whole crop. That distinction matters: the window's top edge then works out to
 * {@code 16 - 16*originTop}, which does not depend on the sink depth at all. A path stands
 * 15/16 high, so its window starts at row 1 and stays there however deep the rut gets, and row
 * 0 — which in {@code dirt_path_side} is fully transparent — is never sampled. Shifting by the
 * full crop instead would pull that row into view and cut a one-pixel slot along the top of
 * every path wall.
 *
 * <p>
 * Only {@code getInterpolatedV} moves. Leaving {@code getMinV} and {@code getMaxV} alone is
 * deliberate: the renderer's fallback path for out-of-range bounds uses those, and it should
 * still get the whole unshifted sprite.
 */
@SideOnly(Side.CLIENT)
public final class SideShift {

    /** Wrappers are immutable and cheap; this keeps the mesher from re-allocating per face. */
    private static final ConcurrentHashMap<Integer, Shifted> CACHE = new ConcurrentHashMap<Integer, Shifted>();

    private SideShift() {}

    /**
     * How far down to slide the side texture, in sprite rows, or zero for no shift.
     *
     * @param originTop how tall the covered block stands, 0 to 1
     * @param ghostTop  how tall this ghost is being drawn, 0 to 1 — read from the same bounds
     *                  the renderer just copied, so the shift and the geometry cannot disagree
     */
    public static int rows(double originTop, double ghostTop) {
        if (!(originTop > 0.0D) || originTop > 1.0D) return 0;
        if (!(ghostTop > 0.0D) || ghostTop > 1.0D) return 0;
        long shift = Math.round(16.0D * (originTop - ghostTop));
        if (shift <= 0L) return 0;
        return (int) Math.min(shift, 15L);
    }

    /**
     * A view of {@code delegate} sampled {@code rows} further down, or {@code delegate} itself
     * when there is nothing to shift.
     */
    public static IIcon wrap(int packedOrigin, int side, int rows, IIcon delegate) {
        if (rows <= 0 || delegate == null) return delegate;
        Integer key = Integer.valueOf(((packedOrigin * 6 + side) * 16) + rows);
        Shifted cached = CACHE.get(key);
        // The delegate changes identity on a resource reload, which is the one thing that
        // invalidates a wrapper. Checking it is cheaper than hooking the reload.
        if (cached != null && cached.delegate == delegate) return cached;
        Shifted made = new Shifted(delegate, rows);
        CACHE.put(key, made);
        return made;
    }

    /** Drops every wrapper. Safe at any time; the next face rebuilds what it needs. */
    public static void clear() {
        CACHE.clear();
    }

    /**
     * Both fields are final, which is load-bearing rather than tidy: these are published
     * through a map and read by several chunk-meshing threads, and without the freeze a worker
     * could legally observe a half-built wrapper with no delegate.
     */
    private static final class Shifted implements IIcon {

        private final IIcon delegate;

        private final int rows;

        Shifted(IIcon delegate, int rows) {
            this.delegate = delegate;
            this.rows = rows;
        }

        @Override
        public int getIconWidth() {
            return delegate.getIconWidth();
        }

        @Override
        public int getIconHeight() {
            return delegate.getIconHeight();
        }

        @Override
        public float getMinU() {
            return delegate.getMinU();
        }

        @Override
        public float getMaxU() {
            return delegate.getMaxU();
        }

        @Override
        public float getInterpolatedU(double u) {
            return delegate.getInterpolatedU(u);
        }

        @Override
        public float getMinV() {
            return delegate.getMinV();
        }

        @Override
        public float getMaxV() {
            return delegate.getMaxV();
        }

        @Override
        public float getInterpolatedV(double v) {
            return delegate.getInterpolatedV(v - rows);
        }

        @Override
        public String getIconName() {
            return delegate.getIconName();
        }
    }
}
