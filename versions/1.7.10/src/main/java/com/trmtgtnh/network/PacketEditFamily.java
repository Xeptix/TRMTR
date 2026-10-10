package com.trmtgtnh.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.ConfigReload;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.util.MainThread;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * A change to which blocks wear, asked for from the magic tamper's screen.
 *
 * <p>
 * The awkward truth this has to work around: detection writes what it finds back into the family
 * lists and never removes an entry, so taking a name out of a family list does not retract it -
 * the next load puts it straight back. The list that actually retracts a block is
 * {@code surfaces.exclude}, which is consulted by both the detection path and the list path.
 *
 * <p>
 * So the two directions write to two different places, and that asymmetry is the feature rather
 * than an accident: "never wear this" adds to the exclude list, and "wear as this" adds to the
 * family's own list and takes the name back out of exclude.
 *
 * <p>
 * Entries carrying a metadata - {@code modid:block:7} - are left exactly where they are. They say
 * something more specific than this screen can, and a rewrite that flattened them to bare names
 * would quietly widen somebody's careful list to every variant of the block.
 */
public class PacketEditFamily implements IMessage {

    /** Never wear this block, whatever detection decides. */
    public static final int ACTION_EXCLUDE = 0;

    /** Wear this block as the named family. */
    public static final int ACTION_ASSIGN = 1;

    private static final int MAX_NAME = 128;

    private int action;
    private String block = "";
    private int family;

    public PacketEditFamily() {}

    public PacketEditFamily(int action, String block, int family) {
        this.action = action;
        this.block = block;
        this.family = family;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readUnsignedByte();
        family = buf.readUnsignedByte();
        int length = buf.readUnsignedByte();
        if (length > MAX_NAME) length = MAX_NAME;
        byte[] raw = new byte[length];
        buf.readBytes(raw);
        block = new String(raw, java.nio.charset.Charset.forName("UTF-8"));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action & 0xFF);
        buf.writeByte(family & 0xFF);
        byte[] raw = block.getBytes(java.nio.charset.Charset.forName("UTF-8"));
        int length = Math.min(raw.length, MAX_NAME);
        buf.writeByte(length);
        buf.writeBytes(raw, 0, length);
    }

    public static class Handler implements IMessageHandler<PacketEditFamily, IMessage> {

        @Override
        public IMessage onMessage(final PacketEditFamily message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            MainThread.onServer(new Runnable() {

                @Override
                public void run() {
                    // Asked here and asked again, whatever the client was told when it drew the
                    // button. The client is sent its privileges so it knows what to show; it is
                    // never believed about them.
                    if (TrmtConfig.familyEditorOperatorsOnly && !player.canCommandSenderUseCommand(2, "trmt")) {
                        Trmt.LOG.warn(
                            "{} tried to change which blocks wear without permission; refused",
                            player.getCommandSenderName());
                        say(player, EnumChatFormatting.RED, "You are not allowed to change that on this server.");
                        return;
                    }

                    Configuration config = TrmtConfig.raw();
                    String name = message.block == null ? "" : message.block.trim();
                    if (config == null || name.isEmpty()) return;
                    // A block that is not installed is a name somebody made up. Nothing here
                    // needs to be clever about it; it simply is not written.
                    if (net.minecraft.block.Block.getBlockFromName(name) == null) {
                        say(player, EnumChatFormatting.RED, name + " is not a block on this server.");
                        return;
                    }

                    // Only the two actions there are. Anything else fell into the assign branch, edited
                    // the exclude list, and then failed on a family it had never checked.
                    if (message.action != ACTION_ASSIGN && message.action != ACTION_EXCLUDE) return;
                    SurfaceFamily family = SurfaceFamily.byOrdinal(message.family);
                    if (message.action == ACTION_ASSIGN && (family == null || !family.staged)) return;

                    if (message.action == ACTION_EXCLUDE) {
                        addTo(config, TrmtConfig.CATEGORY_SURFACES, "exclude", name);
                        say(player, EnumChatFormatting.AQUA, name + " will no longer wear.");
                    } else {
                        removeFrom(config, TrmtConfig.CATEGORY_SURFACES, "exclude", name);
                        // Out of every other family's list too. The lists are read in the families' order and the later
                        // wins, so a block detection had written into a later family went on wearing as that one
                        // whatever this said, until 0.9.222 (spec CF66, SD25). A bare name only, as below.
                        for (SurfaceFamily other : SurfaceFamily.values()) {
                            if (other == family) continue;
                            removeFrom(
                                config,
                                TrmtConfig.CATEGORY_FAMILIES + Configuration.CATEGORY_SPLITTER + other.key(),
                                "blocks",
                                name);
                        }
                        addTo(
                            config,
                            TrmtConfig.CATEGORY_FAMILIES + Configuration.CATEGORY_SPLITTER + family.key(),
                            "blocks",
                            name);
                        say(player, EnumChatFormatting.AQUA, name + " will wear as " + family.key() + ".");
                    }

                    TrmtConfig.save();
                    ConfigReload.fromGuiDeferred();
                    Trmt.LOG.info(
                        "{} set {} to {}",
                        player.getCommandSenderName(),
                        name,
                        message.action == ACTION_EXCLUDE ? "never wear" : family.key());
                }
            });
            return null;
        }

        private static void addTo(Configuration config, String category, String key, String name) {
            if (!config.hasCategory(category)) return;
            Property property = config.getCategory(category)
                .get(key);
            if (property == null || !property.isList()) return;
            List<String> values = new ArrayList<String>(Arrays.asList(property.getStringList()));
            for (String existing : values) {
                if (existing != null && existing.trim()
                    .equalsIgnoreCase(name)) return;
            }
            values.add(name);
            property.set(values.toArray(new String[values.size()]));
        }

        /**
         * Takes a bare name out of a list, and only a bare one.
         *
         * <p>
         * An entry with a metadata on the end is a narrower statement than this screen can make,
         * so it is left alone rather than swept up by a name match.
         */
        private static void removeFrom(Configuration config, String category, String key, String name) {
            if (!config.hasCategory(category)) return;
            Property property = config.getCategory(category)
                .get(key);
            if (property == null || !property.isList()) return;
            List<String> kept = new ArrayList<String>();
            boolean changed = false;
            for (String existing : property.getStringList()) {
                if (existing != null && existing.trim()
                    .toLowerCase(Locale.ROOT)
                    .equals(name.toLowerCase(Locale.ROOT))) {
                    changed = true;
                    continue;
                }
                kept.add(existing);
            }
            if (changed) property.set(kept.toArray(new String[kept.size()]));
        }

        private static void say(EntityPlayerMP player, EnumChatFormatting color, String message) {
            player.addChatMessage(new ChatComponentText(color + message));
        }
    }
}
