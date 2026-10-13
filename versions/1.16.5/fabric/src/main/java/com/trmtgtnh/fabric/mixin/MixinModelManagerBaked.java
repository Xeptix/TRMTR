package com.trmtgtnh.fabric.mixin;

import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.client.texture.WearTextures;

/**
 * The end of the model bake (WP30, 0.9.224): the block atlas has every sprite's pixels and every block model is baked
 * against it, so a wear picture's faces can be asked of the sprites the blocks actually draw. The method exists under
 * this name in both loaders' jars, in a full reload and the narrow rebuild alike (WearRestitch), on the render thread
 * before anything is drawn with the new atlas. In each loader's module rather than common, because a mixin naming a
 * method writes that name into its module's refmap, and common's is in Fabric's names.
 */
@Mixin(ModelManager.class)
public abstract class MixinModelManagerBaked {

    @Inject(
        method = "apply(Lnet/minecraft/client/resources/model/ModelBakery;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At("TAIL"))
    private void trmt$afterBake(ModelBakery bakery, ResourceManager manager, ProfilerFiller profiler,
        CallbackInfo info) {
        WearTextures.afterBake((ModelManager) (Object) this);
    }
}
