package com.trmtgtnh.erosion;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;

import com.trmtgtnh.util.MobWear;

/**
 * Which group a mob is in, and the name the mobs list knows it by (0.9.228): this edition's half of
 * {@link MobWear}, whose rules are plain Java and the same in every edition. Apart from {@link ErosionEngine} so a
 * test can ask it by class, without the engine or a world - the 1.7.10 edition's {@code MobGroups}, with this game's
 * names for the groups.
 */
public final class MobGroups {

    private MobGroups() {}

    /** A mob's group, by its class; null for a mover that is no mob - a player, an armour stand. */
    public static MobWear.Group groupOf(Entity entity) {
        return entity == null ? null : groupOf(entity.getClass());
    }

    /**
     * Which group a mob of this class is in, as this game names them (Xep, 2026-10-20: "As described"):
     * <strong>hostile</strong> - anything the game marks as a monster ({@code Enemy}: zombies, creepers, slimes,
     * ghasts, shulkers, and both bosses, the dragon and the wither, which this game marks as monsters too - it has no
     * boss mark of its own, where 1.7.10 has {@code IBossDisplayData}); <strong>villager</strong> - a villager or a
     * wandering trader ({@code AbstractVillager}, which holds those two and no other); <strong>passive</strong> -
     * every other mob ({@code Mob}): animals, iron and snow golems, squid, bats, and every modded creature that is
     * none of the above. The hostile test comes first, so a modded villager the game counts as a monster wears as one,
     * as in 1.7.10. Null for a class that is no mob. Asked of the class, which loads it and makes nothing.
     */
    public static MobWear.Group groupOf(Class<?> type) {
        if (type == null || !Mob.class.isAssignableFrom(type)) return null;
        if (Enemy.class.isAssignableFrom(type)) return MobWear.Group.HOSTILE;
        if (AbstractVillager.class.isAssignableFrom(type)) return MobWear.Group.VILLAGER;
        return MobWear.Group.PASSIVE;
    }

    /**
     * The name the mobs list knows a mob by: its registry name, as it always was here. The registry answers this; an
     * entity no longer carries its own name. Null is passed through rather than turned into a default, which is what
     * both older editions do with an entity that is not registered - the lookup already knows what to do with it, and
     * inventing a name here would quietly give an unregistered mob somebody else's multiplier.
     */
    public static String nameOf(Entity entity) {
        if (entity == null) return null;
        Identifier named = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return named == null ? null : named.toString();
    }
}
