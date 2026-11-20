package com.trmtgtnh.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * What a server says about its players' own settings (0.9.229; Xep, 2026-11-18: "could we add a server side option
 * which forces clients to use the settings they specify for some or all settings?").
 *
 * <p>
 * A server lists settings by their place in the file, {@code client.glowingWear}, each with a value and a strength.
 * Force holds the setting at the server's value for the visit and locks it in the settings menu; suggest applies it at
 * join and leaves the player free to change it back, for the visit only. Anything not listed stays the player's. Any
 * client setting may be listed: every setting under {@code client} but the one the server's own rules already carry
 * ({@code settleOnWornGround}), the pictures preset ({@code presets.quality}), and each family's look
 * ({@code wearPattern}, {@code classicWear} and {@code wearStrength}). Server settings never: they are the server's
 * already, by {@code ServerRules}.
 *
 * <p>
 * Portable, the same file in every edition, and everything that decides anything is here: which settings may be
 * listed, what a value may be, a line of the server's file, the bytes on the wire, and the visit - what each listed
 * setting is held at, what a player's change to a suggested one does, and how many the player is told about. Each
 * edition supplies its settings' shapes ({@link Spec}) and does the applying: the values are put into its live
 * settings for each read and taken out again before anything is saved, so the player's file is never written with a
 * server's value, and leaving reads it back.
 */
public final class ClientSettingRules {

    /** The server's heading for its list, and the list's key under it. */
    public static final String CATEGORY = "clientrules";

    public static final String LIST_KEY = "rules";

    /** The first byte of the encoded rules: what a reader must understand to read the rest. */
    public static final int FORMAT = 1;

    /** The most the encoded rules may take on the wire, well inside a packet in every edition. */
    public static final int MAX_BYTES = 24000;

    /** The longest value a rule may carry, and the longest setting name. */
    public static final int LONGEST_VALUE = 256;

    public static final int LONGEST_ID = 96;

    static final String CLIENT = "client";

    static final String PRESETS = "presets";

    static final String FAMILIES = "families";

    /** The one client setting a server's own rules carry already, so a list never needs it. */
    static final String SERVER_HOLDS = "settleOnWornGround";

    /** The pictures preset, the one preset that is the player's. */
    static final String QUALITY = "quality";

    /** A family's look: what its wear pictures are made from. Read by the client and by nobody else. */
    static final String[] LOOKS = { "wearPattern", "classicWear", "wearStrength" };

    private ClientSettingRules() {}

    // ------------------------------------------------------------------
    // Which settings
    // ------------------------------------------------------------------

    /** How strongly a server holds a setting. */
    public enum Strength {

        /** Held at the server's value for the visit, and locked in the menu. */
        FORCE("force"),
        /** Applied at join; the player may change it back, for the visit only. */
        SUGGEST("suggest");

        public final String word;

        Strength(String word) {
            this.word = word;
        }

        /** The strength a word names, or null for none. */
        public static Strength of(String word) {
            if (word == null) return null;
            String asked = word.trim()
                .toLowerCase(Locale.ROOT);
            for (Strength each : values()) {
                if (each.word.equals(asked)) return each;
            }
            return null;
        }
    }

    /** A setting's place: its heading and key joined by a dot, as the file and the commands write it. */
    public static String id(String category, String key) {
        return category + "." + key;
    }

    /** The heading of a place, everything before its last dot, or null for a name with none. */
    public static String categoryOf(String id) {
        int dot = id == null ? -1 : id.lastIndexOf('.');
        return dot <= 0 ? null : id.substring(0, dot);
    }

    /** The key of a place, everything after its last dot. */
    public static String keyOf(String id) {
        int dot = id == null ? -1 : id.lastIndexOf('.');
        return dot < 0 || dot == id.length() - 1 ? null : id.substring(dot + 1);
    }

    /**
     * Whether a server may list this setting: a client setting, and not the one its own rules already carry. A family's
     * heading is {@code families.<name>}, one level down and no further.
     */
    public static boolean settable(String category, String key) {
        if (category == null || key == null || key.isEmpty()) return false;
        if (CLIENT.equals(category)) return !SERVER_HOLDS.equals(key);
        if (PRESETS.equals(category)) return QUALITY.equals(key);
        String prefix = FAMILIES + ".";
        if (category.startsWith(prefix) && category.length() > prefix.length()
            && category.indexOf('.', prefix.length()) < 0) {
            for (String look : LOOKS) {
                if (look.equals(key)) return true;
            }
        }
        return false;
    }

