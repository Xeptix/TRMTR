package com.trmtgtnh.client.render;

import java.nio.FloatBuffer;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.entity.EntityGolemOfWays;
import com.trmtgtnh.entity.GolemCombat;
import com.trmtgtnh.entity.GolemUpgrade;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws a Golem of Ways, in whichever skin its upgrade gives it, holding whatever it is working
 * with.
 *
 * <p>
 * One texture per upgrade, chosen from the entity's own watched value, so fitting one changes what
 * the thing looks like the moment it goes in - and the model puts a mantle across its shoulders at
 * the same time. Cached in an array because this is asked once per golem per frame.
 */
@SideOnly(Side.CLIENT)
public class RenderGolemOfWays extends RenderLiving {

    /**
     * How many steps the All Ways skin turns through, and how long it holds each.
     *
     * <p>
     * The count is the generator's, and the two have to agree - it writes exactly this many
     * numbered frames and this asks for them by number. Three ticks a step puts a full turn at a
     * second and a half, which reads as a colour moving; at one tick it reads as a colour
     * flickering, which is a different and much worse thing to have on a golem.
     */
    private static final int OMNI_FRAMES = 10;

    private static final int OMNI_TICKS = 3;

    private static final ResourceLocation[] SKINS = new ResourceLocation[GolemUpgrade.values().length];

    /** The additive overlay for each skin: black but for its eyes, its waystone and its sigil. */
    private static final ResourceLocation[] GLOWS = new ResourceLocation[GolemUpgrade.values().length];

    private static final ResourceLocation[] OMNI_SKINS = new ResourceLocation[OMNI_FRAMES];

    private static final ResourceLocation[] OMNI_GLOWS = new ResourceLocation[OMNI_FRAMES];

    /**
     * The loose version's frames, which are the bound one's with the missing-texture chequer
     * scattered over them.
     *
     * <p>
     * The same count and the same clock as the bound one, so the two turn through the wheel
     * together and the only difference between them on screen is the thing that is actually
     * different about them.
     */
    private static final ResourceLocation[] LOOSE_SKINS = new ResourceLocation[OMNI_FRAMES];

    private static final ResourceLocation[] LOOSE_GLOWS = new ResourceLocation[OMNI_FRAMES];

    static {
        for (GolemUpgrade upgrade : GolemUpgrade.values()) {
            SKINS[upgrade.ordinal()] = skin("golem_" + upgrade.key);
            GLOWS[upgrade.ordinal()] = skin("golem_glow_" + upgrade.key);
        }
        for (int frame = 0; frame < OMNI_FRAMES; frame++) {
            OMNI_SKINS[frame] = skin("golem_" + GolemUpgrade.OMNI.key + "_" + frame);
            OMNI_GLOWS[frame] = skin("golem_glow_" + GolemUpgrade.OMNI.key + "_" + frame);
            LOOSE_SKINS[frame] = skin("golem_" + GolemUpgrade.UNSTABLE.key + "_" + frame);
            LOOSE_GLOWS[frame] = skin("golem_glow_" + GolemUpgrade.UNSTABLE.key + "_" + frame);
        }
    }

    private static ResourceLocation skin(String name) {
        return new ResourceLocation(Trmt.MODID, "textures/entity/" + name + ".png");
    }

    /**
     * Which step of the turn this frame is, off the world clock rather than the golem's own age.
     *
     * <p>
     * So that two All Ways golems standing beside each other turn together. Off their own
     * {@code ticksExisted} they would drift by however far apart they were built, and a pair of
     * them out of phase reads as two different upgrades.
     */
    private static int frame(Entity entity) {
        if (entity == null || entity.worldObj == null) return 0;
        return (int) ((entity.worldObj.getTotalWorldTime() / OMNI_TICKS) % OMNI_FRAMES);
    }

