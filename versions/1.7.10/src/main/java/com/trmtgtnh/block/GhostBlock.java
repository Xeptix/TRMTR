package com.trmtgtnh.block;

import net.minecraft.block.Block;
import net.minecraft.util.IIcon;

import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceShape;

/**
 * What every ghost variant can answer, whatever it happens to extend.
 *
 * <p>
 * There are two ghost classes rather than one, and the split is forced. Only a block that
 * genuinely {@code extends BlockGrass} can be handed back from MixinGrassTint, whose target
 * expression is typed to that class — so the grass variants have to be one. But being one is
 * a claim other mods read: JourneyMap keys its biome-tint flag off
 * {@code BlockGrass.class.isAssignableFrom(...)}, which is why worn sand, stone and cobble
 * drew as flat grey on the map for as long as every ghost inherited from grass. Java allows
 * one superclass, so the families that are not grass must not have that one.
 *
 * <p>
 * Everything the two share lives in {@link GhostLogic} as statics taking the block back as an
 * argument. Neither class carries behaviour of its own beyond forwarding.
 *
 * <p>
 * No method here is {@code @SideOnly}. {@link IIcon} exists on both sides in 1.7.10, and a
 * side-stripped interface method is an {@code AbstractMethodError} waiting for a caller.
 */
public interface GhostBlock {

    /** What this block is standing in for. */
    SurfaceFamily appearance();

    /** True when this variant renders untinted, with the earth beneath showing on its sides. */
    boolean isUntinted();

    /** True when this variant renders hollowed out. */
    boolean isSunken();

    /** True when this stand-in leaves the holes in its picture open. */
    boolean isWindow();

    /**
     * True when this variant reports vanilla's {@code grass_top} as its own top texture, which
     * is what makes the renderer give it grass's side treatment.
     */
    boolean mimicsVanillaGrassTop();

    /** The stand-in texture used where a position has no overlay data, or null before stitch. */
    /**
     * What shape this stand-in occupies its block in.
     *
     * <p>
     * The shape of the ghost rather than of the block being covered, because what it decides is
     * how this is drawn and what may be read out of its metadata. A slab is covered by an ordinary
     * ghost whose bounds come from the record, so only a stair answers with anything else.
     */
    SurfaceShape shape();

    IIcon fallbackIcon();

    /** This, as a block. Saves every caller a cast it cannot express through the interface. */
    Block asBlock();
}
