package com.trmtgtnh.fabric;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

import com.trmtgtnh.dataload.DataFiles;
import com.trmtgtnh.dataload.DataPackListener;

/**
 * Where Fabric's mods are, and its data packs, for the data read (W3 spec 2.10: "as 1.16.5, Fabric: Fabric Loader's mod
 * containers' root paths; data packs through Fabric API's server-data reload listener"; task T4, carried in T18).
 *
 * <p>
 * Each mod container Fabric Loader holds, with its id and the roots its resources are read from - a jar's root inside
 * the jar, or each folder of a development run. And which of the game's packs are Fabric API's own packs of its mods'
 * files (each mod's {@code ModResourcePack} and the group pack that gathers them), so the packs read passes them over,
 * each mod's file being read as its jar already.
 */
final class FabricData {

    private FabricData() {}

    /** Hands the data read its mod list, its pack test and its data-pack listener. Called once, at initialisation. */
    static void open() {
        com.trmtgtnh.dataload.DataLoad.useMods(FabricData::modFiles);
        DataFiles.useModPacks(FabricData::modPack);
        ResourceManagerHelper.get(PackType.SERVER_DATA)
            .registerReloadListener(new Listener());
        // A reload's end, its tags bound: the surface table is resolved from the files it read. Only a reload that
        // took; one that failed keeps the packs and the table it had.
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) com.trmtgtnh.server.ServerEvents.dataReloaded();
        });
    }

    /** The data packs' listener, under the id Fabric API keys a listener by. */
    private static final class Listener extends DataPackListener implements IdentifiableResourceReloadListener {

        private static final ResourceLocation FABRIC_ID = ResourceLocation.parse(DataPackListener.ID);

        @Override
        public ResourceLocation getFabricId() {
            return FABRIC_ID;
        }
    }

    /** Every mod container Fabric Loader holds, as the data read takes it. Never throws. */
    static List<DataFiles.ModFile> modFiles() {
        List<DataFiles.ModFile> out = new ArrayList<DataFiles.ModFile>();
        for (ModContainer mod : FabricLoader.getInstance()
            .getAllMods()) {
            try {
                String id = mod.getMetadata()
                    .getId();
                List<Path> roots = rootsOf(mod);
                out.add(new DataFiles.ModFile(nameOf(roots, id), roots, Collections.singletonList(id)));
            } catch (RuntimeException | LinkageError unread) {
                com.trmtgtnh.Trmt.LOG.debug("TRMTR data: a mod Fabric Loader listed could not be read", unread);
            }
        }
        return out;
    }

    /**
     * A container's roots: every one, where this Fabric Loader can say ({@code getRootPaths}, from 0.12), else its one
     * root - asked by reflection so a jar built against 0.19 still starts on the oldest loader this mod allows.
     */
    @SuppressWarnings("unchecked")
    private static List<Path> rootsOf(ModContainer mod) {
        try {
            Method all = mod.getClass()
                .getMethod("getRootPaths");
            Object roots = all.invoke(mod);
            if (roots instanceof List) return (List<Path>) roots;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError older) {
            // The single root, below.
        }
        return Collections.singletonList(mod.getRootPath());
    }

    /** The file a mod came from, as {@code /trmt data} names it: its jar's name where its root says, else its id. */
    private static String nameOf(List<Path> roots, String id) {
        try {
            if (roots.isEmpty() || roots.get(0) == null) return id;
            String filesystem = roots.get(0)
                .getFileSystem()
                .toString();
            int slash = Math.max(filesystem.lastIndexOf('/'), filesystem.lastIndexOf(java.io.File.separatorChar));
            if (filesystem.endsWith(".jar") && slash >= 0) return filesystem.substring(slash + 1);
        } catch (RuntimeException | LinkageError unnamed) {
            // The id, below.
        }
        return id;
    }

    /** Whether a pack is one of Fabric API's own packs of its mods' files. */
    static boolean modPack(Object pack) {
        if (pack instanceof net.fabricmc.fabric.api.resource.ModResourcePack) return true;
        return pack != null && pack.getClass()
            .getName()
            .startsWith("net.fabricmc.fabric.impl.resource.loader.");
    }
}