    /**
     * The fog colour in force outside the glow pass, and the black it is swapped for inside it.
     *
     * <p>
     * Fixed-function fog is applied to a fragment <em>before</em> the blend stage, so a texel that
     * is black in the texture does not reach the blender black - it reaches it as the fog colour,
     * scaled by how far away the thing is. Under an additive blend that is not nothing: it is the
     * fog colour added over all twenty-one boxes, which turns the golem into a solid brighter blob
     * instead of a dark shape with two lit points. Black is the correct fog colour for an additive
     * pass rather than a workaround: fading a glow toward black <em>is</em> fading it out.
     *
     * <p>
     * The limits matter and cost a version to learn. Reading the colour back wants sixteen floats,
     * because that is the largest thing {@code glGetFloat} can be asked for and it checks the
     * buffer against the worst case. Handing one to {@code glFog} wants exactly four, because a
     * state manager that caches the fog colour copies everything the buffer has left into its own
     * four - and sixteen into four is a {@code BufferOverflowException}, thrown inside the base
     * class's catch-all, logged, and otherwise invisible except that everything after it in the
     * method silently stops happening. What stopped happening was the golem's held tool.
     */
    private static final FloatBuffer FOG_KEPT = BufferUtils.createFloatBuffer(16);

    private static final FloatBuffer FOG_BLACK = BufferUtils.createFloatBuffer(4);

    static {
        FOG_BLACK.put(0F)
            .put(0F)
            .put(0F)
            .put(1F);
        FOG_BLACK.flip();
    }

    /**
     * Whether the fog colour can be swapped at all on this stack.
     *
     * <p>
     * Turned off for good the first time either call complains. The glow is a nicety; the tool in
     * the golem's hand is not, and no amount of correctly fogged eyes is worth a hand that draws
     * nothing because a state manager somewhere down the stack keeps its buffers differently.
     */
    private static boolean fogSwappable = true;

    /** Whether the glow pass has taken the fog colour and owes it back. */
    private boolean fogTaken;

    /** The same model the base class holds, kept typed so the tool can be hung off its free arm. */
    private final ModelGolemOfWays golemModel;

    public RenderGolemOfWays() {
        super(new ModelGolemOfWays(), 0.7F);
        this.golemModel = (ModelGolemOfWays) this.mainModel;
        // A second copy for the glow pass. It has to be a second object: the base class poses it
        // from the same entity and the same frame, so it comes out in exactly the pose the first
        // one is already in, and one model cannot be drawn twice with two textures in one pass.
        setRenderPassModel(new ModelGolemOfWays());
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        GolemUpgrade fitted = entity instanceof EntityGolemOfWays ? ((EntityGolemOfWays) entity).fittedUpgrade()
            : GolemUpgrade.NONE;
        if (fitted.isOmni()) return OMNI_SKINS[frame(entity)];
        if (fitted == GolemUpgrade.UNSTABLE) return LOOSE_SKINS[frame(entity)];
        return SKINS[fitted.ordinal()];
    }

