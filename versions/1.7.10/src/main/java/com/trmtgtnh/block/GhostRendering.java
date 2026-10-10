package com.trmtgtnh.block;

import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.client.ClientErosionCache;
import com.trmtgtnh.client.texture.SideShift;
import com.trmtgtnh.client.texture.WearTextures;
import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.erosion.Rotations;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;
import com.trmtgtnh.surface.WearScale;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * How a ghost block decides what to look like at one position.
 *
 * <p>
 * Split out of {@link BlockGhost} so the block class itself carries nothing client-only in
 * its constant pool, and because everything here runs on Celeritas' meshing worker threads:
 * the only mutable state it touches is {@link ClientErosionCache}, which is built to be read
 * from them without locking.
 *
 * <p>
 * The covered block's own icons are asked for by metadata rather than by position. The
 * position-aware form gives better fidelity, but it invites an arbitrary mod's code to inspect the
 * world from a mesher thread and find a ghost block where it expected its own, and in a 235-mod
 * pack that is not a trade worth making everywhere.
 *
 * <p>
 * The one exception is snow lying on grass, which is the case that genuinely cannot be answered
 * from metadata alone - the answer is about the block above, not this one. There the position-aware
 * form is used, wrapped in {@link OriginView} so the block finds itself rather than a ghost, and in
 * a catch so an awkward mod costs a fallback texture rather than a chunk build.
 */
@SideOnly(Side.CLIENT)
public final class GhostRendering {

    /**
     * The map color for a worn position, from a base color darkened for its wear stage.
     *
     * <p>
     * A public seam for the JourneyMap handler, which lives in another package and must not
     * reach into {@link GhostLogic} directly. The darkening curve is the one the in-world
     * render color uses, so a block reads as equally worn on the map and underfoot.
     */
    public static int mapColorFor(SurfaceFamily appearance, int wearMeta, int baseRgb) {
        return GhostLogic.darkenForWear(baseRgb, appearance, wearMeta);
    }

    /** As above, told outright how far along the whole run the position is. */
    /**
     * A map color for a worn square, faded toward whatever the ground is turning into.
     *
     * <p>
     * Darkening alone said "this is worn" and left the map calling a bare dirt track a lawn all
     * the way to the bottom of its run, because the color was taken from the block the record
     * started as and never from what it had become. Now it travels: a turf path leaves green and
     * arrives at earth, in step with how far along its own chain it has walked, and every family
     * that wears through into something else does the same without being named.
     *
     * <p>
     * The fade runs first and the darkening second. Both are multiplications of the same three
     * channels and the order does not change the arithmetic much, but it changes what the numbers
     * mean: fade decides which material this square reads as, and darkening then says how heavily
     * that material has been used. A square is one thing at a time and then a shade of it.
     *
     * @param towardRgb the map color of what this ground is becoming, or of its own family where it
     *                  becomes nothing - which still moves a square drawn in its covered block's own
     *                  color toward its family's (spec CO20)
     */
    public static int mapColorFor(float worn, int baseRgb, int towardRgb) {
        if (!TrmtConfig.mapTracksWear) return baseRgb;
        // Material first, then how used it is, then - only if somebody has asked for it -
        // pulled toward a color that is not a material at all. Each reading is laid over
        // the last rather than replacing it, so turning the third off leaves the first two
        // exactly as they were.
        int shaded = GhostLogic.darkenBy(GhostLogic.blendToward(baseRgb, towardRgb, worn), worn);
        return GhostLogic.highlightBy(shaded, worn);
    }

    public static int mapColorFor(float worn, int baseRgb) {
        return GhostLogic.darkenBy(baseRgb, worn);
    }

    /** The family-generic map color, for a position whose origin is not known. */
    public static int mapFallbackColor(SurfaceFamily appearance) {
        return GhostLogic.fallbackMapColor(appearance);
    }

    /**
     * The thinned grass-side fringe for a position, or the vanilla overlay left unchanged.
     *
     * <p>
     * Called from MixinGrassSideOverlay for the value the renderer would otherwise draw as the
     * full green fringe over a grass block's sides. Only a still-grassy, full-height ghost thins
     * it: a ghost that has worn through to earth draws no fringe at all, and the sunken case is
     * left to draw the vanilla fringe for now. With the feature off it is always the vanilla one,
     * so the render path is byte-for-byte what it was.
     */
    public static IIcon grassSideOverlay(GhostBlock ghost, int x, int y, int z, IIcon vanilla) {
        if (PROBE) probe(
            "grassSideOverlay reached, family=" + ghost.appearance()
                + " sunken="
                + ghost.isSunken()
                + " grassSideWear="
                + TrmtConfig.grassSideWear
                + " untinted="
                + ghost.isUntinted()
                + " mimicsGrassTop="
                + ghost.mimicsVanillaGrassTop());
        if (!TrmtConfig.grassSideWear) return vanilla;
        if (ghost.appearance() != SurfaceFamily.GRASS || ghost.isUntinted()) return vanilla;
        if (!ghost.mimicsVanillaGrassTop()) return vanilla;
        if (ghost.isSunken()) return vanilla;
        int layer = sideLayer(SurfaceFamily.GRASS, x, y, z);
        IIcon thinned = WearTextures.grassSideOverlay(layer, Rotations.forPosition(x, z));
        if (PROBE) probe(
            "grassSideOverlay answering: layer=" + layer
                + " thinned="
                + (thinned == null ? "null, falling back" : thinned.getIconName())
                + " vanillaWas="
                + (vanilla == null ? "null" : vanilla.getIconName()));
        return thinned != null ? thinned : vanilla;
    }

