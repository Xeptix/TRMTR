package com.trmtgtnh.client.render;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import com.trmtgtnh.entity.EntityGolemOfWays;
import com.trmtgtnh.entity.GolemCombat;
import com.trmtgtnh.entity.GolemUpgrade;

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
 *
 * <p>
 * From 1.21.2 a renderer reads the golem into a {@link GolemRenderState} before anything is drawn, and the model and
 * the layers draw from that alone; {@link #extractRenderState} is where every question 1.21.1 asked of the golem as it
 * drew is asked now, at the same fraction of the tick.
 */
public class RenderGolemOfWays
    extends net.minecraft.client.renderer.entity.MobRenderer<EntityGolemOfWays, GolemRenderState, ModelGolemOfWays> {

    /**
     * The same model the base class holds, kept so the tool can be hung off its free arm.
     *
     * <p>
     * No longer a cast: a renderer names its own model type here, so the base class already holds a
     * {@code ModelGolemOfWays}. Kept as a field anyway, because what the layer wants is a name for
     * "the golem's model" rather than "whatever this renderer draws with".
     */
    private final ModelGolemOfWays golemModel;

    /** From 1.17 a renderer is made from a context, and bakes its model from the layer each loader registered. */
    public RenderGolemOfWays(net.minecraft.client.renderer.entity.EntityRendererProvider.Context context) {
        super(context, new ModelGolemOfWays(context.bakeLayer(ModelGolemOfWays.LAYER)), 0.7F);
        this.golemModel = this.model;
        addLayer(new LayerGolemGlow(this));
        addLayer(new LayerGolemTool(this));
    }

    ModelGolemOfWays golemModel() {
        return golemModel;
    }

    @Override
    public Identifier getTextureLocation(GolemRenderState state) {
        // Chosen when the golem was read, by the same call 1.21.1 made here.
        return state.skin;
    }

    @Override
    public GolemRenderState createRenderState() {
        return new GolemRenderState();
    }

    /**
     * Reads the golem for this frame: everything the model, the two layers and the rotations below asked of it in
     * 1.21.1, by the same calls, at the same fraction of the tick.
     */
    @Override
    public void extractRenderState(EntityGolemOfWays golem, GolemRenderState state, float partial) {
        super.extractRenderState(golem, state, partial);
        GolemUpgrade fitted = golem.fittedUpgrade();
        state.fitted = fitted;
        state.skin = GolemSkins.skinFor(golem, fitted);
        state.glow = GolemSkins.glowFor(golem, fitted);
        state.walkSpeed = golem.walkAnimation.speed(partial);
        state.walkPos = golem.walkAnimation.position(partial);
        state.strokePhase = golem.strokePhase(partial);
        state.heldRaise = golem.heldRaise(partial);
        state.fleeLean = golem.fleeLean(partial);
        state.armedShow = golem.armedShow(partial);
        state.fightLean = golem.fightLean(partial);
        state.chewShow = golem.chewShow(partial);
        state.blowProgress = golem.blowProgress(partial);
        state.idleShow = golem.idleShow(partial);
        state.idlePose = golem.idlePose();
        // The tool, as 1.21.1's item renderer was handed it: no display context of its own, the golem's world, and a
        // seed of nought. Resolved here from 1.21.4, drawn by the tool layer.
        net.minecraft.world.item.ItemStack shown = golem.getMainHandItem();
        if (shown == null || shown.isEmpty()) {
            state.tool.clear();
        } else {
            itemModelResolver.updateForTopItem(state.tool, shown, net.minecraft.world.item.ItemDisplayContext.NONE,
                golem.level(), null, 0);
        }
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
    protected void setupRotations(GolemRenderState golem, com.mojang.blaze3d.vertex.PoseStack pose, float yaw,
        float scale) {
        // 1.20.5 hands the entity's scale in as well, for the scale attribute; passed through, as the rest is. From
        // 1.21.2 the golem arrives as its state, its age and the fraction of the tick already read into it.
        super.setupRotations(golem, pose, yaw, scale);

        // Crossfaded rather than switched, on the same fifth of a step the model's own legs use. The
        // two used to be a branch taken at a hundredth of a step, which put a jump from one degree of
        // sway to five and a half at a threshold the model was easing across - a pop that has always
        // been there and is only obvious once a pose makes the standing half bigger.
        // From 1.19.4 the walk animation is a state of its own, whose speed and position at a fraction of a tick are
        // exactly the sums this used to write out: speed eased from the last tick's, position less what is still to
        // come.
        float swing = golem.walkSpeed;
        float move = Math.min(1F, swing * 5F);
        float period = 13F;
        float time = golem.walkPos + 6F;
        float lurch = (Math.abs(time % period - period * 0.5F) - period * 0.25F) / (period * 0.25F);

        // The third standing pose, and the only one that moves the whole golem rather than a part of
        // it. That is what makes it the one that reads at range: it carries both eyes, the face marker
        // and the chest sigil together, where the other two move the head alone - and an ungraded
        // golem has no sigil to move.
        float shift = 0F;
        float rest = 0F;
        float settled = golem.idleShow;
        if (golem.idlePose == GolemCombat.IDLE_DONE) shift = settled;
        else if (golem.idlePose == GolemCombat.IDLE_UNTOLD) rest = settled;

        // One sine, used twice, so the settle lands at the ends of the roll where a weight shift
        // belongs rather than wandering across it.
        float swayAt = Mth.sin(golem.ageInTicks * 0.045F);
        float sway = swayAt * (1.1F + 2.4F * shift - 0.85F * rest);
        // A rotation about Z, said as a quaternion rather than as an axis and an angle. The
        // axis is in the name: ZP is the positive Z axis, which is what the three zeroes and the
        // one used to say. PoseStack's mulPose of a quaternion is rotate from 26.3, the same turn.
        pose.rotate(
            com.mojang.math.Axis.ZP.rotationDegrees(5.5F * lurch * move + sway * (1F - move)));
        // Kept just inside the ground clearance an ordinary stride already uses: at three and a half
        // degrees with this dip the outer tread corner reaches the same depth the walk lurch does, and
        // no further.
        if (shift > 0F) pose.translate(0.0D, -0.02F * swayAt * swayAt * shift, 0.0D);
        float blow = golem.blowProgress;
        if (blow > 0F) {
            pose.translate(0.0D, -0.055F * Mth.sin(blow * (float) Math.PI), 0.0D);
        }
    }

    // The factory the other edition needs here is Forge's own IRenderFactory, so it cannot live
    // in a module both loaders share - and neither loader wants one at this version. Each client
    // entry point names this constructor directly, which is one line apiece; see ForgeClientSetup
    // and TrmtFabricClient.
}
