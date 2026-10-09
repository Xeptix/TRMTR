package com.trmtgtnh.network;

import java.util.Arrays;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import com.trmtgtnh.config.ConfigFile;
import com.trmtgtnh.config.ConfigFile.Setting;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.ConfigReload;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.util.MainThread;
import com.trmtgtnh.util.MobEntries;

import io.netty.buffer.ByteBuf;

/**
 * A change to which entities wear the ground, asked for from the Wayfarer's screen.
 *
 * <p>
 * The entity twin of {@link PacketEditFamily}: it edits the {@code multipliers.mobs} list rather
 * than a block family. Simpler than the block editor because there is only the one list - a mob
 * either wears the ground or it does not, and how hard is a number the config screen still owns.
 *
 * <p>
 * Stopping a mob writes {@code Name:0} rather than taking its line out. Taking a line out only
 * works while nothing else names the mob: under a {@code *} line the mob fell straight back to
 * the wildcard's weight and went on wearing the ground, which is the opposite of what the button
 * says. A named line outranks the wildcard, so nought written against the name holds whatever else
 * the list carries, and it is written even when the mob was never listed. Every line that names
 * the mob, in any case, collapses into that one, so the list never holds two answers for it.
 *
 * <p>
 * Letting a mob wear the ground keeps a positive weight exactly as it is - {@code Villager:1.5}
 * says something more specific than this screen can, and a bare name would quietly discard the
 * number. The same goes for a {@code *:2} line that already counts the mob, which a bare name
 * appended beneath it would outrank at one. Only a mob the list counts for nothing, by a weight
 * of nought or below or by no line at all, is given the bare name at the ordinary weight.
 *
 * <p>
 * Only a name the entity registry holds is ever written, in the registry's own spelling. The
 * packet can be sent by anyone while {@code familyEditorOperatorsOnly} is off, and a name taken on
 * trust could be {@code *}, which would stop every mob at once, or could carry a line break into
 * the middle of Forge's list block. The magic tamper only ever sends a registered name, so nothing
 * honest is refused.
 *
 * <p>
 * The list is read for loose mobs only. A mob on a lead still wears the ground while
 * {@code multipliers.fromLeashedMobs} is on, because that counts anything being led whatever the
 * list says, and the chat line sent back says so rather than leaving the player to find out.
 */
public class PacketEditMob implements Message {

    /** Stop this entity wearing the ground while it walks loose. */
    public static final int ACTION_REMOVE = 0;

    /** Make this entity wear the ground, at the default weight unless it already has a positive one. */
    public static final int ACTION_ADD = 1;

    private static final int MAX_NAME = 128;

    private int action;
    private String entity = "";

    public PacketEditMob() {}

