package com.trmtgtnh.forge.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.trmtgtnh.erosion.ErosionStore;

/**
 * Wear going to and coming back from a chunk's own NBT.
 *
 * <p>
 * This is where a world's roads survive being closed, and until this class existed they did not: the
 * store kept every chunk's wear in memory, handed it out all session, and dropped it on unload with
 * nothing having been written. A save reloaded as new ground. The store's four hooks were all
 * written and all correct; no loader called two of them.
 *
 * <p>
 * <strong>The same mixin is in the Fabric module, which is not duplication for its own sake.</strong>
 * Forge does fire events here - {@code ChunkDataEvent.Load} and {@code Save} - and the load one
 * cannot be used: it carries the chunk and the tag but no world, because the only thing that answers
 * Forge's {@code getWorldForge} is a {@code LevelChunk} and a chunk being read off disk is a
 * {@code ProtoChunk}, whose answer is the interface default of null. A hook that cannot say which
 * level a chunk belongs to cannot store its wear, since the level is half of the key. Vanilla's own
 * method takes the {@code ServerLevel} as its first argument, so here the question does not come up
 * on either loader, and both editions read and write wear through the same two injections.
 *
 * <p>
 * The key goes at the root of the chunk's tag, beside {@code Level} and {@code DataVersion}, which is
 * where both older editions put it and what makes a world that drops this mod merely carry one
 * unrecognised key. Nothing eroded was ever written into the block array, so such a world opens with
 * every position already the vanilla block it always was.
 */
@Mixin(ChunkSerializer.class)
public abstract class MixinChunkSerializer {

    /**
     * Parse and park, at the head, before vanilla has built anything.
     *
     * <p>
     * This runs off the main thread, which is why the store does no more here than read the bytes:
     * what a chunk's wear needs next is a live level to catch up against, and that waits for the
     * chunk to arrive.
     */
    @Inject(method = "read", at = @At("HEAD"))
    private static void trmt$read(ServerLevel level, StructureManager structures, PoiManager poi, ChunkPos pos,
        CompoundTag tag, CallbackInfoReturnable<ProtoChunk> callback) {
        if (level == null || pos == null || tag == null) return;
        ErosionStore.get()
            .chunkDataLoaded(
                level.dimension()
                    .location(),
                pos.x,
                pos.z,
                tag);
    }

    /**
     * Write at the return, into the tag vanilla has just built.
     *
     * <p>
     * A chunk's root tag is built fresh every save, so contributing nothing is not "leave what was
     * there" - it is "erase it". That is why the store is asked on every save rather than only when
     * it believes something changed, and why a null answer removes the key outright: an abandoned
     * chunk sheds it rather than carrying an empty array for ever.
     */
    @Inject(method = "write", at = @At("RETURN"))
    private static void trmt$write(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<CompoundTag> callback) {
        CompoundTag tag = callback.getReturnValue();
        if (level == null || chunk == null || tag == null) return;

        byte[] blob = ErosionStore.get()
            .chunkDataSaving(
                level.dimension()
                    .location(),
                chunk.getPos().x,
                chunk.getPos().z);
        if (blob == null) tag.remove(ErosionStore.NBT_KEY);
        else tag.putByteArray(ErosionStore.NBT_KEY, blob);
    }
}
