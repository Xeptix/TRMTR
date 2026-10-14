package com.trmtgtnh.forge.mixin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.trmtgtnh.client.texture.WearTextures;

/**
 * Where this mod's wear sprites are planned for the block atlas, and where what goes to its stitcher is counted, under
 * Forge - the 1.7.10 edition's MixinTextureMap.trmt$noteAtlasRoom and trmt$countStitchedSprites, and the 1.16.5
 * edition's stitch event.
 *
 * <p>
 * From 1.19.3 an atlas is loaded by a {@code SpriteLoader}, which lists what {@code atlases/blocks.json}'s sources
 * find and stitches those, and Forge's {@code TextureStitchEvent.Pre}, where the 1.16.5 edition planned, went with the
 * old atlas: a mod can no longer add a name to the stitch. So the plan is made at the head of {@code loadAndStitch},
 * before the sources are listed, and its sprites are found by that listing - see {@code WearTextures.beginBlockStitch}.
 * The names resolve into the atlas's own sprites at {@code TextureStitchEvent.Post}, which Forge still fires, in
 * ForgeTextures.
 *
 * <p>
 * The count is taken at the head of {@code stitch}, with what the sources loaded and the mipmap levels the stitcher is
 * built with, which is that method's own argument: the stitcher is made with them before anything else, and Forge
 * lowers the levels after it only when its own {@code allowMipmapLowering} is set. Said before the stitch, as there, so
 * that a stitch which then fails to fit still leaves its figures in the log. {@code require = 0}, as the other
 * editions' count is: a mod that has rewritten this method should cost that line, not the game, and
 * WearTextures.reportStitchHooks says at the end of every stitch whether it ran. The plan is required, because without
 * it there is no wear at all.
 *
 * <p>
 * It lives in this module for the reason the other method-naming mixins do: common's annotation processor writes a
 * refmap in Fabric's intermediary names, which Forge refuses. Only the block atlas: the game builds several, and none
 * of the others is this mod's business.
 */
@Mixin(SpriteLoader.class)
public abstract class MixinSpriteLoader {

    @Shadow
    @Final
    private ResourceLocation location;

    @Inject(method = "loadAndStitch", at = @At("HEAD"))
    private void trmt$planWearSprites(ResourceManager manager, ResourceLocation atlasInfo, int mipLevel,
        Executor executor, CallbackInfoReturnable<CompletableFuture<SpriteLoader.Preparations>> callback) {
        if (!TextureAtlas.LOCATION_BLOCKS.equals(location)) return;
        WearTextures.beginBlockStitch(
            manager,
            ((SpriteResourceLoaderSources) SpriteResourceLoader.load(manager, atlasInfo)).trmt$sources());
    }

    @Inject(require = 0, method = "stitch", at = @At("HEAD"))
    private void trmt$countStitchedSprites(List<SpriteContents> contents, int mipLevel, Executor executor,
        CallbackInfoReturnable<SpriteLoader.Preparations> callback) {
        if (!TextureAtlas.LOCATION_BLOCKS.equals(location)) return;
        WearTextures.noteStitched(contents, mipLevel);
    }
}
