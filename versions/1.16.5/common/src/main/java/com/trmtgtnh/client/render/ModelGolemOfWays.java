package com.trmtgtnh.client.render;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;

import com.trmtgtnh.entity.EntityGolemOfWays;
import com.trmtgtnh.entity.GolemCombat;
import com.trmtgtnh.entity.GolemUpgrade;

/**
 * The Golem of Ways: a road-mender built of rammed earth and cut stone.
 *
 * <p>
 * Kin to the iron golem, and deliberately so - the same broad barrel of a chest, the same head sunk
 * between the shoulders with a marker jutting from its face, the same over-long arms hanging past
 * the knees on stubby legs. Anyone who has seen a village guard will read this as one of its
 * cousins at a glance, which is the point. The vertical extents are the iron golem's exactly, so it
 * fills its two-and-seven-tenths hitbox without the renderer scaling it up.
 *
 * <p>
 * What makes it this mod's golem rather than a reskin is what it is made of and what it carries.
 * The head is a slab of cut stone under a worn capstone. The torso is rammed earth between two
 * courses of set stone, with turf grown over the shoulders of a thing that stands still for a
 * living. The left arm ends not in a fist but in a wide flat tamper plate, the right in a blunt
 * cobble fist - so it is visibly holding a tool on one side and nothing on the other. The feet are
 * broad tread plates, because it is the thing that makes the path.
 *
 * <p>
 * That asymmetry does a second job, and which side it falls on is the whole of it. The right arm is
 * the one that strokes: it is the arm the work stroke plays on and the arm a blow comes down
 * through, so it is the arm that has to hold the tamper it was handed. Its own plate hangs off the
 * other one, where it stays part of the silhouette without ever reading as a second tamper in a
 * hand that is already holding yours.
 *
 * <p>
 * Fitting an upgrade adds a mantle - two shoulder plates, a strap behind the neck, and a plaque on
 * the chest carrying that upgrade's sigil - and the whole skin changes with it. Fitting the last
 * one adds a cairn stone on top of its head, because that one should look like it cost something.
 */
public class ModelGolemOfWays extends net.minecraft.client.model.EntityModel<EntityGolemOfWays> {

    private static final float DEG = 180F / (float) Math.PI;

    /** Fraction of the stroke spent lifting; the rest drops and then rests. */
    private static final float LIFT_END = 0.62F;

    private static final float FALL_END = 0.76F;

    /**
     * How far the tool arm is held up simply to carry the thing, in radians.
     *
     * <p>
     * Not decoration - it is what keeps the tamper visible at all. These arms hang a block and a
     * half below the shoulder, so an arm left hanging puts the middle of a held item at shin
     * height with its lower edge below the ground, behind the foot. That is what happened when the
     * tool moved to this arm: on the other one it rode the carry and sat at chest height whenever
     * the golem was busy; on this one it rode the work stroke, which rests at zero. The tool never
     * stopped being drawn. It was being drawn in the dirt.
     */
    private static final float CARRY = 0.60F;

    /** How far the idle plate arm swings out from the hip, in radians. */
    private static final float CAST = 0.20F;

    /** The whole cycle of that cast, in ticks: out, held, back, and a pause before again. */
    private static final float CAST_TICKS = 90F;

    /** How deep the untold bow is, and how much of a breath rides on top of it. */
    private static final float BOW = 0.34F;

    private static final float BREATH = 0.045F;

    /** How far the work stroke lifts it above that carry. */
    private static final float SWING = 0.85F;

    /** How much higher again a fight holds it before striking down. */
    private static final float FIGHT_LIFT = 0.35F;

    /**
     * How much of that stroke a golem plays while a standing pose is still arriving.
     *
     * <p>
     * It used to be the whole answer to standing about, and it is now the handover between two
     * answers. A golem that has stopped working falls into one of the three standing poses, and
     * this fades out underneath whichever one it is - so what the rehearsal covers is the three
     * seconds a pose takes to dawn, rather than the state itself. Kept rather than deleted
     * precisely because that gap wants filling: without it the arm would simply stop.
     *
     * <p>
     * It fades out as the real stroke fades in, so the two never add.
     */
    private static final float IDLE_SHARE = 0.34F;