    /**
     * Draws its eyes lit, whatever the light where it is standing.
     *
     * <p>
     * The spider's trick, which is the only one 1.7.10 offers: the whole model again, in a texture
     * that is black everywhere except the few places the golem glows, with the lightmap forced to
     * its brightest corner and the blend set to add. Black adds nothing, so everything but those
     * places is invisible and costs one more pass of twenty-one boxes.
     *
     * <p>
     * Every golem gets it and not only the fitted ones, because the thing worth seeing at night is
     * that the figure standing in your road is a golem rather than a monster. An unfitted one
     * carries a dull ember and a fitted one carries its own colour, which is the same fact the
     * skin says in daylight.
     */
    @Override
    protected int shouldRenderPass(EntityLivingBase entity, int pass, float partial) {
        if (pass != 0 || !(entity instanceof EntityGolemOfWays)) return -1;
        GolemUpgrade fitted = ((EntityGolemOfWays) entity).fittedUpgrade();
        bindTexture(
            fitted.isOmni() ? OMNI_GLOWS[frame(entity)]
                : fitted == GolemUpgrade.UNSTABLE ? LOOSE_GLOWS[frame(entity)] : GLOWS[fitted.ordinal()]);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
        GL11.glDepthMask(!entity.isInvisible());
        if (fogSwappable) {
            try {
                // Sixteen remaining to read it back, then exactly four to hand it anywhere.
                FOG_KEPT.clear();
                GL11.glGetFloat(GL11.GL_FOG_COLOR, FOG_KEPT);
                FOG_KEPT.limit(4);
                GL11.glFog(GL11.GL_FOG_COLOR, FOG_BLACK);
                fogTaken = true;
            } catch (RuntimeException beyondUs) {
                fogSwappable = false;
                fogTaken = false;
                Trmt.LOG.warn("Leaving the fog colour alone for the golem glow: " + beyondUs);
            }
        }
        // Vanilla's own two numbers for the brightest corner of the lightmap, unpacked the way it
        // unpacks them.
        char full = 61680;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, full % 65536, full / 65536);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        return 1;
    }

    /**
     * Puts the tool in its free fist.
     *
     * <p>
     * This hook runs after the model has been drawn and while the arm angles for this frame are
     * still set, so posting off the arm reproduces the pose exactly and a tool hung there swings
     * with it for nothing. That is what lets the tool be carried at rest as well as swung: the arm
     * hangs at the golem's side when it has nothing to do, and the tamper hangs with it, because
     * both are drawn from the same angles. An unarmed golem holds nothing and this costs it one
     * null check.
     *
     * <p>
     * The transform is vanilla's own for a long-handled tool, which every tamper asks for, but
     * larger than a player's. A tool sized for a hand at chest height reads as a twig in a fist
     * this size, and the golem is half again the height of the man who handed it over.
     *
     * <p>
     * The matrix is popped from a finally, because the whole of an entity's drawing sits inside one
     * catch-all up in the base class: a throw between the push and the pop would be swallowed into
     * a log line, and what the player would see instead is every model drawn after this one in the
     * frame slowly sliding off its own feet.
     */
    @Override
    protected void renderEquippedItems(EntityLivingBase entity, float partial) {
        // The glow pass runs before this one and leaves the lightmap pinned at full bright, and
        // the base class does not put it back before calling here. Left alone, a golem carrying a
        // tamper in a dark room would be holding a tamper lit like daylight - which is a bug that
        // only appears once something else on the same entity glows.
        int here = entity.getBrightnessForRender(partial);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, here % 65536, here / 65536);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        if (fogTaken) {
            fogTaken = false;
            try {
                GL11.glFog(GL11.GL_FOG_COLOR, FOG_KEPT);
            } catch (RuntimeException beyondUs) {
                fogSwappable = false;
                Trmt.LOG.warn("Leaving the fog colour alone for the golem glow: " + beyondUs);
            }
        }

        super.renderEquippedItems(entity, partial);

        ItemStack shown = entity.getHeldItem();
        if (shown == null || shown.getItem() == null) return;
        Item held = shown.getItem();

        GL11.glPushMatrix();
        try {
            golemModel.postRenderToolHand(0.0625F);
            // The middle of the fist, in the arm's own units: eleven across and twenty-five and
            // a half down from the shoulder is where that box is drawn.
            GL11.glTranslatef(-0.6875F, 1.59375F, 0F);

            if (held.isFull3D()) {
                // Vanilla's own transform for a long-handled tool, unaltered, and it has to be
                // unaltered. Negating the spin about the vertical was tried, on the argument that
                // moving the tool to the other fist had left the flat face of the sprite pointing
                // into the body. The measurement was right and the conclusion was not: an item is
                // drawn as an extruded slab with both faces textured, so which way the normal
                // points was never what made it visible - and ninety degrees of spin is very much
                // what makes a tamper look like it is being held sideways. This arm is the one
                // vanilla hangs a held item off, so these are its numbers.
                float size = 0.9F;
                GL11.glScalef(size, -size, size);
                GL11.glRotatef(-100F, 1F, 0F, 0F);
                GL11.glRotatef(45F, 0F, 1F, 0F);
            } else {
                // Nothing the mod puts here takes this branch - every tamper is a long-handled
                // tool - but a third-party tool that implements the interface would, and a flat
                // sprite drawn with the transform above would lie flat against the fist.
                float size = 0.55F;
                GL11.glTranslatef(0.25F, 0.1875F, -0.1875F);
                GL11.glScalef(size, size, size);
                GL11.glRotatef(60F, 0F, 0F, 1F);
                GL11.glRotatef(-90F, 1F, 0F, 0F);
                GL11.glRotatef(20F, 0F, 0F, 1F);
            }

            // Vanilla's own two-branch draw. No tamper in the mod asks for either the extra passes
            // or the tint, so this is one pass and plain white today; it is written out because a
            // tool from somewhere else may ask, and a tool drawn in the wrong colour is a bug
            // report about this mod.
            if (held.requiresMultipleRenderPasses()) {
                for (int pass = 0; pass < held.getRenderPasses(shown.getItemDamage()); pass++) {
                    tint(held.getColorFromItemStack(shown, pass));
                    renderManager.itemRenderer.renderItem(entity, shown, pass);
                }
            } else {
                tint(held.getColorFromItemStack(shown, 0));
                renderManager.itemRenderer.renderItem(entity, shown, 0);
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }

    /** Sets the drawing colour from one of vanilla's packed item tints. */
    private static void tint(int packed) {
        GL11.glColor4f((packed >> 16 & 255) / 255F, (packed >> 8 & 255) / 255F, (packed & 255) / 255F, 1F);
    }

    /**
     * Rocks it from side to side as it walks, breathes while it stands, and drops onto a blow.
     *
     * <p>
     * The lurch is the iron golem's own, on the same thirteen-tick clock the model's legs use, so
     * the roll lands on the footfalls rather than drifting against them. Without it a golem this
     * heavy glides, which is the one thing it must never look like it is doing.
     *
     * <p>
     * Standing still it sways instead, a degree either way on a clock slow enough that nobody
     * watching for it can see where the turn is. That is the whole of what separates a golem
     * waiting from a statue of one, and it costs a sine.
     *
     * <p>
     * The jolt is the only part of a blow that is not in the arm. A rammer coming down puts its
     * weight through its feet, and an inch of drop timed to the bottom of the swing does more for
     * how heavy the thing looks than another ten degrees of arm would. Here rather than in the
     * model because it moves the whole golem, and a model can only move its own parts away from
     * each other.
     */
    @Override
    protected void rotateCorpse(EntityLivingBase entity, float age, float yaw, float partial) {
        super.rotateCorpse(entity, age, yaw, partial);

        // Crossfaded rather than switched, on the same fifth of a step the model's own legs use.
        // The two used to be a branch taken at a hundredth of a step, which put a jump from one
        // degree of sway to five and a half at a threshold the model was easing across - a pop that
        // has always been there and is only obvious once a pose makes the standing half bigger.
        float swing = entity.prevLimbSwingAmount + (entity.limbSwingAmount - entity.prevLimbSwingAmount) * partial;
        float move = Math.min(1F, swing * 5F);
        float period = 13F;
        float time = entity.limbSwing - entity.limbSwingAmount * (1F - partial) + 6F;
        float lurch = (Math.abs(time % period - period * 0.5F) - period * 0.25F) / (period * 0.25F);

        // The third standing pose, and the only one that moves the whole golem rather than a part
        // of it. That is what makes it the one that reads at range: it carries both eyes, the face
        // marker and the chest sigil together, where the other two move the head alone - and an
        // ungraded golem has no sigil to move.
        float shift = 0F;
        float rest = 0F;
        if (entity instanceof EntityGolemOfWays) {
            EntityGolemOfWays golem = (EntityGolemOfWays) entity;
            float settled = golem.idleShow(partial);
            if (golem.idlePose() == GolemCombat.IDLE_DONE) shift = settled;
            else if (golem.idlePose() == GolemCombat.IDLE_UNTOLD) rest = settled;
        }

        // One sine, used twice, so the settle lands at the ends of the roll where a weight shift
        // belongs rather than wandering across it.
        float swayAt = MathHelper.sin((entity.ticksExisted + partial) * 0.045F);
        float sway = swayAt * (1.1F + 2.4F * shift - 0.85F * rest);
        GL11.glRotatef(5.5F * lurch * move + sway * (1F - move), 0F, 0F, 1F);
        // Kept just inside the ground clearance an ordinary stride already uses: at three and a
        // half degrees with this dip the outer tread corner reaches the same depth the walk lurch
        // does, and no further.
        if (shift > 0F) GL11.glTranslatef(0F, -0.02F * swayAt * swayAt * shift, 0F);
        if (entity instanceof EntityGolemOfWays) {
            float blow = ((EntityGolemOfWays) entity).blowProgress(partial);
            if (blow > 0F) {
                GL11.glTranslatef(0F, -0.055F * MathHelper.sin(blow * (float) Math.PI), 0F);
            }
        }
    }
}
