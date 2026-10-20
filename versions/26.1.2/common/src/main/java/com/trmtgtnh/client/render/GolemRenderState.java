package com.trmtgtnh.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;

import com.trmtgtnh.entity.GolemCombat;
import com.trmtgtnh.entity.GolemUpgrade;

/**
 * What one Golem of Ways looks like in one frame, read off it before it is drawn.
 *
 * <p>
 * From 1.21.2 an entity is not handed to its renderer, its model or its layers: the renderer reads what they will
 * need into a state of this kind, and they draw from that alone. So everything 1.21.1's model, renderer and two
 * layers asked the golem as they drew is asked once here, with the fraction of a tick already folded in, exactly as
 * each of them asked it - and every number they work from is the one they worked from then.
 */
public class GolemRenderState extends LivingEntityRenderState {

    /** Which upgrade is fitted: the mantle, the crown and the skin all follow it. */
    public GolemUpgrade fitted = GolemUpgrade.NONE;

    /** The skin and the glow, chosen as 1.21.1 chose them, off the world clock for the turning ones. */
    public Identifier skin;

    public Identifier glow;

    /** The walk, read straight off the golem as 1.21.1's renderer read it - dead or riding included. */
    public float walkSpeed;

    public float walkPos;

    /** The work stroke and the postures, each eased on the golem and read at this frame's fraction of a tick. */
    public float strokePhase;

    public float heldRaise;

    public float fleeLean;

    public float armedShow;

    public float fightLean;

    public float chewShow;

    public float blowProgress;

    public float idleShow;

    public int idlePose = GolemCombat.IDLE_NONE;

    /** The tool in its fist, resolved to its model as 1.21.1's item renderer resolved it when drawing. */
    public final ItemStackRenderState tool = new ItemStackRenderState();
}
