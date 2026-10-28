package com.trmtgtnh.client;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.util.OlderServer;

/**
 * A newer client on an older server is told so, once a visit (0.9.228; Xep: "A newer client on an older server - Add
 * it, every edition"): the server runs an older build, and some of what the player sees there may differ from it.
 *
 * <p>
 * The newer client is the one speaking, so the words are its own language file's, with the English carried in
 * {@link OlderServer} behind them. Decided when the server's rules arrive, after they are applied: the rules' last tail
 * carries the server's protocol and build, and a server with none is 0.9.227 or older. Said once a visit - the flag
 * set when said and cleared as the client leaves the world and drops the server's rules ({@link ClientSide#leaveWorld})
 * - so rules sent again on a reload say nothing again. A server without this mod sends no rules and is never spoken
 * of, and the client's own single-player or LAN server is the same build, which is never older.
 */
public final class OlderServerNotice {

    /** Whether this visit has been told, which is once. */
    private static boolean told;

    private OlderServerNotice() {}

    /** The server's rules arrived: tells the player, if the server is older and this visit has not been told. */
    public static void rulesArrived(boolean tail, int protocol, String build) {
        if (told) return;
        if (Trmt.runningServer() != null) return;
        if (!OlderServer.older(tail, protocol, build, com.trmtgtnh.network.TrmtNetwork.PROTOCOL, Trmt.version())) return;
        told = true;
        show(Minecraft.getInstance(), tail, build);
    }

    /** Leaving a world: the next visit is told afresh. */
    public static void leave() {
        told = false;
    }

    /** Puts the lines in the chat. The harness's own photograph calls this directly. */
    public static void show(Minecraft mc, boolean tail, String build) {
        if (mc == null || mc.player == null) return;
        List<String> lines = OlderServer.lines(tail, Trmt.version(), build);
        for (int i = 0; i < lines.size(); i++) {
            mc.player.sendSystemMessage(i == 0 ? headline(lines.get(i)) : under(lines.get(i)));
        }
        Trmt.LOG.info(
            "Older server: told the player that this server runs {}, older than this client's {}",
            tail ? build : "0.9.227 or older",
            Trmt.version());
    }

    /** The headline: this mod's mark dark gray and the words gray, as the 1.7.10 edition's are. */
    private static Component headline(String line) {
        MutableComponent root = Component.literal("");
        String mark = OlderServer.PREFIX;
        String words = line.startsWith(mark) ? line.substring(mark.length()) : line;
        root.append(styled(mark, ChatFormatting.DARK_GRAY));
        root.append(styled(words, ChatFormatting.GRAY));
        return root;
    }

    /** A dashed line: the dash dark gray, the words gray, as the 1.7.10 edition's are. */
    private static Component under(String line) {
        MutableComponent root = Component.literal("");
        String dash = OlderServer.UNDER;
        String words = line.startsWith(dash) ? line.substring(dash.length()) : line;
        root.append(styled(dash, ChatFormatting.DARK_GRAY));
        root.append(styled(words, ChatFormatting.GRAY));
        return root;
    }

    private static MutableComponent styled(String text, ChatFormatting color) {
        return Component.literal(text)
            .setStyle(Style.EMPTY.applyFormat(color));
    }
}