    /**
     * How far a blow drives that arm back down, in radians.
     *
     * <p>
     * Further than the carry, so the arm passes through where it was hanging and finishes slightly
     * behind - a rammer driven down, rather than a sword swung across.
     */
    private static final float BLOW = 0.95F;

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart armRight;
    private final ModelPart armLeft;
    private final ModelPart legRight;
    private final ModelPart legLeft;

    /** Shoulder plates, neck strap and chest plaque - drawn only when an upgrade is fitted. */
    private final ModelPart mantle;

    /** A cairn stone on the capstone - drawn only for the last upgrade of all. */
    private final ModelPart crown;

    /**
     * How far into this tick the frame is.
     *
     * <p>
     * The only thing kept between the two calls, because it is the only thing the pose needs that
     * the pose is not handed. Everything else is read off the entity where the angles are set, so
     * that a second draw of the same model - the flash a hurt or dying golem gets, or the glow pass
     * over its eyes - poses itself from that golem rather than from whichever one was drawn last.
     */
    private float partial;

    /**
     * Which upgrade is fitted, remembered for the same reason {@link #partial} is.
     *
     * <p>
     * The other edition reads this off the entity in the middle of drawing, because there posing and
     * drawing are one call. Here they are two and only the first is handed the golem, so the draw
     * would have had nothing to ask. Remembered in {@code setupAnim}, which runs immediately before
     * every draw of this model - including the second draw a hurt or dying golem gets and the glow
     * pass over its eyes, each of which poses itself from its own golem first.
     */
    private GolemUpgrade fitted = GolemUpgrade.NONE;

