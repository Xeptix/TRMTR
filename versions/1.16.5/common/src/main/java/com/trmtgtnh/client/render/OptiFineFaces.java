package com.trmtgtnh.client.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.Trmt;

/**
 * The step a settled snow layer or carpet shares with a neighbour at another height, kept under OptiFine (0.9.221).
 *
 * <p>
 * OptiFine draws a block's faces through its own test, {@code net.optifine.util.BlockUtils.shouldSideBeRendered},
 * and where the neighbour can occlude it answers from a cache keyed by the two blocks' states and the face alone -
 * never by where they stand. Two snow layers of one depth hide the side between them, which is right on level
 * ground and wrong at a rut's step, where they stand at different heights: so under OptiFine the higher layer's
 * side at every step was missing, and a thin line of the ground beneath showed instead (0.9.220's close-ups).
 * {@code MixinSettledFaces} keeps that face in vanilla's test and {@code MixinSettledFacesSodium} in Sodium's; this
 * is the same rule in OptiFine's, reached by a redirect of OptiFine's call in the block renderer it patches.
 *
 * <p>
 * <strong>Made to fail safe, at the user's asking</strong> (2026-10-09: "should OptiFine update later, we lose this fix
 * but not any major issues"). The redirect is {@code require = 0}, so an OptiFine that no longer makes the call
 * costs this fix and nothing else. OptiFine's own answer is always what is returned otherwise, asked through a
 * handle found at run time. And the rule is applied only on the OptiFine it was written against,
 * {@link #WRITTEN_FOR}: another says so once in the log and is answered exactly as OptiFine answers.
 */
public final class OptiFineFaces {

    /** The OptiFine this was written against, as its {@code net.optifine.Config.VERSION} says. */
    public static final String WRITTEN_FOR = "OptiFine_1.16.5_HD_U_G8";

    /** Whether the rule applies here - null until first asked. */
    private static volatile Boolean standing;

    /** OptiFine's own test, or null when it could not be found. */
    private static volatile MethodHandle original;

    private static volatile boolean looked;

    private static volatile boolean ranSaid;

    private OptiFineFaces() {}

    /** OptiFine's face test with the step kept: what the redirect in each loader's block renderer calls. */
    public static boolean decide(BlockState state, BlockGetter level, BlockPos pos, Direction face, Object env) {
        // Every face of every chunk built asks: the version is read once, until the answer is kept.
        Boolean held = standing;
        boolean on = held != null ? held.booleanValue() : applies(version());
        if (on && Settling.keepsFace(state, level, pos, face)) {
            noteRan();
            return true;
        }
        return optiFine(state, level, pos, face, env);
    }

    /** Whether the rule is applied under the OptiFine whose version this is. */
    static boolean applies(String version) {
        Boolean held = standing;
        if (held != null) return held.booleanValue();
        // Decided once: every chunk-building thread asks at once as the first world draws, and each said so.
        synchronized (OptiFineFaces.class) {
            held = standing;
            if (held != null) return held.booleanValue();
            held = Boolean.valueOf(WRITTEN_FOR.equals(version));
            standing = held;
            if (held.booleanValue()) {
                Trmt.LOG.info(
                    "OptiFine {}: a settled snow layer's or carpet's step is kept in OptiFine's face test",
                    version);
            } else {
                Trmt.LOG.info(
                    "OptiFine {}: the snow step fix is written for {} and stands down here - OptiFine decides every face",
                    version,
                    WRITTEN_FOR);
            }
        }
        return held.booleanValue();
    }

    /** Forgets what was decided. For tests. */
    static void forget() {
        standing = null;
    }

    /** OptiFine's version, read once from its own class, or "unknown". */
    private static String version() {
        try {
            Object said = Class.forName("net.optifine.Config")
                .getField("VERSION")
                .get(null);
            return said == null ? "unknown" : said.toString();
        } catch (Throwable e) {
            return "unknown";
        }
    }

    /**
     * OptiFine's own answer. Should its test not be found, vanilla's is the answer instead - the face test OptiFine
     * replaced, which is never wrong in a way the player can see.
     */
    @SuppressWarnings("deprecation")
    private static boolean optiFine(BlockState state, BlockGetter level, BlockPos pos, Direction face, Object env) {
        MethodHandle test = handle();
        if (test != null) {
            try {
                return ((Boolean) test.invoke(state, level, pos, face, env)).booleanValue();
            } catch (Throwable e) {
                // Fall through to vanilla's answer.
            }
        }
        return Block.shouldRenderFace(state, level, pos, face);
    }

    private static MethodHandle handle() {
        if (looked) return original;
        looked = true;
        try {
            Class<?> utils = Class.forName("net.optifine.util.BlockUtils");
            Class<?> env = Class.forName("net.optifine.render.RenderEnv");
            for (java.lang.reflect.Method each : utils.getMethods()) {
                Class<?>[] types = each.getParameterTypes();
                if (each.getName()
                    .equals("shouldSideBeRendered") && types.length == 5
                    && types[4] == env) {
                    original = MethodHandles.publicLookup()
                        .unreflect(each)
                        .asType(
                            MethodType.methodType(
                                boolean.class,
                                Object.class,
                                Object.class,
                                Object.class,
                                Object.class,
                                Object.class));
                    break;
                }
            }
        } catch (Throwable e) {
            original = null;
        }
        if (original == null) Trmt.LOG.warn("OptiFine's face test could not be found; vanilla's answers in its place");
        return original;
    }

    /** Said once, the first time a step is kept - the only evidence the redirect bound and ran at all. */
    private static void noteRan() {
        if (ranSaid) return;
        ranSaid = true;
        Trmt.LOG.info("Keeping a settled step's face in OptiFine's face test; the redirect is in place");
    }
}
