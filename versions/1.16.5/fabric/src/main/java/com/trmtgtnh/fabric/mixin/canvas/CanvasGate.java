package com.trmtgtnh.fabric.mixin.canvas;

import java.util.List;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Refuses the Canvas mixins on a client that has no Canvas - the 1.12.2 edition's OculusGate, for this loader.
 *
 * <p>
 * A mixin whose target class is nowhere is not a quiet no-op: Mixin reports it, and a mod that logs an error on
 * every client that simply does not have an optional dependency is a mod that has taught its users to ignore its
 * errors. So this config holds only Canvas's mixins, and this decides before Mixin asks. Asked of the loader's list
 * of mods rather than of the class loader, which would define the class it was asked about.
 */
public final class CanvasGate implements IMixinConfigPlugin {

    private boolean present;

    @Override
    public void onLoad(String mixinPackage) {
        present = FabricLoader.getInstance()
            .isModLoaded("canvas");
        // Said, because a gate that shut is otherwise silent, and snow and carpet would then simply stay up off
        // worn ground under Canvas again.
        org.apache.logging.log4j.LogManager.getLogger("trmtgtnh")
            .info("Canvas at mixin load: {}", present ? "found" : "not found");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return present;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
        IMixinInfo mixinInfo) {}
}
