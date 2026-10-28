package com.trmtgtnh.erosion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;

import com.trmtgtnh.util.MobWear;

/**
 * Which of 0.9.228's three groups a mob is in, by this version's own names for them (Xep: "As described"): hostile is
 * every monster - {@code Enemy}, which every boss here is too, the dragon and the wither both; villager is
 * {@code AbstractVillager}, which at this version holds the villager and the wandering trader and nothing else; and
 * passive is every other {@code Mob} - animals, iron and snow golems, water and ambient mobs. A living thing that is
 * not a mob at all, an armour stand, is in none, and only the list can count it. Asked of the class, so a test can ask
 * it of a class without making a world to put the mob in; the Golem of Ways is the engine's to leave out, before
 * its lead or any group is asked.
 */
public final class MobGroups {

    private MobGroups() {}

    /** The group a mob of this class is in, or null for one in none. */
    public static MobWear.Group groupOf(Class<?> type) {
        if (type == null) return null;
        // Hostile first: a monster is a monster whatever else it is, so a modded villager that attacks counts as one.
        if (Enemy.class.isAssignableFrom(type)) return MobWear.Group.HOSTILE;
        if (AbstractVillager.class.isAssignableFrom(type)) return MobWear.Group.VILLAGER;
        if (Mob.class.isAssignableFrom(type)) return MobWear.Group.PASSIVE;
        return null;
    }

    /** The group this entity is in, or null for one in none. */
    public static MobWear.Group groupOf(Entity entity) {
        return entity == null ? null : groupOf(entity.getClass());
    }

    /**
     * Every mob the game has registered, by registry name, sorted without regard to case - what the settings screen's
     * Add mob list offers and what a mobs-list name must answer to. A mob is any kind outside the miscellaneous
     * category, any kind with a spawn egg, and, where a world is given, any other kind that is made as a mob there: the
     * villager and the golems sit in the miscellaneous category beside arrows and boats, and a mod's mob may have no
     * egg. Made only to be asked, never added to the world; a kind that throws as it is made is no mob.
     */
    public static java.util.List<String> registered(net.minecraft.world.level.Level world) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (net.minecraft.world.entity.EntityType<?> type : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (key != null && isMob(type, world)) out.add(key.toString());
        }
        java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** Whether a kind is a mob, as {@link #registered} counts one. */
    public static boolean isMob(net.minecraft.world.entity.EntityType<?> type, net.minecraft.world.level.Level world) {
        if (type.getCategory() != net.minecraft.world.entity.MobCategory.MISC) return true;
        if (net.minecraft.world.item.SpawnEggItem.byId(type) != null) return true;
        if (world == null) return false;
        try {
            return type.create(world) instanceof Mob;
        } catch (Throwable hostile) {
            return false;
        }
    }

    /**
     * The name the mobs list knows this entity by: its registry name, exactly the name the list was looked up by before
     * 0.9.228. Null for an entity the registry does not hold, which the list then cannot name and nothing invents a name
     * for - inventing one would quietly hand an unregistered mob somebody else's number.
     */
    public static String nameOf(Entity entity) {
        if (entity == null) return null;
        ResourceLocation named = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return named == null ? null : named.toString();
    }
}
