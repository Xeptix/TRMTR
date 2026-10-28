package com.trmtgtnh.erosion;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityVillager;

import com.trmtgtnh.util.MobWear;

/**
 * Which group a mob is in, and the name the mobs list knows it by (0.9.228): this edition's half of
 * {@link MobWear}, whose rules are plain Java and the same in every edition. Apart from {@link ErosionEngine} so a
 * test can ask it by class, without the engine or a world.
 */
public final class MobGroups {

    private MobGroups() {}

    /** A mob's group, by its class; null for a mover that is no mob - a player, or a modded living thing. */
    public static MobWear.Group groupOf(Entity entity) {
        return entity == null ? null : groupOf(entity.getClass());
    }

    /**
     * Which group a mob of this class is in, as 1.7.10 names them (Xep, 2026-10-20: "As described"):
     * <strong>hostile</strong> - anything the game marks as a monster ({@code IMob}: zombies, creepers, slimes, ghasts,
     * the dragon) or as a boss ({@code IBossDisplayData}: the wither too, and a modded boss that is no {@code IMob});
     * <strong>villager</strong> - a villager ({@code EntityVillager}, its children included; this version has no
     * wandering trader); <strong>passive</strong> - every other mob ({@code EntityLiving}): animals, iron and snow
     * golems, squid, bats, and every modded creature that is none of the above. The hostile test comes first, so a
     * modded villager the game counts as a monster wears as one. Null for a class that is no mob. Asked of the class,
     * which loads it and makes nothing.
     */
    public static MobWear.Group groupOf(Class<?> type) {
        if (type == null || !EntityLiving.class.isAssignableFrom(type)) return null;
        if (IMob.class.isAssignableFrom(type) || IBossDisplayData.class.isAssignableFrom(type)) {
            return MobWear.Group.HOSTILE;
        }
        if (EntityVillager.class.isAssignableFrom(type)) return MobWear.Group.VILLAGER;
        return MobWear.Group.PASSIVE;
    }

    /** The name the mobs list knows a mob by: its entity registry name, as it always was. */
    public static String nameOf(Entity entity) {
        return EntityList.getEntityString(entity);
    }
}
