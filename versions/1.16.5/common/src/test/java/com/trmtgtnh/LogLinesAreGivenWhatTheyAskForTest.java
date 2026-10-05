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
 * Every log line in this mod gets as many values as it leaves spaces for.
 *
 * <p>
 * This mod's log lines are long and they are the thing somebody reads when a feature has not
 * happened, so a line that is wrong about itself is worse than no line. Log4j does not complain about
 * a mismatch: given too few values it prints the {@code {}} as written, and given too many it drops
 * the extras. Either way it prints, the run carries on, and the only evidence is in a log nobody
 * reads until something else is already wrong.
 *
 * <p>
 * <strong>This test exists because that happened.</strong> A line reporting where the wear textures'
 * faces came from was adapted for this edition - three of its four counts cannot happen here - and
 * the arguments were reduced without the message being rewritten. What shipped said "Read 47 block
 * faces out of the atlas for the wear textures and {} from their files", which is two placeholders
 * unfilled and, worse, the wrong source named for the one number it did print: the 47 were read from
 * files, not from the atlas. Nothing failed. Nothing could.
 *
 * <p>
 * It reads the source rather than running anything, because what is being checked is a relationship
 * between two parts of one call that compiles perfectly either way.
 */
class LogLinesAreGivenWhatTheyAskForTest {

    /** The modules whose source is checked: all of them, since any of them can log. */
    private static final String[] MODULES = { "common", "forge", "fabric" };

    /** A log call on this mod's own logger. Other loggers belong to whatever they belong to. */
    private static final Pattern CALL = Pattern
        .compile("\\bLOG\\s*\\.\\s*(info|warn|error|debug|trace)\\s*\\(");

    /** A string constant, which is where this mod keeps its longer lines. */
    private static final Pattern CONSTANT = Pattern
        .compile("static\\s+final\\s+String\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(\"(?:[^\"\\\\]|\\\\.)*\"(?:\\s*\\+\\s*\"(?:[^\"\\\\]|\\\\.)*\")*)\\s*;");

    @Test
    void every_log_line_is_given_a_value_for_every_space_it_leaves() throws IOException {
        List<String> wrong = new ArrayList<String>();
        int checked = 0;
        for (File source : sources()) {
            String text = new String(Files.readAllBytes(source.toPath()), Charset.forName("UTF-8"));
            Map<String, String> constants = constantsIn(text);
            Matcher call = CALL.matcher(text);
            while (call.find()) {
                String arguments = inside(text, call.end() - 1);
                if (arguments == null) continue;
                List<String> parts = topLevelCommas(arguments);
                if (parts.isEmpty()) continue;
                String message = literal(parts.get(0), constants);
                // A message built at the call site - concatenated from a variable, or chosen by a
                // ternary - cannot be read here, and guessing would make this test lie in the one
                // direction that matters.
                if (message == null) continue;
                checked++;
                int spaces = placeholders(message);
                List<String> values = valuesAfter(parts);
                int given = values.size();
                if (spaces == given) continue;
                // Log4j logs a trailing throwable that no {} accounts for as the exception, with its
                // stack trace, and this mod relies on that in every "could not" line it writes. So one
                // unaccounted argument at the end is right, as long as it could be a caught thing.
                if (given == spaces + 1 && looksLikeAThrowable(values.get(given - 1))) continue;
                wrong.add(
                    relative(source) + ":" + lineOf(text, call.start()) + "  leaves " + spaces
                        + " space(s) and is given " + given + ": " + shortly(message));
            }
        }
        if (!wrong.isEmpty()) {
            StringBuilder message = new StringBuilder(
                "These log calls do not give a value for every {} in their message, or give values "
                    + "with nowhere to go. Log4j prints either without complaint:\n");
            for (String one : wrong) {
                message.append("  ")
                    .append(one)
                    .append('\n');
            }
            fail(message.toString());
        }
        assertTrue(
            checked > 40,
            "Only " + checked + " log calls were read, which is too few to be the whole mod - this "
                + "test has stopped finding them rather than stopped finding faults");
    }

    // ------------------------------------------------------------------
    // Reading the call apart
    // ------------------------------------------------------------------

    /** Every source file in every module, which is where a log call can be. */
    private static List<File> sources() {
        List<File> found = new ArrayList<File>();
        for (String module : MODULES) {
            File root = new File(SourceTree.repoRoot(), module + "/src/main/java");
            if (root.isDirectory()) gather(root, found);
        }
        return found;
    }