    /** {@link #settable(String, String)} for a place. */
    public static boolean settable(String id) {
        return settable(categoryOf(id), keyOf(id));
    }

    // ------------------------------------------------------------------
    // What a value may be
    // ------------------------------------------------------------------

    /** The kinds of setting a file holds. */
    public enum Kind {
        BOOLEAN,
        INTEGER,
        DOUBLE,
        STRING
    }

    /**
     * The shape of one setting as an edition holds it: its kind, whether it is a list, its bounds where it has them and
     * the values it takes where it names them. Built by each edition from its own settings, so a value is held to
     * exactly what that edition's file would take.
     */
    public static final class Spec {

        public final Kind kind;

        public final boolean list;

        /** Bounds as the file writes them, or null for none. */
        public final String min;

        public final String max;

        /** The values it takes, or null or empty for any. */
        public final String[] valid;

        public Spec(Kind kind, boolean list, String min, String max, String[] valid) {
            this.kind = kind == null ? Kind.STRING : kind;
            this.list = list;
            this.min = min;
            this.max = max;
            this.valid = valid;
        }
    }

    /** What checking a value found: the value as it will be held, or why it was refused. */
    public static final class Verdict {

        /** The value in its one written form, or null where refused. */
        public final String value;

        /** Why it was refused, in plain words for whoever set it, or null where taken. */
        public final String problem;

        private Verdict(String value, String problem) {
            this.value = value;
            this.problem = problem;
        }

        public boolean taken() {
            return value != null;
        }

        static Verdict take(String value) {
            return new Verdict(value, null);
        }

        static Verdict refuse(String problem) {
            return new Verdict(null, problem);
        }
    }

