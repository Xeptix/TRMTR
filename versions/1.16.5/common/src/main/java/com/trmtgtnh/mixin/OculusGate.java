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
 * Everything else in the config is waved through by name. This is the config's only gate because it is
 * the config's only optional target, and splitting a second config out for one mixin would buy a
 * package that overlaps the first one's.
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
    public static final String HOLDER = "net.coderbot.iris.compat.sodium.impl.block_context.BlockContextHolder";

    /** The one mixin this gate has an opinion about. */
    private static final String GATED = "MixinOculusBlockContext";

    private boolean present;

    @Override
    public void onLoad(String mixinPackage) {
        String path = HOLDER.replace('.', '/') + ".class";
        present = thereUnder(OculusGate.class.getClassLoader(), path)
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
        if (!mixinClassName.endsWith(GATED)) return true;
        return present;
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
