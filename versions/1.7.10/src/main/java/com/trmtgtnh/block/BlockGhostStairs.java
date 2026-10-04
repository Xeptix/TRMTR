package com.trmtgtnh.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceShape;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The ghost that stands in for a worn stair.
 *
 * <p>
 * It extends {@link BlockStairs} and there is no way round it. A stair is drawn by render type ten,
 * and the renderer's dispatch for that type casts its argument to {@code BlockStairs} before it
 * does anything else, so a block claiming the type without the ancestry would take a chunk build
 * down on the first stair anybody wore.
 *
 * <p>
 * The inheritance brings two problems, and both have the same shape of answer.
 *
 * <p>
 * The first is that everything giving a stair its form reads the facing and the half out of
 * metadata, and a ghost's metadata is its wear gradation - so left alone the stair rules read a
 * gradation as a compass bearing. That is answered in
 * {@link com.trmtgtnh.mixin.MixinStairMetadata} rather than here, and it has to be: the block
 * reading the wrong number is as often the unworn stair <em>next door</em>, which asks about its
 * neighbours through its own copy of the world and cannot be reached from this class at all.
 * Putting the answer at the point of reading covers both directions at once, and is what makes the
 * inherited shape, joining, collision and selection right without a line written for any of them.
 *
 * <p>
 * The second is the long list of things {@code BlockStairs} delegates to its model block, which are
 * the whole point of the class and every one of which is wrong here. The model block is a
 * placeholder chosen only to satisfy the constructor; the block whose behaviour should show through
 * is the one at the position, and that is a different block at every position. Each is overridden
 * to the same {@link GhostLogic} call the rest of the ghost family already uses.
 *
 * <p>
 * A worn stair does not physically sink. A stair fuses its drawn shape and its collision shape into
 * one set of methods, so a dip in the picture would be a dip the server has not got, and the server
 * would spend every tick pushing whoever stood in it back out of ground it believes is solid. What
 * a worn stair shows is the wear itself, which was always the larger half of the effect.
 */
public class BlockGhostStairs extends BlockStairs implements GhostBlock {

    private final Material material;

    private final SurfaceFamily appearance;

    private IIcon fallbackIcon;

    /** True when this variant may be seen through; see {@link GhostLogic#seeThrough}. */
    private final boolean clear;

    /**
     * True when this variant leaves the holes in its picture open rather than filling them in.
     *
     * <p>
     * Which is a fact about how it is drawn rather than about what it is standing in for: the block
     * underneath is solid, the server still holds it, and nothing about where anybody walks changes.
     * What changes is that this one goes into the pass that blends instead of the one that tests,
     * and stops claiming to be an opaque cube so that its neighbours draw the faces it is now
     * showing them.
     */
    private final boolean window;

    public BlockGhostStairs(SurfaceFamily appearance) {
        this(appearance, GhostLogic.seeThrough(appearance));
    }

    public BlockGhostStairs(SurfaceFamily appearance, boolean clear) {
        this(appearance, clear, false);
    }

    public BlockGhostStairs(SurfaceFamily appearance, boolean clear, boolean window) {
        // A placeholder, and nothing but. Everything the superclass would ask it is overridden
        // below, and the four things the constructor took from it are set again immediately.
        super(Blocks.stone, 0);
        this.material = GhostLogic.materialFor(appearance);
        this.appearance = appearance;
        this.clear = clear;
        this.window = window;

        setHardness(GhostLogic.hardnessFor(appearance));
        setResistance(GhostLogic.resistanceFor(appearance));
        setStepSound(GhostLogic.stepSoundFor(appearance));
        // What BlockStairs itself sets, and it has to be. Light opacity is one of the few things
        // both sides work out for themselves, and the server still holds a real stair here - so a
        // ghost that let light through would have the client relight everything around it
        // differently from the server, which reads as faces shaded wrong rather than as a lighting
        // bug. A stair is not a full cube and blocks light like one anyway; that is vanilla's
        // decision, not one this stand-in gets to revisit.
        setLightOpacity(255);
        // Vanilla decides this for its own blocks in a loop at the end of Block.registerBlocks,
        // and that loop runs inside the Minecraft constructor - long before any mod has registered
        // anything. So no modded block is ever visited by it and every one of them keeps the
        // default, false. A stair is one of the shapes the loop exists for: it stops all light and
        // does not fill its cube, so the light stored in its own square is nought, and any face
        // looking at that square is painted with nought. Vanilla stairs are saved by the flag; a
        // stand-in that copies the opacity and not the flag turns the block beside it black.
        //
        // Set here rather than by reaching into the loop, because the loop is a one-shot over a
        // registry that no longer contains only the blocks it was written for.
        useNeighborBrightness = true;
        setHarvestLevel(GhostLogic.harvestToolFor(appearance), 0);
        slipperiness = GhostLogic.slipperinessFor(appearance);
        setTickRandomly(false);
        setCreativeTab(null);
        // No creative tab and no item form: there is no legitimate way to hold one of these.
    }

