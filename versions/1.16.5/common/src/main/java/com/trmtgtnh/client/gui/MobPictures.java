package com.trmtgtnh.client.gui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.util.EntityNames;

/**
 * The picture beside a line of the mob list, on both settings screens (0.9.228), in the order
 * {@link ConfigLayout#picture} gives: the mob itself, drawn small, the way this version draws an entity in a screen
 * ({@code InventoryScreen.renderEntityInInventory}, which draws the player in the inventory), turning slowly (spec C6); else its spawn egg; else
 * this mod's own generic egg, {@code textures/gui/mob_egg.png}. A name no mob answers to gets none, and is red.
 *
 * <p>
 * A mob is made to be drawn, and a mob at this version cannot be made without a world - its constructor asks the
 * world for its goals - so outside one, on the title screen's way in, every known mob is drawn as its egg or the
 * generic egg instead. One of each kind is made once per world and kept, never added to the world and never ticked.
 * A mob whose making or drawing throws is never asked again for the session, and is drawn as its egg: a name can reach
 * any mod in the pack, and a mod's renderer throwing is a reason to draw something else, never to take the screen down.
 *
 * <p>
 * What a line shows is worked out once for its text ({@link #shown}) and held by its row in a {@link RowPicture}, so a
 * row added or a name typed has its picture on the next frame and a row left alone asks nothing again.
 */
public final class MobPictures {

    /** This mod's own generic spawn egg, sixteen by sixteen: the last of the three pictures. */
    static final ResourceLocation GENERIC = new ResourceLocation(Trmt.MODID, "textures/gui/mob_egg.png");

    /** Every mob the game has registered, asked once a session: registries are frozen by the time a screen opens. */
    private static List<String> registered;

    /** The mobs made for drawing, by name, and the world they were made in. */
    private static final Map<String, LivingEntity> MADE = new HashMap<String, LivingEntity>();

    private static Level madeIn;

    /** The kinds that could not be made or drawn, which are drawn as their egg from then on. */
    private static final Set<String> REFUSED = new HashSet<String>();

    /** How long the last mob made took to make, for the settingsperf part's slowest mob (0.9.228). */
    static long madeNanos;

    private MobPictures() {}

    /** What one line shows: whether it is red, the mob it stands for, and its picture. */
    public static final class Shown {

        /** No mob in this game answers to the line's name. */
        public final boolean red;

        /** The registered mob the line stands for, or null. */
        final String mob;

        final ItemStack egg;

        Shown(boolean red, String mob, ItemStack egg) {
            this.red = red;
            this.mob = mob;
            this.egg = egg;
        }
    }

    /** Every registered mob, by name, in name order: what Add mob offers and what a line is checked against. */
    public static List<String> registered() {
        if (registered == null) registered = EntityNames.mobs();
        return registered;
    }

    /** What a line shows, worked out from its text as it stands. */
    public static Shown shown(String line) {
        if (line == null || line.trim()
            .isEmpty()) return new Shown(false, null, ItemStack.EMPTY);
        if (MobRows.unknown(line, registered())) return new Shown(true, null, ItemStack.EMPTY);
        List<String> known = registered();
        String mob = null;
        for (String name : ConfigLayout.mobNames(line)) {
            if (known.contains(name)) {
                mob = name;
                break;
            }
        }
        return new Shown(false, mob, mob == null ? ItemStack.EMPTY : ConfigIcons.resolve(ConfigLayout.Icons.MOB, line));
    }

    /** Draws a line's picture, sixteen by sixteen with its top left at x and y. */
    public static void draw(PoseStack pose, Shown shown, String line, int x, int y) {
        if (shown == null) return;
        ConfigLayout.Picture picture = ConfigLayout
            .picture(shown.mob != null, shown.mob != null && made(shown.mob) != null, !shown.egg.isEmpty());
        switch (picture) {
            case MOB:
                if (drawMob(shown.mob, x, y)) return;
                // A mob that would not be drawn is drawn as what comes after it, from this frame.
                if (!shown.egg.isEmpty()) WearIcons.drawIcon(line.trim(), shown.egg, x, y);
                else WornPathArt.picture(pose, GENERIC, x, y, 16);
                return;
            case EGG:
                WearIcons.drawIcon(line.trim(), shown.egg, x, y);
                return;
            case GENERIC:
                WornPathArt.picture(pose, GENERIC, x, y, 16);
                return;
            default:
                return;
        }
    }

