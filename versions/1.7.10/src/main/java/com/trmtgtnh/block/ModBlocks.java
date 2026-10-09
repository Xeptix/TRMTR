package com.trmtgtnh.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceShape;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The seven blocks that exist only so a client has something to paint with.
 *
 * <p>
 * One per staged family, plus two more grass variants, a solid twin for ice, and a see-through
 * twin of every one of them. The count is deliberately fixed and
 * independent of config: metadata carries the wear stage and rotation comes from the
 * position, so raising a family's gradations from five to twelve needs no new blocks. That
 * matters beyond tidiness — registered block names are written into a save's Forge id map, so
 * a set that changed with config would make Forge report missing ids every time the config
 * changed.
 *
 * <p>
 * They are registered on both sides even though only a client ever places one, because the id
 * map is compared between client and server on connect and has to agree.
 */
public final class ModBlocks {

    /** Wear on grass whose top texture is vanilla's, so the grass side overlay applies. */
    public static BlockGhostGrass ghostGrassVanilla;

    /** Wear on any other grass-like block, which never had that special case. */
    public static BlockGhostGrass ghostGrassPlain;

    /** Grass worn past the point where a biome tint still helps: untinted, earth-sided. */
    public static Block ghostGrassDeep;

    /**
     * Ice that fills its square to look at as well as to walk on.
     *
     * <p>
     * Three of them and not a map, because the clear families are one family and naming it says so.
     * Registered after every loop below rather than inside one, so that a save written by an older
     * build finds every name it already knew at the id it already had.
     */
    public static Block ghostIceSolid;

    public static Block ghostIceSolidSunken;

    public static Block ghostIceSolidStair;

    /**
     * The see-through twins, one for every stand-in that can end up with a hole in it.
     *
     * <p>
     * A whole second set, and the count is not a matter of taste. What decides whether a block wants
     * one is a texture named in a config file, which can name a block of any family - and the rule
     * at the head of this class says the set of registered names may not vary with config, because
     * those names go into a save's own record of what its ids mean. So either every family that can
     * wear has a twin or none can, and the set is fixed the moment it ships.
     *
     * <p>
     * Registered last, after the ice twins that came before them, so that a save written by an
     * older build finds every name it already knew at the id it already had.
     */
    private static final Map<SurfaceFamily, Block> WINDOWS = new EnumMap<SurfaceFamily, Block>(SurfaceFamily.class);

    private static final Map<SurfaceFamily, Block> WINDOWS_SUNKEN = new EnumMap<SurfaceFamily, Block>(
        SurfaceFamily.class);

    private static final Map<SurfaceFamily, Block> WINDOWS_STAIRS = new EnumMap<SurfaceFamily, Block>(
        SurfaceFamily.class);

    /** Grass whose top texture is vanilla's, seen through. */
    public static Block ghostGrassWindow;

    /** Any other grass-like block, seen through. */
    public static Block ghostGrassPlainWindow;

    public static Block ghostIceSolidWindow;

    public static Block ghostIceSolidWindowSunken;

    public static Block ghostIceSolidWindowStair;

    /** The hollowed-out variants, one per family, used once a stage starts sinking. */
    private static final Map<SurfaceFamily, Block> SUNKEN = new EnumMap<SurfaceFamily, Block>(SurfaceFamily.class);

    /** The stair-shaped variants, one per family, used wherever a stair is what wore. */
    private static final Map<SurfaceFamily, Block> STAIRS = new EnumMap<SurfaceFamily, Block>(SurfaceFamily.class);

    private static final Map<SurfaceFamily, Block> GHOSTS = new EnumMap<SurfaceFamily, Block>(SurfaceFamily.class);

    /** Every ghost registered, in registration order, for reporting. */
    private static final List<Block> ALL = new ArrayList<Block>();

    private ModBlocks() {}

