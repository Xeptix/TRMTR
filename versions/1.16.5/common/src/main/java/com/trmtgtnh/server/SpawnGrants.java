package com.trmtgtnh.server;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.item.GuideBook;
import com.trmtgtnh.item.ItemChunkTamper;
import com.trmtgtnh.item.ModItems;
import com.trmtgtnh.item.TamperGrade;

/**
 * The starting items, handed out on login rather than only on a fresh spawn.
 *
 * <p>
 * "Spawn with a copy" is the obvious way to say it and the wrong way to build it: a world that has
 * been played for a month has no fresh spawns left in it, so turning the setting on would do
 * nothing for anybody already there. What actually happens is that anyone who has never been given
 * that item <em>in this save</em> gets one the next time they log in - so switching a setting on
 * reaches the people already playing, and switching it off and on again does not hand out seconds.
 *
 * <p>
 * Everything here is off by default. A mod that puts items in your inventory uninvited is a mod
 * somebody will be annoyed by, so it only ever happens because a packmaker asked for it.
 */
public final class SpawnGrants {

    private SpawnGrants() {}

    /** Gives this player anything they are owed and have never had here. */
    public static void onLogin(Player player) {
        if (player == null || player.level == null || player.level.isClientSide()) return;

        Level store = storeWorld(player);
        SpawnGrantData data = SpawnGrantData.get(store);
        if (data == null) return;
        String id = player.getUUID() == null ? null
            : player.getUUID()
                .toString();
        if (id == null) return;

        if (TrmtConfig.spawnWithTamper) grant(player, data, id, "tamper", tamper(ModItems.gradedTamper()));
        if (TrmtConfig.spawnWithChunkTamper) {
            grant(player, data, id, "chunk_tamper", tamper(ModItems.chunkTamper()));
        }
        if (TrmtConfig.spawnWithWayfarer && ModItems.magicTamper() != null) {
            grant(player, data, id, "magic_tamper", new ItemStack(ModItems.magicTamper()));
        }
        grantBook(player, data, id, GuideBook.MK1, TrmtConfig.spawnWithGuideMk1);
        grantBook(player, data, id, GuideBook.MK2, TrmtConfig.spawnWithGuideMk2);
        grantBook(player, data, id, GuideBook.COMMANDS, TrmtConfig.spawnWithGuideCommands);
        grantBook(player, data, id, GuideBook.GOLEM, TrmtConfig.spawnWithGuideGolem);
    }

    private static void grantBook(Player player, SpawnGrantData data, String id, GuideBook book, boolean on) {
        if (!on) return;
        Item item = ModItems.guide(book);
        if (item == null) return;
        grant(player, data, id, book.itemName(), new ItemStack(item));
    }

    /**
     * A plain tamper at the first grade the pack can supply, which is the starter tier rather
     * than whatever happens to be best.
     */
    private static ItemStack tamper(Item item) {
        if (item == null) return null;
        ItemStack stack = new ItemStack(item);
        TamperGrade grade = TamperGrade.available()
            .isEmpty() ? null
                : TamperGrade.available()
                    .get(0);
        if (grade != null) ItemChunkTamper.setGrade(stack, grade);
        return stack;
    }

    private static void grant(Player player, SpawnGrantData data, String id, String key, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        if (data.hasHad(key, id)) return;

        // Recorded whether or not it fits, so a full inventory cannot turn into a second copy
        // at the next login. It is dropped at their feet instead, which is what vanilla does
        // with anything it cannot fit.
        data.record(key, id);
        if (!player.inventory.add(stack)) {
            player.drop(stack, false);
        }
        Trmt.LOG.debug("Gave {} their starting {}", player.getName(), key);
    }

    /**
     * The world the record lives in.
     *
     * <p>
     * Map storage is shared across dimensions, so any loaded world answers - but the overworld is
     * asked for by name so the file is always made in the same place regardless of where somebody
     * happens to log in.
     */
    private static Level storeWorld(Player player) {
        net.minecraft.server.MinecraftServer server = Trmt.runningServer();
        if (server != null && server.overworld() != null) return server.overworld();
        return player.level;
    }
}
