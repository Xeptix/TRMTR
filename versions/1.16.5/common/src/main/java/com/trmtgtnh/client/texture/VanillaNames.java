package com.trmtgtnh.client.texture;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * What a vanilla texture is called on this version, given what it was called on the older ones.
 *
 * <p>
 * <strong>This class exists so that {@link FaceRules} does not have to.</strong> FaceRules is one of
 * the twenty-eight files the portable core holds byte for byte across every edition, and it names
 * vanilla's textures - {@code grass_top}, {@code dirt}, {@code gravel} - because the arithmetic
 * around them is the same everywhere. 1.13 renamed a few of those when grass became a grass block,
 * and renamed the folder they all live in. Writing the new names into FaceRules would have made the
 * shared file different in one edition, which the drift check caught within the minute.
 *
 * <p>
 * So the names stay as they were and are translated here, at the one boundary where a name stops
 * being a word and becomes a path. Every other edition translates nothing, because for them this map
 * is empty.
 *
 * <p>
 * <strong>The folder moved too.</strong> {@code textures/blocks} became {@code textures/block} at the
 * same version, which is not a rename of any one texture but of where all of them are, and that is
 * applied by the two callers rather than here.
 *
 * <p>
 * Nothing about any of this is visible from a compile: every one of these is a string. The port built
 * and ran perfectly and composed nothing at all - "0 wear textures, 12800 had nothing readable to be
 * made from" - because every face it went looking for was under a path that stopped existing three
 * versions ago.
 */
public final class VanillaNames {

    /**
     * Only what actually changed. Everything else - dirt, sand, gravel, stone, cobblestone,
     * netherrack, end_stone, snow, ice - is called the same thing at this version and is deliberately
     * absent, so this map stays a list of differences rather than a second copy of the first list.
     */
    private static final Map<String, String> RENAMED;

    static {
        Map<String, String> moved = new HashMap<String, String>();
        moved.put("grass_top", "grass_block_top");
        moved.put("grass_side", "grass_block_side");
        moved.put("grass_side_overlay", "grass_block_side_overlay");
        RENAMED = Collections.unmodifiableMap(moved);
    }

    private VanillaNames() {}

    /** The name this version keeps that texture under, or the name itself where nothing changed. */
    public static String current(String name) {
        if (name == null) return null;
        String moved = RENAMED.get(name);
        return moved == null ? name : moved;
    }
}
