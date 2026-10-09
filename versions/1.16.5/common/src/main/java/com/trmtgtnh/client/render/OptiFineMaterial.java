package com.trmtgtnh.client.render;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.world.level.block.state.BlockState;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.mixin.OculusGate;

/**
 * The claim {@link ShaderMaterial} works out, said to OptiFine.
 *
 * <p>
 * OptiFine looks a block's shader id up once per block, from the shader pack's {@code block.properties},
 * and keeps the answer on a small stack carried by the chunk's own buffer: {@code BlockModelRenderer}
 * pushes it as a block's model starts and pops it as the model ends, and every vertex written in
 * between carries the top of the stack. Read with javap from OptiFine 1.16.5 HD U G8, its patched
 * classes rebuilt by its own patcher. A ghost's quads are built in between, so there the top of the
 * stack is the ghost's own id - a stranger's, to the pack - and that is the one entry this rewrites.
 *
 * <p>
 * <strong>Rewritten in place, never pushed over.</strong> A claim pushed on top would have to come off
 * again before OptiFine pops the ghost's own, at the end of a method this mod does not own; one missed
 * pop and every block after it on that buffer is drawn as the wrong material, and the stack walks off
 * its end. So OptiFine is asked to work the claimed block's entry out the way it works out every
 * block's - its own {@code pushEntity}, with the aliases, metadata and render type exactly as it reads
 * them - and the entry is read, popped straight off again, and written over the ghost's. Whichever step
 * fails, the stack is put back to the depth it was found at.
 *
 * <p>
 * The seat is taken by {@code MixinOptiFineShaderSeat}, at the end of OptiFine's own push and only for
 * a ghost; any other block's push gives it up. It remembers the depth it was taken at, and a claim is
 * only made at that depth, so quads asked for outside the block's own draw - an item, a particle - can
 * never write over an entry that belongs to somebody else. OptiFine pushes only onto a vanilla
 * {@code BufferBuilder}, so nothing else is ever seated.
 *
 * <p>
 * Nothing of OptiFine is named at compile time. The class is reached by the name the gate probes for,
 * and a client without OptiFine never has a seat, so this is never asked anything there. The first
 * reflective failure gives up for the session, as the Oculus half does.
 */
public final class OptiFineMaterial {

    private static Method push;

    private static Method pop;

    private static Field vertexBuilder;

    private static Field stack;

    private static Field depth;

    private static boolean looked;

    /** The buffer of the ghost this thread is drawing, where its entry sits, and what it was claimed as. */
    private static final ThreadLocal<Seat> SEAT = new ThreadLocal<Seat>();

    private static final class Seat {

        BufferBuilder buffer;

        int depth;

        BlockState claimed;

        /** Set while this thread's own push is in flight, which is not a new block arriving. */
        boolean claiming;

        /** The buffer this thread pushed a ghost's claim onto under OptiFabric, and the depth it pushed from. */
        BufferBuilder pushedOn;

        int pushedFrom;
    }

    private OptiFineMaterial() {}

    private static synchronized void look() {
        if (looked) return;
        looked = true;
        try {
            Class<?> builder = Class.forName(OculusGate.OPTIFINE_SEAT);
            push = builder.getMethod("pushEntity", BlockState.class, VertexConsumer.class);
            pop = builder.getMethod("popEntity", VertexConsumer.class);
            vertexBuilder = BufferBuilder.class.getField("sVertexBuilder");
            stack = builder.getDeclaredField("entityData");
            stack.setAccessible(true);
            depth = builder.getDeclaredField("entityDataIndex");
            depth.setAccessible(true);
            Trmt.LOG.info("Worn ground will keep the shader material of the block it covers, under OptiFine");
        } catch (Throwable notThere) {
            // An OptiFine that has moved one of these. Nothing is lost that was not already lost, so this
            // is worth one line rather than a stack trace.
            push = null;
            pop = null;
            vertexBuilder = null;
            stack = null;
            depth = null;
            Trmt.LOG.debug("OptiFine is present but has no block entry to claim: {}", notThere.toString());
        }
    }

