package com.trmtgtnh.apiload;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.api.Wear;
import com.trmtgtnh.api.WearContext;
import com.trmtgtnh.apihost.PluginHost;
import com.trmtgtnh.apihost.WearHere;
import com.trmtgtnh.apihost.WearRecord;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.erosion.ErosionEngine;
import com.trmtgtnh.erosion.ErosionEntry;
import com.trmtgtnh.erosion.ErosionStore;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * Where the 26.1.2 engine reaches the plugins' hooks (W3 spec 2.5, "Where the hooks sit"; task T7, carried in T19).
 *
 * <p>
 * <strong>The points.</strong> {@code ErosionEngine.step}, the one place every kind of wear arrives, asks the ground
 * rules and then the listeners' {@code beforeWear} once it knows the ground there wears and is open, before anything
 * is banked; a golem or a command working ground deeper through {@code forceStage} asks the same. A record's change -
 * a step, healing, a mend, a forced stage, a pin, a record forgotten - is told to {@code afterChange} with the record
 * before and after. A mob's multiplier is put to the wearer rules after TRMTR has worked out its own and before the
 * player's mob list is applied ({@code ErosionEngine.multiplierFor}).
 *
 * <p>
 * <strong>The cause.</strong> {@code step} is not told who walked there, so each way in says what it is before it
 * calls: {@link #enter} with the cause and the entity, {@link #leave} in a {@code finally}. Nested calls (a listener
 * calling {@code Actions}) stack. Server thread only, as the engine is.
 *
 * <p>
 * <strong>Chunk loading.</strong> Nothing of a plugin's runs inside a chunk load, which {@link #enterLoad} and
 * {@link #leaveLoad} mark: the one place the engine reaches a hook from one is the heal a chunk is caught up with as it
 * arrives ({@code ErosionEngine.catchUpChunk}), and what it changes is held and told on the server's next tick
 * ({@link #deliverHeld}, {@code PluginHost}; API.md, "Chunk loading"). No hook point is reached on a chunk's unload.
 *
 * <p>
 * <strong>What it costs with no plugin.</strong> One volatile read per question: each point asks {@link #asks},
 * {@link #listening} or {@link #rulesWearers} first, and builds nothing when the answer is no.
 */
public final class ApiHooks {

    private ApiHooks() {}

    private static final int MOST_NESTED = 16;
    private static final WearContext.Cause[] CAUSES = new WearContext.Cause[MOST_NESTED];
    private static final Object[] ENTITIES = new Object[MOST_NESTED];
    private static int nested;

    /** Says what the wear about to be called for is, and who is doing it (null for nothing walking). */
    public static void enter(WearContext.Cause cause, Object entity) {
        if (nested < MOST_NESTED) {
            CAUSES[nested] = cause;
            ENTITIES[nested] = entity;
        }
        nested++;
    }

    /** Ends what {@link #enter} began; called in a {@code finally}. */
    public static void leave() {
        if (nested <= 0) return;
        nested--;
        if (nested < MOST_NESTED) {
            CAUSES[nested] = null;
            ENTITIES[nested] = null;
        }
    }

    /** The cause of the wear being worked now; a step when nothing said otherwise (the harness's own steps). */
    static WearContext.Cause cause() {
        int at = Math.min(nested, MOST_NESTED) - 1;
        return at >= 0 && CAUSES[at] != null ? CAUSES[at] : WearContext.Cause.STEP;
    }

    private static Object entity() {
        int at = Math.min(nested, MOST_NESTED) - 1;
        return at >= 0 ? ENTITIES[at] : null;
    }

    private static PluginHost host() {
        return ApiLoad.host();
    }

    /** Whether any ground rule or listener is to be asked before wear lands. */
    public static boolean asks() {
        return host().asksBeforeWear();
    }

    /** Whether any listener is to be told of a change, so a record is worth reading before it changes. */
    public static boolean listening() {
        return host().listens();
    }

    /** Whether any wearer rule is to be asked. */
    public static boolean rulesWearers() {
        return host().rulesWearers();
    }

    /**
     * The amount of wear to land at a position, after the ground rules and the listeners: 0 when a rule refuses it or
     * a listener cancels it. Called by {@code ErosionEngine.step}.
     */
    public static float beforeWear(Level world, int x, int y, int z, BlockState state, float amount) {
        PluginHost host = host();
        if (!host.asksBeforeWear()) return amount;
        String id = ApiBridge.blockText(state);
        Object entity = entity();
        if (!host.mayWear(world, x, y, z, id, entity)) return 0f;
        return host.beforeWear(new WearHere(world, x, y, z, id, entity, cause()), amount);
    }

    /**
     * Whether a golem or a command may force ground there deeper, as the ground rules and the listeners answer; asked
     * by {@code ErosionEngine.forceStage} only for those two causes and only when the move deepens the ground. The
     * amount a listener is shown is the number of steps the ground is moved by, and only a cancel (0) is heeded: a
     * forced stage has no amount to scale.
     */
    public static boolean mayForce(Level world, int x, int y, int z, ErosionEntry entry, ErosionChain.Chain chain,
        int chainIndex) {
        WearContext.Cause cause = cause();
        if (cause != WearContext.Cause.GOLEM && cause != WearContext.Cause.COMMAND) return true;
        PluginHost host = host();
        if (!host.asksBeforeWear()) return true;
        int from = entry == null || !entry.isVisible() ? -1
            : chain.indexOf(entry.getFamily(), entry.getStage(), entry.getSink());
        if (chainIndex <= from) return true;
        String id = ApiBridge.blockText(com.trmtgtnh.util.Worlds.stateAt(world, x, y, z));
        Object entity = entity();
        if (!host.mayWear(world, x, y, z, id, entity)) return false;
        return host.beforeWear(new WearHere(world, x, y, z, id, entity, cause), chainIndex - from) > 0f;
    }

    /** The record at a position on the server as the API hands it, or null where there is none drawn. Never throws. */
    public static Wear wearAt(Level world, int x, int y, int z) {
        try {
            return wearOf(
                world,
                x,
                y,
                z,
                ErosionStore.get()
                    .getEntry(world, x, y, z));
        } catch (RuntimeException unread) {
            return null;
        }
    }

    /** A server record as the API hands it, its chain read from the block standing there. Never throws. */
    public static Wear wearOf(Level world, int x, int y, int z, ErosionEntry entry) {
        if (entry == null || !entry.isVisible()) return null;
        try {
            return ApiBridge
                .serverWearOn(entry, com.trmtgtnh.util.Worlds.stateAt(world, x, y, z));
        } catch (RuntimeException unread) {
            return null;
        }
    }

    /**
     * A server record as the API hands it, with the chain its block follows known: its step and steps counted on the
     * block's own chain where a data file gives it one (W3 T9). Never throws.
     */
    public static Wear wearOf(ErosionEntry entry, ErosionChain.Chain chain) {
        if (entry == null || !entry.isVisible()) return null;
        try {
            return ApiBridge.serverWear(entry, chain);
        } catch (RuntimeException unread) {
            return null;
        }
    }

    /** Tells the listeners a record changed, when it looks different now; either side null where there is none. */
    public static void changed(Level world, int x, int y, int z, Wear before, Wear after) {
        if (WearRecord.sameLook(before, after)) return;
        host().afterChange(world, x, y, z, before, after);
    }

    /**
     * This thread enters a chunk load: until {@link #leaveLoad}, a change is held for the server's next tick and no
     * rule is asked. A chunk's load event comes while the chunk is still being finished, and a listener's read of it
     * from there waits on the very thread it is running on: the dev 1.16.5 Fabric server parked for ever in the
     * example's afterChange (0.9.229, W3).
     */
    public static void enterLoad() {
        host().enterLoading();
    }

    /** Ends what {@link #enterLoad} began; called in a {@code finally}. */
    public static void leaveLoad() {
        host().leaveLoading();
    }

    /** Tells the listeners what waited for this tick; called at the end of each server tick. */
    public static void deliverHeld() {
        host().deliverHeld();
    }

    /** Drops what waits, for when the server has stopped: the world each change names has gone. */
    public static void dropHeld() {
        host().dropHeld();
    }

    /**
     * Everything about a record a hook could change, as one value to compare across a call into a plugin (Codex review
     * R16): whether it is there, its packed state (family, stage, depth, pin and reinforcement, as a client is sent
     * it), its ward and its banked wear. A record that reads the same before and after the call was left alone; one
     * that does not was forgotten, pinned or worn by the hook, and what the caller read before the call no longer
     * describes it.
     */
    public static String mark(ErosionEntry entry) {
        if (entry == null) return "none";
        return entry.packState() + "/" + entry.getWard() + "/" + Float.floatToIntBits(entry.getWear());
    }

    /**
     * Changes kept to be told once a sweep has finished with its records, so no listener is called while the sweep
     * still holds one (Codex review R16): a listener may forget, pin or wear any record, and the sweep reads each one
     * afresh only at the top of its loop.
     */
    public static final class Changes {

        private final java.util.List<Object[]> held = new java.util.ArrayList<Object[]>();

        /** Keeps one change, when it looks different; either side null where there is none. */
        public void add(int x, int y, int z, Wear before, Wear after) {
            if (WearRecord.sameLook(before, after)) return;
            held.add(new Object[] { x, y, z, before, after });
        }

        /** Tells the listeners every change kept, in order. */
        public void tell(Level world) {
            for (Object[] each : held) {
                host().afterChange(
                    world,
                    (Integer) each[0],
                    (Integer) each[1],
                    (Integer) each[2],
                    (Wear) each[3],
                    (Wear) each[4]);
            }
            held.clear();
        }
    }

    /** A mob's multiplier after the wearer rules, given the one TRMTR worked out. */
    public static float wearer(LivingEntity entity, float current) {
        PluginHost host = host();
        if (!host.rulesWearers()) return current;
        String id;
        try {
            // The mob's registry id, ns:name, as API.md says the edition spells an entity.
            id = ErosionEngine.dataIdOf(entity);
        } catch (RuntimeException unnamed) {
            id = null;
        }
        return host.multiplier(entity, id, current);
    }
}
