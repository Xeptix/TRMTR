package com.trmtgtnh.config;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.util.ClientSettingRules;

/**
 * A server's list of rules for its players' own settings (0.9.229; Xep, 2026-11-18), kept in its file under
 * {@code clientrules.rules}, a line a rule: {@code client.glowingWear = false force}. Read with the rest of the file,
 * changed live by {@code /trmt clientrules}, which writes the list back and sends it to everyone online with the rest
 * of the server's rules. A line the server's own settings refuse is said in the log, once, and left in the file.
 *
 * <p>
 * Read on a client too, where it is the list a single-player or LAN host's guests are sent; the host itself is never
 * held to it ({@link ClientSettingsHeld}).
 */
public final class ClientSettingsBook {

    private static final ClientSettingRules.Book BOOK = new ClientSettingRules.Book();

    /** Each refusal said once, so a reload says only what is new. */
    private static final Set<String> SAID = new TreeSet<String>();

    static final String COMMENT = "Settings this server sets for every player who joins, a line each: <setting> = <value> force, or suggest. A setting is named by its place in this file, client.glowingWear, presets.quality or families.sand.wearPattern, and only a client setting may be named: everything under client but settleOnWornGround, which the server's own rules already carry; presets.quality; and each family's wearPattern, classicWear and wearStrength. force holds the setting at this value while a player is connected and locks it in their settings menu; suggest applies it when they join and leaves them free to change it back, for that visit. Anything not listed stays each player's own, and nothing here is ever written to a player's own file: leaving gives them all their own settings back. A setting that rebuilds the wear pictures is applied once at join, with one reload. A player on a build older than 0.9.229 keeps their own. /trmt clientrules list, set, clear, clearall and copy change this list while the server runs, and send it to everyone online.";

    static final String HEADING = "The settings this server sets for its players' own: what each player's game is held to, or offered, while they are connected.";

    private ClientSettingsBook() {}

    /** Reads the list out of the file, each line checked against this edition's settings. */
    static synchronized void read(Configuration config) {
        config.setCategoryComment(ClientSettingRules.CATEGORY, HEADING);
        Property list = property(config);
        List<String> problems = BOOK.read(list.getStringList(), ClientSettingsHeld.specs(config));
        for (String problem : problems) {
            if (SAID.add(problem))
                Trmt.LOG.warn("clientrules.rules: {} The line is left in the file and not sent.", problem);
        }
    }

    private static Property property(Configuration config) {
        return config.get(ClientSettingRules.CATEGORY, ClientSettingRules.LIST_KEY, new String[0], COMMENT);
    }

    /** The rules, as the server sends them. */
    public static synchronized List<ClientSettingRules.Rule> rules() {
        return BOOK.rules();
    }

    /** The list command's lines. */
    public static synchronized List<String> listed() {
        return ClientSettingRules.listed(BOOK);
    }

    /** Sets a rule; null where taken, or why not. Written to the file and sent to everyone online. */
    public static synchronized String set(String id, String value, ClientSettingRules.Strength strength) {
        Configuration config = TrmtConfig.raw();
        if (config == null) return "The settings are not loaded yet.";
        String problem = BOOK.set(id, value, strength, ClientSettingsHeld.specs(config));
        if (problem == null) written(config);
        return problem;
    }

    /** The rule now held for a setting, or null. */
    public static synchronized ClientSettingRules.Rule get(String id) {
        return BOOK.get(id == null ? null : id.trim());
    }

    /** Clears a rule; whether there was one. */
    public static synchronized boolean clear(String id) {
        Configuration config = TrmtConfig.raw();
        if (config == null || !BOOK.clear(id)) return false;
        written(config);
        return true;
    }

    /** Clears every rule; how many there were. */
    public static synchronized int clearAll() {
        Configuration config = TrmtConfig.raw();
        if (config == null) return 0;
        int had = BOOK.clearAll();
        if (had > 0) written(config);
        return had;
    }

    /** Replaces the list with an operator's own settings, each checked; what was refused. */
    public static synchronized List<String> copy(Map<String, String> values, ClientSettingRules.Strength strength) {
        Configuration config = TrmtConfig.raw();
        if (config == null) return java.util.Collections.singletonList("The settings are not loaded yet.");
        List<String> problems = BOOK.copy(values, strength, ClientSettingsHeld.specs(config));
        written(config);
        return problems;
    }

    /** How many rules the list holds. */
    public static synchronized int size() {
        return BOOK.size();
    }

    /**
     * Writes the list into the file and saves it, then sends the rules to everyone online. A list read from the file
     * keeps the lines it refused; one written by a command is the list as it stands, so a refused line goes with the
     * first command that changes the list.
     */
    private static void written(Configuration config) {
        property(config).set(BOOK.lines());
        TrmtConfig.save();
        com.trmtgtnh.network.TrmtNetwork.broadcastRules();
    }
}
