package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.trmtgtnh.core.TrmtLoadingPlugin;

/**
 * The loading plugin that hands the mixin config to MixinBooter's early loader when MixinBooter did not boot
 * Mixin - JourneyMap 6 for 1.12.2 boots its own, and then the manifest is never read and every mixin here is
 * missing. Found by the 0.9.219 pass; see {@link TrmtLoadingPlugin}.
 *
 * <p>
 * The plugin, the build that names it, and the warning that says when the mixins are missing all the same.
 */
class LoadingPluginIsWiredTest {

    @Test
    void the_config_is_handed_over_only_when_mixinbooter_did_not_boot_mixin() {
        assertEquals(
            Collections.emptyList(),
            TrmtLoadingPlugin.configsFor("MixinBooter"),
            "handed over under MixinBooter as well as read from the manifest, every mixin would apply twice");
        assertEquals(
            Collections.singletonList("mixins.trmtgtnh.json"),
            TrmtLoadingPlugin.configsFor("LaunchWrapper"),
            "under a Mixin JourneyMap booted, nothing reads the manifest, and nothing hands the config over");
        assertEquals(
            Collections.emptyList(),
            TrmtLoadingPlugin.configsFor(null),
            "with no Mixin to ask, nothing is registered");
    }

    @Test
    void the_plugin_registers_the_config_itself_and_needs_nothing_of_mixinbooter_to_load() throws IOException {
        String plugin = code(String.join("\n", SourceTree.lines("com/trmtgtnh/core/TrmtLoadingPlugin.java")));
        assertFalse(
            plugin.contains("zone.rong.mixinbooter"),
            "the plugin names a MixinBooter class, so a game without MixinBooter fails loading it instead of reaching"
                + " FML's missing-mod screen");
        String inject = plugin.substring(plugin.indexOf("public void injectData("));
        inject = inject.substring(0, inject.indexOf("\n    }"))
            .replaceAll("\\s+", "");
        assertTrue(
            inject.contains("List<String>configs=configsFor(service);if(configs.isEmpty())return;"),
            "the plugin registers the config whoever booted Mixin, so under MixinBooter it would apply twice");
        assertTrue(
            inject.contains("org.spongepowered.asm.mixin.Mixins.addConfiguration(config);"),
            "the plugin never registers the config");
        assertTrue(
            inject.contains("com.llamalad7.mixinextras.MixinExtrasBootstrap.init();"),
            "MixinExtras is left unstarted");
    }

    @Test
    void the_jar_names_the_plugin_and_builds_the_same_manifest_however_it_is_asked() throws IOException {
        String properties = read("gradle.properties");
        assertTrue(properties.contains("\nis_coremod = true"), "the jar is not built as a loading plugin");
        assertTrue(
            properties.contains("\ncoremod_plugin_class_name = com.trmtgtnh.core.TrmtLoadingPlugin"),
            "the manifest does not name TrmtLoadingPlugin");
        assertTrue(
            properties.contains("\nmixin_configs = mixins.trmtgtnh.json"),
            "the manifest no longer names the config");
        String build = code(read("build.gradle"));
        assertTrue(
            build.contains("attribute_map['FMLCorePluginContainsFMLMod'] = true"),
            "FML is not told the plugin's jar holds the mod as well, so the mod would not load");
        assertFalse(
            build.contains("gradle.startParameter.taskNames"),
            "the manifest depends on which Gradle task built the jar again");
    }

    @Test
    void a_game_without_the_mixins_says_so() throws IOException {
        String books = code(String.join("\n", SourceTree.lines("com/trmtgtnh/mixin/MixinLibrarianBooks.java")));
        assertTrue(
            books.contains("public abstract class MixinLibrarianBooks implements com.trmtgtnh.core.MixinsApplied {"),
            "nothing marks a class the mixins applied to, so a game cannot tell whether they did");
        String trmt = code(String.join("\n", SourceTree.lines("com/trmtgtnh/Trmt.java")));
        String preInit = trmt.substring(trmt.indexOf("public void preInit(FMLPreInitializationEvent event) {"));
        assertTrue(
            preInit.substring(0, preInit.indexOf("\n    }"))
                .contains("mixinsLoaded();"),
            "pre-init never asks whether the mixins loaded");
        String check = trmt.substring(trmt.indexOf("static void mixinsLoaded() {"));
        assertTrue(
            check.replaceAll("\\s+", "")
                .contains(
                    "if(com.trmtgtnh.core.MixinsApplied.class.isAssignableFrom(net.minecraft.entity.passive.EntityVillager.ListEnchantedBookForEmeralds.class))return;"),
            "the check does not ask the marked class, so it cannot tell a loaded config from a missing one");
    }

    private static String read(String name) throws IOException {
        File root = SourceTree.mainJava()
            .getParentFile()
            .getParentFile()
            .getParentFile();
        File file = new File(root, name);
        assertTrue(file.isFile(), file.getAbsolutePath() + " is not there");
        return new String(Files.readAllBytes(file.toPath()), Charset.forName("UTF-8")).replace("\r\n", "\n");
    }

    /** Comments out, so a guard reads code and never prose about it. */
    private static String code(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ")
            .replaceAll("//[^\\n]*", " ");
    }
}
