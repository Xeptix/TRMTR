package com.trmtgtnh.mixin;

import java.util.List;
import java.util.Set;

import net.minecraft.launchwrapper.Launch;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Refuses the Oculus mixin on a client that has no Oculus.
 *
 * <p>
 * A mixin whose target class is nowhere is not a quiet no-op: Mixin reports it, and a mod that logs an
 * error on every client that simply does not have an optional dependency is a mod that has taught its
 * users to ignore its errors. This decides the question before Mixin is allowed to ask it.
 *
 * <p>
 * The check is a resource probe and deliberately not {@code Class.forName}. Asking the class loader
 * for the class would define it - and a target class already loaded is a target this mod's mixin can
 * never be applied to, so the very act of checking would be what broke the feature. Asking for the
 * bytes behind it answers the same question and loads nothing.
 *
 * <p>
 * Everything else in the config is waved through by name. This is the config's only gate because the
 * config's optional targets are all of one kind - another mod's class a ghost claims its shader material
 * through - and from 0.9.219 there are two: Oculus's holder, and OptiFine's vertex builder, refused the
 * same way on a client without OptiFine. Splitting a second config out for one mixin would buy a package
 * that overlaps the first one's.
 */
public final class OculusGate implements IMixinConfigPlugin {

    /**
     * Oculus's per-block shader-id holder, named once for the whole mod.
     *
     * <p>
     * It lives here rather than beside the code that uses it because this class is the only one of
     * the three that is not client-only, and a constant is inlined wherever it is read - so the
     * client-only reference scan sees three classes naming a string rather than a server path
     * reaching for a client class.
     */
    public static final String HOLDER = "net.coderbot.iris.compat.sodium.impl.block_context.BlockContextHolder";

    /**
     * OptiFine's vertex builder, whose static {@code pushEntity} puts a block's shader id on the buffer's
     * stack - named once for the whole mod, here, for the same reason as {@link #HOLDER}.
     */
    public static final String OPTIFINE_SEAT = "net.optifine.shaders.SVertexBuilder";

    /** The mixin that needs Oculus. */
    private static final String GATED = "MixinOculusBlockContext";

    /** The mixin that needs OptiFine. */
    private static final String GATED_OPTIFINE = "MixinOptiFineShaderSeat";

    /**
     * The mixins that wrap a call through MixinExtras, each with its redirect form (G17 R5, 2026-11-11).
     *
     * <p>
     * MixinExtras comes in MixinBooter's jar. A pack that boots its own Mixin and has no MixinBooter - RLCraft 2.9.3,
     * whose MixinBooter could not start - has none, and there a wrapped hook would be lost. So there the redirect form
     * applies instead: the same call in the same methods, answered the same way, at the one cost of a redirect - a
     * second mod redirecting the same call is refused by Mixin rather than chained. Where MixinExtras runs, the
     * wrapped form applies, as it always has, and the redirect form never does. One of each pair, never both: both
     * would answer the call twice. Runs, not merely is there: where the loading plugin starts MixinExtras, its word on
     * whether the start succeeded decides ({@code TrmtLoadingPlugin.mixinExtrasRuns}), so a jar that will not start
     * under the game's Mixin gets the redirect forms (Codex's review of R5, 2026-11-11).
     */
    public static final String[][] WRAPPED = { { "MixinGhostNeighbourLightCache", "MixinGhostNeighbourLightCacheRedirected" },
        { "MixinGhostNeighbourLightWorld", "MixinGhostNeighbourLightWorldRedirected" },
        { "MixinGhostShadeVanilla", "MixinGhostShadeVanillaRedirected" },
        { "MixinStairNeighbours", "MixinStairNeighboursRedirected" } };

    /**
     * The priority of the ghost's neighbour-light mixins, both forms (G17, the W4 census's E2E crash, 2026-11-11).
     *
     * <p>
     * Lighting engines overwrite the light reads those mixins inject into: Alfheim 1.6 overwrites
     * {@code World.getLightFromNeighborsFor}, {@code World.getLight(BlockPos, boolean)} and
     * {@code ChunkCache.getLightForExt} at the default priority, 1000, and VintageFix 0.7.1 overwrites the last at 500.
     * Mixin merges every overwrite before any injection is prepared, whatever their priorities, and then lets an
     * injection at a call into an overwritten method only when the overwrite's priority is below the injecting
     * mixin's; {@code require = 0} does not spare it. At the default the game stopped beside Alfheim before its first
     * screen. Above every overwrite below this, the injection is let in: where the overwrite kept the read, as
     * VintageFix's does, it is answered as before; where it did not, as Alfheim's does, nothing is found and nothing
     * fails, and the ghost answers through Alfheim's own question instead ({@code BlockGhost.alfheim$useNeighborBrightness}).
     * An overwrite at this priority or above would still stop the game; none of the 1.12.2 mods read had one.
     */
    public static final int ABOVE_OVERWRITES = 2000;

    /** MixinExtras' start, as the loading plugin names it: its bytes are what is asked for, never the class. */
    static final String MIXINEXTRAS = "com.llamalad7.mixinextras.MixinExtrasBootstrap";

    private boolean present;

    private boolean optiFinePresent;

    private boolean extras;

    @Override
    public void onLoad(String mixinPackage) {
        present = there(HOLDER);
        optiFinePresent = there(OPTIFINE_SEAT);
        extras = com.trmtgtnh.core.TrmtLoadingPlugin.mixinExtrasRuns(there(MIXINEXTRAS));
        // Said, because a gate that shut is otherwise silent: the seat it holds back never logs a word,
        // and worn ground then simply draws without its shader material. The 1.16.5 edition's line.
        org.apache.logging.log4j.LogManager.getLogger("TRMT: Reimagined")
            .info(
                "Shader material seats at mixin load: Oculus or Iris {}, OptiFine {}",
                present ? "found" : "not found",
                optiFinePresent ? "found" : "not found");
        org.apache.logging.log4j.LogManager.getLogger("TRMT: Reimagined")
            .info(
                "MixinExtras at mixin load: {}, so the {} wrapped hooks apply in their {} forms",
                extras ? "running" : "not running",
                WRAPPED.length,
                extras ? "wrapped" : "redirect");
    }

    private static boolean there(String className) {
        try {
            return Launch.classLoader.getResource(className.replace('.', '/') + ".class") != null;
        } catch (Throwable noLoader) {
            // Nothing here is worth failing a mixin config over. Absent is the safe answer.
            return false;
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return applies(mixinClassName, present, optiFinePresent, extras);
    }

    /**
     * Whether a mixin applies, given what this game holds: the Oculus and OptiFine seams only with their mods, each
     * wrapped hook only with MixinExtras and its redirect form only without, and everything else always.
     */
    public static boolean applies(String mixinClassName, boolean oculus, boolean optiFine, boolean mixinExtras) {
        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (simple.equals(GATED)) return oculus;
        if (simple.equals(GATED_OPTIFINE)) return optiFine;
        for (String[] pair : WRAPPED) {
            if (simple.equals(pair[0])) return mixinExtras;
            if (simple.equals(pair[1])) return !mixinExtras;
        }
        return true;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) {}
}
