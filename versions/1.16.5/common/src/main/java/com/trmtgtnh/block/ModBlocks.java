package com.trmtgtnh.block;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import com.trmtgtnh.Trmt;

/**
 * Where the blocks are made, and the seam that lets two loaders register them.
 *
 * <p>
 * One ghost, and that is not a milestone's worth of shortcut: the 1.12.2 edition registers one too.
 * 1.7.10 needs sixty-six because appearance there is a metadata value and an icon per side, so every
 * shape and every rendering path the covered block might need is its own block class. From 1.12.2 on
 * appearance is a blockstate and a model handed the position, so one class carries every family and
 * every gradation and works the rest out.
 *
 * <p>
 * <strong>Which blocks get registered must never depend on a setting.</strong> That rule is carried
 * from both older editions and it is the kind that is learned once: registry names go into a save's
 * own record of what exists, so a set that varies with the settings is a save that will not open
 * after somebody changes one. Nothing here reads config, and nothing here ever should.
 *
 * <p>
 * Registration itself is the first thing in this port that the two loaders genuinely cannot share.
 * Forge wants a deferred register and an event; Fabric wants a direct call into a registry at the
 * right moment. Neither can be named from this module, so this says <em>what</em> to register and the
 * loader modules say <em>how</em> - the same shape as the other two seams, and for the same reason.
 */
public final class ModBlocks {

    /**
     * What a loader module supplies: somewhere to put a block under a name.
     *
     * <p>
     * One method, where {@code ModItems.Registrar} has four. This had a {@code makeGhost} factory
     * beside it for a while, on the expectation that Forge would need a subclass of its own: a model
     * there is handed {@code IModelData} rather than a position, and the method that fills it is
     * Forge's own and cannot be named from this module. In the end nothing supplies model data for a
     * block with no block entity, so that route was abandoned for the one {@code GhostSeat} and
     * {@code MixinBlockRenderDispatcher} take - which asks the block for nothing at all. No loader
     * ever overrode the factory, so it is gone rather than left to be carried forward by the next
     * port as a fork that was never needed.
     */
    public interface Registrar {

        void block(ResourceLocation name, Block block);
    }

    private static BlockGhost ghost;

    private ModBlocks() {}

    /** The one ghost. Null until a loader has registered it, which is during that loader's startup. */
    public static BlockGhost ghost() {
        return ghost;
    }

    /**
     * Whether this is one of this mod's cosmetic blocks.
     *
     * <p>
     * Asked by the demonstration, which lays out a platform per surface and must not lay out one for
     * a ghost: a ghost is what a worn surface is drawn <em>as</em>, so a platform of them would be a
     * platform of the answer rather than of the question. Asked by class rather than by identity
     * because what the caller means is "is this ours", and a second ghost would still be ours.
     */
    public static boolean isGhost(Block block) {
        return block instanceof BlockGhost;
    }

    /**
     * Makes the blocks and hands them over. Called once by each loader as the mod starts.
     *
     * <p>
     * The instance is made here rather than in the loader modules so that both loaders get the same
     * block built the same way. What differs between them is only where it is put.
     */
    public static void register(Registrar into) {
        // Made once however often this is called. Nothing calls it twice today, and the guard stays
        // because Forge fires a separate event per registry and this module hands everything over in
        // one call - so a second visit is a change away rather than a fault. Two ghosts would put one
        // instance in the registry and hand the painter the other, which is the sort of fault that
        // looks like a texture problem for a day.
        if (ghost == null) ghost = new BlockGhost();
        into.block(new ResourceLocation(Trmt.MODID, "ghost"), ghost);

        // And no item form, which all three editions agree on: there is no way to obtain or place a
        // worn square, because a worn square is what the mod paints rather than something anybody
        // owns. There was a BlockItem here, in the Building Blocks tab, for as long as the rendering
        // spike needed something placeable to look at - the store, the painter and /trmt can all wear
        // ground without one, and had all landed, so what it left behind was a block in the creative
        // menu that neither shipped edition has and that shows its own name key.
    }
}