    /**
     * How far the ground drawn at this position has dropped below where its block would stand.
     *
     * <p>
     * The difference between the two rather than the depth in the record, because those are not
     * the same on ground that already stood short: a grass path is a pixel down before anything
     * has walked on it, and a snow layer lying on one was never level with a full block to begin
     * with. What anything resting on this wants to match is where the surface actually is.
     */
    public static double settledDrop(GhostBlock ghost, IBlockAccess world, int x, int y, int z) {
        if (ghost == null || !ghost.isSunken()) return 0.0D;
        // Taken from GhostLogic.collidedHeightAt, the height that can be stood on, which is not the same
        // figure for every shape. On a slab it is the collision depth, so in visual mode - where the
        // server still believes the ground is full height - snow on a worn slab stays up. On anything
        // else heightAt answers footing from the drawn depth as well, so in visual mode snow and carpet
        // on a worn whole block come down into the drawn rut while the player still stands at full
        // height. This comment said, until 2026-10-08, that they stay up there too; the code never did,
        // and Xep chose the code that day - the ports were changed to it, and this was corrected.
        // One number either way, so the footing and the picture of what rests on it cannot part company.
        double drop = GhostLogic.originTop(ghost, x, y, z) - GhostLogic.collidedHeightAt(ghost, world, x, y, z);
        return drop > 0.0D ? drop : 0.0D;
    }

    private GhostRendering() {}

    /**
     * Says what the renderer asked this block for, when asked to.
     *
     * <p>
     * Off unless {@code -Dtrmt.spike.render=true}, and silent after the first answer of each kind,
     * because a chunk rebuild asks this thousands of times a second. It exists because a worn grass
     * flank came out wrong under OptiFine and right everywhere else, and what a renderer asks of this
     * block is the one thing about it that can be watched from this mod's own code. It found both
     * halves of that fault: OptiFine never asking the position-free icon, which cost the tint, and
     * OptiFine answering the fringe hook for one gradation where plain answers sixty-one, which cost
     * the fringe.
     *
     * <p>
     * <b>Every call is behind {@code if (PROBE)} at the call site, not only in here.</b> A message is
     * built before it is passed, so a gate inside this method still cost every face of every ghost a
     * string while the probe was off - in the chunk mesher, for every player. A constant at the call
     * site costs nothing once compiled. {@code RenderProbeIsFreeWhenOffTest} holds that.
     *
     * <p>
     * The two overloads are not interchangeable and that is the whole point: the position-free one
     * answers vanilla's own {@code grass_top} for a ghost that still mimics grass, which is what
     * tells the renderer to treat the sides as grass's - untinted, with a separate fringe over them.
     * The position-aware one answers the worn sprite. A renderer that asks the second where vanilla
     * asks the first would lose the grass treatment silently, and the face would take the tint.
     */
    static final boolean PROBE = Boolean.getBoolean("trmt.spike.render");

    private static final java.util.Set<String> PROBED = java.util.Collections
        .synchronizedSet(new java.util.HashSet<String>());

    /** Says one thing once, so a chunk rebuild does not write a log file a gigabyte long. */
    static void probe(String what) {
        if (!PROBE) return;
        if (!PROBED.add(what)) return;
        Trmt.LOG.info("Ghost probe: {}", what);
    }

