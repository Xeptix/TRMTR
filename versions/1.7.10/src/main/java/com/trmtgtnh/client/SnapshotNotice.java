package com.trmtgtnh.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

import com.trmtgtnh.Tags;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.server.Notices;
import com.trmtgtnh.util.Support;
import com.trmtgtnh.util.UpdateCheck;

/**
 * The snapshot message (0.9.223; Xep, 2026-10-10: "a message to players that its a snapshot build and to report issues
 * on the discord, and that I greatly appreciate them supporting me and becoming an early access member").
 *
 * <p>
 * Told by the client, about the client's own jar, because that is the build the player is trying - on a server
 * running a release as well as in a world of their own - and once a session, on the first world joined. Drawn as the
 * update notice is: the mark and the build in gold, then a dashed line each, the in-game invite on a line of its own,
 * every line inside the chat's width. Nothing at all for a release or a development build.
 */
public final class SnapshotNotice {

    /** Whether this session has been told, which is once. */
    private static boolean told;

    private SnapshotNotice() {}

    /** On the first world joined: tells the player, if this jar is an early build. */
    public static void joined(Minecraft mc) {
        if (told) return;
        told = true;
        if (!UpdateCheck.isEarly(Tags.VERSION)) return;
        show(mc, Tags.VERSION);
    }

    /** Puts the message in the chat for one build's name. The harness's own photograph calls this directly. */
    public static void show(Minecraft mc, String build) {
        if (mc == null || mc.thePlayer == null) return;
        for (IChatComponent each : lines(build)) mc.thePlayer.addChatMessage(each);
        Trmt.LOG.info("Snapshot notice: told the player that {} is an early build", build);
    }

    /** The message, a line each. */
    static List<IChatComponent> lines(String build) {
        List<IChatComponent> out = new ArrayList<IChatComponent>();
        String[] head = Support.cut(translate("trmtgtnh.snapshot.headline"), Support.SNAPSHOT_HEADLINE_FALLBACK, 1);
        IChatComponent root = Notices.line("", null, true);
        root.appendSibling(styled(head[0], EnumChatFormatting.WHITE, false));
        root.appendSibling(styled(build, EnumChatFormatting.GOLD, true));
        root.appendSibling(styled(head[1], EnumChatFormatting.WHITE, false));
        out.add(root);
        out.add(under(said("trmtgtnh.snapshot.early", Support.SNAPSHOT_EARLY_FALLBACK)));
        out.add(under(said("trmtgtnh.snapshot.report", Support.SNAPSHOT_REPORT_FALLBACK)));
        out.add(joined(styled("  ", EnumChatFormatting.WHITE, false), link()));
        out.add(under(said("trmtgtnh.snapshot.thanks", Support.SNAPSHOT_THANKS_FALLBACK)));
        out.add(
            joined(
                styled("    ", EnumChatFormatting.WHITE, false),
                styled(
                    said("trmtgtnh.snapshot.thanksMore", Support.SNAPSHOT_THANKS_MORE_FALLBACK),
                    EnumChatFormatting.WHITE,
                    false)));
        return out;
    }

    /** One dashed line under the headline. */
    private static IChatComponent under(String text) {
        return joined(
            styled("  - ", EnumChatFormatting.DARK_GRAY, false),
            styled(text, EnumChatFormatting.WHITE, false));
    }

    /** The in-game invite, as the update notice gives it. */
    private static IChatComponent link() {
        ChatComponentText out = new ChatComponentText(Support.DISCORD_IN_GAME_SHOWN);
        out.setChatStyle(
            new ChatStyle().setColor(EnumChatFormatting.AQUA)
                .setUnderlined(Boolean.TRUE)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, Support.DISCORD_IN_GAME))
                .setChatHoverEvent(
                    new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(Support.DISCORD_IN_GAME))));
        return out;
    }

    /** A sentence from the language file, or its fallback where the file has none or asks for a place. */
    private static String said(String key, String fallback) {
        String text = translate(key);
        return text == null || text.equals(key) || text.indexOf("%s") >= 0 ? fallback : text;
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
}
