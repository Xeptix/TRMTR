package com.trmtgtnh.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.trmtgtnh.SourceTree;

/**
 * The model overrides that draw a tamper as the metal it is made of have to agree with the code that
 * numbers the metals.
 *
 * <p>
 * <strong>Two halves of one decision, in two languages, and nothing at compile time connects
 * them.</strong> {@code TamperModels} answers a number for a stack - the index of its grade among
 * the drawn ones, sorted - and {@code tamper.json} and {@code chunk_tamper.json} each list a picture
 * against each of those numbers. If the two disagree, every tamper of every metal past the
 * disagreement is drawn as the wrong metal. Nothing is logged, nothing throws, and nobody notices
 * until they look at an iron tamper and see brass.
 *
 * <p>
 * So this reads both model files and the drawn set, and insists on the whole list in order. A grade
 * added to the set and not to the files, or added to the files in the wrong place, fails here.
 * {@code tools/mk_tamper_overrides.py} writes the files and is the fix when it does.
 *
 * <p>
 * Read as text rather than loaded: the model files are resources that only the game parses, and the
 * numbering is a sorted list this test can build for itself. Neither half needs Minecraft, which is
 * what lets this run in the same breath as the portable core's own tests.
 */
class TamperModelOverridesTest {

    private static final String[] TOOLS = { "tamper", "chunk_tamper" };

    /** The same list {@code TamperModels.order()} builds, built the same way and from the same set. */
    private static List<String> expected() {
        List<String> keys = new ArrayList<String>(TamperArt.drawn());
        Collections.sort(keys);
        return keys;
    }

    private static File models() {
        // src/main/java -> src/main -> resources/assets/...
        return new File(
            SourceTree.mainJava()
                .getParentFile(),
            "resources/assets/trmtgtnh/models/item");
    }

    @Test
    void every_drawn_grade_has_an_override_at_the_number_the_code_will_answer() throws IOException {
        List<String> wanted = expected();
        assertTrue(wanted.size() > 20, "the drawn set emptied itself, which would switch this test off");

        for (String tool : TOOLS) {
            File file = new File(models(), tool + ".json");
            assertTrue(file.isFile(), file.getAbsolutePath() + " is not there, so nothing draws a graded " + tool);
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

            // Each override, in the order the file lists them: the number, then the model it names.
            Pattern each = Pattern.compile(
                "\\{\\s*\"predicate\"\\s*:\\s*\\{\\s*\"trmtgtnh:grade\"\\s*:\\s*([0-9]+)\\s*\\}\\s*,"
                    + "\\s*\"model\"\\s*:\\s*\"([^\"]+)\"\\s*\\}");
            Matcher found = each.matcher(text);
            List<String> numbers = new ArrayList<String>();
            List<String> named = new ArrayList<String>();
            while (found.find()) {
                numbers.add(found.group(1));
                named.add(found.group(2));
            }

            if (named.size() != wanted.size()) {
                fail(
                    tool + ".json lists " + named.size() + " override(s) and there are " + wanted.size()
                        + " drawn grade(s). Run tools/mk_tamper_overrides.py.");
            }

            for (int at = 0; at < wanted.size(); at++) {
                assertEquals(
                    String.valueOf(at),
                    numbers.get(at),
                    tool + ".json's override number " + at + " is out of order, so every grade after it draws "
                        + "the wrong metal. Run tools/mk_tamper_overrides.py.");
                assertEquals(
                    "trmtgtnh:item/" + tool + "_" + wanted.get(at),
                    named.get(at),
                    tool + ".json puts the wrong picture at number " + at + " - the code answers " + at
                        + " for the grade '" + wanted.get(at) + "'. Run tools/mk_tamper_overrides.py.");
            }
        }
    }

    @Test
    void every_override_names_a_model_file_that_exists() {
        // A name with no file behind it is the missing-model black cube and a line in the log for
        // every stack of it, which is why both older editions declare the drawn set rather than the
        // configured one. The same reasoning, one layer further out.
        List<String> gone = new ArrayList<String>();
        for (String tool : TOOLS) {
            for (String key : expected()) {
                File named = new File(models(), tool + "_" + key + ".json");
                if (!named.isFile()) gone.add(named.getName());
            }
        }
        if (!gone.isEmpty()) {
            fail(
                "These model files are named by an override and are not there, so those tampers would draw "
                    + "as the missing-model cube: " + gone);
        }
    }
}