    public static IIcon iconFor(GhostBlock ghost, IBlockAccess world, int x, int y, int z, int side) {
        if (PROBE) probe(
            "iconFor (position-aware) side=" + side
                + " family="
                + ghost.appearance()
                + " mimicsGrassTop="
                + ghost.mimicsVanillaGrassTop()
                + " untinted="
                + ghost.isUntinted());
        int stage = world.getBlockMetadata(x, y, z);
        int packedOrigin = ClientErosionCache.get()
            .originAt(x, y, z);

        Block origin = originBlock(packedOrigin);
        int originMeta = packedOrigin >= 0 ? (packedOrigin & 0xF) : 0;
        SurfaceFamily originFamily = origin == null ? null : SurfaceRegistry.familyOf(origin, originMeta);

        // Every face of a surface that looks the same all round, not just the top. Sand, gravel,
        // dirt and stone are one texture on all six sides, so a rut cut into them shows the wear
        // on the wall of the cut as well as on its floor.
        //
        // Grass is the exception, deliberately: its sides are a different texture from its top
        // and carry a tinted overlay, so they render as grass sides always have.
        if (side == 1 || (ghost.appearance() != SurfaceFamily.GRASS && !hasOwnSides(origin, originMeta))) {
            int layer = side == 1 ? topLayer(ghost.appearance(), stage, x, y, z)
                : sideLayer(ghost.appearance(), x, y, z);
            // Read inside this branch rather than at the top of the method: every rendered face of
            // every ghost passes through here, and the faces that do not want a generated wear
            // texture should not pay for the lookup that finds one.
            //
            // Asked by the block the record turns back into under the ids in force, not by the
            // record's number. The atlas is filed by block, so a world or server that numbers this
            // block differently from wherever the atlas was stitched still finds its own pictures,
            // and the wear cannot disagree with the tint and shape, which ask the same block.
            int depth = ErosionState.sinkOf(Trmt.proxy.erosionStateAt(x, y, z));
            IIcon wear = WearTextures
                .icon(origin, originMeta, originFamily, ghost.appearance(), layer, Rotations.forPosition(x, z, depth));
            // A picture whose layer moves is noted where it is drawn, so it moves while it is on the ground near the
            // player and nowhere else (0.9.221, SeenPictures).
            if (wear instanceof com.trmtgtnh.client.texture.WearSprite
                && ((com.trmtgtnh.client.texture.WearSprite) wear).moves()) {
                com.trmtgtnh.client.texture.InnerLayers.noteSeen(wear, x, y, z);
            }
            if (wear != null) return wear;
            if (side == 1) return ghost.fallbackIcon();
        }

        // Grass that has worn through - either past the tint split, or all the way into the
        // earth chain - shows that earth on its sides too. That is what upstream draws, and what
        // stops a bald patch keeping a green fringe around it.
        boolean revealsEarth = originFamily == SurfaceFamily.GRASS
            && (ghost.appearance() != SurfaceFamily.GRASS || ghost.isUntinted());
        int face = revealsEarth ? 0 : side;

        // Snow lying on worn grass. Vanilla swaps a grass block's sides for grass_side_snowed when
        // something snowy sits on top of it, and a ghost standing in for that block has to do the
        // same or a path through a snowfield keeps green flanks its neighbours have lost.
        //
        // Asked of the covered block through its own view of the world rather than hard-coded to
        // vanilla's texture, so a modded turf with a snow variant of its own gets that variant.
        // The view swaps the block and metadata back at this one position - straight off the world
        // the block would read our wear stage as its subtype - while everything around it, the snow
        // included, passes through untouched.
        //
        // Nothing more is needed to suppress the tinted fringe: the renderer decides to draw that
        // pass by comparing the side texture's name to grass_side, and a snowed side is not it.
        // Ground worn past its own family is skipped, because bare earth under snow is bare earth.
        if (side >= 2 && ghost.appearance() == SurfaceFamily.GRASS && !revealsEarth && snowAbove(world, x, y, z)) {
            if (origin != null) {
                try {
                    IIcon snowed = origin.getIcon(new OriginView(world, x, y, z, origin, originMeta), x, y, z, side);
                    if (snowed != null) return snowed;
                } catch (RuntimeException awkwardBlock) {
                    // Fall through to vanilla's, which is the texture this ghost is standing in for.
                }
            }
            return Blocks.grass.getIcon(world, x, y, z, side);
        }

        // A covered block with a renderer of its own may be drawing more than one pass, and
        // its side texture can be cut away where a pass underneath is meant to show through.
        // Biomes O' Plenty's loamy grass is exactly that: the top four rows of its side are
        // holes, and its own renderer fills them by drawing vanilla grass first. A ghost draws
        // in one pass, so borrowing that texture left a band you could see straight through.
        // Grass has somewhere honest to fall back to - vanilla's own grass side, which is what
        // the block is showing through the holes anyway.
        // Worn grass sides recede toward dirt with the top, when the feature is on. The
        // de-greened wall replaces grass's green-edged side with uniform earth once wear has
        // started; the fringe still drawn over it is thinned separately by MixinGrassSideOverlay.
        // The unworn first gradation and sunken grass keep today's look, and the flag off - the
        // guard's first term - restores it entirely.
        if (TrmtConfig.grassSideWear && side >= 2
            && ghost.appearance() == SurfaceFamily.GRASS
            && !revealsEarth
            && !ghost.isSunken()
            // Only the variant standing in for vanilla grass, exactly as MixinGrassTint and the
            // receding fringe are gated. This wall is handed back under the name grass_side so
            // the fringe still draws over it - and that name is a claim only a vanilla-looking
            // turf can make. Wrapping a modded grass in it paints vanilla's tinted fringe across
            // somebody else's texture, which is what put a red edge on spectral moss and a dark
            // band on Biomes O' Plenty's long grass.
            && ghost.mimicsVanillaGrassTop()
            && sideLayer(SurfaceFamily.GRASS, x, y, z) >= WALL_START) {
            IIcon wall = WearTextures.grassEarthWall(origin, originMeta);
            if (wall != null) return asGrassSide(wall);
        }

        if (side >= 2 && ghost.appearance() == SurfaceFamily.GRASS
            && !revealsEarth
            && origin != null
            && origin.getRenderType() != 0) {
            IIcon mended = WearTextures.mendedSide(origin, originMeta);
            if (mended != null) return asGrassSide(mended);
            // Nothing mended for this block, so vanilla's own side rather than a hole.
            return Blocks.grass.getIcon(2, 0);
        }

        if (origin != null) {
            try {
                IIcon icon = origin.getIcon(face, originMeta);
                if (icon != null) {
                    // A made surface keeps its own side texture, and as the ground sinks that
                    // texture slides down with it, so the cap along its top edge stays at the
                    // surface instead of being cropped away and leaving bare soil. Uniform
                    // surfaces never get here - they took the wear-sprite branch above, where a
                    // rut wall showing wear from the top is what you want.
                    if (side >= 2 && face == side
                        && ghost.isSunken()
                        && ghost.appearance() != SurfaceFamily.GRASS
                        && !"grass_side".equals(icon.getIconName())
                        && hasOwnSides(origin, originMeta)) {
                        int rows = SideShift.rows(
                            GhostLogic.originTop(ghost, x, y, z),
                            ghost.asBlock()
                                .getBlockBoundsMaxY());
                        // Still the record's number rather than its block: here it only picks the
                        // cache slot, and a wrapper is reused only around the very icon it was made
                        // for, so a number that has come to name another block builds a fresh one.
                        if (rows > 0) return SideShift.wrap(packedOrigin, side, rows, icon);
                    }
                    return icon;
                }
            } catch (RuntimeException awkwardBlock) {
                // Fall through to the family default rather than failing a chunk build.
            }
        }
        return ghost.asBlock()
            .getIcon(side, stage);
    }

    /** Whether something snowy is resting on this block, which is what vanilla tests for. */
    private static boolean snowAbove(IBlockAccess world, int x, int y, int z) {
        Block above = world.getBlock(x, y + 1, z);
        if (above == null) return false;
        net.minecraft.block.material.Material material = above.getMaterial();
        return material == net.minecraft.block.material.Material.snow
            || material == net.minecraft.block.material.Material.craftedSnow;
    }

    /**
     * Set while the breaking dust for a block is being spawned, so it takes no wear shade.
     *
     * <p>
     * The dust asks a block for its tint through the very same method a minimap does, and hands it
     * the real world doing it - so the one test that tells a map apart from the renderer cannot tell
     * a particle apart from a map. Left alone, the dust off a worn path comes away about three times
     * darker than the ground it came off, because the shade is applied to a particle whose sprite is
     * the unworn face and has nothing darkened into it already.
     *
     * <p>
     * Cleared on the next client tick rather than at the end of the call that set it, because the
     * particles are spawned after the block has finished answering: Forge asks the block first and
     * only builds them when it declines. A tick is the smallest window that covers the spawn, and
     * what it costs is that a map asking about the very square being mined, in that one tick, gets
     * the unworn color - which it will ask again for as soon as the chunk is next redrawn.
     */
    private static volatile boolean shadeOffForDust;