    public PacketEditMob(int action, String entity) {
        this.action = action;
        this.entity = entity;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readUnsignedByte();
        int length = buf.readUnsignedByte();
        if (length > MAX_NAME) length = MAX_NAME;
        byte[] raw = new byte[length];
        buf.readBytes(raw);
        entity = new String(raw, java.nio.charset.Charset.forName("UTF-8"));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action & 0xFF);
        byte[] raw = entity.getBytes(java.nio.charset.Charset.forName("UTF-8"));
        int length = Math.min(raw.length, MAX_NAME);
        buf.writeByte(length);
        buf.writeBytes(raw, 0, length);
    }

    /**
     * What one edit of the list came to.
     *
     * <p>
     * {@link #registered} is null when the name asked for is not a registered entity, and then
     * nothing else was looked at. {@link #before} and {@link #after} are null when the config or
     * its {@code mobs} property could not be found, and then nothing was changed either.
     */
    public static final class Outcome {

        /** The registry's spelling of the entity, or null when the registry does not know it. */
        public final String registered;

        /** Whether the property was set to a different list. */
        public final boolean changed;

        /** The list as it stood, or null when there was no list to read. */
        public final String[] before;

        /** The list as it now stands, or null when there was no list to read. */
        public final String[] after;

        Outcome(String registered, boolean changed, String[] before, String[] after) {
            this.registered = registered;
            this.changed = changed;
            this.before = before;
            this.after = after;
        }
    }

    /**
     * Rewrites the {@code multipliers.mobs} list for one entity, without saving or reloading.
     *
     * <p>
     * Kept apart from the packet so that a probe can run the edit on a server with nobody
     * connected and read back exactly what would be written. The property is set only when the
     * list actually changes; saving and reloading are left to the caller, which knows whether a
     * change is worth a reload. Call it on the server thread, as the handler does: the property it
     * sets is the one the server's reload reads.
     *
     * @param requestedEntity the name as asked for; matched against the entity registry
     * @param wears           true to let the entity wear the ground, false to write it at nought
     */
    public static Outcome edit(String requestedEntity, boolean wears) {
        String registered = registeredName(requestedEntity);
        if (registered == null) return new Outcome(null, false, null, null);

        ConfigFile config = TrmtConfig.raw();
        Setting property = config == null ? null : mobs(config);
        if (property == null) {
            Trmt.LOG
                .warn("Could not find the multipliers.mobs list in the config, so {} was left as it was", registered);
            return new Outcome(registered, false, null, null);
        }

        String[] before = property.getStringList();
        String[] after = wears ? MobEntries.wears(before, registered) : MobEntries.neverWears(before, registered);
        boolean changed = !Arrays.equals(before, after);
        if (changed) property.set(after);
        return new Outcome(registered, changed, before, after);
    }

    /**
     * The entity registry's own spelling of a name, or null when the registry does not hold it.
     *
     * <p>
     * An exact key wins; failing that the first key equal to it ignoring case, because the list
     * reader ignores case too and a name that differs only in its capitals means the same mob.
     * A blank, a {@code *} or a name carrying a control character is never a key, so all of them
     * come back null.
     */
    public static String registeredName(String requested) {
        if (requested == null) return null;
        String name = requested.trim();
        if (name.isEmpty()) return null;
        String looser = null;
        for (String key : com.trmtgtnh.util.EntityNames.all()) {
            if (key == null) continue;
            if (key.equals(name)) return key;
            if (looser == null && key.equalsIgnoreCase(name)) looser = key;
        }
        return looser;
    }

    private static Setting mobs(ConfigFile config) {
        if (!config.hasCategory(TrmtConfig.CATEGORY_MULTIPLIERS)) return null;
        Setting property = config.getCategory(TrmtConfig.CATEGORY_MULTIPLIERS)
            .get("mobs");
        return property != null && property.isList() ? property : null;
    }

    public static class Handler implements Receiver<PacketEditMob> {

        @Override
        public void onMessage(final PacketEditMob message, ServerPlayer from) {
            final ServerPlayer player = from;
            if (player == null) return;

            MainThread.onServer(new Runnable() {

                @Override
                public void run() {
                    // Asked again on the server whatever the client drew, exactly as the family
                    // editor is: the client is told its privileges so it knows what to show, and
                    // never believed about them.
                    if (TrmtConfig.familyEditorOperatorsOnly && !player.hasPermissions(2)) {
                        Trmt.LOG.warn(
                            "{} tried to change which entities wear the ground without permission; refused",
                            player.getName());
                        tell(player, ChatFormatting.RED, "trmtgtnh.mobedit.refused");
                        return;
                    }

                    if (message.action != ACTION_ADD && message.action != ACTION_REMOVE) return;
                    boolean add = message.action == ACTION_ADD;

                    Outcome outcome = edit(message.entity, add);
                    if (outcome.registered == null) {
                        String asked = printable(message.entity);
                        Trmt.LOG.warn(
                            "{} asked to change whether '{}' wears the ground, which is not a registered entity name; the list was left alone",
                            player.getName(),
                            asked);
                        tell(player, ChatFormatting.RED, "trmtgtnh.mobedit.unknown", asked);
                        return;
                    }
                    // The property went missing: edit() has already said so in the log, and there
                    // is nothing true to tell the player about the mob.
                    if (outcome.after == null) return;

                    if (outcome.changed) {
                        TrmtConfig.save();
                        ConfigReload.fromGuiDeferred();
                    }

                    String name = outcome.registered;
                    if (add) {
                        // Nought for players abandons every step before a mob's weight is ever
                        // read, so the ordinary line would promise something that cannot happen.
                        if (TrmtConfig.multiplierPlayer <= 0f) {
                            tell(player, ChatFormatting.YELLOW, "trmtgtnh.mobedit.wears.nobody", name);
                        } else {
                            tell(player, ChatFormatting.AQUA, "trmtgtnh.mobedit.wears", name);
                        }
                    } else {
                        // A led mob is weighed by the lead before the list is read, so it goes on
                        // wearing the ground; said only when that is actually so.
                        if (TrmtConfig.erodeFromLeashedMobs && TrmtConfig.multiplierLeashed > 0f
                            && TrmtConfig.multiplierPlayer > 0f) {
                            tell(player, ChatFormatting.AQUA, "trmtgtnh.mobedit.never.leashed", name);
                        } else {
                            tell(player, ChatFormatting.AQUA, "trmtgtnh.mobedit.never", name);
                        }
                    }

                    Trmt.LOG.info(
                        "{} set {} to {} ({}); multipliers.mobs now reads: {}",
                        player.getName(),
                        name,
                        add ? "wear the ground" : "never wear the ground while loose",
                        outcome.changed ? "changed" : "unchanged",
                        linesNaming(outcome.after, name));
                }
            });
            return;
        }
    }

    /** Every line of the list that names this entity, joined, or "(none)" when no line does. */
    private static String linesNaming(String[] list, String name) {
        StringBuilder joined = new StringBuilder();
        for (String line : list) {
            MobEntries.Entry entry = MobEntries.parse(line);
            if (entry == null || !entry.name.equalsIgnoreCase(name)) continue;
            if (joined.length() > 0) joined.append(", ");
            joined.append(line.trim());
        }
        return joined.length() == 0 ? "(none)" : joined.toString();
    }

    /**
     * A name as sent, with control characters shown as '?', for echoing into the log and chat.
     * A refused name is still somebody else's text, and a line break in it would forge a log line.
     */
    private static String printable(String raw) {
        if (raw == null) return "";
        StringBuilder shown = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            shown.append(Character.isISOControl(c) ? '?' : c);
        }
        return shown.toString();
    }

    private static void tell(ServerPlayer player, ChatFormatting color, String key, Object... args) {
        player.sendMessage(
            new TextComponent(color + com.trmtgtnh.util.Translate.get(key, args)),
            net.minecraft.Util.NIL_UUID);
    }
}
