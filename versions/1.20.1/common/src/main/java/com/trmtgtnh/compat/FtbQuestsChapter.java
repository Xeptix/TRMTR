package com.trmtgtnh.compat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

import com.trmtgtnh.ModsPresent;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.compat.QuestbookCompat.Quest;
import com.trmtgtnh.compat.QuestbookCompat.Stack;

/**
 * The quest chapter, written for FTB Quests (0.9.225; Xep's decision for 1.20.1: "the chapter written as FTB Quests'
 * data files on both loaders").
 *
 * <p>
 * The same seventeen quests {@link QuestbookCompat} writes for BetterQuesting, from the same {@code build()}, said in
 * FTB Quests' own files: one chapter, {@code config/ftbquests/quests/chapters/trmt_reimagined.snbt}. As with
 * BetterQuesting nothing of FTB Quests' code is touched - it reads that folder as the server starts
 * ({@code SERVER_STARTED}), and this is written at {@code serverStarting}, before it. Every key below is one its 2001
 * line reads ({@code BaseQuestFile.readDataFull}, {@code Quest.readData}, {@code ItemTask}, {@code ItemReward},
 * {@code RandomReward}'s {@code table_data}), so a choice carries its own table and no reward-table file is written.
 *
 * <p>
 * The chapter names its items as 1.12.2 did, as every edition's does, so each is asked of this game before it is
 * written: the grass block and the player head by their new names, this mod's own items as they registered, and
 * anything this pack has not got - the loot bags and the HarvestCraft food the GregTech: New Horizons rewards name -
 * left out rather than written as a missing item. A quest keeps its place whatever its rewards come to.
 *
 * <p>
 * Written once per stamp, as the BetterQuesting folder is: FTB Quests saves a chapter in its own shape whenever an
 * editor touches it, so a chapter rewritten on every start would throw away a pack maker's edits. The stamp is the
 * one {@link QuestbookCompat} keeps - the version, the lessons and the golem switches - in its own file beside the
 * chapters, where FTB Quests does not look.
 */
public final class FtbQuestsChapter {

    private static final String FTB_QUESTS = "ftbquests";

    static final String FILE_NAME = "trmt_reimagined";

    private static final String STAMP = ".trmt-version";

    /** The id word every id here is built on, as BetterQuesting's are: "TRMT", which no generated id holds. */
    private static final long BASE = QuestbookCompat.ID_HIGH << 32;

    /** The chapter's own id. Quests take their number, tasks and rewards a slot above it. */
    static final long CHAPTER_ID = BASE | 0x10000000L;

    private FtbQuestsChapter() {}

    public static boolean active() {
        return TrmtConfig.questbook && TrmtConfig.gtnhEnhanced && ModsPresent.has(FTB_QUESTS);
    }

    /** How many quests the chapter holds in this pack, for the harness to hold FTB Quests to. */
    public static int expectedQuests() {
        return QuestbookCompat.build()
            .size();
    }

    public static long chapterId() {
        return CHAPTER_ID;
    }

    /** As the server starts, before FTB Quests reads its folder. */
    public static void write() {
        if (!active()) return;
        try {
            File quests = questsFolder();
            if (quests == null) return;
            File chapters = new File(quests, "chapters");
            File chapter = new File(chapters, FILE_NAME + ".snbt");
            File stamp = new File(quests, STAMP);
            List<Quest> all = QuestbookCompat.build();
            if (all.isEmpty()) return;
            Tally tally = new Tally();
            String text = chapter(all, tally);
            // The stamp names what the chapter holds as well as the build it came from (0.9.225): the stand-in rewards
            // changed the chapter within one version, and every 0.9.225 snapshot a tester had played kept its old one.
            String wanted = "generatedFor=" + QuestbookCompat.stamp() + "/chapter=" + Integer.toHexString(text.hashCode())
                + "\n";
            if (chapter.isFile() && stamp.isFile()
                && wanted.equals(new String(Files.readAllBytes(stamp.toPath()), StandardCharsets.UTF_8))) {
                Trmt.LOG.debug("The FTB Quests chapter is already current");
                return;
            }
            if (!chapters.isDirectory() && !chapters.mkdirs()) {
                Trmt.LOG.warn("Could not create {}", chapters);
                return;
            }
            write(chapter, text);
            write(stamp, wanted);
            Trmt.LOG.info(
                "Wrote the '{}' chapter for FTB Quests ({} quests; {} rewards paid in vanilla stand-ins and {} items left out, that this pack has not got).",
                QuestbookCompat.LINE_NAME,
                Integer.valueOf(all.size()),
                Integer.valueOf(tally.stood),
                Integer.valueOf(tally.left));
        } catch (IOException | RuntimeException problem) {
            Trmt.LOG.warn("Could not write the FTB Quests chapter", problem);
        }
    }

    private static File questsFolder() {
        File own = TrmtConfig.raw() == null ? null
            : TrmtConfig.raw()
                .getConfigFile();
        File configDir = own == null ? null : own.getParentFile();
        return configDir == null ? null : new File(new File(configDir, FTB_QUESTS), "quests");
    }

