package com.trmtgtnh.client.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.trmtgtnh.erosion.PhysicalDecay;

/**
 * The picture of snow and carpet settled onto worn ground: the block's own quads, moved down by the drop of the rut
 * under it, handed out by each loader's level-aware model question (0.9.228; Xep: "Fix both now").
 *
 * <p>
 * <strong>Why the model and not the offset.</strong> From 1.21.2 a block state's offset is asked with the position
 * alone, so the state has no view to read the rut from. Until 0.9.228 Forge and NeoForge lowered that offset inside
 * {@code ModelBlockRenderer.tesselateBlock}, which only the game's own chunk compiler calls - so a renderer meshing
 * chunks itself drew the snow where it stood - and Fabric lowered {@code getOffset} itself, reading the game's own
 * level on the mesher threads, against {@link Settling}'s rule. Every renderer that honours a loader's model API asks
 * the model with the view it is meshing: NeoForge's {@code collectParts(BlockAndTintGetter, BlockPos, BlockState,
 * RandomSource, List)}, Forge's {@code getModelData(BlockAndTintGetter, BlockPos, BlockState, ModelData)} before its
 * {@code collectParts(RandomSource, List, ModelData)}, Fabric's {@code emitQuads(QuadEmitter, BlockAndTintGetter,
 * BlockPos, BlockState, RandomSource, Predicate)} - the same questions this mod's ghost model already answers on each
 * loader. So each loader wraps the baked model of every settling block once, at the end of the bake, and answers that
 * question lowered by {@link #dropAt}; the position-free questions answer the model as it was baked. The 1.7.10
 * edition moves the renderer's bounds for the block instead, which a baked model has none of.
 *
 * <p>
 * <strong>Which models are wrapped: every block that rests on the ground</strong> ({@code PhysicalDecay
 * .restsOnTheGround}), asked at the bake whatever the switch says, because {@code markSettlingBlocks} can change with a
 * server's rules while the models stay baked; the drop itself asks the live switch, through {@link Settling#offset}.
 *
 * <p>
 * The hit shape, the outline, the collision and the face rule keep their own hooks ({@link Settling#hitShape},
 * {@link Settling#outline}, {@link Settling#keepsFace}); only the picture's seat moved here.
 */
public final class SettledModel {

    private SettledModel() {}

    /** Whether this state's baked model is wrapped: a block that rests on the ground, whatever the switches say now. */
    public static boolean wraps(BlockState state) {
        if (state == null) return false;
        Block block = state.getBlock();
        Boolean known = WRAPS.get(block);
        if (known == null) {
            known = Boolean.valueOf(PhysicalDecay.restsOnTheGround(block));
            WRAPS.put(block, known);
        }
        return known.booleanValue();
    }

    private static final Map<Block, Boolean> WRAPS = new ConcurrentHashMap<Block, Boolean>();

    /**
     * How far a settling block's picture comes down at this square of the view the renderer was handed, in blocks, or
     * 0 - Settling's own answer, which asks whether the block settles now and says once that a block was drawn down.
     */
    public static double dropAt(BlockState state, BlockGetter view, BlockPos pos) {
        Vec3 settled = Settling.offset(state, view, pos, null);
        return settled == null ? 0.0D : -settled.y;
    }

    /**
     * The same question asked quietly, for a geometry key: the same figure, without the line said once or the count of
     * faces wanted, which the drawing itself keeps.
     */
    public static double quietDropAt(BlockState state, BlockGetter view, BlockPos pos) {
        if (state == null || !Settling.settles(state.getBlock())) return 0.0D;
        return Settling.dropFor(view, pos);
    }

    /** The quads a settling block draws at this square of the view: its own, lowered by the drop under it. */
    public static List<BakedQuad> quadsAt(BlockState state, BlockGetter view, BlockPos pos, List<BakedQuad> quads) {
        return lowered(quads, dropAt(state, view, pos));
    }

    /**
     * These quads moved down by a drop, kept per quad set and drop: the drops come in sixteenths of a block, and every
     * rebuild of a section asks every square again. The quads handed in where there is no drop.
     */
    @SuppressWarnings("unchecked")
    public static List<BakedQuad> lowered(List<BakedQuad> quads, double drop) {
        if (quads == null || quads.isEmpty() || drop <= 0.0D) return quads;
        Key key = new Key(quads, drop);
        Object kept = CACHE.get(key);
        if (kept != null) return (List<BakedQuad>) kept;
        List<BakedQuad> moved = new ArrayList<BakedQuad>(quads.size());
        for (BakedQuad quad : quads) moved.add(mover.move(quad, (float) drop));
        List<BakedQuad> made = Collections.unmodifiableList(moved);
        keep(key, made);
        return made;
    }

