package com.trmtgtnh.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

import com.trmtgtnh.Tags;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.util.ServerBuild;

/**
 * What a newer client tells its player on a server running an older build of this mod (0.9.228; Xep: "Add it, every
 * edition"): once a visit, after the server's rules, that the server runs an older build and some of what they see may
 * differ from it.
 *
 * <p>
 * The newer client is the one speaking, so the words are its own language file's, with English fallbacks here for a
 * file that has none - the way the snapshot message's are. A gray headline in this mod's style, then a dashed line
 * each, every line inside the chat's 320 pixels with the longest build names this project makes. Said once a visit:
 * the flag is set when it is said, rules sent again say nothing, and it is cleared where the client drops the server's
 * rules on leaving ({@code ClientProxy.handBack}). A server without this mod sends no rules and is never spoken of;
 * single player never differs.
 */
public final class OlderServerNotice {

    static final String HEADLINE_FALLBACK = "This server runs an older build.";

    static final String SILENT_FALLBACK = "the server's %s or older.";

    static final String YOURS_FALLBACK = "Yours is %s,";

    static final String THEIRS_FALLBACK = "the server's %s.";

    static final String DIFFER_FALLBACK = "Some of what you see here may differ from it.";

    /** Whether this visit has been told, which is once. */
    private static boolean told;

    private OlderServerNotice() {}

    /** After a server's rules: tells the player once a visit, if the server runs an older build. */
    public static void considered(Minecraft mc, boolean tailSent, int serverProtocol, String serverBuild) {
        if (told) return;
        if (!ServerBuild.older(tailSent, serverProtocol, serverBuild, TrmtNetwork.PROTOCOL, Tags.VERSION)) return;
        told = true;
        show(mc, Tags.VERSION, tailSent ? serverBuild : null);
    }

    /** On leaving a server, where its rules are dropped: the next visit may be told again. */
    public static void left() {
        told = false;
    }

    /** Puts the message in the chat. The harness's own photograph calls this directly. */
    public static void show(Minecraft mc, String yours, String theirs) {
        if (mc == null || mc.thePlayer == null) return;
        for (IChatComponent each : lines(yours, theirs)) mc.thePlayer.addChatMessage(each);
        Trmt.LOG.info(
            "Older server notice: told the player that this server runs an older build than {} ({})",
            yours,
            theirs == null ? "it sent no build, so " + ServerBuild.LAST_WITHOUT_TAIL + " or older" : theirs);
    }

    /** The message, a line each: the headline, the two builds, and what it means. */
    static List<IChatComponent> lines(String yours, String theirs) {
        List<IChatComponent> out = new ArrayList<IChatComponent>();
        IChatComponent root = new ChatComponentText("");
        root.appendSibling(styled("[TRMT: Reimagined] ", EnumChatFormatting.DARK_GRAY));
        root.appendSibling(
            styled(said("trmtgtnh.olderServer.headline", HEADLINE_FALLBACK, 0), EnumChatFormatting.GRAY));
        out.add(root);
        // Two lines whether the server named its build or not: one sentence holding both builds overflows the chat with
        // a snapshot's name in it (0.9.228-snapshot.12 against a silent server is 334 pixels).
        out.add(under(placed(said("trmtgtnh.olderServer.yours", YOURS_FALLBACK, 1), yours)));
        if (theirs == null) {
            out.add(
                under(placed(said("trmtgtnh.olderServer.silent", SILENT_FALLBACK, 1), ServerBuild.LAST_WITHOUT_TAIL)));
        } else {
            out.add(under(placed(said("trmtgtnh.olderServer.theirs", THEIRS_FALLBACK, 1), theirs)));
        }
        out.add(under(said("trmtgtnh.olderServer.differ", DIFFER_FALLBACK, 0)));
        return out;
    }

    /** One dashed line under the headline. */
    private static IChatComponent under(String text) {
        IChatComponent root = new ChatComponentText("");
        root.appendSibling(styled("  - ", EnumChatFormatting.DARK_GRAY));
        root.appendSibling(styled(text, EnumChatFormatting.GRAY));
        return root;
    }

    /**
     * A sentence from the language file, or its fallback where the file has none or holds a different number of places
     * than the sentence fills.
     */
    private static String said(String key, String fallback, int places) {
        String text = translate(key);
        return text == null || text.equals(key) || count(text) != places ? fallback : text;
    }

    /** How many {@code %s} a sentence holds. */
    public static int count(String text) {
        int found = 0;
        for (int at = text.indexOf("%s"); at >= 0; at = text.indexOf("%s", at + 2)) found++;
        return found;
    }

    /** The sentence with each {@code %s} filled in turn, and nothing else in it read as a format. */
    public static String placed(String sentence, String... fills) {
        StringBuilder out = new StringBuilder();
        int from = 0;
        for (String fill : fills) {
            int at = sentence.indexOf("%s", from);
            if (at < 0) break;
            out.append(sentence, from, at)
                .append(fill);
            from = at + 2;
        }
        return out.append(sentence.substring(from))
            .toString();
    }

    @SuppressWarnings("deprecation")
    private static String translate(String key) {
        return StatCollector.translateToLocal(key);
    }

    private static IChatComponent styled(String text, EnumChatFormatting color) {
        ChatComponentText out = new ChatComponentText(text);
        out.setChatStyle(new ChatStyle().setColor(color));
        return out;
    }
}
