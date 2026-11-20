package com.trmtgtnh.neoforge;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.packs.PackResources;
import net.neoforged.fml.ModList;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;

import com.trmtgtnh.dataload.DataFiles;

/**
 * Where NeoForge's mods are, for the data read (W3 spec 2.10: "1.21.1: as 1.20.1, plus NeoForge's mod list"; task T4,
 * carried in T19 and T20): each file NeoForge loaded mods from, once, with the mod ids it holds and the file's own
 * {@code JarContents}, which holds a jar or a development run's class and resource folders together.
 *
 * <p>
 * From FML 11 (26.1) a mod file has neither {@code findResource} nor {@code getSecureJar}, and the union file system is
 * gone, so there is no root inside a jar to walk: {@code getContentRoots} are paths on disk, a jar's own file. So the
 * file is asked ({@code visitContent}, {@code containsFile}) through {@link DataFiles.Contents}, and
 * {@code ForgesRootPathListsTest} holds on FML's own class that it lists a jar and two folders.
 *
 * <p>
 * And which of the game's packs are NeoForge's own packs of its mods' files, so the packs read passes them over, each
 * mod's file being read as its jar already. Each is the game's own {@code PathPackResources}
 * ({@code ResourcePackLoader.createPackForMod}), the same class as a player's folder pack, so a pack is told by the id
 * NeoForge gives it: {@code mod/<id>} ({@code ResourcePackLoader.packFinder}, read from the constant pool of 26.3.0.94's
 * jar), where a folder pack's is {@code file/<name>} ({@code FolderRepositorySource}). The two parents that gather them,
 * {@code mod_resources} and {@code mod_data}, hold no namespace to read.
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
                out.add(new DataFiles.ModFile(file.getFileName(), new Asked(file.getContents()), ids));
            } catch (RuntimeException | LinkageError unread) {
                com.trmtgtnh.Trmt.LOG.debug("TRMTR data: a mod file NeoForge listed could not be read", unread);
            }
        }
        return out;
    }

    /** A mod file's contents, as the data read asks them. */
    private static final class Asked implements DataFiles.Contents {

        private final JarContents contents;

        Asked(JarContents contents) {
            this.contents = contents;
        }

        @Override
        public void visit(String folder, DataFiles.Visitor visitor) {
            contents.visitContent(folder, (path, resource) -> visitor.file(path, resource::open));
        }

        @Override
        public boolean contains(String path) {
            return contents.containsFile(path);
        }
    }

    /** Whether a pack is one of NeoForge's own packs of its mods' files. */
    static boolean modPack(Object pack) {
        return pack instanceof PackResources && ((PackResources) pack).packId()
            .startsWith(MOD_PACK_ID);
    }
}
