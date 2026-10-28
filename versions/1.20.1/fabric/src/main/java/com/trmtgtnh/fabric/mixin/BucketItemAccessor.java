package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.material.Fluid;

/**
 * What a bucket holds, which the bucket keeps to itself.
 *
 * <p>
 * From 0.9.228 a container is read through Fabric API's fluid transfer API ({@code FabricFluids}), as Forge reads its
 * fluid handler, and every bucket Fabric API knows of answers there. This is what is left of the older reading: a
 * mod's {@code BucketItem} that registered no fluid storage of its own is still read by the field it was built with,
 * so a bucket that paid before 0.9.228 pays still.
 */
@Mixin(BucketItem.class)
public interface BucketItemAccessor {

    @Accessor("content")
    Fluid trmt$content();
}
