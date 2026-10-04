package com.trmtgtnh.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.ConfigReload;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.util.MainThread;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * A client offering its own settings to the server, which decides whether to take them.
 *
 * <p>
 * Everything here is written on the assumption that the sender is hostile, because a packet is
 * the one thing in this mod anybody can forge. Four rules follow from that and none of them is
 * negotiable.
 *
 * <p>
 * <b>The permission check runs after the hand-over, not before.</b> The check and the writes it allows
 * happen in one task, so nothing can change the answer between them. In 1.7.10 the handler itself
 * already runs on the server thread, but ahead of the rest of that tick's work: a check made there would
 * let through a push whose sender had lost operator status to a command later in the same tick, typed
 * in another player's chat or at the console. From 1.8 to 1.12.2 handlers run on the network thread,
 * where the player's permission state would be read while the server tick that owns it may be writing
 * it. Either way the question is asked where the answer is owned, at the moment the settings change.
 *
 * <p>
 * <b>The client never says whether it is an operator.</b> It cannot: a client's own
 * {@code canCommandSenderUseCommand} answers from a permission level it does not have, so the
 * question is only ever asked of the server's copy of the player.
 *
 * <p>
 * <b>Only settings that already exist are written.</b> A name that is not already a property of
 * this mod's config is dropped rather than created, so no amount of invention on the other end
 * can add a category, a key, or a value of a type nothing reads.
 *
 * <p>
 * <b>The size is bounded before anything is read.</b> A custom payload larger than 32k breaks
 * the connection of whoever sent it, and one merely enormous would have the server parse it.
 */
public class PacketPushConfig implements IMessage {

    /** Enough for every setting this mod has, and far short of the payload limit. */
    private static final int MAX_LINES = 512;

    /**
     * The longest one setting may be on the wire, in bytes.
     *
     * <p>
     * Raised from a quarter of that, and the length prefix widened with it, once lists became
     * pushable: a family's successor list is a handful of words, but the same code path carries
     * the block lists detection writes, and those run to thousands of characters. A line that does
     * not fit is dropped by the sender rather than cut short here, because half a block list
     * applied to a server's config would be worse than none of it.
     */
    private static final int MAX_LINE = 1024;

    /** The permission this asks for. Level two, the same as the mod's own command. */
    private static final String PERMISSION_NODE = "trmt";

    private List<String> lines = new ArrayList<String>();

    public PacketPushConfig() {}

    public PacketPushConfig(List<String> lines) {
        this.lines = lines;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readUnsignedShort();
        if (count > MAX_LINES) count = MAX_LINES;
        lines = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            int length = buf.readUnsignedShort();
            if (length > MAX_LINE) length = MAX_LINE;
            byte[] raw = new byte[length];
            buf.readBytes(raw);
            lines.add(new String(raw, java.nio.charset.Charset.forName("UTF-8")));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int count = Math.min(lines.size(), MAX_LINES);
        buf.writeShort(count);
        for (int i = 0; i < count; i++) {
            byte[] raw = lines.get(i)
                .getBytes(java.nio.charset.Charset.forName("UTF-8"));
            int length = Math.min(raw.length, MAX_LINE);
            buf.writeShort(length);
            buf.writeBytes(raw, 0, length);
        }
    }

    /**
     * Whether a setting is the kind that may be handed from one machine to another at all.
     *
     * <p>
     * Asked at both ends - by the sender so it never offers one, and by the server so a sender that
     * does is refused - because four kinds of setting belong to the machine they are written on and
     * mean something wrong anywhere else. The client heading is this player's own screen and says so
     * in its comment. The quality chooser and surfaces.maxTexturedSurfaces, the one figure it moves
     * outside that heading, are as much this machine's own: nothing on a server reads either, and on a
     * world opened to LAN the server is the host's own game, whose file a guest's push would rewrite.
     * The presets' bookkeeping records what one particular file last did. And the
     * enchantment and potion ids are pinned by an operator to the slots that world's own tools were
     * enchanted at: a client still on the default handed its minus one to the server, which took the
     * first free slot at its next start and stripped the enchantment off every tool made under the
     * pinned one.
     */
    public static boolean travels(String category, String key) {
        if (category == null || key == null) return false;
        if (TrmtConfig.CATEGORY_CLIENT.equals(category)) return false;
        if (category.startsWith(com.trmtgtnh.config.Presets.CATEGORY + Configuration.CATEGORY_SPLITTER)) return false;
        // The quality chooser and the one figure it moves outside the client heading are as much this
        // machine's own as the heading is: nothing on a server reads either, and on a world opened to
        // LAN the server is the host's own game, so a guest's push would set the host's quality.
        if (com.trmtgtnh.config.Presets.CATEGORY.equals(category) && "quality".equals(key)) return false;
        if (TrmtConfig.CATEGORY_SURFACES.equals(category) && "maxTexturedSurfaces".equals(key)) return false;
        return !"enchantId".equals(key) && !"lightnessPotionId".equals(key) && !"heavyFootPotionId".equals(key);
    }

