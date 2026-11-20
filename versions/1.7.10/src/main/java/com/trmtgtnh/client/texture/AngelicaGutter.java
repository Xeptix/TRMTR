package com.trmtgtnh.client.texture;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * The one place this mod sets a sprite's Angelica gutter, through Angelica's per-sprite setter
 * (SpriteExtension.angelica$setGutterWidth(I)V, read from Angelica 2.2.43's class). Looked up by name once rather
 * than compiled against, so nothing of Angelica's is a build dependency (ShaderMaterialStaysOptionalTest) and a game
 * without it never resolves the interface; asked only where WearTextures found Angelica's gutter above nought. A
 * lookup that fails gives up for the session and leaves Angelica's own gutter in place. See {@link SmallGutter}.
 */
final class AngelicaGutter {

    private AngelicaGutter() {}

    private static volatile MethodHandle setter;

    private static volatile boolean failed;

    /** Sets the gutter, or answers false where Angelica's setter cannot be reached. */
    static boolean set(TextureAtlasSprite sprite, int gutter) {
        if (failed) return false;
        try {
            MethodHandle found = setter;
            if (found == null) {
                Class<?> extension = Class.forName("com.gtnewhorizons.angelica.rendering.celeritas.SpriteExtension");
                found = MethodHandles.publicLookup()
                    .findVirtual(extension, "angelica$setGutterWidth", MethodType.methodType(void.class, int.class))
                    .asType(MethodType.methodType(void.class, TextureAtlasSprite.class, int.class));
                setter = found;
            }
            found.invokeExact(sprite, gutter);
            return true;
        } catch (Throwable unreachable) {
            failed = true;
            com.trmtgtnh.Trmt.LOG.warn(
                "Could not give this mod's sprites a smaller Angelica gutter, so they keep Angelica's own",
                unreachable);
            return false;
        }
    }
}
