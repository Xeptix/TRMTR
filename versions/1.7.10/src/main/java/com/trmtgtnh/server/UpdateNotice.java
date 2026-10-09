package com.trmtgtnh.server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.FakePlayer;

import com.trmtgtnh.Tags;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.util.DependencyCheck;
import com.trmtgtnh.util.MainThread;
import com.trmtgtnh.util.NoticeSilences;
import com.trmtgtnh.util.Support;
import com.trmtgtnh.util.UpdateCheck;

/**
 * Tells whoever can update the mod, as they join, that a newer release is out - the half of the update
 * notice that knows this edition's server. The other half, what is newer and when to ask, is
 * {@link UpdateCheck}, shared by every edition.
 *
 * <p>
 * Asked for on 2026-10-07 and planned in {@code docs/UPDATE-NOTICE-PLAN.md}, with the user's four
 * decisions: told is whoever can update this copy, the links are CurseForge and Modrinth, it is on until
 * {@code general.updateNotice} says off, and the newest version is one line in a file in the public
 * repository.
 *
 * <p>
 * <strong>Who can update it.</strong> In single player, the player - found by the in-memory connection,
 * not by the operator check the mod uses elsewhere: vanilla makes the single-player owner an operator
 * only when the world allows cheats ({@code ServerConfigurationManager.func_152596_g}, read with javap),
 * and the owner's name does not match under an offline-mode launcher. In a LAN game, the host and not
 * the guests, who cannot update the host's copy. On a dedicated server, the operators. With the setting
 * at {@code everyone}, every real player.
 *
 * <p>
 * <strong>Sent as it is formatted here</strong>, as {@link Notices} explains: a client one version
 * behind has no translation for a key this version adds. Straight to the player rather than through
 * {@link Notices#say}, whose one-second quiet would swallow it whenever the quest notice speaks in the
 * same tick.
 */
public final class UpdateNotice {

    /** This jar's line in the version file. */
    public static final String EDITION = "1.7.10-forge";

    /** The headline, with its two places, in case a language file has none or the wrong number of them. */
    private static final String FALLBACK = "%s is out - this world runs %s.";

    /** The known-issues headline, with the running build in its one place. */
    private static final String BAD_FALLBACK = "This world is running %s, which has known issues.";

    /** And under it, the build to use instead. */
    private static final String BAD_USE_FALLBACK = "%s is the build to use.";

    /** What follows when a build since this one was critical for this jar, with its reason and build. */
    private static final String CRITICAL_FALLBACK = "It includes a critical fix (%s) from %s.";

    /** The same, when the file gives no reason this jar knows. */
    private static final String CRITICAL_PLAIN_FALLBACK = "It includes a critical fix from %s.";

    /** Where to get it, with the two store pages in their places. */
    private static final String GET_FALLBACK = "Get it from %s or %s.";

    /** A required mod older than the version file's minimum for it: its name and the version installed. */
    private static final String BELOW_FALLBACK = "%s %s is older than this mod needs.";

    /** And under it, what that means. */
    private static final String PROBLEMS_FALLBACK = "You may run into problems until it is updated.";

    /** What to update it to, when the minimum is also the recommendation. */
    private static final String UPDATE_TO_FALLBACK = "Update it to %s.";

    /** What to update it to, when a newer one is recommended. */
    private static final String UPDATE_OR_FALLBACK = "Update it to %s, or %s as recommended.";

    /** Where to ask for help, after a dependency warning, in place of the Discord's update line. */
    private static final String HELP_FALLBACK = "Having trouble? Ask in the Discord's #mc-help:";

    /** The last line: the two ways to quiet the notice, each a link that runs /trmtnotice. */
    private static final String SILENCE_FALLBACK = "%s, or %s.";

    /** What /trmtnotice says back. */
    private static final String SILENCED_FALLBACK = "You will not be told of %s again.";

    private static final String OFF_FALLBACK = "Update notices off. %s turns them back on.";

    private static final String ON_FALLBACK = "Update notices are on for you again.";