    /**
     * Checks a value for a setting. Refused: a setting that is not a client setting, one this edition does not have
     * (a null spec), and a value its file would not take. Taken values come back in one written form, so the same
     * value always compares equal: {@code TRUE} is {@code true}, {@code 04} is {@code 4}, a list is its items joined
     * by a comma and a space.
     */
    public static Verdict check(String id, String raw, Spec spec) {
        if (id == null || id.isEmpty() || id.length() > LONGEST_ID)
            return Verdict.refuse("That is not a setting name.");
        if (!settable(id)) {
            String category = categoryOf(id);
            boolean known = category != null
                && (CLIENT.equals(category) || PRESETS.equals(category) || category.startsWith(FAMILIES + "."));
            if (known && SERVER_HOLDS.equals(keyOf(id))) {
                return Verdict.refuse(id + " is already this server's: the server's own rules carry it.");
            }
            return Verdict.refuse(
                id + " is not a client setting. A server may set client.<key>, presets.quality and"
                    + " families.<name>.wearPattern, classicWear and wearStrength.");
        }
        if (spec == null) return Verdict.refuse(id + " is not a setting in this edition.");
        if (raw == null) return Verdict.refuse(id + " needs a value.");
        String value = raw.trim();
        if (value.length() > LONGEST_VALUE) {
            return Verdict.refuse(id + " is given a value longer than " + LONGEST_VALUE + " characters.");
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return Verdict.refuse(id + " is given a value with a control character in it.");
            }
        }
        if (spec.list) {
            List<String> items = new ArrayList<String>();
            for (String part : value.split(",")) {
                String item = part.trim();
                if (item.isEmpty()) continue;
                Verdict one = checkOne(id, item, spec);
                if (!one.taken()) return one;
                items.add(one.value);
            }
            return Verdict.take(join(items));
        }
        if (value.isEmpty() && spec.kind != Kind.STRING) return Verdict.refuse(id + " needs a value.");
        return checkOne(id, value, spec);
    }

    private static Verdict checkOne(String id, String value, Spec spec) {
        switch (spec.kind) {
            case BOOLEAN: {
                String lower = value.toLowerCase(Locale.ROOT);
                if ("true".equals(lower) || "false".equals(lower)) return Verdict.take(lower);
                return Verdict.refuse(id + " is true or false, not " + value + ".");
            }
            case INTEGER: {
                long number;
                try {
                    number = Long.parseLong(value);
                } catch (NumberFormatException notWhole) {
                    return Verdict.refuse(id + " is a whole number" + range(spec) + ", not " + value + ".");
                }
                if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE || outside(number, spec)) {
                    return Verdict.refuse(id + " is a whole number" + range(spec) + ", not " + value + ".");
                }
                return Verdict.take(String.valueOf(number));
            }
            case DOUBLE: {
                double number;
                try {
                    number = Double.parseDouble(value);
                } catch (NumberFormatException notNumber) {
                    return Verdict.refuse(id + " is a number" + range(spec) + ", not " + value + ".");
                }
                if (Double.isNaN(number) || Double.isInfinite(number) || outside(number, spec)) {
                    return Verdict.refuse(id + " is a number" + range(spec) + ", not " + value + ".");
                }
                return Verdict.take(String.valueOf(number));
            }
            default: {
                if (spec.valid == null || spec.valid.length == 0) return Verdict.take(value);
                for (String allowed : spec.valid) {
                    if (allowed != null && allowed.equals(value)) return Verdict.take(allowed);
                }
                for (String allowed : spec.valid) {
                    if (allowed != null && allowed.equalsIgnoreCase(value)) return Verdict.take(allowed);
                }
                return Verdict
                    .refuse(id + " is one of " + join(java.util.Arrays.asList(spec.valid)) + ", not " + value + ".");
            }
        }
    }

    /** Whether a number lies outside a spec's bounds, where it has any that read as numbers. */
    private static boolean outside(double number, Spec spec) {
        Double low = bound(spec.min);
        Double high = bound(spec.max);
        return low != null && number < low.doubleValue() || high != null && number > high.doubleValue();
    }

    private static Double bound(String written) {
        if (written == null) return null;
        try {
            double read = Double.parseDouble(written.trim());
            return Double.isNaN(read) ? null : Double.valueOf(read);
        } catch (NumberFormatException none) {
            return null;
        }
    }

    /** " from 1 to 4", or nothing where a bound is open or is only the type's own limit. */
    private static String range(Spec spec) {
        Double low = bound(spec.min);
        Double high = bound(spec.max);
        if (low == null || high == null
            || Math.abs(low.doubleValue()) >= Integer.MAX_VALUE
            || Math.abs(high.doubleValue()) >= Integer.MAX_VALUE) return "";
        return " from " + shown(low.doubleValue()) + " to " + shown(high.doubleValue());
    }

    private static String shown(double bound) {
        return bound == Math.rint(bound) ? String.valueOf((long) bound) : String.valueOf(bound);
    }

    static String join(Collection<String> items) {
        StringBuilder out = new StringBuilder();
        for (String item : items) {
            if (out.length() > 0) out.append(", ");
            out.append(item);
        }
        return out.toString();
    }

    // ------------------------------------------------------------------
    // One rule, a line of the server's file
    // ------------------------------------------------------------------

    /** One setting a server holds: where it is, the value, and how strongly. */
    public static final class Rule {

        public final String id;

        public final String value;

        public final Strength strength;

        public Rule(String id, String value, Strength strength) {
            this.id = id;
            this.value = value;
            this.strength = strength;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Rule)) return false;
            Rule rule = (Rule) other;
            return id.equals(rule.id) && value.equals(rule.value) && strength == rule.strength;
        }

        @Override
        public int hashCode() {
            return (id.hashCode() * 31 + value.hashCode()) * 31 + strength.ordinal();
        }

        @Override
        public String toString() {
            return line(this);
        }
    }

    /** A rule as the server's file writes it: {@code client.glowingWear = false force}. */
    public static String line(Rule rule) {
        return rule.id + " = " + rule.value + " " + rule.strength.word;
    }

    /**
     * A line of the server's file read as a rule, unchecked, or null where it is not shaped like one: a name, an
     * equals sign, a value, and force or suggest last. The value is everything between, so a list's commas and
     * spaces stay in it.
     */
    public static Rule parse(String line) {
        if (line == null) return null;
        int equals = line.indexOf('=');
        if (equals <= 0) return null;
        String id = line.substring(0, equals)
            .trim();
        String rest = line.substring(equals + 1)
            .trim();
        int space = Math.max(rest.lastIndexOf(' '), rest.lastIndexOf('\t'));
        if (id.isEmpty() || space < 0) return null;
        Strength strength = Strength.of(rest.substring(space + 1));
        if (strength == null) return null;
        return new Rule(
            id,
            rest.substring(0, space)
                .trim(),
            strength);
    }

    // ------------------------------------------------------------------
    // The server's list
    // ------------------------------------------------------------------

    /** Looks a setting's shape up by its place; null for a setting this edition does not have. */
    public interface Specs {

        Spec spec(String id);
    }

    /**
     * A server's list of rules, by setting, in name order so the file and the list command read the same way every
     * time. Every rule in it has been checked against the server's own settings.
     */
    public static final class Book {

        private final TreeMap<String, Rule> rules = new TreeMap<String, Rule>();

        /** The rules in name order. */
        public List<Rule> rules() {
            return new ArrayList<Rule>(rules.values());
        }

        public int size() {
            return rules.size();
        }

        public Rule get(String id) {
            return rules.get(id);
        }

        /**
         * Reads the file's lines into the list, each checked, and says what was refused, one line a problem. A later
         * line for the same setting replaces an earlier one, as a later edit would.
         */
        public List<String> read(String[] lines, Specs specs) {
            rules.clear();
            List<String> problems = new ArrayList<String>();
            if (lines == null) return problems;
            for (String line : lines) {
                if (line == null || line.trim()
                    .isEmpty()) continue;
                Rule rule = parse(line);
                if (rule == null) {
                    problems.add("'" + line.trim() + "' is not a rule: write <setting> = <value> force, or suggest.");
                    continue;
                }
                String problem = set(rule.id, rule.value, rule.strength, specs);
                if (problem != null) problems.add(problem);
            }
            return problems;
        }

        /** Sets a rule, checked; null where taken, or why not. The list is left as it was on a refusal. */
        public String set(String id, String value, Strength strength, Specs specs) {
            if (strength == null) return "Say force or suggest.";
            String name = id == null ? null : id.trim();
            Verdict verdict = check(name, value, specs == null || name == null ? null : specs.spec(name));
            if (!verdict.taken()) return verdict.problem;
            Rule was = rules.put(name, new Rule(name, verdict.value, strength));
            if (encode(rules.values()).length > MAX_BYTES) {
                if (was == null) {
                    rules.remove(name);
                } else {
                    rules.put(name, was);
                }
                return "The list would be too long to send: clear some rules first.";
            }
            return null;
        }

        /** Takes a rule away; whether there was one. */
        public boolean clear(String id) {
            return id != null && rules.remove(id.trim()) != null;
        }

        /** Takes every rule away; how many there were. */
        public int clearAll() {
            int had = rules.size();
            rules.clear();
            return had;
        }

        /**
         * Replaces the list with a player's own settings, every one at the strength given (the copy command). Each is
         * checked against this server's settings; what is refused is left out and said.
         */
        public List<String> copy(Map<String, String> values, Strength strength, Specs specs) {
            rules.clear();
            List<String> problems = new ArrayList<String>();
            if (values == null) return problems;
            for (Map.Entry<String, String> each : new TreeMap<String, String>(values).entrySet()) {
                String problem = set(each.getKey(), each.getValue(), strength, specs);
                if (problem != null) problems.add(problem);
            }
            return problems;
        }

        /** The list as the server's file writes it, a line a rule. */
        public String[] lines() {
            String[] out = new String[rules.size()];
            int i = 0;
            for (Rule rule : rules.values()) {
                out[i++] = line(rule);
            }
            return out;
        }
    }

    // ------------------------------------------------------------------
    // The command
    // ------------------------------------------------------------------

    /** What {@code /trmt clientrules} can be asked to do. */
    public enum Verb {
        LIST,
        SET,
        CLEAR,
        CLEARALL,
        COPY
    }

    /** The command's words after {@code clientrules}, as every edition reads them. */
    public static final String USAGE = "/trmt clientrules <list|set <setting> <value> force|suggest|clear <setting>|clearall|copy [force|suggest]>";

    /** The verbs, for completion. */
    public static final String[] VERBS = { "list", "set", "clear", "clearall", "copy" };

    /** One order read off the command: what to do, to which setting, and with what. Or a problem, said as usage. */
    public static final class Order {

        public final Verb verb;

        public final String id;

        public final String value;

        public final Strength strength;

        /** Why the words do not make an order, or null where they do. */
        public final String problem;

        Order(Verb verb, String id, String value, Strength strength, String problem) {
            this.verb = verb;
            this.id = id;
            this.value = value;
            this.strength = strength;
            this.problem = problem;
        }

        static Order wrong(String problem) {
            return new Order(null, null, null, null, problem);
        }
    }

    /**
     * Reads the words after {@code clientrules}. {@code set}'s value is every word between the setting and the last
     * word, joined by single spaces, so a list's items can be given as {@code a, b}; copy is suggest unless it says
     * force. Nothing is checked against the settings here: that is the list's to do.
     */
    public static Order order(String[] words) {
        if (words == null || words.length == 0) return Order.wrong(USAGE);
        String verb = words[0].toLowerCase(Locale.ROOT);
        if ("list".equals(verb) && words.length == 1) return new Order(Verb.LIST, null, null, null, null);
        if ("clearall".equals(verb) && words.length == 1) return new Order(Verb.CLEARALL, null, null, null, null);
        if ("clear".equals(verb) && words.length == 2) return new Order(Verb.CLEAR, words[1], null, null, null);
        if ("copy".equals(verb) && words.length <= 2) {
            Strength strength = words.length == 2 ? Strength.of(words[1]) : Strength.SUGGEST;
            if (strength == null) return Order.wrong("/trmt clientrules copy [force|suggest]");
            return new Order(Verb.COPY, null, null, strength, null);
        }
        if ("set".equals(verb)) {
            Strength strength = words.length >= 4 ? Strength.of(words[words.length - 1]) : null;
            if (strength == null) return Order.wrong("/trmt clientrules set <setting> <value> force|suggest");
            StringBuilder value = new StringBuilder();
            for (int i = 2; i < words.length - 1; i++) {
                if (value.length() > 0) value.append(' ');
                value.append(words[i]);
            }
            return new Order(Verb.SET, words[1], value.toString(), strength, null);
        }
        return Order.wrong(USAGE);
    }

    /** What the list command says: a heading and a line a rule, or how to add one where there are none. */
    public static List<String> listed(Book book) {
        List<String> out = new ArrayList<String>();
        if (book.size() == 0) {
            out.add(
                "This server sets none of its players' TRMT settings. Add one with /trmt clientrules set <setting> <value> force|suggest.");
            return out;
        }
        out.add(
            "This server sets " + book.size()
                + (book.size() == 1 ? " TRMT setting" : " TRMT settings")
                + " for its players:");
        for (Rule rule : book.rules()) {
            out.add(line(rule));
        }
        return out;
    }

    /** What setting a rule says, once taken. */
    public static String setWords(Rule rule) {
        return rule.id + " is now "
            + (rule.strength == Strength.FORCE ? "forced" : "suggested")
            + " at "
            + rule.value
            + " for every player on this server.";
    }

    /** What clearing a rule says. */
    public static String clearedWords(String id, boolean was) {
        return was ? id + " is every player's own again." : "This server does not set " + id + ".";
    }

    /** What clearing the list says. */
    public static String clearedAllWords(int had) {
        return had == 0 ? "This server sets none of its players' TRMT settings already."
            : "Cleared " + had
                + (had == 1 ? " rule" : " rules")
                + ": every player's TRMT settings are their own again.";
    }

    /** What a copy says once the operator's client has answered. */
    public static String copiedWords(int taken, Strength strength) {
        return "This server now " + (strength == Strength.FORCE ? "forces" : "suggests")
            + " your "
            + taken
            + (taken == 1 ? " TRMT setting" : " TRMT settings")
            + " to every player.";
    }

    /** What a copy asked from somewhere with no client of its own says. */
    public static final String COPY_NEEDS_A_PLAYER = "Only a player can copy their settings: run it from the game, as an operator.";

    /** What a copy asked of a client too old to answer says. */
    public static final String COPY_NEEDS_NEWER = "Your copy of TRMT: Reimagined is too old to send its settings. Update it to copy them.";

    /** What an answer from a player who is no longer an operator says. */
    public static final String COPY_NEEDS_AN_OPERATOR = "Only an operator can copy their settings to this server.";

    /** What an answer this server cannot read says. */
    public static final String COPY_UNREADABLE = "Your game's settings arrived in a form this server cannot read; nothing changed.";

    /** What a copy says while it waits for the operator's client. */
    public static final String COPY_ASKED = "Asking your game for its TRMT settings.";

    /**
     * What an operator's game sends for a copy: its settings at the values it is using, leaving out a value too long
     * for a rule, which the server would refuse anyway, and stopping before the answer would pass what one packet
     * carries in every edition.
     */
    public static List<Rule> answer(Map<String, String> current) {
        List<Rule> out = new ArrayList<Rule>();
        if (current == null) return out;
        int size = 3;
        for (Map.Entry<String, String> each : current.entrySet()) {
            String id = each.getKey();
            String value = each.getValue();
            if (id == null || value == null || id.length() > LONGEST_ID || value.length() > LONGEST_VALUE) continue;
            int cost = 5 + id.length() * 3 + value.length() * 3;
            if (size + cost > MAX_BYTES) break;
            size += cost;
            out.add(new Rule(id, value, Strength.SUGGEST));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // The wire
    // ------------------------------------------------------------------

    /** The rules as bytes: the format, a count, and each rule's place, value and strength. */
    public static byte[] encode(Collection<Rule> rules) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        try {
            out.writeByte(FORMAT);
            List<Rule> all = rules == null ? Collections.<Rule>emptyList() : new ArrayList<Rule>(rules);
            out.writeShort(all.size());
            for (Rule rule : all) {
                out.writeUTF(rule.id);
                out.writeUTF(rule.value);
                out.writeByte(rule.strength.ordinal());
            }
            out.flush();
        } catch (IOException cannot) {
            throw new IllegalStateException("Writing to memory failed", cannot);
        }
        return bytes.toByteArray();
    }

    /**
     * The rules in some bytes, unchecked - the client checks each against its own settings as it holds them. Null for
     * bytes this build cannot read: a newer format, or bytes cut short, which a client leaves alone rather than half
     * applying.
     */
    public static List<Rule> decode(byte[] bytes) {
        if (bytes == null || bytes.length < 3) return null;
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        try {
            int format = in.readUnsignedByte();
            if (format != FORMAT) return null;
            int count = in.readUnsignedShort();
            List<Rule> out = new ArrayList<Rule>(Math.min(count, 1024));
            for (int i = 0; i < count; i++) {
                String id = in.readUTF();
                String value = in.readUTF();
                int strength = in.readUnsignedByte();
                if (strength >= Strength.values().length) return null;
                out.add(new Rule(id, value, Strength.values()[strength]));
            }
            return out;
        } catch (IOException cutShort) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // The visit, on the client
    // ------------------------------------------------------------------

    /** The settings the pictures preset moves: one of them held moves the preset off its rung for the read. */
    static final String[] QUALITY_MOVES = { "client.wearGradations", "client.wearRotations", "client.maxWearSprites",
        "client.perSurfaceTextures" };

    /**
     * Everything a read is to see for the visit: the held settings at their values, and where one of them is a setting
     * the pictures preset moves and the preset itself is not held, the preset at custom, as a hand edit would leave it.
     * Without that the preset reads the held number as the player's edit and moves itself to custom, saying so, on
     * every read of the visit; with it, a rung the player chose still decides the settings the server leaves alone,
     * because the file holds that rung's numbers. A held preset decides its own settings, and {@link Visit#hold} sets
     * aside any of them held one by one beside it.
     */
    public static Map<String, String> forRead(Map<String, String> applied) {
        Map<String, String> out = new LinkedHashMap<String, String>(applied);
        if (applied.containsKey(PRESETS + "." + QUALITY)) return out;
        for (String moved : QUALITY_MOVES) {
            if (applied.containsKey(moved)) {
                out.put(PRESETS + "." + QUALITY, "custom");
                out.put(PRESETS + ".remembered." + QUALITY + "LastApplied", "custom");
                break;
            }
        }
        return out;
    }

    /**
     * What a server holds of this client's settings for one visit. One per client; held by the edition, emptied on the
     * way out.
     */
    public static final class Visit {

        /** The rules this client can honour, by place, as the server last sent them. */
        private final Map<String, Rule> rules = new LinkedHashMap<String, Rule>();

        /** Suggested settings the player has changed this visit, and to what. */
        private final Map<String, String> chosen = new HashMap<String, String>();

        /** What the server sent that does not apply here, or was refused, with why: shown, not applied. */
        private final Map<String, String> notApplying = new LinkedHashMap<String, String>();

        /**
         * Takes a server's rules, each checked against this client's own settings, and says whether anything this
         * client applies has moved - only then is a read wanted. A setting this edition does not have, and a value its
         * file would not take, is kept aside as not applying. A suggested setting the player changed keeps their
         * change while the server goes on suggesting the same value; a new suggestion is applied afresh.
         */
        public boolean hold(List<Rule> sent, Specs specs) {
            Map<String, String> before = applied();
            Map<String, Rule> was = new HashMap<String, Rule>(rules);
            rules.clear();
            notApplying.clear();
            if (sent != null) {
                for (Rule rule : sent) {
                    if (rule == null || rule.id == null || rule.value == null || rule.strength == null) continue;
                    Spec spec = specs == null ? null : specs.spec(rule.id);
                    Verdict verdict = check(rule.id, rule.value, spec);
                    if (!verdict.taken()) {
                        notApplying.put(rule.id, verdict.problem);
                        continue;
                    }
                    rules.put(rule.id, new Rule(rule.id, verdict.value, rule.strength));
                }
            }
            // A held pictures preset decides the settings it moves: one of them held beside it would be undone by the
            // preset's rung on every read, so it is shown as not applying rather than locked at a value never used.
            if (rules.containsKey(PRESETS + "." + QUALITY)) {
                for (String moved : QUALITY_MOVES) {
                    if (rules.remove(moved) != null) {
                        notApplying.put(moved, "presets.quality is set by this server too, and decides " + moved + ".");
                    }
                }
            }
            for (String id : new ArrayList<String>(chosen.keySet())) {
                Rule now = rules.get(id);
                Rule then = was.get(id);
                if (now == null || now.strength != Strength.SUGGEST || then == null || !now.equals(then)) {
                    chosen.remove(id);
                }
            }
            return !before.equals(applied());
        }

        /** Whether any rule is held. */
        public boolean held() {
            return !rules.isEmpty();
        }

        /** The value a setting is held at this visit, or null where it is the player's. */
        public String effective(String id) {
            Rule rule = rules.get(id);
            if (rule == null) return null;
            if (rule.strength == Strength.SUGGEST && chosen.containsKey(id)) return chosen.get(id);
            return rule.value;
        }

        /** The server's value for a forced setting, which the menu shows locked, or null where it is not forced. */
        public String forced(String id) {
            Rule rule = rules.get(id);
            return rule != null && rule.strength == Strength.FORCE ? rule.value : null;
        }

        /** The server's own value for a setting it holds, whatever the player chose since, or null. */
        public String serverValue(String id) {
            Rule rule = rules.get(id);
            return rule == null ? null : rule.value;
        }

        /** Whether the server suggests this setting. */
        public boolean suggested(String id) {
            Rule rule = rules.get(id);
            return rule != null && rule.strength == Strength.SUGGEST;
        }

        /**
         * The player changed a held setting, in the menu, to this value. A suggested one takes it for the rest of the
         * visit and not beyond; a forced one is not theirs to change. Whether the change was taken.
         */
        public boolean chose(String id, String value) {
            Rule rule = rules.get(id);
            if (rule == null || rule.strength != Strength.SUGGEST || value == null) return false;
            if (value.equals(rule.value)) {
                chosen.remove(id);
            } else {
                chosen.put(id, value);
            }
            return true;
        }

        /** Every setting held, at the value it is held at, in the order the server sent them. */
        public Map<String, String> applied() {
            Map<String, String> out = new LinkedHashMap<String, String>();
            for (String id : rules.keySet()) {
                out.put(id, effective(id));
            }
            return out;
        }

        /** How many of the player's settings this server sets: the number the chat line gives. */
        public int count() {
            return rules.size();
        }

        /** What the server sent that does not apply here, with why. */
        public Map<String, String> notApplying() {
            return Collections.unmodifiableMap(notApplying);
        }

        /** Everything let go, on the way out or when a server clears its list: the player's own settings stand. */
        public void release() {
            rules.clear();
            chosen.clear();
            notApplying.clear();
        }
    }
}
