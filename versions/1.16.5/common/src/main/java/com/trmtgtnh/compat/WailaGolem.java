package com.trmtgtnh.compat;

import java.util.List;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.level.Level;

import com.trmtgtnh.entity.EntityGolemOfWays;
import com.trmtgtnh.entity.GolemCombat;
import com.trmtgtnh.entity.GolemUpgrade;


/**
 * What a Golem of Ways will tell you when you look at it.
 *
 * <p>
 * The two questions anybody standing in front of one actually has: whose is it, and why is it not
 * doing anything. So it reports who built it and who last gave it orders, it says what it is doing
 * this moment when it is doing something, and it says plainly when it has no tool, nothing to mend
 * with, or no orders at all - which between them cover every reason a golem stands still. A golem
 * that has left its road altogether gets a line of its own, because a keeper walking away from the
 * ground it keeps is the most alarming thing one of these can do and the explanation is short.
 *
 * <p>
 * Everything shown is read from the entity's own watched values, so no server round trip is needed
 * and the tooltip is right the moment it changes. That matters more than it sounds for the tool,
 * the material and the orders: a client's copy of the storage stays empty until somebody opens the
 * screen and its orders never cross at all, so counting either on this side would have called a
 * fully stocked golem under full instruction empty and idle.
 */
/**
 * The lines a Golem of Ways writes into an entity tooltip. See {@link WailaCompat} for why no
 * tooltip type is named here.
 */
public final class WailaGolem {

    private WailaGolem() {}

    /** Every line for one golem, appended in the order they are said. */
    public static void golem(List<String> into, Entity entity) {
        if (!(entity instanceof EntityGolemOfWays)) return;
        EntityGolemOfWays golem = (EntityGolemOfWays) entity;
        GolemUpgrade fitted = golem.fittedUpgrade();
        if (fitted != GolemUpgrade.NONE) {
            into.add(
                ChatFormatting.AQUA + com.trmtgtnh.util.Translate.get("trmtgtnh.golem.waila.upgrade")
                    + " "
                    + ChatFormatting.WHITE
                    + com.trmtgtnh.util.Translate.get(fitted.itemNameKey()));
        }

        into.add(
            ChatFormatting.GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.golem.gui.radius")
                + " "
                + ChatFormatting.WHITE
                + golem.watchedRadius());

        // What it is doing. Chewing is asked before the busy kinds because it is not one of
        // them: eating is carried by a bit of its own rather than by a stroke of work, so a
        // golem visibly taking a mouthful - arms up to its mouth, head dipping to meet them,
        // the eating sound with it - used to report that it had nothing to do for as long as
        // the mouthful lasted. The state was already on the wire and simply never asked for.
        String doing = doingKey(golem.busyKind());
        if (golem.isFleeing()) {
            into.add(ChatFormatting.RED + com.trmtgtnh.util.Translate.get("trmtgtnh.golem.waila.fleeing"));
        } else if (golem.isChewing()) {
            into.add(ChatFormatting.GREEN + com.trmtgtnh.util.Translate.get("trmtgtnh.golem.waila.chewing"));
        } else if (doing != null) {
            into.add(ChatFormatting.GREEN + com.trmtgtnh.util.Translate.get(doing));
        } else {
            into.add(ChatFormatting.GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.golem.waila.idle"));
        }

        // And, separately, whatever is stopping it - which is not the same question and used to be
        // asked as though it were. A golem wearing ground perfectly well can still be unable to
        // mend any of it, and hanging that behind "is it busy" meant the one line that would have
        // explained the problem was the one line never shown while the problem was happening.
        if (!golem.isArmed()) {
            into.add(ChatFormatting.RED + com.trmtgtnh.util.Translate.get("trmtgtnh.golem.waila.noTool"));
        }
        if (!golem.watchedOrders()) {
            into.add(ChatFormatting.RED + com.trmtgtnh.util.Translate.get("trmtgtnh.golem.waila.noOrders"));
        }
        if (!golem.hasMendingStock()) {
            into.add(ChatFormatting.RED + com.trmtgtnh.util.Translate.get("trmtgtnh.golem.waila.noBlocks"));
        } else if (golem.watchedShortOfBlocks()) {
            into.add(ChatFormatting.RED + com.trmtgtnh.util.Translate.get("trmtgtnh.golem.waila.wrongBlocks"));
        }

        String built = golem.watchedSummoner();
        if (built != null && !built.isEmpty()) {
            into.add(ChatFormatting.DARK_GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.golem.gui.built") + " " + built);
        }
        String told = golem.watchedConfigurer();
        if (told != null && !told.isEmpty()) {
            into.add(ChatFormatting.DARK_GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.golem.waila.told") + " " + told);
        }
        return;
    }

    /** What to call whatever it has its tool up for, or null when it has it down. */
    private static String doingKey(int busy) {
        switch (busy) {
            case GolemCombat.BUSY_WEAR:
                return "trmtgtnh.golem.waila.wearing";
            case GolemCombat.BUSY_MEND:
                return "trmtgtnh.golem.waila.mending";
            case GolemCombat.BUSY_FIGHT:
                return "trmtgtnh.golem.waila.fighting";
            default:
                return null;
        }
    }

}
