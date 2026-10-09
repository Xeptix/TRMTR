package com.trmtgtnh.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.client.texture.WearTextures;
import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.erosion.SinkProfile;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceShape;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Everything the two ghost classes do, held once.
 *
 * <p>
 * {@link BlockGhost} and {@link BlockGhostGrass} have to be separate classes — see
 * {@link GhostBlock} for why — but they must behave identically, so neither holds logic. Each
 * override in those classes is a single line into this one. The bodies here were moved out of
 * the old single class unchanged; the only edits were to take the block as an argument rather
 * than being {@code this}, and to hand back a sentinel where the original called {@code super}.
 */
final class GhostLogic {

    /**
     * How far a fully worn surface darkens on a map, over its whole run rather than one layer.
     *
     * <p>
     * Half, and it is spread across every gradation the ground has - eighty of them for most
     * families, so each step is worth about six tenths of a per cent. That is deliberately too
     * small to see on its own and exactly the point: the map is the one place where wear should
     * read as an accumulation rather than as an event, so that a road two pixels deep is visibly
     * darker than one that has only just started rather than identical to it.
     *
     * <p>
     * It used to be a per-layer number, which meant the tint climbed for sixteen gradations and
     * then jumped back to nothing the moment the ground sank a pixel and started the next layer.
     * A well-used road looked exactly like fresh ground about a fifth of the time. The old
     * darkest shade - the end of a layer - is roughly where the whole run now ends up, so nothing
     * on the map has become darker than it ever was; the darkness has simply stopped resetting.
     */
    static final float MAP_DARKENING = 0.62f;

    /**
     * The same idea for everything that only knows the layer, which is a shallower curve.
     *
     * <p>
     * The in-world sprite and a vanilla map item both fall here, and neither can be told where
     * the ground sits along its whole run - the sprite because its tint rides the block's own
     * metadata, and the map item because it asks the block a question with no position in it.
     * They keep the number they always had, so the in-world look does not move on the way past.
     */
    static final float LAYER_DARKENING = 0.45f;

    private GhostLogic() {}

    // ------------------------------------------------------------------
    // Fixed properties of a family
    // ------------------------------------------------------------------

    static Material materialFor(SurfaceFamily family) {
        switch (family) {
            case GRASS:
                return Material.grass;
            case SAND:
            case GRAVEL:
                return Material.sand;
            case STONE:
            case COBBLE:
            case NETHER:
            case END:
                return Material.rock;
            case SNOW:
                return Material.craftedSnow;
            case ICE:
                return Material.ice;
            default:
                return Material.ground;
        }
    }

    static float hardnessFor(SurfaceFamily family) {
        switch (family) {
            case GRASS:
                return 0.6f;
            case GRAVEL:
                return 0.6f;
            case STONE:
                return 1.5f;
            case COBBLE:
                return 2.0f;
            case NETHER:
                // Netherrack's own, which is famously soft to dig even though it barely wears.
                return 0.4f;
            case END:
                return 3.0f;
            case SNOW:
                return 0.2f;
            case ICE:
                return 0.5f;
            default:
                return 0.5f;
        }
    }

    static float resistanceFor(SurfaceFamily family) {
        return isStony(family) ? 10.0f : 0f;
    }

    static Block.SoundType stepSoundFor(SurfaceFamily family) {
        switch (family) {
            case GRASS:
                return Block.soundTypeGrass;
            case SAND:
                return Block.soundTypeSand;
            case GRAVEL:
                return Block.soundTypeGravel;
            case STONE:
            case COBBLE:
            case NETHER:
            case END:
                return Block.soundTypeStone;
            case SNOW:
                return Block.soundTypeSnow;
            case ICE:
                return Block.soundTypeGlass;
            default:
                return Block.soundTypeGravel;
        }
    }

    /**
     * How slippery a surface of this family is underfoot.
     *
     * <p>
     * Ice, and nothing else. A ghost inherits Block's own 0.6 otherwise, which is right for every
     * family made of earth or rock; it is wrong for ice, where the server still holds real ice at
     * 0.98 and a client that thought otherwise would disagree with it about where a sliding player
     * ends up.
     *
     * <p>
     * Per family rather than per position, and unlike everything else inherited here that is not a
     * choice: slipperiness is a plain public field that callers read straight off the block, so
     * there is no call to intercept and no position to intercept it at. One block instance serves
     * every worn position in the world.
     *
     * <p>
     * Which makes this the one answer in this class that is knowingly wrong somewhere. The family
     * is decided by material, and the ice material is used by a fair amount of a pack's scenery
     * that is not ice underfoot - Chisel's cloud is the plain case, and it never touches its own
     * slipperiness, so the server slides a player across it at 0.6 while a worn one gives them
     * 0.98. It is written down here rather than quietly left because the fix is not a better guess:
     * it is either a stand-in per distinct value, which is unbounded and costs a block id apiece, or
     * a version of the game whose blocks are asked this at a position.
     */
    static float slipperinessFor(SurfaceFamily family) {
        return family == SurfaceFamily.ICE ? 0.98F : 0.6F;
    }

