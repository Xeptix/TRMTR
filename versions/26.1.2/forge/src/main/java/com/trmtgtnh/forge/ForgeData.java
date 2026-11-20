package com.trmtgtnh.forge;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;

import com.trmtgtnh.dataload.DataFiles;

/**
 * Where Forge's mods are, for the data read (W3 spec 2.10: "1.21.1 Forge: as 1.20.1"; task T4, carried in T19 and T20): each
 * file Forge loaded mods from, once, with the mod ids it holds and the one root its resources are read from,
 * {@code SecureJar.getRootPath} - the root of the union file system Forge reads every mod through, which holds a jar's
 * inside or a development run's class and resource folders together. Forge 64 reads it through securemodules 2.2.24, not
 * 1.20.1's securejarhandler; {@code ForgesRootPathListsTest} holds that the root it gives lists a jar and two folders.
 * Never the parent of a {@code findResource}, which has a parent only by luck (W3's found entry for R1's Forge fix).
 *
 * <p>
 * And which of the game's packs are Forge's own packs of its mods' files - each mod's {@code PathPackResources}, which
 * at this version is the game's own class ({@code ResourcePackLoader} builds each mod's from {@code ResourcesSupplier}),
 * and the {@code DelegatingPackResources} that gathers them as "Mod Resources". Where a player's pack in a folder would
 * be the same class, the test is the pack's id, which Forge 64 writes as {@code mod:<id>} (read from the constant pool of
 * its {@code ResourcePackLoader}) and a folder pack as {@code file/<name>}; see {@link #modPack}.
 */
final class ForgeData {

    /** What Forge's {@code ResourcePackLoader.findPacks} puts before a mod's id to name that mod's pack. */
    private static final String MOD_PACK_ID = "mod:";

    private ForgeData() {}

    /** Every mod file Forge loaded, as the data read takes it. Never throws. */
    static List<DataFiles.ModFile> modFiles() {
        List<DataFiles.ModFile> out = new ArrayList<DataFiles.ModFile>();
        // Static from Forge 64, ModList being one list for the game.
        for (IModFileInfo info : ModList.getModFiles()) {
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
                com.trmtgtnh.Trmt.LOG.debug("TRMTR data: a mod file Forge listed could not be read", unread);
            }
        }
        return out;
    }

    /** Whether a pack is one of Forge's own packs of its mods' files. */
    static boolean modPack(Object pack) {
        if (pack instanceof net.minecraftforge.resource.DelegatingPackResources) return true;
        return pack instanceof net.minecraft.server.packs.PackResources
            && ((net.minecraft.server.packs.PackResources) pack).packId()
                .startsWith(MOD_PACK_ID);
    }
}
