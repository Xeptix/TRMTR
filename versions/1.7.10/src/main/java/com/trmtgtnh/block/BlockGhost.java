package com.trmtgtnh.block;

import java.util.Random;

import net.minecraft.block.Block;
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
 * A block that only ever exists in a client's own copy of the world.
 *
 * <p>
 * The server never places one of these. The client paints them over terrain it has been told
 * is worn, reads back what was underneath from its overlay cache, and renders the wear
 * texture generated for that particular block. Switch the overlay off and they are all
 * replaced with what they were covering; delete the mod and they were never in the save to
 * begin with.
 *
 * <p>
 * Because the server still believes the position holds the original block, everything a
 * client predicts locally has to agree with it. Collision, light opacity and mining speed are
 * therefore either identical by construction or delegated to the block being covered.
 * Metadata is the wear stage and nothing else; rotation comes from the position, which is why
 * one block covers all fourteen possible gradations instead of needing four.
 *
 * <p>
 * This is the variant for every family that is not grass. It extends plain {@link Block}, and
 * that is the whole point: this class used to extend {@code BlockGrass}, which made JourneyMap
 * flag worn sand, gravel, stone and cobble as biome-coloured grass and paint every one of them
 * the same flat grey. See {@link GhostBlock}. Grass itself is {@link BlockGhostGrass}; the
 * behaviour both share is in {@link GhostLogic}.
 */
public class BlockGhost extends Block implements GhostBlock {

    /**
     * The material this ghost should report.
     *
     * <p>
     * The inherited field stays {@link Material#grass} — what {@code BlockGrass} used to pass —
     * so anything reading it directly sees exactly what it saw before. This is what every
     * virtual caller gets instead.
     */
    private final Material material;

    private final SurfaceFamily appearance;

    private final boolean mimicVanillaGrassTop;

    /** True when this variant renders untinted, showing the earth beneath on its sides. */
    private final boolean untinted;

    /**
     * True when this variant renders hollowed out. A sunken ghost reports itself as not a full
     * cube so its neighbours stop culling the faces the hollow exposes.
     */
    private final boolean sunken;

    /**
     * True when this variant may be seen through, which is the family's own answer for all but the
     * solid twins - see {@link GhostLogic#seeThrough}. Held rather than asked because two stand-ins
     * of the same family give different answers to it.
     */
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

    private IIcon fallbackIcon;

    public BlockGhost(SurfaceFamily appearance, boolean mimicVanillaGrassTop, boolean untinted, boolean sunken) {
        this(appearance, mimicVanillaGrassTop, untinted, sunken, GhostLogic.seeThrough(appearance));
    }

    public BlockGhost(SurfaceFamily appearance, boolean mimicVanillaGrassTop, boolean untinted, boolean sunken,
        boolean clear) {
        this(appearance, mimicVanillaGrassTop, untinted, sunken, clear, false);
    }

    public BlockGhost(SurfaceFamily appearance, boolean mimicVanillaGrassTop, boolean untinted, boolean sunken,
        boolean clear, boolean window) {
        // Deliberately grass, not the family's own material: the inherited field is read
        // directly in vanilla hot paths, and it held grass under the old superclass. What the
        // block reports through getMaterial is the family's, exactly as before.
        super(Material.grass);
        this.material = GhostLogic.materialFor(appearance);
        this.appearance = appearance;
        this.mimicVanillaGrassTop = mimicVanillaGrassTop;
        this.untinted = untinted;
        this.sunken = sunken;
        this.clear = clear;
        this.window = window;

        setHardness(GhostLogic.hardnessFor(appearance));
        setResistance(GhostLogic.resistanceFor(appearance));
        setStepSound(GhostLogic.stepSoundFor(appearance));
        setLightOpacity(clear ? GhostLogic.lightOpacityFor(appearance) : 255);
        // The same thing vanilla names farmland by hand for, and for the same reason: a block that
        // stops all light and does not fill its cube has nothing but darkness stored in its own
        // square, and every face looking into the hollow is painted from that square. Vanilla works
        // this out in a loop that runs before any mod exists, so a stand-in has to say it itself.
        //
        // Only the hollowed ones, and not ice: a full cube's neighbours never draw the face at all,
        // and ice lets light through, so its square holds the real answer already.
        // A window as well as a hollow, and the reason is the block BEHIND it rather than this one.
        // Its neighbours now draw the faces they used to cull, and they take the light for those
        // faces from this square - which holds nothing, because a window still stops all light. The
        // flag is what sends them to the brightest neighbour instead of into the dark.
        useNeighborBrightness = !clear && (sunken || window);
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
        // A slab is covered by one of these too: its bounds are taken from the record rather than
        // from a shape of its own, so there is nothing here for a shape to decide.
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
     * Flat white. Breaking particles and held-item rendering use this, and the living
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
        // Clear surfaces are drawn in the pass that sorts back to front, the same as the block
        // they are standing in for. Left in the solid pass, worn ice draws over whatever is
        // behind it instead of through it.
        return clear || window ? 1 : 0;
    }

    @Override
    public boolean isOpaqueCube() {
        // A hollowed block no longer fills its space, so neighbours must draw the faces the
        // hollow exposes; a clear one never filled it to look at in the first place.
        return !sunken && !clear && !window;
    }

    /**
     * Two windows side by side hide the face between them, the way two panes of glass do.
     *
     * <p>
     * Without this a road of worn waterstone draws every internal face and every one of them is a
     * blue sheet seen through the one in front, which is both slower and wrong. Vanilla does exactly
     * this for glass, and the reason it is safe there is the reason it is guarded here: a full cube
     * meeting another full cube shares that face exactly. A sunken one does not - two hollows of
     * different depths leave part of the taller one's side open onto its own rut - so only the full
     * cubes take the shortcut.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
        if (window && !sunken && world.getBlock(x, y, z) == this) return false;
        return super.shouldSideBeRendered(world, x, y, z, side);
    }

    @Override
    public boolean renderAsNormalBlock() {
        return !sunken;
    }

    /**
     * Sets the rendered shape from the wear stage.
     *
     * <p>
     * This is the one place the shared bounds fields on {@link Block} get written, which the
     * chunk mesher may be doing on several threads at once. That is the same exposure every
     * vanilla slab and stair has under a threaded mesher, and it is confined to appearance:
     * collision never reads these fields, so a torn read can only ever mean a frame with the
     * wrong rut depth, never a player standing in the wrong place.
     */
    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        setBlockBounds(
            0.0F,
            (float) GhostLogic.bottomAt(x, y, z),
            0.0F,
            1.0F,
            (float) GhostLogic.heightAt(this, world, x, y, z),
            1.0F);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return GhostLogic.solidBox(appearance, world, x, y, z);
    }

    /**
     * Selection follows collision, not the visuals, so what you can click on is what the server
     * agrees is there - except that it can never be nothing, which collision can. See
     * {@link GhostLogic#outlineBox}.
     */
    @Override
    public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {
        return GhostLogic.outlineBox(appearance, world, x, y, z);
    }

    @Override
    public int getRenderType() {
        return 0; // the standard block renderer, which is the one Celeritas meshes natively
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister register) {
        // Wear textures are generated and registered wholesale by the texture layer; this
        // only needs something valid to hand back when a position has no overlay data.
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
