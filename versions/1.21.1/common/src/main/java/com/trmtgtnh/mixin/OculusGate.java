package com.trmtgtnh.mixin;

import java.util.List;
import java.util.Set;

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
 * Which loader is asked is the one thing that changed. Both older editions ask
 * {@code Launch.classLoader}, which is LaunchWrapper's and is gone. A mixin config plugin is loaded
 * by the loader's own class loader on both loaders here, and that is the loader that can see another
 * mod's jar - so this class's own is asked first, and the context loader after it, because which of
 * the two it is differs between Forge and Fabric and neither is worth naming.
 *
 * <p>
 * Everything else in the config is waved through by name. This is the config's only gate because the
 * config's optional targets are all of one kind - another mod's class, named by string - and from 0.9.219
 * there are three: Oculus's holder and OptiFine's vertex builder, through which a ghost claims its shader
 * material, and the Sodium family's face test, through which a settled snow layer keeps its step. Each is
 * refused the same way on a client without its mod. Splitting a second config out for one mixin would buy
 * a package that overlaps the first one's.
 */
public final class OculusGate implements IMixinConfigPlugin {

    /**
     * Oculus's per-block shader-id holder, named once for the whole mod.
     *
     * <p>
     * It lives here rather than beside the code that reads it because this class is the one of the
     * three that the mixin itself has to name: {@code Mixin(targets = ...)} wants a constant, and a
     * constant in the gate is the one place both the gate and the mixin can see it.
     */
    // Iris moved it out of net.coderbot by its 1.20.1 builds, and Oculus kept Iris's packages: the 1.16.5 name found
    // nothing here, so worn ground drew without its shader material and the log said "not found" (0.9.225). And again
    // for 1.21 (Iris 1.8, read from its jars on both loaders, 0.9.226): into vertices.sodium.terrain, its set(BlockState,
    // ...) become setBlockData(int, ...) with Iris's own id for the block, and overrideBlock(int) beside it for a block
    // drawn as another - which is what ShaderMaterial now calls. There is no Oculus for 1.21.1.
    public static final String HOLDER = "net.irisshaders.iris.vertices.sodium.terrain.BlockContextHolder";

    /**
     * OptiFine's vertex builder, whose static {@code pushEntity} puts a block's shader id on the buffer's
     * stack - named once for the whole mod, here, for the same reason as {@link #HOLDER}.
     */
    public static final String OPTIFINE_SEAT = "net.optifine.shaders.SVertexBuilder";

    /**
     * The Sodium family's face test, which Rubidium, Embeddium and Sodium all ask in place of vanilla's -
     * named here for the same reason as {@link #HOLDER}. Not a shader seat: a settled snow layer's step is
     * kept through it. See {@code MixinSettledFacesSodium}.
     */
    // Sodium 0.5 and Embeddium 0.3 kept it in the chunk compile pipeline (0.9.225). For 1.21 each moved its packages and
    // kept the class and its shouldDrawSide as they were (read from their jars, 0.9.226): Sodium 0.6 under
    // net.caffeinemc, Embeddium 1.0 under org.embeddedt. The mixin names both.
    public static final String SODIUM_FACES = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache";

    /** Embeddium's face test at 1.21.1, beside Sodium's - see {@link #SODIUM_FACES}. */
    public static final String EMBEDDIUM_FACES = "org.embeddedt.embeddium.impl.render.chunk.compile.pipeline.BlockOcclusionCache";

    /** The mixin that needs Oculus. */
    private static final String GATED = "MixinOculusBlockContext";

    /** The mixin that needs OptiFine. */
    private static final String GATED_OPTIFINE = "MixinOptiFineShaderSeat";

    /** The mixin that needs the Sodium family. */
    private static final String GATED_SODIUM = "MixinSettledFacesSodium";

    /**
     * Jade's harvest-tool line, through which a worn square names the tool its covered block wants - named here for the
     * same reason as {@link #HOLDER}. From 0.9.226 its mixin is the common module's, so a Fabric client without Jade
     * was asked to apply it too and logged that the class could not be loaded on every start; refused here instead.
     */
    public static final String JADE_HARVEST = "snownee.jade.addon.harvest.HarvestToolProvider";

    /** The mixin that needs Jade. */
    private static final String GATED_JADE = "MixinJadeHarvestLine";

    /** WTHIT's harvest line, handed the covered block as Jade's is (0.9.227, spec GF9); its mixin needs WTHIT. */
    public static final String WTHIT_HARVEST = "mcp.mobius.waila.plugin.harvest.provider.HarvestProvider";

    /** The mixin that needs WTHIT. */
    private static final String GATED_WTHIT = "MixinWthitHarvestLine";

    private boolean present;

    private boolean optiFinePresent;

    private boolean sodiumPresent;

    private boolean jadePresent;

    private boolean wthitPresent;

    @Override
    public void onLoad(String mixinPackage) {
        present = there(HOLDER);
        optiFinePresent = there(OPTIFINE_SEAT);
        sodiumPresent = there(SODIUM_FACES) || there(EMBEDDIUM_FACES);
        jadePresent = there(JADE_HARVEST);
        wthitPresent = there(WTHIT_HARVEST);
        // Said, because a gate that shut is otherwise silent: the seat it holds back never logs a word,
        // and worn ground then simply draws without its shader material.
        org.apache.logging.log4j.LogManager.getLogger("TRMT: Reimagined")
            .info(
                "Shader material seats at mixin load: Oculus or Iris {}, OptiFine {}; the Sodium family's face test {}",
                present ? "found" : "not found",
                optiFinePresent ? "found" : "not found",
                sodiumPresent ? "found" : "not found");
    }

    /** Whether either loader can see a class's bytes. */
    private static boolean there(String className) {
        String path = className.replace('.', '/') + ".class";
        return thereUnder(OculusGate.class.getClassLoader(), path)
            || thereUnder(
                Thread.currentThread()
                    .getContextClassLoader(),
                path);
    }

    /** Whether one loader can see those bytes, without defining anything. */
    private static boolean thereUnder(ClassLoader loader, String path) {
        try {
            return loader != null && loader.getResource(path) != null;
        } catch (Throwable noLoader) {
            // Nothing here is worth failing a mixin config over. Absent is the safe answer.
            return false;
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(GATED)) return present;
        if (mixinClassName.endsWith(GATED_OPTIFINE)) return optiFinePresent;
        if (mixinClassName.endsWith(GATED_SODIUM)) return sodiumPresent;
        if (mixinClassName.endsWith(GATED_JADE)) return jadePresent;
        if (mixinClassName.endsWith(GATED_WTHIT)) return wthitPresent;
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