    /** One mob of this kind, made in the world the client is in, or null where there is none or it cannot be made. */
    private static LivingEntity made(String name) {
        Minecraft client = Minecraft.getInstance();
        Level level = client == null ? null : client.level;
        if (level != madeIn) {
            MADE.clear();
            madeIn = level;
        }
        if (level == null || REFUSED.contains(name)) return null;
        if (MADE.containsKey(name)) return MADE.get(name);
        LivingEntity mob = null;
        long started = System.nanoTime();
        try {
            ResourceLocation at = ResourceLocation.tryParse(name);
            EntityType<?> type = at == null ? null
                : Registry.ENTITY_TYPE.getOptional(at)
                    .orElse(null);
            Entity entity = type == null ? null : type.create(level);
            if (entity instanceof LivingEntity) mob = (LivingEntity) entity;
            else REFUSED.add(name);
        } catch (Throwable hostile) {
            REFUSED.add(name);
        } finally {
            madeNanos = System.nanoTime() - started;
        }
        MADE.put(name, mob);
        return mob;
    }

    /** A line's picture as the settings screen's list rows draw it: the line worked out, then drawn (spec M3.4). */
    static void draw(PoseStack pose, String line, int x, int y) {
        draw(pose, shown(line), line, x, y);
    }

    /** The generic egg, for {@code *} in the mob list: every mob no line names (spec M3.2, ANY_MOB). */
    static void drawGenericEgg(PoseStack pose, int x, int y) {
        WornPathArt.picture(pose, GENERIC, x, y, 16);
    }

    /** The mob, standing in the sixteen-pixel square at x and y, scaled so its larger side fits it; false if it threw. */
    private static boolean drawMob(String name, int x, int y) {
        LivingEntity mob = MADE.get(name);
        if (mob == null) return false;
        float size = Math.max(mob.getBbWidth(), mob.getBbHeight());
        int scale = Math.max(1, (int) (14F / Math.max(0.1F, size)));
        try {
            turned(x + 8, y + 15, scale, MobRows.turn(System.currentTimeMillis()), mob);
            return true;
        } catch (Throwable hostile) {
            REFUSED.add(name);
            MADE.remove(name);
            return false;
        }
    }

    /**
     * The mob drawn as the inventory draws the player ({@code InventoryScreen.renderEntityInInventory}), but turned
     * about its upright axis to {@code angle} rather than towards the pointer (spec C6): body, head and look all at the
     * one angle, nought facing out of the screen as there, and set back afterwards, since the mob is kept and drawn
     * again. The inventory's own steps otherwise, read from this version's {@code InventoryScreen}.
     */
    private static void turned(int x, int y, int scale, float angle, LivingEntity mob) {
        RenderSystem.pushMatrix();
        RenderSystem.translatef(x, y, 1050.0F);
        RenderSystem.scalef(1.0F, 1.0F, -1.0F);
        PoseStack pose = new PoseStack();
        pose.translate(0.0D, 0.0D, 1000.0D);
        pose.scale(scale, scale, scale);
        Quaternion upright = Vector3f.ZP.rotationDegrees(180.0F);
        Quaternion look = Vector3f.XP.rotationDegrees(0.0F);
        upright.mul(look);
        pose.mulPose(upright);
        float body = mob.yBodyRot;
        float yaw = mob.yRot;
        float pitch = mob.xRot;
        float headBefore = mob.yHeadRotO;
        float head = mob.yHeadRot;
        mob.yBodyRot = 180.0F + angle;
        mob.yRot = 180.0F + angle;
        mob.xRot = 0.0F;
        mob.yHeadRot = mob.yRot;
        mob.yHeadRotO = mob.yRot;
        EntityRenderDispatcher dispatcher = Minecraft.getInstance()
            .getEntityRenderDispatcher();
        look.conj();
        dispatcher.overrideCameraOrientation(look);
        dispatcher.setRenderShadow(false);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance()
            .renderBuffers()
            .bufferSource();
        try {
            RenderSystem.runAsFancy(() -> dispatcher.render(mob, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, pose, buffers, 15728880));
            buffers.endBatch();
        } finally {
            dispatcher.setRenderShadow(true);
            mob.yBodyRot = body;
            mob.yRot = yaw;
            mob.xRot = pitch;
            mob.yHeadRotO = headBefore;
            mob.yHeadRot = head;
            RenderSystem.popMatrix();
        }
    }
}
