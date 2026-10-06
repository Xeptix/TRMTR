package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * A log line may not be handed a collection to stringify.
 *
 * <p>
 * Log4j formats a collection parameter by walking it, and nothing bounds how long the result is. On
 * 2026-10-06 that stopped this edition starting at all: {@code WearTextures.finishPass} passed its
 * set of stand-in surfaces straight to {@code LOG.info}, and on a stitch composing forty-four
 * thousand pictures from a hundred and fifty-six surfaces the formatter threw
 * {@code OutOfMemoryError} with a StringBuilder past two gigabytes - inside
 * {@code TextureAtlas.reload}, which took the whole resource reload down with it and left Indigo
 * tessellating blocks against a null model.
 *
 * <p>
 * The 1.7.10 and 1.12.2 editions carry the identical line and survive it only because their packs are
 * an order of magnitude smaller. That is not a property anybody chose, and it is not one that holds
 * when somebody installs a large texture pack, so all three were changed together: the count first,
 * then a bounded sample off a snapshot.
 *
 * <p>
 * <strong>What this reads, and what it cannot.</strong> It finds the fields of each class that are
 * declared as a collection, a map or an array, and then refuses any {@code LOG} argument that is
 * exactly one of them - {@code tally.improvised}, {@code names}, {@code this.rows}. It cannot see a
 * method that returns a collection, or one built inline, so it is a fence against the shape of the
 * fault that happened rather than a proof. A sampled line reads {@code join(list.subList(0, n))} and
 * passes, which is the point.
 */
class LogLinesCannotGrowWithoutLimitTest {

    /** A field declaration this test treats as unbounded when printed. */
    private static final Pattern FIELD = Pattern.compile(
        "(?:private|protected|public|static|final|transient|volatile|\\s)+"
            + "(?:java\\.util\\.)?(List|Set|Map|Collection|Queue|Deque|ArrayList|HashSet|TreeSet|"
            + "LinkedHashSet|HashMap|TreeMap|LinkedHashMap|ArrayDeque)\\s*<[^;=]*>\\s+(\\w+)\\s*[;=]");

    /** An array field, which log4j walks the same way. */
    private static final Pattern ARRAY = Pattern.compile(
        "(?:private|protected|public|static|final|transient|volatile|\\s)+"
            + "(\\w[\\w.<>]*)\\s*\\[\\s*\\]\\s+(\\w+)\\s*[;=]");

    private static final Pattern CALL = Pattern.compile("(?:Trmt\\.)?LOG\\s*\\.\\s*(?:info|warn|error|debug|trace)\\s*\\(");

    @Test
    void no_log_line_is_handed_a_whole_collection() throws IOException {
        List<String> wrong = new ArrayList<String>();
        int read = 0;

        for (File file : javaFiles(new File(SourceTree.repoRoot(), "common/src/main/java"))) {
            String text = new String(Files.readAllBytes(file.toPath()), Charset.forName("UTF-8"));
            Map<String, String> unbounded = fieldsOf(text);
            if (unbounded.isEmpty()) continue;

            Matcher call = CALL.matcher(text);
            while (call.find()) {
                read++;
                String arguments = argumentsAt(text, call.end() - 1);
                if (arguments == null) continue;
                for (String argument : split(arguments)) {
                    String bare = argument.trim();
                    // Only a plain name or one field access. Anything with a call in it has done
                    // something to the collection, and doing something to it is the whole fix.
                    if (!bare.matches("(?:this\\.)?(?:\\w+\\.)?\\w+")) continue;
                    String last = bare.substring(bare.lastIndexOf('.') + 1);
                    if (unbounded.containsKey(last)) {
                        wrong.add(
                            relative(file) + ":  " + last + " is a " + unbounded.get(last)
                                + " and is printed whole");
                    }
                }
            }
        }

        assertTrue(
            read > 40,
            "Only " + read + " log calls were read, which is too few to be the whole mod - this test "
                + "has stopped finding them rather than stopped finding faults");

        if (!wrong.isEmpty()) {
            fail(
                "These log calls hand a collection to log4j, which walks it and builds a string "
                    + "nothing bounds. One of these stopped the game starting:\n  "
                    + String.join("\n  ", wrong));
        }
    }

    private static Map<String, String> fieldsOf(String text) {
        Map<String, String> found = new HashMap<String, String>();
        Matcher field = FIELD.matcher(text);
        while (field.find()) {
            found.put(field.group(2), field.group(1));
        }
        Matcher array = ARRAY.matcher(text);
        while (array.find()) {
            // Not a cast, a new, or a local in a for header - those do not look like declarations
            // with a modifier in front, which the pattern already requires.
            found.put(array.group(2), array.group(1) + "[]");
        }
        return found;
    }

    /** The text between a call's brackets, or null when they do not balance inside this file. */
    private static String argumentsAt(String text, int open) {
        int depth = 0;
        boolean inString = false;
        boolean inChar = false;
        for (int at = open; at < text.length(); at++) {
            char here = text.charAt(at);
            if (inString) {
                if (here == '\\') at++;
                else if (here == '"') inString = false;
                continue;
            }
            if (inChar) {
                if (here == '\\') at++;
                else if (here == '\'') inChar = false;
                continue;
            }
            if (here == '"') inString = true;
            else if (here == '\'') inChar = true;
            else if (here == '(') depth++;
            else if (here == ')') {
                depth--;
                if (depth == 0) return text.substring(open + 1, at);
            }
        }
        return null;
    }

    /** One argument list, split on the commas between arguments and nowhere else. */
    private static List<String> split(String arguments) {
        List<String> parts = new ArrayList<String>();
        int depth = 0;
        boolean inString = false;
        boolean inChar = false;
        int from = 0;
        for (int at = 0; at < arguments.length(); at++) {
            char here = arguments.charAt(at);
            if (inString) {
                if (here == '\\') at++;
                else if (here == '"') inString = false;
                continue;
            }
            if (inChar) {
                if (here == '\\') at++;
                else if (here == '\'') inChar = false;
                continue;
            }
            if (here == '"') inString = true;
            else if (here == '\'') inChar = true;
            else if (here == '(' || here == '[' || here == '{') depth++;
            else if (here == ')' || here == ']' || here == '}') depth--;
            else if (here == ',' && depth == 0) {
                parts.add(arguments.substring(from, at));
                from = at + 1;
            }
        }
        parts.add(arguments.substring(from));
        return parts;
    }

    private static List<File> javaFiles(File at) {
        List<File> found = new ArrayList<File>();
        gather(at, found);
        return found;
    }

    private static void gather(File at, List<File> found) {
        File[] children = at.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) gather(child, found);
            else if (child.getName()
                .endsWith(".java")) found.add(child);
        }
    }

    private static String relative(File file) {
        String root = SourceTree.repoRoot()
            .getAbsolutePath();
        String path = file.getAbsolutePath();
        return path.startsWith(root) ? path.substring(root.length() + 1)
            .replace('\\', '/') : path;
    }
}