    // ------------------------------------------------------------------
    // GhostBlock
    // ------------------------------------------------------------------

    @Override
    public boolean isWindow() {
        return window;
    }

    @Override
    public SurfaceFamily appearance() {
        return appearance;
    }

    @Override
    public boolean isUntinted() {
        return false;
    }

    @Override
    public boolean isSunken() {
        // A stair states its own shape and never has a height clamped onto it, which is the only
        // thing this flag is read for.
        return false;
    }

    @Override
    public boolean mimicsVanillaGrassTop() {
        return false;
    }

    @Override
    public SurfaceShape shape() {
        return SurfaceShape.STAIR;
    }

    @Override
    public IIcon fallbackIcon() {
        return fallbackIcon;
    }

    @Override
    public Block asBlock() {
        return this;
    }

    // ------------------------------------------------------------------
    // Everything BlockStairs would otherwise have asked its model block
    // ------------------------------------------------------------------

    @Override
    public Material getMaterial() {
        return material;
    }

    /** Never. A ghost is a picture of worn ground, not ground. */
    @Override
    public void updateTick(World world, int x, int y, int z, Random random) {}

    /**
     * Whatever the block underneath scatters into the air, it goes on scattering.
     *
     * <p>
     * Not the same thing as {@link #updateTick}, which is the world asking a block to do something
     * and is rightly refused: this is the client asking what a block looks like doing nothing.
     */
    @SideOnly(Side.CLIENT)
    @Override
    public void randomDisplayTick(World world, int x, int y, int z, Random random) {
        GhostInherit.ambientTick(this, world, x, y, z, random);
    }

