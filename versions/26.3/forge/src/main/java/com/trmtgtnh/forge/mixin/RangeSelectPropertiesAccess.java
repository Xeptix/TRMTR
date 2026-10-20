package com.trmtgtnh.forge.mixin;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Vanilla's table of numeric item properties, which a client item file's {@code range_dispatch} names its property from
 * (client/renderer/item/properties/numeric/RangeSelectItemModelProperties.java, from 1.21.4). Private, and Forge 64 has
 * no event for adding to it, so the tampers' grade property is put in through this - see
 * TrmtForgeClient.gradeProperty. The 1.21.1 edition registered the property per item through ItemProperties, which
 * Forge made public; that class is gone with item models.
 */
@Mixin(RangeSelectItemModelProperties.class)
public interface RangeSelectPropertiesAccess {

    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends RangeSelectItemModelProperty>> trmt$idMapper() {
        throw new AssertionError();
    }
}
