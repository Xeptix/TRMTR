package com.trmtgtnh.mixin;

import java.util.Collections;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.client.texture.WearTextures;

/**
 * Generates the wear textures at the one moment every block's own pixels are available.
 *
 * <p>
 * A worn block is drawn from the pixels of the block it is covering, so generating one means
 * reading another sprite. There is exactly one window in which that works. Sprites are loaded in
 * the order a hash map happens to iterate, so during loading roughly half of them have their
 * pixels and the rest do not - which is why some blocks wore correctly and others silently fell
 * back to a stock texture, with no pattern to it. And immediately after each sprite is uploaded
 * to the atlas its pixels are thrown away, so by the time stitching has finished there is
 * nothing left to read.
 *
 * <p>
 * Between those two is the moment: everything loaded, nothing discarded yet, sizes already fixed
 * at the edges the plan priced so nothing can move. Stitching is about to begin; every wear
 * sprite's picture is composed here, once, and its mipmaps generated with it, since the pass that
 * mipmapped every sprite ran over a placeholder.
 */
@Mixin(TextureMap.class)
public class MixinTextureMap {

    @Shadow
    private int mipmapLevels;

    @Shadow
    private java.util.Map mapRegisteredSprites;

    /**
     * Tells the wear planner how much atlas there is before it plans anything.
     *
     * <p>
     * Four things, none of which can be worked out from inside a stitch event: which textures the atlas
     * has gathered, so that their files can be read for their sizes; how large a texture this card will
     * address; whether anisotropic filtering will widen each of them; and the mipmap levels the stitcher
     * was built with a moment ago, which the atlas may lower on its own field before it stitches. Taken at
     * the call that fires the event, which is after every block and item has registered its icons and
     * before any handler of the event has run - so before this mod registers anything, and before any
     * other mod's handler has either. What those add arrives afterwards, is not in the count, and is
     * counted for the log by the injection below.
     *
     * <p>
     * Anisotropic filtering is read from the game settings rather than shadowed off the atlas. The two
     * are the same number - the game hands the setting to the block atlas at start-up and again
     * whenever it changes, and asks for a re-stitch when it does - and a shadowed field is one more
     * thing whose absence would stop every injection in this class from applying.
     *
     * <p>
     * {@code require = 0} for the same reason the injections below carry it: a pack that has replaced
     * this class is a pack where the wear textures are planned as though half the atlas were taken,
     * rather than one where the game refuses to start. None of them says anything at all when it
     * fails to bind, which is what {@link WearTextures#reportStitchHooks()} exists to make audible.
     */
    @Inject(
        require = 0,
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/client/ForgeHooksClient;onTextureStitchedPre(Lnet/minecraft/client/renderer/texture/TextureMap;)V"))
    private void trmt$noteAtlasRoom(IResourceManager manager, CallbackInfo callback) {
        trmt$noteRoom(manager);
    }

    /**
     * The same, under OptiFine 1.7.10 (0.9.229, stitch-optifine). OptiFine's own loadTextureAtlas fires the stitch
     * event through its Reflector, {@code Reflector.callVoid(Reflector.ForgeHooksClient_onTextureStitchedPre, this)},
     * the first Reflector.callVoid in the method (read from HD_U_E7's TextureMap, bpz, with its patch applied), so the
     * injection above has nothing to attach to there and the plan was a guess at half the square, naming no stitcher.
     * Reflector is OptiFine's own class, in no package and never obfuscated, so its name is not remapped. Without
     * OptiFine this finds no call and writes nothing, as the one above does under OptiFine; at most one of the two runs
     * in any game.
     */
    @Inject(
        require = 0,
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "LReflector;callVoid(LReflectorMethod;[Ljava/lang/Object;)V",
            ordinal = 0,
            remap = false))
    private void trmt$noteAtlasRoomUnderOptiFine(IResourceManager manager, CallbackInfo callback) {
        trmt$noteRoom(manager);
    }

    private void trmt$noteRoom(IResourceManager manager) {
        TextureMap map = (TextureMap) (Object) this;
        if (map.getTextureType() != 0) return;
        Minecraft game = Minecraft.getMinecraft();
        WearTextures.noteAtlasRoom(
            manager,
            mapRegisteredSprites == null ? Collections.emptyMap() : mapRegisteredSprites,
            Minecraft.getGLMaximumTextureSize(),
            game != null && game.gameSettings != null && game.gameSettings.anisotropicFiltering > 1,
            mipmapLevels);
    }

