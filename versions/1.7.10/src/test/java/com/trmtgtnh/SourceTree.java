package com.trmtgtnh;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;

/**
 * Where the source is, for the tests that read it rather than run it.
 *
 * <p>
 * A handful of the promises this mod makes are about the shape of a file rather than the value of a
 * method - which classes may name the game, and which line of a pose may overwrite which other line.
 * None of that can be asked of a loaded class, because the thing being guarded against compiles
 * perfectly; it is only visible in the text. So those tests read the tree, and this is the one place
 * that knows how to find it.
 *
 * <p>
 * Gradle sets the working directory to the project and an IDE often does not, so it walks upward and
 * insists on an answer. A wrong answer would let one of those tests pass by finding nothing rather
 * than fail by finding something, which is the one way a fence like that fails silently.
 */
public final class SourceTree {

    private SourceTree() {}

    /** {@code src/main/java}, whatever the test happens to be run from. */
    public static File mainJava() {
        File here = new File(System.getProperty("user.dir"));
        for (File at = here; at != null; at = at.getParentFile()) {
            File candidate = new File(at, "src/main/java");
            if (candidate.isDirectory()) return candidate;
        }
        throw new IllegalStateException(
            "Could not find src/main/java from " + here.getAbsolutePath()
                + ". This test reads source rather than classes, so it needs the tree.");
    }

    /** Every line of one source file, named by its path under the source root. */
    public static List<String> lines(String relative) throws IOException {
        File file = new File(mainJava(), relative);
        if (!file.isFile()) {
            throw new IllegalStateException(
                relative + " is not there. A test reads it by name, so moving or renaming it is a "
                    + "decision that has to be made in that test as well.");
        }
        return Files.readAllLines(file.toPath(), Charset.forName("UTF-8"));
    }
}
