package com.trmtgtnh.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Sodium family's face test, kept open for a settled step the way {@link MixinSettledFaces} keeps
 * vanilla's. Rubidium, Embeddium and Sodium mesh chunks themselves and ask {@code shouldDrawSide} rather
 * than vanilla's test - the 1.7.10 edition answers Angelica's better face culling the same way.
 *
 * <p>
 * Named by string and gated by {@link OculusGate}, so a client without one of them never sees it.
 *
 * <p>
 * <strong>Where the test is at 26.1.2.</strong> Sodium for 26.1.2 (its {@code 26.1.2/stable} branch, read from its
 * source) moved {@code shouldDrawSide} out of {@code BlockOcclusionCache} into {@code AbstractBlockRenderContext},
 * which its terrain and its other block pipelines share, and it takes only the face now: the block, the getter and the
 * square are the context's own {@code state}, {@code level} and {@code pos}, set for each block before its faces are
 * asked. So they are read from there, and the rule is {@link com.trmtgtnh.client.render.FaceRule}'s, as every loader's. There is no Embeddium for 26.1.2, and
 * Fabric runs Mojang's names from 26.1 (no intermediary), so the one Mojang descriptor is the whole of the name.
 */
@Pseudo
@Mixin(targets = OculusGate.SODIUM_FACES, remap = false)
public abstract class MixinSettledFacesSodium {

    @Shadow(remap = false)
    protected BlockAndTintGetter level;

    @Shadow(remap = false)
    protected BlockState state;

    @Shadow(remap = false)
    protected BlockPos pos;

    @Inject(
        method = "shouldDrawSide(Lnet/minecraft/core/Direction;)Z",
        at = @At("RETURN"),
        cancellable = true,
        require = 0,
        remap = false)
    private void trmt$keepSettledFace(Direction face, CallbackInfoReturnable<Boolean> callback) {
        // The rule every face hook asks - a worn stair beside, two windows, a settled step - is FaceRule's.
        boolean vanilla = callback.getReturnValueZ();
        boolean drawn = com.trmtgtnh.client.render.FaceRule.decide(vanilla, state, level, pos, face);
        if (drawn != vanilla) callback.setReturnValue(Boolean.valueOf(drawn));
    }
}