    /**
     * Counts what went to the stitcher, this mod's sprites apart from everything else, each at the size
     * the stitcher will round it to, so the log can say whether the plan priced them right. The field
     * shadowed here is only the fallback for that rounding: by this call the atlas may have lowered it
     * below the levels its stitcher was built with, so the count uses the levels the measure noted, and
     * the log says so when there were none to use. Said before the stitch rather than after it, so that a
     * stitch which then fails to fit still leaves its figures in the log. Kept apart from the build below so
     * that neither can go missing on the other's account, and so the build stays the single call it is.
     */
    @Inject(
        require = 0,
        method = "loadTextureAtlas",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/Stitcher;doStitch()V"))
    private void trmt$countStitchedSprites(IResourceManager manager, CallbackInfo callback) {
        trmt$count();
    }

    @Inject(
        require = 0,
        method = "loadTextureAtlas",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/Stitcher;doStitch()V"))
    private void trmt$buildWearTextures(IResourceManager manager, CallbackInfo callback) {
        trmt$build(manager);
    }

    /**
     * The same two, under FastCraft 1.25 (0.9.229, fastcraft-wear). FastCraft's transformer replaces this method's
     * call to {@code Stitcher.doStitch()} with its own hook, the static {@code fastcraft.HC.g(Stitcher, TextureMap)},
     * whose body is {@code fastcraft.ah.a(Stitcher, TextureMap)}: it sorts every sprite it was handed, packs them into
     * an atlas of its own, writes their places back and copies each sprite's frames into one buffer for the whole
     * atlas (read with javap from fastcraft-1.25.jar). That rewrite comes before this mod's mixin, so the two
     * injections above found no call and no worn picture was ever composed. These wait at FastCraft's call instead,
     * where vanilla's doStitch was: before its packer reads a single frame, and so before both the buffer it fills
     * there and every upload after it, which its upload hook copies into that buffer. HC is FastCraft's own class,
     * never obfuscated, so its name is not remapped. Without FastCraft this finds no call, and under FastCraft the two
     * above find none: in any game the count and the build each run once (FastCraftStitchIsWiredTest).
     */
    @Inject(
        require = 0,
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            remap = false,
            target = "Lfastcraft/HC;g(Lnet/minecraft/client/renderer/texture/Stitcher;Lnet/minecraft/client/renderer/texture/TextureMap;)V"))
    private void trmt$countStitchedSpritesUnderFastCraft(IResourceManager manager, CallbackInfo callback) {
        trmt$count();
    }

    @Inject(
        require = 0,
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            remap = false,
            target = "Lfastcraft/HC;g(Lnet/minecraft/client/renderer/texture/Stitcher;Lnet/minecraft/client/renderer/texture/TextureMap;)V"))
    private void trmt$buildWearTexturesUnderFastCraft(IResourceManager manager, CallbackInfo callback) {
        trmt$build(manager);
    }

    private void trmt$count() {
        TextureMap map = (TextureMap) (Object) this;
        if (map.getTextureType() != 0) return;
        WearTextures
            .noteStitched(mapRegisteredSprites == null ? Collections.emptyMap() : mapRegisteredSprites, mipmapLevels);
    }

    private void trmt$build(IResourceManager manager) {
        TextureMap map = (TextureMap) (Object) this;
        // Blocks only. This same method assembles the item atlas afterwards, and running there
        // would rebuild every wear texture with no block pixels left to read - which is exactly
        // what happened: the blocks pass got them all right and the items pass six seconds later
        // replaced the lot with guesses.
        if (map.getTextureType() != 0) return;
        WearTextures.buildFromLoadedSprites(map, manager, mipmapLevels);
    }

    /**
     * Once the block atlas is stitched and uploaded, every wear picture that shares another's pixels takes that
     * picture's place (0.9.229, public issue #3). At the end of the method, after vanilla has given every sprite that
     * was not stitched missingno's place.
     */
    @Inject(require = 0, method = "loadTextureAtlas", at = @At("RETURN"))
    private void trmt$shareTwinPlaces(IResourceManager manager, CallbackInfo callback) {
        TextureMap map = (TextureMap) (Object) this;
        if (map.getTextureType() != 0) return;
        WearTextures.afterStitch();
    }
}
