package com.trmtgtnh.client.render;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.trmtgtnh.entity.EntityGolemOfWays;
import com.trmtgtnh.entity.GolemCombat;

/**
 * Draws a Golem of Ways, in whichever skin its upgrade gives it, holding whatever it is working with.
 *
 * <p>
 * Two things beyond the model itself, and in 1.12.2 both are layers rather than methods here: the
 * glow that lights its eyes whatever the light where it stands, and the tool in its free fist. See
 * {@link LayerGolemGlow} and {@link LayerGolemTool} for what each of those saved over the other
 * edition's versions, which are {@code shouldRenderPass} and {@code renderEquippedItems} on the
 * renderer and no longer exist.
 *
 * <p>
 * What is left here is the skin, and the way the whole figure moves.
 */
@SideOnly(Side.CLIENT)
public class RenderGolemOfWays extends RenderLiving<EntityGolemOfWays> {

    /** The same model the base class holds, kept typed so the tool can be hung off its free arm. */
    private final ModelGolemOfWays golemModel;

    public RenderGolemOfWays(RenderManager manager) {
        super(manager, new ModelGolemOfWays(), 0.7F);
        this.golemModel = (ModelGolemOfWays) this.mainModel;
        addLayer(new LayerGolemGlow(this));
        addLayer(new LayerGolemTool(this));
    }

    ModelGolemOfWays golemModel() {
        return golemModel;
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityGolemOfWays golem) {
        return GolemSkins.skinFor(golem, golem.fittedUpgrade());
    }

    /**
     * Rocks it from side to side as it walks, breathes while it stands, and drops onto a blow.
     *
     * <p>
     * The lurch is the iron golem's own, on the same thirteen-tick clock the model's legs use, so the
     * roll lands on the footfalls rather than drifting against them. Without it a golem this heavy
     * glides, which is the one thing it must never look like it is doing.
     *
     * <p>
     * Standing still it sways instead, a degree either way on a clock slow enough that nobody watching
     * for it can see where the turn is. That is the whole of what separates a golem waiting from a
     * statue of one, and it costs a sine.
     *
     * <p>
     * The jolt is the only part of a blow that is not in the arm. A rammer coming down puts its weight
     * through its feet, and an inch of drop timed to the bottom of the swing does more for how heavy
     * the thing looks than another ten degrees of arm would. Here rather than in the model because it
     * moves the whole golem, and a model can only move its own parts away from each other.
     */
    @Override
    protected void applyRotations(EntityGolemOfWays golem, float age, float yaw, float partial) {
        super.applyRotations(golem, age, yaw, partial);

        // Crossfaded rather than switched, on the same fifth of a step the model's own legs use. The
        // two used to be a branch taken at a hundredth of a step, which put a jump from one degree of
        // sway to five and a half at a threshold the model was easing across - a pop that has always
        // been there and is only obvious once a pose makes the standing half bigger.
        float swing = golem.prevLimbSwingAmount + (golem.limbSwingAmount - golem.prevLimbSwingAmount) * partial;
        float move = Math.min(1F, swing * 5F);
        float period = 13F;
        float time = golem.limbSwing - golem.limbSwingAmount * (1F - partial) + 6F;
        float lurch = (Math.abs(time % period - period * 0.5F) - period * 0.25F) / (period * 0.25F);

        // The third standing pose, and the only one that moves the whole golem rather than a part of
        // it. That is what makes it the one that reads at range: it carries both eyes, the face marker
        // and the chest sigil together, where the other two move the head alone - and an ungraded
        // golem has no sigil to move.
        float shift = 0F;
        float rest = 0F;
        float settled = golem.idleShow(partial);
        if (golem.idlePose() == GolemCombat.IDLE_DONE) shift = settled;
        else if (golem.idlePose() == GolemCombat.IDLE_UNTOLD) rest = settled;

        // One sine, used twice, so the settle lands at the ends of the roll where a weight shift
        // belongs rather than wandering across it.
        float swayAt = MathHelper.sin((golem.ticksExisted + partial) * 0.045F);
        float sway = swayAt * (1.1F + 2.4F * shift - 0.85F * rest);
        GlStateManager.rotate(5.5F * lurch * move + sway * (1F - move), 0F, 0F, 1F);
        // Kept just inside the ground clearance an ordinary stride already uses: at three and a half
        // degrees with this dip the outer tread corner reaches the same depth the walk lurch does, and
        // no further.
        if (shift > 0F) GlStateManager.translate(0F, -0.02F * swayAt * swayAt * shift, 0F);
        float blow = golem.blowProgress(partial);
        if (blow > 0F) {
            GlStateManager.translate(0F, -0.055F * MathHelper.sin(blow * (float) Math.PI), 0F);
        }
    }

    /**
     * Hands the render manager one of these when it asks.
     *
     * <p>
     * The other edition registers the renderer object itself; 1.12.2 wants a factory, because the
     * manager is built after the client proxy runs and a renderer needs one. A named class with its
     * own side annotation rather than an anonymous one, for the reason {@code TamperModels} is written
     * the way it is: an anonymous class is its own class file with none of this one's annotation on
     * it, and the side scan would report it on the only evidence it has.
     */
    @SideOnly(Side.CLIENT)
    public static final class Factory implements IRenderFactory<EntityGolemOfWays> {

        @Override
        public Render<? super EntityGolemOfWays> createRenderFor(RenderManager manager) {
            return new RenderGolemOfWays(manager);
        }
    }
}