    private static void gather(File at, List<File> into) {
        File[] children = at.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) gather(child, into);
            else if (child.getName()
                .endsWith(".java")) into.add(child);
        }
    }

    /** Every string constant in one file, by name, with its pieces joined as the compiler joins them. */
    private static Map<String, String> constantsIn(String text) {
        Map<String, String> out = new HashMap<String, String>();
        Matcher each = CONSTANT.matcher(text);
        while (each.find()) {
            out.put(each.group(1), join(each.group(2)));
        }
        return out;
    }

    /** The text between a bracket at {@code open} and its match, or null if it never closes. */
    private static String inside(String text, int open) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        boolean charLiteral = false;
        for (int at = open; at < text.length(); at++) {
            char here = text.charAt(at);
            if (quoted || charLiteral) {
                if (escaped) escaped = false;
                else if (here == '\\') escaped = true;
                else if (quoted && here == '"') quoted = false;
                else if (charLiteral && here == '\'') charLiteral = false;
                continue;
            }
            if (here == '"') quoted = true;
            else if (here == '\'') charLiteral = true;
            else if (here == '(') depth++;
            else if (here == ')') {
                depth--;
                if (depth == 0) return text.substring(open + 1, at);
            }
        }
        return null;
    }

    /**
     * One argument list, split on the commas between arguments.
     *
     * <p>
     * Depth is counted for brackets of all three kinds and quoting for both, so a comma inside a
     * call, an array, a lambda body or a string is not a separator. Angle brackets are not counted,
     * because a generic type with two parameters has never appeared in a log argument here - and if
     * one ever does, this test over-counts and fails, which is the safe direction.
     */
    private static List<String> topLevelCommas(String arguments) {
        List<String> parts = new ArrayList<String>();
        StringBuilder piece = new StringBuilder();
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        boolean charLiteral = false;
        for (int at = 0; at < arguments.length(); at++) {
            char here = arguments.charAt(at);
            if (quoted || charLiteral) {
                piece.append(here);
                if (escaped) escaped = false;
                else if (here == '\\') escaped = true;
                else if (quoted && here == '"') quoted = false;
                else if (charLiteral && here == '\'') charLiteral = false;
                continue;
            }
            if (here == '"') quoted = true;
            else if (here == '\'') charLiteral = true;
            else if (here == '(' || here == '[' || here == '{') depth++;
            else if (here == ')' || here == ']' || here == '}') depth--;
            else if (here == ',' && depth == 0) {
                parts.add(piece.toString()
                    .trim());
                piece.setLength(0);
                continue;
            }
            piece.append(here);
        }
        String last = piece.toString()
            .trim();
        if (!last.isEmpty() || !parts.isEmpty()) parts.add(last);
        return parts;
    }

    /** How an explicit varargs array is written, which is how a call with many values is written here. */
    private static final Pattern OBJECT_ARRAY = Pattern
        .compile("^new\\s+Object\\s*\\[\\s*\\]\\s*\\{(.*)\\}$", Pattern.DOTALL);

    /**
     * The values a call hands its message, however they were handed over.
     *
     * <p>
     * Log4j takes them as varargs, and a call with more than a couple of them is written here as the
     * array that varargs is - {@code new Object[] { a, b, c }} - because the formatter this project
     * uses puts each on its own line otherwise. To the compiler the two forms are the same call; to a
     * test reading the source the array is one argument, and a line with six values in an array would
     * look like a line given one. So the array is opened and its elements counted as what they are.
     */
    private static List<String> valuesAfter(List<String> parts) {
        List<String> values = new ArrayList<String>(parts.subList(1, parts.size()));
        if (values.size() != 1) return values;
        Matcher array = OBJECT_ARRAY.matcher(values.get(0)
            .trim());
        if (!array.find()) return values;
        return topLevelCommas(array.group(1));
    }

    /**
     * The message as a literal, whether it was written at the call or named there, or null when it
     * cannot be read from the source.
     */
    private static String literal(String first, Map<String, String> constants) {
        String written = first.trim();
        if (written.startsWith("\"")) {
            // Possibly several literals joined with +, which is how a long line is wrapped. Anything
            // else in the expression means it cannot be read here.
            String bare = written.replaceAll("\\s*\\+\\s*", "+");
            if (!bare.matches("(\"(?:[^\"\\\\]|\\\\.)*\")(\\+\"(?:[^\"\\\\]|\\\\.)*\")*")) return null;
            return join(written);
        }
        if (written.matches("[A-Za-z_][A-Za-z0-9_]*")) return constants.get(written);
        return null;
    }

    /** Several adjacent string literals as the one string the compiler makes of them. */
    private static String join(String expression) {
        StringBuilder out = new StringBuilder();
        Matcher each = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"")
            .matcher(expression);
        while (each.find()) {
            out.append(each.group(1));
        }
        return out.toString();
    }

    /** How many values a message leaves room for. {@code \{}} is log4j's way of writing one plainly. */
    private static int placeholders(String message) {
        int count = 0;
        for (int at = message.indexOf("{}"); at >= 0; at = message.indexOf("{}", at + 2)) {
            // In the source a literal backslash is written \\, which is what this sees.
            boolean escaped = at >= 2 && message.charAt(at - 1) == '\\' && message.charAt(at - 2) == '\\';
            if (!escaped) count++;
        }
        return count;
    }

    /**
     * Whether an argument is plausibly the caught thing rather than a value, for log4j's
     * message-and-throwable form.
     *
     * <p>
     * A bare name, which is what a caught variable is. This mod names them for what went wrong rather
     * than for their type - {@code unnamable}, {@code awkwardBlock} - so there is no suffix to match
     * on, and anything that is not a bare name is certainly a value.
     */
    private static boolean looksLikeAThrowable(String argument) {
        return argument.matches("[a-z][A-Za-z0-9_]*");
    }

    // ------------------------------------------------------------------
    // Saying where
    // ------------------------------------------------------------------

    private static int lineOf(String text, int at) {
        int line = 1;
        for (int i = 0; i < at && i < text.length(); i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    private static String relative(File source) {
        String root = SourceTree.repoRoot()
            .getAbsolutePath();
        String path = source.getAbsolutePath();
        return path.startsWith(root) ? path.substring(root.length() + 1)
            .replace('\\', '/') : path;
    }

    private static String shortly(String message) {
        String flat = message.replace('\n', ' ');
        return flat.length() <= 90 ? flat : flat.substring(0, 87) + "...";
    }
}
