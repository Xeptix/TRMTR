package com.trmtgtnh.mixin;

import net.minecraft.client.renderer.BufferBuilder;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.trmtgtnh.client.model.BakedLight;

/**
 * A quad's baked light kept wherever the face's light is written over it (0.9.228, real glow): see {@link BakedLight}.
 * The game's own block renderer, which draws whenever Forge's light pipeline is off, copies a quad's vertices into the
 * buffer and then hands {@code putBrightness4} the face's light for the four it just copied; OptiFine switches that
 * pipeline off and draws through render methods of its own that end in the same call. So the call itself takes the
 * brighter of each light it is handed and the light that vertex carried in, which reaches every one of those callers
 * however they are named. A quad of the game's own carries nought and is written exactly as before.
 *
 * <p>
 * The first version wrapped the call inside the game's two render methods, named by their arguments, and a night
 * photograph under OptiFine found the glow unlit: OptiFine's copies were never reached (2026-11-16).
 */
@Mixin(BufferBuilder.class)
public abstract class MixinBakedLightKept {

    @ModifyVariable(method = "putBrightness4", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int trmt$first(int light) {
        return BakedLight.brighter((BufferBuilder) (Object) this, 0, light);
    }

    @ModifyVariable(method = "putBrightness4", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int trmt$second(int light) {
        return BakedLight.brighter((BufferBuilder) (Object) this, 1, light);
    }

    @ModifyVariable(method = "putBrightness4", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int trmt$third(int light) {
        return BakedLight.brighter((BufferBuilder) (Object) this, 2, light);
    }

    @ModifyVariable(method = "putBrightness4", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private int trmt$fourth(int light) {
        return BakedLight.brighter((BufferBuilder) (Object) this, 3, light);
    }
}
