package com.trmtgtnh;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

/**
 * The mixins that reach into another mod, held back until that mod's classes exist.
 *
 * <p>
 * Everything in the ordinary config is prepared while the game is still assembling its class path,
 * before a single mod jar has been opened - which is fine for a mixin into Minecraft and hopeless
 * for one into anybody else. A mixin whose target cannot be resolved at that moment is not deferred,
 * it is dropped: the log says "Skipping virtual target" once, at debug level, and the feature simply
 * never exists. That is exactly what happened to the shell lift for three versions.
 *
 * <p>
 * A late config is prepared after mods are discovered instead, and it is handed the set of mod ids
 * that were found - so the decision is made on whether the mod is actually there rather than on
 * whether a class happened to resolve, which is both correct and legible. Nothing here is a
 * coremod: the loader is found by its annotation, and the mixin library that finds it is already
 * required.
 *
 * <p>
 * Outside the mixin package on purpose, though every mixin it names lives in it. A config's package
 * is declared to the transformer as one whose classes are never to be loaded directly - a mixin is
 * something applied to a class, not something instantiated - and this one has to be loaded like any
 * other, because the mod loader finds it by scanning for its annotation and then constructs it.
 */
@LateMixin
public class LateMixins implements ILateMixinLoader {

    /**
     * The config to prepare late. Its own lists are deliberately empty.
     *
     * <p>
     * The orchestrator reads whatever the file declares and then adds what {@link #getMixins}
     * returns to the same list, so a name written in both places is registered twice. Empty arrays
     * and one source of truth.
     */
    @Override
    public String getMixinConfig() {
        return "mixins.trmtgtnh.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        List<String> wanted = new ArrayList<String>();
        // Named rather than probed, so that a pack without Chisel never loads the class and a
        // future Chisel that has moved its renderer fails as a skipped mixin rather than a crash.
        // Client only. The shell lift is a drawing correction and its class is marked for the
        // client, and a late config is prepared on every side: handed to a dedicated server, the
        // class is read through the side check while the mixin is prepared, the side check refuses
        // it, and a required config that fails to prepare stops the server starting - on exactly
        // the packs that have Chisel, which is every GTNH pack.
        if (loadedMods.contains("chisel") && cpw.mods.fml.relauncher.FMLLaunchHandler.side()
            .isClient()) {
            wanted.add("MixinLayeredBlockShell");
        }
        return wanted;
    }
}
