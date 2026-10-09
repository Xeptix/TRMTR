package com.trmtgtnh.fabric.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import com.trmtgtnh.client.texture.WearTextures;

/**
 * Where this mod's wear sprites join the block atlas, under Fabric.
 *
 * <p>
 * Forge fires an event on either side of a stitch and needs no mixin for this. Fabric offered the
 * same thing once - {@code ClientSpriteRegistryCallback} - and it is not in the API at this version,
 * so the two moments are taken from vanilla's own method instead. That is the whole of what this
 * loader has to know about the texture pipeline.
 *
 * <p>
 * <strong>It lives in this module rather than in common, and that is deliberate.</strong> A mixin
 * naming a method has its name written into a refmap, and the common module's annotation processor
 * writes that refmap in Fabric's intermediary names - which Forge then refuses, having been handed a
 * target class called {@code net/minecraft/class_4723}. The one common mixin this mod has gets away
 * with it by naming only {@code <init>}, which is the same word in every namespace. A method cannot
 * do that, so it belongs where the processor will name it correctly.
 *
 * <p>
 * Only the block atlas: the game builds several, and none of the others is this mod's business.
 *
 * <p>
 * Since 0.9.220 it also measures the atlas before anything is planned, and counts what goes to the
 * stitcher - the 1.7.10 edition's two injections of those names, which this edition had carried
 * the reporting half of and never called. The measure rides with the naming, which stays required
 * because without it there is no wear at all. The count is {@code require = 0}, as there: a mod
 * that has rewritten this method should cost that line, not the game, and the end of the stitch
 * says when it did not run.
 */
@Mixin(TextureAtlas.class)
public abstract class MixinTextureAtlas {

    private boolean trmt$isBlockAtlas() {
        return TextureAtlas.LOCATION_BLOCKS.equals(
            ((TextureAtlas) (Object) this).location());
    }

    /**
     * Adds this mod's sprite names to the list the stitcher is about to take.
     *
     * <p>
     * Nothing in any model mentions a wear texture, so without this the atlas would never ask the
     * pack for one and every worn block would draw the missing-texture chequer. The names are
     * appended to the stream rather than replacing it, obviously, but it is worth saying: the stream
     * handed in is every texture the models want, and losing it would be the whole game untextured.
     */
    @ModifyVariable(method = "prepareToStitch", at = @At("HEAD"), argsOnly = true)
    private Stream<ResourceLocation> trmt$nameWearSprites(Stream<ResourceLocation> names) {
        if (!trmt$isBlockAtlas()) return names;

        // Gathered first, so the atlas can be measured from every texture the models want before
        // anything is planned - the 1.7.10 edition's MixinTextureMap.trmt$noteAtlasRoom, an injection
        // of its own there; here the planner needs the same moment. The stream can be read only once,
        // and the stitch reads it again.
        List<ResourceLocation> wanted = names.collect(Collectors.toList());
        WearTextures.measureAtlas(wanted);

        final List<ResourceLocation> ours = new ArrayList<ResourceLocation>();
        WearTextures.beginStitch(new WearTextures.Registrar() {

            @Override
            public void sprite(ResourceLocation name) {
                ours.add(name);
            }
        });
        return Stream.concat(wanted.stream(), ours.stream());
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
     * Says what went to the stitcher against what the plan priced, as the stitch begins - the
     * 1.7.10 edition's MixinTextureMap.trmt$countStitchedSprites. At the mipmap levels the stitcher
     * was built with, which is this method's own argument: vanilla may then load the sprites at
     * fewer, but the stitcher has already rounded every size at these.
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

    /** Resolves those names into the atlas's own sprites, now that there are some. */
    @Inject(method = "reload", at = @At("TAIL"))
    private void trmt$resolveWearSprites(TextureAtlas.Preparations prepared, CallbackInfo callback) {
        if (!trmt$isBlockAtlas()) return;
        WearTextures.endStitch((TextureAtlas) (Object) this);
    }
}
