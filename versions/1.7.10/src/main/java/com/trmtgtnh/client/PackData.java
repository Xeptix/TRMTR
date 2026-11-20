package com.trmtgtnh.client;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.ResourcePackRepository;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.data.DataProblems;
import com.trmtgtnh.dataload.DataFiles;
import com.trmtgtnh.dataload.DataLoad;

/**
 * The resource packs' data files, the client's half of the data read (W3 spec 2.2, task T4): the looks a resource pack
 * gives under {@code assets/<ns>/trmtr/looks/}, read from each enabled pack in the game's own order.
 *
 * <p>
 * The packs are the player's enabled ones ({@code ResourcePackRepository.getRepositoryEntries}), in the order the game
 * hands them to its resource manager, where the later wins ({@code Minecraft.refreshResources}); so the data's pack
 * order is the same as the textures'. Each entry is read as a file in the packs folder by its name, which is the file's
 * own name ({@code AbstractResourcePack.getPackName}), so a zipped pack and a folder pack are read alike without
 * reaching into the pack object. Mods' own resources are not packs here: a mod's jar is layer 1, read by
 * {@link DataLoad} straight from the file.
 *
 * <p>
 * Read first in pre-init, before the first data read, and again after every resource reload, the pattern of
 * {@code WornPathArt.listen}; {@link DataLoad#packsRead} re-merges only when the packs' files differ, so a reload that
 * changed nothing about data says nothing. A server's own resource pack, offered to a visiting client, is not read:
 * the game holds it only as a pack object, and its looks would be the server's choice for the client's drawing.
 */
public final class PackData {

    private PackData() {}

    private static boolean listening;

    /** Reads the enabled packs once, and again after every resource reload. Client pre-init. */
    public static void start() {
        Minecraft mc = Minecraft.getMinecraft();
        DataLoad.packsRead(collect(mc));
        if (listening || !(mc.getResourceManager() instanceof IReloadableResourceManager)) return;
        listening = true;
        ((IReloadableResourceManager) mc.getResourceManager())
            .registerReloadListener(new IResourceManagerReloadListener() {

                @Override
                public void onResourceManagerReload(IResourceManager resources) {
                    // Inside the game's own reload: nothing a pack holds may stop it.
                    try {
                        DataLoad.packsRead(collect(Minecraft.getMinecraft()));
                    } catch (RuntimeException | LinkageError unexpected) {
                        Trmt.LOG.warn(
                            DataProblems.PREFIX
                                + "the resource packs' data files could not be read after this reload; the last read stays in use",
                            unexpected);
                    }
                }
            });
    }

    /**
     * Reads the enabled packs again as a block-atlas stitch begins (W3 T5), so the wear textures are built from the
     * packs' looks as they are now: the game's own atlas is reloaded before any mod's listener, so the listener above
     * alone would leave a stitch drawing the packs from before the change. Never throws.
     */
    public static void beforeStitch() {
        try {
            DataLoad.packsRead(collect(Minecraft.getMinecraft()));
        } catch (RuntimeException | LinkageError unexpected) {
            Trmt.LOG.warn(
                DataProblems.PREFIX
                    + "the resource packs' data files could not be read for this stitch; the last read stays in use",
                unexpected);
        }
    }

    /** Every data file of every enabled pack, lowest first. */
    static List<DataFiles.Found> collect(Minecraft mc) {
        List<DataFiles.Found> out = new ArrayList<DataFiles.Found>();
        ResourcePackRepository repository = mc == null ? null : mc.getResourcePackRepository();
        if (repository == null) return out;
        File folder = repository.getDirResourcepacks();
        List<ResourcePackRepository.Entry> enabled = repository.getRepositoryEntries();
        for (int order = 0; order < enabled.size(); order++) {
            String name = enabled.get(order)
                .getResourcePackName();
            out.addAll(DataFiles.fromPack(new File(folder, name), name, order, DataLoad.problems()));
        }
        return out;
    }
}
