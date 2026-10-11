package com.trmtgtnh.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

import com.trmtgtnh.Tags;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.server.Notices;
import com.trmtgtnh.util.Support;
import com.trmtgtnh.util.UpdateCheck;

/**
 * The snapshot message, as the 1.7.10 edition's SnapshotNotice tells it (0.9.223; Xep, 2026-10-10: "a message to
 * players that its a snapshot build and to report issues on the discord, and that I greatly appreciate them
 * supporting me and becoming an early access member").
 *
 * <p>
 * Told by the client, about the client's own jar, once a session on the first world joined, and nothing at all for a
 * release or a development build. Drawn as the update notice is, every line inside the chat's width.
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
        if (mc == null || mc.player == null) return;
        for (ITextComponent each : lines(build)) mc.player.sendMessage(each);
        Trmt.LOG.info("Snapshot notice: told the player that {} is an early build", build);
    }

    /** The message, a line each. */
    static List<ITextComponent> lines(String build) {
        List<ITextComponent> out = new ArrayList<ITextComponent>();
        String[] head = Support.cut(translate("trmtgtnh.snapshot.headline"), Support.SNAPSHOT_HEADLINE_FALLBACK, 1);
        ITextComponent root = Notices.line("", null, true);
        root.appendSibling(styled(head[0], TextFormatting.WHITE, false));
        root.appendSibling(styled(build, TextFormatting.GOLD, true));
        root.appendSibling(styled(head[1], TextFormatting.WHITE, false));
        out.add(root);
        out.add(under(said("trmtgtnh.snapshot.early", Support.SNAPSHOT_EARLY_FALLBACK)));
        out.add(under(said("trmtgtnh.snapshot.report", Support.SNAPSHOT_REPORT_FALLBACK)));
        out.add(joined(styled("  ", TextFormatting.WHITE, false), link()));
        out.add(under(said("trmtgtnh.snapshot.thanks", Support.SNAPSHOT_THANKS_FALLBACK)));
        out.add(
            joined(
                styled("    ", TextFormatting.WHITE, false),
                styled(
                    said("trmtgtnh.snapshot.thanksMore", Support.SNAPSHOT_THANKS_MORE_FALLBACK),
                    TextFormatting.WHITE,
                    false)));
        return out;
    }

    /** One dashed line under the headline. */
    private static ITextComponent under(String text) {
        return joined(styled("  - ", TextFormatting.DARK_GRAY, false), styled(text, TextFormatting.WHITE, false));
    }

    /** The in-game invite, as the update notice gives it. */
    private static ITextComponent link() {
        TextComponentString out = new TextComponentString(Support.DISCORD_IN_GAME_SHOWN);
        out.setStyle(
            new Style().setColor(TextFormatting.AQUA)
                .setUnderlined(Boolean.TRUE)
                .setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, Support.DISCORD_IN_GAME))
                .setHoverEvent(
                    new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(Support.DISCORD_IN_GAME))));
        return out;
    }

    /** A sentence from the language file, or its fallback where the file has none or asks for a place. */
    private static String said(String key, String fallback) {
        String text = translate(key);
        return text == null || text.equals(key) || text.indexOf("%s") >= 0 ? fallback : text;
    }

    @SuppressWarnings("deprecation")
    private static String translate(String key) {
        return net.minecraft.util.text.translation.I18n.translateToLocal(key);
    }

    private static ITextComponent styled(String text, TextFormatting color, boolean bold) {
        TextComponentString out = new TextComponentString(text);
        out.setStyle(
            new Style().setColor(color)
                .setBold(Boolean.valueOf(bold)));
        return out;
    }

    private static ITextComponent joined(ITextComponent... parts) {
        ITextComponent root = new TextComponentString("");
        for (ITextComponent part : parts) root.appendSibling(part);
        return root;
    }
}