    /** The questions the silence links ask before they act. */
    private static final String ASK_SILENCE_FALLBACK = "Stop telling you about %s?";

    private static final String ASK_OFF_FALLBACK = "Turn update notices off for you?";

    private static final String UNSAVED_FALLBACK = "That could not be saved on this server.";

    private static final String USAGE_FALLBACK = "/trmtnotice silence <version>, off, or on";

    /** The command the links run, which any player may: it acts for whoever runs it and nobody else. */
    public static final String COMMAND = "trmtnotice";

    /** Who has quieted the notice here, read from the config folder as the server starts. */
    private static volatile NoticeSilences silences;

    /** When the last round of telling began, for the round every eight hours ({@link #serverTick}). */
    private static volatile long lastRound;

    /**
     * The mods this jar requires, by their mod ids and the names a player knows them by (0.9.221): what the dependency
     * check asks the version file about. The file names no mod of its own (DependencyCheck).
     */
    static final String[][] REQUIRED = { { "unimixins", "UniMixins" } };

    /** Each required mod with the version installed, or null for one that is not. */
    static String[][] installed() {
        String[][] out = new String[REQUIRED.length][];
        for (int at = 0; at < REQUIRED.length; at++) {
            cpw.mods.fml.common.ModContainer mod = cpw.mods.fml.common.Loader.instance()
                .getIndexedModList()
                .get(REQUIRED[at][0]);
            out[at] = new String[] { REQUIRED[at][0], REQUIRED[at][1], mod == null ? null : mod.getVersion() };
        }
        return out;
    }

    /** Who has been told since they last joined, so an answer arriving late tells each of them once. */
    private static final Set<UUID> TOLD = new HashSet<UUID>();

    private UpdateNotice() {}

    /** At server start: asks, unless the setting, a harness run or a development game says not to - and says which. */
    public static void serverStarting() {
        TOLD.clear();
        String not = UpdateCheck.whyNot(TrmtConfig.updateNotice, development(), System.getProperties());
        if (not != null) Trmt.LOG.info("Update check: not asked - {}", not);
        silences = null;
        lastRound = System.currentTimeMillis();
        ask(false);
    }

    /**
     * Starts a check when this game may ask and the last answer is missing or eight hours old - or, for the round every
     * eight hours ({@link #serverTick}), whatever its age, telling everyone online afresh once it answers.
     */
    private static void ask(final boolean round) {
        if (!UpdateCheck.mayAsk(TrmtConfig.updateNotice, development(), System.getProperties())) return;
        if (!round && !UpdateCheck.due(System.currentTimeMillis())) return;
        UpdateCheck
            .start(EDITION, Tags.VERSION, TrmtConfig.updateNoticeReleases, System.getProperties(), new Runnable() {

                @Override
                public void run() {
                    UpdateCheck.Answer answer = UpdateCheck.last();
                    if (answer != null) Trmt.LOG.info("Update check: {}", answer.said);
                    // And every required mod older than this mod wants (0.9.221): in the log always.
                    for (DependencyCheck.Below need : DependencyCheck
                        .below(UpdateCheck.lastBody(), EDITION, installed())) {
                        Trmt.LOG.warn(
                            "Dependency check: {} {} is older than the {} this mod needs",
                            need.name,
                            need.installed,
                            need.minimum);
                    }
                    // Whoever joined while the request was out is told now, on the server's own thread.
                    MainThread.onServer(new Runnable() {

                        @Override
                        public void run() {
                            if (round) TOLD.clear();
                            tellEveryoneWaiting();
                        }
                    });
                }
            });
    }

    /**
     * The round every eight hours (0.9.221; Xep, 2026-10-09: "make the text post on a 8h schedule, so 3 times a day if
     * the server stays running that long"): the version file is read again, and once it answers everyone online who
     * can update and has not quieted it is told afresh. Server tick, once a second.
     */
    public static void serverTick() {
        long now = System.currentTimeMillis();
        if (lastRound == 0L) lastRound = now;
        if (now - lastRound < UpdateCheck.STALE_AFTER_MS) return;
        lastRound = now;
        ask(true);
    }

