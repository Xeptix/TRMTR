package com.trmtgtnh.fabric.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.trmtgtnh.erosion.ErosionStore;
import com.trmtgtnh.fabric.ChunkErosionCarrier;

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
 * <strong>The same mixin is in the Forge module, which is not duplication for its own sake.</strong>
 * Forge does fire events here - {@code ChunkDataEvent.Load} and {@code Save} - and the load one
 * cannot be used: it carries the chunk and the tag but no world, because the only thing that answers
 * Forge's {@code getWorldForge} is a {@code LevelChunk} and a chunk being read off disk is a
 * {@code ProtoChunk}, whose answer is the interface default of null. A hook that cannot say which
 * level a chunk belongs to cannot store its wear, since the level is half of the key. Vanilla's own
 * method takes the {@code ServerLevel} as its first argument, so here the question does not come up
 * on either loader, and both editions read and write wear through the same two injections.
 *
 * <p>
 * The key goes at the root of the chunk's tag, beside {@code DataVersion}, which is
 * where every older edition put it - beside 1.16.5's {@code Level} too, which 1.18 flattened into the root. A chunk
 * written by 1.16.5 is brought up to date by the game's own data fixers before it reaches {@code read}, and the key
 * should come through them at the root where it was, which spike 3 plays to be sure of. The root is also what makes a
 * world that drops this mod merely carry one
 * unrecognised key. Nothing eroded was ever written into the block array, so such a world opens with
 * every position already the vanilla block it always was.
 *
 * <p>
 * <strong>From 1.21.2 ChunkSerializer is SerializableChunkData</strong>, and each of 1.21.1's two calls is two: a
 * chunk is read by {@code parse} (the tag, no level) and then {@code read} (the level and the position, no tag), and
 * saved by {@code copyOf} (the level and the chunk, on the server's thread, where 1.21.1's {@code write} ran) and
 * then {@code write} (the tag, later, on a worker). So the tag's wear bytes are kept on the parsed copy and handed to
 * the store once {@code read} has built the chunk; and the store is asked at the copy, on the server's thread as
 * before, and its bytes written into the tag the copy builds. The blob and its key ({@code ErosionStore.NBT_KEY}, a
 * byte array at the root) are unchanged, so a world 1.21.1 saved loads its wear. Fabric API keeps its attachment data
 * across the same two gaps the same way. The NeoForge and Forge modules carry the same class.
 */
@Mixin(SerializableChunkData.class)
public abstract class MixinChunkSerializer implements ChunkErosionCarrier {

    @Unique
    private byte[] trmt$erosion;

    @Override
    public byte[] trmt$erosion() {
        return trmt$erosion;
    }

    @Override
    public void trmt$erosion(byte[] wear) {
        trmt$erosion = wear;
    }

    /** The tag's wear, kept on the copy it is parsed into until the copy is built into a chunk. */
    @Inject(method = "parse", at = @At("RETURN"))
    private static void trmt$parsed(LevelHeightAccessor height, PalettedContainerFactory containers, CompoundTag tag,
        CallbackInfoReturnable<SerializableChunkData> callback) {
        SerializableChunkData data = callback.getReturnValue();
        if (data == null || tag == null) return;
        // From 1.21.5 the getter answers in an Optional; anything but a byte array reads as none.
        tag.getByteArray(ErosionStore.NBT_KEY)
            .ifPresent(bytes -> ((ChunkErosionCarrier) (Object) data).trmt$erosion(bytes));
    }

    /**
     * Parse and park, once the chunk is built and before anything is done with it.
     *
     * <p>
     * This runs off the main thread, which is why the store does no more here than read the bytes:
     * what a chunk's wear needs next is a live level to catch up against, and that waits for the
     * chunk to arrive.
     */
    @Inject(method = "read", at = @At("RETURN"))
    private void trmt$read(ServerLevel level, PoiManager poi, RegionStorageInfo region, ChunkPos pos,
        CallbackInfoReturnable<ProtoChunk> callback) {
        byte[] bytes = trmt$erosion;
        if (level == null || pos == null || bytes == null) return;
        // The store reads the key off a tag, as 1.21.1 handed it the chunk's own; this hands it that key alone.
        CompoundTag tag = new CompoundTag();
        tag.putByteArray(ErosionStore.NBT_KEY, bytes);
        // ResourceKey.location() is identifier() by 26.1, where ResourceLocation is Identifier; the same name.
        ErosionStore.get()
            .chunkDataLoaded(
                level.dimension()
                    .identifier(),
                pos.x(),
                pos.z(),
                tag);
    }

    /**
     * The store asked as the chunk is copied for saving, on the server's thread, where 1.21.1 asked it as the tag was
     * built.
     *
     * <p>
     * A chunk's root tag is built fresh every save, so contributing nothing is not "leave what was
     * there" - it is "erase it". That is why the store is asked on every save rather than only when
     * it believes something changed, and why a null answer writes no key: an abandoned
     * chunk sheds it rather than carrying an empty array for ever.
     */
    @Inject(method = "copyOf", at = @At("RETURN"))
    private static void trmt$copied(ServerLevel level, ChunkAccess chunk,
        CallbackInfoReturnable<SerializableChunkData> callback) {
        SerializableChunkData data = callback.getReturnValue();
        if (level == null || chunk == null || data == null) return;
        ((ChunkErosionCarrier) (Object) data).trmt$erosion(ErosionStore.get()
            .chunkDataSaving(
                level.dimension()
                    .identifier(),
                chunk.getPos()
                    .x(),
                chunk.getPos()
                    .z()));
    }

    /** Write at the return, into the tag vanilla has just built. */
    @Inject(method = "write", at = @At("RETURN"))
    private void trmt$write(CallbackInfoReturnable<CompoundTag> callback) {
        CompoundTag tag = callback.getReturnValue();
        byte[] bytes = trmt$erosion;
        if (tag == null || bytes == null) return;
        tag.putByteArray(ErosionStore.NBT_KEY, bytes);
    }
}
