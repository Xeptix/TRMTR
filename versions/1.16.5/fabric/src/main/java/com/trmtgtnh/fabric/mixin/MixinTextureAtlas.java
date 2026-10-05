package com.trmtgtnh.fabric.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

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

        final List<ResourceLocation> ours = new ArrayList<ResourceLocation>();
        WearTextures.beginStitch(new WearTextures.Registrar() {

            @Override
            public void sprite(ResourceLocation name) {
                ours.add(name);
            }
        });
        return Stream.concat(names, ours.stream());
    }

    /** Resolves those names into the atlas's own sprites, now that there are some. */
    @Inject(method = "reload", at = @At("TAIL"))
    private void trmt$resolveWearSprites(TextureAtlas.Preparations prepared, CallbackInfo callback) {
        if (!trmt$isBlockAtlas()) return;
        WearTextures.endStitch((TextureAtlas) (Object) this);
    }
}