    /** Who has quieted the notice here, read once a server start. */
    private static NoticeSilences silences() {
        NoticeSilences held = silences;
        if (held == null) {
            held = NoticeSilences.in(TrmtConfig.folder());
            silences = held;
        }
        return held;
    }

    /**
     * What /trmtnotice does for the player who runs it, and the line it says back (0.9.221): {@code silence <build>}
     * quiets that one build for them, {@code off} quiets the notice for them altogether, {@code on} undoes either.
     * Nobody else's notice changes, so any player may run it.
     */
    public static IChatComponent answer(EntityPlayerMP player, String what, String build, boolean confirmed) {
        String who = player.getUniqueID()
            .toString();
        if ("silence".equals(what) && build != null && UpdateCheck.trusted(build)) {
            // Asked first, and done only from the confirmation's own link (Xep, 2026-10-09: "should have a confirm
            // system").
            if (!confirmed) {
                return ask(
                    Support.fill(translate("trmtgtnh.notice.askSilence"), ASK_SILENCE_FALLBACK, build),
                    "/" + COMMAND + " silence " + build + " confirm");
            }
            return confirm(
                silences().silence(who, build),
                Support.fill(translate("trmtgtnh.notice.silenced"), SILENCED_FALLBACK, build));
        }
        if ("off".equals(what)) {
            if (!confirmed)
                return ask(said("trmtgtnh.notice.askOff", ASK_OFF_FALLBACK), "/" + COMMAND + " off confirm");
            return confirm(
                silences().turnOff(who),
                Support.fill(translate("trmtgtnh.notice.off"), OFF_FALLBACK, "/" + COMMAND + " on"));
        }
        if ("on".equals(what)) return confirm(silences().turnOn(who), said("trmtgtnh.notice.on", ON_FALLBACK));
        return confirm(true, said("trmtgtnh.notice.usage", USAGE_FALLBACK));
    }

    /** The question a silence link asks first, with the mod's mark, and a Confirm link that does it. */
    private static IChatComponent ask(String question, String line) {
        IChatComponent root = Notices.line("", null, true);
        root.appendSibling(styled(question + " ", EnumChatFormatting.GRAY, false));
        root.appendSibling(command(said("trmtgtnh.notice.confirm", "Confirm"), line));
        return root;
    }

    /** A line said back, with the mod's mark: gray when it was kept, red when the server could not keep it. */
    private static IChatComponent confirm(boolean kept, String text) {
        IChatComponent root = Notices.line("", null, true);
        root.appendSibling(
            kept ? styled(text, EnumChatFormatting.GRAY, false)
                : styled(said("trmtgtnh.notice.unsaved", UNSAVED_FALLBACK), EnumChatFormatting.RED, false));
        return root;
    }

