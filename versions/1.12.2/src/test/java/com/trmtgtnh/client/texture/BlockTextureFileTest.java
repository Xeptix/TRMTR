package com.trmtgtnh.client.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The file a registered icon name is read from, which has to be the file the block atlas loads for it.
 *
 * <p>
 * Kept apart from AtlasPlanTest because it needs Minecraft's ResourceLocation. A face read from the wrong file
 * fails silently in game: it is priced from one texture and built from another, and nothing says so but a
 * stitch that runs past its plan.
 */
class BlockTextureFileTest {

    @Test
    @DisplayName("a domain registered in capitals is read from the lower-case domain the atlas loads")
    void aCapitalisedDomainIsLowered() {
        assertEquals(
            "mymod:textures/blocks/turf_bottom.png",
            WearPatterns.blockTextureFile("MyMod:turf_bottom")
                .toString());
    }

    @Test
    @DisplayName("a name with no domain, or a colon too early to end one, is read as vanilla's")
    void aNameWithoutADomainIsVanillas() {
        assertEquals(
            "minecraft:textures/blocks/dirt.png",
            WearPatterns.blockTextureFile("dirt")
                .toString());
        assertEquals(
            "minecraft:textures/blocks/x.png",
            WearPatterns.blockTextureFile(":x")
                .toString());
        assertEquals(
            "minecraft:textures/blocks/x.png",
            WearPatterns.blockTextureFile("a:x")
                .toString());
    }

    @Test
    @DisplayName("a 1.12.2 sprite name carries its own folder and is not given another")
    void aNameWithItsFolderIsTakenAsItIs() {
        assertEquals(
            "minecraft:textures/blocks/dirt.png",
            WearPatterns.blockTextureFile("minecraft:blocks/dirt")
                .toString());
        assertEquals(
            "mymod:textures/blocks/turf/top.png",
            WearPatterns.blockTextureFile("mymod:blocks/turf/top")
                .toString());
    }
}
