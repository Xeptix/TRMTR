package com.trmtgtnh.apihost;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.trmtgtnh.api.GroundRule;
import com.trmtgtnh.api.Registrar;
import com.trmtgtnh.api.TrmtrApi;
import com.trmtgtnh.api.TrmtrPlugin;
import com.trmtgtnh.api.Wear;
import com.trmtgtnh.api.WearContext;
import com.trmtgtnh.api.WearListener;
import com.trmtgtnh.api.WearRenderer;
import com.trmtgtnh.api.WearerRule;
import com.trmtgtnh.data.Selector;

/**
 * trmtr-api's host: the plugins a session loaded, what each registered, and every call into them (W3 spec 2.3 and
 * 2.5; api/API.md, "Failures").
 *
 * <p>
 * <strong>A plugin never stops the game.</strong> Loading a plugin's class, making it, its {@code register} and every
 * later call into a listener, rule or renderer is caught: an {@code Exception}, a {@code LinkageError} (a class it
 * needs is missing, or its static set-up threw), an {@code AssertionError} or a {@code StackOverflowError}. Nothing
 * else is, so the machine running out of memory is still the machine's to report. A plugin that cannot be loaded, or
 * whose {@code register} throws, is switched off and keeps nothing it registered. A hook that throws, or answers with
 * a number that is no number, is switched off for the rest of the session and the mod's other hooks go on (API.md,
 * "Failures"). Either is said once, at WARN, with the stack, naming the mod and telling the player to report it to
 * that mod: never through {@code Trmt.error}, whose first line sends the player to TRMTR's own tracker for another
 * mod's fault ({@code ApiFailuresBlameTheirModTest}).
 *
 * <p>
 * <strong>What a plugin registers lasts the session.</strong> A {@link Registrar} is handed to one {@code register}
 * call and is only valid during it; what it gathers is published when the call returns, whole, so a hook list is never
 * seen half-built. The lists are copied on every change and read without a lock, which suits lists that change a
 * handful of times a session and are read on every footstep.
 *
 * <p>
 * <strong>Hooks calling back.</strong> A listener may call {@code Actions}, which passes through the listeners again.
 * The depth is held to {@link #MOST_DEPTH} on each thread; past it the call goes on unhooked, said once, so a listener
 * that wears the ground it is told about cannot recurse until the stack runs out.
 *
 * <p>
 * <strong>Nothing of a plugin's runs inside a chunk load.</strong> A chunk's load event comes on the server thread
 * while the chunk is still being finished, and a chunk caught up there is healed there, so a listener told at once
 * reads a block of a chunk that cannot finish, because the thread that would finish it is the one reading. The dev
 * 1.16.5 Fabric run parked its server thread for ever that way, in the example's own {@code afterChange}, and the
 * sweep read the run as cut short on a saturated disk (0.9.229, W3). So between {@link #enterLoading} and
 * {@link #leaveLoading} a change is held, and {@link #deliverHeld} tells it on the server's next tick, in order, on the
 * server thread; a change after it waits behind it, so the listeners hear every change in the order it happened. At
 * most {@link #MOST_HELD} wait, {@link #MOST_TOLD_A_TICK} are told a tick, and {@link #dropHeld} throws the rest away
 * when the server stops. A rule is asked for an answer and cannot wait: inside a load it is not asked at all, the
 * wear does not land and a wearer rule's multiplier stays as TRMTR worked it out (API.md, "Chunk loading").
 *
 * <p>
 * Nothing here names the game: each edition's adapter turns its world, entities and records into what this hands a
 * plugin. Portable: the same file in all eight trees.
 */
public final class PluginHost {

    /** Where the host's lines go; the adapter's is the mod's logger. */
    public interface Log {

        /** A line at WARN, with the stack when there is one (null otherwise). */
        void warn(String line, Throwable thrown);

        void info(String line);
    }

    /** The start of every line the host says. */
    public static final String PREFIX = "TRMTR API: ";