    /** A link that runs a command when clicked, and shows it when pointed at. */
    private static IChatComponent command(String name, String line) {
        ChatComponentText out = new ChatComponentText(name);
        out.setChatStyle(
            new ChatStyle().setColor(EnumChatFormatting.AQUA)
                .setUnderlined(Boolean.TRUE)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, line))
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(line))));
        return out;
    }

    /** As a player joins: tells them if they can update and there is something to tell. */
    public static void onLogin(EntityPlayerMP player) {
        if (player == null || player instanceof FakePlayer) return;
        TOLD.remove(player.getUniqueID());
        if (UpdateCheck.due(System.currentTimeMillis())) ask(false);
        tell(player);
    }

    /** As a player leaves: their next join is a new one, and tells them again. */
    public static void onLogout(EntityPlayerMP player) {
        if (player != null) TOLD.remove(player.getUniqueID());
    }

    private static void tellEveryoneWaiting() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return;
        List<?> online = server.getConfigurationManager().playerEntityList;
        for (Object each : online) {
            if (each instanceof EntityPlayerMP && !(each instanceof FakePlayer)) tell((EntityPlayerMP) each);
        }
    }

    private static void tell(EntityPlayerMP player) {
        UpdateCheck.Answer answer = UpdateCheck.last();
        if (answer == null) return;
        List<DependencyCheck.Below> needs = DependencyCheck.below(UpdateCheck.lastBody(), EDITION, installed());
        // Quiet for this player when they turned notices off, or silenced this very build (0.9.221); a newer one is
        // told.
        String who = player.getUniqueID()
            .toString();
        boolean update = answer.newer != null && !silences().off(who) && !silences().silenced(who, answer.newer);
        if (!update && needs.isEmpty()) return;
        if (TOLD.contains(player.getUniqueID()) || !canUpdate(player)) return;
        TOLD.add(player.getUniqueID());
        List<IChatComponent> sent = lines(answer, needs, update);
        for (IChatComponent each : sent) player.addChatMessage(each);
        for (DependencyCheck.Below need : needs) {
            Trmt.LOG.info(
                "Update notice: told {} that {} {} is older than this mod needs",
                player.getCommandSenderName(),
                need.name,
                need.installed);
        }
        if (update) Trmt.LOG.info(
            answer.bad ? "Update notice: told {} that this build has known issues, and {} is the one to use"
                : "Update notice: told {} that {} is out",
            player.getCommandSenderName(),
            answer.newer);
        // Under the test address, every line as it was sent, so a run can read where each link goes without anybody
        // clicking them.
        if (System.getProperty(UpdateCheck.TEST_ADDRESS) != null) {
            for (IChatComponent each : sent)
                Trmt.LOG.info("Update notice: a line as sent - {}", IChatComponent.Serializer.func_150696_a(each));
        }
    }

    /** Whether this player can act on the notice, by the setting. */
    static boolean canUpdate(EntityPlayerMP player) {
        String setting = TrmtConfig.updateNotice == null ? "" : TrmtConfig.updateNotice.trim();
        if (TrmtConfig.UPDATE_OFF.equalsIgnoreCase(setting)) return false;
        if (TrmtConfig.UPDATE_EVERYONE.equalsIgnoreCase(setting)) return true;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return false;
        if (!server.isDedicatedServer()) {
            // The integrated server's own player is the one on the in-memory connection.
            return player.playerNetServerHandler != null && player.playerNetServerHandler.netManager != null
                && player.playerNetServerHandler.netManager.isLocalChannel();
        }
        return server.getConfigurationManager()
            .func_152596_g(player.getGameProfile());
    }

    /**
     * The notice, as a few short lines (0.9.221; Xep, 2026-10-09: "make sure the update chat looks good to the user, is
     * formatted well, and is digestible"): the headline with the mod's mark - the build to get, in gold, against the
     * one running - then under it, one thing to a line, the critical fix if a build since was critical for this jar,
     * what the newest change to it does if the release said, and where to get it; then each required mod older than
     * this mod wants; and last the Discord, its link on a line of its own. Every line fits the chat's own width, 320 of
     * the game's pixels, measured against its font: a line that wraps breaks where it likes, and on 1.7.10 it broke
     * the invite in two.
     */
    static List<IChatComponent> lines(UpdateCheck.Answer answer, List<DependencyCheck.Below> needs, boolean update) {
        List<IChatComponent> out = new ArrayList<IChatComponent>();
        if (update) {
            out.add(headline(answer.newer, answer.running, answer.bad));
            if (answer.bad) {
                String[] use = Support.cut(translate("trmtgtnh.update.badUse"), BAD_USE_FALLBACK, 1);
                out.add(
                    under(
                        joined(
                            styled(use[0], EnumChatFormatting.GRAY, false),
                            styled(answer.newer, EnumChatFormatting.GOLD, true),
                            styled(use[1], EnumChatFormatting.GRAY, false))));
            }
            if (!answer.bad && answer.critical != null) out.add(under(criticalWords(answer.critical, answer.reason)));
            // The release's own brief sentence, when it gave one: a line of its own, in white.
            if (!answer.bad && answer.says != null)
                out.add(under(styled(answer.says, EnumChatFormatting.WHITE, false)));
            String[] get = Support.cut(translate("trmtgtnh.update.get"), GET_FALLBACK, 2);
            out.add(
                under(
                    joined(
                        styled(get[0], EnumChatFormatting.GRAY, false),
                        link("CurseForge", UpdateCheck.CURSEFORGE),
                        styled(get[1], EnumChatFormatting.GRAY, false),
                        link("Modrinth", UpdateCheck.MODRINTH),
                        styled(get[2], EnumChatFormatting.GRAY, false))));
        }
        // Then each required mod older than this mod wants (0.9.221): its name and version, what that means, and what
        // to
        // update to - one version when the minimum is also the recommendation.
        for (DependencyCheck.Below need : needs) {
            out.add(belowHeadline(need));
            out.add(
                under(styled(said("trmtgtnh.requires.problems", PROBLEMS_FALLBACK), EnumChatFormatting.GRAY, false)));
            String[] to = need.recommendsMore()
                ? Support.cut(translate("trmtgtnh.requires.updateOr"), UPDATE_OR_FALLBACK, 2)
                : Support.cut(translate("trmtgtnh.requires.update"), UPDATE_TO_FALLBACK, 1);
            out.add(
                under(
                    need.recommendsMore()
                        ? joined(
                            styled(to[0], EnumChatFormatting.GRAY, false),
                            styled(need.minimum, EnumChatFormatting.GOLD, true),
                            styled(to[1], EnumChatFormatting.GRAY, false),
                            styled(need.recommended, EnumChatFormatting.GOLD, true),
                            styled(to[2], EnumChatFormatting.GRAY, false))
                        : joined(
                            styled(to[0], EnumChatFormatting.GRAY, false),
                            styled(need.minimum, EnumChatFormatting.GOLD, true),
                            styled(to[1], EnumChatFormatting.GRAY, false))));
        }
        // Last, the Discord: where to hear of the next update - or, after a warning, where to ask for help - and its
        // link on a line of its own.
        String join = needs.isEmpty() ? said("trmtgtnh.update.discord", Support.JOIN_FALLBACK)
            : said("trmtgtnh.requires.help", HELP_FALLBACK);
        out.add(under(styled(join, EnumChatFormatting.GRAY, false)));
        out.add(
            joined(
                styled("      ", EnumChatFormatting.GRAY, false),
                link(Support.DISCORD_IN_GAME_SHOWN, Support.DISCORD_IN_GAME)));
        // Last of all, the two ways to quiet it, each a link running /trmtnotice for whoever clicks (0.9.221).
        if (update) {
            String[] quiet = Support.cut(translate("trmtgtnh.update.silence"), SILENCE_FALLBACK, 2);
            out.add(
                under(
                    joined(
                        styled(quiet[0], EnumChatFormatting.GRAY, false),
                        command(
                            said("trmtgtnh.update.silenceThis", "Silence this update"),
                            "/" + COMMAND + " silence " + answer.newer),
                        styled(quiet[1], EnumChatFormatting.GRAY, false),
                        command(said("trmtgtnh.update.silenceAll", "turn update notices off"), "/" + COMMAND + " off"),
                        styled(quiet[2], EnumChatFormatting.GRAY, false))));
        }
        return out;
    }

    /**
     * The headline, with the mod's mark: the build to get in gold, the one running in gray - or, for a build marked
     * bad (Xep, 2026-10-08), the running build and that it has known issues, with the one to use on the line under it.
     */
    static IChatComponent headline(String newer, String running, boolean bad) {
        IChatComponent root = Notices.line("", null, true);
        if (bad) {
            String[] pieces = Support.cut(translate("trmtgtnh.update.badHeadline"), BAD_FALLBACK, 1);
            root.appendSibling(styled(pieces[0], EnumChatFormatting.WHITE, false));
            root.appendSibling(styled(running, EnumChatFormatting.GRAY, false));
            root.appendSibling(styled(pieces[1], EnumChatFormatting.WHITE, false));
            return root;
        }
        String[] pieces = Support.cut(translate("trmtgtnh.update.headline"), FALLBACK, 2);
        root.appendSibling(styled(pieces[0], EnumChatFormatting.WHITE, false));
        root.appendSibling(styled(newer, EnumChatFormatting.GOLD, true));
        root.appendSibling(styled(pieces[1], EnumChatFormatting.WHITE, false));
        root.appendSibling(styled(running, EnumChatFormatting.GRAY, false));
        root.appendSibling(styled(pieces[2], EnumChatFormatting.WHITE, false));
        return root;
    }

    /** A required mod's warning, with the mod's mark: its name in gold, the version installed in gray. */
    static IChatComponent belowHeadline(DependencyCheck.Below need) {
        String[] pieces = Support.cut(translate("trmtgtnh.requires.below"), BELOW_FALLBACK, 2);
        IChatComponent root = Notices.line("", null, true);
        root.appendSibling(styled(pieces[0], EnumChatFormatting.WHITE, false));
        root.appendSibling(styled(need.name, EnumChatFormatting.GOLD, true));
        root.appendSibling(styled(pieces[1], EnumChatFormatting.WHITE, false));
        root.appendSibling(styled(need.installed, EnumChatFormatting.GRAY, false));
        root.appendSibling(styled(pieces[2], EnumChatFormatting.WHITE, false));
        return root;
    }

    /**
     * A sentence with no place in it, from the language file - or its fallback, when the file has none, or one that
     * asks for a place it would be given nothing for.
     */
    private static String said(String key, String fallback) {
        String text = translate(key);
        return text == null || text.equals(key) || text.indexOf("%s") >= 0 ? fallback : text;
    }

    /** One line under the headline: a dark gray dash, then the line. */
    private static IChatComponent under(IChatComponent line) {
        return joined(styled("  - ", EnumChatFormatting.DARK_GRAY, false), line);
    }

    /**
     * The critical sentence (0.9.221; Xep, 2026-10-09): "It includes a critical fix (a crash) from 0.9.218." - the
     * newest build is still the one named above, and this says why it matters more than usual. The reason is one
     * of {@link UpdateCheck#REASONS}, put into words by this jar's language file, never by the version file.
     */
    private static IChatComponent criticalWords(String critical, String reason) {
        String text;
        if (reason != null) {
            String said = translate("trmtgtnh.update.reason." + reason);
            String because = said.startsWith("trmtgtnh.") ? UpdateCheck.reasonWords(reason) : said;
            text = Support.fill(translate("trmtgtnh.update.critical"), CRITICAL_FALLBACK, because, critical);
        } else {
            text = Support.fill(translate("trmtgtnh.update.criticalPlain"), CRITICAL_PLAIN_FALLBACK, critical);
        }
        // Red, not bold: bold widens every letter, and the longest reason would then run past the chat.
        return styled(text, EnumChatFormatting.RED, false);
    }

    @SuppressWarnings("deprecation")
    private static String translate(String key) {
        return StatCollector.translateToLocal(key);
    }

    private static IChatComponent styled(String text, EnumChatFormatting color, boolean bold) {
        ChatComponentText out = new ChatComponentText(text);
        out.setChatStyle(
            new ChatStyle().setColor(color)
                .setBold(Boolean.valueOf(bold)));
        return out;
    }

    private static IChatComponent joined(IChatComponent... parts) {
        IChatComponent root = new ChatComponentText("");
        for (IChatComponent part : parts) root.appendSibling(part);
        return root;
    }

    private static IChatComponent link(String name, String address) {
        ChatComponentText out = new ChatComponentText(name);
        out.setChatStyle(
            new ChatStyle().setColor(EnumChatFormatting.AQUA)
                .setUnderlined(Boolean.TRUE)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, address))
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(address))));
        return out;
    }

    /** Whether this is a development game - a dev run reports versions that are not releases. */
    private static boolean development() {
        Object said = net.minecraft.launchwrapper.Launch.blackboard == null ? null
            : net.minecraft.launchwrapper.Launch.blackboard.get("fml.deobfuscatedEnvironment");
        return Boolean.TRUE.equals(said);
    }
}
