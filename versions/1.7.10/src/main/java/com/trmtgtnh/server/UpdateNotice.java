package com.trmtgtnh.server;

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
import com.trmtgtnh.util.MainThread;
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

    /** The sentence, with its four places, in case a language file has none or the wrong number of them. */
    private static final String FALLBACK = "%s is out - this world is running %s. Get it from %s or %s.";

    /** Who has been told since they last joined, so an answer arriving late tells each of them once. */
    private static final Set<UUID> TOLD = new HashSet<UUID>();

    private UpdateNotice() {}

    /** At server start: asks, unless the setting, a harness run or a development game says not to - and says which. */
    public static void serverStarting() {
        TOLD.clear();
        String not = UpdateCheck.whyNot(TrmtConfig.updateNotice, development(), System.getProperties());
        if (not != null) Trmt.LOG.info("Update check: not asked - {}", not);
        ask();
    }

    /** Starts a check when this game may ask and the last answer is missing or a day old. */
    private static void ask() {
        if (!UpdateCheck.mayAsk(TrmtConfig.updateNotice, development(), System.getProperties())) return;
        if (!UpdateCheck.due(System.currentTimeMillis())) return;
        UpdateCheck.start(EDITION, Tags.VERSION, System.getProperties(), new Runnable() {

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
    public static void onLogin(EntityPlayerMP player) {
        if (player == null || player instanceof FakePlayer) return;
        TOLD.remove(player.getUniqueID());
        if (UpdateCheck.due(System.currentTimeMillis())) ask();
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
        if (answer == null || answer.newer == null) return;
        if (TOLD.contains(player.getUniqueID()) || !canUpdate(player)) return;
        TOLD.add(player.getUniqueID());
        IChatComponent said = line(answer.newer, answer.running);
        player.addChatMessage(said);
        Trmt.LOG.info("Update notice: told {} that {} is out", player.getCommandSenderName(), answer.newer);
        // Under the test address, the line as it was sent, so a run can read where both links go
        // without anybody clicking them.
        if (System.getProperty(UpdateCheck.TEST_ADDRESS) != null) {
            Trmt.LOG.info("Update notice: the line as sent - {}", IChatComponent.Serializer.func_150696_a(said));
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

    /** The line: the mod's mark, the sentence, and the two links in their places. */
    static IChatComponent line(String newer, String running) {
        String[] pieces = UpdateCheck.pieces(StatCollector.translateToLocal("trmtgtnh.update.available"));
        if (pieces == null) pieces = UpdateCheck.pieces(FALLBACK);
        IChatComponent root = Notices.line("", null, true);
        root.appendSibling(words(pieces[0] + newer + pieces[1] + running + pieces[2]));
        root.appendSibling(link("CurseForge", UpdateCheck.CURSEFORGE));
        root.appendSibling(words(pieces[3]));
        root.appendSibling(link("Modrinth", UpdateCheck.MODRINTH));
        root.appendSibling(words(pieces[4]));
        return root;
    }

    private static IChatComponent words(String text) {
        ChatComponentText out = new ChatComponentText(text);
        out.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.WHITE));
        return out;
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