    /** Called by a ghost about to let vanilla spawn its breaking dust. */
    public static void holdShadeForDust() {
        shadeOffForDust = true;
    }

    /** Called once a tick, so the hold above cannot outlive the particles it was for. */
    public static void releaseShadeForDust() {
        shadeOffForDust = false;
    }

    /**
     * Whether this question is coming from outside the block renderer.
     *
     * <p>
     * The one test that separates a map reading the loaded world from the mesher building a chunk,
     * and the reason both of the map-facing corrections below are safe: neither can reach a face
     * being drawn. Vanilla meshes through a {@code ChunkCache} and this pack's Celeritas through a
     * {@code WorldSlice}; a map walking the world passes the world itself. The breaking dust passes
     * it too, and is excluded by hand because nothing about its type says so.
     */
    private static boolean fromOutsideTheRenderer(IBlockAccess world) {
        return !shadeOffForDust && world instanceof World;
    }

    /**
     * What an untinted turf top averages to, which is the color a biome tint is meant to be
     * applied to.
     *
     * <p>
     * Vanilla's own {@code grass_top.png}, measured the way a minimap measures it. It is a grey
     * because that is the whole design of tinted grass: the texture carries the light and shade and
     * none of the color, and the biome decides the rest.
     */
    private static final int UNTINTED_TURF = 0x969696;

    /**
     * Cancels the green already sitting in a fallback map color, for a map that will tint on top.
     *
     * <p>
     * A map that cannot read this mod's generated sprites falls back to asking the block for its map
     * color, and for turf that answer is {@code MapColor.grassColor} - which is green already,
     * having been chosen for a map that does no tinting of its own. A map that then multiplies by
     * this block's tint applies the biome's green to a color that is green twice over, and a path
     * through modded turf comes out a vivid stripe against the duller grass around it.
     *
     * <p>
     * Vanilla turf escapes it by accident rather than by design: the icon reported for that case is
     * vanilla's own {@code grass_top}, a real file, so the map finds it, averages the grey, and the
     * tint lands on exactly what it was meant to land on. This puts every other turf on the same
     * footing by scaling the tint so that grassColor times the tint arrives where grey times the
     * tint would have - which is why the factor is the ratio of the two and not a color anybody
     * picked.
     *
     * <p>
     * Only inside the map gate, so nothing the renderer is handed changes, and only for turf that is
     * not already mimicking vanilla's top - the same predicate that decided which icon was reported,
     * so the case that already works is left exactly alone. Only turf, because turf is the only
     * family whose covered block returns a tint at all; sand and stone hand back white and there is
     * nothing to double.
     */
    private static int untintCorrection(int tint) {
        int red = scaleChannel(tint, 16, (UNTINTED_TURF >> 16) & 0xFF, (MapColor.grassColor.colorValue >> 16) & 0xFF);
        int green = scaleChannel(tint, 8, (UNTINTED_TURF >> 8) & 0xFF, (MapColor.grassColor.colorValue >> 8) & 0xFF);
        int blue = scaleChannel(tint, 0, UNTINTED_TURF & 0xFF, MapColor.grassColor.colorValue & 0xFF);
        return (red << 16) | (green << 8) | blue;
    }

    /**
     * One channel scaled by a ratio, clamped to what a channel can hold.
     *
     * <p>
     * Clamped rather than allowed to wrap, and it does reach the clamp: the blue of grassColor is a
     * long way under the grey's, so that channel is asked to rise nearly threefold and saturates on
     * anything but a dark tint. Saturating is the right answer - the ceiling is what a map can draw -
     * and it is named here because a silent wrap would show up as a magenta path.
     */
    private static int scaleChannel(int rgb, int shift, int wanted, int have) {
        if (have <= 0) return (rgb >> shift) & 0xFF;
        int scaled = ((rgb >> shift) & 0xFF) * wanted / have;
        return scaled > 0xFF ? 0xFF : scaled;
    }

    /**
     * How much darker this position should read to something asking from outside the renderer.
     *
     * <p>
     * White - no change at all - for the chunk mesher, and that is the whole delicacy of this
     * method. A worn block's darkening already lives in the pixels of the sprite it is drawn with,
     * so multiplying it in again while the world is being meshed would darken every worn block
     * twice. But a minimap that reads the client's own world has no sprite to look at: the only
     * position-aware question any of them asks a block is this one, so wear either travels here or
     * it does not travel at all.
     *
     * <p>
     * The two are told apart by what the caller hands over, which is an inference rather than a
     * contract and is written down as one. Vanilla meshes a chunk through a {@code ChunkCache} and
     * this pack's own mesher, Celeritas, through a {@code WorldSlice}; neither is a {@link World}.
     * A minimap walking the loaded world passes the world itself. The inference holds for every
     * caller that exists today and was checked against both meshers by name, but nothing stops a
     * future one handing over the real thing - which is why {@code client.mapWearThroughTint} is
     * here to switch it off, and why the failure it would cause is written into that setting.
     *
     * <p>
     * Two callers are known to fall on the wrong side of it. The block-breaking overlay is built
     * against the real world, so the cracks on a square being mined take the shade; that one is
     * left alone, because it lasts as long as the swing and vanilla tints that overlay by this
     * very method anyway. The breaking dust is the other, and it is not left alone - see
     * {@link #holdShadeForDust}, because a particle drawn from the unworn face has nothing
     * darkened into it already and the shade on top of that is simply wrong.
     *
     * <p>
     * Asked of the run rather than of the gradation, so a position reads by how far along its whole
     * chain it has come - the same fraction, from the same chain, that the JourneyMap handler works
     * from. Neither the fade toward the material underneath nor the desire-path highlight can come
     * with it: both are blends toward an absolute color, and a multiplier cannot carry those.
     */
    static int wearShade(GhostBlock ghost, IBlockAccess world, SurfaceFamily base, int x, int y, int z) {
        if (!TrmtConfig.mapWearThroughTint || !TrmtConfig.mapTracksWear) return 0xFFFFFF;
        if (!fromOutsideTheRenderer(world)) return 0xFFFFFF;
        if (base == null) return 0xFFFFFF;

        int length = ErosionChain.length(base);
        if (length <= 1) return 0xFFFFFF;

        short state;
        try {
            state = ClientErosionCache.get()
                .stateAt(x, y, z);
        } catch (RuntimeException racingAnUnload) {
            // A read that raced a chunk going away. Untouched is the least-wrong answer.
            return 0xFFFFFF;
        }
        if (state == ErosionState.NONE) return 0xFFFFFF;

        int index = ErosionChain
            .indexOf(base, ErosionState.familyOf(state), ErosionState.layerOf(state), ErosionState.sinkOf(state));
        if (index < 0) index = 0;
        if (index > length - 1) index = length - 1;
        return GhostLogic.darkenBy(0xFFFFFF, index / (float) (length - 1));
    }

