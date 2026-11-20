package com.trmtgtnh.client.render;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Hands Canvas the block atlas's sprite finder of the stitch just uploaded (1.0.0, canvas-120).
 *
 * <p>
 * Canvas for 1.20.1 (canvas-fabric 20.0.2625, FREX 20.0.341) finds the sprite of every quad it is handed copied in, a
 * block model's as much as this mod's ({@code BaseQuadEmitter.fromVanilla}, then {@code normalizeSprite}), from the
 * middle of the quad's coordinates through the atlas's sprite finder. Its {@code TextureMaterialState} fetches that
 * finder the first time it draws with the atlas and keeps it, while Fabric API drops the atlas's finder at every upload
 * and builds the next from the new stitch. A stitch that moves sprites therefore leaves Canvas looking quads up in the
 * places of a stitch that is gone: it takes another sprite's index and draws the quad from that sprite's place, in the
 * right shape and with the wrong pixels. This mod stitches the atlas again on the first client tick with its full set
 * of pictures, so from 0.9.229 that happened at every start under Canvas, and the 1.0.0 final pass found the worn yards
 * drawn black and from other blocks' pictures.
 *
 * <p>
 * So, as each block atlas upload ends, the state Canvas keeps for it is given the finder of this stitch. A state Canvas
 * has not yet filled is left alone: it fetches the new finder itself when it first draws. Asked by name and by
 * reflection, because Canvas is not on this build's classpath and a Canvas that renames these costs this, not the game.
 * Everything that reaches the game is in {@link #refresh}, and the step that decides is {@link #replace}, plain Java,
 * so a test can hold it without a game.
 */
public final class CanvasKeptFinder {

    /** Canvas's own class, which keeps one state per texture by its location in {@code MAP}. */
    static final String STATE = "grondag.canvas.material.property.TextureMaterialState";

    /** FREX's {@code SpriteFinder.get(TextureAtlas)}, the call Canvas makes when it first fills a state. */
    static final String FINDER = "io.vram.frex.api.texture.SpriteFinder";

    private static volatile boolean said;

    private static volatile boolean failed;

    private CanvasKeptFinder() {}

    /**
     * Gives the state Canvas keeps for this atlas the finder FREX now builds for it, where Canvas has one; nothing where
     * Canvas is not installed, or has not yet drawn with the atlas. Called at the end of the block atlas's upload.
     *
     * @param location the atlas's location, the key Canvas keeps its state under
     * @param atlas    the atlas just uploaded
     * @param type     the atlas's class, as FREX's {@code get} is declared to take it
     */
    public static void refresh(Object location, Object atlas, Class<?> type) {
        if (failed) return;
        Class<?> state;
        try {
            state = Class.forName(STATE);
        } catch (ClassNotFoundException noCanvas) {
            return;
        } catch (LinkageError noCanvas) {
            return;
        }
        try {
            Field map = state.getDeclaredField("MAP");
            map.setAccessible(true);
            Object kept = ((Map<?, ?>) map.get(null)).get(location);
            if (kept == null) return;
            Method get = Class.forName(FINDER).getMethod("get", type);
            Object fresh = get.invoke(null, atlas);
            if (replace(kept, fresh) && !said) {
                said = true;
                com.trmtgtnh.Trmt.LOG.info(
                    "Canvas still held the block atlas's sprite finder from an earlier stitch; it now has this stitch's, so it finds each quad's sprite where this stitch put it (said once).");
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError renamed) {
            failed = true;
            com.trmtgtnh.Trmt.LOG.warn(
                "Could not hand Canvas the block atlas's new sprite finder, so after a stitch that moves sprites it may draw blocks from another sprite's place; this Canvas has renamed what this looks for.",
                renamed);
        }
    }

    /**
     * Sets a kept state's {@code spriteFinder} to {@code fresh} where the state has been filled, its {@code texture}
     * set, and holds another finder. True where it changed one.
     */
    static boolean replace(Object kept, Object fresh) throws ReflectiveOperationException {
        if (fresh == null) return false;
        Class<?> type = kept.getClass();
        Field texture = type.getDeclaredField("texture");
        texture.setAccessible(true);
        if (texture.get(kept) == null) return false;
        Field finder = type.getDeclaredField("spriteFinder");
        finder.setAccessible(true);
        if (finder.get(kept) == fresh) return false;
        finder.set(kept, fresh);
        return true;
    }
}