    /**
     * At most so many distinct said-once lines a session, so a plugin calling wrongly in a loop cannot fill the log.
     */
    public static final int MOST_SAID = 1024;

    /** How deep a hook may call back into TRMTR and still be hooked again. */
    public static final int MOST_DEPTH = 4;

    /**
     * The most a listener's amount, a wearer rule's multiplier or an {@code Actions.wear} may be: far past anything
     * traffic or a blast gives (a step is about 1, the middle of a dynamite blast 168 by default), and small enough
     * that wear banked with it stays
     * finite (Codex review R16).
     */
    public static final float MOST_AMOUNT = 1.0e6f;

    /**
     * The most changes that wait for a tick. A chunk away for months can hold thousands and a player arriving brings
     * hundreds of chunks at once, so this is far past an ordinary arrival, and at a few words of memory each it is
     * still a small price. Past it a change is not told, said once.
     */
    public static final int MOST_HELD = 65536;

    /** The most held changes told in one tick, so a large arrival costs some ticks a little rather than one a lot. */
    public static final int MOST_TOLD_A_TICK = 4096;

    /** One plugin a mod's {@code plugin.json} names. */
    public static final class Plugin {

        public final String className;
        public final String modId;
        /** The namespace its {@code plugin.json} is under, which is whose blocks its renderers may draw. */
        public final String namespace;
        /** Where its {@code plugin.json} was found, as a line names it. */
        public final String where;
        volatile boolean off;

        Plugin(String className, String modId, String namespace, String where) {
            this.className = className;
            this.modId = modId == null ? "unknown" : modId;
            this.namespace = namespace == null ? "" : namespace;
            this.where = where == null ? "" : where;
        }

        /** Whether it was switched off: it could not be loaded, or its register threw. */
        public boolean off() {
            return off;
        }
    }

    /** A change held for the server's next tick. Its records are snapshots, so it says what was true when it came. */
    private static final class Change {

        final Object level;
        final int x;
        final int y;
        final int z;
        final Wear before;
        final Wear after;

        Change(Object level, int x, int y, int z, Wear before, Wear after) {
            this.level = level;
            this.x = x;
            this.y = y;
            this.z = z;
            this.before = before;
            this.after = after;
        }
    }

    /** One registered listener, rule or renderer, with the plugin that registered it. */
    static final class Hook<T> {

        final Plugin owner;
        final T hook;
        final String kind;
        volatile boolean off;

        Hook(Plugin owner, T hook, String kind) {
            this.owner = owner;
            this.hook = hook;
            this.kind = kind;
        }

        String name() {
            return hook.getClass()
                .getName();
        }
    }

    /** A renderer with its selector, read once when it was kept. */
    private static final class Drawer {

        final Selector selector;
        final Hook<WearRenderer> hook;

        Drawer(Selector selector, Hook<WearRenderer> hook) {
            this.selector = selector;
            this.hook = hook;
        }
    }

    /** A data file a plugin gave from code ({@code Registrar.data}), placed as its own mod's jar would hold it. */
    public static final class Data {

        public final String modId;
        /** {@code assets/<namespace>/trmtr/<name>}, as the data reader places a file. */
        public final String path;
        public final String text;

        Data(String modId, String path, String text) {
            this.modId = modId;
            this.path = path;
            this.text = text;
        }
    }

    private final Log log;
    private final List<Plugin> plugins = new ArrayList<Plugin>();
    private volatile List<Hook<WearListener>> listeners = Collections.emptyList();
    private volatile List<Hook<GroundRule>> groundRules = Collections.emptyList();
    private volatile List<Hook<WearerRule>> wearerRules = Collections.emptyList();
    private volatile Map<String, Hook<WearRenderer>> renderers = Collections.emptyMap();
    /** The renderers with their selectors read once, so a chunk build never reads a selector's text again. */
    private volatile List<Drawer> drawers = Collections.emptyList();
    private volatile List<Data> data = Collections.emptyList();
    private final Set<String> said = new HashSet<String>();
    private final ThreadLocal<int[]> depth = new ThreadLocal<int[]>() {

        @Override
        protected int[] initialValue() {
            return new int[1];
        }
    };