    /**
     * Handed straight back to the block underneath, which is the only one that knows what standing
     * in it is supposed to do to you.
     */
    @Override
    public void onEntityCollidedWithBlock(World world, int x, int y, int z, Entity entity) {
        GhostInherit.entityCollided(this, world, x, y, z, entity);
    }

    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z) {
        int lit = GhostLight.levelAt(world, x, y, z);
        int covered = GhostInherit.glowOf(world, x, y, z);
        return lit > covered ? lit : covered;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public int getBlockColor() {
        return 0xFFFFFF;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public int getRenderColor(int meta) {
        return GhostLogic.renderColour(appearance, meta, false);
    }

    @Override
    public MapColor getMapColor(int meta) {
        return GhostLogic.mapColour(appearance, false);
    }

    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z) {
        ItemStack covered = GhostLogic.pickBlock(this, x, y, z);
        return covered != null ? covered : super.getPickBlock(target, world, x, y, z);
    }

    /**
     * Whether a plant may stand here, answered for the block underneath.
     *
     * <p>
     * See {@link GhostLogic#sustainsPlant}: Forge asks this by identity, and a ghost is never the
     * grass or the sand it is drawn as, so without it nothing could be planted on worn ground.
     */
    @Override
    public boolean canSustainPlant(IBlockAccess world, int x, int y, int z,
        net.minecraftforge.common.util.ForgeDirection direction, net.minecraftforge.common.IPlantable plantable) {
        Boolean covered = GhostLogic.sustainsPlant(this, world, x, y, z, direction, plantable);
        return covered != null ? covered.booleanValue() : super.canSustainPlant(world, x, y, z, direction, plantable);
    }

    /**
     * Whether a face is solid, answered for the block underneath.
     *
     * <p>
     * See {@link GhostLogic#sideSolid}: the client asks this before it will place a torch or a rail,
     * and a sunken ghost would otherwise say no where the server's whole block says yes.
     */
    @Override
    public boolean isSideSolid(IBlockAccess world, int x, int y, int z,
        net.minecraftforge.common.util.ForgeDirection side) {
        Boolean covered = GhostLogic.sideSolid(this, world, x, y, z, side);
        return covered != null ? covered.booleanValue() : super.isSideSolid(world, x, y, z, side);
    }

    @Override
    public float getBlockHardness(World world, int x, int y, int z) {
        float covered = GhostLogic.blockHardness(this, world, x, y, z);
        return Float.isNaN(covered) ? super.getBlockHardness(world, x, y, z) : covered;
    }

    /**
     * Mining speed answered for the block underneath, tool and all.
     *
     * <p>
     * See {@link GhostLogic#breakSpeed}: hardness alone was not enough, because whether a tool can
     * harvest what is there is asked of the block being clicked, and that block is this one.
     */
    /**
     * Lets vanilla spawn the breaking dust, with the wear shade held off while it does.
     *
     * <p>
     * False, so nothing here replaces vanilla's own placement - only the tint those particles will
     * ask this block for a moment later. See {@link GhostRendering#holdShadeForDust}.
     */
    @Override
    public boolean addHitEffects(World world, net.minecraft.util.MovingObjectPosition target,
        net.minecraft.client.particle.EffectRenderer renderer) {
        GhostRendering.holdShadeForDust();
        return false;
    }

    @Override
    public boolean addDestroyEffects(World world, int x, int y, int z, int meta,
        net.minecraft.client.particle.EffectRenderer renderer) {
        GhostRendering.holdShadeForDust();
        return false;
    }

    @Override
    public float getPlayerRelativeBlockHardness(net.minecraft.entity.player.EntityPlayer player, World world, int x,
        int y, int z) {
        float covered = GhostLogic.breakSpeed(this, world, player, x, y, z);
        return Float.isNaN(covered) ? super.getPlayerRelativeBlockHardness(player, world, x, y, z) : covered;
    }

    @Override
    public Item getItemDropped(int meta, Random random, int fortune) {
        // The server drops whatever the real block drops. A ghost must never add to that.
        return null;
    }

    @Override
    public int quantityDropped(Random random) {
        return 0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderBlockPass() {
        return clear || window ? 1 : 0;
    }

    /**
     * Whether a neighbour may see past this stair on the side it is asking about.
     *
     * <p>
     * Deliberately not marked as an override, because the method it replaces does not exist when
     * this class is compiled. It is added to {@code BlockStairs} at runtime by the better face
     * culling that comes with Angelica, along with the interface that declares it, and a neighbour
     * deciding whether to draw a face reaches it through that interface rather than by name. A
     * subclass method of the same name and shape wins the dispatch once the superclass has gained
     * one, and this class is built in preInit, long after that class is transformed - so writing the
     * method here is the whole of it: no mixin, no late config, and not one line of anybody else's
     * code on the compile path. Every type in the signature is vanilla.
     *
     * <p>
     * It has to be written here rather than added to {@code BlockStairs} the way that mod added it,
     * and that is not a preference. Two mixins adding one method to one class is a merge conflict,
     * ours would be registered first, and the loser is skipped - so the tidier-looking version of
     * this would delete that mod's stair face culling outright and say so once, at debug level.
     *
     * <p>
     * What it fixes is a stair drawn one way and culled against another. The added method reads the
     * metadata at the position and takes the upside-down flag out of bit two, which is vanilla's own
     * arithmetic and correct for a vanilla stair. A ghost's metadata is its wear gradation, nought
     * to fifteen, so from gradation four onward bit two says "upside-down" about a stair standing
     * the right way up, and again the right way up about one that is not. Half of all gradations,
     * deterministically. The two shapes are mirror images about the half-block line, so a neighbour
     * sharing a face with a worn stair takes its decision against the wrong half: the riser between
     * two stairs in a row is culled where it should be drawn, and four gradations later a face is
     * drawn where it should have been culled. That is why the blemish wanted a row of stairs rather
     * than one, and why it came and went in bands as the ground wore in.
     *
     * <p>
     * {@link com.trmtgtnh.mixin.MixinStairMetadata} cannot reach this. Its redirects name the four
     * vanilla shape methods and the ray trace, and its config is prepared before a single mod jar
     * has been opened - a redirect naming a method that does not exist yet would fail selection
     * outright. So the answer has to come from the block holding the wrong number, which for this
     * one method is this one: what is being asked about is always this ghost, at its own position.
     *
     * <p>
     * The rest is that mod's rule reproduced rather than delegated to, because there is nothing to
     * delegate to - the inherited version is the thing being replaced. A stair drawn in the pass
     * that sorts back to front never obstructs; the bottom face is clear exactly when the stair is
     * upside-down and the top face exactly when it is not; and a side face is clear when the
     * caller's box crosses the half-block line into the half this stair has left empty. If that rule
     * ever changes shape this stops overriding anything and the blemish comes back. It cannot crash,
     * and it cannot be worse than not writing it.
     */
    @SideOnly(Side.CLIENT)
    public boolean isFaceNonObstructing(IBlockAccess world, int x, int y, int z, int side, double otherMinX,
        double otherMinY, double otherMinZ, double otherMaxX, double otherMaxY, double otherMaxZ) {
        // Asked of clearness rather than of the pass, which a window also answers with one -
        // and a window stair is mostly solid stone and obstructs whatever is behind it.
        if (clear) return true;
        // The stair's own metadata, put back. Bit two is vanilla's upside-down flag - see BlockStairs
        // reading (meta & 4) for exactly this - and metadataAt hands back the facing and the half
        // this ghost stands in for rather than the gradation it is wearing.
        boolean upsideDown = (GhostStairs.metadataAt(world, x, y, z) & 7) > 3;
        switch (side) {
            case 0:
                return !upsideDown;
            case 1:
                return upsideDown;
            default:
                return upsideDown ? otherMinY < 0.5D : otherMaxY > 0.5D;
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister register) {
        fallbackIcon = register.registerIcon(GhostLogic.fallbackTextureName(appearance));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(int side, int meta) {
        return GhostLogic.icon(this, side, meta);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        return GhostRendering.iconFor(this, world, x, y, z, side);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public int colorMultiplier(IBlockAccess world, int x, int y, int z) {
        return GhostRendering.colorFor(this, world, x, y, z);
    }
}
