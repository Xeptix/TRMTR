package com.trmtgtnh.forge;

/**
 * The wear a chunk's save record carries between the steps 1.21.2 split a chunk's load and save into: parsed off the
 * main thread and read into a chunk on it, copied from a chunk on it and written off it. MixinChunkSerializer puts
 * this on the game's record (SerializableChunkData); outside the mixin package, because the game's class is made to
 * implement it.
 */
public interface ErosionCarrier {

    /** The bytes under ErosionStore.NBT_KEY, or null when there are none. */
    byte[] trmt$erosion();

    void trmt$erosion(byte[] erosion);
}
