package com.trmtgtnh.server;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import com.trmtgtnh.Tags;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.util.MainThread;
import com.trmtgtnh.util.UpdateCheck;

/**
 * Tells whoever can update the mod, as they join, that a newer release is out - the half of the update
 * notice that knows this edition's server. The other half, what is newer and when to ask, is
 * {@link UpdateCheck}, shared by every edition. A port of the 1.7.10 edition's class of the same name,
 * which explains the choices: who can update it, why the single-player owner is found by connection,
 * and why the line is formatted here and sent straight to the player.
 */
public final class UpdateNotice {

    /** This jar's line in the version file. */
    public static final String EDITION = "1.12.2-forge";

    /** The sentence, with its four places, in case a language file has none or the wrong number of them. */
    private static final String FALLBACK = "%s is out - this world is running %s. Get it from %s or %s.";

    /** The known-issues sentence, the same way. */
    private static final String BAD_FALLBACK = "This world is running %s, which has known issues - %s is the build to use. Get it from %s or %s.";

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
        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        if (server == null || server.getPlayerList() == null) return;
        for (EntityPlayerMP each : server.getPlayerList()
            .getPlayers()) {
            if (!(each instanceof FakePlayer)) tell(each);
        }
    }

    private static void tell(EntityPlayerMP player) {
        UpdateCheck.Answer answer = UpdateCheck.last();
        if (answer == null || answer.newer == null) return;
        if (TOLD.contains(player.getUniqueID()) || !canUpdate(player)) return;
        TOLD.add(player.getUniqueID());
        ITextComponent said = line(answer.newer, answer.running, answer.bad);
        player.sendMessage(said);
        Trmt.LOG.info(
            answer.bad ? "Update notice: told {} that this build has known issues, and {} is the one to use"
                : "Update notice: told {} that {} is out",
            player.getName(),
            answer.newer);
        // Under the test address, the line as it was sent, so a run can read where both links go
        // without anybody clicking them.
        if (System.getProperty(UpdateCheck.TEST_ADDRESS) != null) {
            Trmt.LOG.info("Update notice: the line as sent - {}", ITextComponent.Serializer.componentToJson(said));
        }
    }

    /** Whether this player can act on the notice, by the setting. */
    static boolean canUpdate(EntityPlayerMP player) {
        String setting = TrmtConfig.updateNotice == null ? "" : TrmtConfig.updateNotice.trim();
        if (TrmtConfig.UPDATE_OFF.equalsIgnoreCase(setting)) return false;
        if (TrmtConfig.UPDATE_EVERYONE.equalsIgnoreCase(setting)) return true;
        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        if (server == null) return false;
        if (!server.isDedicatedServer()) {
            // The integrated server's own player is the one on the in-memory connection.
            return player.connection != null && player.connection.netManager != null
                && player.connection.netManager.isLocalChannel();
        }
        return server.getPlayerList()
            .canSendCommands(player.getGameProfile());
    }

    /** The line: the mod's mark, the sentence, and the two links in their places. */
    @SuppressWarnings("deprecation")
    static ITextComponent line(String newer, String running) {
        return line(newer, running, false);
    }

    /**
     * The line, or - for a build marked bad (Xep, 2026-10-08) - the known-issues line: the running build first, then
     * the one to use, which may be older.
     */
    @SuppressWarnings("deprecation")
    static ITextComponent line(String newer, String running, boolean bad) {
        String[] pieces = UpdateCheck.pieces(
            net.minecraft.util.text.translation.I18n
                .translateToLocal(bad ? "trmtgtnh.update.bad" : "trmtgtnh.update.available"));
        if (pieces == null) pieces = UpdateCheck.pieces(bad ? BAD_FALLBACK : FALLBACK);
        ITextComponent root = Notices.line("", null, true);
        root.appendSibling(words(pieces[0] + (bad ? running : newer) + pieces[1] + (bad ? newer : running) + pieces[2]));
        root.appendSibling(link("CurseForge", UpdateCheck.CURSEFORGE));
        root.appendSibling(words(pieces[3]));
        root.appendSibling(link("Modrinth", UpdateCheck.MODRINTH));
        root.appendSibling(words(pieces[4]));
        return root;
    }

    private static ITextComponent words(String text) {
        TextComponentString out = new TextComponentString(text);
        out.setStyle(new Style().setColor(TextFormatting.WHITE));
        return out;
    }

    private static ITextComponent link(String name, String address) {
        TextComponentString out = new TextComponentString(name);
        out.setStyle(
            new Style().setColor(TextFormatting.AQUA)
                .setUnderlined(Boolean.TRUE)
                .setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, address))
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(address))));
        return out;
    }

    /** Whether this is a development game - a dev run reports versions that are not releases. */
    private static boolean development() {
        Object said = net.minecraft.launchwrapper.Launch.blackboard == null ? null
            : net.minecraft.launchwrapper.Launch.blackboard.get("fml.deobfuscatedEnvironment");
        return Boolean.TRUE.equals(said);
    }
}
