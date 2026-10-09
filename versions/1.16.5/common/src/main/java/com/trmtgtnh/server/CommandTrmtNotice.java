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
 */
public final class CommandTrmtNotice {

    private CommandTrmtNotice() {}

    /** Registers /trmtnotice beside /trmt, on both loaders. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal(UpdateNotice.COMMAND)
                .executes(context -> say(context.getSource(), "", null, false))
                .then(
                    Commands.literal("silence")
                        .then(
                            Commands.argument("version", StringArgumentType.word())
                                .executes(
                                    context -> say(
                                        context.getSource(),
                                        "silence",
                                        StringArgumentType.getString(context, "version"),
                                        false))
                                .then(
                                    Commands.literal("confirm")
                                        .executes(
                                            context -> say(
                                                context.getSource(),
                                                "silence",
                                                StringArgumentType.getString(context, "version"),
                                                true)))))
                .then(
                    Commands.literal("off")
                        .executes(context -> say(context.getSource(), "off", null, false))
                        .then(
                            Commands.literal("confirm")
                                .executes(context -> say(context.getSource(), "off", null, true))))
                .then(
                    Commands.literal("on")
                        .executes(context -> say(context.getSource(), "on", null, false))));
    }

    private static int say(CommandSourceStack source, String what, String build, boolean confirmed)
        throws CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer player = source.getPlayerOrException();
        player.sendMessage(UpdateNotice.answer(player, what, build, confirmed), net.minecraft.Util.NIL_UUID);
        return 1;
    }
}
