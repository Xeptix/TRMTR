package com.trmtgtnh.apiload;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.CodeSource;
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
        if (own != null && api != null && !own.equals(api)) {
            HOST.sayOnce(
                "shaded|loaded",
                PluginHost.PREFIX + "trmtr-api was loaded from "
                    + api.getName()
                    + ", not from TRMTR's own "
                    + own.getName()
                    + ": that file carries a shaded copy of the API, which must not be - report this to that mod");
        }
        for (DataFiles.ModFile mod : DataLoad.modFiles()) {
            File file = mod.file.getAbsoluteFile();
            if (file.equals(own)) continue;
            if (!carriesApi(file)) continue;
            HOST.sayOnce(
                "shaded|" + file.getPath(),
                PluginHost.PREFIX + file.getName()
                    + " (mod "
                    + mod.modIds.get(0)
                    + ") carries its own copy of trmtr-api; TRMTR provides the API and a mod must not shade it - report"
                    + " this to that mod");
        }
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
            URL at = source.getLocation();
            if (!"file".equals(at.getProtocol())) return null;
            File found = new File(at.toURI()).getAbsoluteFile();
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
}
