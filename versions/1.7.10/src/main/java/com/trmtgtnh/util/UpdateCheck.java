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
 * <strong>The file only ever supplies names in one strict shape.</strong> A value is shown to nobody unless
 * it is a build's name - a version of one to four numbers, then optionally a stage of {@link #STAGES} with its
 * number and an edition's own stage, {@value #LONGEST_NAME} characters at most - and the links the notice
 * offers are {@link #CURSEFORGE} and {@link #MODRINTH}, held here in the jar. Every word that can appear is in
 * this class, so whatever happens to that file, it cannot put words, formatting or an address into
 * anybody's chat.
 *
 * <p>
 * <strong>Each stage reads its own line</strong> (Xep, 2026-10-08). A project may publish builds at a stage
 * before a release - {@code 1.0.0-alpha.2}, {@code 0.9.231-nightly.20261008}, {@code 1.0.0-beta.1} - or hold
 * one edition of a release at a stage, {@code 0.9.230-alpha} beside {@code 0.9.230}. A jar reads
 * {@code <edition>.<stage>} for the least stable stage in its own name, which the release tools keep at the
 * newest build at that stage or a more stable one: alpha players hear of newer alphas, nightlies, betas and
 * releases, beta players of betas and releases, release players of releases only. A file without that line
 * yet is read at the edition's own.
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
 * <strong>An early build is told when the build it previews is out.</strong> From 0.9.220 a build can go
 * to supporters first as {@code 0.9.220-snapshot.2} (or {@code 1.0.0-alpha.2-snapshot.1}), and become public
 * days later. That jar counts as just below the build it previews: {@code 0.9.220} published is an update
 * for it, {@code 0.9.219} is not. Only the exact grammar gets this; anything else after the version is still
 * a development build. TRMT names its early builds snapshots from 2026-10-08; {@code -early.} is read too,
 * because it was the name before. The file itself never names an early build, so early testers are told at
 * the same moment as everybody else.
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

    /**
     * The stages a build may be at before its release, least stable first - this project's, from its
     * decisions (Master Release Pipeline, section 18; {@code check_tools.py} holds this list to them).
     */
    static final String[] STAGES = { "alpha", "nightly", "beta" };

    /** The stages numbered by their UTC date ({@code 0.9.231-nightly.20261008}) rather than counted. */
    static final String[] DATED = { "nightly" };

    /** The words an early build may carry: the project's own, then the ones a build may ask for instead. */
    static final String[] EARLY_WORDS = { "snapshot", "early" };

    /**
     * Whether a pre-release player hears of newer builds at their stage (Xep, 2026-10-08: yes) - read from
     * the stage's own line - or only of releases.
     */
    static final boolean BY_STAGE = true;

    /** The longest name that may be shown to anybody. */
    static final int LONGEST_NAME = 48;

    /** What a line marking a build bad starts with: {@code bad.0.9.221=0.9.220}. */
    static final String BAD = "bad.";

    /**
     * A build's name, and nothing else: the version, then a stage and its number, then an edition's own
     * stage, then an early word and its number - each part optional, in that order.
     */
    private static final Pattern NAME = Pattern.compile(
        "([0-9]{1,6}(?:\\.[0-9]{1,6}){0,3})" + "(?:-("
            + any(STAGES)
            + ")\\.([0-9]{1,8}(?:\\.[0-9]{1,3})?))?"
            + "(?:-("
            + any(STAGES)
            + "))?"
            + "(?:-("
            + any(EARLY_WORDS)
            + ")\\.([0-9]{1,4}))?");

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

        /**
         * Whether the build this jar is has been marked bad (Xep, 2026-10-08): then {@link #newer} is the build to
         * use instead - which may be older - and the notice says the running one has known issues.
         */
        public final boolean bad;

        Answer(String newer, String running, String said, long at) {
            this(newer, running, said, at, false);
        }

        Answer(String newer, String running, String said, long at, boolean bad) {
            this.newer = newer;
            this.running = running;
            this.said = said;
            this.at = at;
            this.bad = bad;
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
        String early = earlyOf(running);
        String mine = parse(running) == null && testing ? leading(running) : running;
        String body;
        try {
            body = fetch(address);
        } catch (IOException e) {
            return new Answer(null, mine, "could not read " + where(address) + " (" + e + ")", now);
        } catch (RuntimeException e) {
            return new Answer(null, mine, "could not read " + where(address) + " (" + e + ")", now);
        }
        if (mine == null || parse(mine) == null) {
            return new Answer(null, mine, "this is a development build (" + running + "), which is never told", now);
        }
        // A pre-release reads its own stage's line, which holds the newest build at that stage or a more
        // stable one; a file with no such line yet is read at the edition's own, the newest release.
        String line = lineFor(edition, mine);
        String newest = newestFor(body, line);
        if (newest == null && !line.equals(edition)) newest = newestFor(body, line = edition);
        if (newest == null) return new Answer(null, mine, "no line for " + edition + " in " + where(address), now);
        if (!trusted(newest)) {
            return new Answer(null, mine, "the line for " + line + " is not a version, so nothing is shown", now);
        }
        if (compare(newest, mine) <= 0) {
            // Nothing newer - but this very build may have been marked bad since it went out (Xep, 2026-10-08):
            // then the file names the build to use instead, `bad.<this build>=<that build>`, and the player is
            // told so. A newer build, the fix, is offered by the line above before this is ever asked.
            String better = early == null ? newestFor(body, BAD + mine) : null;
            if (better != null && trusted(better) && !better.equals(mine)) {
                return new Answer(
                    better,
                    mine,
                    mine + " has known issues, and " + better + " is the build to use",
                    now,
                    true);
            }
            // An early build sorts just below the build it previews, so that build is the update it waits for.
            return new Answer(
                null,
                mine,
                "up to date: " + mine + (early != null ? " is an early build of " + early : "") + ", newest " + newest,
                now);
        }
        return new Answer(
            newest,
            mine,
            newest + " is out, and this is " + (early != null ? "the early build " : "") + mine,
            now);
    }

    /**
     * The line of the file a jar of this name reads: {@code <edition>.<stage>} for the least stable stage in
     * its name, or {@code <edition>} for a release - and always {@code <edition>} when pre-release players
     * are told of releases only.
     */
    static String lineFor(String edition, String name) {
        Parsed p = parse(name);
        if (p == null || !BY_STAGE) return edition;
        int least = Math.min(p.stage, p.estage);
        return least < STAGES.length ? edition + "." + STAGES[least] : edition;
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

    /**
     * Whether a value from the file may be shown to somebody: a build's name in this project's grammar, of
     * at most {@link #LONGEST_NAME} characters, and never an early build's - the file never names one, so
     * an early tester is told at the same moment as everybody else. Digits, dots, hyphens and the words
     * held in this class, and nothing else, so the file cannot put words, formatting or an address into
     * anybody's chat.
     */
    public static boolean trusted(String version) {
        Parsed p = parse(version);
        return p != null && !p.early;
    }

    /**
     * The build an early build leads to - {@code 0.9.220} for {@code 0.9.220-snapshot.2}, {@code 1.0.0-alpha.2}
     * for {@code 1.0.0-alpha.2-snapshot.1} - or null if it is not one.
     */
    static String earlyOf(String version) {
        Parsed p = parse(version);
        return p != null && p.early ? p.target : null;
    }

    /** One build's name, taken apart. */
    static final class Parsed {

        /** The name without its early part: the build an early build previews, or the name itself. */
        String target;
        long[] version = new long[4];
        /** The build's stage, least stable first; {@code STAGES.length} for none. */
        int stage;
        long[] number = new long[2];
        /** The edition's own stage, appended when it is less stable than its build's. */
        int estage;
        boolean early;
        int k;
    }

    /** A build's name taken apart, or null if it is not one - a development build's never is. */
    static Parsed parse(String name) {
        if (name == null || name.length() > LONGEST_NAME) return null;
        Matcher m = NAME.matcher(name);
        if (!m.matches()) return null;
        Parsed p = new Parsed();
        String[] numbers = m.group(1)
            .split("\\.");
        for (int at = 0; at < numbers.length; at++) p.version[at] = Long.parseLong(numbers[at]);
        p.stage = rank(m.group(2));
        p.estage = rank(m.group(4));
        if (m.group(2) != null) {
            String[] n = m.group(3)
                .split("\\.");
            boolean dated = indexOf(DATED, m.group(2)) >= 0;
            // A counted stage is one number; a dated one is its UTC date, with a second number for a second build that
            // day.
            if (dated ? n[0].length() != 8 : n.length != 1 || n[0].length() > 4) return null;
            for (int at = 0; at < n.length; at++) p.number[at] = Long.parseLong(n[at]);
        }
        if (m.group(2) != null && m.group(4) != null && p.estage >= p.stage) return null;
        p.early = m.group(5) != null;
        p.k = p.early ? Integer.parseInt(m.group(6)) : 0;
        int cut = p.early ? name.length() - (m.group(5)
            .length()
            + m.group(6)
                .length()
            + 2) : name.length();
        p.target = name.substring(0, cut);
        return p;
    }

    private static int rank(String stage) {
        int at = indexOf(STAGES, stage);
        return at < 0 ? STAGES.length : at;
    }

    private static int indexOf(String[] words, String word) {
        for (int at = 0; at < words.length; at++) if (words[at].equals(word)) return at;
        return -1;
    }

    private static String any(String[] words) {
        StringBuilder out = new StringBuilder();
        for (String word : words) out.append(out.length() == 0 ? "" : "|")
            .append(word);
        return out.toString();
    }

    /** The release a development build was made from, or null if it does not start with one. */
    static String leading(String version) {
        Matcher found = LEADING.matcher(version == null ? "" : version);
        return found.find() ? found.group() : null;
    }

    /**
     * Two builds' names, in order: the version number by number ({@code 0.10.0} is newer than {@code 0.9.999},
     * a missing number a nought), then a stage below its release in {@link #STAGES}' order, then the stage's
     * number, then an edition's own stage, and an early build just below the build it previews - so
     * {@code 1.0.0-alpha.1 < 1.0.0-alpha.2-snapshot.1 < 1.0.0-alpha.2 < 1.0.0-beta.1 < 1.0.0}.
     */
    public static int compare(String a, String b) {
        Parsed x = parse(a);
        Parsed y = parse(b);
        if (x == null || y == null) throw new IllegalArgumentException("not a build's name: " + (x == null ? a : b));
        for (int at = 0; at < 4; at++)
            if (x.version[at] != y.version[at]) return x.version[at] < y.version[at] ? -1 : 1;
        if (x.stage != y.stage) return x.stage < y.stage ? -1 : 1;
        for (int at = 0; at < 2; at++) if (x.number[at] != y.number[at]) return x.number[at] < y.number[at] ? -1 : 1;
        if (x.estage != y.estage) return x.estage < y.estage ? -1 : 1;
        if (x.early != y.early) return x.early ? -1 : 1;
        return x.k < y.k ? -1 : x.k > y.k ? 1 : 0;
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