    public static int colorFor(GhostBlock ghost, IBlockAccess world, int x, int y, int z) {
        if (PROBE) probe(
            "colorFor family=" + ghost
                .appearance() + " mimicsGrassTop=" + ghost.mimicsVanillaGrassTop() + " untinted=" + ghost.isUntinted());
        // A glow is a deliberate act and outranks every rule below about when ground should and
        // should not be tinted - somebody chose this color for this block, so it wins.
        int glow = GhostLight.packedAt(world, x, y, z);

        int packedOrigin = ClientErosionCache.get()
            .originAt(x, y, z);
        Block origin = originBlock(packedOrigin);
        // Nothing recorded here, so there is no run to read a position off and nothing to shade by.
        if (origin == null) return GhostLight.colorOf(glow);

        int originMeta = packedOrigin & 0xF;
        SurfaceFamily originFamily = SurfaceRegistry.familyOf(origin, originMeta);

        // Multiplied into every answer below rather than into one of them. The exit taken says
        // whether a biome tint applies, which is a different question from how worn the ground is -
        // and the exit most worth shading is the one for ground that has worn past its own family,
        // because that is the deepest wear there is.
        int shade = wearShade(ghost, world, originFamily, x, y, z);

        // Once a surface has worn past its own family, or past the point where a biome tint
        // still helps, it is mostly bare earth. Tinting that is what would turn a path olive.
        if (originFamily != ghost.appearance() || ghost.isUntinted()) {
            return blend(GhostLight.colorOf(glow), shade);
        }

        // The tint the covered block would take, eased off as its cover wears away.
        //
        // A face gets one color, and by the end of the grass run most of that face is the earth
        // underneath, which wants no tint at all. Holding the tint at full strength leaves worn
        // earth olive; correcting the earth in the texture instead means guessing which green to
        // correct against, and every biome that is not the guess comes out wrong in one
        // direction or the other. This is the one place the real tint is known, so this is where
        // it gets decided: full strength while the face is mostly grass, easing toward none as
        // the earth takes over. What little grass is left at that point is under-tinted, which
        // is a far smaller error than earth the color of moss.
        try {
            // Asked through a view that gives the covered block back its own metadata. Straight
            // off the world it would read ours, which at this position is the wear stage - and a
            // block whose color switches on metadata then answers for a subtype it never was.
            // Natura's turf did exactly that: at stage one it came back the blue of bluegrass,
            // at stage two the orange of the autumnal kind, in flat squares across the face.
            int tint = origin.colorMultiplier(new OriginView(world, x, y, z, origin, originMeta), x, y, z);
            // Corrected for a map and never for the renderer, and only where a map will have
            // fallen back to a green map color to tint. Its own question rather than the wear
            // one, because this is wrong at nought wear too. See untintCorrection.
            if (fromOutsideTheRenderer(world) && ghost.appearance() == SurfaceFamily.GRASS
                && !ghost.mimicsVanillaGrassTop()) {
                tint = untintCorrection(tint);
            }
            return blend(blend(tint, GhostLight.colorOf(glow)), shade);
        } catch (RuntimeException awkwardBlock) {
            return blend(GhostLight.colorOf(glow), shade);
        }
    }

    /**
     * Two tints, multiplied channel by channel.
     *
     * <p>
     * Which is what a color multiplier already is, so a lit block gets its biome color and its
     * glow at once rather than one replacing the other - grass in a swamp still reads as swamp
     * grass when somebody lights it green.
     */
    private static int blend(int first, int second) {
        if (second == 0xFFFFFF) return first;
        if (first == 0xFFFFFF) return second;
        int red = ((first >> 16) & 0xFF) * ((second >> 16) & 0xFF) / 255;
        int green = ((first >> 8) & 0xFF) * ((second >> 8) & 0xFF) / 255;
        int blue = (first & 0xFF) * (second & 0xFF) / 255;
        return (red << 16) | (green << 8) | blue;
    }

    /**
     * The world as the covered block would have found it, at the one position being asked about.
     *
     * <p>
     * Only the block and its metadata are answered differently, and only at that position.
     * Everything else - the biome above all, which is where a grass tint actually comes from -
     * is the real world's answer, because the point is to let a block color itself correctly,
     * not to show it a world that does not exist.
     */
    /**
     * The side a worn grass block is drawn with, for OptiFine's comparison, or null when it is
     * vanilla's own side or anything else that is not one of these stand-ins.
     *
     * <p>
     * Called from {@code MixinOptiFineGrassSide}, which hands this to the comparison OptiFine makes
     * instead of the one vanilla makes - see {@link AsGrassSide}. It asks {@link #iconFor} itself
     * rather than repeating its conditions, so the two cannot come to disagree about which sides
     * are stand-ins; and it asks for side 2 because every branch that makes one depends only on
     * the side being a side.
     */
    public static IIcon grassSideStandIn(GhostBlock ghost, IBlockAccess world, int x, int y, int z) {
        IIcon side = iconFor(ghost, world, x, y, z, 2);
        IIcon standIn = side instanceof AsGrassSide ? side : null;
        if (PROBE) probe("grassSideStandIn " + (standIn != null ? "substituted for OptiFine" : "not needed"));
        return standIn;
    }

