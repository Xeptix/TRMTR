package com.trmtgtnh.mixin;

import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraftforge.fml.common.ProgressManager;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.client.texture.WearTextures;

/**
 * Counts what went to the stitcher, this mod's sprites apart from everything else, each at the size the stitcher
 * will round it to, so the log can say whether the plan priced them right - the 1.7.10 edition's
 * MixinTextureMap.trmt$countStitchedSprites, carried.
 *
 * <p>
 * Until 0.9.220 this edition carried WearTextures.noteStitched and everything it writes, and nothing called it: the
 * line the other edition writes as its atlas goes to the stitcher was never written here (found by running it, with
 * a stack trace inside the method that never printed). Said before the stitch rather than after it, as there, so that
 * a stitch which then fails to fit - Forge rethrows the stitcher's exception and the event after the stitch never
 * fires - still leaves its figures in the log. Forge 1.12.2 stitches in its own finishLoading, which it added and
 * which no mapping renames.
 *
 * <p>
 * {@code require = 0}, as the other edition's injections carry it: a mod that has replaced this method should cost
 * this line, not the game. An optional injection that fails to bind writes nothing anywhere, which is why
 * {@link WearTextures#reportStitchHooks()} says so at the end of every stitch that planned anything.
 *
 * <p>
 * And tells the sprite pass the order the atlas is about to load its sprites in, which the other edition has no need
 * of: there every picture is composed in one place after loading, here each is asked for by its own sprite's load.
 *
 * <p>
 * The block atlas only; on 1.12.2 it is the one atlas for blocks and items, and a mod's own texture map is not this
 * mod's business. Its mipmap levels are the stitcher's own: Forge 1.12.2 never lowers them here
 * ({@code if (false) // FORGE: do not lower the mipmap level}).
 */
@Mixin(TextureMap.class)
public class MixinTextureMap {

    @Shadow
    @Final
    private Map<String, TextureAtlasSprite> mapRegisteredSprites;

    @Shadow
    private int mipmapLevels;

    @Inject(
        require = 0,
        method = "finishLoading",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/Stitcher;doStitch()V"))
    private void trmt$countStitchedSprites(Stitcher stitcher, ProgressManager.ProgressBar bar, int j, int k,
        CallbackInfo callback) {
        if ((Object) this != Minecraft.getMinecraft()
            .getTextureMapBlocks()) return;
        WearTextures.noteStitched(mapRegisteredSprites, mipmapLevels);
    }

    /**
     * Tells the sprite pass the order the block atlas is about to load its sprites in, so it can compose wear pictures
     * ahead of the loads that will ask for them (WearGeneration.Ahead).
     *
     * <p>
     * At the call that makes the copy the loading loop walks, {@code Maps.newHashMap(this.mapRegisteredSprites)}, and
     * not merely at the head of the method, so the map is read as that copy is made of it. The pass makes its own copy
     * the same way, which walks in the same order. Optional for the same reason as the count above: a mod that has
     * rewritten the loop should cost speed, not the game, and the pass then guesses at the order and says so.
     */
    @Inject(
        require = 0,
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Lcom/google/common/collect/Maps;newHashMap(Ljava/util/Map;)Ljava/util/HashMap;",
            remap = false))
    private void trmt$noteLoadOrder(IResourceManager manager, CallbackInfo callback) {
        if ((Object) this != Minecraft.getMinecraft()
            .getTextureMapBlocks()) return;
        WearTextures.noteLoadOrder(mapRegisteredSprites);
    }
}
