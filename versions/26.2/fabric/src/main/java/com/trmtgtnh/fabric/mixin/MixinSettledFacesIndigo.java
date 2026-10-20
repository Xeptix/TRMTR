package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@link MixinSettledFaces}' rule, asked where Fabric API's own renderer asks the face test.
 *
 * <p>
 * Under Fabric API a chunk's blocks are drawn by its renderer, Indigo, rather than vanilla's: at 26.1.2 by
 * {@code AltModelBlockRendererImpl}, which culls a face in its own {@code shouldCullFace(Direction)} by asking
 * {@code Block.shouldRenderFace(state, neighbourState, face)} - two states, as vanilla's static test is from 1.21.2 -
 * with the level, the square and its state in its own fields (read from fabric-renderer-indigo 8.1.5, Fabric API
 * 0.155.3). At 1.21.1 the rule was asked in vanilla's {@code Block.shouldRenderFace}, which every renderer called
 * with the level and the square; this asks it at that same question, through
 * {@link com.trmtgtnh.client.render.FaceRule}: a face beside a worn stair is tested against the stair at its square,
 * two windows hide the face between them, a settled block keeps its step, and otherwise vanilla's answer stands.
 *
 * <p>
 * Optional, as the face hooks always were, and named by its class name because Indigo is Fabric API's implementation
 * and is named nowhere at compile time: a Fabric API that has moved this costs a picture, never the game.
 */
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl")
public abstract class MixinSettledFacesIndigo {

    @Shadow
    private BlockAndTintGetter level;

    @Shadow
    private BlockPos pos;

    @Redirect(
        method = "shouldCullFace(Lnet/minecraft/core/Direction;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;shouldRenderFace(Lnet/minecraft/world/level/block/state/BlockState;"
                + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z"),
        require = 0)
    private boolean trmt$keepSettledFace(BlockState state, BlockState neighbour, Direction face) {
        boolean vanilla = Block.shouldRenderFace(state, neighbour, face);
        if (level == null || pos == null) return vanilla;
        return com.trmtgtnh.client.render.FaceRule.decide(vanilla, state, level, pos, face);
    }
}