    /** One stand-in per sprite, so that the same side is the same object every time it is asked for. */
    private static final ConcurrentHashMap<IIcon, AsGrassSide> STAND_INS = new ConcurrentHashMap<IIcon, AsGrassSide>();

    /**
     * The stand-in for one sprite - always the same object for the same sprite.
     *
     * <p>
     * <b>That is load-bearing, not tidiness.</b> OptiFine decides whether a side gets its fringe with
     * {@code ==}, so the object the renderer drew the face with and the one {@link #grassSideStandIn}
     * hands its comparison have to be one and the same; a fresh wrapper per call would never match.
     * A sprite changes identity on a resource reload, which is the one thing that retires a stand-in,
     * so the stale ones are dropped when there are more than a reload's worth - they can only ever
     * be looked up by sprites that no longer exist.
     */
    private static IIcon asGrassSide(IIcon delegate) {
        AsGrassSide held = STAND_INS.get(delegate);
        if (held != null) return held;
        if (STAND_INS.size() > 4096) STAND_INS.clear();
        AsGrassSide made = new AsGrassSide(delegate);
        AsGrassSide raced = STAND_INS.putIfAbsent(delegate, made);
        return raced != null ? raced : made;
    }

    /**
     * A sprite that answers to vanilla grass's name.
     *
     * <p>
     * Vanilla decides whether to draw grass's separately tinted fringe by comparing the side
     * texture's name against {@code grass_side}. A mended side carries the covered block's own
     * earth with vanilla's grass baked into the rows its texture cuts away - which is the right
     * picture underneath, but under a name the renderer does not recognise, so the fringe that
     * gives it its biome color would never be drawn. The de-greened wall of worn grass is the
     * same case.
     *
     * <p>
     * <b>OptiFine does not compare the name.</b> Its smooth-lighting method, and the overlay test in
     * the flat-lit one, compare the side against its own cached copy of vanilla's sprite with
     * {@code ==}, so a wrapper answering to the name was refused and every worn grass wall under
     * OptiFine was drawn bare, with no fringe at all. Found on 2026-10-07 by reading OptiFine's own
     * RenderBlocks out of its patches. {@code MixinOptiFineGrassSide} hands that comparison this
     * stand-in instead - which is why there is only ever one of each, see {@link #asGrassSide}.
     *
     * <p>
     * Only the name is borrowed. Every coordinate is the mended sprite's own, so what gets
     * drawn is the block's loam under vanilla's tinted fringe, which is what the block's own
     * renderer produces in two passes.
     */
    private static final class AsGrassSide implements IIcon {

        private final IIcon delegate;

        AsGrassSide(IIcon delegate) {
            this.delegate = delegate;
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
            return delegate.getInterpolatedV(v);
        }

        @Override
        public String getIconName() {
            return "grass_side";
        }
    }

    /** How much of this face has worn through to the earth underneath, from 0 to 1. */
    private static float coverageAt(SurfaceFamily appearance, IBlockAccess world, int x, int y, int z) {
        FamilySettings settings = TrmtConfig.family(appearance);
        if (settings == null) return 0f;
        int layers = Math.max(1, settings.stages);
        if (layers <= 1) return 0f;
        int layer = world.getBlockMetadata(x, y, z);
        if (layer < 0) return 0f;
        if (layer > layers - 1) layer = layers - 1;
        return layer / (float) (layers - 1);
    }

    /**
     * Whether a ghost drawn as this appearance is still made of the block it is covering.
     *
     * <p>
     * True for the whole of a family's own run and false once it has worn through into something
     * else, which is the moment what the ground is made of stops being what it started as.
     */
    public static boolean showsOwnMaterial(Block origin, int originMeta, SurfaceFamily appearance) {
        if (origin == null || appearance == null) return false;
        return SurfaceRegistry.familyOf(origin, originMeta) == appearance;
    }

    /** The stock texture a family is drawn from when a block's own pixels cannot be read. */
    public static String stockTextureName(SurfaceFamily appearance) {
        return GhostLogic.fallbackTextureName(appearance);
    }

    /** The vanilla block a family looks like, for anything that needs a real block to point at. */
    public static Block counterpartOf(SurfaceFamily appearance) {
        return appearance == null ? null : GhostLogic.vanillaCounterpart(appearance);
    }

    /** Resolves a packed origin back to its block, or null when nothing was recorded. */
    public static Block originBlock(int packedOrigin) {
        if (packedOrigin < 0) return null;
        return Block.getBlockById(packedOrigin >> 4);
    }