    public ModelGolemOfWays() {
        texWidth = 128;
        texHeight = 128;

        // Head: a stone slab under an overhanging capstone, with a waystone set in its face.
        head = new ModelPart(this);
        head.setPos(0F, -7F, -2F);
        head.texOffs(0, 0)
            .addBox(-4.5F, -11F, -5.5F, 9, 9, 8);
        head.texOffs(34, 0)
            .addBox(-5.5F, -12F, -6.5F, 11, 2, 10);
        head.texOffs(76, 0)
            .addBox(-1.5F, -6F, -8F, 3, 5, 3);

        crown = new ModelPart(this);
        crown.setPos(0F, -7F, -2F);
        crown.texOffs(98, 18)
            .addBox(-3F, -13F, -5F, 6, 2, 6);

        // Body: rammed earth, turf across the shoulders, a cobbled apron at the hem.
        //
        // Sixteen across and ten deep, down from eighteen and eleven. It was built to the iron
        // golem's own barrel and read as a wall from behind, because the iron golem is not also
        // carrying a slab of turf and a pair of shoulder plates on top of that width. The depth
        // came off the back and only the back: the chest, both shoulder plates and the strap
        // behind the neck all stop further forward than they did, and the front of the golem is
        // exactly where it was.
        body = new ModelPart(this);
        body.setPos(0F, -7F, 0F);
        body.texOffs(0, 18)
            .addBox(-8F, -2F, -6F, 16, 12, 10);
        body.texOffs(0, 73)
            .addBox(-8.5F, -3F, -6.5F, 17, 1, 11);
        body.texOffs(68, 59)
            .addBox(-4.5F, 10F, -3F, 9, 5, 6, 0.5F);

        // Mantle: two shoulder plates, a strap behind the neck, a plaque on the chest.
        mantle = new ModelPart(this);
        mantle.setPos(0F, -7F, 0F);
        mantle.texOffs(58, 18)
            .addBox(-10.5F, -5.5F, -6F, 6, 3, 11);
        mantle.texOffs(40, 42)
            .addBox(4.5F, -5.5F, -6F, 6, 3, 11);
        mantle.texOffs(58, 35)
            .addBox(-4.5F, -5.5F, 1F, 9, 3, 4);
        mantle.texOffs(98, 26)
            .addBox(-4F, -0.5F, -6.75F, 8, 6, 1);

        // Right arm: shaft, wrist, and the blunt fist your tamper is carried in.
        armRight = new ModelPart(this);
        armRight.setPos(0F, -7F, 0F);
        armRight.texOffs(0, 42)
            .addBox(-12F, -2.5F, -3F, 4, 24, 6);
        armRight.texOffs(104, 0)
            .addBox(-11.5F, 21.5F, -2.5F, 3, 2, 5);
        armRight.texOffs(40, 59)
            .addBox(-13.5F, 23.5F, -4.5F, 5, 4, 9);

        // Left arm: shaft, wrist, and the golem's own tamper plate.
        armLeft = new ModelPart(this);
        armLeft.setPos(0F, -7F, 0F);
        armLeft.texOffs(20, 42)
            .addBox(8F, -2.5F, -3F, 4, 24, 6);
        armLeft.texOffs(88, 0)
            .addBox(8.5F, 21.5F, -2.5F, 3, 4, 5);
        armLeft.texOffs(86, 35)
            .addBox(8.5F, 25.5F, -6F, 6, 2, 12);

        // Both hands sit outboard of their own shaft rather than centred on it, and that is what
        // the narrower body cost. A fist five wide and a plate six wide on a shaft four wide have
        // to overhang somewhere, and the inboard side is where the feet are: centred, the plate
        // stood half a unit inside the right leg at all times and the fist swung a full unit
        // through the left foot every stride. Hung outboard, every pose in the animation clears -
        // measured, not eyeballed, by running the real boxes through a separating-axis test over a
        // whole stride in each of the six states.

        // Legs: short and thick, on broad tread plates.
        legLeft = new ModelPart(this);
        legLeft.setPos(-4F, 11F, 0F);
        legLeft.texOffs(62, 73)
            .addBox(-3.5F, -3F, -3F, 6, 13, 5);
        legLeft.texOffs(0, 92)
            .addBox(-4.5F, 10F, -4.5F, 8, 3, 9);

        legRight = new ModelPart(this);
        legRight.setPos(5F, 11F, 0F);
        legRight.texOffs(84, 73)
            .addBox(-3.5F, -3F, -3F, 6, 13, 5);
        legRight.texOffs(34, 92)
            .addBox(-4.5F, 10F, -4.5F, 8, 3, 9);
    }

    /**
     * Picks up the one thing the pose needs that the frame does not carry.
     *
     * <p>
     * This is the only hook a model is handed the fraction of a tick in, which a raise and a blow
     * both need if they are to look like movement rather than stepping.
     */
    @Override
    public void prepareMobModel(EntityGolemOfWays entity, float limbSwing, float limbSwingAmount,
        float partialTick) {
        super.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
        this.partial = partialTick;
    }