    /** Every ghost block registered, in registration order. */
    public static List<Block> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static void register() {
        ghostGrassVanilla = (BlockGhostGrass) create(SurfaceFamily.GRASS, true, false, false, "ghost_grass");
        ghostGrassPlain = (BlockGhostGrass) create(SurfaceFamily.GRASS, false, false, false, "ghost_grass_plain");
        ghostGrassDeep = create(SurfaceFamily.GRASS, false, true, false, "ghost_grass_deep");
        GHOSTS.put(SurfaceFamily.GRASS, ghostGrassPlain);

        for (SurfaceFamily family : SurfaceFamily.values()) {
            if (!family.staged || family == SurfaceFamily.GRASS) continue;
            GHOSTS.put(family, create(family, false, false, false, "ghost_" + family.key()));
        }

        // Registered for every family whether or not it is configured to sink, so the set of
        // block names in a save never depends on config. A family with no sink depth simply
        // never has its hollow variant chosen.
        for (SurfaceFamily family : SurfaceFamily.values()) {
            if (!family.staged) continue;
            boolean untinted = family == SurfaceFamily.GRASS;
            SUNKEN.put(family, create(family, false, untinted, true, "ghost_" + family.key() + "_sunken"));
        }

        // And one stair apiece, for the same reason and on the same terms: registered whatever the
        // family's stairs switch says, so turning that switch off never changes the set of block
        // names a save contains. A stair needs a class of its own because the renderer's dispatch
        // for its render type casts to BlockStairs before it does anything else.
        for (SurfaceFamily family : SurfaceFamily.values()) {
            if (!family.staged) continue;
            STAIRS.put(family, createStair(family, "ghost_" + family.key() + "_stair"));
        }

        // Ice is decided by material, and that material is worn by packed ice and by a good deal of
        // a pack's decorative frost - blocks as solid to look at as stone. A stand-in that called
        // one of those clear left its neighbours drawing faces nobody can see, sorted it with the
        // glass every frame, and let daylight in through a block that should have stopped it. So
        // the clear family gets a second set, chosen by what the covered block says about itself.
        ghostIceSolid = create(SurfaceFamily.ICE, false, false, false, false, "ghost_ice_solid");
        ghostIceSolidSunken = create(SurfaceFamily.ICE, false, false, true, false, "ghost_ice_solid_sunken");
        ghostIceSolidStair = createStair(SurfaceFamily.ICE, false, "ghost_ice_solid_stair");

        // And a see-through twin apiece, mirroring the four groups above exactly. Nothing reaches
        // one of these unless a block's own texture is cut away AND whatever the config names behind
        // it has transparency in it - which today means Chisel's waterstone and nothing else, lava
        // having none at all. Registered whatever any of that says, for the same reason the hollows
        // and the stairs are: the names in a save must not depend on a setting.
        //
        // The clear ice stand-ins are left out of all three loops. They already draw in the blended
        // pass and already decline to be opaque cubes, so they are their own window, and the lookup
        // falls back to them rather than to a twin that would be identical.
        ghostGrassWindow = create(SurfaceFamily.GRASS, true, false, false, false, true, "ghost_grass_window");
        ghostGrassPlainWindow = create(
            SurfaceFamily.GRASS,
            false,
            false,
            false,
            false,
            true,
            "ghost_grass_plain_window");
        WINDOWS.put(SurfaceFamily.GRASS, ghostGrassPlainWindow);

        for (SurfaceFamily family : SurfaceFamily.values()) {
            if (!family.staged || family == SurfaceFamily.GRASS || family == SurfaceFamily.ICE) continue;
            WINDOWS.put(family, create(family, false, false, false, false, true, "ghost_" + family.key() + "_window"));
        }
        for (SurfaceFamily family : SurfaceFamily.values()) {
            if (!family.staged || family == SurfaceFamily.ICE) continue;
            boolean untinted = family == SurfaceFamily.GRASS;
            WINDOWS_SUNKEN.put(
                family,
                create(family, false, untinted, true, false, true, "ghost_" + family.key() + "_window_sunken"));
        }
        for (SurfaceFamily family : SurfaceFamily.values()) {
            if (!family.staged || family == SurfaceFamily.ICE) continue;
            WINDOWS_STAIRS.put(family, createStair(family, false, true, "ghost_" + family.key() + "_window_stair"));
        }

        ghostIceSolidWindow = create(SurfaceFamily.ICE, false, false, false, false, true, "ghost_ice_solid_window");
        ghostIceSolidWindowSunken = create(
            SurfaceFamily.ICE,
            false,
            false,
            true,
            false,
            true,
            "ghost_ice_solid_window_sunken");
        ghostIceSolidWindowStair = createStair(SurfaceFamily.ICE, false, true, "ghost_ice_solid_window_stair");
    }

    private static Block createStair(SurfaceFamily appearance, String name) {
        return createStair(appearance, GhostLogic.seeThrough(appearance), name);
    }

    private static Block createStair(SurfaceFamily appearance, boolean clear, String name) {
        return createStair(appearance, clear, false, name);
    }

    private static Block createStair(SurfaceFamily appearance, boolean clear, boolean window, String name) {
        Block block = new BlockGhostStairs(appearance, clear, window);
        block.setBlockName(Trmt.MODID + "." + name);
        block.setBlockTextureName(Trmt.MODID + ":" + name);
        GameRegistry.registerBlock(block, null, name);
        ALL.add(block);
        return block;
    }

    private static Block create(SurfaceFamily appearance, boolean mimicVanillaGrassTop, boolean untinted,
        boolean sunken, String name) {
        return create(appearance, mimicVanillaGrassTop, untinted, sunken, GhostLogic.seeThrough(appearance), name);
    }

    private static Block create(SurfaceFamily appearance, boolean mimicVanillaGrassTop, boolean untinted,
        boolean sunken, boolean clear, String name) {
        return create(appearance, mimicVanillaGrassTop, untinted, sunken, clear, false, name);
    }