    static boolean isStony(SurfaceFamily family) {
        return family == SurfaceFamily.STONE || family == SurfaceFamily.COBBLE
            || family == SurfaceFamily.NETHER
            || family == SurfaceFamily.END;
    }

    static String harvestToolFor(SurfaceFamily family) {
        if (family == SurfaceFamily.ICE) return "pickaxe";
        return isStony(family) ? "pickaxe" : "shovel";
    }

    /**
     * True for a family that may be seen through, which changes how the ghost has to be drawn.
     *
     * <p>
     * Ice is the only one, and it is the first surface here that is not solid to look at. A
     * ghost that reported itself opaque over ice would have its neighbours stop drawing the
     * faces the ice was showing them, and the world behind a frozen lake would simply go.
     *
     * <p>
     * May rather than is, and that is the whole of what the solid twins registered beside these
     * exist for. The family is decided by material, and the ice material covers packed ice and a
     * good deal of a pack's decorative frost besides - blocks that fill their square to look at as
     * completely as stone does. Answering clear for one of those is the mirror of the fault above
     * and costs rather more: its neighbours go on drawing faces nobody can see, it is sorted with
     * the glass every frame, and daylight walks in through a block that should have stopped it. So
     * this settles what a family may be, and {@code ModBlocks} keeps a stand-in for each answer.
     */
    static boolean seeThrough(SurfaceFamily family) {
        return family == SurfaceFamily.ICE;
    }

    /** How much light a covered position stops. Full, except where the surface is clear. */
    static int lightOpacityFor(SurfaceFamily family) {
        // Ice's own figure. Anything higher and a frozen lake would darken the water beneath it
        // the moment somebody walked across.
        return family == SurfaceFamily.ICE ? 3 : 255;
    }

    /** The vanilla block this appearance stands in for, used for map color and fallbacks. */
    static Block vanillaCounterpart(SurfaceFamily appearance) {
        switch (appearance) {
            case GRASS:
                return Blocks.grass;
            case SAND:
                return Blocks.sand;
            case GRAVEL:
                return Blocks.gravel;
            case STONE:
                return Blocks.stone;
            case COBBLE:
                return Blocks.cobblestone;
            case NETHER:
                return Blocks.netherrack;
            case END:
                return Blocks.end_stone;
            case SNOW:
                return Blocks.snow;
            case ICE:
                return Blocks.ice;
            default:
                return Blocks.dirt;
        }
    }

    /**
     * Two colors mixed channel by channel, part of the way from the first toward the second.
     *
     * <p>
     * Rounded exactly the way {@link #darkenBy} rounds, so a color that goes through both comes
     * out the same whichever order they are applied in.
     */
    public static int blendToward(int from, int to, float amount) {
        float mix = amount < 0f ? 0f : (amount > 1f ? 1f : amount);
        if (mix <= 0f) return from;
        int result = 0;
        for (int channel = 0; channel < 3; channel++) {
            int shift = 16 - channel * 8;
            int start = (from >>> shift) & 0xFF;
            int end = (to >>> shift) & 0xFF;
            int value = Math.round(start + (end - start) * mix);
            if (value < 0) value = 0;
            if (value > 255) value = 255;
            result |= value << shift;
        }
        return result;
    }

    /**
     * The map color of a ghost that is standing in for something, told whether it is tinted.
     *
     * <p>
     * The untinted grass variants draw bare earth rather than turf - that is what untinted means
     * here - and reporting turf's color for them was the map calling a dirt path a lawn. A
     * vanilla map item cannot be given more than this: its palette is sixty-four fixed entries
     * with no darker sibling to choose, so there is no honest way to vary it by how worn a square
     * is. Anything finer than this belongs to a map mod that asks per position.
     */
    static MapColor mapColor(SurfaceFamily appearance, boolean untinted) {
        if (untinted && appearance == SurfaceFamily.GRASS) {
            return net.minecraft.init.Blocks.dirt.getMapColor(0);
        }
        return mapColor(appearance);
    }

