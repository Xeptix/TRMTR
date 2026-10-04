package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Every placeholder in this edition says what it is waiting for, and they are counted.
 *
 * <p>
 * This edition is carried across from the 1.7.10 one a layer at a time, and a layer often reaches for
 * a class that belongs to a later one. Where that class is small and its reach is a single method,
 * the answer is a placeholder in the real class's own package: the method that is called exists and
 * does nothing, and the file that calls it stays identical to its twin in the other edition. That is
 * a good trade while a port is under way and a bad one in anything released, because an empty method
 * where a real one should be is silent - nothing fails, something simply does not happen.
 *
 * <p>
 * So each placeholder carries the marker {@code PORT-STUB} and a {@code waiting for:} line naming the
 * milestone that replaces it, and this lists them all on every run. It does not fail because they
 * exist; during the port they are meant to. It fails when one is anonymous - marked a stub without
 * saying what will replace it - because an anonymous placeholder is the one that survives.
 *
 * <p>
 * Before this edition is released, the list printed here has to be empty.
 */
class PortStubsTest {

    private static final Pattern MARKER = Pattern.compile("PORT-STUB\\s*-\\s*waiting for:\\s*([^\\n*]+)");

    @Test
    void every_placeholder_says_what_it_is_waiting_for() throws IOException {
        List<String> stubs = new ArrayList<String>();
        List<String> anonymous = new ArrayList<String>();
        walk(SourceTree.mainJava(), SourceTree.mainJava(), stubs, anonymous);

        if (!anonymous.isEmpty()) {
            StringBuilder message = new StringBuilder(
                "These are marked PORT-STUB without saying what replaces them. Add a 'waiting for:' line "
                    + "naming the milestone, so the placeholder cannot outlive the port unnoticed:\n");
            for (String one : anonymous) {
                message.append("  ")
                    .append(one)
                    .append('\n');
            }
            fail(message.toString());
        }

        System.out.println("Placeholders still standing in for real classes (" + stubs.size() + "):");
        for (String one : stubs) {
            System.out.println("  " + one);
        }
    }

    private static void walk(File root, File at, List<String> stubs, List<String> anonymous) throws IOException {
        File[] children = at.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                walk(root, child, stubs, anonymous);
                continue;
            }
            if (!child.getName()
                .endsWith(".java")) continue;
            String text = new String(Files.readAllBytes(child.toPath()), Charset.forName("UTF-8"));
            if (!text.contains("PORT-STUB")) continue;
            String relative = root.toURI()
                .relativize(child.toURI())
                .getPath();
            Matcher waiting = MARKER.matcher(text);
            if (waiting.find()) {
                stubs.add(
                    relative + "  -  "
                        + waiting.group(1)
                            .trim());
            } else {
                anonymous.add(relative);
            }
        }
    }
}
