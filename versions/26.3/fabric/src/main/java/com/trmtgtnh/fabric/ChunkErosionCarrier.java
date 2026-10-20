package com.trmtgtnh.fabric;

/**
 * The wear a chunk's serialised form carries for this mod between the moment the game reads or copies a chunk and
 * the moment it builds or writes its tag. Added to 26.1's {@code SerializableChunkData} by
 * {@code MixinChunkSerializer}; here, outside the mixin package, so the mixin's own code can name it.
 *
 * <p>
 * From 1.21.2 a chunk is saved in two steps - copied on the server's thread, written to a tag later on another - and
 * read in two - parsed from the tag, then built into a chunk for its level - where 1.21.1 did each in one call that
 * held the level, the chunk and the tag together. What 1.21.1 read and wrote in that one call is carried across the
 * gap on the copy itself: the same bytes, under the same key.
 */
public interface ChunkErosionCarrier {

    /** The wear bytes this copy carries - read off its tag, or given by the store as it was copied - or null. */
    byte[] trmt$erosion();

    void trmt$erosion(byte[] wear);
}