    /**
     * A color pulled toward the desire-path highlight, for how worn the position is.
     *
     * <p>
     * Applied after the darkening rather than instead of it, so the two readings compose: at half
     * strength a map still says which material a square is and says on top of that how much traffic
     * it carries. At full strength the material reading is gone from worn ground, which is the trade
     * the setting exists to offer rather than a shortcoming of it.
     *
     * <p>
     * Scaled by the square root of the wear, which is deliberately not the straight line the
     * darkening uses. Darkening answers "how deep is this rut", and a straight line is honest for
     * that. This answers "does anybody walk here", where the interesting case is a route somebody
     * has just started using - and on a straight line a tenth-worn square is a tenth of the way to
     * the color, which is nothing anybody can see. Nought wear still gives nought pull, so ground
     * nobody has touched comes back untouched whatever this is set to.
     */
    public static int highlightBy(int rgb, float worn) {
        float strength = TrmtConfig.desirePathHighlight;
        if (strength <= 0f) return rgb;
        float clamped = worn < 0f ? 0f : (worn > 1f ? 1f : worn);
        if (clamped <= 0f) return rgb;
        return blendToward(rgb, TrmtConfig.desirePathRgb, (float) Math.sqrt(clamped) * Math.min(strength, 1f));
    }

    /**
     * The color a map draws this family in, when it has nothing finer to go on.
     *
     * <p>
     * Vanilla's own answer for the covered block, except where vanilla's answer names the wrong
     * material. Two do. Gravel is Material.sand, so it reports the pale cream sand color and a
     * worn gravel road draws as a bright streak across grey ground; end stone is Material.rock, so
     * it reports grey and a worn end road draws as a dark band across a pale plain. In both cases
     * the mistake is not that the color is ugly but that it reads as a change of MATERIAL rather
     * than a change of condition, which is the one thing a map of worn ground must not say.
     *
     * <p>
     * This is the answer far more maps use than it looks. A map that averages a block's texture
     * reads that texture off disk by name, and this mod's wear sprites are generated at stitch time
     * and have no file - so the lookup misses and the map falls back here. Xaero's is the one that
     * was measured, and it lands here on every ghost, in both of its color modes.
     */
    static MapColor mapColor(SurfaceFamily appearance) {
        // Keeps minimaps, JourneyMap and Distant Horizons showing a path as the ground it is
        // rather than as an unknown block.
        if (appearance == SurfaceFamily.GRAVEL) return MapColor.stoneColor;
        if (appearance == SurfaceFamily.END) return MapColor.sandColor;
        return vanillaCounterpart(appearance).getMapColor(0);
    }

    /**
     * The family's fallback map color as a plain 0xRRGGBB int.
     *
     * <p>
     * Used by the JourneyMap handler when a position has no recorded origin - the same
     * family-generic answer {@link #mapColor} gives, in the form the handler needs.
     */
    public static int fallbackMapColor(SurfaceFamily appearance) {
        MapColor color = mapColor(appearance);
        return color == null ? 0x7F7F7F : color.colorValue;
    }

    // ------------------------------------------------------------------
    // Color
    // ------------------------------------------------------------------

    /**
     * No tint of its own, except on grass.
     *
     * <p>
     * This is a multiplier over the block's texture. The texture a worn block reports is
     * already the worn one — it carries how far along the wear is in its own pixels — so a
     * darkening factor here as well would count that twice for everything whose color is in
     * those pixels.
     *
     * <p>
     * This is not dead code, and a comment here used to say it was. JourneyMap reaches it
     * through {@code VanillaColorHandler.loadTextureColor} for every metadata above zero,
     * because a ghost has no item form and so only its meta-0 entry is ever written to the
     * color palette. For stages one and up this is what decides the color drawn on the map.
     */
    @SideOnly(Side.CLIENT)
    static int renderColor(SurfaceFamily appearance, int meta, boolean untinted) {
        int base = 0xFFFFFF;
        // Untinted means the cover has worn off and what is showing is the earth underneath.
        // Earth is brown in its own pixels and wants no multiplier; telling a map it is grass
        // green would paint bare ground the color of a lawn.
        if (appearance == SurfaceFamily.GRASS && !untinted) {
            // Grass has to say what color grass is, because its texture does not: vanilla's
            // grass_top is grey and only becomes grass once a biome tint runs through it. A map
            // averaging that texture drew every worn patch of turf as grey stone.
            base = 0x91BD59;
            try {
                int own = Blocks.grass.getBlockColor();
                if (own != 0xFFFFFF && own != 0) base = own;
            } catch (RuntimeException awkwardBlock) {
                // Keeps the vanilla figure.
            }
        }

        return darkenForWear(base, appearance, meta);
    }

