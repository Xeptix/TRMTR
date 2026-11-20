package com.trmtgtnh.neoforge;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.server.packs.PackResources;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;

import com.trmtgtnh.dataload.DataFiles;

/**
 * Where NeoForge's mods are, for the data read (W3 spec 2.10: "1.21.1: as 1.20.1, plus NeoForge's mod list"; task T4,
 * carried in T19): each file NeoForge loaded mods from, once, with the mod ids it holds and the one root its resources
 * are read from, {@code SecureJar.getRootPath} - the root of the union file system FML 4 reads every mod through, which
 * holds a jar's inside or a development run's class and resource folders together. securejarhandler 3.0.8's root;
 * {@code ForgesRootPathListsTest} holds that it lists a jar and two folders. Never the parent of a {@code findResource}
 * (W3's found entry for R1's Forge fix).
 *
 * <p>
 * And which of the game's packs are NeoForge's own packs of its mods' files, so the packs read passes them over, each
 * mod's file being read as its jar already. Each is the game's own {@code PathPackResources} over that root
 * ({@code ResourcePackLoader.createPackForMod}, read from 21.1.259's jar), the same class as a player's folder pack, so
 * a pack is told by the id NeoForge gives it: {@code mod/<id>} ({@code ResourcePackLoader.packFinder}), where a folder
 * pack's is {@code file/<name>} ({@code FolderRepositorySource}). The two parents that gather them,
 * {@code mod_resources} and {@code mod_data}, are {@code EmptyPackResources} and hold no namespace to read.
 */
final class NeoForgeData {

    /** What NeoForge's {@code ResourcePackLoader.packFinder} puts before a mod's id to name that mod's pack. */
    private static final String MOD_PACK_ID = "mod/";

    private NeoForgeData() {}

    /** Every mod file NeoForge loaded, as the data read takes it. Never throws. */
    static List<DataFiles.ModFile> modFiles() {
        List<DataFiles.ModFile> out = new ArrayList<DataFiles.ModFile>();
        for (IModFileInfo info : ModList.get()
            .getModFiles()) {
            try {
                IModFile file = info.getFile();
                if (file == null) continue;
                List<String> ids = new ArrayList<String>();
                for (IModInfo mod : file.getModInfos()) ids.add(mod.getModId());
                Path root = file.getSecureJar()
                    .getRootPath();
                List<Path> roots = root == null ? Collections.<Path>emptyList() : Collections.singletonList(root);
                out.add(new DataFiles.ModFile(file.getFileName(), roots, ids));
            } catch (RuntimeException | LinkageError unread) {
                com.trmtgtnh.Trmt.LOG.debug("TRMTR data: a mod file NeoForge listed could not be read", unread);
            }
        }
        return out;
    }

    /** Whether a pack is one of NeoForge's own packs of its mods' files. */
    static boolean modPack(Object pack) {
        return pack instanceof PackResources && ((PackResources) pack).packId()
            .startsWith(MOD_PACK_ID);
    }
}
