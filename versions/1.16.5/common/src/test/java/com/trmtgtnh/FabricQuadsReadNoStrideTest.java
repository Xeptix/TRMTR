package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

/**
 * Under OptiFabric the Fabric model hands its quads to the renderer one attribute at a time; everywhere else, copied
 * in whole.
 *
 * <p>
 * {@code fromVanilla} copies a whole quad at the stride the renderer fixed once, from
 * {@code DefaultVertexFormat.BLOCK} - which OptiFine grows while a shader pack is on. Under OptiFabric, Indigo fixed
 * the grown stride and was then handed the ghost's eight-int vertices once the pack was turned off mid-session, and
 * copied past their end: the 0.9.219 toggle pass crashed the game with {@code ArrayIndexOutOfBoundsException} in
 * {@code MutableQuadViewImpl.fromVanilla}.
 *
 * <p>
 * The first fix set every quad by attribute on every renderer, and Canvas drew the first yard smeared into streaks:
 * it does not read a quad set that way as it reads one copied in. Nothing but OptiFine moves that stride, and on
 * Fabric OptiFine comes only through OptiFabric - so the attribute path is taken there and nowhere else.
 */
class FabricQuadsReadNoStrideTest {

    private static final String MODEL = "fabric/src/main/java/com/trmtgtnh/fabric/GhostModelFabric.java";

    @Test
    void quads_go_by_attribute_under_optifabric_and_whole_everywhere_else() throws IOException {
        File at = new File(SourceTree.repoRoot(), MODEL);
        assertTrue(at.isFile(), at.getAbsolutePath() + " is not there");
        String code = new String(Files.readAllBytes(at.toPath()), Charset.forName("UTF-8"))
            .replaceAll("(?s)/\\*.*?\\*/", " ")
            .replaceAll("//[^\\n]*", " ")
            .replaceAll("\\s+", "");
        assertTrue(
            code.contains("privatestaticfinalbooleanBY_ATTRIBUTE=net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(\"optifabric\");"),
            "the attribute path is no longer chosen by OptiFabric's presence");
        assertTrue(
            code.contains("if(!BY_ATTRIBUTE){emitter.fromVanilla(quad.getVertices(),0,false);}else{emitVertices(emitter,quad.getVertices());}"),
            "the model no longer copies quads in whole except under OptiFabric, where it sets them by attribute");
        assertEquals(
            1,
            code.split("fromVanilla\\(", -1).length - 1,
            "fromVanilla is called somewhere the OptiFabric switch does not guard");
        String vertices = code.substring(code.indexOf("privatestaticvoidemitVertices(QuadEmitteremitter,int[]packed){"));
        for (String call : new String[] { "intstride=packed.length/4;", "intat=vertex*stride;", "emitter.pos(vertex,",
            "emitter.spriteColor(vertex,0,packed[at+3]);",
            "emitter.sprite(vertex,0,Float.intBitsToFloat(packed[at+4]),Float.intBitsToFloat(packed[at+5]));",
            "emitter.lightmap(vertex,packed[at+6]);" }) {
            assertTrue(vertices.contains(call), "the attribute path no longer sets " + call + " for each vertex");
        }
    }
}
