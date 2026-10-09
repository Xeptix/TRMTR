package com.trmtgtnh.client;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which pass a ghost draws in, which is the pass the block it stands in for draws in.
 *
 * <p>
 * <strong>One ghost standing in for everything has to answer this per square, and the game asks it
 * per block.</strong> The 1.7.10 edition has no such problem: it registers a ghost block per family
 * and variant - a clear one, a window one, a sunken one - and each simply answers for itself.
 * {@code BlockGhost} there says {@code return clear || window ? 1 : 0} and that is the whole of it.
 *
 * <p>
 * Here there is one ghost, so the pass is declared once and the quads are filtered per square. Both
 * loaders use this class to decide what a square's pass is; they differ only in how they act on the
 * answer, because the two renderers disagree about where a pass may be chosen:
 *
 * <ul>
 * <li><em>Forge</em> takes a predicate of passes for the block, so the ghost claims all four, and the
 * model hands back nothing in the three that are not this square's. {@code MinecraftForgeClient}
 * says which pass is being built.</li>
 * <li><em>Fabric</em> binds a block to one pass and has no such question to ask - but its rendering
 * API lets a <em>quad</em> carry its own blend mode, which is a better fit than the question Forge
 * answers. The model sets it per quad and the block's registered pass is only a fallback.</li>
 * </ul>
 *
 * <p>
 * Without this every ghost drew in the cut-out pass, and worn ice drew over what was behind it
 * rather than through it.
 */
public final class GhostLayers {

    private GhostLayers() {}

    /**
     * The pass for a square standing in for the block this state id names.
     *
     * <p>
     * Cut-out mipped where there is nothing to ask - no remembered block, or a state id this world
     * has no block for - because that is the pass that draws both a solid picture and one with holes
     * correctly, and is what every ghost used before this class existed.
     */
    public static RenderType of(int origin) {
        if (origin < 0) return RenderType.cutoutMipped();
        // A window blends, whatever pass the block it covers draws in: the clause quoted above, {@code return clear
        // || window ? 1 : 0}, whose window half was never carried until 0.9.220. Chisel's waterstone draws solid,
        // and the solid pass writes the water in its holes opaque. See GhostWindows.
        if (com.trmtgtnh.client.model.GhostWindows.windowOf(origin)) return RenderType.translucent();
        try {
            BlockState under = Block.stateById(origin);
            RenderType layer = lookup.apply(under);
            return layer == null ? RenderType.cutoutMipped() : layer;
        } catch (RuntimeException awkwardBlock) {
            return RenderType.cutoutMipped();
        }
    }

    /**
     * How the pass a block draws in is looked up: vanilla's chunk table unless a loader knows better.
     *
     * <p>
     * Fabric's mods fill that table, through BlockRenderLayerMap, so it answers for them. Forge's do not - its
     * setRenderLayer keeps a predicate per block that the table never reads - so on Forge it answered solid for every
     * modded block, and a worn modded block with holes or see-through parts was drawn solid there and right on
     * Fabric: two loaders disagreeing about one pack (found 2026-10-08). The Forge module hands its own lookup in.
     */
    private static volatile java.util.function.Function<BlockState, RenderType> lookup = ItemBlockRenderTypes::getChunkRenderType;

    /** Replaces how a block's own pass is looked up; the Forge module's client setup calls it. */
    public static void lookUpPassesWith(java.util.function.Function<BlockState, RenderType> how) {
        if (how != null) lookup = how;
    }

    /** Whether the ghost offers itself to a pass at all, which on Forge is every pass it may need. */
    public static boolean claims(RenderType layer) {
        return layer == RenderType.solid() || layer == RenderType.cutoutMipped()
            || layer == RenderType.cutout()
            || layer == RenderType.translucent();
    }
}