    /** How many chunk loads this thread is inside. */
    private final ThreadLocal<int[]> loads = new ThreadLocal<int[]>() {

        @Override
        protected int[] initialValue() {
            return new int[1];
        }
    };
    /** The changes waiting for a tick, oldest first. Guarded by itself. */
    private final ArrayDeque<Change> held = new ArrayDeque<Change>();
    /** How many wait, read without the lock on every change so an empty queue costs one volatile read. */
    private volatile int heldCount;

    public PluginHost(Log log) {
        this.log = log;
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /**
     * Loads one plugin and calls its {@code register}, once a session: a class already loaded is not loaded again,
     * however many reads list it. Never throws.
     *
     * @param loader TRMTR's own class loader, which sees every mod
     */
    public Plugin load(String className, String modId, String namespace, String where, ClassLoader loader) {
        Plugin plugin;
        synchronized (this) {
            for (Plugin known : plugins) if (known.className.equals(className)) return known;
            plugin = new Plugin(className, modId, namespace, where);
            plugins.add(plugin);
        }
        TrmtrPlugin instance;
        try {
            Class<?> type = Class.forName(className, true, loader);
            if (!TrmtrPlugin.class.isAssignableFrom(type)) {
                switchOff(
                    plugin,
                    carriesItsOwnApi(type)
                        ? "implements a copy of TrmtrPlugin that is not TRMTR's: the mod has shaded"
                            + " trmtr-api into its jar, which it must not"
                        : "does not implement " + TrmtrPlugin.class.getName(),
                    null);
                return plugin;
            }
            instance = (TrmtrPlugin) type.getConstructor()
                .newInstance();
        } catch (ClassNotFoundException missing) {
            switchOff(plugin, "is not in the game: no class " + className + " was found", null);
            return plugin;
        } catch (NoSuchMethodException noMaker) {
            switchOff(plugin, "has no public constructor without parameters", null);
            return plugin;
        } catch (Exception | LinkageError | AssertionError | StackOverflowError broken) {
            switchOff(plugin, "could not be made", unwrap(broken));
            return plugin;
        }
        Opening opening = new Opening(plugin);
        try {
            instance.register(opening);
        } catch (Exception | LinkageError | AssertionError | StackOverflowError thrown) {
            opening.open = false;
            switchOff(plugin, "threw from its register, so nothing it registered is kept", thrown);
            return plugin;
        }
        opening.open = false;
        keep(opening);
        log.info(
            PREFIX + "loaded the plugin "
                + className
                + " of mod "
                + plugin.modId
                + " (trmtr-api "
                + TrmtrApi.VERSION
                + ")");
        return plugin;
    }

    private static Throwable unwrap(Throwable thrown) {
        if (thrown instanceof java.lang.reflect.InvocationTargetException && thrown.getCause() != null) {
            return thrown.getCause();
        }
        return thrown;
    }

    /** Whether a class implements an interface named as TRMTR's plugin interface that is not TRMTR's own class. */
    private static boolean carriesItsOwnApi(Class<?> type) {
        for (Class<?> at = type; at != null; at = at.getSuperclass()) {
            for (Class<?> each : at.getInterfaces()) {
                if (each.getName()
                    .equals(TrmtrPlugin.class.getName()) && each != TrmtrPlugin.class) return true;
            }
        }
        return false;
    }

    private void switchOff(Plugin plugin, String why, Throwable thrown) {
        plugin.off = true;
        log.warn(
            PREFIX + "did not load the plugin "
                + plugin.className
                + " of mod "
                + plugin.modId
                + (plugin.where.isEmpty() ? "" : " (" + plugin.where + ")")
                + ": it "
                + why
                + ". The game goes on without it - report this to that mod",
            thrown);
    }

    /** Publishes what one register call gathered, whole. */
    private synchronized void keep(Opening opening) {
        listeners = joined(listeners, opening.listeners);
        groundRules = joined(groundRules, opening.groundRules);
        wearerRules = joined(wearerRules, opening.wearerRules);
        if (!opening.renderers.isEmpty()) {
            Map<String, Hook<WearRenderer>> next = new LinkedHashMap<String, Hook<WearRenderer>>(renderers);
            next.putAll(opening.renderers);
            renderers = Collections.unmodifiableMap(next);
            List<Drawer> parsed = new ArrayList<Drawer>();
            for (Map.Entry<String, Hook<WearRenderer>> each : next.entrySet()) {
                Selector selector = Selector.block(each.getKey());
                if (selector != null) parsed.add(new Drawer(selector, each.getValue()));
            }
            drawers = Collections.unmodifiableList(parsed);
        }
        if (!opening.data.isEmpty()) {
            List<Data> next = new ArrayList<Data>(data);
            next.addAll(opening.data);
            data = Collections.unmodifiableList(next);
        }
    }

    private static <T> List<Hook<T>> joined(List<Hook<T>> held, List<Hook<T>> more) {
        if (more.isEmpty()) return held;
        List<Hook<T>> next = new ArrayList<Hook<T>>(held);
        next.addAll(more);
        return Collections.unmodifiableList(next);
    }

    /** The registrar one register call is handed; valid only until that call returns. */
    private final class Opening implements Registrar {

        final Plugin plugin;
        boolean open = true;
        final List<Hook<WearListener>> listeners = new ArrayList<Hook<WearListener>>();
        final List<Hook<GroundRule>> groundRules = new ArrayList<Hook<GroundRule>>();
        final List<Hook<WearerRule>> wearerRules = new ArrayList<Hook<WearerRule>>();
        final Map<String, Hook<WearRenderer>> renderers = new LinkedHashMap<String, Hook<WearRenderer>>();
        final List<Data> data = new ArrayList<Data>();

        Opening(Plugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void wearListener(WearListener listener) {
            if (refused("wearListener", listener)) return;
            listeners.add(new Hook<WearListener>(plugin, listener, "WearListener"));
        }

        @Override
        public void groundRule(GroundRule rule) {
            if (refused("groundRule", rule)) return;
            groundRules.add(new Hook<GroundRule>(plugin, rule, "GroundRule"));
        }

        @Override
        public void wearerRule(WearerRule rule) {
            if (refused("wearerRule", rule)) return;
            wearerRules.add(new Hook<WearerRule>(plugin, rule, "WearerRule"));
        }

        @Override
        public void renderer(String blockSelector, WearRenderer renderer) {
            if (refused("renderer", renderer)) return;
            String why = notOwn(blockSelector, plugin.namespace, plugin.modId);
            if (why != null) {
                sayOnce(
                    "renderer|" + plugin.className + "|" + blockSelector,
                    PREFIX + "refused the renderer the plugin of mod "
                        + plugin.modId
                        + " registered for "
                        + blockSelector
                        + ": "
                        + why
                        + " - report this to that mod");
                return;
            }
            renderers.put(
                Selector.block(blockSelector)
                    .canonical(),
                new Hook<WearRenderer>(plugin, renderer, "WearRenderer"));
        }

        @Override
        public void data(String name, String json) {
            if (refused("data", name) || refused("data", json)) return;
            String clean = name.replace('\\', '/');
            if (clean.startsWith("/") || clean.contains("..")) {
                sayOnce(
                    "data|" + plugin.className + "|" + name,
                    PREFIX + "refused the data file "
                        + name
                        + " the plugin of mod "
                        + plugin.modId
                        + " gave: a name is a path under trmtr/, such as families/loam.json - report this to that mod");
                return;
            }
            data.add(new Data(plugin.modId, "assets/" + plugin.namespace + "/trmtr/" + clean, json));
        }

        private boolean refused(String method, Object given) {
            if (!open) {
                sayOnce(
                    "late|" + plugin.className + "|" + method,
                    PREFIX + "ignored Registrar."
                        + method
                        + " from the plugin of mod "
                        + plugin.modId
                        + ": it was called after register had returned, and a Registrar is only valid during that call"
                        + " - report this to that mod");
                return true;
            }
            if (given == null) {
                sayOnce(
                    "null|" + plugin.className + "|" + method,
                    PREFIX + "ignored Registrar."
                        + method
                        + " from the plugin of mod "
                        + plugin.modId
                        + ": it was given null - report this to that mod");
                return true;
            }
            return false;
        }
    }

    /**
     * Why a renderer's selector is not the plugin's own, or null when it is. A plugin draws only its own namespace's
     * blocks (API.md, "Registrar"): the namespace its {@code plugin.json} is under, or its mod id, without regard to
     * case, since a 1.7.10 mod's blocks are often named {@code ModId:block} while its assets are {@code modid}.
     */
    public static String notOwn(String blockSelector, String namespace, String modId) {
        Selector selector = Selector.block(blockSelector);
        if (selector == null) return "it is not a block selector";
        String of = selector.namespace;
        if (of.equalsIgnoreCase(namespace) || (modId != null && of.equalsIgnoreCase(modId))) return null;
        return "it is of namespace " + of
            + ", and a plugin draws only its own namespace's blocks ("
            + (namespace == null || namespace.isEmpty() ? modId : namespace)
            + ")";
    }

    // ------------------------------------------------------------------
    // Calling the hooks
    // ------------------------------------------------------------------

    /** Whether any listener or ground rule is to be asked before wear lands; false costs the engine nothing more. */
    public boolean asksBeforeWear() {
        return !listeners.isEmpty() || !groundRules.isEmpty();
    }

    /** Whether any listener is to be told of a change. */
    public boolean listens() {
        return !listeners.isEmpty();
    }

    /** Whether any wearer rule is to be asked. */
    public boolean rulesWearers() {
        return !wearerRules.isEmpty();
    }

    /** Whether the ground may wear: false when any ground rule says so. */
    public boolean mayWear(Object level, int x, int y, int z, String block, Object entity) {
        if (notAsked("GroundRule.mayWear")) return false;
        if (!enter()) return true;
        try {
            for (Hook<GroundRule> rule : groundRules) {
                if (rule.off) continue;
                try {
                    if (!rule.hook.mayWear(level, x, y, z, block, entity)) return false;
                } catch (Exception | LinkageError | AssertionError | StackOverflowError thrown) {
                    failed(rule, "mayWear", null, thrown);
                }
            }
            return true;
        } finally {
            leave();
        }
    }

    /** The amount after every listener has had its say, in the order they were registered; 0 cancels. */
    public float beforeWear(WearContext context, float amount) {
        if (notAsked("WearListener.beforeWear")) return 0f;
        if (!enter()) return amount;
        try {
            float now = amount;
            for (Hook<WearListener> listener : listeners) {
                if (listener.off) continue;
                try {
                    float answer = listener.hook.beforeWear(context, now);
                    if (Float.isNaN(answer) || Float.isInfinite(answer)) {
                        failed(listener, "beforeWear", "returned " + answer + ", which is no amount", null);
                        continue;
                    }
                    now = answer < 0f ? 0f : held(listener, "beforeWear", answer);
                    if (now == 0f) return 0f;
                } catch (Exception | LinkageError | AssertionError | StackOverflowError thrown) {
                    failed(listener, "beforeWear", null, thrown);
                }
            }
            return now;
        } finally {
            leave();
        }
    }

    /**
     * Tells every listener a record changed; either side is null where there was no record. Inside a chunk load, or
     * while earlier changes still wait, the change is held for the server's next tick instead. A hook's own call back
     * is told at once, as it always was: its listener is already running, outside any load.
     */
    public void afterChange(Object level, int x, int y, int z, Wear before, Wear after) {
        if (listeners.isEmpty()) return;
        if (loading() || (heldCount > 0 && depth.get()[0] == 0)) {
            hold(new Change(level, x, y, z, before, after));
            return;
        }
        tell(level, x, y, z, before, after);
    }

    private void tell(Object level, int x, int y, int z, Wear before, Wear after) {
        if (!enter()) return;
        try {
            for (Hook<WearListener> listener : listeners) {
                if (listener.off) continue;
                try {
                    listener.hook.afterChange(level, x, y, z, before, after);
                } catch (Exception | LinkageError | AssertionError | StackOverflowError thrown) {
                    failed(listener, "afterChange", null, thrown);
                }
            }
        } finally {
            leave();
        }
    }

    /** A mob's multiplier after every wearer rule, each given the one before it; never below 0. */
    public float multiplier(Object entity, String entityId, float current) {
        if (notAsked("WearerRule.multiplier")) return current;
        if (!enter()) return current;
        try {
            float now = current;
            for (Hook<WearerRule> rule : wearerRules) {
                if (rule.off) continue;
                try {
                    float answer = rule.hook.multiplier(entity, entityId, now);
                    if (Float.isNaN(answer) || Float.isInfinite(answer)) {
                        failed(rule, "multiplier", "returned " + answer + ", which is no multiplier", null);
                        continue;
                    }
                    now = answer < 0f ? 0f : held(rule, "multiplier", answer);
                } catch (Exception | LinkageError | AssertionError | StackOverflowError thrown) {
                    failed(rule, "multiplier", null, thrown);
                }
            }
            return now;
        } finally {
            leave();
        }
    }

    // ------------------------------------------------------------------
    // Chunk loading
    // ------------------------------------------------------------------

    /** This thread is entering a chunk load: until {@link #leaveLoading}, nothing of a plugin's is called from it. */
    public void enterLoading() {
        loads.get()[0]++;
    }

    /** Ends what {@link #enterLoading} began; called in a {@code finally}. */
    public void leaveLoading() {
        loads.get()[0]--;
    }

    /** Whether this thread is inside a chunk load. */
    public boolean loading() {
        return loads.get()[0] > 0;
    }

    /** How many changes wait for a tick. */
    public int heldChanges() {
        return heldCount;
    }

    /**
     * Whether a rule is to be left unasked, because this thread is inside a chunk load: a rule reads the world, and a
     * rule's answer cannot wait for a tick. Said once, as nothing in TRMTR should have come here.
     */
    private boolean notAsked(String hook) {
        if (!loading()) return false;
        sayOnce(
            "loading|" + hook,
            PREFIX + hook
                + " was reached inside a chunk load and was not asked, as a hook that reads the world there would wait"
                + " for ever for the chunk it stands on; no wear lands inside a load");
        return true;
    }

    private void hold(Change change) {
        boolean full;
        synchronized (held) {
            full = held.size() >= MOST_HELD;
            if (!full) {
                held.add(change);
                heldCount = held.size();
            }
        }
        if (full) {
            sayOnce(
                "held",
                PREFIX + "more than "
                    + MOST_HELD
                    + " changes were waiting for a tick, so the listeners are not told of those past that many");
        }
    }

    /**
     * Tells the listeners what waited for a tick, oldest first, at most {@link #MOST_TOLD_A_TICK} of it; what is left
     * goes on the next tick. Called at the end of each server tick, which is outside every chunk load. Returns how
     * many it told. Inside a load it tells nothing: the changes wait for the next tick.
     */
    public int deliverHeld() {
        if (heldCount == 0 || loading()) return 0;
        int told = 0;
        while (told < MOST_TOLD_A_TICK) {
            Change next;
            synchronized (held) {
                next = held.poll();
                heldCount = held.size();
            }
            if (next == null) break;
            tell(next.level, next.x, next.y, next.z, next.before, next.after);
            told++;
        }
        return told;
    }

    /**
     * Throws away what waits, for when the server has stopped: the world each change names has gone, and a change
     * told into the next world would be a lie. Returns how many it dropped, which is said.
     */
    public int dropHeld() {
        int dropped;
        synchronized (held) {
            dropped = held.size();
            held.clear();
            heldCount = 0;
        }
        if (dropped > 0) {
            log.info(PREFIX + "dropped " + dropped + " change(s) held for the next tick, as the server stopped");
        }
        return dropped;
    }

    /** Whether any renderer is registered and still on; false costs a chunk build nothing more. */
    public boolean draws() {
        for (Drawer each : drawers) if (!each.hook.off) return true;
        return false;
    }

    /**
     * Hands a worn block to the renderer registered for it (W3 T8): the most particular selector that takes it wins
     * (one naming its metadata or properties before one naming the block alone, a tag last), and between equals the
     * first registered. Returns the renderer's answer: true when it drew the block, false when there is none, it
     * declined, or it threw - in which case it is switched off for the session, said once naming its mod, and TRMTR
     * draws the block as usual.
     *
     * @param blockId the block's id, {@code ns:name}
     * @param meta    its metadata, or {@link Selector#ANY_META} on an edition without
     * @param state   its properties, or null on an edition without
     * @param tags    the tags it is in, or null on an edition without
     */
    public boolean draw(String blockId, int meta, Map<String, String> state, java.util.Collection<String> tags,
        com.trmtgtnh.api.WearDraw draw) {
        Hook<WearRenderer> chosen = null;
        int best = -1;
        for (Drawer each : drawers) {
            Hook<WearRenderer> hook = each.hook;
            if (hook.off) continue;
            Selector selector = each.selector;
            if (!selector.matches(blockId, meta, state, tags)) continue;
            int particular = selector.tag ? 0
                : 1 + (selector.meta != Selector.ANY_META ? 1 : 0) + selector.properties.size();
            if (particular > best) {
                best = particular;
                chosen = hook;
            }
        }
        if (chosen == null || !enter()) return false;
        try {
            return chosen.hook.draw(draw);
        } catch (Exception | LinkageError | AssertionError | StackOverflowError thrown) {
            failed(chosen, "draw", null, thrown);
            return false;
        } finally {
            leave();
        }
    }

    /**
     * An answer held to {@link #MOST_AMOUNT}, said once for the hook that gave more (Codex review R16): a finite answer
     * as large as {@code Float.MAX_VALUE}, added to the wear a record has banked, would make it infinite.
     */
    private float held(Hook<?> hook, String method, float answer) {
        if (answer <= MOST_AMOUNT) return answer;
        sayOnce(
            "most|" + hook.owner.className + "|" + hook.name() + "|" + method,
            PREFIX + "held the "
                + hook.kind
                + " "
                + hook.name()
                + " of mod "
                + hook.owner.modId
                + " to "
                + MOST_AMOUNT
                + ": it returned "
                + answer
                + " from "
                + method
                + ", more than any wear or multiplier may be - report this to that mod");
        return MOST_AMOUNT;
    }

    private boolean enter() {
        int[] held = depth.get();
        if (held[0] >= MOST_DEPTH) {
            sayOnce(
                "depth",
                PREFIX + "a plugin's hook called back into TRMTR more than "
                    + MOST_DEPTH
                    + " deep; calls past that depth are not hooked again");
            return false;
        }
        held[0]++;
        return true;
    }

    private void leave() {
        depth.get()[0]--;
    }

    /** Switches a hook off for the session and says so once, naming its mod. */
    private void failed(Hook<?> hook, String method, String why, Throwable thrown) {
        synchronized (this) {
            if (hook.off) return;
            hook.off = true;
            listeners = without(listeners);
            groundRules = without(groundRules);
            wearerRules = without(wearerRules);
        }
        log.warn(
            PREFIX + "switched off the "
                + hook.kind
                + " "
                + hook.name()
                + " of mod "
                + hook.owner.modId
                + " for this session: it "
                + (why != null ? why
                    : "threw " + thrown.getClass()
                        .getName())
                + " in "
                + method
                + ". The mod's other hooks go on - report this to that mod",
            thrown);
    }

    private static <T> List<Hook<T>> without(List<Hook<T>> held) {
        List<Hook<T>> next = new ArrayList<Hook<T>>(held.size());
        for (Hook<T> each : held) if (!each.off) next.add(each);
        return next.size() == held.size() ? held : Collections.unmodifiableList(next);
    }

    // ------------------------------------------------------------------
    // What the adapter reads
    // ------------------------------------------------------------------

    /** The data files plugins gave from code, in the order they were given. */
    public List<Data> data() {
        return data;
    }

    /** The renderers registered, by their selectors' canonical text; for the render hook (T8). */
    public Map<String, WearRenderer> renderers() {
        Map<String, WearRenderer> out = new LinkedHashMap<String, WearRenderer>();
        for (Map.Entry<String, Hook<WearRenderer>> each : renderers.entrySet()) {
            if (!each.getValue().off) out.put(each.getKey(), each.getValue().hook);
        }
        return Collections.unmodifiableMap(out);
    }

    /** One line saying what was loaded, or null when no mod declared a plugin. */
    public synchronized String summary() {
        if (plugins.isEmpty()) return null;
        int loaded = 0;
        for (Plugin plugin : plugins) if (!plugin.off) loaded++;
        return PREFIX + "trmtr-api "
            + TrmtrApi.VERSION
            + ": "
            + count(loaded, "plugin")
            + " loaded, "
            + (plugins.size() - loaded)
            + " not; "
            + count(listeners.size(), "listener")
            + ", "
            + count(groundRules.size(), "ground rule")
            + ", "
            + count(wearerRules.size(), "wearer rule")
            + ", "
            + count(renderers.size(), "renderer")
            + ", "
            + count(data.size(), "data file");
    }

    private static String count(int n, String what) {
        return n + " " + what + (n == 1 ? "" : "s");
    }

    /** Says a line once a session by its key, up to {@link #MOST_SAID} lines; true when it was said now. */
    public boolean sayOnce(String key, String line) {
        synchronized (said) {
            if (said.contains(key)) return false;
            if (said.size() >= MOST_SAID) return false;
            said.add(key);
        }
        log.warn(line, null);
        return true;
    }

    // ------------------------------------------------------------------
    // Answers that are the same in every edition
    // ------------------------------------------------------------------

    /**
     * Whether a version {@code major.minor.patch} is at least {@code major.minor}; false for one that does not read.
     */
    public static boolean atLeast(String version, int major, int minor) {
        if (version == null) return false;
        String[] parts = version.split("\\.");
        if (parts.length < 2) return false;
        try {
            int ownMajor = Integer.parseInt(parts[0]);
            int ownMinor = Integer.parseInt(parts[1]);
            return ownMajor > major || (ownMajor == major && ownMinor >= minor);
        } catch (NumberFormatException unread) {
            return false;
        }
    }

    /**
     * The family every one of a block's metadata values is in, or null when they are not all in one (API.md, "Asking
     * about a block on 1.7.10"): a block whose values differ has no one answer, and none is made up for it.
     *
     * @param byMeta each metadata value's family key, null where that value does not wear
     */
    public static String sharedFamily(String[] byMeta) {
        if (byMeta == null || byMeta.length == 0) return null;
        String first = byMeta[0];
        for (String each : byMeta) {
            if (first == null ? each != null : !first.equals(each)) return null;
        }
        return first;
    }

    /** Whether a block's metadata values are in more than one family, counting not wearing as one of them. */
    public static boolean mixedFamilies(String[] byMeta) {
        if (byMeta == null || byMeta.length == 0) return false;
        String first = byMeta[0];
        for (String each : byMeta) {
            if (first == null ? each != null : !first.equals(each)) return true;
        }
        return false;
    }

    /** The line said once when a block's metadata values differ: which forms answer instead. */
    public static String formsLine(String block) {
        return PREFIX + "familyOf was asked about the block "
            + block
            + ", whose metadata values are not all in one family, so it answered null. Ask about one value with its"
            + " metadata, familyOf(\""
            + block
            + ":0\") to familyOf(\""
            + block
            + ":15\"), or where the block stands, with familyAt and chainLengthAt";
    }
}
