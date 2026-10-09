package com.trmtgtnh.forge.mixin;

import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.trmtgtnh.client.render.OptiFineFaces;

/**
 * Keeps a settled snow layer's or carpet's step in OptiFine's face test (0.9.221) - see {@link OptiFineFaces}, which
 * says why OptiFine missed it and how this fails safe.
 *
 * <p>
 * Redirects OptiFine's own call in the block renderer it patches, {@code BlockUtils.shouldSideBeRendered}, named in the
 * names Forge's, where OptiFine ships its classes in MCP's. {@code remap = false} because the target is OptiFine's and
 * in no refmap; {@code require = 0} because a
 * game without OptiFine has no such call, and an OptiFine that no longer makes it costs this fix and nothing else. The
 * handler only calls out: a mixin body is read by an ASM older than this workspace's JDK.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class MixinOptiFineSettledFaces {

    @Redirect(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Lnet/optifine/util/BlockUtils;shouldSideBeRendered(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/IBlockReader;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/Direction;Lnet/optifine/render/RenderEnv;)Z",
            remap = false),
        require = 0,
        remap = false)
    private boolean trmt$keepSettledFace(BlockState state, BlockGetter level, BlockPos pos, Direction face,
        @Coerce Object env) {
        return OptiFineFaces.decide(state, level, pos, face, env);
    }
}
