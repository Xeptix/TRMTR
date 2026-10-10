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

    private boolean present;

    private boolean optiFinePresent;

    @Override
    public void onLoad(String mixinPackage) {
        present = there(HOLDER);
        optiFinePresent = there(OPTIFINE_SEAT);
        // Said, because a gate that shut is otherwise silent: the seat it holds back never logs a word,
        // and worn ground then simply draws without its shader material. The 1.16.5 edition's line.
        org.apache.logging.log4j.LogManager.getLogger("TRMT: Reimagined")
            .info(
                "Shader material seats at mixin load: Oculus or Iris {}, OptiFine {}",
                present ? "found" : "not found",
                optiFinePresent ? "found" : "not found");
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
        if (mixinClassName.endsWith(GATED)) return present;
        if (mixinClassName.endsWith(GATED_OPTIFINE)) return optiFinePresent;
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
