package com.trmtgtnh.fabric.mixin.canvas;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.trmtgtnh.Trmt;

/**
 * Every model face JMX makes on a thread that has read no model file gets the version a model file without one would
 * have given it (0.9.225).
 *
 * <p>
 * JMX - the model extensions the Canvas for 1.20.1 carries inside it - keeps the version of the last model file a thread
 * read in a thread-local, set as it reads one and never cleared, and asks it whenever a face is made. The faces vanilla
 * builds for an item from its texture are made as the item is baked, on whichever worker thread bakes it, and that need
 * not be a thread that read any model file: one that did not finds nothing, and the bake fails. With this mod's wear
 * pictures composed on a worker as the models load, one worker read none and 599 item models failed to bake in the
 * 1.20.1 Canvas instance - this mod's golem upgrades and vanilla's pufferfish and music discs among them, drawn as the
 * missing model - where without this mod, the same instance baked every one. The gap is JMX's; this mod's load is what
 * reaches it.
 *
 * <p>
 * So an unset version is set to nought before JMX reads it, which is what its own reader sets for a model file that
 * names none, so a face is given what a vanilla model's face would have been given. A thread that has read a model file
 * keeps its own. {@code @Pseudo}, {@code require = 0}, gated by {@link CanvasGate} with the other Canvas hooks, and
 * naming no JMX type: the field is found by name once, and a JMX that has renamed it costs this and nothing else.
 */
@Pseudo
@Mixin(targets = "io.vram.jmx.json.FaceExtData", remap = false)
public abstract class MixinJmxFaceVersion {

    private static volatile ThreadLocal<Object> trmt$version;

    private static volatile boolean trmt$looked;

    /** Held while the field is looked for: a mixin may not name its own class at run time, which is merged away. */
    private static final Object trmt$lock = new Object();

    @Inject(method = "empty", at = @At("HEAD"), require = 0)
    private static void trmt$versionForThisThread(CallbackInfoReturnable<Object> info) {
        ThreadLocal<Object> version = trmt$version();
        if (version != null && version.get() == null) version.set(Integer.valueOf(0));
    }

    @SuppressWarnings("unchecked")
    private static ThreadLocal<Object> trmt$version() {
        if (trmt$looked) return trmt$version;
        synchronized (trmt$lock) {
            if (!trmt$looked) {
                try {
                    // Canvas and this mod share the game's class loader on Fabric.
                    java.lang.reflect.Field field = Class
                        .forName("io.vram.jmx.json.JmxModelExt", false, Trmt.class.getClassLoader())
                        .getField("VERSION");
                    trmt$version = (ThreadLocal<Object>) field.get(null);
                    Trmt.LOG.info("A model face JMX makes on a thread that has read no model file is given a model file's version");
                } catch (ReflectiveOperationException | ClassCastException | LinkageError changed) {
                    Trmt.LOG.warn("Not giving JMX's model faces a version where a thread has none: {}", changed.toString());
                }
                trmt$looked = true;
            }
        }
        return trmt$version;
    }
}