    /**
     * Takes the seat for a ghost OptiFine has just pushed its own entry for.
     *
     * <p>
     * The depth is read here, once, because it is the one thing a claim must check: the quads are asked
     * for inside this block's draw, at this depth, and anywhere else the top of the stack is not the
     * ghost's.
     */
    public static void seat(VertexConsumer consumer) {
        noteReached();
        if (!TrmtConfig.inheritShaderMaterial) return;
        if (!(consumer instanceof BufferBuilder)) return;
        if (!looked) look();
        if (push == null) return;
        BufferBuilder buffer = (BufferBuilder) consumer;
        Seat seat = SEAT.get();
        if (seat == null) {
            seat = new Seat();
            SEAT.set(seat);
        }
        try {
            seat.depth = depth.getInt(vertexBuilder.get(buffer));
        } catch (Throwable awkward) {
            push = null;
            pop = null;
            vertexBuilder = null;
            stack = null;
            depth = null;
            Trmt.LOG.warn("Giving up on the shader material override under OptiFine: {}", awkward.toString());
            return;
        }
        seat.buffer = buffer;
        seat.claimed = null;
    }

    /**
     * Gives the seat up, because the block OptiFine has just pushed for is not one of ours - unless the
     * push was this class's own, made while claiming, which is not a block arriving at all.
     */
    public static void unseat() {
        noteReached();
        Seat seat = SEAT.get();
        if (seat == null || seat.claiming) return;
        seat.buffer = null;
        seat.claimed = null;
    }

    private static volatile boolean reachedSaid;

    /**
     * Said once, the first time OptiFine's push reaches the seat for any block at all.
     *
     * <p>
     * The one line that tells a seat never bound from a seat bound and never handed a ghost: the claim's
     * own line comes only with a ghost. Under OptiFabric on 2026-10-07 neither came. OptiFabric defines
     * OptiFine's classes itself, from a cache of its own and after Mixin has prepared every config, so no
     * mixin binds to one there - the log says "Error loading class: net/optifine/shaders/SVertexBuilder"
     * the moment one tries. Binding it would not have helped either: there Indigo draws the ghost and
     * cancels OptiFine's renderModel before OptiFine's push, so no push for a ghost ever arrives to seat.
     * OptiFabric has its own route since 0.9.220 - {@link #pushClaim}.
     */
    private static void noteReached() {
        if (reachedSaid) return;
        reachedSaid = true;
        Trmt.LOG.info("OptiFine's shader stack reaches the material seat");
    }

    /**
     * Whether OptiFine's shader stack is here to push onto at all, worked out once - so a renderer without OptiFine,
     * which is every Fabric client but OptiFabric's, pays one field read per square for asking.
     */
    public static boolean pushes() {
        if (!TrmtConfig.inheritShaderMaterial) return false;
        if (!looked) look();
        return push != null;
    }

    /**
     * Pushes the claimed block's entry for a ghost, the way OptiFine pushes any vanilla block's - for the one
     * arrangement where OptiFine pushes nothing for a ghost: OptiFabric, where Indigo hands the ghost's FRAPI model
     * the chunk's buffer straight from OptiFine's rebuild, past OptiFine's own push (GhostModelFabric,
     * OptiFabricEntry). Undone by {@link #popClaim} at the end of the same draw.
     *
     * <p>
     * Pushed rather than written over, unlike the seat, because here there is nothing to write over: the top of
     * the stack is the entry of whatever block was drawn before. The pop is in hand this time - the same method's
     * return, which nothing cancels - and it takes the stack back to the depth found here and never further.
     */
    public static void pushClaim(BlockState claim, VertexConsumer consumer) {
        if (!TrmtConfig.inheritShaderMaterial || claim == null) return;
        if (!(consumer instanceof BufferBuilder)) return;
        if (!looked) look();
        if (push == null) return;
        Seat seat = SEAT.get();
        if (seat == null) {
            seat = new Seat();
            SEAT.set(seat);
        }
        try {
            Object builder = vertexBuilder.get(consumer);
            int from = depth.getInt(builder);
            // Room for one more, or nothing is pushed at all: a full stack is OptiFine's to overflow.
            if (from + 1 >= ((long[]) stack.get(builder)).length) return;
            // Our own push is not a block arriving, so a seat bound beside this one must not give itself up.
            seat.claiming = true;
            try {
                push.invoke(null, claim, consumer);
            } finally {
                seat.claiming = false;
            }
            seat.pushedOn = (BufferBuilder) consumer;
            seat.pushedFrom = from;
            notePushed();
        } catch (Throwable awkward) {
            push = null;
            pop = null;
            vertexBuilder = null;
            stack = null;
            depth = null;
            Trmt.LOG.warn("Giving up on the shader material override under OptiFabric: {}", awkward.toString());
        }
    }

