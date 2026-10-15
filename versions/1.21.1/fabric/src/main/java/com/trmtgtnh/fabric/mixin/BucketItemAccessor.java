package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.material.Fluid;

/**
 * What a bucket holds, which the bucket keeps to itself.
 *
 * <p>
 * Forge has a capability that asks any container what is inside it, and a mod's cell or tank answers
 * as readily as a bucket does. Fabric has nothing of the sort at this version - its fluid transfer
 * API is several versions away - so the only containers that can be read here are buckets, and the
 * only way to read one is the field it was built with.
 *
 * <p>
 * An accessor rather than a hardcoded list of the vanilla buckets, because a mod's bucket is a
 * {@code BucketItem} too and this reads it exactly as well. What it cannot read is a container that
 * is not a bucket at all, and that is the gap this loader has and the other does not.
 */
@Mixin(BucketItem.class)
public interface BucketItemAccessor {

    @Accessor("content")
    Fluid trmt$content();
}
