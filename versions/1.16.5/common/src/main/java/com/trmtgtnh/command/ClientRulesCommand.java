package com.trmtgtnh.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;

import com.trmtgtnh.config.ClientSettingsBook;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.util.ClientSettingRules;

/**
 * {@code /trmt clientrules} (0.9.229; Xep, 2026-11-18: "config + command + copy my settings"): an operator lists, sets
 * and clears the settings this server sets for its players, live. Every change is written to the server's file and
 * sent to everyone online. The words are read and answered by ClientSettingRules, the same in every edition; this is
 * the edition's wiring alone. Held to the operator level by {@link CommandTrmt}, before it gets here.
 */
final class ClientRulesCommand {

    private ClientRulesCommand() {}

    /** Runs the words after {@code clientrules}. */
    static void run(CommandSourceStack sender, String[] words) {
        for (Said line : answer(sender, words)) {
            CommandTrmt.tell(
                sender,
                new TextComponent((line.refused ? ChatFormatting.RED : ChatFormatting.AQUA) + line.words));
        }
    }

    /**
     * What the words make of the list, and what is said. A copy asks the operator's game and says so; its answer is
     * said when it arrives (TrmtNetwork.takeSettingsCopy).
     */
    static List<Said> answer(CommandSourceStack sender, String[] words) {
        List<Said> out = new ArrayList<Said>();
        ClientSettingRules.Order order = ClientSettingRules.order(words);
        if (order.problem != null) {
            out.add(new Said(true, order.problem));
            return out;
        }
        switch (order.verb) {
            case LIST:
                for (String line : ClientSettingsBook.listed()) {
                    out.add(new Said(false, line));
                }
                break;
            case SET: {
                String problem = ClientSettingsBook.set(order.id, order.value, order.strength);
                out.add(
                    problem != null ? new Said(true, problem)
                        : new Said(false, ClientSettingRules.setWords(ClientSettingsBook.get(order.id))));
                break;
            }
            case CLEAR:
                out.add(new Said(false, ClientSettingRules.clearedWords(order.id, ClientSettingsBook.clear(order.id))));
                break;
            case CLEARALL:
                out.add(new Said(false, ClientSettingRules.clearedAllWords(ClientSettingsBook.clearAll())));
                break;
            case COPY:
                if (sender == null || !(sender.getEntity() instanceof ServerPlayer)) {
                    out.add(new Said(true, ClientSettingRules.COPY_NEEDS_A_PLAYER));
                } else if (!TrmtNetwork.askForSettings((ServerPlayer) sender.getEntity(), order.strength)) {
                    out.add(new Said(true, ClientSettingRules.COPY_NEEDS_NEWER));
                } else {
                    out.add(new Said(false, ClientSettingRules.COPY_ASKED));
                }
                break;
            default:
                out.add(new Said(true, ClientSettingRules.USAGE));
        }
        return out;
    }

    /** The words offered as the next one is typed. */
    static List<String> completions(String[] words) {
        if (words.length == 1) return Arrays.asList(ClientSettingRules.VERBS);
        String verb = words[0];
        if (words.length == 2 && ("set".equalsIgnoreCase(verb) || "clear".equalsIgnoreCase(verb))) {
            List<String> out = new ArrayList<String>();
            if ("clear".equalsIgnoreCase(verb)) {
                for (ClientSettingRules.Rule rule : ClientSettingsBook.rules()) {
                    out.add(rule.id);
                }
            } else {
                out.addAll(com.trmtgtnh.config.ClientSettingsHeld.settable());
            }
            return out;
        }
        if (words.length == 2 && "copy".equalsIgnoreCase(verb)) return Arrays.asList("force", "suggest");
        if (words.length >= 4 && "set".equalsIgnoreCase(verb)) return Arrays.asList("force", "suggest");
        return new ArrayList<String>();
    }

    /**
     * The whole lines offered for what is typed after {@code clientrules} (0.9.229): this edition's command takes the
     * rest of the line as one argument, and a suggestion replaces it whole, so each word {@link #completions} offers
     * is written after the words already typed. The last word is the one being typed, empty after a space.
     */
    static List<String> lines(String typed) {
        String[] words = typed.split(" ", -1);
        StringBuilder before = new StringBuilder();
        for (int i = 0; i < words.length - 1; i++) {
            before.append(words[i])
                .append(' ');
        }
        List<String> out = new ArrayList<String>();
        if (words.length < 2) return out;
        for (String offered : completions(Arrays.copyOfRange(words, 1, words.length))) {
            out.add(before + offered);
        }
        return out;
    }

    /** One line said, and whether it is a refusal, said in red. */
    static final class Said {

        final boolean refused;

        final String words;

        Said(boolean refused, String words) {
            this.refused = refused;
            this.words = words;
        }
    }
}
