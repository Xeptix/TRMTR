package com.trmtgtnh.erosion;

import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.trmtgtnh.util.UpdateCheck;

/**
 * A world's wear backups (0.9.229; docs/goals/0.9.229-wear-backup-spec.md, "Backups"): what each is called, what its
 * manifest says, which go when there are too many or they take too much room, and when the next is due. Portable, the
 * same file in every edition; the edition makes the files and asks this which to make and which to delete.
 *
 * <p>
 * Every choice here is Xep's (2026-11-15): one backup five minutes after the server or world starts when the last is
 * older than the interval, then one every interval of play, 6 hours and 5 kept by default ("Boot + every 6h"); one
 * before a mod update and one before a command that clears wear in bulk; a disk limit per world, 256 MB by default and
 * configurable, past which the oldest go - never the newest, and never the one taken before a mod update.
 */
public final class WearBackupSet {

    /** The backups' folder inside the mod's own folder in the world, beside the mirror's. */
    public static final String FOLDER = "backups";

    /** Why a backup was taken, as its name and manifest say. */
    public static final String SCHEDULED = "scheduled";

    /** Before the first load of the world by a different version of this mod, before anything touches the wear. */
    public static final String UPDATE = "update";

    /** Before a command that clears or resets wear in bulk. */
    public static final String COMMAND = "command";

    /** Just before a restore, so the restore can be undone. */
    public static final String RESTORE = "restore";

    /** The reasons a name may carry, in no particular order. */
    static final String[] REASONS = { SCHEDULED, UPDATE, COMMAND, RESTORE };

    /** The defaults, as the settings declare them. */
    public static final int DEFAULT_HOURS = 6;

    public static final int DEFAULT_KEEP = 5;

    public static final int DEFAULT_DISK_MB = 256;

    /** How long the server or world runs before the boot backup, in ticks: five minutes. */
    public static final int WARM_UP_TICKS = 5 * 60 * 20;

    /**
     * The system property that shortens the interval, in seconds, for a test run alone (see {@link #testSeconds}).
     */
    public static final String TEST_SECONDS = "trmt.wearBackup.testSeconds";

    /** The prefix of every harness switch, which is what lets {@link #TEST_SECONDS} be read. */
    static final String HARNESS = "trmt.spike.";

    private WearBackupSet() {}

    // ------------------------------------------------------------------
    // One backup
    // ------------------------------------------------------------------

    /** A backup, as its file's name and manifest describe it. */
    public static final class Backup {

        /** The file's name in the backups folder, which is also what a command names it by. */
        public final String name;

        /** When it was taken, in milliseconds since 1970. */
        public final long made;

        public final String reason;

        /** The version of this mod that took it. */
        public final String version;

        /** The world clock when it was taken, in ticks, so a restore knows how long the ground has had to heal. */
        public final long ticks;

        /** How many chunks it holds wear for. */
        public final int chunks;

        /** Its size on disk, in bytes. */
        public final long bytes;

        /**
         * The world's play when it was taken, in ticks of the server running ({@link State#played}), so the backup a
         * restore took first is kept for seven days of play (see {@link #kept}).
         */
        public final long played;

        public Backup(String name, long made, String reason, String version, long ticks, int chunks, long bytes) {
            this(name, made, reason, version, ticks, chunks, bytes, 0L);
        }

        public Backup(String name, long made, String reason, String version, long ticks, int chunks, long bytes,
            long played) {
            this.name = name;
            this.made = made;
            this.reason = reason;
            this.version = version;
            this.ticks = ticks;
            this.chunks = chunks;
            this.bytes = bytes;
            this.played = played;
        }

        /** This backup on disk at another size, as the folder listing finds it. */
        public Backup sized(long size) {
            return new Backup(name, made, reason, version, ticks, chunks, size, played);
        }