    /** Takes this thread's push back off the same buffer, to the depth it was made from and never further. */
    public static void popClaim(VertexConsumer consumer) {
        Seat seat = SEAT.get();
        if (seat == null || seat.pushedOn == null || seat.pushedOn != consumer) return;
        BufferBuilder buffer = seat.pushedOn;
        int from = seat.pushedFrom;
        seat.pushedOn = null;
        if (pop == null) return;
        try {
            Object builder = vertexBuilder.get(buffer);
            while (depth.getInt(builder) > from) pop.invoke(null, buffer);
        } catch (Throwable awkward) {
            push = null;
            pop = null;
            vertexBuilder = null;
            stack = null;
            depth = null;
            Trmt.LOG.warn("Giving up on the shader material override under OptiFabric: {}", awkward.toString());
        }
    }

    private static volatile boolean pushedSaid;

    /** Said once, so a run can tell "never reached" from "working" - a feature that never ran logs nothing. */
    private static void notePushed() {
        if (pushedSaid) return;
        pushedSaid = true;
        Trmt.LOG.info("Under OptiFabric, worn ground hands OptiFine the block it covers");
    }

    /** Whether this thread holds a ghost's seat - the only thing worth working a claim out for. */
    static boolean seated() {
        Seat seat = SEAT.get();
        return seat != null && seat.buffer != null;
    }

    /**
     * Writes the claim over the ghost's own entry, if this thread is still inside that ghost's draw.
     *
     * <p>
     * Asked once per face; the faces of one square want the same answer, so after the first it is a
     * reference compare.
     */
    static void claim(BlockState claim) {
        Seat seat = SEAT.get();
        if (seat == null || seat.buffer == null || claim == null || seat.claimed == claim) return;
        if (push == null) return;
        try {
            Object builder = vertexBuilder.get(seat.buffer);
            if (depth.getInt(builder) != seat.depth) return;
            // Room for one more, or nothing is pushed at all: a full stack is OptiFine's to overflow.
            if (seat.depth + 1 >= ((long[]) stack.get(builder)).length) return;
            long entry = 0L;
            boolean read = false;
            seat.claiming = true;
            try {
                push.invoke(null, claim, seat.buffer);
                long[] entries = (long[]) stack.get(builder);
                entry = entries[depth.getInt(builder)];
                read = true;
            } finally {
                // Whatever happened above, the stack goes back to the depth it was found at.
                while (depth.getInt(builder) > seat.depth) pop.invoke(null, seat.buffer);
                seat.claiming = false;
            }
            if (!read) return;
            ((long[]) stack.get(builder))[seat.depth] = entry;
            seat.claimed = claim;
        } catch (Throwable awkward) {
            // One failure is enough: asking again every face for the rest of the session would be the
            // expensive way to keep finding out that something has changed under us.
            push = null;
            pop = null;
            vertexBuilder = null;
            stack = null;
            depth = null;
            Trmt.LOG.warn("Giving up on the shader material override under OptiFine: {}", awkward.toString());
        }
    }
}
