package com.trmtgtnh.forge.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.trmtgtnh.client.texture.WearTextures;

/**
 * Measures the block atlas before anything is planned, and counts what goes to its stitcher - the
 * 1.7.10 edition's MixinTextureMap.trmt$noteAtlasRoom and trmt$countStitchedSprites, under Forge.
 *
 * <p>
 * Until 0.9.220 this edition carried WearTextures.noteStitched and everything it writes, and
 * nothing called it, and it planned every stitch as though the pack had taken half the atlas,
 * saying that no loader hands out the list it would be measured from. Both were found by running
 * the game with a stack trace inside the method that never printed. The list was there all along:
 * it is the set Forge hands to the stitch event, in the method this mixin names.
 *
 * <p>
 * Forge fires an event on either side of a stitch, and this mod plans and resolves its sprites
 * from those, in ForgeTextures; what the events do not carry is the moment just before the plan
 * and the moment just before the stitch, and those are what the other edition injects at. The
 * measure is taken as the set goes to the event, so it is in hand before the planner runs - where
 * the other edition injects, at its own call to ForgeHooksClient.onTextureStitchedPre. The count is
 * taken as the atlas calls its stitcher, said before the stitch, as there, so that a stitch which
 * then fails to fit still leaves its figures in the log.
 *
 * <p>
 * All three are {@code require = 0}, as the other edition's injections are: a mod that has
 * rewritten this method should cost these lines, not the game. An optional injection that fails to
 * bind writes nothing anywhere, which is why WearTextures.reportStitchHooks says so at the end of
 * every stitch.
 *
 * <p>
 * It lives in this module for the reason the other method-naming mixins do: common's annotation
 * processor writes a refmap in Fabric's intermediary names, which Forge refuses. Only the block
 * atlas: the game builds several, and none of the others is this mod's business.
 */
@Mixin(TextureAtlas.class)
public abstract class MixinTextureAtlas {

    private boolean trmt$isBlockAtlas() {
        return TextureAtlas.LOCATION_BLOCKS.equals(
            ((TextureAtlas) (Object) this).location());
    }

    /**
     * Measures the block atlas from every texture its models want, as Forge hands that set to the
     * stitch event - before this mod's planner, which is a handler of that event, runs.
     */
    @ModifyArg(
        require = 0,
        method = "prepareToStitch",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/client/ForgeHooksClient;onTextureStitchedPre(Lnet/minecraft/client/renderer/texture/TextureAtlas;Ljava/util/Set;)V"),
        index = 1)
    private Set<ResourceLocation> trmt$noteAtlasRoom(Set<ResourceLocation> names) {
        if (trmt$isBlockAtlas()) WearTextures.measureAtlas(names);
        return names;
    }

    /** Every description the block atlas hands its stitcher during the current stitch. */
    @Unique
    private List<TextureAtlasSprite.Info> trmt$stitched;

    /**
     * Notes every sprite description as the atlas hands it to the stitcher, missingno with them -
     * both of the method's calls to {@code registerSprite}, because the second is missingno's.
     */
    @ModifyArg(
        require = 0,
        method = "prepareToStitch",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/Stitcher;registerSprite(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite$Info;)V"))
    private TextureAtlasSprite.Info trmt$noteStitchedSprite(TextureAtlasSprite.Info info) {
        if (trmt$isBlockAtlas()) {
            if (trmt$stitched == null) trmt$stitched = new ArrayList<TextureAtlasSprite.Info>();
            trmt$stitched.add(info);
        }
        return info;
    }

    /**
     * Says what went to the stitcher against what the plan priced, as the stitch begins. At the
     * mipmap levels the stitcher was built with, which is this method's own argument; Forge never
     * lowers them here ({@code if (false) // FORGE: do not lower the mipmap level}).
     */
    @Inject(
        require = 0,
        method = "prepareToStitch",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/Stitcher;stitch()V"))
    private void trmt$countStitchedSprites(ResourceManager manager, Stream<ResourceLocation> names,
        ProfilerFiller profiler, int mipLevel, CallbackInfoReturnable<TextureAtlas.Preparations> callback) {
        if (!trmt$isBlockAtlas()) return;
        List<TextureAtlasSprite.Info> stitched = trmt$stitched;
        trmt$stitched = null;
        WearTextures.noteStitched(
            stitched == null ? new ArrayList<TextureAtlasSprite.Info>() : stitched, mipLevel);
    }
}
