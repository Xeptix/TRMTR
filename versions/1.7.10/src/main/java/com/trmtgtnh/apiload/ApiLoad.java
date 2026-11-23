package com.trmtgtnh.apiload;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.CodeSource;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.api.Trmtr;
import com.trmtgtnh.api.TrmtrApi;
import com.trmtgtnh.apihost.PluginHost;
import com.trmtgtnh.data.DataTable;
import com.trmtgtnh.dataload.DataFiles;
import com.trmtgtnh.dataload.DataLoad;

/**
 * Starting trmtr-api on 1.7.10 (W3 spec 2.5, "Plugin loading"; task T7): TRMTR installed as what
 * {@code TrmtrApi.get()} gives, the check that nobody shaded the API, and every plugin loaded.
 *
 * <p>
 * <strong>When.</strong> Once, at pre-init, straight after the first data read (which is what lists the plugins, from
 * the {@code plugin.json} files in mod jars and nowhere else) and before the first {@code SurfaceRegistry.resolve} in
 * init. The data plugins give from code ({@code Registrar.data}) are then handed to the data read, which merges them
 * as their own mod's jar and publishes the table again; a {@code /trmt reload} keeps them. Plugins are not loaded again
 * on a reload: what a plugin registers lasts the session.
 *
 * <p>
 * <strong>By whose class loader.</strong> TRMTR's own, the launch class loader, which sees every mod.
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

    /** What every jar of trmtr-api's own says it is, in its manifest's Implementation-Title (D43, 1.0.1). */
    static final String API_TITLE = "trmtr-api";

    /** Where a jar or a class folder keeps its manifest. */
    static final String MANIFEST = "META-INF/MANIFEST.MF";

    private static boolean started;

    /** The session's host: its plugins and their hooks. */
    public static PluginHost host() {
        return HOST;
    }

    /** At pre-init, after the first data read: install, check, load. Once a session. Never throws. */
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
     * is the one used whenever it was loaded first.
     */
    static void checkShading() {
        File own = locate(Trmt.class);
        File api = locate(TrmtrApi.class);
        if (own != null && api != null && !own.equals(api)) HOST.sayOnce("shaded|loaded", loadedLine(api, own));
        for (DataFiles.ModFile mod : DataLoad.modFiles()) {
            File file = mod.file.getAbsoluteFile();
            if (isTrmtr(mod, own)) continue;
            if (!carriesApi(file)) continue;
            HOST.sayOnce("shaded|" + file.getPath(), carrierLine(mod));
        }
    }

    /**
     * Whether a mod file is TRMTR's own, which carries the API by design and is never named by the check: by its mod
     * id, as every later tree asks, or by the file TRMTR's classes were found in. Under LaunchWrapper that location
     * was once unread (see fileOf), and TRMTR's jar was named as a mod that shaded the API in every run (1.0.1).
     */
    static boolean isTrmtr(DataFiles.ModFile mod, File own) {
        return mod.modIds.contains(Trmt.MODID) || mod.file.getAbsoluteFile()
            .equals(own);
    }

    /**
     * What is said when the API's classes came from another file than TRMTR's own: trmtr-api's own jar is named as
     * itself (D43, 1.0.1), any other file as a mod that shaded the API.
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
     * put beside TRMTR from its Modrinth page, is named as itself, for mod developers and safe to remove, and never as
     * a
     * mod to report (D43, 1.0.1); any other file as a mod that shaded the API.
     */
    static String carrierLine(DataFiles.ModFile mod) {
        File file = mod.file.getAbsoluteFile();
        if (isApiJar(file)) {
            return PluginHost.PREFIX + file.getName()
                + " is trmtr-api's own jar, for mod developers to compile against: TRMTR already includes the API, so"
                + " the file is not needed and can be removed";
        }
        return PluginHost.PREFIX + file.getName()
            + " (mod "
            + mod.modIds.get(0)
            + ") carries its own copy of trmtr-api; TRMTR provides the API and a mod must not shade it - report"
            + " this to that mod";
    }

    /**
     * Whether a file is trmtr-api's own jar: every jar trmtr-api builds, the plain one and each loader's, says
     * {@code Implementation-Title: trmtr-api} in its manifest ({@code tools/api/build_api.py}). FML here never lists
     * the Forge jar, whose manifest's {@code ModType: Library} has it skipped, so what this meets is the plain jar
     * loaded in place of TRMTR's own copy.
     */
    static boolean isApiJar(File file) {
        if (file == null) return false;
        try {
            if (file.isDirectory()) {
                File manifest = new File(file, MANIFEST);
                if (!manifest.isFile()) return false;
                InputStream in = new FileInputStream(manifest);
                try {
                    return titled(new Manifest(in));
                } finally {
                    in.close();
                }
            }
            if (!file.isFile()) return false;
            JarFile jar = new JarFile(file);
            try {
                return titled(jar.getManifest());
            } finally {
                jar.close();
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

    /** Whether a mod's file (a jar, or a dev run's class folder) holds the API's entry class. */
    static boolean carriesApi(File file) {
        if (file.isDirectory()) return new File(file, API_CLASS).isFile();
        if (!file.isFile()) return false;
        ZipFile zip = null;
        try {
            zip = new ZipFile(file);
            return zip.getEntry(API_CLASS) != null;
        } catch (IOException | RuntimeException unreadable) {
            return false;
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (IOException ignored) {
                    // Read only; nothing to lose.
                }
            }
        }
    }

    /** The jar or class folder a class was loaded from, or null where the loader does not say. */
    static File locate(Class<?> type) {
        try {
            CodeSource source = type.getProtectionDomain()
                .getCodeSource();
            if (source == null || source.getLocation() == null) return null;
            File found = fileOf(source.getLocation());
            if (found == null) return null;
            // From a class folder the launch class loader gives the class file itself; its root is the folder its
            // package starts in, which is what a jar's location already is.
            if (found.getName()
                .endsWith(".class")) {
                for (int up = type.getName()
                    .split("\\.").length; up > 0 && found != null; up--) found = found.getParentFile();
            }
            return found;
        } catch (Exception unread) {
            return null;
        }
    }

    /**
     * The file a code source's location names, or null where it names none. LaunchWrapper's class loader gives a class
     * from a jar the location of its own entry, {@code jar:file:/.../mods/x.jar!/com/trmtgtnh/Trmt.class}, and a class
     * from a folder its class file; the jar is the part before {@code !/}.
     */
    static File fileOf(URL at) throws Exception {
        if ("jar".equals(at.getProtocol())) {
            String inner = at.getFile();
            int bang = inner.indexOf("!/");
            at = new URL(bang < 0 ? inner : inner.substring(0, bang));
        }
        if (!"file".equals(at.getProtocol())) return null;
        return new File(at.toURI()).getAbsoluteFile();
    }
}
