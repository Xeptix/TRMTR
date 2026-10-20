package com.trmtgtnh.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The bounds of the face the lighter is working out, which MixinSunkLightVanilla reads in both lighters NeoForge draws
 * blocks with: vanilla's {@code BlockModelLighter}, and NeoForge's {@code EnhancedBlockModelLighter}, which extends it
 * and fills the same array (26.1.2.122). An accessor, because the second of those is a subclass and a shadow on a
 * subclass cannot name its superclass's field.
 */
@Mixin(net.minecraft.client.renderer.block.BlockModelLighter.class)
public interface LighterFaceShape {

    @Accessor("faceShape")
    float[] trmt$faceShape();
}
