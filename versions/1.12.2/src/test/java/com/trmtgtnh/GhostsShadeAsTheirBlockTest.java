package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * A whole ghost shades the corners beside it as the block it replaced did, and a hollowed one does not.
 *
 * <p>
 * The 1.7.10 edition's {@code renderAsNormalBlock}, which is {@code !sunken} there - and its sunken variant is the
 * hollowed one, chosen once the ground has sunk or when the block covered is short. This version's ghost answers
 * from its state alone that it shades nothing, so until 0.9.219 every ghost did, and the first yard's close-ups
 * showed sunk earth beside a worn square that had not sunk drawn evenly lit here and shaded on 1.7.10. Both
 * renderers' readings are corrected per square now - Forge's light pipeline, and vanilla's, which OptiFine
 * draws through - and this holds the calls, not the names.
 */
class GhostsShadeAsTheirBlockTest {

    @Test
    void both_renderers_are_corrected_and_the_mixins_are_listed() throws IOException {
        java.io.File json = new java.io.File(
            SourceTree.mainJava()
                .getParentFile(),
            "resources/mixins.trmtgtnh.json");
        String mixins = new String(
            java.nio.file.Files.readAllBytes(json.toPath()),
            java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\s+", "");
        int client = mixins.indexOf("\"client\":[");
        assertTrue(client >= 0, "no client mixins at all");
        String clientList = mixins.substring(client, mixins.indexOf(']', client));
        assertTrue(
            clientList.contains("\"MixinGhostShadeForge\"") && clientList.contains("\"MixinGhostShadeVanilla\""),
            "a shade mixin is not on the client list in mixins.trmtgtnh.json, so it never applies");

        String forge = squeezed(source("com/trmtgtnh/mixin/MixinGhostShadeForge.java"));
        assertTrue(
            forge.contains("@Inject(method=\"updateLightMatrix\",at=@At(\"TAIL\"))") && forge.contains(
                "if(world.getBlockState(pos).getBlock()instanceofBlockGhost&&BlockGhost.wholeAt(world,pos)){ao[x][y][z]=BlockGhost.WHOLE_SHADE;"),
            "Forge's light pipeline is not told that a whole ghost shades");

        String vanilla = squeezed(source("com/trmtgtnh/mixin/MixinGhostShadeVanilla.java"));
        assertTrue(
            vanilla.contains(
                "target=\"Lnet/minecraft/world/IBlockAccess;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;\"")
                && vanilla
                    .contains("target=\"Lnet/minecraft/block/state/IBlockState;getAmbientOcclusionLightValue()F\""),
            "vanilla's smooth lighting is not wrapped where it fetches a state and reads its shade");
        assertTrue(
            vanilla.contains(
                "if(state==trmt$fetched&&state.getBlock()instanceofBlockGhost&&BlockGhost.wholeAt(trmt$world,newBlockPos(trmt$x,trmt$y,trmt$z))){BlockGhost.shadeSeen(\"vanilla'ssmoothlighting\");returnBlockGhost.WHOLE_SHADE;}"),
            "vanilla's smooth lighting, which OptiFine draws through, is not told that a whole ghost shades");
    }

    @Test
    void whole_is_the_other_edition_s_rule() throws IOException {
        String ghost = squeezed(source("com/trmtgtnh/block/BlockGhost.java"));
        assertTrue(
            ghost.contains("publicstaticfinalfloatWHOLE_SHADE=0.2F;"),
            "a whole ghost's shade is not vanilla's 0.2");
        String whole = ghost.substring(ghost.indexOf("publicstaticbooleanwholeAt(IBlockAccessworld,BlockPospos){"));
        whole = whole.substring(0, whole.indexOf("}}") + 2);
        assertTrue(
            whole.contains("if(sunkAt(world,pos))returnfalse;"),
            "a sunken square shades as though it were whole");
        assertTrue(
            whole.contains("if(com.trmtgtnh.surface.SurfaceShape.of(covered.getBlock()).isPartial())returnfalse;"),
            "a square standing in for a slab or a stair shades as though it were whole");
        assertTrue(
            whole.contains("returncovered.getBoundingBox(world,pos).maxY>=0.999D;"),
            "a square standing in for a path or farmland shades as though it were whole");
    }

    private static String source(String relative) throws IOException {
        return String.join("\n", SourceTree.lines(relative));
    }

    private static String squeezed(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ")
            .replaceAll("//[^\\n]*", " ")
            .replaceAll("\\s+", "");
    }
}
