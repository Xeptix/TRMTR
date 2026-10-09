package com.trmtgtnh.server;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.util.MainThread;
import com.trmtgtnh.util.Translate;
import com.trmtgtnh.util.UpdateCheck;

/**
 * Tells whoever can update the mod, as they join, that a newer release is out - the half of the update
 * notice that knows this edition's server. The other half, what is newer and when to ask, is
 * {@link UpdateCheck}, shared by every edition. A port of the 1.7.10 edition's class of the same name,
 * which explains the choices: who can update it, why the single-player owner is found by connection,
 * and why the line is formatted here and sent straight to the player.
 *
 * <p>
 * <strong>One class, two jars.</strong> Each jar reads its own line in the version file, and whether
 * the game is a development one is a question only the loader can answer, so both are handed in by
 * whichever loader started the mod - {@code TrmtForge} and {@code TrmtFabric} - before any server
 * starts. Until one has, this asks nothing: a check that does not know which jar it is would read
 * another edition's line.
 *
 * <p>
 * A machine standing in for a player - a block breaker, a dispenser - is caught as {@link Notices}
 * catches it, by having no connection, because Forge's {@code FakePlayer} cannot be named in a module
 * both loaders share.
 */
public final class UpdateNotice {

    /** The sentence, with its four places, in case a language file has none or the wrong number of them. */
    private static final String FALLBACK = "%s is out - this world is running %s. Get it from %s or %s.";

    /** The known-issues sentence, the same way. */
    private static final String BAD_FALLBACK = "This world is running %s, which has known issues - %s is the build to use. Get it from %s or %s.";

    /** Who has been told since they last joined, so an answer arriving late tells each of them once. */
    private static final Set<UUID> TOLD = new HashSet<UUID>();

    /** This jar's line in the version file, as its loader said; null until one has. */
    private static volatile String edition;

    /** Whether the loader that started the mod calls this a development game. */
    private static volatile boolean development;

    private UpdateNotice() {}

    /** Which jar this is and whether its game is a development one. Called once by each loader module. */
    public static void edition(String key, boolean inDevelopment) {
        edition = key;
        development = inDevelopment;
    }

    /** This jar's line in the version file, or null before a loader has said. */
    public static String edition() {
        return edition;
    }

    /** At server start: asks, unless the setting, a harness run or a development game says not to - and says which. */
    public static void serverStarting() {
        TOLD.clear();
        String not = UpdateCheck.whyNot(TrmtConfig.updateNotice, development, System.getProperties());
        if (not != null) Trmt.LOG.info("Update check: not asked - {}", not);
        ask();
    }

    /** Starts a check when this game may ask and the last answer is missing or a day old. */
    private static void ask() {
        String key = edition;
        if (key == null) return;
        if (!UpdateCheck.mayAsk(TrmtConfig.updateNotice, development, System.getProperties())) return;
        if (!UpdateCheck.due(System.currentTimeMillis())) return;
        UpdateCheck.start(key, Trmt.version(), System.getProperties(), new Runnable() {

            @Override
            public void run() {
                UpdateCheck.Answer answer = UpdateCheck.last();
                if (answer != null) Trmt.LOG.info("Update check: {}", answer.said);
                // Whoever joined while the request was out is told now, on the server's own thread.
                MainThread.onServer(new Runnable() {

                    @Override
                    public void run() {
                        tellEveryoneWaiting();
                    }
                });
            }
        });
    }

    /** As a player joins: tells them if they can update and there is something to tell. */
    public static void onLogin(Player player) {
        if (!(player instanceof ServerPlayer) || ((ServerPlayer) player).connection == null) return;
        ServerPlayer joined = (ServerPlayer) player;
        TOLD.remove(joined.getUUID());
        if (UpdateCheck.due(System.currentTimeMillis())) ask();
        tell(joined);
    }

