package com.trmtgtnh.fabric.mixin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import net.minecraft.resources.Identifier;
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
 * Fabric - the 1.7.10 edition's MixinTextureMap.trmt$noteAtlasRoom and trmt$countStitchedSprites, and the 1.16.5
 * edition's mixin on TextureAtlas.prepareToStitch.
 *
 * <p>
 * From 1.19.3 an atlas is loaded by a {@code SpriteLoader}, which lists what {@code atlases/blocks.json}'s sources
 * find and stitches those, and {@code prepareToStitch}, where the 1.16.5 edition added its names to the stream the
 * stitcher took, went with the old atlas: a mod can no longer add a name to the stitch. So the plan is made at the head
 * of {@code loadAndStitch}, before the sources are listed, and its sprites are found by that listing - see
 * {@code WearTextures.beginBlockStitch}. The names resolve into the atlas's own sprites as the atlas takes the stitch,
 * in MixinTextureAtlas.
 *
 * <p>
 * The count is taken at the head of {@code stitch}, with what the sources loaded and the mipmap levels the stitcher is
 * built with, which is that method's own argument: the stitcher is made with them before anything else, and vanilla
 * lowers the levels after it only for a sprite too small to be mipped that far. Said before the stitch, as there, so
 * that a stitch which then fails to fit still leaves its figures in the log. {@code require = 0}, as the other
 * editions' count is: a mod that has rewritten this method should cost that line, not the game, and
 * WearTextures.reportStitchHooks says at the end of every stitch whether it ran. The plan is required, because without
 * it there is no wear at all.
 *
 * <p>
 * It lives in this module, beside the Forge module's copy, for the reason the other method-naming mixins do: common's
 * annotation processor writes a refmap in Fabric's intermediary names, which Forge refuses. Only the block atlas: the game builds several, and none
 * of the others is this mod's business.
 */
@Mixin(SpriteLoader.class)
public abstract class MixinSpriteLoader {

    @Shadow
    @Final
    private Identifier location;

    // From 1.20.5 there are two loadAndStitch, the shorter handing the longer the game's own metadata readers; the
    // longer is the one every atlas goes through, so it is named in full and the plan is made once. The atlas's list
    // of sources is SpriteSourceList's at this version, where it was SpriteResourceLoader's.
    @Inject(
        method = "loadAndStitch(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/Identifier;ILjava/util/concurrent/Executor;Ljava/util/Set;)Ljava/util/concurrent/CompletableFuture;",
        at = @At("HEAD"))
    private void trmt$planWearSprites(ResourceManager manager, Identifier atlasInfo, int mipLevel,
        // The metadata sections are a Set by 26.1, where 1.21.1 took a Collection; the same argument.
        Executor executor, java.util.Set<?> metadata,
        CallbackInfoReturnable<CompletableFuture<SpriteLoader.Preparations>> callback) {
        if (!TextureAtlas.LOCATION_BLOCKS.equals(location)) return;
        WearTextures.beginBlockStitch(
            manager,
            ((SpriteResourceLoaderSources) SpriteSourceList.load(manager, atlasInfo)).trmt$sources());
    }

    @Inject(require = 0, method = "stitch", at = @At("HEAD"))
    private void trmt$countStitchedSprites(List<SpriteContents> contents, int mipLevel, Executor executor,
        CallbackInfoReturnable<SpriteLoader.Preparations> callback) {
        if (!TextureAtlas.LOCATION_BLOCKS.equals(location)) return;
        WearTextures.noteStitched(contents, mipLevel);
    }
}