    private static Block create(SurfaceFamily appearance, boolean mimicVanillaGrassTop, boolean untinted,
        boolean sunken, boolean clear, boolean window, String name) {
        // Grass that is still green extends BlockGrass, and nothing else does. That
        // inheritance is what map mods read to decide a block takes a biome tint, and it is why
        // worn sand and stone drew as flat grey until the two were separated. See GhostBlock.
        //
        // Untinted grass is excluded for the same reason it was included: by the time the tint
        // is off, what is showing is earth. It kept the inheritance and therefore kept the flag,
        // so a map painted it the flat grey it paints vanilla grass - except that grey is
        // supposed to be multiplied by a biome color, and an untinted ghost reports white. The
        // inheritance was buying it nothing anyway: MixinGrassTint returns early on exactly this
        // case, so no grass treatment was ever being claimed.
        boolean stillGreen = appearance == SurfaceFamily.GRASS && !untinted;
        Block block = stillGreen ? new BlockGhostGrass(appearance, mimicVanillaGrassTop, untinted, sunken, window)
            : new BlockGhost(appearance, mimicVanillaGrassTop, untinted, sunken, clear, window);
        block.setBlockName(Trmt.MODID + "." + name);
        block.setBlockTextureName(Trmt.MODID + ":" + name);
        // A null item class means no ItemBlock: there is no way to obtain or place one.
        GameRegistry.registerBlock(block, null, name);
        ALL.add(block);
        return block;
    }

    /**
     * The block to paint at a position.
     *
     * @param appearance          what the position should look like
     * @param usesVanillaGrassTop whether the block being covered reports vanilla's grass top
     *                            texture, which is what decides how its side faces get shaded
     * @param deep                whether this grass stage has worn past taking a biome tint
     */
    public static Block forAppearance(SurfaceFamily appearance, boolean usesVanillaGrassTop, boolean deep,
        boolean sunken) {
        return forAppearance(appearance, usesVanillaGrassTop, deep, sunken, SurfaceShape.FULL);
    }

    /**
     * The block to paint at a position, for ground that is not a plain cube.
     *
     * @param shape what the block being covered is shaped like, which decides whether a stand-in
     *              that states its own geometry is needed instead of one that takes a height
     */
    public static Block forAppearance(SurfaceFamily appearance, boolean usesVanillaGrassTop, boolean deep,
        boolean sunken, SurfaceShape shape) {
        if (shape == SurfaceShape.STAIR) {
            Block stair = STAIRS.get(appearance);
            if (stair != null) return stair;
        }
        if (sunken) {
            Block hollow = SUNKEN.get(appearance);
            if (hollow != null) return hollow;
        }
        if (appearance == SurfaceFamily.GRASS) {
            if (deep) return ghostGrassDeep;
            return usesVanillaGrassTop ? ghostGrassVanilla : ghostGrassPlain;
        }
        return GHOSTS.get(appearance);
    }

    /**
     * The block to paint at a position, for ground whose family may or may not be seen through.
     *
     * @param opaqueBase whether the block being covered fills its square to look at, which only a
     *                   clear family can answer either way
     */
    public static Block forAppearance(SurfaceFamily appearance, boolean usesVanillaGrassTop, boolean deep,
        boolean sunken, SurfaceShape shape, boolean opaqueBase) {
        if (opaqueBase && appearance == SurfaceFamily.ICE) {
            if (shape == SurfaceShape.STAIR) return ghostIceSolidStair;
            return sunken ? ghostIceSolidSunken : ghostIceSolid;
        }
        return forAppearance(appearance, usesVanillaGrassTop, deep, sunken, shape);
    }

    /**
     * The block to paint at a position, for ground that may be drawn through.
     *
     * @param window whether the covered block has something see-through behind its own cut-away
     *               texture, and the player has asked for it to be shown
     */
    public static Block forAppearance(SurfaceFamily appearance, boolean usesVanillaGrassTop, boolean deep,
        boolean sunken, SurfaceShape shape, boolean opaqueBase, boolean window) {
        if (!window) return forAppearance(appearance, usesVanillaGrassTop, deep, sunken, shape, opaqueBase);

        if (opaqueBase && appearance == SurfaceFamily.ICE) {
            if (shape == SurfaceShape.STAIR) return ghostIceSolidWindowStair;
            return sunken ? ghostIceSolidWindowSunken : ghostIceSolidWindow;
        }
        if (shape == SurfaceShape.STAIR) {
            Block stair = WINDOWS_STAIRS.get(appearance);
            if (stair != null) return stair;
        }
        if (sunken) {
            Block hollow = WINDOWS_SUNKEN.get(appearance);
            if (hollow != null) return hollow;
        }
        if (appearance == SurfaceFamily.GRASS && usesVanillaGrassTop && ghostGrassWindow != null) {
            return ghostGrassWindow;
        }
        Block plain = WINDOWS.get(appearance);
        if (plain != null) return plain;
        // A family with no twin - which today means only the clear ice, whose ordinary stand-in is
        // already drawn through. Falling back rather than refusing keeps the picture right.
        return forAppearance(appearance, usesVanillaGrassTop, deep, sunken, shape, opaqueBase);
    }

    /** True when this block is one of ours, i.e. already painted. */
    public static boolean isGhost(Block block) {
        return block instanceof GhostBlock;
    }
}