    /** Whether one line will survive the wire whole, so a sender can drop it rather than cut it. */
    public static boolean fits(String line) {
        return line != null && line.getBytes(java.nio.charset.Charset.forName("UTF-8")).length <= MAX_LINE;
    }

    public static class Handler implements IMessageHandler<PacketPushConfig, IMessage> {

        @Override
        public IMessage onMessage(final PacketPushConfig message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            MainThread.onServer(new Runnable() {

                @Override
                public void run() {
                    // Asked here, on the thread that owns the answer, and asked of the server's
                    // player rather than of anything the packet said.
                    if (!player.canCommandSenderUseCommand(2, PERMISSION_NODE)) {
                        Trmt.LOG.warn(
                            "{} offered a config push without permission; refused",
                            player.getCommandSenderName());
                        say(player, EnumChatFormatting.RED, "You are not allowed to change this server's settings.");
                        return;
                    }

                    Configuration config = TrmtConfig.raw();
                    if (config == null) {
                        say(player, EnumChatFormatting.RED, "The server has no config loaded to change.");
                        return;
                    }

                    int applied = 0;
                    int refused = 0;
                    for (String line : message.lines) {
                        int firstTab = line.indexOf('\t');
                        int secondTab = firstTab < 0 ? -1 : line.indexOf('\t', firstTab + 1);
                        if (firstTab <= 0 || secondTab <= firstTab) {
                            refused++;
                            continue;
                        }
                        String category = line.substring(0, firstTab);
                        String key = line.substring(firstTab + 1, secondTab);
                        String value = line.substring(secondTab + 1);

                        if (!config.hasCategory(category)) {
                            refused++;
                            continue;
                        }
                        // Refused here as well as never offered, so a sender that offers one anyway
                        // cannot write another machine's settings over this one's. See travels.
                        if (!travels(category, key)) {
                            refused++;
                            continue;
                        }
                        ConfigCategory section = config.getCategory(category);
                        Property property = section.get(key);
                        // Existing properties only. Anything else would let the other end invent
                        // settings this mod never declared and would never read.
                        // A list is fine now. Whether a setting is one is the SERVER's
                        // property's answer rather than the sender's, so nothing about the wire
                        // decides what shape a setting has here.
                        if (property == null) {
                            refused++;
                            continue;
                        }
                        if (!assign(property, value)) {
                            refused++;
                            continue;
                        }
                        applied++;
                    }

                    if (applied > 0) {
                        TrmtConfig.save();
                        // The same path the config screen uses, so a pushed change lands exactly
                        // as a locally made one does rather than through a second reload route.
                        ConfigReload.fromGuiDeferred();
                    }
                    Trmt.LOG.info(
                        "{} pushed {} settings to the server, {} refused",
                        player.getCommandSenderName(),
                        Integer.valueOf(applied),
                        Integer.valueOf(refused));
                    say(
                        player,
                        EnumChatFormatting.AQUA,
                        "Applied " + applied
                            + " settings to the server"
                            + (refused > 0 ? ", " + refused + " refused" : "")
                            + ".");
                }
            });
            return null;
        }

        /** Writes one value through the property's own declared type, or refuses it. */
        private static boolean assign(Property property, String value) {
            try {
                if (property.isList()) {
                    // Empty means an empty list rather than a list holding one empty
                    // entry, which is what a plain split gives and what would leave a
                    // stray blank line in the file.
                    property.set(value.isEmpty() ? new String[0] : value.split("\u001f", -1));
                    return true;
                }
                switch (property.getType()) {
                    case BOOLEAN:
                        property.set(Boolean.parseBoolean(value));
                        return true;
                    case INTEGER:
                        property.set(Integer.parseInt(value.trim()));
                        return true;
                    case DOUBLE:
                        property.set(Double.parseDouble(value.trim()));
                        return true;
                    case STRING:
                        property.set(value);
                        return true;
                    default:
                        return false;
                }
            } catch (NumberFormatException notThatType) {
                return false;
            }
        }

        private static void say(EntityPlayerMP player, EnumChatFormatting colour, String message) {
            player.addChatMessage(new ChatComponentText(colour + message));
        }
    }
}
