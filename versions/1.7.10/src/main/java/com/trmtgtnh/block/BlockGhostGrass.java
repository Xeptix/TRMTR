package com.trmtgtnh.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceShape;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The ghost variant for worn grass, which really is a {@link BlockGrass}.
 *
 * <p>
 * Identical in behaviour to {@link BlockGhost} — every method here forwards to the same
 * {@link GhostLogic} call — and separate only because it must inherit from grass while the
 * other families must not. MixinGrassTint's target expression is typed to {@code BlockGrass},
 * so what it hands back has to be one; and the renderer's side-overlay treatment, which is
 * what makes worn turf look like turf rather than like green-tinted dirt, keys off the same
 * inheritance. See {@link GhostBlock} for the full reasoning.
 */
public class BlockGhostGrass extends BlockGrass implements GhostBlock {

    private final Material material;

    private final SurfaceFamily appearance;

    private final boolean mimicVanillaGrassTop;

    private final boolean untinted;

    private final boolean sunken;

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

    private IIcon fallbackIcon;

    public BlockGhostGrass(SurfaceFamily appearance, boolean mimicVanillaGrassTop, boolean untinted, boolean sunken) {
        this(appearance, mimicVanillaGrassTop, untinted, sunken, false);
    }

    public BlockGhostGrass(SurfaceFamily appearance, boolean mimicVanillaGrassTop, boolean untinted, boolean sunken,
        boolean window) {
        super();
        this.material = GhostLogic.materialFor(appearance);
        this.appearance = appearance;
        this.mimicVanillaGrassTop = mimicVanillaGrassTop;
        this.untinted = untinted;
        this.sunken = sunken;
        this.window = window;

        setHardness(GhostLogic.hardnessFor(appearance));
        setResistance(GhostLogic.resistanceFor(appearance));
        setStepSound(GhostLogic.stepSoundFor(appearance));
        setLightOpacity(GhostLogic.lightOpacityFor(appearance));
        // See BlockGhost: what this rescues is the block behind, whose newly drawn face would
        // otherwise take its light from a square that stops all of it.
        useNeighborBrightness = sunken || window;
        setHarvestLevel(GhostLogic.harvestToolFor(appearance), 0);
        // Everything BlockGrass switches on in its own constructor, switched back off. Its
        // update tick spreads turf onto neighbours and reverts in low light, both by writing
        // blocks — which for something that exists only as a client-side cover would be a way to
        // edit a world nobody asked it to touch. It is disabled here and overridden below.
        slipperiness = GhostLogic.slipperinessFor(appearance);
        setTickRandomly(false);
        setCreativeTab(null);
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
        return untinted;
    }

    @Override
    public boolean isSunken() {
        return sunken;
    }

    @Override
    public boolean mimicsVanillaGrassTop() {
        return mimicVanillaGrassTop;
    }

    @Override
    public SurfaceShape shape() {
        // Always, and it cannot be otherwise: this class exists only to inherit BlockGrass, and
        // BlockGrass is a full cube.
        return SurfaceShape.FULL;
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
    // Block
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

    /**
     * How brightly this ghost glows, which is nothing unless somebody lit it.
     *
     * <p>
     * The only block method here that both sides genuinely call: the server propagates block light
     * through the world with it, and the client bakes the result into a chunk mesh.
     * {@link GhostLight} settles which side is asking.
     */
    @Override
    public int getLightValue(net.minecraft.world.IBlockAccess world, int x, int y, int z) {
        int lit = GhostLight.levelAt(world, x, y, z);
        int covered = GhostInherit.glowOf(world, x, y, z);
        return lit > covered ? lit : covered;
    }

    /**
     * Flat white. Inherited, this returns grass's fixed green, which is what breaking particles
     * and held-item rendering would use — so a worn patch would throw up green dust. The living,
     * per-position tint is {@link #colorMultiplier}, which is separate.
     */
    @SideOnly(Side.CLIENT)
    @Override
    public int getBlockColor() {
        return 0xFFFFFF;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public int getRenderColor(int meta) {
        return GhostLogic.renderColour(appearance, meta, untinted);
    }

    @Override
    public MapColor getMapColor(int meta) {
        return GhostLogic.mapColour(appearance, untinted);
    }

    // Bone meal has nothing to grow here. Inherited from BlockGrass, which implements IGrowable.

    @Override
    public boolean func_149851_a(World world, int x, int y, int z, boolean isRemote) {
        return false;
    }

    @Override
    public boolean func_149852_a(World world, Random random, int x, int y, int z) {
        return false;
    }

    @Override
    public void func_149853_b(World world, Random random, int x, int y, int z) {}

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
        return null;
    }

    @Override
    public int quantityDropped(Random random) {
        return 0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderBlockPass() {
        // Clear surfaces are drawn in the pass that sorts back to front, the same as the block
        // they are standing in for. Left in the solid pass, worn ice draws over whatever is
        // behind it instead of through it.
        return window || GhostLogic.seeThrough(appearance) ? 1 : 0;
    }

    @Override
    public boolean isOpaqueCube() {
        // A hollowed block no longer fills its space, so neighbours must draw the faces the
        // hollow exposes; a clear one never filled it to look at in the first place.
        return !sunken && !window && !GhostLogic.seeThrough(appearance);
    }

    @Override
    public boolean renderAsNormalBlock() {
        return !sunken;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, (float) GhostLogic.heightAt(this, world, x, y, z), 1.0F);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return GhostLogic.solidBox(appearance, world, x, y, z);
    }

    /**
     * What you can click on is what the server agrees is there - except that it can never be
     * nothing, which collision can. See {@link GhostLogic#outlineBox}.
     */
    @Override
    public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {
        return GhostLogic.outlineBox(appearance, world, x, y, z);
    }

    @Override
    public int getRenderType() {
        return 0;
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