    /** One part of a settling block's model moved down by a drop, kept per part and drop; the part where there is none. */
    public static BlockStateModelPart part(BlockStateModelPart part, double drop, PartMaker make) {
        if (part == null || drop <= 0.0D) return part;
        Key key = new Key(part, drop);
        Object kept = CACHE.get(key);
        if (kept != null) return (BlockStateModelPart) kept;
        BlockStateModelPart made = make.make(part, drop);
        keep(key, made);
        return made;
    }

    /** Every part, moved down by the drop, into the list a renderer handed the model. */
    public static void lowerParts(List<BlockStateModelPart> parts, double drop, List<BlockStateModelPart> into,
        PartMaker make) {
        for (BlockStateModelPart each : parts) into.add(part(each, drop, make));
    }

    /**
     * The geometry key a wrapped model answers: the plain one where nothing moved, none where the plain model keeps
     * none, and otherwise the plain one with the drop beside it - so a renderer that keeps geometry by key never draws
     * one square's settled snow at another's height.
     */
    public static Object geometryKey(Object plain, double drop) {
        if (plain == null || drop <= 0.0D) return plain;
        return new DropKey(plain, drop);
    }

    /** Forgets every lowered quad set, as a new bake makes new models. */
    public static void forget() {
        CACHE.clear();
        WRAPS.clear();
    }

    private static void keep(Key key, Object made) {
        // A bound and no more: a pack whose settling models answer a new list each time is not kept for ever.
        if (CACHE.size() > 4096) CACHE.clear();
        CACHE.put(key, made);
    }

    private static final Map<Key, Object> CACHE = new ConcurrentHashMap<Key, Object>();

    /** A quad set or part by identity, beside the drop it was moved by. */
    private static final class Key {

        final Object of;

        final long drop;

        Key(Object of, double drop) {
            this.of = of;
            this.drop = Double.doubleToLongBits(drop);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return key.of == of && key.drop == drop;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(of) * 31 + Long.hashCode(drop);
        }
    }

    /** A plain model's geometry key with the drop its square was drawn down by. */
    private record DropKey(Object plain, double drop) {}

    // ---- moving a quad ----

    /** Moves one quad down: the game's own record, every corner lower and nothing else changed. */
    public interface Mover {

        BakedQuad move(BakedQuad quad, float drop);
    }

    /**
     * The game's own record made again, its corners lower - which on NeoForge loses the normals and colors it adds to
     * a quad, so NeoForge's module hands its own mover in ({@link #useMover}).
     */
    public static BakedQuad moveVanilla(BakedQuad quad, float drop) {
        return new BakedQuad(down(quad.position0(), drop), down(quad.position1(), drop), down(quad.position2(), drop),
            down(quad.position3(), drop), quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
            quad.direction(), quad.materialInfo());
    }

    /** A corner moved down by the drop. */
    public static Vector3f down(Vector3fc corner, float drop) {
        return new Vector3f(corner.x(), corner.y() - drop, corner.z());
    }

    private static volatile Mover mover = SettledModel::moveVanilla;

    /** The loader's own way to move a quad, where its quads carry more than the game's record. */
    public static void useMover(Mover loaders) {
        if (loaders != null) mover = loaders;
    }

    // ---- moving a part ----

    /** Makes a lowered part - common's {@link LoweredPart}, or a loader's own that also answers its extensions. */
    public interface PartMaker {

        BlockStateModelPart make(BlockStateModelPart part, double drop);
    }

    /** One part of a model, its quads moved down by a drop, everything else the part's own. */
    public static class LoweredPart implements BlockStateModelPart {

        protected final BlockStateModelPart part;

        private final double drop;

        @SuppressWarnings("unchecked")
        private final List<BakedQuad>[] sides = new List[7];

        public LoweredPart(BlockStateModelPart part, double drop) {
            this.part = part;
            this.drop = drop;
        }

        @Override
        public List<BakedQuad> getQuads(Direction side) {
            int at = side == null ? 6 : side.ordinal();
            List<BakedQuad> kept = sides[at];
            if (kept == null) {
                kept = lowered(part.getQuads(side), drop);
                sides[at] = kept;
            }
            return kept;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return part.useAmbientOcclusion();
        }

        @Override
        public Material.Baked particleMaterial() {
            return part.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return part.materialFlags();
        }
    }
}