    /**
     * A color dimmed for how far along its wear this stage is.
     *
     * <p>
     * Shared by {@link #renderColor} and the JourneyMap handler so a worn block reads as worn by
     * the same curve wherever its color is decided - the map has no wear texture to carry the
     * darkening in its pixels the way the in-world sprite does, so it has to be applied to the
     * color instead. Full brightness at the first stage, easing to {@code MAP_DARKENING} off it
     * at the last.
     */
    /**
     * A color dimmed by an already-worked-out fraction of the whole run.
     *
     * <p>
     * The map path knows where a position sits along its entire chain, because it can see the
     * sink as well as the layer. Everything else only has the layer, and takes the overload below.
     */
    public static int darkenBy(int rgb, float worn) {
        // Read straight rather than through a nought-means-default sentinel, which is what
        // stood here: a pack setting this to nought meant no darkening and silently got the
        // default instead. A setting that quietly means something other than what it says is
        // the same fault this mod keeps finding, pointing the other way.
        float depth = TrmtConfig.mapWearDarkening;
        float keep = 1f - Math.min(Math.max(worn, 0f), 1f) * Math.min(Math.max(depth, 0f), 0.9f);
        int result = 0;
        for (int channel = 0; channel < 3; channel++) {
            int shift = 16 - channel * 8;
            int value = Math.round(((rgb >>> shift) & 0xFF) * keep);
            if (value < 0) value = 0;
            if (value > 255) value = 255;
            result |= value << shift;
        }
        return result;
    }

