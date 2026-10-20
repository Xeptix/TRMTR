package com.trmtgtnh.neoforge.mixin;

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
import com.trmtgtnh.neoforge.ErosionCarrier;

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
 * on either loader, and both editions read and write wear through the same injections.
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
 * <strong>From 1.21.2 a chunk's save is a record in two steps each way, and so is this.</strong> 1.21.1's
 * {@code ChunkSerializer} read and wrote a chunk in one call each, handed the level and the tag together. 26.1's
 * {@code SerializableChunkData} splits both: a tag is parsed into the record ({@code parse}) and the record read into a
 * chunk later ({@code read}, handed the level and the position), and a chunk is copied into the record on the server
 * thread ({@code copyOf}) and the record written to a tag ({@code write}). So the wear's bytes are taken from the tag
 * as it is parsed and kept on the record ({@link ErosionCarrier}), handed to the store when the record is read into a
 * chunk, as 1.21.1's read handed them; and asked of the store as the chunk is copied, on the server thread where
 * 1.21.1 asked as it wrote, and kept on the record until its tag is written. The blob and its key
 * ({@code ErosionStore.NBT_KEY}, a byte array at the root) are unchanged, so a 1.21.1 world's wear loads. The four
 * methods are vanilla's in NeoForge 26.1.2.122's patched jar; the Forge and Fabric modules carry the same design.
 */
@Mixin(SerializableChunkData.class)
public abstract class MixinChunkSerializer implements ErosionCarrier {

    /** The wear's bytes this record carries: read from its tag, or taken from its chunk for its tag. */
    @Unique
    private byte[] trmt$erosion;

    @Override
    public byte[] trmt$erosion() {
        return trmt$erosion;
    }

    @Override
    public void trmt$erosion(byte[] erosion) {
        trmt$erosion = erosion;
    }

    /**
     * Keep the bytes once vanilla has parsed the tag. Off the main thread, as 1.21.1's read was, so nothing more than
     * the bytes is read here.
     */
    @Inject(method = "parse", at = @At("RETURN"))
    private static void trmt$keepParsed(LevelHeightAccessor levelHeight, PalettedContainerFactory containers,
        CompoundTag tag, CallbackInfoReturnable<SerializableChunkData> callback) {
        SerializableChunkData parsed = callback.getReturnValue();
        if (parsed == null || tag == null) return;
        // From 1.21.5 the getter answers in an Optional; anything but a byte array is no wear.
        ((ErosionCarrier) (Object) parsed).trmt$erosion(
            tag.getByteArray(ErosionStore.NBT_KEY)
                .orElse(null));
    }

    /**
     * Park the wear for its chunk, as 1.21.1's read did at its head, now that the level and the position are both in
     * hand.
     *
     * <p>
     * This runs off the main thread, which is why the store does no more here than read the bytes:
     * what a chunk's wear needs next is a live level to catch up against, and that waits for the
     * chunk to arrive. The store's call is 1.21.1's, handed a tag holding just the key and the bytes.
     */
    @Inject(method = "read", at = @At("RETURN"))
    private void trmt$read(ServerLevel level, PoiManager poi, RegionStorageInfo region, ChunkPos pos,
        CallbackInfoReturnable<ProtoChunk> callback) {
        byte[] blob = trmt$erosion;
        if (level == null || pos == null || blob == null) return;
        CompoundTag tag = new CompoundTag();
        tag.putByteArray(ErosionStore.NBT_KEY, blob);
        // ChunkPos is a record by 26.1, and a key's name is identifier() (1.21.11).
        ErosionStore.get()
            .chunkDataLoaded(
                level.dimension()
                    .identifier(),
                pos.x(),
                pos.z(),
                tag);
    }

    /**
     * Take the wear as the chunk is copied for its save, on the server thread, where 1.21.1 took it as it wrote.
     *
     * <p>
     * A chunk's root tag is built fresh every save, so contributing nothing is not "leave what was
     * there" - it is "erase it". That is why the store is asked on every save rather than only when
     * it believes something changed: an abandoned chunk sheds the key rather than carrying an empty array for ever.
     */
    @Inject(method = "copyOf", at = @At("RETURN"))
    private static void trmt$take(ServerLevel level, ChunkAccess chunk,
        CallbackInfoReturnable<SerializableChunkData> callback) {
        SerializableChunkData copied = callback.getReturnValue();
        if (level == null || chunk == null || copied == null) return;
        ((ErosionCarrier) (Object) copied).trmt$erosion(
            ErosionStore.get()
                .chunkDataSaving(
                    level.dimension()
                        .identifier(),
                    chunk.getPos()
                        .x(),
                    chunk.getPos()
                        .z()));
    }

    /** Write at the return, into the tag vanilla has just built, what was taken when the chunk was copied. */
    @Inject(method = "write", at = @At("RETURN"))
    private void trmt$write(CallbackInfoReturnable<CompoundTag> callback) {
        CompoundTag tag = callback.getReturnValue();
        byte[] blob = trmt$erosion;
        if (tag == null || blob == null) return;
        tag.putByteArray(ErosionStore.NBT_KEY, blob);
    }
}