        /** The manifest's text: one {@code key=value} a line. */
        public String manifest() {
            StringBuilder out = new StringBuilder();
            out.append("format=1\n");
            out.append("made=")
                .append(made)
                .append('\n');
            out.append("reason=")
                .append(reason)
                .append('\n');
            out.append("version=")
                .append(version == null ? "" : version)
                .append('\n');
            out.append("ticks=")
                .append(ticks)
                .append('\n');
            out.append("chunks=")
                .append(chunks)
                .append('\n');
            out.append("played=")
                .append(played)
                .append('\n');
            return out.toString();
        }

        /** A backup from its manifest, or null when the manifest is not one; the size is the caller's to give. */
        public static Backup fromManifest(String name, String text, long bytes) {
            Properties read = new Properties();
            try {
                read.load(new StringReader(text == null ? "" : text));
            } catch (IOException never) {
                return null;
            }
            if (!"1".equals(read.getProperty("format"))) return null;
            try {
                return new Backup(
                    name,
                    Long.parseLong(read.getProperty("made", "")),
                    read.getProperty("reason", SCHEDULED),
                    read.getProperty("version", ""),
                    Long.parseLong(read.getProperty("ticks", "0")),
                    Integer.parseInt(read.getProperty("chunks", "0")),
                    bytes,
                    Long.parseLong(read.getProperty("played", "0")));
            } catch (NumberFormatException notOne) {
                return null;
            }
        }
    }

    /** {@code wear-20261115-174301-scheduled.zip}: the time it was taken, in UTC, and why. */
    private static final Pattern NAME = Pattern.compile("wear-(\\d{8}-\\d{6})(?:-(\\d+))?-([a-z]+)\\.zip");

