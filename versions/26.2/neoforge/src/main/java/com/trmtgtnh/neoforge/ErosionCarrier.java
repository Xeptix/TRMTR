package com.trmtgtnh.neoforge;

/**
 * The wear a chunk's saved record carries between the calls that 1.21.2 split one read and one write into.
 * MixinChunkSerializer puts this on the game's own record of a chunk on disk (SerializableChunkData); outside the mixin
 * package, because the game's class is made to implement it. The same in all three loaders' modules.
 */
public interface ErosionCarrier {

    /** The bytes kept under ErosionStore.NBT_KEY, or null for none. */
    byte[] trmt$erosion();

    void trmt$erosion(byte[] erosion);
}