    /**
     * How worn the top face looks.
     *
     * <p>
     * A mix of the gradation this block is actually on and how far it has come overall. On its
     * own the gradation restarts every time the ground drops a pixel, so a block eight pixels
     * down looked <em>newer</em> than one that had never sunk - which is backwards, and very
     * visible when two of them sit side by side.
     *
     * <p>
     * Mixing keeps both readings. Each run still wears visibly from its own start to its own
     * end, so you can watch a block work through its gradations; but every run starts further
     * along than the one above it, so a deeper block is always the more worn of the two. The
     * balance between those is {@code client.wearCarriesWithDepth}: zero is the old behaviour,
     * one ignores the gradation and shows only the overall figure.
     */
    /**
     * How much of a surface's pictures the first run is given, before the ground has sunk at all.
     *
     * <p>
     * Worth being plain about what this cannot do before what it does. A family with sixteen
     * stages, eight layers to a pixel of depth and eight pixels of depth walks eighty chain steps
     * with sixteen pictures. Fifteen picture changes plus eight sinkings is twenty-three of the
     * seventy-nine transitions that can show anything new, and fifty-six that cannot - a count that
     * comes out at twenty-three whatever shape is used here, measured across every exponent from
     * 0.35 to 1.10 and at every share tried. Nothing here buys a single extra visible step. All it
     * decides is where the fifty-six silent ones fall.
     *
     * <p>
     * They should not fall evenly, and the reason is not taste. The first run is the sixteen steps
     * before the ground has dropped a pixel - the wear anybody actually watches - and the picture
     * is the only thing it has to speak with. Every later run drops the block a pixel on its way
     * past, which is real geometry rather than a texture, and is legible without any help. Spread
     * by step count, the first run keeps four of the sixteen pictures and tops out at picture
     * three: a fresh path across ground that has not sunk accumulates 8.3 color levels of change
     * where it had 19.2, which is not a coarser path but a path half as far along. Note what does
     * NOT change there - the smallest step inside the first run is 2.74 either way, because
     * consecutive pictures are always one gradation apart. It is the run's length that goes, not
     * its steps.
     *
     * <p>
     * Which is why this is a share rather than an exponent, and the share is what a power curve
     * cannot give. Under a power the shallow end's picture count is decided by how long the chain
     * behind it happens to be, so a pack that doubles {@code layersPerDepth} halves the legibility
     * of a run it never touched - measured, a half power gives the first run anything from five
     * pictures to ten depending on numbers the family owns. Here it is eight on every shape tried.
     * At forty hundredths the first run keeps seven of the sixteen pictures at the config minimum and
     * thirty-three of the eighty at the default, and nowhere on the whole eighty does the ground go
     * more than two steps looking identical. Forty-seven kept eight of the sixteen, and is what this
     * was while the share only ever ran at coarse gradation counts; it costs one picture at the
     * minimum to buy back five of the eighty at the default, which is where almost everybody is. The half power this
     * replaces held one picture unbroken for eleven
     * consecutive steps near the bottom of a rut.
     */
    private static final float SHALLOW_SHARE = 0.40f;

    /**
     * A record's layer, placed in the space the whole chain is counted in.
     *
     * <p>
     * The two early exits above hand back what was read out of block metadata, and that is a
     * position within one run of one family - nought to fifteen. Everything downstream now reads a
     * position along the whole eighty-step chain, so handing the raw layer over would draw a
     * thoroughly worn surface at the first fifth of its pictures. It went unnoticed for as long as
     * the two spaces were the same width, and it is only reachable with
     * {@code client.wearCarriesWithDepth} at zero or with a family that has no settings at all.
     *
     * <p>
     * Spread rather than copied, so the last gradation of a run is the last picture of the run
     * whatever length somebody has given it. That also mends something older: the counted value
     * used to be bounded by the family's own stage count while {@code WearTextures.icon} divided by
     * sixteen, so a family configured to eight stages could never reach the top half of its
     * pictures. The two ends are now the same number by construction rather than by coincidence.
     */
    private static int counted(int layer, FamilySettings settings) {
        if (layer <= 0) return 0;
        int runLast = (settings == null ? SurfaceFamily.MAX_STAGES : Math.max(1, settings.stages)) - 1;
        if (runLast <= 0) return 0;
        int last = WearScale.COUNTED_STEPS - 1;
        int placed = Math.round(layer / (float) runLast * last);
        return placed > last ? last : placed;
    }

    /**
     * How much side wear the de-greened grass wall waits for.
     *
     * <p>
     * Three at the shipped counted space and one at sixteen, which is the point of writing it as a
     * fraction rather than as a number. The gate below used to be simply "more than nothing", and
     * that meant one drawn gradation of sixteen; widening the counted space fivefold without this
     * would have brought the wall out at a fifth of the wear it needs today, on every existing
     * save, for nobody who asked. Measured, the wall first appears at chain step three either way.
     */
    private static final int WALL_START = Math
        .max(1, (WearScale.COUNTED_STEPS + SurfaceFamily.MAX_STAGES) / (2 * SurfaceFamily.MAX_STAGES));

    /** How many steps this family's whole chain has - the same count {@code progressOf} divides by. */
    private static int chainSteps(FamilySettings settings) {
        if (settings == null) return SurfaceFamily.MAX_STAGES;
        return Math.max(1, settings.stages)
            + Math.max(0, settings.maxSinkPixels) * Math.max(1, settings.layersPerDepth);
    }

    private static int topLayer(SurfaceFamily appearance, int layer, int x, int y, int z) {
        FamilySettings settings = TrmtConfig.family(appearance);
        if (settings == null || TrmtConfig.wearCarriesWithDepth <= 0f) return counted(layer, settings);

        short state = Trmt.proxy.erosionStateAt(x, y, z);
        if (state == ErosionState.NONE) return counted(layer, settings);

        // The top of the counted space, which is not the family's stage count. What is drawn is a
        // client-side ramp of pictures, and how long a family's run of records happens to be has
        // nothing to say about how finely that ramp is divided. Everything below still works in
        // fractions and multiplies by this at the end, so only the scale changes.
        int last = WearScale.COUNTED_STEPS - 1;
        int sink = ErosionState.sinkOf(state);
        int runLast = Math.max(1, sink == 0 ? settings.stages : settings.layersPerDepth) - 1;
        float withinRun = runLast <= 0 ? 1f : layer / (float) runLast;

        if (TrmtConfig.wearNeverStepsBack) {
            // Each run gets its own slice of the pictures and fills it from one end to the
            // other. Both readings survive - a block still wears visibly across its own run,
            // and a deeper block is still always the more worn - but they can no longer
            // disagree, because the run's detail is laid inside its slice rather than added
            // alongside it. Adding them is what let the restart at a depth boundary outweigh
            // the rise and send the ground back toward clean as it dropped.
            float from = eased(runStart(settings, sink), settings);
            float to = eased(sink >= settings.maxSinkPixels ? 1f : runStart(settings, sink + 1), settings);
            int placed = Math.round((from + withinRun * (to - from)) * last);
            if (placed < 0) return 0;
            return placed > last ? last : placed;
        }
        // Through the same method the monotone branch uses, so the toggle changes which gradation
        // is drawn and not how the run is shaped. It used to be a second copy of the number.
        float overall = eased(
            ErosionState.progressOf(state, settings.stages, settings.layersPerDepth, settings.maxSinkPixels),
            settings);

        float carry = TrmtConfig.wearCarriesWithDepth;
        int blended = Math.round((overall * carry + withinRun * (1f - carry)) * last);
        if (blended < 0) return 0;
        return blended > last ? last : blended;
    }

