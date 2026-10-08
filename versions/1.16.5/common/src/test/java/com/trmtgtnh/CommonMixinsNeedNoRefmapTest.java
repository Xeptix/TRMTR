package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Every mixin in the common module's config needs no refmap.
 *
 * <p>
 * <strong>A Forge development run reads common's refmap as common's processor wrote it</strong>, in Fabric's
 * intermediary names. A production Forge jar has it remapped on the way out and a Fabric run of either kind
 * remaps it as it loads, so a common mixin that names a game method works in three of the four places it
 * runs - and in the fourth refuses to apply, which on a class loaded as early as {@code Block} is a game that
 * never starts. That is how the dev 1.16.5 Forge target died on 2026-10-07, the first time a common mixin
 * named one: {@code MixinSettledFaces} asked for {@code net/minecraft/class_2248}. The Forge module's build
 * had said as much since the first mixin was written, and every older common mixin injects with
 * {@code remap = false}; the three that did not were moved into both loader modules.
 *
 * <p>
 * So two things are held. The refmap the build wrote for common holds no mappings at all, which is the
 * fact itself. And each common mixin is read, and every annotation the processor would write an entry
 * for - an injector, an overwrite, an accessor - must say {@code remap = false} unless the whole mixin does,
 * which is the same fact said where it is fixed. A {@code @Shadow} field is not one: the processor writes
 * nothing for it, the loaders' remappers rename it in the class itself, and {@code MixinPackRepository}'s
 * has applied in all four places since the first build.
 */
class CommonMixinsNeedNoRefmapTest {

    /** The annotations the processor writes into a refmap unless told not to. */
    private static final Pattern MEMBER = Pattern.compile(
        "@(Inject|Redirect|ModifyArg|ModifyArgs|ModifyVariable|ModifyConstant|Overwrite|Accessor|Invoker)\\b");

    @Test
    void the_refmap_common_writes_maps_nothing() throws IOException {
        File refmap = new File(SourceTree.repoRoot(), "common/build/classes/java/main/trmtgtnh-common-refmap.json");
        assertTrue(refmap.isFile(), "no refmap at " + refmap + " - the build writes one with the classes");
        String mappings = new String(Files.readAllBytes(refmap.toPath()), StandardCharsets.UTF_8).replaceAll("\\s+", "");
        assertTrue(
            mappings.contains("\"mappings\":{}"),
            "common's refmap maps a member, and a Forge development run reads it in intermediary names: " + mappings);
    }

    @Test
    void every_common_mixin_names_its_members_without_a_refmap() throws IOException {
        File root = SourceTree.repoRoot();
        String config = new String(
            Files.readAllBytes(new File(root, "common/src/main/resources/trmtgtnh-common.mixins.json").toPath()),
            StandardCharsets.UTF_8);
        List<String> names = declared(config);
        assertFalse(names.isEmpty(), "found no mixins in common's config, which would switch this test off");

        for (String name : names) {
            File file = new File(root, "common/src/main/java/com/trmtgtnh/mixin/" + name + ".java");
            assertTrue(file.isFile(), name + " is in common's config and has no source here");
            String source = code(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            String mixin = arguments(source, source.indexOf("@Mixin"));
            if (mixin.replaceAll("\\s+", "")
                .contains("remap=false")) continue;
            Matcher member = MEMBER.matcher(source);
            while (member.find()) {
                String args = arguments(source, member.start());
                assertTrue(
                    args.replaceAll("\\s+", "")
                        .contains("remap=false"),
                    name + " looks up a member through common's refmap (@" + member.group(1)
                        + "), which a Forge development run cannot read - move it to both loader modules, or say "
                        + "remap = false if the name needs no remapping");
            }
        }
    }

    /** Every mixin a config declares, in all three of its lists. */
    private static List<String> declared(String config) {
        List<String> out = new ArrayList<String>();
        for (String list : new String[] { "\"mixins\"", "\"client\"", "\"server\"" }) {
            int at = config.indexOf(list);
            if (at < 0) continue;
            int open = config.indexOf('[', at);
            int close = config.indexOf(']', open);
            Matcher name = Pattern.compile("\"([A-Za-z0-9_$.]+)\"")
                .matcher(config.substring(open, close));
            while (name.find()) out.add(name.group(1));
        }
        return out;
    }

    /** The text inside an annotation's brackets, matched by depth, or nothing if it has none. */
    private static String arguments(String source, int at) {
        if (at < 0) return "";
        int open = at;
        while (open < source.length() && source.charAt(open) != '(' && source.charAt(open) != '\n') open++;
        if (open >= source.length() || source.charAt(open) != '(') return "";
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char each = source.charAt(i);
            if (each == '(') depth++;
            else if (each == ')' && --depth == 0) return source.substring(open + 1, i);
        }
        return "";
    }

    /** Source with its comments taken out, so the annotations are read and the prose about them is not. */
    private static String code(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ")
            .replaceAll("//[^\\n]*", " ");
    }
}
