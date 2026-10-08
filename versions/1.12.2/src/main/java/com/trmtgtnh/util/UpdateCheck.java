package com.trmtgtnh.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whether a newer release of this mod is out: the half of the update notice that is the same in every
 * edition, so it is written once and held identical by the drift check.
 *
 * <p>
 * Asked for on 2026-10-07 and planned in {@code docs/UPDATE-NOTICE-PLAN.md}: a player who can update
 * the mod is told, as they join, when a newer release exists for the edition they are running. The
 * newest version of each edition is one line in {@code latest.properties} at the root of the public
 * repository, which the release tools write; a server reads it once a launch, and again when an answer
 * is more than a day old. Nothing is sent but the request itself.
 *
 * <p>
 * <strong>The file only ever supplies numbers.</strong> A value is shown to nobody unless it is a bare
 * version - one to four numbers, twenty characters at most - and the links the notice offers are
 * {@link #CURSEFORGE} and {@link #MODRINTH}, held here in the jar. Whatever happens to that file, it
 * cannot put words, formatting or an address into anybody's chat.
 *
 * <p>
 * <strong>A development build is never told.</strong> A released jar's version is a bare number - the
 * release checks refuse anything else - and a development build's is not:
 * {@code 0.9.218-master.5+04de10a08b-dirty} on 1.7.10, {@code 0.9.218-3-g1a2b3c4-dirty} on 1.12.2.
 * The test address ({@link #TEST_ADDRESS}) is the one exception, and there a development build counts
 * as the release it was built from, because that is the only way to see the notice before a real
 * release exists.
 *
 * <p>
 * Never throws, never blocks the thread that asks, and never retries: one request, five seconds to
 * connect and five to read, and any failure - no network, a refusal, a Java too old to trust the
 * certificate - is an answer that says so, for one line in the log, and tells nobody anything.
 *
 * <p>
 * Portable: no Minecraft and no logging framework. What it found comes back as words for the loader's
 * own log, and who is told is the loader's business.
 */
public final class UpdateCheck {

    /**
     * Where every released jar reads the newest versions from.
     *
     * <p>
     * <strong>Permanent.</strong> Every jar from 0.9.219 on asks this address for as long as it is
     * played, so moving the file, renaming the repository or changing its branch would silence every
     * one of them at once.
     */
    public static final String ADDRESS = "https://raw.githubusercontent.com/Xeptix/TRMTR/master/latest.properties";

    /** The two project pages the notice links to. In the jar, so the file can never send anybody elsewhere. */
    public static final String CURSEFORGE = "https://www.curseforge.com/minecraft/mc-mods/trmt-reimagined";

    public static final String MODRINTH = "https://modrinth.com/mod/trmtr";

    /**
     * A system property naming another address to read instead - a {@code file:} one for a test - which
     * also lifts the harness and development gates and lets a development build be told.
     */
    public static final String TEST_ADDRESS = "trmt.update.url";

    /** Every test harness switch begins with this, in every edition, and any of them set means no request. */
    static final String HARNESS = "trmt.spike";

    /** How long the connection, and then the read, may each take. */
    static final int TIMEOUT_MS = 5000;

    /** How much of an answer is read at most. The real file is a few hundred bytes. */
    static final int LONGEST_ANSWER = 4096;

    /** How long an answer is trusted before an eligible join asks again: a day, for servers that run for weeks. */
    public static final long STALE_AFTER_MS = 24L * 60L * 60L * 1000L;

    /** The words the notice is built from: newest, running, and the two link names, in that order. */
    public static final int PLACES = 4;

    /** A version that may be shown to somebody. */
    private static final Pattern BARE = Pattern.compile("[0-9]{1,6}(\\.[0-9]{1,6}){0,3}");

    /** The release a development build was made from, read off its front. */
    private static final Pattern LEADING = Pattern.compile("^[0-9]{1,6}(\\.[0-9]{1,6}){0,3}");

    /** The last answer, or null before the first one arrives. */
    private static volatile Answer last;

    /** Whether a check is out now, so two joins in one second do not send two requests. */
    private static final AtomicBoolean ASKING = new AtomicBoolean();

    private UpdateCheck() {}

    /** What one check found. */
    public static final class Answer {

        /** The newer version to tell an eligible player about, or null when there is nothing to tell. */
        public final String newer;

        /** The version it was compared with - this jar's, or the release a test build counts as. */
        public final String running;

        /** What happened, in words, for the one line a check writes to the log. */
        public final String said;

        /** When it was found, in {@link System#currentTimeMillis()} terms. */
        public final long at;

        Answer(String newer, String running, String said, long at) {
            this.newer = newer;
            this.running = running;
            this.said = said;
            this.at = at;
        }
    }

    /**
     * Whether this game may ask at all.
     *
     * <p>
     * Not when the setting is off - and then no request is made, ever. Not in a development environment
     * or under the test harness either, unless the test address is given: a harness run is a test of
     * something else, and a development run reports versions that are not releases.
     *
     * @param setting     the {@code general.updateNotice} setting: {@code operators}, {@code everyone} or
     *                    {@code off}
     * @param development whether the loader says this is a development environment
     * @param properties  the system properties, or a stand-in for them in a test
     */
    public static boolean mayAsk(String setting, boolean development, Map<?, ?> properties) {
        return whyNot(setting, development, properties) == null;
    }

    /**
     * Why this game will not ask, in words for the one line a server writes as it starts, or null when it
     * may - the same rules as {@link #mayAsk}, which answers from this.
     *
     * <p>
     * Until 0.9.219 a game that did not ask said nothing, so a server with the notice switched off could not
     * be told from one whose check had never been wired up.
     */
    public static String whyNot(String setting, boolean development, Map<?, ?> properties) {
        if ("off".equalsIgnoreCase(setting == null ? "" : setting.trim())) {
            return "general.updateNotice is off, so no request is made";
        }
        if (testAddress(properties) != null) return null;
        if (development) return "this is a development game, which never asks";
        return harness(properties) ? "a test harness is running, which never asks" : null;
    }

    /**
     * Starts one check on a daemon thread, unless one is already out. The answer lands in {@link #last()},
     * and {@code then}, if given, runs on that thread once it has - for telling whoever joined while the
     * request was out.
     *
     * @param edition    this jar's line in the file, {@code <game>-<loader>}: {@code 1.16.5-fabric}
     * @param running    this jar's own version
     * @param properties the system properties, or a stand-in for them in a test
     */
    public static void start(final String edition, final String running, final Map<?, ?> properties,
        final Runnable then) {
        if (!ASKING.compareAndSet(false, true)) return;
        final String test = testAddress(properties);
        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    last = ask(test != null ? test : ADDRESS, edition, running, test != null);
                } finally {
                    ASKING.set(false);
                }
                if (then != null) then.run();
            }
        }, "TRMT update check");
        thread.setDaemon(true);
        thread.start();
    }

    /** The last answer, or null if no check has finished yet. */
    public static Answer last() {
        return last;
    }

    /** Whether there is no answer yet, or the one there is has had its day. */
    public static boolean due(long now) {
        Answer held = last;
        return held == null || now - held.at > STALE_AFTER_MS;
    }

    /** One whole check, on whatever thread calls it. Never throws. */
    static Answer ask(String address, String edition, String running, boolean testing) {
        long now = System.currentTimeMillis();
        String mine = testing ? leading(running) : running;
        String body;
        try {
            body = fetch(address);
        } catch (IOException e) {
            return new Answer(null, mine, "could not read " + where(address) + " (" + e + ")", now);
        } catch (RuntimeException e) {
            return new Answer(null, mine, "could not read " + where(address) + " (" + e + ")", now);
        }
        String newest = newestFor(body, edition);
        if (newest == null) return new Answer(null, mine, "no line for " + edition + " in " + where(address), now);
        if (!trusted(newest)) {
            return new Answer(null, mine, "the line for " + edition + " is not a version, so nothing is shown", now);
        }
        if (!trusted(mine)) {
            return new Answer(null, mine, "this is a development build (" + running + "), which is never told", now);
        }
        if (compare(newest, mine) <= 0)
            return new Answer(null, mine, "up to date: " + mine + ", newest " + newest, now);
        return new Answer(newest, mine, newest + " is out, and this is " + mine, now);
    }

    /** The answer's text, at most {@link #LONGEST_ANSWER} bytes of it. */
    static String fetch(String address) throws IOException {
        URLConnection connection = new URL(address).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setUseCaches(false);
        if (connection instanceof HttpURLConnection) {
            int code = ((HttpURLConnection) connection).getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
        }
        InputStream in = connection.getInputStream();
        try {
            byte[] held = new byte[LONGEST_ANSWER];
            int filled = 0;
            for (int read; filled < held.length && (read = in.read(held, filled, held.length - filled)) > 0;) {
                filled += read;
            }
            return new String(held, 0, filled, Charset.forName("UTF-8"));
        } finally {
            in.close();
        }
    }

    /** This edition's line in the file, trimmed, or null if it has none or the file cannot be read as one. */
    static String newestFor(String body, String edition) {
        Properties read = new Properties();
        try {
            read.load(new StringReader(body == null ? "" : body));
        } catch (IOException e) {
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
        String value = read.getProperty(edition);
        return value == null ? null : value.trim();
    }

    /** Whether a version may be shown to somebody: a bare one, of one to four numbers, twenty characters at most. */
    public static boolean trusted(String version) {
        return version != null && version.length() <= 20
            && BARE.matcher(version)
                .matches();
    }

    /** The release a development build was made from, or null if it does not start with one. */
    static String leading(String version) {
        Matcher found = LEADING.matcher(version == null ? "" : version);
        return found.find() ? found.group() : null;
    }

    /**
     * Two bare versions, number by number: {@code 0.10.0} is newer than {@code 0.9.999}, and a missing number is a
     * nought.
     */
    public static int compare(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int at = 0; at < Math.max(x.length, y.length); at++) {
            long p = at < x.length ? Long.parseLong(x[at]) : 0L;
            long q = at < y.length ? Long.parseLong(y[at]) : 0L;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    /**
     * The notice's sentence cut at its {@link #PLACES} places, so the two links can be put back as links
     * - five pieces, or null when a translation does not have exactly four {@code %s} in it, and the
     * caller falls back to the words it carries itself.
     */
    public static String[] pieces(String pattern) {
        if (pattern == null) return null;
        List<String> out = new ArrayList<String>();
        int from = 0;
        for (int at = pattern.indexOf("%s"); at >= 0; at = pattern.indexOf("%s", from)) {
            out.add(pattern.substring(from, at));
            from = at + 2;
        }
        out.add(pattern.substring(from));
        return out.size() == PLACES + 1 ? out.toArray(new String[PLACES + 1]) : null;
    }

    /** Whether any test harness switch is on. */
    static boolean harness(Map<?, ?> properties) {
        for (Map.Entry<?, ?> each : properties.entrySet()) {
            if (String.valueOf(each.getKey())
                .startsWith(HARNESS) && "true".equalsIgnoreCase(String.valueOf(each.getValue()))) return true;
        }
        return false;
    }

    /** The test address, if one is given. */
    static String testAddress(Map<?, ?> properties) {
        Object given = properties.get(TEST_ADDRESS);
        String said = given == null ? ""
            : given.toString()
                .trim();
        return said.isEmpty() ? null : said;
    }

    /** Where an address points, said briefly - the host for a web address, the address itself otherwise. */
    private static String where(String address) {
        try {
            String host = new URL(address).getHost();
            return host == null || host.isEmpty() ? address : host;
        } catch (IOException e) {
            return address;
        }
    }
}
