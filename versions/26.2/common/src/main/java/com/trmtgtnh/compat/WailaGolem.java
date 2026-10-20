package com.trmtgtnh.compat;

import java.util.List;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.level.Level;

import com.trmtgtnh.entity.EntityGolemOfWays;


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
        // Worded in TooltipLines, which words the same lines for The One Probe.
        into.addAll(TooltipLines.golem((EntityGolemOfWays) entity, com.trmtgtnh.util.Translate::get));
    }
}