    /** As a player leaves: their next join is a new one, and tells them again. */
    public static void onLogout(Player player) {
        if (player != null) TOLD.remove(player.getUUID());
    }

    private static void tellEveryoneWaiting() {
        MinecraftServer server = Trmt.runningServer();
        if (server == null || server.getPlayerList() == null) return;
        for (ServerPlayer each : server.getPlayerList()
            .getPlayers()) {
            if (each.connection != null) tell(each);
        }
    }

    private static void tell(ServerPlayer player) {
        UpdateCheck.Answer answer = UpdateCheck.last();
        if (answer == null || answer.newer == null) return;
        if (TOLD.contains(player.getUUID()) || !canUpdate(player)) return;
        TOLD.add(player.getUUID());
        Component said = line(answer.newer, answer.running, answer.bad);
        player.sendMessage(said, net.minecraft.Util.NIL_UUID);
        Trmt.LOG.info(
            answer.bad ? "Update notice: told {} that this build has known issues, and {} is the one to use"
                : "Update notice: told {} that {} is out",
            player.getGameProfile()
                .getName(),
            answer.newer);
        // Under the test address, the line as it was sent, so a run can read where both links go
        // without anybody clicking them.
        if (System.getProperty(UpdateCheck.TEST_ADDRESS) != null) {
            Trmt.LOG.info("Update notice: the line as sent - {}", Component.Serializer.toJson(said));
        }
    }

    /** Whether this player can act on the notice, by the setting. */
    static boolean canUpdate(ServerPlayer player) {
        String setting = TrmtConfig.updateNotice == null ? "" : TrmtConfig.updateNotice.trim();
        if (TrmtConfig.UPDATE_OFF.equalsIgnoreCase(setting)) return false;
        if (TrmtConfig.UPDATE_EVERYONE.equalsIgnoreCase(setting)) return true;
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        if (!server.isDedicatedServer()) {
            // The integrated server's own player is the one on the in-memory connection.
            return player.connection != null && player.connection.connection != null
                && player.connection.connection.isMemoryConnection();
        }
        return server.getPlayerList()
            .isOp(player.getGameProfile());
    }

    /** The line: the mod's mark, the sentence, and the two links in their places. */
    static Component line(String newer, String running) {
        return line(newer, running, false);
    }

    /**
     * The line, or - for a build marked bad (Xep, 2026-10-08) - the known-issues line: the running build first, then
     * the one to use, which may be older.
     */
    static Component line(String newer, String running, boolean bad) {
        String[] pieces = UpdateCheck.pieces(Translate.get(bad ? "trmtgtnh.update.bad" : "trmtgtnh.update.available"));
        if (pieces == null) pieces = UpdateCheck.pieces(bad ? BAD_FALLBACK : FALLBACK);
        // Notices.line builds the root as a text component; it is handed back under the plainer type.
        MutableComponent root = (MutableComponent) Notices.line("", null, true);
        root.append(words(pieces[0] + (bad ? running : newer) + pieces[1] + (bad ? newer : running) + pieces[2]));
        root.append(link("CurseForge", UpdateCheck.CURSEFORGE));
        root.append(words(pieces[3]));
        root.append(link("Modrinth", UpdateCheck.MODRINTH));
        root.append(words(pieces[4]));
        return root;
    }

    private static Component words(String text) {
        return new TextComponent(text).setStyle(Style.EMPTY.applyFormat(ChatFormatting.WHITE));
    }

    private static Component link(String name, String address) {
        return new TextComponent(name).setStyle(
            // applyFormat rather than withUnderlined, which both loaders keep on the client only: this line
            // is built by the server, and a dedicated one would have thrown NoSuchMethodError at the first
            // operator it told. Found by tools/server_safe.py on 2026-10-07, before any server had told one.
            Style.EMPTY.applyFormat(ChatFormatting.AQUA)
                .applyFormat(ChatFormatting.UNDERLINE)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, address))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent(address))));
    }
}
