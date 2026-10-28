package com.trmtgtnh.client.gui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Quaternionf;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;

import com.trmtgtnh.Trmt;

/**
 * A mob row's picture, on both settings screens (0.9.228), in the order {@link MobRows#picture} decides: the mob
 * itself, drawn small - where there is a world to make one in, because a mob is made in a world and the title screen
 * has none; else its spawn egg; else this mod's own generic egg, {@code textures/gui/mob_egg.png}. A name no mob
 * answers to gets nothing. Names no Cloth type, so the mod's own screen loads it without Cloth; the Cloth screen's
 * rows ask it too.
 *
 * <p>
 * A mob is drawn as the game draws one in a screen, {@code InventoryScreen.renderEntityInInventory} - at 1.20.1 handed
 * the mob's feet and a whole number of pixels to the block - which hands the frame to whatever renderer its mod
 * registered, so a mob that cannot be made or drawn is never tried again that session and falls back to its egg. Each
 * kind is made once per world and kept, never added to it: it is a picture, not a creature in the world.
 */
public final class MobPictures {

    /** The generic egg: the last fallback, so a known mob without an egg still looks unlike an unknown name. */
    static final ResourceLocation GENERIC = new ResourceLocation(Trmt.MODID, "textures/gui/mob_egg.png");

    /** The mobs made for their pictures, by kind, for the world they were made in. */
    private static final Map<EntityType<?>, LivingEntity> MADE = new HashMap<EntityType<?>, LivingEntity>();

    /** Kinds whose mob could not be made or drawn, never tried again. */
    private static final Set<EntityType<?>> BROKEN = new HashSet<EntityType<?>>();

    private static Level madeIn;

    private static List<String> registered;

    private static Level registeredIn;

    private static int registeredFrom = -1;

    /** How long the last mob made took to make, for the settingsperf part's slowest mob (0.9.228). */
    static long madeNanos;

    private MobPictures() {}

    /**
     * Every mob the game answers to, read again only when the registry grows or another world is open - the world
     * decides which of the miscellaneous kinds are mobs ({@code MobGroups.registered}).
     */
    static List<String> registered() {
        Minecraft client = Minecraft.getInstance();
        Level level = client == null ? null : client.level;
        int size = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.size();
        if (registered == null || size != registeredFrom || level != registeredIn) {
            registered = com.trmtgtnh.erosion.MobGroups.registered(level);
            registeredFrom = size;
            registeredIn = level;
        }
        return registered;
    }

    /** The first registered mob a line names, or null where none answers. */
    static EntityType<?> typeOf(String line) {
        for (String name : ConfigLayout.mobNames(line)) {
            ResourceLocation at = ResourceLocation.tryParse(name);
            if (at == null) continue;
            EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(at)
                .orElse(null);
            if (type != null) return type;
        }
        return null;
    }

    /** Draws the picture for one line of the mobs list, 16 by 16 with its top left at x, y. */
    public static void draw(GuiGraphics graphics, String line, int x, int y) {
        EntityType<?> type = typeOf(line);
        LivingEntity mob = type == null ? null : mobFor(type);
        SpawnEggItem egg = type == null || mob != null ? null : SpawnEggItem.byId(type);
        switch (MobRows.picture(line, registered(), mob != null, egg != null)) {
            case MOB:
                if (drawMob(graphics, type, mob, x, y)) return;
                // Refused as it was drawn: its egg, or the generic one, from here on.
                SpawnEggItem fallback = SpawnEggItem.byId(type);
                if (fallback != null) WearIcons.drawIcon(graphics, null, new ItemStack(fallback), x, y);
                else drawGeneric(graphics, x, y);
                return;
            case EGG:
                WearIcons.drawIcon(graphics, null, new ItemStack(egg), x, y);
                return;
            case GENERIC:
                drawGeneric(graphics, x, y);
                return;
            default:
        }
    }