    private static void write(File file, String contents) throws IOException {
        Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        try {
            writer.write(contents);
        } finally {
            writer.close();
        }
    }

    // ------------------------------------------------------------------
    // The file
    // ------------------------------------------------------------------

    /** The chapter as FTB Quests writes one: a key to a line, tabs, no commas, counted into {@code tally}. */
    static String chapter(List<Quest> all, Tally tally) {
        Set<Long> written = new HashSet<Long>();
        for (Quest quest : all) written.add(Long.valueOf(quest.id));
        Snbt out = new Snbt();
        out.open();
        out.text("id", hex(CHAPTER_ID));
        out.text("filename", FILE_NAME);
        out.text("title", QuestbookCompat.LINE_NAME);
        String icon = item(Stack.of(QuestbookCompat.registryName(
            com.trmtgtnh.item.ModItems.magicTamper(),
            "minecraft:stone_shovel")));
        if (icon != null) out.raw("icon", icon);
        out.raw("order_index", "999");
        out.openList("quests");
        for (Quest quest : all) quest(out, quest, written, tally);
        out.closeList();
        out.openList("quest_links");
        out.closeList();
        out.close();
        return out.toString();
    }

    private static void quest(Snbt out, Quest quest, Set<Long> written, Tally tally) {
        out.open();
        out.text("id", hex(BASE | quest.id));
        // BetterQuesting places by the pixel, a quest every 48; FTB Quests by the quest, so 24 to one leaves a
        // quest's width between neighbours as the book has.
        out.raw("x", quest.x / 24.0D + "d");
        out.raw("y", quest.y / 24.0D + "d");
        out.text("title", quest.title);
        String icon = item(quest.icon);
        if (icon != null) out.raw("icon", icon);
        if (quest.main) out.raw("size", "1.5d");
        out.openList("description");
        for (String line : quest.description.split("\n", -1)) out.item(quote(described(line)));
        out.closeList();
        out.openList("dependencies");
        for (Quest parent : quest.parents) {
            if (written.contains(Long.valueOf(parent.id))) out.item(quote(hex(BASE | parent.id)));
        }
        out.closeList();
        out.openList("tasks");
        for (int at = 0; at < quest.taskItems.size(); at++) {
            Stack wanted = quest.taskItems.get(at);
            String item = wanted == null ? null : item(wanted);
            if (wanted != null && item == null) {
                tally.left++;
                continue;
            }
            out.open();
            out.text("id", hex(BASE | quest.id << 16 | 0x100L | at));
            if (wanted == null) {
                out.text("type", "checkmark");
            } else {
                out.text("type", "item");
                out.raw("item", item);
                out.raw("count", wanted.count + "L");
                // A tamper is asked for whatever its wear, which is its damage and therefore its tag here; a book is
                // asked for by its lesson, which is its tag too, so it alone matches - weakly, as BetterQuesting's
                // partial match did, so a book carrying more than the one lesson still counts.
                boolean matched = !quest.taskIgnoresNbt.get(at)
                    .booleanValue();
                out.raw("match_nbt", matched ? "true" : "false");
                if (matched) out.raw("weak_nbt_match", "true");
            }
            out.close();
        }
        out.closeList();
        out.openList("rewards");
        int paid = 0;
        for (int at = 0; at < quest.rewardItems.size(); at++) {
            // Each reward slot takes sixteen ids, so a bag paid in two stand-ins keeps them apart.
            long id = BASE | quest.id << 16 | 0x200L | (long) at << 4;
            Stack given = quest.rewardItems.get(at);
            if (given != null) {
                // The pack's own item where it has it, its vanilla stand-ins where it has not (0.9.225, Xep's decision).
                List<Stack> paying = item(given) != null ? java.util.Collections.singletonList(given) : QuestbookCompat.standInsFor(given);
                if (paying.isEmpty()) {
                    tally.left++;
                    continue;
                }
                if (paying.get(0) != given) tally.stood++;
                for (int k = 0; k < paying.size(); k++) {
                    String item = item(paying.get(k));
                    if (item == null) {
                        tally.left++;
                        continue;
                    }
                    out.open();
                    out.text("id", hex(id | k));
                    out.text("type", "item");
                    out.raw("item", item);
                    out.raw("count", Integer.toString(paying.get(k).count));
                    out.close();
                    paid++;
                }
                continue;
            }
            Snbt table = new Snbt();
            Set<String> offered = new HashSet<String>();
            int missingAt = 0;
            for (Stack choice : quest.rewardChoices.get(at)) {
                Stack paying = item(choice) != null ? choice : QuestbookCompat.standInChoice(choice, missingAt++);
                String item = paying == null ? null : item(paying);
                if (item == null) {
                    tally.left++;
                    continue;
                }
                if (paying != choice) tally.stood++;
                // Offered once: two options that stand in as the same stack are one option.
                if (!offered.add(item + "x" + paying.count)) continue;
                table.open();
                table.text("type", "item");
                table.raw("item", item);
                table.raw("count", Integer.toString(paying.count));
                table.close();
            }
            if (offered.isEmpty()) continue;
            out.open();
            out.text("id", hex(id));
            out.text("type", "choice");
            out.openObject("table_data");
            out.raw("loot_size", "1");
            out.openList("rewards");
            out.append(table);
            out.closeList();
            out.closeObject();
            out.close();
            paid++;
        }
        out.closeList();
        out.close();
        tally.paid.put(quest.title, Integer.valueOf(paid));
    }

