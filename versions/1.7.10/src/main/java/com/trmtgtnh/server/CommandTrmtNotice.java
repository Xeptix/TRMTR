package com.trmtgtnh.server;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * /trmtnotice, which the update notice's last line runs when clicked (0.9.221): silence one build, or turn the notice
 * off or back on, for whoever runs it. Open to every player, because it acts on nobody else - and a single-player owner
 * in a world without cheats, who is told the notice, may run no command that needs an operator.
 */
public final class CommandTrmtNotice extends CommandBase {

    @Override
    public String getCommandName() {
        return UpdateNotice.COMMAND;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/" + UpdateNotice.COMMAND + " silence <version> [confirm] | off [confirm] | on";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return sender instanceof EntityPlayerMP;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) sender;
        player.addChatMessage(
            UpdateNotice.answer(
                player,
                args.length > 0 ? args[0] : "",
                args.length > 1 ? args[1] : null,
                // The count first: typed alone the command has no last word, and reading one threw before the answer it
                // hands over for exactly that case (0.9.222, spec CM88).
                args.length > 1 && "confirm".equals(args[args.length - 1])));
    }
}