    /**
     * Where a run begins, as a fraction of the whole chain.
     *
     * <p>
     * The same arithmetic {@code ErosionState.progressOf} uses, asked about the first layer of
     * a run rather than about a record that exists. It is what lets a run be given a slice with
     * ends rather than a single point.
     */
    private static float runStart(FamilySettings settings, int sink) {
        int first = Math.max(1, settings.stages);
        int per = Math.max(1, settings.layersPerDepth);
        int index = sink == 0 ? 0 : first + (sink - 1) * per;
        int total = first + Math.max(0, settings.maxSinkPixels) * per;
        if (total <= 1) return 0f;
        float fraction = index / (float) (total - 1);
        if (fraction < 0f) return 0f;
        return fraction > 1f ? 1f : fraction;
    }

    /**
     * Where a chain position sits among the drawn pictures.
     *
     * <p>
     * The same shape everywhere it is asked for, so that turning {@code wearNeverStepsBack} off
     * changes which gradation is drawn and not how a run is shaped - which this method has always
     * promised and which two copies of a magic number in one file were one edit away from breaking.
     *
     * <p>
     * A family that never sinks is handed straight back, and that is a correctness guard rather
     * than tidiness. Grass has no depth of its own, so its whole chain is its first run and its
     * sixteen steps get all sixteen pictures. In {@link #topLayer} that happens anyway, because a
     * run with nowhere to sink to takes its upper end at exactly one; but the blended path asks
     * this about interior positions, and without the guard grass would be squashed into the share
     * above - the one family in the mod that already gives every step its own picture, spoiled by a
     * rule written for families it has nothing in common with.
     *
     * <p>
     * It used to be handed straight back when the atlas held at least a picture for every step of
     * the chain, on the reasoning that with enough pictures to go round there is nothing to ration.
     * The count was the wrong thing to count. Handed straight through, all seventy-one of the
     * transitions that can show anything new do - and every one of them shows the same amount, 0.43
     * of a color level on dirt and 0.70 on stone, from the first step to the last, because the
     * chain's progress maps onto the pictures in a straight line.
     *
     * <p>
     * Flat is the fault rather than the cure. The first sixteen steps are the only ones with nothing
     * but the picture to speak with: every later run drops the block a pixel on its way past, and a
     * rut with walls on four sides carries its own reading. Nine stored levels of wear on stone used
     * to move not one pixel of two hundred and fifty-six by a color difference anybody can see,
     * against grass's twenty at the first level alone - and grass is the one look nobody has
     * complained about. So the share is applied at every gradation count now. At forty hundredths,
     * sixty-two of the seventy-nine transitions show something new and the longest run that shows
     * nothing is two steps. Of the seventeen that stay silent, eight are the depth boundaries, where
     * the block drops a pixel and the geometry says it instead; the other nine all fall at stored
     * level sixteen or later, inside a rut that has already begun.
     */
    private static float eased(float progress, FamilySettings settings) {
        if (progress <= 0f) return 0f;
        if (progress >= 1f) return 1f;
        float firstRunEnd = runStart(settings, 1);
        if (firstRunEnd <= 0f || firstRunEnd >= 1f) return progress;
        if (progress <= firstRunEnd) return SHALLOW_SHARE * (progress / firstRunEnd);
        return SHALLOW_SHARE + (1f - SHALLOW_SHARE) * ((progress - firstRunEnd) / (1f - firstRunEnd));
    }

    /**
     * Whether this block draws its sides from a different texture than its top.
     *
     * <p>
     * Sand, stone and gravel are one texture all round, so a rut cut into them shows wear on the
     * wall of the cut as well as its floor. A grass block or a made path is not: its sides are
     * their own texture, showing the earth it sits on with a cap along the top edge. Wearing
     * those from the top texture puts the path's surface down its sides, which is how a worn
     * path came to look like a column of path rather than a path over soil.
     */
    private static boolean hasOwnSides(Block origin, int originMeta) {
        if (origin == null) return false;
        try {
            IIcon top = origin.getIcon(1, originMeta);
            IIcon flank = origin.getIcon(2, originMeta);
            if (top == null || flank == null) return false;
            return !top.getIconName()
                .equals(flank.getIconName());
        } catch (RuntimeException awkwardBlock) {
            return false;
        }
    }

    /**
     * How worn a side face looks.
     *
     * <p>
     * Driven by how far the ground has come overall, not by the layer it happens to be showing.
     * Those differ, because the layers restart every time the ground drops a pixel - a rut eight
     * pixels deep is on layer zero of its ninth run - so a side that followed the layer would
     * visibly un-wear eight times on the way down. Overall progress only ever goes forwards,
     * which is what a wall that has been brushing past traffic the whole time should do.
     *
     * <p>
     * Scaled down as well, so the wall stays behind the floor. The floor is what takes the
     * traffic; the sides only get what catches them on the way past.
     */
    private static int sideLayer(SurfaceFamily appearance, int x, int y, int z) {
        FamilySettings settings = TrmtConfig.family(appearance);
        if (settings == null) return 0;

        short state = Trmt.proxy.erosionStateAt(x, y, z);
        if (state == ErosionState.NONE) return 0;

        // The third copy of that number, and the one nobody would have thought to look for: a rut's
        // wall shaped differently from its floor is two shapes in one hole.
        float progress = eased(
            ErosionState.progressOf(state, settings.stages, settings.layersPerDepth, settings.maxSinkPixels),
            settings);
        int last = WearScale.COUNTED_STEPS - 1;
        int layer = Math.round(progress * last * TrmtConfig.sideWearFraction);
        if (layer < 0) return 0;
        return layer > last ? last : layer;
    }
}
