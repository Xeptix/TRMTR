package com.trmtgtnh.apiload;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.api.Trmtr;
import com.trmtgtnh.api.TrmtrApi;
import com.trmtgtnh.apihost.PluginHost;
import com.trmtgtnh.data.DataTable;
import com.trmtgtnh.dataload.DataFiles;
import com.trmtgtnh.dataload.DataLoad;

/**
 * Starting trmtr-api on 1.21.1 (W3 spec 2.5, "Plugin loading"; task T7, carried in T19): TRMTR installed as what
 * {@code TrmtrApi.get()} gives, the check that nobody shaded the API, and every plugin loaded.
 *
 * <p>
 * <strong>When.</strong> Once, straight after the first data read (which is what lists the plugins, from the
 * {@code plugin.json} files in mod jars and nowhere else) and before the first {@code SurfaceRegistry.resolve} as a
 * server starts: on Forge at common setup, once every mod has been constructed (so an example mod's loader class has
 * already told its plugin what it needs, and never from TRMTR's own constructor, which Forge may run beside another
 * mod's); on Fabric from the main entry point, after every mod's pre-launch entry point. The data plugins give from
 * code ({@code Registrar.data}) are then handed to the data read, which merges them as their own mod's jar and
 * publishes the table again; a {@code /trmt reload} or a data-pack reload keeps them. Plugins are not loaded again on a
 * reload: what a plugin registers lasts the session.
 *
 * <p>
 * <strong>Or as the first stitch begins.</strong> A Forge or NeoForge client plans its first block-atlas stitch
 * before common setup, and that stitch reads the looks and, where nothing has yet, resolves the surfaces; so the
 * stitch starts the plugins as it begins, straight after its own first data read
 * ({@code client.PackData.beforeStitch}), where Fabric's entry point has started them already, and common setup's
 * call then does nothing (0.9.229 W6 R9). That start is on the reload's worker thread, beside other mods' common
 * setup: every mod has been constructed and has registered its blocks by then, the start is synchronized, and a
 * plugin's {@code register} only hands TRMTR what it registers.
 *
 * <p>
 * <strong>By whose class loader.</strong> TRMTR's own, which sees every mod: Forge's mod class loader, Fabric's Knot.
 *
 * <p>
 * <strong>Where its lines go.</strong> At WARN on the mod's logger, naming the mod to tell, never through
 * {@code Trmt.error}: a plugin's fault is its mod's to mend ({@code ApiFailuresBlameTheirModTest}).
 */
public final class ApiLoad {

    private ApiLoad() {}

    /** The mod's logger, as the host says its lines. */
    static final PluginHost.Log LOG = new PluginHost.Log() {

        @Override
        public void warn(String line, Throwable thrown) {
            if (thrown == null) Trmt.LOG.warn(line);
            else Trmt.LOG.warn(line, thrown);
        }

        @Override
        public void info(String line) {
            Trmt.LOG.info(line);
        }
    };

    private static final PluginHost HOST = new PluginHost(LOG);

    /** The class every copy of the API carries, as a jar holds it. */
    static final String API_CLASS = "com/trmtgtnh/api/TrmtrApi.class";

    /** trmtr-api's own mod id, as its Fabric jar gives it (D43, 1.0.1; tools/api/build_api.py, FABRIC_ID). */
    static final String API_MOD_ID = "trmtr-api";

    /** What every jar of trmtr-api's own says it is, in its manifest's Implementation-Title (D43, 1.0.1). */
    static final String API_TITLE = "trmtr-api";

    /** Where a jar or a class folder keeps its manifest. */
    static final String MANIFEST = "META-INF/MANIFEST.MF";

    private static boolean started;

    /** The session's host: its plugins and their hooks. */
    public static PluginHost host() {
        return HOST;
    }

    /** After the first data read: install, check, load. Once a session. Never throws. */
    public static synchronized void start() {
        if (started) return;
        started = true;
        try {
            install(ApiBridge.INSTANCE);
            checkShading();
            DataTable table = DataLoad.table();
            for (DataTable.Plugin plugin : table.plugins()) {
                HOST.load(
                    plugin.className,
                    plugin.source.modId,
                    plugin.namespace,
                    plugin.source.where()
                        .replaceFirst("^in ", ""),
                    ApiLoad.class.getClassLoader());
            }
            if (!HOST.data()
                .isEmpty()) DataLoad.pluginData(HOST.data());
            String summary = HOST.summary();
            if (summary != null) LOG.info(summary);
        } catch (RuntimeException | LinkageError unexpected) {
            Trmt.LOG.warn(
                PluginHost.PREFIX + "trmtr-api could not be started, so no plugin is loaded this session - report this"
                    + " to TRMTR",
                unexpected);
        }
    }

    /**
     * Sets what {@code TrmtrApi.get()} gives. {@code install} is package-private in the API, so nothing a mod compiles
     * against can reach it; TRMTR reaches it here, by reflection, from its own jar.
     */
    static void install(Trmtr itself) {
        try {
            Method install = TrmtrApi.class.getDeclaredMethod("install", Trmtr.class);
            install.setAccessible(true);
            install.invoke(null, itself);
        } catch (InvocationTargetException refused) {
            Throwable why = refused.getCause() == null ? refused : refused.getCause();
            Trmt.LOG.warn(PluginHost.PREFIX + "TRMTR could not be installed into trmtr-api: " + why, why);
        } catch (ReflectiveOperationException | SecurityException unreachable) {
            Trmt.LOG.warn(
                PluginHost.PREFIX + "TRMTR could not be installed into trmtr-api, whose TrmtrApi has no install(Trmtr):"
                    + " another jar's copy of the API was loaded in place of TRMTR's own",
                unreachable);
        }
    }