    /**
     * The name for a backup taken at this moment for this reason, in UTC so a server's backups sort the same whatever
     * its clock's zone. A second backup in the same second takes the next free number after the time.
     */
    public static String nameFor(long millis, String reason, Collection<String> taken) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        String stamp = format.format(new Date(millis));
        String name = "wear-" + stamp + "-" + reason + ".zip";
        for (int next = 2; taken != null && taken.contains(name); next++) {
            name = "wear-" + stamp + "-" + next + "-" + reason + ".zip";
        }
        return name;
    }

    /** Whether a file in the backups folder is named as a backup is. */
    public static boolean isBackupName(String name) {
        return name != null && NAME.matcher(name)
            .matches();
    }

    /** The reason a backup's name carries, or null. */
    public static String reasonOf(String name) {
        Matcher found = NAME.matcher(name == null ? "" : name);
        return found.matches() ? found.group(3) : null;
    }

    // ------------------------------------------------------------------
    // Which there are, and which go
    // ------------------------------------------------------------------

    /** Newest first; two taken in the same millisecond by name, so the order never depends on a listing's. */
    public static List<Backup> newestFirst(Collection<Backup> backups) {
        List<Backup> out = new ArrayList<Backup>(backups);
        Collections.sort(out, new Comparator<Backup>() {

            @Override
            public int compare(Backup a, Backup b) {
                if (a.made != b.made) return a.made > b.made ? -1 : 1;
                return b.name.compareTo(a.name);
            }
        });
        return out;
    }

    /** The newest backup, or null with none. */
    public static Backup newest(Collection<Backup> backups) {
        List<Backup> sorted = newestFirst(backups);
        return sorted.isEmpty() ? null : sorted.get(0);
    }

    /** A backup by its name, or null. */
    public static Backup named(Collection<Backup> backups, String name) {
        for (Backup each : backups) {
            if (each.name.equals(name)) return each;
        }
        return null;
    }

    /** How long the backup a restore took first is kept from pruning: seven days of play, in ticks. */
    public static final long UNDO_KEPT_TICKS = 7L * 24L * 3600L * 20L;

    /**
     * The backups that may not be deleted: the newest; the newest taken before a mod update, because it is the one copy
     * of the wear as the version before this one left it; and the newest taken before a restore, which its undo stands
     * on, until another restore takes its place or seven days of play have gone by (Xep's choice, after phase 1).
     *
     * @param played the world's play now, in ticks ({@link State#played})
     */
    public static Set<String> kept(Collection<Backup> backups, long played) {
        Set<String> out = new LinkedHashSet<String>();
        List<Backup> sorted = newestFirst(backups);
        if (!sorted.isEmpty()) out.add(sorted.get(0).name);
        for (Backup each : sorted) {
            if (UPDATE.equals(each.reason)) {
                out.add(each.name);
                break;
            }
        }
        for (Backup each : sorted) {
            if (RESTORE.equals(each.reason)) {
                if (played - each.played < UNDO_KEPT_TICKS) out.add(each.name);
                break;
            }
        }
        return out;
    }

    /**
     * Which backups to delete, oldest first: while more than {@code keep} are left, or they take more than
     * {@code limitBytes} between them, the oldest that may go goes. Those {@link #kept} are never on the list, so a
     * limit too small for them leaves them and nothing else.
     */
    public static List<Backup> toPrune(Collection<Backup> backups, int keep, long limitBytes, long played) {
        List<Backup> oldestFirst = newestFirst(backups);
        Collections.reverse(oldestFirst);
        Set<String> kept = kept(backups, played);
        int count = oldestFirst.size();
        long total = 0L;
        for (Backup each : oldestFirst) total += Math.max(0L, each.bytes);
        List<Backup> out = new ArrayList<Backup>();
        for (Backup each : oldestFirst) {
            if (count <= Math.max(1, keep) && total <= limitBytes) break;
            if (kept.contains(each.name)) continue;
            out.add(each);
            count--;
            total -= Math.max(0L, each.bytes);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // When the next is due
    // ------------------------------------------------------------------

    /**
     * Whether the backup five minutes after the start is due: there is none yet, or the newest is older than the
     * interval by the clock on the wall. A clock that has gone backwards counts the backup as new.
     */
    public static boolean dueAtStart(Backup newest, long nowMillis, long intervalSeconds) {
        if (newest == null) return true;
        return nowMillis - newest.made >= intervalSeconds * 1000L;
    }

    /** Whether a backup is due after this many ticks of play since the last (or since the start, with none taken). */
    public static boolean dueInPlay(long ticksSinceLast, long intervalSeconds) {
        return ticksSinceLast >= intervalSeconds * 20L;
    }

    /**
     * The interval, in seconds: the hours setting, or the test interval where a test run asks for one.
     *
     * <p>
     * The test interval ({@link #TEST_SECONDS}) is read only in a development game or under the test harness, so a
     * player cannot set a world backing up every few seconds by a stray property: the settings take whole hours, from
     * one up.
     */
    public static long intervalSeconds(int hours, Map<?, ?> properties, boolean development) {
        long test = testSeconds(properties, development);
        return test > 0 ? test : Math.max(1, hours) * 3600L;
    }

    /** The test interval in seconds, or 0 where none may be given. */
    public static long testSeconds(Map<?, ?> properties, boolean development) {
        if (properties == null) return 0L;
        if (!development && !harness(properties)) return 0L;
        Object given = properties.get(TEST_SECONDS);
        if (given == null) return 0L;
        try {
            long seconds = Long.parseLong(
                given.toString()
                    .trim());
            return seconds > 0 ? seconds : 0L;
        } catch (NumberFormatException notOne) {
            return 0L;
        }
    }

    /** The system property that lowers the quiet threshold for a test run alone, read as {@link #TEST_SECONDS} is. */
    public static final String TEST_QUIET = "trmt.wearBackup.testQuietMost";

    /**
     * The quiet threshold ({@link WearLoss#QUIET_MOST}), or a lower one a development game or a harness run asks for,
     * so a played check can cross it with a world of a few worn chunks.
     */
    public static int quietMost(Map<?, ?> properties, boolean development) {
        if (properties == null || !development && !harness(properties)) return WearLoss.QUIET_MOST;
        Object given = properties.get(TEST_QUIET);
        if (given == null) return WearLoss.QUIET_MOST;
        try {
            int most = Integer.parseInt(
                given.toString()
                    .trim());
            return most >= 0 ? most : WearLoss.QUIET_MOST;
        } catch (NumberFormatException notOne) {
            return WearLoss.QUIET_MOST;
        }
    }

    /** How long the start's backup waits: five minutes, or, under a test interval, the interval if it is shorter. */
    public static long warmUpTicks(long intervalSeconds) {
        return Math.min(WARM_UP_TICKS, intervalSeconds * 20L);
    }

    private static boolean harness(Map<?, ?> properties) {
        for (Map.Entry<?, ?> each : properties.entrySet()) {
            if (String.valueOf(each.getKey())
                .startsWith(HARNESS) && "true".equalsIgnoreCase(String.valueOf(each.getValue()))) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // What the world remembers between sessions
    // ------------------------------------------------------------------

    /**
     * The backups' own record in the world ({@code <world>/trmtgtnh/state.properties}): which version of this mod last
     * opened the world, the world clock at this mod's last save, whether the player said not to be asked again, and the
     * operators' notice of the last automatic restore with whom it has been told to.
     */
    public static final class State {

        /** The version of this mod that last opened the world; empty for a world it never has. */
        public String version = "";

        /** The world clock at this mod's last save of the world, in ticks; -1 when it has never saved one. */
        public long saved = -1L;

        /** Ticks the server has run with this world, all sessions together: the clock of "days of play". */
        public long played;

        /**
         * The mirror is still to be built from the region files: a world from before wear backups, whose first load
         * stopped before it was done.
         */
        public boolean seedPending;

        /** Set by "Don't ask again for this world". */
        public boolean dontAsk;

        /** The notice operators are given as they join, after an automatic restore; empty with none. */
        public String notice = "";

        /** Who has been given it, by name. */
        public final Set<String> told = new LinkedHashSet<String>();

        public String text() {
            StringBuilder out = new StringBuilder();
            Properties props = new Properties();
            props.setProperty("version", version);
            props.setProperty("saved", Long.toString(saved));
            props.setProperty("played", Long.toString(played));
            props.setProperty("dontAsk", Boolean.toString(dontAsk));
            props.setProperty("seed", seedPending ? "pending" : "done");
            props.setProperty("notice", notice);
            StringBuilder names = new StringBuilder();
            for (String name : told) {
                if (names.length() > 0) names.append(',');
                names.append(name);
            }
            props.setProperty("told", names.toString());
            java.io.StringWriter written = new java.io.StringWriter();
            try {
                props.store(written, "TRMT: Reimagined's wear backups");
            } catch (IOException never) {
                throw new IllegalStateException(never);
            }
            // Properties writes a date line after the comment; it is left out, so the file changes only when its
            // contents do.
            for (String line : written.toString()
                .split("\r?\n")) {
                if (line.startsWith("#") && !line.startsWith("# TRMT")) continue;
                out.append(line)
                    .append('\n');
            }
            return out.toString();
        }

        public static State from(String text) {
            State out = new State();
            if (text == null) return out;
            Properties props = new Properties();
            try {
                props.load(new StringReader(text));
            } catch (IOException never) {
                return out;
            }
            out.version = props.getProperty("version", "");
            try {
                out.saved = Long.parseLong(props.getProperty("saved", "-1"));
                out.played = Long.parseLong(props.getProperty("played", "0"));
            } catch (NumberFormatException notOne) {
                out.saved = -1L;
            }
            out.dontAsk = "true".equalsIgnoreCase(props.getProperty("dontAsk", "false"));
            out.seedPending = "pending".equals(props.getProperty("seed", "done"));
            out.notice = props.getProperty("notice", "");
            for (String name : props.getProperty("told", "")
                .split(",")) {
                if (!name.trim()
                    .isEmpty()) out.told.add(name.trim());
            }
            return out;
        }
    }

    /**
     * How a world's last opening compares with this build: {@code 0} the same version (or a world this mod has never
     * opened), {@code 1} an update (the world was last opened by an older version, or one this build cannot read as a
     * version), {@code -1} the world was last opened by a newer version than this one.
     */
    public static int versionChange(String last, String running) {
        if (last == null || last.trim()
            .isEmpty() || last.equals(running)) return 0;
        try {
            return UpdateCheck.compare(last, running) > 0 ? -1 : 1;
        } catch (IllegalArgumentException notAVersion) {
            return 1;
        }
    }
}
