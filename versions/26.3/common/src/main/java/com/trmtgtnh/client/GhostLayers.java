package com.trmtgtnh.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
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
 *
 * <p>
 * <strong>What 26.1 changed, and how the answer is carried.</strong> A chunk's passes are no longer render types but
 * {@code ChunkSectionLayer}s - solid, cut-out and translucent; cut-out mipped and cut-out are one pass, whose mipmapping
 * each sprite now says for itself - and the pass is not the block's at all: every {@code BakedQuad} carries its own in
 * its material, and the chunk compiler puts each quad where its material says. Vanilla no longer keeps a table of
 * blocks and passes ({@code ItemBlockRenderTypes} is gone); a quad's pass is worked out from its sprite's transparency
 * when the model is baked. So {@link #of} still answers one pass for a square - the pass the covered block draws in -
 * and {@code GhostQuads} writes it into every quad of that square, which is what the per-square filtering and the per-quad
 * blend mode did on 1.21.1. "The pass the covered block draws in" is read from its baked model: the most demanding pass
 * among its quads, because on 1.21.1 a block had one pass for every face it drew and that pass had to suit the face that
 * needed most - grass's overlay made grass cut-out mipped, which is cut-out here. See render-questions.md.
 */
public final class GhostLayers {

    private GhostLayers() {}

    /**
     * The pass for a square standing in for the block this state id names.
     *
     * <p>
     * Cut-out mipped where there is nothing to ask - no remembered block, or a state id this world
     * has no block for - because that is the pass that draws both a solid picture and one with holes
     * correctly, and is what every ghost used before this class existed. From 26.1 that pass is the cut-out one, which
     * took cut-out mipped into itself.
     */
    public static ChunkSectionLayer of(int origin) {
        if (origin < 0) return ChunkSectionLayer.CUTOUT;
        // A window blends, whatever pass the block it covers draws in: the clause quoted above, {@code return clear
        // || window ? 1 : 0}, whose window half was never carried until 0.9.220. Chisel's waterstone draws solid,
        // and the solid pass writes the water in its holes opaque. See GhostWindows.
        if (com.trmtgtnh.client.model.GhostWindows.windowOf(origin)) return ChunkSectionLayer.TRANSLUCENT;
        try {
            BlockState under = Block.stateById(origin);
            ChunkSectionLayer layer = lookup.apply(under);
            return layer == null ? ChunkSectionLayer.CUTOUT : layer;
        } catch (RuntimeException awkwardBlock) {
            return ChunkSectionLayer.CUTOUT;
        }
    }

    /**
     * How the pass a block draws in is looked up: from the block's own baked model unless a loader knows better.
     *
     * <p>
     * Fabric's mods fill that table, through BlockRenderLayerMap, so it answers for them. Forge's do not - its
     * setRenderLayer keeps a predicate per block that the table never reads - so on Forge it answered solid for every
     * modded block, and a worn modded block with holes or see-through parts was drawn solid there and right on
     * Fabric: two loaders disagreeing about one pack (found 2026-10-08). The Forge module hands its own lookup in.
     *
     * <p>
     * From 26.1 there is no table: 1.21.1 read {@code ItemBlockRenderTypes.getChunkRenderType}, which is gone, and the
     * game's own answer is the pass each of the block's quads carries, read here by {@link #fromModel}. A loader that
     * keeps a pass of its own for a block can still hand its lookup in.
     */
    private static volatile java.util.function.Function<BlockState, ChunkSectionLayer> lookup = GhostLayers::fromModel;

    /**
     * Replaces how a block's own pass is looked up. 1.21.1's Forge and NeoForge client setups called it; from 26.1 none
     * does, because the model answers for every block whichever loader set it up. Kept for a loader that keeps a pass
     * of its own again.
     */
    public static void lookUpPassesWith(java.util.function.Function<BlockState, ChunkSectionLayer> how) {
        if (how != null) lookup = how;
    }

    /**
     * Whether the ghost offers itself to a pass at all, which on Forge is every pass it may need. From 26.1 these are
     * every pass a chunk has; a quad says its own, so nothing a loader asks with this is turned away - and no loader
     * asks it any more, as none is asked a block's passes; kept beside {@link #lookUpPassesWith} for the same reason.
     */
    public static boolean claims(ChunkSectionLayer layer) {
        return layer == ChunkSectionLayer.SOLID || layer == ChunkSectionLayer.CUTOUT
            || layer == ChunkSectionLayer.TRANSLUCENT;
    }

    /** What each covered state's model answered, kept until the models are baked again. */
    private static final Map<BlockState, ChunkSectionLayer> FROM_MODEL = new ConcurrentHashMap<BlockState, ChunkSectionLayer>();

    /** The model set those answers were read from; a new one (a resource reload) empties them. */
    private static volatile BlockStateModelSet readFrom;

    /**
     * The pass a block draws in, read from its baked model as 26.1 decides it: the most demanding pass among its quads
     * - translucent over cut-out over solid - with vanilla's one exception, leaves drawn solid where the player has
     * turned cut-out leaves off ({@code ModelBlockRenderer.forceOpaque}), which 1.21.1's table said as well. Null where
     * the model draws nothing, so the square takes the cut-out pass, as for a block 1.21.1's table did not know.
     */
    static ChunkSectionLayer fromModel(BlockState state) {
        if (state == null) return null;
        Minecraft game = Minecraft.getInstance();
        if (game == null || game.getModelManager() == null) return null;
        BlockStateModelSet models = game.getModelManager()
            .getBlockStateModelSet();
        if (models == null) return null;
        if (models != readFrom) {
            FROM_MODEL.clear();
            readFrom = models;
        }
        if (game.options != null && ModelBlockRenderer.forceOpaque(game.options.cutoutLeaves()
            .get()
            .booleanValue(), state)) return ChunkSectionLayer.SOLID;
        ChunkSectionLayer known = FROM_MODEL.get(state);
        if (known != null) return known;
        ChunkSectionLayer found = mostDemanding(models.get(state));
        if (found != null) FROM_MODEL.put(state, found);
        return found;
    }

    /** The most demanding pass among a model's quads, every side and the unculled ones, or null with none. */
    static ChunkSectionLayer mostDemanding(BlockStateModel model) {
        if (model == null) return null;
        List<BlockStateModelPart> parts = new ArrayList<BlockStateModelPart>();
        model.collectParts(RandomSource.create(42L), parts);
        ChunkSectionLayer most = null;
        for (BlockStateModelPart part : parts) {
            most = demanding(most, part.getQuads(null));
            for (Direction side : Direction.values()) most = demanding(most, part.getQuads(side));
        }
        return most;
    }

    private static ChunkSectionLayer demanding(ChunkSectionLayer most, List<BakedQuad> quads) {
        if (quads == null) return most;
        for (BakedQuad quad : quads) {
            ChunkSectionLayer layer = quad.materialInfo()
                .layer();
            if (most == null || rank(layer) > rank(most)) most = layer;
        }
        return most;
    }

    private static int rank(ChunkSectionLayer layer) {
        if (layer == ChunkSectionLayer.TRANSLUCENT) return 2;
        return layer == ChunkSectionLayer.CUTOUT ? 1 : 0;
    }
}