    /**
     * The shading check (API.md, "Never shade the API into your jar"): the API must come from TRMTR's own jar, and no
     * other mod's file may carry a copy. Each jar that does is named once; nothing is refused, since TRMTR's own copy
     * is the one used whenever it was loaded first. TRMTR's own file is known by its mod id, since a loader may hand
     * its jar over as a path inside the jar rather than the file.
     */
    static void checkShading() {
        File own = locate(Trmt.class);
        File api = locate(TrmtrApi.class);
        if (own != null && api != null && !own.equals(api)) HOST.sayOnce("shaded|loaded", loadedLine(api, own));
        for (DataFiles.ModFile mod : DataLoad.modFiles()) {
            if (mod.modIds.contains(Trmt.MODID)) continue;
            if (!carriesApi(mod)) continue;
            HOST.sayOnce("shaded|" + mod.name, carrierLine(mod));
        }
    }

    /**
     * What is said when the API's classes came from another file than TRMTR's own: trmtr-api's own jar is named as
     * itself (D43, 1.0.1), any other file as a mod that shaded the API. On Fabric, where every mod is on one class
     * path, trmtr-api's jar beside TRMTR may be the copy that loads first; it is the same classes.
     */
    static String loadedLine(File api, File own) {
        if (isApiJar(api)) {
            return PluginHost.PREFIX + "trmtr-api was loaded from "
                + api.getName()
                + ", trmtr-api's own jar for mod developers, rather than from TRMTR's own "
                + own.getName()
                + ": TRMTR already includes the API, so that file is not needed and can be removed";
        }
        return PluginHost.PREFIX + "trmtr-api was loaded from "
            + api.getName()
            + ", not from TRMTR's own "
            + own.getName()
            + ": that file carries a shaded copy of the API, which must not be - report this to that mod";
    }

    /**
     * What is said of a mod file other than TRMTR's that carries the API: trmtr-api's own jar, which a player may have
     * put beside TRMTR from its Modrinth page, is named as itself, for mod developers and safe to remove, and never as a
     * mod to report (D43, 1.0.1); any other file as a mod that shaded the API.
     */
    static String carrierLine(DataFiles.ModFile mod) {
        if (isApiJar(mod)) {
            return PluginHost.PREFIX + mod.name
                + " is trmtr-api's own jar, for mod developers to compile against: TRMTR already includes the API, so"
                + " the file is not needed and can be removed";
        }
        return PluginHost.PREFIX + mod.name
            + " (mod "
            + mod.modIds.get(0)
            + ") carries its own copy of trmtr-api; TRMTR provides the API and a mod must not shade it - report"
            + " this to that mod";
    }

    /**
     * Whether a mod file is trmtr-api's own jar: on Fabric by its mod id, trmtr-api, and wherever its loader lists it
     * by its manifest, whose {@code Implementation-Title} is trmtr-api in every jar trmtr-api builds
     * ({@code tools/api/build_api.py}). Forge and NeoForge load its jars as libraries, never as mods, so their lists
     * do not hold it; the manifest is what would name it there.
     */
    static boolean isApiJar(DataFiles.ModFile mod) {
        if (mod.modIds.contains(API_MOD_ID)) return true;
        for (Path root : mod.roots) {
            try {
                if (root == null) continue;
                Path at = root.resolve(MANIFEST);
                if (!Files.isRegularFile(at)) continue;
                try (InputStream in = Files.newInputStream(at)) {
                    if (titled(new Manifest(in))) return true;
                }
            } catch (IOException | RuntimeException unreadable) {
                // A root that cannot be read says nothing of what it is.
            }
        }
        return false;
    }

    /** Whether a jar or a class folder is trmtr-api's own, by its manifest (see above). */
    static boolean isApiJar(File file) {
        if (file == null) return false;
        try {
            if (file.isDirectory()) {
                File manifest = new File(file, MANIFEST);
                if (!manifest.isFile()) return false;
                try (InputStream in = new FileInputStream(manifest)) {
                    return titled(new Manifest(in));
                }
            }
            if (!file.isFile()) return false;
            try (JarFile jar = new JarFile(file)) {
                return titled(jar.getManifest());
            }
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
    }

    private static boolean titled(Manifest manifest) {
        return manifest != null && API_TITLE.equals(
            manifest.getMainAttributes()
                .getValue(Attributes.Name.IMPLEMENTATION_TITLE));
    }

    /** Whether a mod's file (a jar, or a development run's folders) holds the API's entry class at any root. */
    static boolean carriesApi(DataFiles.ModFile mod) {
        for (Path root : mod.roots) {
            try {
                if (root != null && Files.isRegularFile(root.resolve(API_CLASS))) return true;
            } catch (RuntimeException unreadable) {
                // A root that cannot be asked holds nothing this check can see.
            }
        }
        return false;
    }

    /** The jar or class folder a class was loaded from, or null where the loader does not say. */
    static File locate(Class<?> type) {
        try {
            CodeSource source = type.getProtectionDomain()
                .getCodeSource();
            if (source == null || source.getLocation() == null) return null;
            URL at = source.getLocation();
            if (!"file".equals(at.getProtocol())) return null;
            File found = new File(at.toURI()).getAbsoluteFile();
            // From a class folder a loader may give the class file itself; its root is the folder its package starts
            // in, which is what a jar's location already is.
            if (found.getName()
                .endsWith(".class")) {
                for (int parents = type.getName()
                    .split("[.]").length; parents > 0 && found != null; parents--) found = found.getParentFile();
            }
            return found;
        } catch (Exception unread) {
            return null;
        }
    }
}