    public static int darkenForWear(int rgb, SurfaceFamily appearance, int meta) {
        FamilySettings settings = TrmtConfig.family(appearance);
        int layers = settings == null ? 1 : Math.max(settings.stages, 1);
        float worn = layers <= 1 ? 0f : Math.min(Math.max(meta, 0), layers - 1) / (float) (layers - 1);
        float keep = 1f - worn * LAYER_DARKENING;

        int result = 0;
        for (int channel = 0; channel < 3; channel++) {
            int shift = 16 - channel * 8;
            int value = Math.round(((rgb >>> shift) & 0xFF) * keep);
            if (value < 0) value = 0;
            if (value > 255) value = 255;
            result |= value << shift;
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Behaviour: mirror the covered block so nothing the client predicts diverges
    // ------------------------------------------------------------------

    /**
     * The stack a middle-click should produce, or null to let the block answer for itself.
     *
     * <p>
     * Null rather than a fallback because the fallback is {@code super.getPickBlock}, and
     * {@code super} means nothing here. Each block class applies its own.
     */
    static ItemStack pickBlock(GhostBlock ghost, int x, int y, int z) {
        int packed = Trmt.proxy.originPackedAt(x, y, z);
        if (packed >= 0) {
            Block origin = Block.getBlockById(packed >> 4);
            if (origin != null && origin != ghost.asBlock()) {
                Item item = Item.getItemFromBlock(origin);
                // The metadata here is our wear stage, not the covered block's, so the stack is
                // built from the recorded original rather than from the world.
                if (item != null) return new ItemStack(item, 1, origin.damageDropped(packed & 0xF));
            }
        }
        return null;
    }

    /**
     * The covered block's mining hardness, or {@link Float#NaN} to let the block answer for
     * itself. Mining speed is predicted client-side; anything but the covered block's own
     * hardness makes a block appear to break early and then snap back.
     */
    static float blockHardness(GhostBlock ghost, World world, int x, int y, int z) {
        Block origin = originBlock(world, x, y, z);
        if (origin != null && origin != ghost.asBlock()) return origin.getBlockHardness(world, x, y, z);
        return Float.NaN;
    }

    /**
     * How fast this player breaks the covered block, or {@link Float#NaN} to let the block answer.
     *
     * <p>
     * The hardness above was only half the sum. Forge works a break out as the player's speed
     * against the tool, divided by the hardness, divided again by thirty where the tool can harvest
     * the block and by one hundred where it cannot - and that last question was being asked of the
     * ghost rather than of the ground. A ghost is registered with no harvest tool and level nought,
     * so every worn block answered "yes, you can harvest this" whatever was underneath it.
     *
     * <p>
     * Hardness delegating correctly is what makes the mismatch so easy to miss: the numerator is
     * right, so ordinary stone and earth come out exactly right, and nothing looks wrong until the
     * ground is something a pack gates behind a better pickaxe. There the client divides by thirty
     * and the server by a hundred, so worn black granite breaks better than three times too fast,
     * vanishes, and is put straight back by the server. A tooltip mod showing the harvest state
     * reads it off the same ghost and agrees with the client, which is what makes it convincing.
     *
     * <p>
     * Asked of the recorded original's own metadata rather than of the world's, for the same reason
     * the pick stack is: the metadata at this position is the wear stage. And worked out here rather
     * than by handing the whole question to the covered block, because that would read the world's
     * metadata again and arrive back at the wear stage by another route.
     */
    static float breakSpeed(GhostBlock ghost, World world, EntityPlayer player, int x, int y, int z) {
        if (world == null || !world.isRemote || player == null) return Float.NaN;
        int packed = Trmt.proxy.originPackedAt(x, y, z);
        if (packed < 0) return Float.NaN;
        Block origin = Block.getBlockById(packed >> 4);
        if (origin == null || origin == ghost.asBlock()) return Float.NaN;

        float hardness = origin.getBlockHardness(world, x, y, z);
        // Unbreakable underneath means unbreakable here. Nought rather than a negative, because
        // this is a rate and the caller divides by nothing.
        if (hardness < 0f) return 0f;

        int originMeta = packed & 0xF;
        boolean canHarvest = net.minecraftforge.common.ForgeHooks.canHarvestBlock(origin, player, originMeta);
        return player.getBreakSpeed(origin, canHarvest, originMeta, x, y, z) / hardness / (canHarvest ? 30f : 100f);
    }

    /** The block this ghost is standing in for at a position, or null if unknown. */
    /** How deep this thread is in answering for a covered block, so a question cannot loop. */
    private static final ThreadLocal<int[]> ANSWERING = new ThreadLocal<int[]>() {

        @Override
        protected int[] initialValue() {
            return new int[1];
        }
    };

    /**
     * Whether the covered block would hold this plant, or null to let the ghost answer for itself.
     *
     * <p>
     * Forge decides what a plant may stand on by asking the block beneath whether it is grass, or
     * dirt, or sand - by identity, not by kind - and a ghost is none of those whatever it is drawn
     * as. The client asks before it sends a placement at all and sends nothing on a no, so a
     * sapling, a flower or a cactus could not be planted on any worn square, although the server,
     * which still holds the real ground, would have taken it. A plant already standing there asks
     * the same thing when a block beside it changes, and on a no the client takes it away.
     *
     * <p>
     * Asked of the covered block through a view that reports its own metadata here, for the reason
     * every other question a ghost passes on is: the metadata in the world is the wear stage.
     */
    static Boolean sustainsPlant(GhostBlock ghost, net.minecraft.world.IBlockAccess world, int x, int y, int z,
        net.minecraftforge.common.util.ForgeDirection direction, net.minecraftforge.common.IPlantable plantable) {
        Block origin = coveredAt(ghost, x, y, z);
        if (origin == null) return null;
        int[] depth = ANSWERING.get();
        if (depth[0] > 0) return null;
        depth[0]++;
        try {
            return Boolean.valueOf(
                origin.canSustainPlant(
                    new OriginView(world, x, y, z, origin, originMetaAt(x, y, z)),
                    x,
                    y,
                    z,
                    direction,
                    plantable));
        } catch (RuntimeException awkwardBlock) {
            return null;
        } finally {
            depth[0]--;
        }
    }

    /**
     * Whether a face of the covered block is solid, or null to let the ghost answer for itself.
     *
     * <p>
     * A sunken ghost is drawn as less than a whole block and so reports no solid face of its own,
     * while the server still holds the whole block. The client asks this before placing a torch, a
     * rail, a pressure plate or a lever against a face, and sends nothing on a no - so a worn road
     * could not be lit once it had sunk a single pixel. A worn stair answered from its wear stage
     * rather than its facing, which put the solid back of it in a different place every four
     * gradations.
     *
     * <p>
     * Guarded against asking itself: the view passes a solid-face question about any position
     * through to the world, and a covered block that asks the world about its own position would
     * otherwise arrive straight back here.
     */
    static Boolean sideSolid(GhostBlock ghost, net.minecraft.world.IBlockAccess world, int x, int y, int z,
        net.minecraftforge.common.util.ForgeDirection side) {
        Block origin = coveredAt(ghost, x, y, z);
        if (origin == null) return null;
        int[] depth = ANSWERING.get();
        if (depth[0] > 0) return null;
        depth[0]++;
        try {
            return Boolean.valueOf(
                origin.isSideSolid(new OriginView(world, x, y, z, origin, originMetaAt(x, y, z)), x, y, z, side));
        } catch (RuntimeException awkwardBlock) {
            return null;
        } finally {
            depth[0]--;
        }
    }

    /** The real block under a ghost at a position, or null when nothing trustworthy is recorded. */
    private static Block coveredAt(GhostBlock ghost, int x, int y, int z) {
        int packed = Trmt.proxy.originPackedAt(x, y, z);
        if (packed < 0) return null;
        Block origin = Block.getBlockById(packed >> 4);
        if (origin == null || origin == ghost.asBlock() || origin instanceof GhostBlock) return null;
        return origin;
    }

    static Block originBlock(World world, int x, int y, int z) {
        if (world == null || !world.isRemote) return null;
        return Trmt.proxy.originBlockAt(x, y, z);
    }

    // ------------------------------------------------------------------
    // Shape
    // ------------------------------------------------------------------

    /**
     * The rendered height at a position: the covered block's own height, less however far this
     * stage has worn down.
     *
     * <p>
     * The covered block's height matters because not everything worth wearing is a full cube. A
     * grass path already stands a pixel short, and a ghost drawn at full height over one would
     * make the path visibly grow the moment it started wearing.
     */
    /**
     * The height the ground here can be stood on, for anything resting on it - which is not always the
     * height it is drawn at.
     *
     * <p>
     * On a slab the two part company in visual mode, where the rut is a picture and the server still
     * holds a block of full height: this takes the collision depth and the drawn one does not. On
     * anything else heightAt takes the drawn depth for both, so in visual mode snow and carpet resting
     * on a worn whole block come down into the drawn rut. That is the rule - Xep's, on 2026-10-08, when
     * this comment was found saying otherwise; the ports follow it.
     */
    static double collidedHeightAt(GhostBlock ghost, net.minecraft.world.IBlockAccess world, int x, int y, int z) {
        return heightAt(ghost, world, x, y, z, true);
    }

    static double heightAt(GhostBlock ghost, net.minecraft.world.IBlockAccess world, int x, int y, int z) {
        return heightAt(ghost, world, x, y, z, false);
    }

    private static double heightAt(GhostBlock ghost, net.minecraft.world.IBlockAccess world, int x, int y, int z,
        boolean footing) {
        SurfaceShape shape = shapeAt(x, y, z);
        if (shape.isPartial()) {
            // A shaped surface is worn down from its own top rather than down towards an absolute
            // height: a slab resting on the floor starts at half a block, so the same eight-pixel
            // rut measured from zero would leave nothing of it at all. It is also why the depth
            // itself is halved - see SinkProfile - and why the floor here is the bottom of the
            // space the block actually occupies rather than the bottom of its cell.
            int meta = originMetaAt(x, y, z);
            double top = SurfaceShape.topOf(shape, meta);
            if (!ghost.isSunken()) return top;
            double floor = SurfaceShape.bottomOf(shape, meta);
            int sunk = footing ? SinkProfile.collides(ghost.appearance(), heldSink(world, x, y, z), shape)
                : SinkProfile.shown(ghost.appearance(), heldSink(world, x, y, z), shape);
            return Math.max(floor, top - sunk / 16.0D);
        }

        double base = originTop(ghost, x, y, z);
        if (!ghost.isSunken()) return base;
        // The drawn depth whether or not this is footing - see collidedHeightAt. Deliberate since
        // 2026-10-08, when it was found and Xep chose it over the comment that said otherwise.
        return Math.min(base, SinkProfile.heightFor(SinkProfile.shown(ghost.appearance(), heldSink(world, x, y, z))));
    }

    /** Where the space this ghost stands in begins, which is only ever above zero for a slab. */
    static double bottomAt(int x, int y, int z) {
        SurfaceShape shape = shapeAt(x, y, z);
        return shape.isPartial() ? SurfaceShape.bottomOf(shape, originMetaAt(x, y, z)) : 0.0D;
    }

    /** What shape the block being covered here stands in. */
    static SurfaceShape shapeAt(int x, int y, int z) {
        return SurfaceShape.of(Trmt.proxy.originBlockAt(x, y, z));
    }

    /**
     * The metadata the covered block had when it was painted over.
     *
     * <p>
     * Kept in the overlay beside the block id for exactly this: a slab's half and a stair's facing
     * live in metadata, and a ghost's own metadata is the wear gradation and has no room for
     * anything else.
     */
    static int originMetaAt(int x, int y, int z) {
        int packed = Trmt.proxy.originPackedAt(x, y, z);
        return packed < 0 ? 0 : packed & 0xF;
    }

    /**
     * How tall the covered block stands, or 1 when that cannot be established.
     *
     * <p>
     * Read rather than recomputed: these bounds are shared mutable state, and the blocks this
     * matters for — paths, and earth cut short — set theirs once and never move them again.
     */
    static double originTop(GhostBlock ghost, int x, int y, int z) {
        Block origin = Trmt.proxy.originBlockAt(x, y, z);
        if (origin != null && origin != ghost.asBlock()) {
            double top = origin.getBlockBoundsMaxY();
            if (top > 0.0D && top <= 1.0D) return top;
        }
        return 1.0D;
    }

    /**
     * How far the ground at this position has dropped.
     *
     * <p>
     * From the record rather than from the block's metadata, which holds the visual layer and
     * has no room for anything else. Reading the record also means the shape a player collides
     * with and the shape they see come from one number: deriving them separately is how you end
     * up standing inside ground that looks solid.
     */
    static int storedSink(int x, int y, int z) {
        return ErosionState.sinkOf(Trmt.proxy.erosionStateAt(x, y, z));
    }

    /**
     * How far a position has dropped for anything standing or resting on it: nothing while a plant holds it.
     *
     * <p>
     * The server stands people on held ground at full height - its collision reads the hold before the
     * record - while the client read the record alone. A sapling planted on a worn dirt road was drawn
     * flat and walked through half a block deep, and the server put the player straight back up every
     * tick. Asked through the view of the world the caller already has, so a mesher's view of one chunk
     * answers for itself.
     */
    static int heldSink(net.minecraft.world.IBlockAccess world, int x, int y, int z) {
        int sink = storedSink(x, y, z);
        // The record first. Most of a run has not sunk at all, and asking after a plant costs two chunk
        // reads and a registry lookup on the collision path, for an answer that can only turn a sink into
        // none.
        if (sink == 0 || world == null) return sink;
        return com.trmtgtnh.erosion.GroundCover.holdsAt(world, x, y, z) ? 0 : sink;
    }

    /**
     * The outline drawn round the block you are looking at, which is never nothing.
     *
     * <p>
     * Collision may be nothing - a block you can walk through says so by handing back no box at
     * all, and a ghost standing in for one has to say the same or it would turn a cloud into a
     * floor. An outline may not: the renderer that draws it takes what it is given and expands it
     * without ever asking whether it is there, so a stand-in that answered nothing crashed the game
     * the moment somebody looked at one. That is a vanilla method with a vanilla contract, and this
     * mod is the one that broke it.
     *
     * <p>
     * What is drawn instead is the covered block's own outline, which is the honest answer: it is
     * the shape that is really at that position, and it is what a player was pointing at. Failing
     * that, the whole cell, because an outline in the wrong place is a smaller fault than no game.
     */
    static AxisAlignedBB outlineBox(SurfaceFamily appearance, World world, int x, int y, int z) {
        AxisAlignedBB box = solidBox(appearance, world, x, y, z);
        if (box != null) return box;

        Block origin = Trmt.proxy.originBlockAt(x, y, z);
        if (origin != null && !(origin instanceof GhostBlock) && world != null) {
            try {
                AxisAlignedBB own = origin.getSelectedBoundingBoxFromPool(world, x, y, z);
                if (own != null) return own;
            } catch (RuntimeException awkwardBlock) {
                Trmt.LOG.debug("Block {} refused to state its own outline through a ghost", origin, awkwardBlock);
            }
        }
        return AxisAlignedBB.getBoundingBox(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D);
    }

    /**
     * Collision and selection, computed straight from the record rather than from the shared
     * bounds fields.
     *
     * <p>
     * May be null, and only collision may use it that way. Anything drawing an outline goes through
     * {@link #outlineBox} instead.
     *
     * <p>
     * Two reasons not to reuse those fields here. They are written by the mesher on other
     * threads, and a torn read that moved a player would be far worse than one that flickered a
     * texture. And in visual mode the rendered shape and the collision shape are deliberately
     * different: the server still believes this block is full height, so anything shallower
     * would have the server shove the player back out every tick.
     */
    static AxisAlignedBB solidBox(SurfaceFamily appearance, World world, int x, int y, int z) {
        // Asked before anything is measured off the record, because a block narrower than its own
        // square has already answered this question and wear has nothing to add to it. Handing back
        // a full cell over a cloud turned a block you fall through into a block you stand on.
        Block origin = Trmt.proxy.originBlockAt(x, y, z);
        if (origin != null) {
            AxisAlignedBB own = GhostInherit.ownFootingAt(origin, world, x, y, z);
            if (own != GhostInherit.ORDINARY) return own;
        }

        // Off the block just read rather than through shapeAt, which would look the same block up a
        // second time. This is the collision path and the lookup is the dearest thing on it.
        SurfaceShape shape = SurfaceShape.of(origin);
        if (shape.isPartial()) {
            int meta = originMetaAt(x, y, z);
            double floor = SurfaceShape.bottomOf(shape, meta);
            double top = SurfaceShape.topOf(shape, meta);
            double worn = Math
                .max(floor, top - SinkProfile.collides(appearance, heldSink(world, x, y, z), shape) / 16.0D);
            return AxisAlignedBB.getBoundingBox(x, y + floor, z, x + 1.0D, y + worn, z + 1.0D);
        }
        double height = SinkProfile.heightFor(SinkProfile.collides(appearance, heldSink(world, x, y, z)));
        return AxisAlignedBB.getBoundingBox(x, y, z, x + 1.0D, y + height, z + 1.0D);
    }

    // ------------------------------------------------------------------
    // Appearance
    // ------------------------------------------------------------------

    /**
     * The stand-in texture, which doubles as an answer to a question the renderer asks.
     *
     * <p>
     * {@code RenderBlocks} decides whether to tint a block's side faces by comparing the name
     * of its top texture against {@code grass_top}: vanilla grass gets untinted sides plus a
     * separately tinted overlay, everything else gets tinted sides. Only the variant standing
     * in for vanilla grass should claim that name — this one reported it for every grass ghost,
     * which left a modded grass with pale, untinted sides where vanilla would have tinted them.
     */
    static String fallbackTextureName(SurfaceFamily appearance) {
        switch (appearance) {
            case GRASS:
                // Deliberately vanilla's name. Both the stock renderer and the pack's
                // connected-textures one decide whether to tint a block's sides by comparing
                // the name of its top texture against this exact string: match it and the sides
                // are left alone and a separately tinted overlay is drawn over them, which is
                // how grass is meant to look. Report anything else and the biome tint lands on
                // the side texture itself, which turns a worn block's sides dark green.
                return "grass_top";
            case SAND:
                return "sand";
            case GRAVEL:
                return "gravel";
            case STONE:
                return "stone";
            case COBBLE:
                return "cobblestone";
            case NETHER:
                return "netherrack";
            case END:
                return "end_stone";
            case SNOW:
                return "snow";
            case ICE:
                return "ice";
            default:
                return "dirt";
        }
    }

    /**
     * The position-independent icon. Two callers matter: item and particle rendering, and
     * {@code RenderBlocks}, which decides how to shade a block's side faces by asking what
     * this block's top texture is called.
     */
    @SideOnly(Side.CLIENT)
    static IIcon icon(GhostBlock ghost, int side, int meta) {
        if (GhostRendering.PROBE) GhostRendering.probe(
            "icon (position-free) side=" + side + " meta=" + meta + " mimicsGrassTop=" + ghost.mimicsVanillaGrassTop());
        SurfaceFamily appearance = ghost.appearance();
        if (side == 1) {
            // Reporting vanilla's name here is what tells RenderBlocks to treat this block's
            // sides the way it treats grass: untinted, with a separately tinted overlay.
            if (ghost.mimicsVanillaGrassTop()) return Blocks.grass.getIcon(1, 0);

            // The worn face, not a stock one. Nothing in the world asks this - chunk rendering
            // uses the form that knows where it is - but map mods do, and they read it by
            // averaging the pixels of whatever sprite it names. Naming a stock texture is why
            // every path on the map came out the same shade whatever it was made of; naming the
            // worn sprite means a sand path reads as worn sand and gets darker as it wears,
            // because that is what the sprite actually is.
            // Deliberately the unworn one, whatever the metadata says. Map mods average this
            // texture once per block and keep the answer, so it has to be the surface's own
            // color rather than one gradation's; how worn a spot is comes through the render
            // color above, which they apply on top.
            IIcon worn = WearTextures.icon(null, 0, appearance, appearance, 0, 0);
            if (worn != null) return worn;
            return ghost.fallbackIcon() != null ? ghost.fallbackIcon() : Blocks.dirt.getIcon(1, 0);
        }

        if (appearance == SurfaceFamily.GRASS) {
            // Bottom is the earth underneath; the four sides are grass sides.
            //
            // This form takes no position, so it cannot consult what is really being covered,
            // and for a long time it answered "dirt" for all of them. That was very nearly
            // harmless: the position-aware form below is what vanilla asks. But MCPatcherForge's
            // connected-textures renderer, which this pack enables, asks the metadata form for
            // side faces instead — so every worn grass block was showing bare dirt on its sides
            // while sand and dirt, whose answer here is right either way, looked fine.
            return side == 0 ? Blocks.dirt.getIcon(0, 0) : Blocks.grass.getIcon(2, 0);
        }
        return ghost.fallbackIcon() != null ? ghost.fallbackIcon() : Blocks.dirt.getIcon(side, 0);
    }
}
