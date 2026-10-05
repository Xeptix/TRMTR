package com.trmtgtnh.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.trmtgtnh.SourceTree;

/**
 * Only {@link Dimensions} may ask the settings whether a dimension is allowed.
 *
 * <p>
 * <strong>This test exists because the port nearly got it wrong, silently.</strong> Both older
 * editions identify a dimension by an int, and they ask that question for two unrelated reasons:
 * once to look a setting up, and once to key a map or a chunk by the level it belongs to. In the
 * source the two read identically - {@code world.provider.getDimension()} either way - so a carry
 * script translating the second shape will happily translate the first along with it.
 *
 * <p>
 * The engine's carry did exactly that. Thirteen of its fourteen uses want a key, and the right
 * answer there is the erosion store's own index; the fourteenth wanted a setting, and the store's
 * index is a session-local counter handed out in load order. Feeding it to
 * {@code dimensionAllowed} compiled, ran, and would have tested the wrong dimension - index zero
 * passing as the overworld whatever level happened to load first, so somebody who had restricted
 * the mod to the overworld would get it in the nether instead.
 *
 * <p>
 * Nothing about that would have shown up in a build, a test or a log. So the rule is made
 * structural: the numeric lookup has exactly one caller, the class whose whole job is to decide what
 * a number means here, and that class honours the vanilla three by name and says in the log what it
 * cannot honour. Anything else that wants to know asks {@code Dimensions.allowed(level)}.
 */
class DimensionsIsTheOnlyAskerTest {

    /** The lookup itself, and the one class allowed to reach it. */
    private static final String DECLARED_IN = "com/trmtgtnh/config/TrmtConfig.java";

    private static final String ASKED_BY = "com/trmtgtnh/erosion/Dimensions.java";

    @Test
    void nothing_but_Dimensions_asks_the_settings_about_a_numeric_dimension() throws IOException {
        File root = SourceTree.mainJava();
        List<String> found = new ArrayList<String>();
        look(root, root, "dimensionAllowed", found);
        Collections.sort(found);

        List<String> allowed = new ArrayList<String>();
        allowed.add(ASKED_BY);
        allowed.add(DECLARED_IN);
        Collections.sort(allowed);

        assertEquals(
            allowed,
            found,
            "TrmtConfig.dimensionAllowed takes a dimension *id*, and the only thing that knows what "
                + "an id means in this version is Dimensions. A new caller here is almost certainly "
                + "a carried call site that was handed the erosion store's index instead - which "
                + "compiles, runs, and tests the wrong dimension. Call Dimensions.allowed(level).");
    }

    private static void look(File root, File at, String needle, List<String> into) throws IOException {
        File[] children = at.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                look(root, child, needle, into);
                continue;
            }
            if (!child.getName()
                .endsWith(".java")) continue;
            for (String line : Files.readAllLines(child.toPath(), Charset.forName("UTF-8"))) {
                if (line.contains(needle)) {
                    into.add(
                        root.toPath()
                            .relativize(child.toPath())
                            .toString()
                            .replace('\\', '/'));
                    break;
                }
            }
        }
    }
}