    /**
     * What writing the chapter came to: rewards left out, rewards stood in for, and how many rewards each quest pays, by
     * its title - which the walk harness holds FTB Quests' own file to (0.9.225).
     */
    static final class Tally {

        int left;

        int stood;

        final java.util.Map<String, Integer> paid = new java.util.LinkedHashMap<String, Integer>();
    }

    /** How many rewards each quest of this pack's chapter pays, by title, for the harness to read FTB Quests against. */
    public static java.util.Map<String, Integer> rewardsPaid() {
        Tally tally = new Tally();
        chapter(QuestbookCompat.build(), tally);
        return tally.paid;
    }

    /** BetterQuesting's two marks, in the formatting codes FTB Quests reads. */
    static String described(String line) {
        return line.replace("[note]", "&e")
            .replace("[warn]", "&c")
            .replace("[/note]", "&r")
            .replace("[/warn]", "&r");
    }

    /**
     * One stack as this game names it, or null where it has nothing by that name. The two vanilla names the chapter uses
     * that 1.13 changed are put right here, and nowhere wider: {@code minecraft:grass} is a plant now, which is why
     * {@code OldNames} leaves it alone, while the chapter means the block.
     */
    static String item(Stack stack) {
        String id = stack.id;
        if ("minecraft:grass".equals(id)) id = "minecraft:grass_block";
        if ("minecraft:skull".equals(id)) id = stack.damage == 3 ? "minecraft:player_head" : "minecraft:skeleton_skull";
        ResourceLocation name = ResourceLocation.tryParse(id);
        if (name == null || !BuiltInRegistries.ITEM.containsKey(name)) return null;
        StringBuilder out = new StringBuilder("{ id: ").append(quote(name.toString()))
            .append(" Count: 1b");
        String stored = enchantments(stack.stored);
        String applied = enchantments(stack.applied);
        if (stored != null) out.append(" tag: { StoredEnchantments: ")
            .append(stored)
            .append(" }");
        else if (applied != null) out.append(" tag: { Enchantments: ")
            .append(applied)
            .append(" }");
        return out.append(" }")
            .toString();
    }

    /** Each lesson at level one, the only level any has, by the name its id holds in this game. */
    private static String enchantments(int[] ids) {
        StringBuilder out = new StringBuilder();
        for (int id : ids) {
            Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.byId(id);
            ResourceLocation name = enchantment == null ? null : BuiltInRegistries.ENCHANTMENT.getKey(enchantment);
            if (name == null) continue;
            out.append(out.length() == 0 ? "[" : " ")
                .append("{ id: ")
                .append(quote(name.toString()))
                .append(" lvl: 1s }");
        }
        return out.length() == 0 ? null
            : out.append("]")
                .toString();
    }

    static String hex(long id) {
        return String.format("%016X", Long.valueOf(id));
    }

    static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int at = 0; at < text.length(); at++) {
            char c = text.charAt(at);
            if (c == '"' || c == '\\') out.append('\\');
            out.append(c);
        }
        return out.append('"')
            .toString();
    }

    /** FTB Library's SNBT as it writes it: one entry to a line, tab indented, no separators. */
    static final class Snbt {

        private final StringBuilder out = new StringBuilder();

        private int depth;

        private void indent() {
            for (int at = 0; at < depth; at++) out.append('\t');
        }

        void open() {
            indent();
            out.append("{\n");
            depth++;
        }

        void close() {
            depth--;
            indent();
            out.append("}\n");
        }

        void openObject(String key) {
            indent();
            out.append(key)
                .append(": {\n");
            depth++;
        }

        void closeObject() {
            close();
        }

        void openList(String key) {
            indent();
            out.append(key)
                .append(": [\n");
            depth++;
        }

        void closeList() {
            depth--;
            indent();
            out.append("]\n");
        }

        void text(String key, String value) {
            raw(key, quote(value));
        }

        void raw(String key, String value) {
            indent();
            out.append(key)
                .append(": ")
                .append(value)
                .append('\n');
        }

        void item(String value) {
            indent();
            out.append(value)
                .append('\n');
        }

        /** Another builder's entries, at this one's depth. */
        void append(Snbt other) {
            for (String line : other.out.toString()
                .split("\n")) {
                if (line.isEmpty()) continue;
                indent();
                out.append(line)
                    .append('\n');
            }
        }

        @Override
        public String toString() {
            return out.toString();
        }
    }
}
