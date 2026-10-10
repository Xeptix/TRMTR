package com.trmtgtnh.server;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * /trmtnotice, which the update notice's last line runs when clicked (0.9.221): silence one build, or turn the notice
 * off or back on, for whoever runs it. Open to every player, because it acts on nobody else - and a single-player owner
 * in a world without cheats, who is told the notice, may run no command that needs an operator.
 *
 * <p>
 * <strong>One argument, the rest of the line, read word by word as the 1.7.10 edition reads its arguments</strong>
 * (0.9.222, spec CM87 and CM89). Until then the command was a tree of {@code silence}, {@code off}, {@code on} and
 * {@code confirm}: it offered those words as each was typed, which 1.7.10's completes nothing, and anything off the tree
 * - another first word, {@code silence} with no build, a word after one the tree ends on - was refused in red by the
 * game's own parser instead of being answered with the usage line, which {@link UpdateNotice#answer} gives for exactly
 * those. The answer was always the one that decides; the tree only stood in front of it.
 */
public final class CommandTrmtNotice {

    private CommandTrmtNotice() {}

    /** Registers /trmtnotice beside /trmt, on both loaders. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal(UpdateNotice.COMMAND)
                .executes(context -> say(context.getSource(), new String[0]))
                .then(
                    Commands.argument("words", StringArgumentType.greedyString())
                        .executes(
                            context -> say(
                                context.getSource(),
                                words(StringArgumentType.getString(context, "words"))))));
    }

    /** The line after the command's name, as the words 1.7.10's game hands its commands. */
    static String[] words(String line) {
        String trimmed = line == null ? "" : line.trim();
        return trimmed.isEmpty() ? new String[0] : trimmed.split(" +");
    }

    private static int say(CommandSourceStack source, String[] args) throws CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer player = source.getPlayerOrException();
        player.sendMessage(
            UpdateNotice.answer(
                player,
                args.length > 0 ? args[0] : "",
                args.length > 1 ? args[1] : null,
                // The count first, as 1.7.10's does since 0.9.222: typed alone there is no last word (spec CM88).
                args.length > 1 && "confirm".equals(args[args.length - 1])),
            net.minecraft.Util.NIL_UUID);
        return 1;
    }
}