    /**
     * Draws it, wearing whatever it has been fitted with.
     *
     * <p>
     * The upgrade rides in on the entity's watched value, so the extra geometry appears and
     * vanishes the moment one goes in - which together with the per-upgrade skin is what makes each
     * one look like itself.
     */
    @Override
    public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack pose,
        com.mojang.blaze3d.vertex.VertexConsumer into, int light, int overlay, float red, float green,
        float blue, float alpha) {
        head.render(pose, into, light, overlay);
        body.render(pose, into, light, overlay);
        legLeft.render(pose, into, light, overlay);
        legRight.render(pose, into, light, overlay);
        armRight.render(pose, into, light, overlay);
        armLeft.render(pose, into, light, overlay);

        if (fitted != GolemUpgrade.NONE) mantle.render(pose, into, light, overlay);
        if (fitted.carriesTheSet()) crown.render(pose, into, light, overlay);
    }

    /**
     * Its stride, its work stroke, its blow, and the hunch it runs in.
     *
     * <p>
     * The walk is the iron golem's own triangle wave rather than a sine, because that stiff
     * lurching stomp is half of what makes a golem read as a golem; softening it into a smooth
     * swing would make it walk like a villager.
     *
     * <p>
     * Everything else happens on one arm - the right, the one the tool is in - and it is one motion
     * at three strengths rather than three motions that then have to be kept from colliding.
     * Standing still with a tamper and nothing to do, it plays a third of the stroke: a rehearsal,
     * enough to tell a stocked golem from an empty one across a field. Actually wearing or mending
     * ground, it plays all of it, on the clock its work really runs on, so a swift one visibly
     * works twice as often. Fighting, the stroke stops and the arm is held up and driven down
     * through a blow instead, which is a different shape and ought to look like one.
     *
     * <p>
     * The left arm counterweights all three at about a fifth and the head follows the work down. An
     * arm frozen solid against a walking body looks broken, so most of the walk is taken out of the
     * working arm while it is up rather than all of it.
     */
    @Override
    public void setupAnim(EntityGolemOfWays entity, float limbSwing, float limbSwingAmount, float age,
        float yaw, float pitch) {
        // Noted here because the draw is not handed the golem; see the field.
        fitted = upgradeOf(entity);

        float step = stride(limbSwing, 13F);

        legLeft.xRot = -1.5F * step * limbSwingAmount;
        legRight.xRot = 1.5F * step * limbSwingAmount;
        legLeft.yRot = 0F;
        legRight.yRot = 0F;

        armRight.xRot = (-0.2F + 1.5F * step) * limbSwingAmount;
        armLeft.xRot = (-0.2F - 1.5F * step) * limbSwingAmount;

        head.yRot = yaw / DEG;
        head.xRot = pitch / DEG;

        // Every one of these is a blend rather than a fact, and that is on purpose. Whether the
        // golem is fighting and whether it is holding a tamper are both yes-or-no, but a pose that
        // reads them as yes-or-no moves between two frames instead of over them - forty-nine
        // degrees of working arm when a fight ends, twenty-five when a tamper breaks. They are
        // eased on the entity, where each golem has somewhere to keep the blend.
        float raise = 0F;
        float drive = 0F;
        float fight = 0F;
        float flee = 0F;
        float armed = 0F;
        float chew = 0F;
        float settled = 0F;
        int pose = GolemCombat.IDLE_NONE;
        float phase = (age % EntityGolemOfWays.STROKE_TICKS) / EntityGolemOfWays.STROKE_TICKS;
        if (entity instanceof EntityGolemOfWays) {
            EntityGolemOfWays golem = (EntityGolemOfWays) entity;
            phase = golem.strokePhase(partial);
            raise = golem.heldRaise(partial);
            flee = golem.fleeLean(partial);
            armed = golem.armedShow(partial);
            fight = golem.fightLean(partial);
            chew = golem.chewShow(partial);
            float blow = golem.blowProgress(partial);
            drive = blow <= 0F ? 0F : Mth.sin(blow * (float) Math.PI) * BLOW;
            // Already damped by the walk, the work, the fight and the flight, on the entity, where
            // the renderer reads the same number. It must not be multiplied by any of them again
            // here, and in particular not by still.
            settled = golem.idleShow(partial);
            pose = golem.idlePose();
        }

        // Only while it is actually standing, and blended out as soon as it takes a step.
        float still = 1F - Math.min(1F, limbSwingAmount * 5F);
        float working = raise * (1F - fight);
        // The rehearsal now covers only the handover into a standing pose, so it goes out as that
        // pose comes in. Multiplied by the settled figure rather than gated on the pose, because
        // both are eased and a gate would put a step where the crossfade is.
        float idle = IDLE_SHARE * (1F - raise) * armed * (1F - settled);
        float strength = still * Math.max(working, idle) * (1F - flee);

        float lift = 0F;
        if (strength > 0.001F) {
            if (phase < LIFT_END) lift = phase / LIFT_END;
            else if (phase < FALL_END) lift = 1F - (phase - LIFT_END) / (FALL_END - LIFT_END);
            lift *= strength;
        }

        // An armed golem holds its tamper up whether it is using it or not, because an arm this
        // long puts a held item in the ground otherwise. Work lifts it further, and a fight lifts
        // it further still before driving it down through.
        float held = raise * fight;
        float hold = Math.max(raise, armed);
        armRight.xRot *= 1F - hold * 0.6F;
        armRight.xRot -= CARRY * hold;
        armRight.xRot -= lift * SWING;
        armRight.xRot -= (FIGHT_LIFT - drive) * held;
        armLeft.xRot += lift * 0.20F + (0.22F - drive * 0.28F) * held;
        head.xRot += lift * 0.14F + drive * 0.10F * held;

        // Eating. Both arms come up together and the head dips to meet them, then the pair work
        // in a small cycle for as long as the mouthful lasts. Laid over whatever the walk and the
        // carry have already put there rather than replacing it, so a golem that eats while it
        // walks still walks - and blended by the same eased figure as every other posture here,
        // because a mouthful that snapped on would read as a different golem for half a second.
        float chewRoll = 0F;
        if (chew > 0F) {
            float bite = Mth.sin(age * 0.55F) * 0.16F * chew;
            armRight.xRot += (-1.35F - bite) * chew;
            armLeft.xRot += (-1.35F - bite) * chew;
            // Inward at the elbow rather than the shoulder roll the run had to give up: the carry
            // has already taken these past the horizontal, so a roll about the chest brings the
            // hands up the last stretch and in to either side of the head - twenty-seven units of
            // arm and no elbow in it cannot reach a mouth any other way. Worked out here and
            // written below, because the run assigns this axis outright a few lines further on.
            chewRoll = 0.42F * chew;
            head.xRot += (0.34F + bite * 1.6F) * chew;
        }

        // Running: both arms swung forward and the head down. Eased on the entity rather than
        // here, because this model is one object drawn for every golem on the screen and can hold
        // nothing of its own - and a posture that snapped on would read as the golem being swapped
        // for a different golem.
        //
        // The arms were also rolled inward, and that had to go. They turn about the middle of the
        // chest rather than about a shoulder, so the hands hang twenty-seven units below the pivot
        // and an eighth of a radian swings them three and a third units in - through legs that
        // only stand seven and a half out. Every stride, the plate passed through a foot. The roll
        // is left assigned to zero rather than deleted so that the reason it is zero is written
        // where somebody would otherwise add it back.
        // The swing is damped before the lean is added, not after, and that is the whole of it.
        // Added on top of an undamped stride the lean took the arms to a hundred and twenty-nine
        // degrees - past horizontal, up towards the head, which is a pose for flying rather than
        // for running. Damped first, the arms hold forward through a shortened stride and never
        // leave the range an ordinary walk already uses: vanilla's own iron golem reaches
        // ninety-seven degrees on a plain walk, and this now stops just inside that.
        armRight.xRot *= 1F - flee * 0.35F;
        armLeft.xRot *= 1F - flee * 0.35F;
        armRight.xRot -= 0.55F * flee;
        armLeft.xRot -= 0.55F * flee;
        // The one place this axis is set, and everything that wants a roll is gathered into it.
        // The run needs it assigned rather than added, for the reason written above; a mouthful
        // needs it kept, and from 0.9.182 until now it was not - the roll was worked out where the
        // rest of that pose is set, twenty lines above a line that wiped it, so the arms went up
        // to the horizontal and stopped there with the hands a shoulder's width either side of a
        // head they were supposed to be reaching. Damped by the run on the way through, because a
        // golem sprinting off its road is not holding anything up to its face.
        float roll = chewRoll * (1F - flee);
        armRight.zRot = roll;
        armLeft.zRot = -roll;
        head.xRot += 0.22F * flee;

        // Standing about, in whichever of the three ways. After the run, because these are what a
        // golem does when it is doing nothing and the run is the loudest thing it can be doing;
        // before the crown, so a bowed head takes its capstone with it.
        //
        // Two of the three are here and the third is in the renderer, which is not an inconsistency
        // but the shape of the thing: nothing in this model is a child of anything else, so there
        // is no box that means "all of it", and a pose that moves the whole golem can only be moved
        // by whoever holds the matrix.
        //
        // The head is the part that carries at range. Only the eyes, the face marker and the chest
        // sigil are lit, so at dusk or across a field a pose is whatever the head is doing - which
        // is why the untold bow is deep and dead still, and the short-of-means dip rides the cast
        // rather than sitting at a different depth. Amplitude alone would be one gesture at two
        // sizes; stillness against movement survives the dark.
        float cast = 0F;
        if (pose == GolemCombat.IDLE_UNTOLD && settled > 0F) {
            // A bow that holds, with a breath on it so it is a golem waiting rather than a statue.
            head.xRot += (BOW + BREATH * Mth.sin(age * 0.021F)) * settled;
        } else if (pose == GolemCombat.IDLE_SHORT && settled > 0F) {
            cast = castAt(age) * settled;
            // Out from the hip and back, on the arm that carries the golem's own plate rather than
            // the one that holds a tamper - so it reads as showing you an empty hand, and cannot be
            // mistaken for the work stroke, which is the other arm, another axis, and a fifth of
            // this period.
            armLeft.zRot -= CAST * cast;
            armLeft.xRot -= 0.10F * cast;
            head.xRot += 0.18F * cast;
        }

        // Assigned every frame whatever the pose, because one of these objects is drawn for every
        // golem on the screen and again for the glow pass: a rotation point written only sometimes
        // is one golem's arm hung on the next golem's shoulder.
        //
        // Without it the roll is a pendulum about the middle of the chest rather than about a
        // shoulder, and the plate's top corner swings up clear of the shoulder line into open air.
        // These put the pivot back where the joint is; both terms are exactly zero and minus seven
        // when nothing is cast.
        float swung = -CAST * cast;
        float cos = Mth.cos(swung);
        float sin = Mth.sin(swung);
        armLeft.x = 10F - (10F * cos + 2.5F * sin);
        armLeft.y = -7F + (-2.5F - 10F * sin + 2.5F * cos);

        crown.xRot = head.xRot;
        crown.yRot = head.yRot;
    }

    /**
     * The cast's envelope: out over a second, held, back over the best part of two, then a rest.
     *
     * <p>
     * Asymmetric on purpose. Out is a gesture and comes quickly; back is the arm being let down and
     * takes longer than it went; and the pause afterwards is what stops it reading as a machine
     * cycling. The whole thing is two and a quarter times the length of a work stroke, which is
     * most of what keeps the two from being confused at a distance.
     */
    private static float castAt(float age) {
        float at = age % CAST_TICKS;
        if (at < 20F) return at / 20F;
        if (at < 35F) return 1F;
        if (at < 70F) return 1F - (at - 35F) / 35F;
        return 0F;
    }

    /**
     * Leaves the matrix at the fist, for the renderer to hang a tool off.
     *
     * <p>
     * The right one, and that is what the two arms were drawn differently for. The right arm is the
     * one that strokes and the one a blow drives down, so the tool it was handed goes in that fist
     * and moves with the work; the golem's own tamper plate is on the left, where it is part of the
     * shape without ever reading as a second tool.
     */
    public void postRenderToolHand(com.mojang.blaze3d.vertex.PoseStack pose) {
        armRight.translateAndRotate(pose);
    }

    private static GolemUpgrade upgradeOf(Entity entity) {
        return entity instanceof EntityGolemOfWays ? ((EntityGolemOfWays) entity).fittedUpgrade() : GolemUpgrade.NONE;
    }

    /** The iron golem's triangle wave: a stomp with a flat top, not a swing. */
    private static float stride(float time, float period) {
        return (Math.abs(time % period - period * 0.5F) - period * 0.25F) / (period * 0.25F);
    }
}
