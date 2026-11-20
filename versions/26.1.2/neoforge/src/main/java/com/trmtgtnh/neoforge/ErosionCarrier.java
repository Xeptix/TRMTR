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

    /**
     * The original mod's worn squares converted from the tag as it was parsed (0.9.229 W5, spec 2.9): null when the
     * parse converted nothing, and a list, empty when no square carries a record, when it converted something. The same
     * in all three loaders' modules.
     */
    java.util.List<com.trmtgtnh.original.OriginalWorld.Square> trmt$converted();

    void trmt$converted(java.util.List<com.trmtgtnh.original.OriginalWorld.Square> squares);
}
