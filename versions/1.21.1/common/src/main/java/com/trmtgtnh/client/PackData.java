package com.trmtgtnh.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.data.DataProblems;
import com.trmtgtnh.dataload.DataFiles;
import com.trmtgtnh.dataload.DataLoad;

/**
 * The resource packs' data files, the client's half of the data read (W3 spec 2.2, task T4): the looks a resource pack
 * gives under {@code assets/<ns>/trmtr/looks/}, read from each enabled pack in the game's own order, the later pack
 * winning as its textures do.
 *
 * <p>
 * Read through the client's own resource manager ({@link DataFiles#fromPacks} with {@code dataPacks} false), which
 * holds the enabled packs in the order the game stacks them, so the data's pack order is the textures' order. A
 * resource pack's {@code families/}, {@code shapes/} or {@code wearers/} are refused with one line by the shared
 * code, since those are the server's facts; a data pack's looks are never read here.
 *
 * <p>
 * Read as every block-atlas stitch begins ({@link #beforeStitch}, from {@code WearTextures.registerAll}), because the
 * game stitches the block atlas inside the model manager's own preparation, beside every mod's reload listener and not
 * after it: a listener alone could hand a stitch the looks of the packs from before the change. And again by a
 * listener after every resource reload, the pattern of {@code WornPathArt.listen}, so a reload that stitched nothing
 * still leaves the data current. {@link DataLoad#packsRead} re-merges only when the packs' files differ, so a reload
 * that changed nothing about data says nothing. Carried from the 1.12.2 tree's class of the same name in W3 T18.
 */
public final class PackData {

    private PackData() {}

    private static boolean listening;

    /**
     * Listens for every resource reload from now on. Client setup, on the game's thread; reads nothing itself, since
     * the first stitch, already under way, reads the packs as it begins.
     */
    public static void start() {
        Minecraft mc = Minecraft.getInstance();
        if (listening || mc == null || !(mc.getResourceManager() instanceof ReloadableResourceManager)) return;
        listening = true;
        ((ReloadableResourceManager) mc.getResourceManager())
            .registerReloadListener((ResourceManagerReloadListener) resources -> {
                // Inside the game's own reload: nothing a pack holds may stop it.
                try {
                    DataLoad.packsRead(collect(resources));
                } catch (RuntimeException | LinkageError unexpected) {
                    Trmt.LOG.warn(
                        DataProblems.PREFIX
                            + "the resource packs' data files could not be read after this reload; the last read stays in use",
                        unexpected);
                }
            });
    }

    /**
     * Reads the enabled packs again as a block-atlas stitch begins (W3 T5), so the wear textures are built from the
     * packs' looks as they are now. Never throws.
     */
    public static void beforeStitch() {
        try {
            // The mods' and the config folder's files first, where nothing has read them yet: Forge's and NeoForge's
            // first stitch comes before their common setup (DataLoad.readIfUnread).
            DataLoad.readIfUnread();
            // Then trmtr-api's plugins, where nothing has started them, for the same reason: their files from code are
            // in the table this stitch plans and resolves with, as Fabric's entry point has them already. A second
            // start does nothing (ApiLoad.start).
            com.trmtgtnh.apiload.ApiLoad.start();
            Minecraft mc = Minecraft.getInstance();
            DataLoad.packsRead(collect(mc == null ? null : mc.getResourceManager()));
        } catch (RuntimeException | LinkageError unexpected) {
            Trmt.LOG.warn(
                DataProblems.PREFIX
                    + "the resource packs' data files could not be read for this stitch; the last read stays in use",
                unexpected);
        }
    }

    /** Every data file of every enabled resource pack, lowest first. */
    static List<DataFiles.Found> collect(ResourceManager resources) {
        if (resources == null) return new ArrayList<DataFiles.Found>();
        return DataFiles.fromPacks(resources, false, DataLoad.problems());
    }
}
