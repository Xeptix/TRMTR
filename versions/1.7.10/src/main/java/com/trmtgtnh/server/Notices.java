package com.trmtgtnh.server;

import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.util.FakePlayer;

/**
 * The one door a line of chat about a gesture goes out through.
 *
 * <p>
 * Kept apart from any one tool because more than one tool now has something to say about the same
 * thing - what a mend was short of - and a player holding right-click with two of them in turn
 * should not be told twice as often as with one. The limit lives with the map it needs, and every
 * caller shares both.
 *
 * <p>
 * Server side, and careful about it. Everything that goes into a line is formatted here, before it
 * leaves, and sent as plain text: a client one version behind has no translation for a key this
 * version added, and would show the key itself.
 */
public final class Notices {

    /** How long a player is left in peace after being told something, in ticks. */
    private static final int QUIET_TICKS = 20;

    /**
     * Last tick each player was told something. Weak so a disconnected player drops out without
     * any bookkeeping; this only ever runs on the server thread, so an unsynchronised map is
     * correct here.
     */
    private static final WeakHashMap<EntityPlayer, int[]> lastTold = new WeakHashMap<EntityPlayer, int[]>();

    private Notices() {}

    /**
     * One line, at most once a second per player.
     *
     * <p>
     * Holding right-click repeats five times a second, and a player standing on the wrong ground
     * without the material would otherwise be told so five times a second until they stopped.
     *
     * <p>
     * Nobody is told anything when there is nobody to tell. A null player is a gesture made by no
     * one, and a fake player is a machine: a block breaker or a dispenser has no chat window, and
     * keeping a place for it in the map would only hold a line back from nobody.
     */
    public static void say(EntityPlayer player, IChatComponent line) {
        if (player == null || line == null || player instanceof FakePlayer) return;
        int[] when = lastTold.get(player);
        if (when != null && player.ticksExisted - when[0] < QUIET_TICKS) return;
        if (when == null) {
            lastTold.put(player, new int[] { player.ticksExisted });
        } else {
            when[0] = player.ticksExisted;
        }
        player.addChatMessage(line);
    }

    /**
     * A line of already formatted text, in a colour, with the mod's mark in front when asked for.
     *
     * <p>
     * The mark is a component of its own with its own colour, rather than a colour code typed into
     * the front of the text. The client closes every component with a reset when it draws the line,
     * so a colour typed into the mark would stop at the mark's end and never reach the text after it,
     * and a colour typed into the text would be the only thing holding the two apart.
     *
     * @param text     the words, already translated; colour codes inside it still work
     * @param colour   the colour of the words, or null to leave them to whatever codes they carry
     * @param prefixed whether to put {@code [TRMT]} in front
     */
    public static IChatComponent line(String text, EnumChatFormatting colour, boolean prefixed) {
        ChatComponentText body = new ChatComponentText(text == null ? "" : text);
        if (colour != null) body.setChatStyle(new ChatStyle().setColor(colour));
        if (!prefixed) return body;

        // An empty root holding both, so the words inherit nothing from the mark's style.
        ChatComponentText root = new ChatComponentText("");
        ChatComponentText mark = new ChatComponentText("[TRMT] ");
        mark.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.DARK_GRAY));
        root.appendSibling(mark);
        root.appendSibling(body);
        return root;
    }
}
