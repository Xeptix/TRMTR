package com.trmtgtnh.dataload;

import java.util.List;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * The server's data packs' data files, read on every data reload (W3 spec 2.2: "1.16.5 on, the server's data on every
 * data-pack reload ({@code /reload} included) before {@code resolve}"; task T4, carried in T17). The first edition
 * with data packs: families, shapes and wearers from {@code data/<ns>/trmtr/}.
 *
 * <p>
 * Registered by each loader as a server-data reload listener: Forge through {@code AddReloadListenerEvent}, Fabric
 * through Fabric API's {@code ResourceManagerHelper} for {@code SERVER_DATA}. The files are found off the server
 * thread, in the reload's preparation, and handed to {@link DataLoad#dataPacksRead} as the reload applies. The
 * surface table is resolved again only once the whole reload is done and the new tags are bound - a file's
 * {@code #ns:tag} reads them - which each loader hears as its own event and passes to
 * {@code ServerEvents.dataReloaded}. At a world's first load the table is resolved as the server starts, as before.
 */
public class DataPackListener extends SimplePreparableReloadListener<List<DataFiles.Found>> {

    /** The id Fabric API keys a reload listener by, and the name Forge's profiler shows it under. */
    public static final String ID = "trmtgtnh:data_files";

    @Override
    protected List<DataFiles.Found> prepare(ResourceManager manager, ProfilerFiller profiler) {
        return DataFiles.fromPacks(manager, true, DataLoad.problems());
    }

    @Override
    protected void apply(List<DataFiles.Found> found, ResourceManager manager, ProfilerFiller profiler) {
        DataLoad.dataPacksRead(found);
    }
}
