package com.trmtgtnh.entity;

import com.trmtgtnh.Trmt;

import cpw.mods.fml.common.registry.EntityRegistry;

/**
 * The mod's entities, registered on both sides.
 *
 * <p>
 * Mod-scoped rather than global. A global entity id would buy a vanilla spawn egg and cost a slot
 * out of a space every mod in a large pack is competing for; the egg is a small item of our own
 * instead, which also gets to look like the thing it spawns.
 *
 * <p>
 * The tracking range matches the furthest the golem can be told to work, so a player standing at
 * the edge of its round still sees it. It is slow, so it is updated infrequently.
 */
public final class ModEntities {

    private ModEntities() {}

    public static void register() {
        EntityRegistry.registerModEntity(EntityGolemOfWays.class, "golem_of_ways", 0, Trmt.instance, 80, 3, true);
        Trmt.LOG.info("Registered the Golem of Ways");
    }
}