    /** The mob of this kind, made once in the world the client is in; null with no world, or for one that failed. */
    private static LivingEntity mobFor(EntityType<?> type) {
        Minecraft client = Minecraft.getInstance();
        Level level = client == null ? null : client.level;
        if (level == null) return null;
        if (level != madeIn) {
            MADE.clear();
            madeIn = level;
        }
        if (BROKEN.contains(type)) return null;
        if (MADE.containsKey(type)) return MADE.get(type);
        LivingEntity made = null;
        long started = System.nanoTime();
        try {
            Entity entity = type.create(level);
            if (entity instanceof LivingEntity) made = (LivingEntity) entity;
        } catch (Throwable unmade) {
            BROKEN.add(type);
            return null;
        } finally {
            madeNanos = System.nanoTime() - started;
        }
        if (MADE.size() > 256) MADE.clear();
        MADE.put(type, made);
        return made;
    }

    /**
     * The mob, fitted into 16 by 16 standing on the bottom edge, turning slowly to the clock's angle (spec C6); a mob
     * whose drawing throws is not drawn again.
     */
    private static boolean drawMob(GuiGraphics graphics, EntityType<?> type, LivingEntity mob, int x, int y) {
        try {
            float size = Math.max(mob.getBbHeight(), mob.getBbWidth());
            int scale = Math.max(1, Math.min(16, Math.round(15F / Math.max(0.1F, size))));
            turned(graphics, x + 8, y + 15, scale, MobRows.turn(System.currentTimeMillis()), mob);
            return true;
        } catch (Throwable hostileRenderer) {
            BROKEN.add(type);
            MADE.remove(type);
            return false;
        }
    }

    /**
     * The mob drawn as the inventory draws the player ({@code InventoryScreen.renderEntityInInventory}), but turned
     * about its upright axis to {@code angle} rather than towards the pointer (spec C6): body, head and look all at the
     * one angle, nought facing out of the screen as there, and set back afterwards, since the mob is kept and drawn
     * again. The 1.7.10 edition's turn; the inventory's own steps otherwise, which at 1.20.1 take the frame's
     * {@code GuiGraphics} and the two turns as quaternions.
     */
    private static void turned(GuiGraphics graphics, int x, int y, int scale, float angle, LivingEntity mob) {
        float body = mob.yBodyRot;
        float bodyBefore = mob.yBodyRotO;
        float yaw = mob.getYRot();
        float yawBefore = mob.yRotO;
        float pitch = mob.getXRot();
        float pitchBefore = mob.xRotO;
        float headBefore = mob.yHeadRotO;
        float head = mob.yHeadRot;
        mob.yBodyRot = 180.0F + angle;
        mob.yBodyRotO = mob.yBodyRot;
        mob.setYRot(180.0F + angle);
        mob.yRotO = mob.getYRot();
        mob.setXRot(0.0F);
        mob.xRotO = 0.0F;
        mob.yHeadRot = mob.getYRot();
        mob.yHeadRotO = mob.getYRot();
        Quaternionf upright = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf look = new Quaternionf();
        upright.mul(look);
        try {
            InventoryScreen.renderEntityInInventory(graphics, x, y, scale, upright, look, mob);
        } finally {
            mob.yBodyRot = body;
            mob.yBodyRotO = bodyBefore;
            mob.setYRot(yaw);
            mob.yRotO = yawBefore;
            mob.setXRot(pitch);
            mob.xRotO = pitchBefore;
            mob.yHeadRotO = headBefore;
            mob.yHeadRot = head;
        }
    }

    /** The generic egg, for {@code *} in the mob list: every mob no line names (spec M3.2, ANY_MOB). */
    static void drawGenericEgg(GuiGraphics graphics, int x, int y) {
        drawGeneric(graphics, x, y);
    }

    /** The generic egg, at full size. */
    private static void drawGeneric(GuiGraphics graphics, int x, int y) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        graphics.blit(GENERIC, x, y, 0F, 0F, 16, 16, 16, 16);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }
}
