package com.trmtgtnh.erosion;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import com.trmtgtnh.Trmt;

/**
 * Turning one of the store's level indices back into a live level.
 *
 * <p>
 * The healing sweep walks the store's keys and has to act on the world each key belongs to. In both
 * older editions a key carries a dimension id and Forge hands back the world for it -
 * {@code DimensionManager.getWorld(id)}, one line. Here a dimension is a {@code ResourceKey} rather
 * than a number, so the store keeps its own list of the levels it has seen and the key carries an
 * index into that list; this is the other end of that arrangement.
 *
 * <p>
 * <strong>No loader seam, and that was worth checking rather than assuming.</strong> Forge's
 * {@code DimensionManager} is Forge's, but what it does is reachable from vanilla:
 * {@code MinecraftServer.getLevel} answers the same question, and the server is already available to
 * this module through {@link Trmt#runningServer()}. So the only seam involved is the one that already
 * existed for the server itself, and the two loaders need nothing new.
 *
 * <p>
 * Null whenever anything along the way is absent - no server running, a level that has been unloaded,
 * an index from a key older than this session. Every caller is a sweep over keys that may have gone
 * stale, so null is the ordinary answer rather than an exceptional one, and none of them may treat it
 * as a fault.
 */
public final class Levels {

    private Levels() {}

    /** The level a store key belongs to, or null if it cannot be found now. */
    public static Level byKey(long chunkKey) {
        return byIndex(ErosionStore.levelIndexOf(chunkKey));
    }

    /** The level at one of the store's indices, or null. */
    public static Level byIndex(int levelIndex) {
        ResourceLocation name = ErosionStore.get()
            .levelNameOf(levelIndex);
        return name == null ? null : byName(name);
    }

    /** The live level with this name, or null if there is no server or no such level. */
    public static ServerLevel byName(ResourceLocation name) {
        MinecraftServer server = Trmt.runningServer();
        if (server == null || name == null) return null;
        return server.getLevel(ResourceKey.create(Registry.DIMENSION_REGISTRY, name));
    }
}
