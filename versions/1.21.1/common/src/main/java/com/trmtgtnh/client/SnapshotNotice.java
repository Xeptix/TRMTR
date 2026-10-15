package com.trmtgtnh.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.MutableComponent;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.server.Notices;
import com.trmtgtnh.util.Support;
import com.trmtgtnh.util.Translate;
import com.trmtgtnh.util.UpdateCheck;

/**
 * The snapshot message, as the 1.7.10 edition's SnapshotNotice tells it (0.9.223; Xep, 2026-10-10: "a message to
 * players that its a snapshot build and to report issues on the discord, and that I greatly appreciate them
 * supporting me and becoming an early access member"), on both loaders.
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
        if (!UpdateCheck.isEarly(Trmt.version())) return;
        show(mc, Trmt.version());
    }

    /** Puts the message in the chat for one build's name. The harness's own photograph calls this directly. */
    public static void show(Minecraft mc, String build) {
        if (mc == null || mc.player == null) return;
        // The second argument is who said it: nobody, the mod talking.
        for (Component each : lines(build)) mc.player.sendSystemMessage(each);
        Trmt.LOG.info("Snapshot notice: told the player that {} is an early build", build);
    }

    /** The message, a line each. */
    static List<Component> lines(String build) {
        List<Component> out = new ArrayList<>();
        String[] head = Support.cut(Translate.get("trmtgtnh.snapshot.headline"), Support.SNAPSHOT_HEADLINE_FALLBACK, 1);
        MutableComponent root = Component.literal("").append(Notices.line("", null, true));
        root.append(styled(head[0], ChatFormatting.WHITE, false));
        root.append(styled(build, ChatFormatting.GOLD, true));
        root.append(styled(head[1], ChatFormatting.WHITE, false));
        out.add(root);
        out.add(under(said("trmtgtnh.snapshot.early", Support.SNAPSHOT_EARLY_FALLBACK)));
        out.add(under(said("trmtgtnh.snapshot.report", Support.SNAPSHOT_REPORT_FALLBACK)));
        out.add(joined(styled("  ", ChatFormatting.WHITE, false), link()));
        out.add(under(said("trmtgtnh.snapshot.thanks", Support.SNAPSHOT_THANKS_FALLBACK)));
        out.add(
            joined(
                styled("    ", ChatFormatting.WHITE, false),
                styled(
                    said("trmtgtnh.snapshot.thanksMore", Support.SNAPSHOT_THANKS_MORE_FALLBACK),
                    ChatFormatting.WHITE,
                    false)));
        return out;
    }

    /** One dashed line under the headline. */
    private static Component under(String text) {
        return joined(styled("  - ", ChatFormatting.DARK_GRAY, false), styled(text, ChatFormatting.WHITE, false));
    }

    /** The in-game invite, as the update notice gives it. */
    private static Component link() {
        return Component.literal(Support.DISCORD_IN_GAME_SHOWN).setStyle(
            Style.EMPTY.applyFormat(ChatFormatting.AQUA)
                .applyFormat(ChatFormatting.UNDERLINE)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, Support.DISCORD_IN_GAME))
                .withHoverEvent(
                    new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(Support.DISCORD_IN_GAME))));
    }

    /** A sentence from the language file, or its fallback where the file has none or asks for a place. */
    private static String said(String key, String fallback) {
        String text = Translate.get(key);
        return text == null || text.equals(key) || text.indexOf("%s") >= 0 ? fallback : text;
    }

    private static Component styled(String text, ChatFormatting color, boolean bold) {
        Style style = Style.EMPTY.applyFormat(color);
        return Component.literal(text).setStyle(bold ? style.applyFormat(ChatFormatting.BOLD) : style);
    }

    private static Component joined(Component... parts) {
        MutableComponent root = Component.literal("");
        for (Component part : parts) root.append(part);
        return root;
    }
}
