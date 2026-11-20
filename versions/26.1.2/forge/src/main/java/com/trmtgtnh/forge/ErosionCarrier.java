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

    /**
     * The original mod's worn squares converted from the tag as it was parsed (0.9.229 W5, spec 2.9): null when the
     * parse converted nothing, and a list, empty when no square carries a record, when it converted something. The
     * same in all three loaders' modules.
     */
    java.util.List<com.trmtgtnh.original.OriginalWorld.Square> trmt$converted();

    void trmt$converted(java.util.List<com.trmtgtnh.original.OriginalWorld.Square> squares);
}
