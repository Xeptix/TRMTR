package com.trmtgtnh.block;

import net.minecraft.block.Block;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.trmtgtnh.Tags;

/**
 * Where the blocks are made and registered.
 *
 * <p>
 * One so far - the spike's ghost. The finished mod registers sixty-six, one per staged family plus
 * the sunken, stair, window and solid-ice twins, and the 1.7.10 edition's hard-won rule about that
 * set carries over unchanged: <em>which</em> blocks are registered must never depend on a config
 * setting. Registry names go into a save's own record of what exists, so a set that varies with the
 * settings is a save that will not open after somebody changes one.
 *
 * <p>
 * Registration itself is entirely different here. 1.7.10 called {@code GameRegistry.registerBlock}
 * during preInit and handed out numeric ids that Forge then had to keep in step across saves;
 * 1.12.2 fires an event, names are the identity, and the whole id-map apparatus - the remapping, the
 * missing-mapping retirement, the caches keyed by block id - has nothing left to do.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class ModBlocks {

    private static BlockGhost ghostGrass;

    private ModBlocks() {}

    /**
     * Whether this is one of this mod's cosmetic blocks.
     *
     * <p>
     * Asked by the demonstration, which lays out a platform per surface and must not lay out one for
     * a ghost: a ghost is what a worn surface is drawn *as*, so a platform of them would be a platform
     * of the answer rather than of the question. The other edition asks the same thing of sixty-six
     * classes; here there is one, and it is still worth asking by name rather than by class, because
     * what the caller means is "is this ours" and not "is this that class".
     */
    public static boolean isGhost(Block block) {
        return block instanceof BlockGhost;
    }

    public static BlockGhost ghostGrass() {
        return ghostGrass;
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Block> event) {
        ghostGrass = new BlockGhost();
        event.getRegistry()
            .register(ghostGrass);
    }
}
