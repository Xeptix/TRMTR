package com.trmtgtnh.forge;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;

import com.trmtgtnh.dataload.DataFiles;

/**
 * Where Forge's mods are, for the data read (W3 spec 2.10: "1.16.5 Forge: Forge's mod file list"; task T4, carried in
 * T17): each file Forge loaded mods from, once, with the mod ids it holds and the root its resources are read from -
 * the jar's own root inside the jar, or in a development run the folder Forge finds {@code assets} in among the run's
 * class and resource folders ({@code IModFile.findResource}, which asks each folder in turn).
 * {@link DataFiles#rootOf} takes that root from what {@code findResource} gives, which inside a jar has no parent.
 *
 * <p>
 * And which of the game's packs are Forge's own packs of its mods' files - each mod's {@code ModFileResourcePack} and
 * the {@code DelegatingResourcePack} that gathers them as "Mod Resources" - so the packs read passes them over, each
 * mod's file being read as its jar already.
 */
final class ForgeData {

    private ForgeData() {}

    /** Every mod file Forge loaded, as the data read takes it. Never throws. */
    static List<DataFiles.ModFile> modFiles() {
        List<DataFiles.ModFile> out = new ArrayList<DataFiles.ModFile>();
        for (ModFileInfo info : ModList.get()
            .getModFiles()) {
            try {
                ModFile file = info.getFile();
                if (file == null) continue;
                List<String> ids = new ArrayList<String>();
                for (IModInfo mod : file.getModInfos()) ids.add(mod.getModId());
                Path root = DataFiles.rootOf(file.findResource("assets"));
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
        return pack instanceof net.minecraftforge.fml.packs.ModFileResourcePack
            || pack instanceof net.minecraftforge.fml.packs.DelegatingResourcePack;
    }
}
