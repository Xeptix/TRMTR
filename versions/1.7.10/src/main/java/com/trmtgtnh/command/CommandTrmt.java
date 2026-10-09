package com.trmtgtnh.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.ModBlocks;
import com.trmtgtnh.config.ConfigReload;
import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ChunkErosionData;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.erosion.ErosionEngine;
import com.trmtgtnh.erosion.ErosionEntry;
import com.trmtgtnh.erosion.ErosionStore;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;
import com.trmtgtnh.surface.SurfaceShape;
import com.trmtgtnh.util.BlockEntry;

/**
 * Runtime control: {@code /trmt status|enable|disable|purge|reload|surfaces|here}.
 *
 * <p>
 * The switches here are the server-side half of the toggles. Disabling stops accumulation
 * and clears every client's overlay but keeps the stored data, so it can be turned straight
 * back on; purge is the one that actually discards anything.
 */
public class CommandTrmt extends CommandBase {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
        "status",
        "enable",
        "disable",
        "purge",
        "reload",
        "surfaces",
        "here",
        "golem",
        "showcase",
        "demonstrate",
        "mapcolor");

    @Override
    public String getCommandName() {
        return "trmt";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/trmt <status|enable|disable|purge|reload|surfaces [family]|here|golem|mapcolor|showcase [radius]|demonstrate [NxN|NxNxN] [y=<h>] [max=<n>|all] [book|snake|radial] [cleararea[=n]] [samearea[=x,z]] [realdemo[=<w>x<l>]] [nogolems] [quick=<n>] [quicktp=<n>] [warded[=h|p|h+p]] [reinforced[=0-3]] [tight] [overwrite] [frozen] [tp]>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, SUBCOMMANDS.toArray(new String[0]));
        return null;
    }

    /**
     * Runs the command on the server thread, whichever thread asked.
     *
     * <p>
     * A command can arrive on a thread other than the server's: from a chat bridge or web panel that calls
     * the command manager from a thread of its own, or over RCon where a mod has mended vanilla's listener,
     * which otherwise closes the connection straight after the login. GTNH's Hodgepodge moves RCon's back,
     * but this mod runs without Hodgepodge, and nearly everything here touches state only the server
     * thread may. A reload rebuilt and published the surface table and announced it to every player from
     * that thread while the server thread went on answering the table requests the last announcement
     * had prompted. The two share one channel, and the player a message is addressed to is read off that
     * channel at the moment of writing, so a table could be delivered to the wrong player, an older table
     * could arrive after the rules naming a newer one, and one table's bytes could be cached under
     * another's fingerprint - each of them lasting for the rest of a visit. The whole command is handed
     * to the server thread instead, and runs at the end of the tick in progress.
     */
    @Override
    public void processCommand(ICommandSender sender, final String[] args) {
        if (Trmt.runningServer() != null && !Trmt.onServerThread()) {
            reply(
                sender,
                EnumChatFormatting.GRAY,
                "Handed to the server thread, which runs it at the end of this tick; what it says goes to the server log as well.");
            // A player keeps their own chat, which hears a late answer perfectly well. Anything else is
            // assumed to have stopped listening, which RCon and most bridges have.
            final ICommandSender answer = sender instanceof net.minecraft.entity.player.EntityPlayer ? sender
                : new LateSender(sender);
            com.trmtgtnh.util.MainThread.onServer(new Runnable() {

                @Override
                public void run() {
                    runReporting(answer, args);
                }
            });
            return;
        }
        runCommand(sender, args);
    }

    /**
     * Runs a command handed over from another thread, saying what went wrong the way the game would have.
     *
     * <p>
     * The game's command handler turns a refusal into a red line for whoever asked, and it is not on the
     * stack any more by the time a handed-over command runs. Without this a mistyped subcommand sent from
     * another thread would surface only as a stack trace from the queue.
     */
    private void runReporting(ICommandSender sender, String[] args) {
        try {
            runCommand(sender, args);
        } catch (WrongUsageException usage) {
            net.minecraft.util.ChatComponentTranslation line = new net.minecraft.util.ChatComponentTranslation(
                "commands.generic.usage",
                new net.minecraft.util.ChatComponentTranslation(usage.getMessage(), usage.getErrorOjbects()));
            line.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            sender.addChatMessage(line);
        } catch (net.minecraft.command.CommandException refused) {
            net.minecraft.util.ChatComponentTranslation line = new net.minecraft.util.ChatComponentTranslation(
                refused.getMessage(),
                refused.getErrorOjbects());
            line.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            sender.addChatMessage(line);
        }
    }

    /**
     * Whoever asked for a command that is only now running, with everything said to them logged as well.
     *
     * <p>
     * RCon gathers what a command says while the command runs and sends it back the moment the command
     * returns, and a bridge does much the same, so an answer given later lands where nobody reads it.
     * Passed on regardless, and written to the server log, where whoever sent it can find it.
     */
    private static final class LateSender implements ICommandSender {

        /**
         * A color or style code, stripped from what goes to the log.
         *
         * <p>
         * Stripped here rather than by the game's own helper for it, which exists only on a client: a
         * dedicated server has it taken out when the class loads, and calling it there stopped the server
         * the first time a command handed over from another thread said anything.
         */
        private static final java.util.regex.Pattern COLOR_CODES = java.util.regex.Pattern
            .compile("(?i)\u00a7[0-9a-fk-or]");

        private final ICommandSender asked;

        LateSender(ICommandSender asked) {
            this.asked = asked;
        }

        @Override
        public String getCommandSenderName() {
            return asked.getCommandSenderName();
        }

        @Override
        public net.minecraft.util.IChatComponent func_145748_c_() {
            return asked.func_145748_c_();
        }

        @Override
        public void addChatMessage(net.minecraft.util.IChatComponent message) {
            asked.addChatMessage(message);
            if (message == null) return;
            Trmt.LOG.info(
                "[/trmt for {}] {}",
                asked.getCommandSenderName(),
                COLOR_CODES.matcher(message.getUnformattedText())
                    .replaceAll(""));
        }

        @Override
        public boolean canCommandSenderUseCommand(int level, String command) {
            return asked.canCommandSenderUseCommand(level, command);
        }

        @Override
        public net.minecraft.util.ChunkCoordinates getPlayerCoordinates() {
            return asked.getPlayerCoordinates();
        }

        @Override
        public World getEntityWorld() {
            return asked.getEntityWorld();
        }
    }

    private void runCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) throw new WrongUsageException(getCommandUsage(sender));
        String sub = args[0].toLowerCase(Locale.ROOT);

        if ("status".equals(sub)) {
            status(sender);
        } else if ("enable".equals(sub)) {
            TrmtConfig.setEnabled(true);
            // Every connected player who should see wear is subscribed again and sent what is around
            // them now, rather than left with the overlay a disable cleared - or, for anybody who
            // joined while it was off, with nothing at all until they reconnected.
            TrmtNetwork.reconcileSubscriptions();
            reply(
                sender,
                EnumChatFormatting.GREEN,
                "Erosion enabled. Every connected player who should see wear has been sent what is around them.");
        } else if ("disable".equals(sub)) {
            TrmtConfig.setEnabled(false);
            TrmtNetwork.broadcastClearAll();
            // After the clear, which reaches only players still subscribed. Letting them go is what
            // makes enabling again send each of them their surroundings rather than nothing.
            TrmtNetwork.reconcileSubscriptions();
            reply(
                sender,
                EnumChatFormatting.YELLOW,
                "Erosion disabled and every client's overlay cleared. The stored wear is kept rather than thrown away, so nothing has to be walked in again. Recovery is measured against the world clock, though, and that clock counts time whether erosion is on or off, so a long spell disabled reads as a long spell of nobody walking there: chunks heal as they reload, and the first sweep after /trmt enable pays out the rest. Purge is the one that throws wear away.");
        } else if ("purge".equals(sub)) {
            purge(sender);
        } else if ("mapcolor".equals(sub)) {
            reportMapColor(sender);
        } else if ("reload".equals(sub)) {
            ConfigReload.Delta delta = ConfigReload.fromDisk();
            if (delta == null) {
                reply(
                    sender,
                    EnumChatFormatting.RED,
                    "Config could not be read; nothing changed and nothing was written. See the log.");
                return;
            }
            reply(sender, EnumChatFormatting.GREEN, "Config re-read from disk and surfaces re-detected.");
            if (delta.textures) {
                reply(
                    sender,
                    EnumChatFormatting.GRAY,
                    "Wear textures changed. They are built by whatever draws the world, so in a world of your own that is this machine, and it will stop responding for up to half a minute while they are rebuilt. A dedicated server draws nothing and so rebuilds nothing, and every connected player builds their wear from their own config, so an edit here does not reach them. The only part of that picture a server hands out is how finely ground wears through, and a change to that is what makes a connected player rebuild.");
            }
            if (delta.surfaces) {
                reply(
                    sender,
                    EnumChatFormatting.GRAY,
                    "Which blocks erode has changed. Ground that is no longer tracked clears as the healing sweep reaches it, because the drop that notices a surface has gone rides along with recovery rather than running on a pass of its own - so it needs healing switched on. With healing off nothing sweeps, and the stale records stay in the store until it comes back. Nothing wrong is drawn in the meantime, since no client paints wear onto ground it no longer recognises, but the wear is still written down and comes back looking exactly as it did if those blocks are ever put back on the list. /trmt purge clears the chunks that are loaded now, if that is not wanted.");
            }
        } else if ("surfaces".equals(sub)) {
            surfaces(sender, args);
        } else if ("here".equals(sub)) {
            here(sender);
        } else if ("golem".equals(sub)) {
            golem(sender);
        } else if ("showcase".equals(sub)) {
            showcase(sender, args);
        } else if ("demonstrate".equals(sub)) {
            demonstrate(sender, args);
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private void status(ICommandSender sender) {
        reply(
            sender,
            EnumChatFormatting.AQUA,
            Trmt.NAME + ": "
                + (TrmtConfig.enabled ? "enabled" : "disabled")
                + ", healing "
                + (TrmtConfig.healingEnabled ? "on" : "off"));
        reply(
            sender,
            EnumChatFormatting.GRAY,
            "  global x" + TrmtConfig.globalSpeed
                + ", erosion x"
                + TrmtConfig.erosionSpeed
                + ", healing x"
                + TrmtConfig.healingRate);
        reply(
            sender,
            EnumChatFormatting.GRAY,
            "  physical decay: " + TrmtConfig.physicalDecay
                + (TrmtConfig.physicalDecayCollides() ? " (clients must show path visuals)" : ""));
        reply(
            sender,
            EnumChatFormatting.GRAY,
            "  " + ErosionStore.get()
                .describe() + ", subscribers=" + TrmtNetwork.subscriberCount());

        // Said here because the config text for the two id settings promises it, and because
        // it is the one diagnostic that settles a disagreement about which number an effect is.
        reply(
            sender,
            EnumChatFormatting.GRAY,
            "  potions: " + com.trmtgtnh.item.ModPotions.describe()
                + (TrmtConfig.potionsEnabled ? "" : " (effects off)"));

        StringBuilder families = new StringBuilder("  families:");
        for (SurfaceFamily family : SurfaceFamily.values()) {
            FamilySettings settings = TrmtConfig.family(family);
            if (settings == null) continue;
            families.append(' ')
                .append(family.key())
                .append('=')
                .append(settings.enabled ? "on" : "off");
        }
        reply(sender, EnumChatFormatting.GRAY, families.toString());
    }

    private void purge(ICommandSender sender) {
        int positions = ErosionStore.get()
            .trackedPositionCount();
        ErosionStore.get()
            .clearAll();
        TrmtNetwork.broadcastClearAll();
        reply(
            sender,
            EnumChatFormatting.YELLOW,
            "Purged " + positions
                + " tracked positions from loaded chunks. Unloaded chunks keep theirs until they load; run this again after visiting them, or disable the mod instead.");
    }

    private void surfaces(ICommandSender sender, String[] args) {
        List<SurfaceRegistry.SurfaceState> states = SurfaceRegistry.texturableStates();
        SurfaceFamily filter = null;
        if (args.length > 1) {
            filter = SurfaceFamily.byKey(args[1]);
            // Refused rather than quietly ignored, because an unrecognised key and "no filter was
            // asked for" are the same null. The reply is cut off well short of the whole list and
            // the entries arrive in no particular order, so a mistyped family used to answer with a
            // sample of everything under a heading that named no family at all; somebody checking
            // whether one block of theirs is tracked could read that, not find it, and come away
            // certain it is not, when all that happened was that "cobblestone" is spelt "cobble".
            // There is no tab completion past the subcommand to spell the keys out, so the refusal
            // names them. Only the staged families are named, being the only ones that ever appear
            // in this list; a family that is real but switched off is still accepted, so that it
            // can answer plainly that nothing of it was detected.
            if (filter == null) {
                StringBuilder known = new StringBuilder();
                for (SurfaceFamily family : SurfaceFamily.staged()) {
                    if (known.length() > 0) known.append(", ");
                    known.append(family.key());
                }
                reply(
                    sender,
                    EnumChatFormatting.RED,
                    "There is no surface family called " + args[1]
                        + ". The ones this list can be narrowed to are: "
                        + known
                        + ".");
                return;
            }
        }

        List<String> lines = new ArrayList<String>();
        for (SurfaceRegistry.SurfaceState state : states) {
            if (filter != null && state.family != filter) continue;
            lines.add(state.family.key() + "  " + state.registryName);
        }

        reply(
            sender,
            EnumChatFormatting.AQUA,
            "Detected " + lines.size() + " erodable blocks" + (filter == null ? "" : " in family " + filter.key()));
        int shown = Math.min(lines.size(), 40);
        for (int i = 0; i < shown; i++) {
            reply(sender, EnumChatFormatting.GRAY, "  " + lines.get(i));
        }
        if (shown < lines.size()) {
            reply(
                sender,
                EnumChatFormatting.DARK_GRAY,
                "  ...and " + (lines.size() - shown) + " more; narrow it with /trmt surfaces <family>");
        }
    }

    private void here(ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP)) {
            reply(sender, EnumChatFormatting.RED, "Only a player can ask about the block they are standing on.");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        World world = player.worldObj;
        int x = MathHelper.floor_double(player.posX);
        int y = MathHelper.floor_double(player.posY) - 1;
        int z = MathHelper.floor_double(player.posZ);

        SurfaceFamily family = SurfaceRegistry.familyOf(world.getBlock(x, y, z), world.getBlockMetadata(x, y, z));
        String name = SurfaceRegistry.registryName(world.getBlock(x, y, z));

        reply(
            sender,
            EnumChatFormatting.AQUA,
            "At " + x + "," + y + "," + z + ": " + name + " -> " + (family == null ? "not erodable" : family.key()));

        ErosionEntry entry = ErosionStore.get()
            .getEntry(world, x, y, z);
        if (entry == null) {
            reply(sender, EnumChatFormatting.GRAY, "  no wear recorded");
            return;
        }
        int elapsedDays = (ErosionEngine.nowSeconds(world) - entry.getLastTouchedSeconds()) / 1200;
        reply(
            sender,
            EnumChatFormatting.GRAY,
            "  appearance=" + entry.getFamily()
                .key()
                + " stage="
                + entry.getStage()
                + " wear="
                + String.format(Locale.ROOT, "%.2f", Float.valueOf(entry.getWear()))
                + "/"
                // The effective one, not the stored draw: the speed setting and any reinforcement
                // both move the finish line, and a readout showing wear past a threshold it has
                // not actually reached is worse than no readout.
                + String.format(Locale.ROOT, "%.2f", Float.valueOf(entry.effectiveThreshold()))
                + " untouched="
                + elapsedDays
                + "d");
    }

    /**
     * Why the golem in front of you is not eating what you have thrown at it.
     *
     * <p>
     * Feeding a golem is quiet by design - the material goes in, the container comes back, and the
     * reinforcement it paid for is invisible - so a golem that is refusing and a golem that is
     * working look very nearly the same from outside, and the six conditions it refuses on are all
     * silent. This says which of them is not met, in the order the golem asks them.
     *
     * <p>
     * It ends on the item in your hand, which is the condition a pack is most likely to fail. What
     * counts as reinforcing material is a list of names, and a name that no mod in this pack answers
     * to is indistinguishable, from inside, from a material nobody is carrying. So the list is
     * printed with what each entry actually resolves to, what the held item actually is, and - when
     * the two do not meet - the exact line to add to make them.
     */
    private void golem(ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP)) {
            reply(sender, EnumChatFormatting.RED, "Only a player can stand in front of a golem.");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        World world = player.worldObj;

        List<String> off = new ArrayList<String>();
        if (!TrmtConfig.golemReinforces) off.add("golem.reinforces");
        if (!TrmtConfig.reinforceEnabled) off.add("reinforce.enabled");
        if (!TrmtConfig.golemPickup) off.add("golem.pickUpDrops");
        if (off.isEmpty()) {
            reply(sender, EnumChatFormatting.AQUA, "Feeding golems is switched on.");
        } else {
            reply(
                sender,
                EnumChatFormatting.RED,
                "Feeding golems is switched off by " + join(off)
                    + ". Nothing below matters until that is on: a golem notices thrown material only through the sweep that picks up what its work shakes loose.");
        }

        reportGolem(sender, world, player);
        reportHeld(sender, player.getHeldItem());
    }

    /** The nearest golem's own answers, in the order it asks them of itself. */
    private void reportGolem(ICommandSender sender, World world, EntityPlayerMP player) {
        @SuppressWarnings("unchecked")
        List<com.trmtgtnh.entity.EntityGolemOfWays> near = world.getEntitiesWithinAABB(
            com.trmtgtnh.entity.EntityGolemOfWays.class,
            player.boundingBox.expand(24D, 12D, 24D));
        com.trmtgtnh.entity.EntityGolemOfWays golem = null;
        double best = Double.MAX_VALUE;
        for (com.trmtgtnh.entity.EntityGolemOfWays one : near) {
            if (!one.isEntityAlive()) continue;
            double away = one.getDistanceSqToEntity(player);
            if (away < best) {
                best = away;
                golem = one;
            }
        }
        if (golem == null) {
            reply(sender, EnumChatFormatting.GRAY, "No golem within twenty-four blocks, so there is none to ask.");
            return;
        }

        int[] home = golem.anchor();
        reply(
            sender,
            EnumChatFormatting.AQUA,
            "Golem " + (int) Math.sqrt(best)
                + " blocks away, home at "
                + home[0]
                + ","
                + home[1]
                + ","
                + home[2]
                + ", round "
                + golem.workRadius()
                + " blocks.");

        boolean ordered = golem.hasOrders();
        reply(
            sender,
            ordered ? EnumChatFormatting.GRAY : EnumChatFormatting.RED,
            ordered ? "  orders: yes"
                : "  orders: none. It has been told to keep nothing, so it takes no bite: laying happens inside a stroke of work, and a golem with no work does no strokes.");

        int tool = golem.findTool();
        ItemStack tamper = tool < 0 ? null : golem.inventory()[tool];
        reply(
            sender,
            tool >= 0 ? EnumChatFormatting.GRAY : EnumChatFormatting.RED,
            tool >= 0 ? "  tamper: " + tamper.getDisplayName()
                : "  tamper: none. It cannot lay what it eats without one, so it does not eat.");

        if (ordered && tool >= 0) {
            int[] square = com.trmtgtnh.entity.GolemMasonry.somewhereToLay(golem);
            reply(
                sender,
                square != null ? EnumChatFormatting.GRAY : EnumChatFormatting.YELLOW,
                square != null
                    ? "  somewhere to lay: yes, " + square[0] + "," + square[1] + "," + square[2] + " wants reinforcing"
                    : "  somewhere to lay: none found in " + com.trmtgtnh.entity.GolemMasonry.looks()
                        + " looks at its round. It refuses to eat what it could not put down. Ground already reinforced to the ceiling, ground of no family it wears, and ground with something standing on it are all passed over.");
        }

        reply(
            sender,
            EnumChatFormatting.GRAY,
            "  mouth: " + (golem.masonryInMouth() == null ? "empty"
                : "chewing " + golem.masonryInMouth()
                    .getDisplayName())
                + ", holding "
                + golem.masonryReady()
                + " mouthful(s) ready to lay");
        if (golem.masonrySulk() > 0) {
            reply(
                sender,
                EnumChatFormatting.YELLOW,
                "  waiting " + (golem.masonrySulk() / 20)
                    + "s before it looks at its round again, having found nowhere to lay.");
        }
        reply(
            sender,
            EnumChatFormatting.DARK_GRAY,
            "  It notices material only within about two and a half blocks of itself, once a stroke, and never goes to fetch it - its round has to carry it past.");
    }

    /** What the held item is, what the list asks for, and the line that would join them. */
    private void reportHeld(ICommandSender sender, ItemStack held) {
        if (held == null) {
            reply(sender, EnumChatFormatting.GRAY, "Nothing in your hand to ask about.");
            return;
        }
        Object name = net.minecraft.item.Item.itemRegistry.getNameForObject(held.getItem());
        reply(
            sender,
            EnumChatFormatting.AQUA,
            "In your hand: " + held.getDisplayName() + "  (" + name + ", meta " + held.getItemDamage() + ")");

        String fluid = com.trmtgtnh.server.ReinforceCost.fluidNameOf(held);
        if (fluid == null) {
            reply(
                sender,
                EnumChatFormatting.GRAY,
                "  nothing has registered it as a container of any fluid, so no fluid: entry can ever name it - it has to be named as an item.");
        } else {
            reply(
                sender,
                EnumChatFormatting.GRAY,
                "  it holds " + com.trmtgtnh.server.ReinforceCost.fluidAmountOf(held)
                    + "L of the fluid registered as '"
                    + fluid
                    + "'");
        }

        reply(sender, EnumChatFormatting.GRAY, "  reinforce.materials, in the order it is tried:");
        for (String raw : TrmtConfig.reinforceMaterials) {
            if (raw == null || raw.trim()
                .isEmpty()) continue;
            String entry = raw.trim();
            boolean exists = com.trmtgtnh.server.ReinforceCost.entryExists(entry);
            reply(
                sender,
                exists ? EnumChatFormatting.GRAY : EnumChatFormatting.YELLOW,
                "    " + entry + (exists ? "" : "  - nothing in this game answers to that name"));
        }

        String paid = com.trmtgtnh.server.ReinforceCost.entryFor(held);
        if (paid != null) {
            reply(
                sender,
                EnumChatFormatting.GREEN,
                "  This pays, by the entry " + paid + ". A golem with orders and a tamper will eat it.");
            return;
        }
        String suggestion = fluid != null ? "fluid:" + fluid
            : name + (held.getItemDamage() == 0 ? "" : ":" + held.getItemDamage());
        reply(
            sender,
            EnumChatFormatting.RED,
            "  Nothing in that list names it, so no golem will eat it and no tamper will pay with it. Add " + suggestion
                + " to reinforce.materials to make it count.");
    }

    private static String join(List<String> names) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) out.append(i == names.size() - 1 ? " and " : ", ");
            out.append(names.get(i));
        }
        return out.toString();
    }

    /**
     * Wears a patch of ground around the player through every stage at once, so the whole
     * chain can be looked at side by side without walking a route two hundred times.
     *
     * <p>
     * Nothing here is special-cased: it writes ordinary wear entries through the same path
     * traffic uses, so what it shows is exactly what a real path will look like. It heals away
     * on the usual schedule, and {@code /trmt purge} clears it immediately.
     */
    private void showcase(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            reply(sender, EnumChatFormatting.RED, "Only a player can lay out a showcase.");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        World world = player.worldObj;

        int radius = 6;
        if (args.length > 1) {
            try {
                radius = Math.max(1, Math.min(16, Integer.parseInt(args[1])));
            } catch (NumberFormatException bad) {
                throw new WrongUsageException("/trmt showcase [radius 1-16]");
            }
        }

        int centreX = MathHelper.floor_double(player.posX);
        int centreZ = MathHelper.floor_double(player.posZ);
        int centreY = MathHelper.floor_double(player.posY) - 1;

        int painted = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = centreX + dx;
                int z = centreZ + dz;
                // Find the surface within a few blocks of the player's feet, so a showcase on
                // uneven ground still lands on the ground.
                for (int dy = 2; dy >= -3; dy--) {
                    int y = centreY + dy;
                    if (y < 0 || y > 255) continue;
                    SurfaceFamily family = SurfaceRegistry
                        .familyOf(world.getBlock(x, y, z), world.getBlockMetadata(x, y, z));
                    if (family == null || !family.staged) continue;
                    if (world.getBlock(x, y + 1, z)
                        .isOpaqueCube()) continue;

                    // Stripe the stages across one axis so every gradation is visible at once.
                    int steps = ErosionChain.length(family);
                    if (steps <= 0) break;
                    int target = ((dx + radius) * steps) / (radius * 2 + 1);
                    if (ErosionEngine.get()
                        .forceStage(world, x, y, z, family, target)) painted++;
                    break;
                }
            }
        }

        reply(
            sender,
            EnumChatFormatting.AQUA,
            "Laid out " + painted
                + " worn blocks around you, one gradation to each line across the patch, stepped evenly along the run. A run holds many more gradations than a patch this wide has lines, so what is here is an even sample of the run rather than the whole of it, and it stops a little short of the heaviest gradation. Widen the radius for a finer sample, or use /trmt demonstrate, which builds a platform per surface and, given a platform big enough, holds every step of a run. Use /trmt purge to clear them.");
    }

    // ------------------------------------------------------------------
    // demonstrate
    // ------------------------------------------------------------------

    private static final int DEMO_DEFAULT_SIZE = 6;

    private static final int DEMO_DEFAULT_HEIGHT = 2;

    private static final int DEMO_MAX_SIZE = 16;

    /** How far above the player's head the demo is built. */
    private static final int DEMO_CLEARANCE = 12;

    /**
     * How many platforms get built when no count is asked for.
     *
     * <p>
     * A default rather than a rule. It exists because the number of surfaces a pack detects
     * is the pack's business and could be anything, not because any particular number is
     * right - so it is always said out loud when it bites, and max=&lt;n&gt; or 'all' lifts it.
     */
    /** How far a 'cleararea' reaches when no distance is given. */
    private static final int DEMO_CLEAR_DEFAULT = 64;

    /**
     * The most positions one clear may touch.
     *
     * <p>
     * A clear is a box, and a box grows fast: sixty-four blocks of margin around a demo of any
     * size is millions of positions, all of them inside the tick the command was typed in. The
     * margin is pulled in until the work fits rather than the command being refused, because
     * what is lost is the outermost ring of a margin whose whole job is to be empty anyway.
     */
    private static final int DEMO_CLEAR_MAX_POSITIONS = 1200000;

    /**
     * The most chunks one demo may bring into memory.
     *
     * <p>
     * Generous enough for every shape these commands make and short of the point where loading
     * becomes generating half a region. A demo asked for somewhere absurd builds in whatever is
     * already there rather than making the world bigger to suit itself.
     */
    private static final int DEMO_MAX_CHUNKS = 1024;

    private static final int DEMO_MAX_PLATFORMS = 96;

    /**
     * Builds one platform per detected surface in the sky, each stepping from untouched ground
     * to fully worn.
     *
     * <p>
     * {@code showcase} wears whatever is already underfoot, which is the honest test on real
     * terrain but shows only the surfaces that happen to be there. This builds an exhibit
     * instead: every block the mod has detected, side by side, at every gradation, which is
     * what you want when checking whether a change looks right on all of them at once.
     *
     * <p>
     * The first square of each platform is left untouched deliberately. The chain's own first
     * step is already visible wear, so without a pristine square beside it there is nothing to
     * judge the first gradation against.
     */
    /** "h", "p" or "h+p" as ward bits: 1 hostile, 2 passive. Null when it reads as neither. */
    private static Integer wardFlagsOf(String text) {
        if (text == null) return null;
        String value = text.trim()
            .toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return null;
        int flags = 0;
        for (String part : value.split("\\+")) {
            String piece = part.trim();
            if ("h".equals(piece) || "hostile".equals(piece)) {
                flags |= 0x1;
            } else if ("p".equals(piece) || "passive".equals(piece)) {
                flags |= 0x2;
            } else {
                return null;
            }
        }
        return flags == 0 ? null : Integer.valueOf(flags);
    }

    /**
     * Puts a ward and a reinforcement on one demo block, making the record if it has none.
     *
     * <p>
     * A pristine corner of a platform has no wear and therefore no record, and it still wants
     * warding - so an invisible entry is made for it, exactly as reinforcing unworn ground does.
     */
    private static void markDemoBlock(World world, int x, int y, int z, int ward, int reinforce) {
        if (ward == 0 && reinforce <= 0) return;
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        ChunkErosionData data = ErosionStore.get()
            .getOrCreateChunk(world.provider.dimensionId, chunkX, chunkZ);
        int key = com.trmtgtnh.erosion.ErosionKey.packWorld(x, y, z);
        com.trmtgtnh.erosion.ErosionEntry entry = data.get(key);
        if (entry == null) {
            SurfaceFamily family = SurfaceRegistry.familyOf(world.getBlock(x, y, z), world.getBlockMetadata(x, y, z));
            if (family == null || !family.staged) family = SurfaceFamily.DIRT;
            // Its threshold is a stand-in until the square is first walked on, when the engine draws the
            // family's own.
            entry = new com.trmtgtnh.erosion.ErosionEntry(
                family,
                ErosionEntry.UNDRAWN_THRESHOLD,
                com.trmtgtnh.erosion.ErosionEngine.nowSeconds(world));
            data.put(key, entry);
        }
        if (ward != 0) entry.setWard(ward);
        if (reinforce > 0) entry.setReinforce(reinforce);
        data.markDirty();
        ErosionStore.get()
            .markModified(world, chunkX, chunkZ);
    }

    private void demonstrate(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            reply(sender, EnumChatFormatting.RED, "Only a player has somewhere for this to be built above.");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        World world = player.worldObj;

        // Read before anything else, because both of these decide whether the rest of the
        // arguments are even ours to parse.
        for (int i = 1; i < args.length; i++) {
            String arg = args[i].toLowerCase(Locale.ROOT);
            boolean teleportOnly = arg.startsWith("quicktp");
            if (!teleportOnly && !arg.startsWith("quick")) continue;

            int split = arg.indexOf('=');
            if (split < 0) {
                explainQuick(sender, teleportOnly);
                return;
            }
            List<Integer> chosen = quickSelection(arg.substring(split + 1));
            if (chosen == null) {
                explainQuick(sender, teleportOnly);
                return;
            }

            if (teleportOnly) {
                int[] at = quickAnchor(
                    chosen.get(0)
                        .intValue() - 1);
                player.mountEntity(null);
                player.fallDistance = 0f;
                player.setPositionAndUpdate(at[0] + 0.5D, at[1] + 4.0D, at[2] + 0.5D);
                reply(
                    sender,
                    EnumChatFormatting.AQUA,
                    "Moved to demo " + chosen.get(0) + " at " + at[0] + ", " + at[2] + ".");
                return;
            }

            for (Integer which : chosen) {
                int index = which.intValue() - 1;
                String[] preset = QUICK[index];
                int[] at = quickAnchorFor(index);
                String[] full = new String[preset.length + 3];
                full[0] = "demonstrate";
                System.arraycopy(preset, 0, full, 1, preset.length);
                // Placed rather than named: the preset says what the demo is, the index says
                // where it goes, and the two cannot drift into each other.
                full[full.length - 2] = "samearea=" + at[0] + "," + at[1];
                // A quick demo exists to be looked at, so nothing is allowed to spawn on it.
                full[full.length - 1] = "warded=h+p";
                reply(sender, EnumChatFormatting.AQUA, "--- demo " + which + " ---");
                demonstrate(sender, full);
            }
            return;
        }

        int size = DEMO_DEFAULT_SIZE;
        int height = DEMO_DEFAULT_HEIGHT;
        boolean frozen = false;
        boolean teleport = false;
        int askedY = -1;
        int cap = DEMO_MAX_PLATFORMS;
        boolean tight = false;
        int wardFlags = 0;
        int reinforceLevel = 0;
        boolean overwrite = false;
        DemoOrder order = DemoOrder.BOOK;
        int clearMargin = -1;
        boolean roads = false;
        boolean pens = true;
        int roadWidth = ROAD_WIDTH;
        int roadLength = ROAD_LENGTH;
        boolean anchored = false;
        int anchorX = 0;
        int anchorZ = 0;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i].toLowerCase(Locale.ROOT);
            if ("frozen".equals(arg)) {
                frozen = true;
                continue;
            }
            if ("tp".equals(arg)) {
                teleport = true;
                continue;
            }
            if ("book".equals(arg) || "snake".equals(arg) || "radial".equals(arg)) {
                order = "snake".equals(arg) ? DemoOrder.SNAKE
                    : "radial".equals(arg) ? DemoOrder.RADIAL : DemoOrder.BOOK;
                continue;
            }
            if ("realdemo".equals(arg) || "roads".equals(arg)) {
                roads = true;
                continue;
            }
            if ("nogolems".equals(arg)) {
                pens = false;
                continue;
            }
            if (arg.startsWith("realdemo=") || arg.startsWith("roads=")) {
                String[] shape = arg.substring(arg.indexOf('=') + 1)
                    .split("x");
                if (shape.length != 2) {
                    throw new WrongUsageException(
                        "/trmt demonstrate: realdemo= wants width x length, as in realdemo=16x128");
                }
                try {
                    roadWidth = Math.max(2, Math.min(64, Integer.parseInt(shape[0].trim())));
                    roadLength = Math.max(8, Math.min(512, Integer.parseInt(shape[1].trim())));
                    roads = true;
                    continue;
                } catch (NumberFormatException notAShape) {
                    throw new WrongUsageException(
                        "/trmt demonstrate: realdemo= wants width x length, as in realdemo=16x128");
                }
            }
            if ("cleararea".equals(arg)) {
                clearMargin = DEMO_CLEAR_DEFAULT;
                continue;
            }
            if (arg.startsWith("cleararea=")) {
                try {
                    clearMargin = Math.max(0, Math.min(256, Integer.parseInt(arg.substring(10))));
                    continue;
                } catch (NumberFormatException notADistance) {
                    throw new WrongUsageException("/trmt demonstrate: cleararea= wants a distance, as in cleararea=32");
                }
            }
            if ("samearea".equals(arg)) {
                anchored = true;
                continue;
            }
            if (arg.startsWith("samearea=")) {
                String[] at = arg.substring(9)
                    .split(",");
                if (at.length != 2) {
                    throw new WrongUsageException(
                        "/trmt demonstrate: samearea= wants two numbers, as in samearea=123,456");
                }
                try {
                    anchorX = Integer.parseInt(at[0].trim());
                    anchorZ = Integer.parseInt(at[1].trim());
                    anchored = true;
                    continue;
                } catch (NumberFormatException notACoordinate) {
                    throw new WrongUsageException(
                        "/trmt demonstrate: samearea= wants two numbers, as in samearea=123,456");
                }
            }
            if ("tight".equals(arg) || "nogap".equals(arg)) {
                tight = true;
                continue;
            }
            if ("overwrite".equals(arg) || "force".equals(arg)) {
                overwrite = true;
                continue;
            }
            if ("warded".equals(arg)) {
                wardFlags = 0x3;
                continue;
            }
            if (arg.startsWith("warded=")) {
                Integer flags = wardFlagsOf(arg.substring("warded=".length()));
                if (flags == null) {
                    throw new WrongUsageException("/trmt demonstrate: warded= wants h, p or h+p, as in warded=h+p");
                }
                wardFlags = flags.intValue();
                continue;
            }
            if ("reinforced".equals(arg)) {
                reinforceLevel = Math.max(1, Math.min(3, TrmtConfig.reinforceMaxLevel));
                continue;
            }
            if (arg.startsWith("reinforced=")) {
                try {
                    reinforceLevel = Math.max(0, Math.min(3, Integer.parseInt(arg.substring("reinforced=".length()))));
                    continue;
                } catch (NumberFormatException notALevel) {
                    throw new WrongUsageException("/trmt demonstrate: reinforced= wants a level from 0 to 3");
                }
            }
            if ("all".equals(arg)) {
                // No ceiling at all. Worth having, because the ceiling exists to protect against
                // a pack nobody has measured rather than because any particular number is right.
                cap = Integer.MAX_VALUE;
                continue;
            }
            if (arg.startsWith("y=") || arg.startsWith("y:")) {
                try {
                    askedY = Math.max(1, Math.min(250, Integer.parseInt(arg.substring(2))));
                    continue;
                } catch (NumberFormatException notAHeight) {
                    throw new WrongUsageException("/trmt demonstrate: y= wants a number, as in y=120");
                }
            }
            if (arg.startsWith("max=") || arg.startsWith("max:")) {
                try {
                    cap = Math.max(1, Integer.parseInt(arg.substring(4)));
                    continue;
                } catch (NumberFormatException notACount) {
                    throw new WrongUsageException("/trmt demonstrate: max= wants a number, as in max=200");
                }
            }
            String[] parts = arg.split("x");
            boolean parsed = false;
            if (parts.length == 2 || parts.length == 3) {
                try {
                    int width = Integer.parseInt(parts[0]);
                    int depth = Integer.parseInt(parts[1]);
                    // Square only. The gradient runs along one axis and wraps onto the next row,
                    // so a platform that is not square reads as a mistake rather than as a ramp.
                    if (width != depth) {
                        throw new WrongUsageException(
                            "/trmt demonstrate: platforms are square, so 4x4 rather than " + arg);
                    }
                    size = Math.max(1, Math.min(DEMO_MAX_SIZE, width));
                    if (parts.length == 3) {
                        height = Math.max(1, Math.min(DEMO_MAX_SIZE, Integer.parseInt(parts[2])));
                    }
                    parsed = true;
                } catch (NumberFormatException notASize) {
                    parsed = false;
                }
            }
            if (!parsed) {
                throw new WrongUsageException(
                    "/trmt demonstrate [NxN|NxNxN] [y=<h>] [max=<n>|all] [book|snake|radial]"
                        + " [cleararea|cleararea=<n>] [samearea|samearea=<x>,<z>] [realdemo|realdemo=<w>x<l>]"
                        + " [nogolems]"
                        + " [quick=<n>|quick=1+2|quick=1-6] [quicktp=<n>]"
                        + " [warded|warded=h|warded=p|warded=h+p] [reinforced|reinforced=<0-3>]"
                        + " [tight] [overwrite] [frozen] [tp]");
            }
        }

        List<DemoSurface> demos = new ArrayList<DemoSurface>();
        int unsuitable = 0;
        for (SurfaceRegistry.SurfaceState state : SurfaceRegistry.texturableStates()) {
            if (ErosionChain.length(state.family) <= 0) {
                unsuitable++;
                continue;
            }
            // Only our own ghosts are refused outright. Asking for a full opaque cube seemed
            // safe and was not: a made path stands a pixel short and answers no to both
            // questions, and ice answers no to the second, so the demo quietly omitted two of
            // the things most worth looking at. What belongs here is what detection decided
            // belongs, and surfaces.exclude is the lever for anything that turns out not to.
            if (ModBlocks.isGhost(state.block)) {
                unsuitable++;
                continue;
            }
            int meta = erodableMeta(state);
            if (meta < 0) {
                unsuitable++;
                continue;
            }
            demos.add(new DemoSurface(state, meta));
        }
        if (demos.isEmpty()) {
            reply(sender, EnumChatFormatting.RED, "Nothing is detected as erodable, so there is nothing to show.");
            return;
        }

        // Grouped by family, so the exhibit reads as a handful of runs rather than one alphabet.
        Collections.sort(demos, new Comparator<DemoSurface>() {

            @Override
            public int compare(DemoSurface left, DemoSurface right) {
                if (left.state.family != right.state.family) {
                    return left.state.family.ordinal() - right.state.family.ordinal();
                }
                return left.state.registryName.compareTo(right.state.registryName);
            }
        });

        int dropped = 0;
        if (demos.size() > cap) {
            dropped = demos.size() - cap;
            demos = new ArrayList<DemoSurface>(demos.subList(0, cap));
        }

        int count = demos.size();
        int columns = (int) Math.ceil(Math.sqrt(count));
        int rows = (count + columns - 1) / columns;
        // Two blocks of air between platforms reads as a gap; none reads as one continuous
        // field, which is what you want when comparing surfaces against each other rather than
        // looking at each on its own.
        int pitch = tight ? size : size + 2;
        // Anchored, the demo is laid out around a fixed point instead of around whoever typed
        // the command, so the same command puts it back in the same place from anywhere.
        int centreX = anchored ? anchorX : MathHelper.floor_double(player.posX);
        int centreZ = anchored ? anchorZ : MathHelper.floor_double(player.posZ);
        int originX = centreX - (columns * pitch) / 2;
        int originZ = centreZ - (rows * pitch) / 2;
        int baseY = askedY >= 0 ? askedY : MathHelper.floor_double(player.posY) + DEMO_CLEARANCE;
        int topY = baseY + height;
        if (topY > 254) {
            reply(
                sender,
                EnumChatFormatting.RED,
                askedY >= 0 ? "y=" + askedY + " leaves no room for a " + height + "-thick platform under the sky."
                    : "Not enough sky above you. Stand somewhere lower, or give a height with y=<n>.");
            return;
        }

        // Loaded before anything is surveyed, because the survey refuses a position whose
        // chunk is not there and would otherwise skip most of a demo built somewhere new.
        //
        // No ticket is taken and none is needed: all of this happens inside the single tick the
        // command was typed in, and nothing unloads underneath a tick that is still running. A
        // forced-chunk ticket would only be holding ground that cannot go anywhere, and Forge
        // caps a ticket at far fewer chunks than a demo of this size spans.
        // The whole yard, not just the grid: the roads run out behind the platforms and the
        // pens stand in front of them, and a demo built in chunks that were never brought in is a
        // demo with holes through it.
        boolean yard = roads && pens && TrmtConfig.golemEnabled;
        int penBand = yard ? DemoYard.depth() + PEN_MARGIN : 0;
        int spanAll = Math.max(Math.max(columns * pitch, roads ? roadLength + 4 : 0), yard ? DemoYard.width() : 0);
        int backBand = (ROAD_DEPTHS.length + 1) * (roadWidth + 4);
        int loaded = loadArea(
            world,
            originX - 2,
            originZ - backBand,
            spanAll + 4,
            rows * pitch + backBand + penBand,
            clearMargin);
        if (loaded > 0) {
            reply(sender, EnumChatFormatting.DARK_GRAY, "Loaded " + loaded + " chunks to build in.");
        }

        int cleared = 0;
        int swept = 0;
        if (clearMargin >= 0) {
            // The whole of what is about to be built, not just the platforms. The roads run
            // out behind them and a margin measured from the grid alone left them standing in
            // whatever was already there - which did not show while the margin was sixty-four
            // blocks and everything was swallowed anyway.
            int spanX = roads ? spanAll : columns * pitch;
            int backZ = roads ? backBand : 0;
            // The margin the clear will settle on, worked out before it runs rather than after.
            // A big enough box pulls its own margin in until the work fits, and a sweep built from
            // the margin that was asked for would then be reaching into ground nobody touched.
            int reach = fittedMargin(spanX + 4, rows * pitch + backZ + penBand, baseY, clearMargin);
            cleared = clearAround(
                world,
                originX - 2,
                originZ - backZ,
                spanX + 4,
                rows * pitch + backZ + penBand,
                baseY,
                clearMargin);
            // Clearing the ground breaks whatever was standing in it, and a chest breaking empties
            // itself onto the floor. Swept after the clear rather than before it, so what the clear
            // itself dropped goes too - which is the whole of a re-run's litter.
            swept = DemoYard.sweep(
                world,
                originX - 2 - reach,
                baseY - reach,
                originZ - backZ - reach,
                originX + spanX + 2 + reach,
                255,
                originZ + rows * pitch + penBand + reach);
        }

        // Surveyed whole before a single block is placed. This is the only command in the mod
        // that writes blocks, and a refusal is recoverable where a hole through someone's build
        // is not - so it refuses by default and only overwrites when told to, which is the way
        // round that lets a demo be rebuilt in place without making that the accident.
        int blocked = 0;
        for (int i = 0; i < count && blocked == 0; i++) {
            int platformX = originX + (i % columns) * pitch;
            int platformZ = originZ + (i / columns) * pitch;
            for (int dx = 0; dx < size && blocked == 0; dx++) {
                for (int dz = 0; dz < size && blocked == 0; dz++) {
                    for (int y = baseY; y <= topY; y++) {
                        int x = platformX + dx;
                        int z = platformZ + dz;
                        if (!world.blockExists(x, y, z)) {
                            blocked++;
                            break;
                        }
                        if (world.isAirBlock(x, y, z)) continue;
                        if (!world.getBlock(x, y, z)
                            .isReplaceable(world, x, y, z)) {
                            blocked++;
                            break;
                        }
                    }
                }
            }
        }
        // The pens too, and in the same pass, so one refusal covers the whole yard and 'overwrite'
        // means the same thing everywhere. A pen with holes punched out of it is not a pen.
        if (yard && blocked == 0) {
            blocked += DemoYard.survey(world, originX, originZ + rows * pitch + PEN_MARGIN, baseY);
        }
        if (blocked > 0 && !overwrite) {
            reply(
                sender,
                EnumChatFormatting.RED,
                "Something is already up there, or stands in ground this would not load to look at, which happens when a demo is asked to span more chunks than it will generate in one go. Nothing has been placed. The platforms are surveyed only as far as the first position found taken, so there may be a great deal more than one block in the way. Move, or add 'overwrite' to build over it.");
            return;
        }

        for (int i = 0; i < count; i++) {
            DemoSurface demo = demos.get(i);
            int platformX = originX + (i % columns) * pitch;
            int platformZ = originZ + (i / columns) * pitch;
            // A slab and a stair wear like anything else and are worth looking at, but a platform
            // two courses deep in either of them is a stack of half blocks with the sky through
            // it. So the courses underneath are laid in the family's own plain block and the shape
            // itself is only the top - which is what a slab or a stair is in a road anyway.
            SurfaceShape shape = SurfaceShape.of(demo.state.block);
            Block under = shape.isPartial() ? roadBlock(demo.state.family) : demo.state.block;
            int underMeta = shape.isPartial() ? 0 : demo.meta;
            for (int dx = 0; dx < size; dx++) {
                for (int dz = 0; dz < size; dz++) {
                    int x = platformX + dx;
                    int z = platformZ + dz;
                    // Filled from the top down so the height map for the column rises once
                    // rather than once per layer, and with flag 2, which sends the change
                    // without telling every neighbour about it.
                    for (int y = topY; y > baseY; y--) {
                        Block wanted = y == topY ? demo.state.block : under;
                        int wantedMeta = y == topY ? demo.meta : underMeta;
                        // Left alone when it is already what it should be. Rebuilding a block
                        // that already matches drops its record and lays a fresh one, which on
                        // an overwrite threw away the very wear the rebuild meant to keep - and
                        // it makes a re-run of the same demo cost nothing.
                        if (world.getBlock(x, y, z) == wanted && world.getBlockMetadata(x, y, z) == wantedMeta) {
                            continue;
                        }
                        world.setBlock(x, y, z, wanted, wantedMeta, 2);
                    }
                    // Sand and gravel fall. A platform of them hung in the air would empty
                    // itself into the ground two ticks later, so it is given a floor to land on
                    // and stay on. Placed last, but well inside the same tick.
                    if (world.getBlock(x, baseY, z) != Blocks.stone || world.getBlockMetadata(x, baseY, z) != 0) {
                        world.setBlock(x, baseY, z, Blocks.stone, 0, 2);
                    }
                }
            }
        }

        int painted = 0;
        Set<Long> touchedChunks = new HashSet<Long>();
        for (int i = 0; i < count; i++) {
            DemoSurface demo = demos.get(i);
            int platformX = originX + (i % columns) * pitch;
            int platformZ = originZ + (i / columns) * pitch;
            int cells = size * size;
            int chainLength = ErosionChain.length(demo.state.family);
            int steps = Math.max(1, cells - 2);
            // The far corner, for the radial ordering. Measured rather than assumed so a
            // platform of any size spends its whole run between the two corners.
            double furthest = Math.sqrt(2.0D * (size - 1) * (size - 1));

            for (int row = 0; row < size; row++) {
                for (int column = 0; column < size; column++) {
                    // The corner every ordering starts from is left untouched, so there is
                    // something pristine to judge the first gradation against.
                    if (row == 0 && column == 0) continue;

                    int index;
                    if (order == DemoOrder.RADIAL) {
                        double reach = Math.sqrt((double) row * row + (double) column * column);
                        double share = furthest <= 0d ? 1d : reach / furthest;
                        index = (int) Math.round(share * (chainLength - 1));
                    } else {
                        int ordinal = order == DemoOrder.SNAKE && (row & 1) == 1 ? row * size + (size - 1 - column)
                            : row * size + column;
                        index = Math.round(((ordinal - 1) * (chainLength - 1)) / (float) steps);
                    }
                    if (index < 0) index = 0;

                    int x = platformX + column;
                    int z = platformZ + row;
                    if (ErosionEngine.get()
                        .forceStage(world, x, topY, z, demo.state.family, index, frozen, false)) {
                        painted++;
                        touchedChunks.add(Long.valueOf(((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL)));
                    }
                }
            }
        }

        // Wards and reinforcement, on the surface of each platform - the block a mob would
        // stand on and the block a blast would meet. After the wear, because the wear is what
        // creates the record these ride in.
        if (wardFlags != 0 || reinforceLevel > 0) {
            for (int i = 0; i < count; i++) {
                int platformX = originX + (i % columns) * pitch;
                int platformZ = originZ + (i / columns) * pitch;
                for (int dx = 0; dx < size; dx++) {
                    for (int dz = 0; dz < size; dz++) {
                        int x = platformX + dx;
                        int z = platformZ + dz;
                        markDemoBlock(world, x, topY, z, wardFlags, reinforceLevel);
                        touchedChunks.add(Long.valueOf(((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL)));
                    }
                }
            }
        }

        // One packet per chunk rather than one per position. Sending thousands of positions a
        // delta at a time makes the client rebuild its whole overlay for every one of them.
        for (Long key : touchedChunks) {
            int chunkX = (int) (key.longValue() >> 32);
            int chunkZ = (int) key.longValue();
            ChunkErosionData data = ErosionStore.get()
                .getChunk(world.provider.dimensionId, chunkX, chunkZ);
            if (data != null) TrmtNetwork.sendChunkToWatchers(world, chunkX, chunkZ, data, true);
        }

        if (teleport) {
            player.mountEntity(null);
            player.fallDistance = 0f;
            player.setPositionAndUpdate(originX + size / 2.0D, topY + 1.0D, originZ + size / 2.0D);
        }

        reply(
            sender,
            EnumChatFormatting.AQUA,
            "Built " + count
                + " platforms of "
                + size
                + "x"
                + size
                + ", "
                + height
                + " thick, with "
                + painted
                + " worn squares in all."
                + (frozen ? " Pinned, so it will not change." : ""));
        if (dropped > 0) {
            reply(
                sender,
                EnumChatFormatting.YELLOW,
                "Left out " + dropped
                    + " further detected surfaces past the first "
                    + count
                    + ". Raise it with max=<count>, or say 'all' for every one.");
        }
        if (unsuitable > 0) {
            reply(
                sender,
                EnumChatFormatting.DARK_GRAY,
                "Skipped " + unsuitable
                    + " detected entries: families with no wear chain to walk, this mod's own worn ground, and blocks with no metadata value at all that lands in the family they were detected as. Shape is not one of the reasons, so a slab or a stair its family allows stands here like any whole block; anything else that should not be here belongs in surfaces.exclude.");
        }
        if (roads) {
            int[] tally = new int[3];
            buildRoads(world, originX, originZ, baseY, roadWidth, roadLength, frozen, overwrite, tally);
            reply(
                sender,
                EnumChatFormatting.AQUA,
                "Laid " + ROAD_DEPTHS.length
                    + " stretches of road, "
                    + roadWidth
                    + " by "
                    + roadLength
                    + ", turf through to stone, with "
                    + tally[1]
                    + " worn squares between them.");
        }
        if (yard) {
            int[] penTally = new int[3];
            swept += DemoYard.build(world, originX, originZ + rows * pitch + PEN_MARGIN, baseY, penTally);
            reply(
                sender,
                EnumChatFormatting.AQUA,
                "Stood up " + penTally[1]
                    + " golems in "
                    + penTally[0]
                    + " pens, each with a Wayfarer, a chest of tools and a chest of material.");
        } else if (roads && !pens) {
            reply(sender, EnumChatFormatting.DARK_GRAY, "Left the golem pens out.");
        } else if (roads && !TrmtConfig.golemEnabled) {
            reply(sender, EnumChatFormatting.DARK_GRAY, "Golems are switched off, so the pens were skipped.");
        }
        if (cleared > 0) {
            reply(sender, EnumChatFormatting.YELLOW, "Cleared " + cleared + " blocks to make room.");
        }
        if (swept > 0) {
            reply(
                sender,
                EnumChatFormatting.YELLOW,
                "Cleared away " + swept + " golems and dropped items left over from a previous run.");
        }
        if (blocked > 0) {
            reply(
                sender,
                EnumChatFormatting.YELLOW,
                "Built over at least " + blocked
                    + (blocked == 1 ? " position" : " positions")
                    + " that were already occupied. The survey stops at the first platform position it finds taken, because one is enough to know the ground is not clear, so the true number may be a great deal higher - a warning that something was standing there rather than a tally of what went.");
        }
        if (!frozen) {
            reply(
                sender,
                EnumChatFormatting.DARK_GRAY,
                "Walking on it wears it further. Add 'frozen' to pin it. /trmt purge clears the wear;"
                    + " the blocks themselves stay until you break them or build over them with 'overwrite'.");
        }
    }

    /**
     * The margin a clear of this shape can afford, which is not always the one that was asked for.
     *
     * <p>
     * Pulled in until the work fits. A box grows fast and all of this happens inside one tick; what
     * a smaller margin costs is the outermost ring of a space whose whole job is to be empty, which
     * is the cheapest thing here to give up.
     *
     * <p>
     * Worked out here rather than inside the clear because two things now need the same answer. The
     * clear empties a box and the sweep afterwards takes the entities out of it, and a sweep built
     * from a margin the clear could not afford is a sweep reaching into ground nobody touched.
     */
    private static int fittedMargin(int spanX, int spanZ, int baseY, int margin) {
        while (margin > 0) {
            long wide = (long) spanX + 2L * margin;
            long deep = (long) spanZ + 2L * margin;
            long tall = Math.max(1, 255 - Math.max(1, baseY - margin) + 1);
            if (wide * deep * tall <= DEMO_CLEAR_MAX_POSITIONS) break;
            margin -= 4;
        }
        return Math.max(0, margin);
    }

    /**
     * Empties a box around where the demo will stand, so it is read against nothing.
     *
     * <p>
     * Everything above the demo goes too, all the way to the sky, because an overhang is the
     * one thing that would shade the exhibit and change what is being looked at. Destructive by
     * definition, which is why it happens only when asked for by name.
     */
    private static int clearAround(World world, int originX, int originZ, int spanX, int spanZ, int baseY, int margin) {
        int top = 255;
        margin = fittedMargin(spanX, spanZ, baseY, margin);

        int fromY = Math.max(1, baseY - margin);
        int emptied = 0;
        for (int x = originX - margin; x < originX + spanX + margin; x++) {
            for (int z = originZ - margin; z < originZ + spanZ + margin; z++) {
                for (int y = fromY; y <= top; y++) {
                    if (!world.blockExists(x, y, z)) continue;
                    if (world.isAirBlock(x, y, z)) continue;
                    // Flag 2 sends the change without telling every neighbour about it, which
                    // for a box this size is the difference between one update and millions.
                    world.setBlock(x, y, z, Blocks.air, 0, 2);
                    emptied++;
                }
            }
        }
        return emptied;
    }

    /**
     * The demos worth having laid out, spelled as the arguments that make them.
     *
     * <p>
     * Arguments rather than a second code path, and that is the whole point: a preset that drifts
     * from what the command actually does is worse than no preset, so these are fed back through
     * the same parser and get whatever the flags currently mean. Each sits on its own coordinates,
     * so they can all stand at once and be flown between.
     */
    /**
     * How far apart the quick demos stand.
     *
     * <p>
     * Derived rather than written into each preset, because a preset that names its own spot
     * starts overlapping its neighbour the moment a demo grows - and these have grown twice
     * already. Five hundred and twelve is comfortably wider than the largest: a hundred blocks
     * of platforms, a hundred more of road behind them, and the clear margin around both.
     */
    /** How much empty ground is left between one quick demo's cleared box and the next. */
    private static final int QUICK_GAP = 24;

    /** The margin each quick demo clears around itself. Small, now that the gap is deliberate. */
    private static final int QUICK_MARGIN = 8;

    /**
     * How far apart the quick demos stand, measured rather than guessed.
     *
     * <p>
     * It used to be a flat five hundred and twelve, which was safe and meant flying half a
     * kilometre to compare two of them. The demos size themselves from what the pack detects,
     * so the spacing can too: the width of one, plus the margin it clears on each side, plus
     * the gap that keeps them visibly separate rather than accidentally adjacent.
     *
     * <p>
     * The roads decide it in practice. They run a hundred and twenty-eight blocks along the same
     * axis the demos are spaced on, which is wider than the grid of platforms ever gets.
     */
    private static int quickSpacing() {
        int count = 0;
        for (SurfaceRegistry.SurfaceState state : SurfaceRegistry.texturableStates()) {
            if (!ModBlocks.isGhost(state.block) && ErosionChain.length(state.family) > 0) count++;
        }
        int columns = Math.max(1, (int) Math.ceil(Math.sqrt(Math.max(1, count))));
        // The preset's own 8x8 at the default pitch, which is what these are all built with.
        int gridWidth = columns * 10;
        int width = Math.max(Math.max(gridWidth, ROAD_LENGTH + 4), DemoYard.width());
        return width + 2 * QUICK_MARGIN + QUICK_GAP;
    }

    /** Where a numbered demo stands. One row, evenly spaced, so they read as a set. */
    private static int[] quickAnchorFor(int index) {
        return new int[] { index * quickSpacing(), 0 };
    }

    private static final String[][] QUICK = {
        { "y=242", "8x8", "all", "tp", "frozen", "overwrite", "cleararea=8", "realdemo" },
        { "y=242", "8x8", "all", "tp", "frozen", "overwrite", "cleararea=8", "tight", "realdemo" },
        { "y=242", "8x8", "all", "tp", "frozen", "overwrite", "radial", "cleararea=8", "realdemo" },
        { "y=242", "8x8", "all", "tp", "frozen", "overwrite", "radial", "cleararea=8", "tight", "realdemo" },
        { "y=242", "8x8", "all", "tp", "frozen", "overwrite", "snake", "cleararea=8", "realdemo" },
        { "y=242", "8x8", "all", "tp", "frozen", "overwrite", "snake", "cleararea=8", "tight", "realdemo" } };

    /**
     * Which presets a {@code quick=} value names.
     *
     * <p>
     * Accepts one number, a list joined by plus signs, or a range with a dash. Returns null when
     * it cannot make sense of the value, so the caller can say what it does take rather than
     * guessing at what was meant.
     */
    private static List<Integer> quickSelection(String value) {
        List<Integer> chosen = new ArrayList<Integer>();
        if (value == null || value.isEmpty()) return null;
        if ("all".equalsIgnoreCase(value.trim())) {
            for (int i = 1; i <= QUICK.length; i++) {
                chosen.add(Integer.valueOf(i));
            }
            return chosen;
        }
        try {
            if (value.indexOf('-') > 0) {
                String[] ends = value.split("-", 2);
                int from = Integer.parseInt(ends[0].trim());
                int to = Integer.parseInt(ends[1].trim());
                if (from > to) {
                    int swap = from;
                    from = to;
                    to = swap;
                }
                for (int i = from; i <= to; i++) {
                    if (i >= 1 && i <= QUICK.length) chosen.add(Integer.valueOf(i));
                }
            } else {
                for (String part : value.split("\\+")) {
                    int i = Integer.parseInt(part.trim());
                    if (i >= 1 && i <= QUICK.length) chosen.add(Integer.valueOf(i));
                }
            }
        } catch (NumberFormatException notANumber) {
            return null;
        }
        return chosen.isEmpty() ? null : chosen;
    }

    /** Says what quick takes, rather than guessing at what was meant. */
    private void explainQuick(ICommandSender sender, boolean teleportOnly) {
        reply(
            sender,
            EnumChatFormatting.AQUA,
            (teleportOnly ? "quicktp" : "quick") + " takes a number from 1 to "
                + QUICK.length
                + ", a list like 1+2+3, a range like 1-"
                + QUICK.length
                + ", or 'all'.");
        for (int i = 0; i < QUICK.length; i++) {
            StringBuilder line = new StringBuilder();
            line.append(EnumChatFormatting.GRAY)
                .append("  ")
                .append(i + 1)
                .append(": ");
            for (String arg : QUICK[i]) {
                line.append(arg)
                    .append(' ');
            }
            reply(sender, EnumChatFormatting.DARK_GRAY, line.toString());
        }
    }

    /** Where a preset puts its demo, read out of the preset itself so the two cannot disagree. */
    private static int[] quickAnchor(int index) {
        int[] at = quickAnchorFor(index);
        int y = 64;
        for (String arg : QUICK[index]) {
            if (arg.startsWith("y=")) {
                try {
                    y = Integer.parseInt(arg.substring(2));
                } catch (NumberFormatException ignored) {
                    // Left at the default; the parser reads the preset again anyway.
                }
            }
        }
        return new int[] { at[0], y, at[1] };
    }

    /** How much empty ground is left between the last platform and the first pen. */
    private static final int PEN_MARGIN = 6;

    /** How wide a stretch of road is, across the rut. */
    private static final int ROAD_WIDTH = 16;

    /** How long a stretch of road is, along the fade from turf to stone. */
    private static final int ROAD_LENGTH = 128;

    /** How worn the middle of each stretch gets: a line somebody walked, a path, a hollow way. */
    private static final float[] ROAD_DEPTHS = { 0.35f, 0.7f, 1.0f };

    /**
     * Lays out a few stretches of road, which is the thing the platforms are not.
     *
     * <p>
     * A platform answers "what does gradation eleven of gravel look like". It cannot answer the
     * question that actually matters - whether a road reads as a road - because that only shows
     * up over a distance, with a rut deepest where the feet go and fading to nothing at the
     * verge, over ground that changes underneath it.
     *
     * <p>
     * So each stretch runs turf, sand, gravel and stone in turn with the boundaries nibbled
     * rather than ruled, and carries a rut whose middle wanders and whose depth is uneven.
     * Three of them, from a line walked twice to a hollow way.
     *
     * <p>
     * Nothing here differs between runs. It is all a function of position, so the same command
     * lays the same road twice and two people can talk about the same spot in it.
     */
    private static void buildRoads(World world, int originX, int originZ, int baseY, int width, int length,
        boolean frozen, boolean overwrite, int[] tally) {
        SurfaceFamily[] run = { SurfaceFamily.GRASS, SurfaceFamily.SAND, SurfaceFamily.GRAVEL, SurfaceFamily.STONE };
        int segment = Math.max(1, length / run.length);

        for (int strip = 0; strip < ROAD_DEPTHS.length; strip++) {
            int stripZ = originZ - (strip + 2) * (width + 4);
            float deepest = ROAD_DEPTHS[strip];

            for (int along = 0; along < length; along++) {
                // The boundary between one ground and the next is nibbled rather than ruled, so
                // it reads as somewhere the ground turns sandy rather than as a seam.
                int shift = (int) Math.round(3.0D * wobble(along, strip * 977, 1.0D));
                int which = Math.max(0, Math.min(run.length - 1, (along + shift) / segment));
                SurfaceFamily family = run[which];
                Block block = roadBlock(family);
                int chainLength = ErosionChain.length(family);
                if (block == null || chainLength <= 0) continue;

                // The middle of a road is not a straight line, because nobody walks one.
                double centre = (width - 1) / 2.0D + 2.2D * wobble(along, strip * 131 + 7, 0.11D);

                for (int across = 0; across < width; across++) {
                    int x = originX + along;
                    int z = stripZ + across;
                    if (!world.blockExists(x, baseY, z)) continue;
                    if (!overwrite && !clearForRoad(world, x, baseY, z)) continue;

                    world.setBlock(x, baseY + 1, z, block, 0, 2);
                    world.setBlock(x, baseY, z, Blocks.stone, 0, 2);
                    tally[0]++;

                    double edge = Math.max(1.0D, (width - 1) / 2.0D);
                    double fromCentre = Math.min(1.0D, Math.abs(across - centre) / edge);
                    // Squared, so the rut has a floor rather than a point and the verge rises
                    // gently, which is how a worn hollow actually sits.
                    double profile = 1.0D - fromCentre * fromCentre;
                    profile += 0.18D * wobble(along * 3 + across, strip * 613, 0.7D);
                    if (profile <= 0.02D) continue;

                    double depth = Math.max(0.0D, Math.min(1.0D, deepest * profile));
                    int step = (int) Math.round(depth * (chainLength - 1));
                    if (step <= 0) continue;
                    if (ErosionEngine.get()
                        .forceStage(world, x, baseY + 1, z, family, step, frozen, false)) {
                        tally[1]++;
                    }
                }
            }

            // One at each end, on the floor course so they sit level with the road surface.
            int middle = stripZ + width / 2;
            stockChest(world, originX - 2, baseY + 1, middle);
            stockChest(world, originX + length + 1, baseY + 1, middle);

            for (int along = 0; along <= length; along += 16) {
                for (int across = 0; across <= width; across += 16) {
                    int chunkX = (originX + Math.min(along, length - 1)) >> 4;
                    int chunkZ = (stripZ + Math.min(across, width - 1)) >> 4;
                    ChunkErosionData data = ErosionStore.get()
                        .getChunk(world.provider.dimensionId, chunkX, chunkZ);
                    if (data != null) TrmtNetwork.sendChunkToWatchers(world, chunkX, chunkZ, data, true);
                }
            }
        }
    }

    /**
     * A container at each end of a stretch, holding one of everything the mod adds.
     *
     * <p>
     * A road you can walk but not work on is half a demo. Both ends rather than one, because the
     * point of a hundred and twenty-eight blocks of road is to walk its length, and having to walk
     * back for a tool is exactly the friction the demo exists to remove.
     *
     * <p>
     * The mod now adds well over twenty-seven things once every tamper grade is counted, so a
     * single vanilla chest silently dropped the tail of the list - which for a demonstration is the
     * worst possible failure, because what is missing is invisible. Whichever container the pack
     * can supply is used instead, tried in the order configured, and a plain vanilla chest is
     * doubled up so even the bare-Forge case has room.
     */
    private static void stockChest(World world, int x, int y, int z) {
        if (!world.blockExists(x, y, z)) return;

        List<ItemStack> wanted = demoContents();
        int placed = placeContainer(world, x, y, z, wanted, 0);

        // Whatever the pack settled on may still be smaller than the list. A second one beside the
        // first is a double chest where the container joins into one, and simply a second container
        // where it does not; either way it holds the tail.
        if (placed < wanted.size() && world.blockExists(x + 1, y, z)) {
            placed = placeContainer(world, x + 1, y, z, wanted, placed);
        }

        if (placed < wanted.size()) {
            Trmt.LOG.warn(
                "The demo container held only {} of {} items; set a roomier one in config",
                Integer.valueOf(placed),
                Integer.valueOf(wanted.size()));
        }
    }

    /**
     * Puts the roomiest container this pack can supply at a position, and fills it from a list.
     *
     * <p>
     * The metadata matters as much as the name does. Iron Chest is one block with a chest per
     * value, and the value is what decides how many slots the thing has - so a name alone settled
     * on the smallest of them and dropped the tail of the list into nothing. Written as
     * {@code mod:block:meta}, exactly the spelling the golem's build blocks already use.
     *
     * <p>
     * Checked afterwards rather than trusted, because a name can resolve to a block that keeps no
     * inventory at all and a metadata can be one this version of that mod never had. Either way
     * what is standing there holds nothing, and a demo that silently fills nothing is worse than
     * one that falls back to the plain chest it started as.
     *
     * @return how many of the list have been placed in all, including those handed in
     */
    static int placeContainer(World world, int x, int y, int z, List<ItemStack> wanted, int from) {
        if (!world.blockExists(x, y, z)) return from;

        int packed = resolveContainer();
        Block container = packed < 0 ? Blocks.chest : Block.getBlockById(packed >> 4);
        int meta = packed < 0 ? 0 : packed & 0xF;
        if (container == null || container == Blocks.air) {
            container = Blocks.chest;
            meta = 0;
        }

        // Emptied before it is replaced. Breaking a container spills its contents on the floor, and
        // a demo that has been re-run three times is a demo standing in a drift of its own tools.
        empty(world, x, y, z);
        world.setBlock(x, y, z, container, meta, 2);
        if (container != Blocks.chest && !(world.getTileEntity(x, y, z) instanceof IInventory)) {
            Trmt.LOG.warn("demoContainer settled on {}:{}, which holds nothing; using a plain chest", container, meta);
            empty(world, x, y, z);
            world.setBlock(x, y, z, Blocks.chest, 0, 2);
        }
        return fill(world, x, y, z, wanted, from);
    }

    /** Takes everything out of whatever is at a position, so replacing it drops nothing. */
    private static void empty(World world, int x, int y, int z) {
        net.minecraft.tileentity.TileEntity standing = world.getTileEntity(x, y, z);
        if (!(standing instanceof IInventory)) return;
        IInventory holder = (IInventory) standing;
        for (int slot = 0; slot < holder.getSizeInventory(); slot++) {
            holder.setInventorySlotContents(slot, null);
        }
        holder.markDirty();
    }

    /**
     * Puts as much of the list as fits into whatever is at a position.
     *
     * @return how many items have been placed in total, including those handed in
     */
    private static int fill(World world, int x, int y, int z, List<ItemStack> wanted, int from) {
        net.minecraft.tileentity.TileEntity holder = world.getTileEntity(x, y, z);
        // Asked as an inventory rather than as a chest, so a container from another mod works
        // without this knowing anything about it.
        if (!(holder instanceof net.minecraft.inventory.IInventory)) return from;
        net.minecraft.inventory.IInventory inventory = (net.minecraft.inventory.IInventory) holder;

        int at = from;
        for (int slot = 0; slot < inventory.getSizeInventory() && at < wanted.size(); slot++) {
            ItemStack stack = wanted.get(at);
            if (!inventory.isItemValidForSlot(slot, stack)) continue;
            inventory.setInventorySlotContents(slot, stack);
            at++;
        }
        inventory.markDirty();
        return at;
    }

    /** One of everything the mod adds, with a chunk tamper per grade rather than one blank one. */
    private static List<ItemStack> demoContents() {
        List<ItemStack> out = new ArrayList<ItemStack>();

        // The ground itself, which the mod does not add and every one of its tools is for. A chest
        // of tampers with nothing to tamper is half a demo: mending costs a block of what is being
        // mended, so without these the only direction the exhibit can be walked is downhill.
        //
        // First rather than last, because a container smaller than the list loses its tail, and the
        // tail should be the fortieth nearly identical tamper grade rather than every block the
        // other thirty-nine are for.
        out.add(new ItemStack(Items.dye, 64, 15));
        Block[] ground = { Blocks.dirt, Blocks.grass, Blocks.cobblestone, Blocks.stone, Blocks.sand, Blocks.gravel,
            Blocks.snow, Blocks.snow_layer, Blocks.ice, Blocks.netherrack, Blocks.end_stone };
        for (Block block : ground) {
            out.add(new ItemStack(block, 64, 0));
        }

        for (net.minecraft.item.Item item : com.trmtgtnh.item.ModItems.all()) {
            // The chunk tamper is one item with a grade inside it, so one of each grade rather
            // than one of the item - otherwise the chest holds a single unmarked tool and the
            // whole point of the grades is invisible.
            if (item instanceof com.trmtgtnh.item.ItemChunkTamper
                && !(item instanceof com.trmtgtnh.item.ItemMagicTamper)) {
                for (com.trmtgtnh.item.TamperGrade grade : com.trmtgtnh.item.TamperGrade.available()) {
                    ItemStack graded = new ItemStack(item, 1, 0);
                    com.trmtgtnh.item.ItemChunkTamper.setGrade(graded, grade);
                    out.add(graded);
                }
                continue;
            }
            out.add(new ItemStack(item, 1, 0));
        }
        return out;
    }

    /**
     * The first container this pack can actually supply, packed as its id and metadata.
     *
     * <p>
     * Anything outside vanilla is skipped while the GTNH enhancements are off, exactly as the
     * golem's build blocks are, so a pack asked to behave as plain Forge gets the plain chest.
     */
    private static int resolveContainer() {
        String[] candidates = TrmtConfig.demoContainer;
        if (candidates == null) return -1;
        for (String entry : candidates) {
            if (entry == null || entry.trim()
                .isEmpty()) {
                continue;
            }
            String name = BlockEntry.nameOf(entry);
            if (!TrmtConfig.gtnhEnhanced && !name.startsWith("minecraft:")) continue;
            Block block = Block.getBlockFromName(name);
            if (block == null || block == Blocks.air) continue;
            int id = Block.getIdFromBlock(block);
            if (id < 0) continue;
            return (id << 4) | (BlockEntry.metaOf(entry, 0) & 0xF);
        }
        return -1;
    }

    /** The plainest block of a family, so a road reads as ground rather than as a mod's sampler. */
    public static Block roadBlock(SurfaceFamily family) {
        switch (family) {
            case GRASS:
                return Blocks.grass;
            case SAND:
                return Blocks.sand;
            case GRAVEL:
                return Blocks.gravel;
            case STONE:
                return Blocks.stone;
            case COBBLE:
                return Blocks.cobblestone;
            case NETHER:
                return Blocks.netherrack;
            case END:
                return Blocks.end_stone;
            case SNOW:
                return Blocks.snow;
            case ICE:
                return Blocks.ice;
            default:
                return Blocks.dirt;
        }
    }

    /**
     * Brings every chunk the demo will touch into memory, generating any that do not exist yet.
     *
     * <p>
     * Bounded, because asking for a chunk that has never been generated generates it, and a
     * command that quietly generated a thousand of them would be a command that froze the server
     * and grew the save by a hundred megabytes. Past the ceiling the demo simply builds in what
     * is loaded, which is what it did before this existed.
     */
    private static int loadArea(World world, int minX, int minZ, int spanX, int spanZ, int margin) {
        int reach = Math.max(0, margin);
        int fromChunkX = (minX - reach) >> 4;
        int toChunkX = (minX + spanX + reach) >> 4;
        int fromChunkZ = (minZ - reach) >> 4;
        int toChunkZ = (minZ + spanZ + reach) >> 4;

        long wanted = (long) (toChunkX - fromChunkX + 1) * (toChunkZ - fromChunkZ + 1);
        if (wanted > DEMO_MAX_CHUNKS) return 0;

        int touched = 0;
        for (int chunkX = fromChunkX; chunkX <= toChunkX; chunkX++) {
            for (int chunkZ = fromChunkZ; chunkZ <= toChunkZ; chunkZ++) {
                if (world.getChunkProvider()
                    .chunkExists(chunkX, chunkZ)) continue;
                world.getChunkFromChunkCoords(chunkX, chunkZ);
                touched++;
            }
        }
        return touched;
    }

    /** True when this column is free to build a road in. */
    private static boolean clearForRoad(World world, int x, int y, int z) {
        for (int up = y; up <= y + 1; up++) {
            if (world.isAirBlock(x, up, z)) continue;
            if (!world.getBlock(x, up, z)
                .isReplaceable(world, x, up, z)) return false;
        }
        return true;
    }

    /**
     * A smooth, repeatable wobble between -1 and 1.
     *
     * <p>
     * Two sines at unrelated rates rather than a random draw, so the same command lays the same
     * road every time. A demo two people can look at separately and still be discussing the
     * same rut is worth more than one that differs each run.
     */
    private static double wobble(int along, int seed, double rate) {
        double a = Math.sin((along + seed) * 0.117D * rate);
        double b = Math.sin((along * 0.61D + seed * 1.7D) * 0.043D * rate);
        return a * 0.65D + b * 0.35D;
    }

    /**
     * Which metadata of this block to build a platform out of.
     *
     * <p>
     * A metadata the block actually declares comes first, and one detection merely claims comes
     * second. Detection claims all sixteen because it cannot tell which are real, so a naive
     * "first claimed" always answers zero - and zero is a variant plenty of blocks do not have.
     * Chisel's snakestone declares one and thirteen and nothing else, so a demo built at zero was
     * a demo of a state the block is never in.
     */
    private static int erodableMeta(SurfaceRegistry.SurfaceState state) {
        for (int meta : declaredMetas(state.block)) {
            if (SurfaceRegistry.familyOf(state.block, meta) == state.family) return meta;
        }
        for (int meta = 0; meta < 16; meta++) {
            if (SurfaceRegistry.familyOf(state.block, meta) == state.family) return meta;
        }
        return -1;
    }

    /** The metadata values a block offers of itself, or none when it will not say. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static int[] declaredMetas(Block block) {
        net.minecraft.item.Item item = net.minecraft.item.Item.getItemFromBlock(block);
        if (item == null) return new int[0];
        // Asked only where the method exists. getSubBlocks is marked for the client and is stripped
        // from Block on a dedicated server, where calling it is a NoSuchMethodError - an error rather
        // than an exception, so the catch below never saw it and /trmt demonstrate failed outright on
        // every dedicated server. A server answers that the block will not say, which the caller
        // already meets by walking all sixteen values.
        if (!cpw.mods.fml.common.FMLCommonHandler.instance()
            .getSide()
            .isClient()) {
            return new int[0];
        }
        try {
            List stacks = new ArrayList();
            block.getSubBlocks(item, (net.minecraft.creativetab.CreativeTabs) null, stacks);
            int[] metas = new int[stacks.size()];
            int count = 0;
            for (Object entry : stacks) {
                if (!(entry instanceof net.minecraft.item.ItemStack)) continue;
                int damage = ((net.minecraft.item.ItemStack) entry).getItemDamage();
                if (damage >= 0 && damage < 16) metas[count++] = damage;
            }
            int[] trimmed = new int[count];
            System.arraycopy(metas, 0, trimmed, 0, count);
            return trimmed;
        } catch (RuntimeException awkwardBlock) {
            // Plenty of blocks assume a live creative tab here. One of them must not be able to
            // stop a demo being built.
            return new int[0];
        }
    }

    /** How a platform's squares are ordered from untouched to fully worn. */
    private enum DemoOrder {
        /**
         * Left to right, top to bottom, the way a page is read. The default, because it is the
         * order a reader already has in their head before they look.
         */
        BOOK,
        /**
         * Rows alternating direction, so consecutive steps always physically touch. Reads as one
         * continuous gradient rather than as lines, at the cost of the run doubling back.
         */
        SNAKE,
        /**
         * Spreading out of the top-left corner by distance, so the gradations fall in arcs. The
         * one that reads as something happening to the ground rather than as a chart of it.
         */
        RADIAL
    }

    /** One platform's worth of what to build. */
    private static final class DemoSurface {

        final SurfaceRegistry.SurfaceState state;

        final int meta;

        DemoSurface(SurfaceRegistry.SurfaceState state, int meta) {
            this.state = state;
            this.meta = meta;
        }
    }

    private static void reply(ICommandSender sender, EnumChatFormatting color, String message) {
        sender.addChatMessage(new ChatComponentText(color + message));
    }

    /**
     * Prints what decides each ghost's color on a map.
     *
     * <p>
     * An earlier version of this probed the block under the player, which was useless: a
     * command runs on the server, and the server's copy of the world never holds a ghost — that
     * is the whole design. It reported the ordinary block underneath every time and read as
     * confirmation that nothing was wrong.
     *
     * <p>
     * What actually decides the answer is the class. JourneyMap keeps a table of block classes
     * that take a biome tint and matches with {@code isAssignableFrom}; {@code BlockGrass} is in
     * it, and a flagged block has its palette color thrown away in favour of a hardcoded grey
     * multiplied by the block's tint. While every ghost inherited from grass, every worn surface
     * drew as that grey. Only the grass variants should be in that table, and this says which
     * are.
     */
    private static void reportMapColor(ICommandSender sender) {
        reply(sender, EnumChatFormatting.AQUA, "Ghost blocks and what a map mod makes of them:");
        for (Block block : ModBlocks.all()) {
            boolean tinted = block instanceof BlockGrass;
            String name = block.getUnlocalizedName();
            int cut = name.lastIndexOf('.');
            if (cut >= 0 && cut + 1 < name.length()) name = name.substring(cut + 1);
            reply(
                sender,
                tinted ? EnumChatFormatting.GREEN : EnumChatFormatting.GRAY,
                String.format(
                    Locale.ROOT,
                    "  %-20s %-16s %s",
                    name,
                    block.getClass()
                        .getSimpleName(),
                    tinted ? "biome-tinted (grass)" : "own color"));
        }
        reply(
            sender,
            EnumChatFormatting.AQUA,
            "Only the grass rows should be green. Any other surface listed as biome-tinted is the grey-map bug.");
    }
}
