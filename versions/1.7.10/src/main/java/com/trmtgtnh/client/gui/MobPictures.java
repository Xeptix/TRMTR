package com.trmtgtnh.client.gui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.MobNames;

/**
 * A mob row's picture, on both settings screens (0.9.228), in the order {@link MobRows#picture} decides: the mob
 * itself, drawn small - where there is a world to make one in, because a mob is made in a world and the title screen
 * has none; else its spawn egg - a mob a mod registered without a global id has none, which is most of GT New
 * Horizons'; else this mod's own generic egg, {@code textures/gui/mob_egg.png}. A name no mob answers to gets nothing.
 *
 * <p>
 * Drawing a mob hands the frame to whatever renderer its mod registered, inside a settings screen, so it is held as
 * the block icons are held ({@link BlockIconArrayEntry}): the state it leaves is put back, and a mob that cannot be
 * made or drawn is never tried again that session and falls back to its egg. Each mob is made once per world and
 * kept, never spawned: it is a picture, not a creature in the world.
 */
final class MobPictures {

    /** The generic egg: the last fallback, so a known mob without an egg still looks unlike an unknown name. */
    static final ResourceLocation GENERIC = new ResourceLocation(Trmt.MODID, "textures/gui/mob_egg.png");

    /** The mobs made for their pictures, by lower-case name, for the world they were made in. */
    private static final Map<String, EntityLivingBase> MADE = new HashMap<String, EntityLivingBase>();

    /** Names whose mob could not be made or drawn, never tried again. */
    private static final Set<String> BROKEN = new HashSet<String>();

    private static World madeIn;

    private static List<String> registered;

    private static int registeredFrom = -1;

    /** How long the last mob made took to make, for the settingsperf part's slowest mob (0.9.228). */
    static long madeNanos;

    private MobPictures() {}

    /** Every mob name the game answers to, read again only when the registry grows. */
    static List<String> registered() {
        int size = EntityList.stringToClassMapping.size();
        if (registered == null || size != registeredFrom) {
            registered = MobNames.registered();
            registeredFrom = size;
        }
        return registered;
    }

    /** Draws the picture for one line of the mobs list, 16 by 16 with its top left at x, y. */
    static void draw(String line, int x, int y) {
        String name = MobRows.nameOf(line)
            .trim();
        EntityLivingBase mob = mobFor(name);
        ItemStack egg = mob == null ? MobIconArrayEntry.resolve(line) : null;
        switch (MobRows.picture(line, registered(), mob != null, egg != null)) {
            case MOB:
                drawMob(name, mob, x, y);
                return;
            case EGG:
                BlockIconArrayEntry.drawIcon("egg:" + name, egg, x, y);
                return;
            case GENERIC:
                drawGeneric(x, y);
                return;
            default:
        }
    }

    /** The mob of this name, made once in the world the client is in; null with no world, or for one that failed. */
    private static EntityLivingBase mobFor(String name) {
        if (name.isEmpty()) return null;
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc == null ? null : mc.theWorld;
        if (world == null) return null;
        if (world != madeIn) {
            MADE.clear();
            madeIn = world;
        }
        String key = name.toLowerCase(Locale.ROOT);
        if (BROKEN.contains(key)) return null;
        if (MADE.containsKey(key)) return MADE.get(key);
        EntityLivingBase made = null;
        long started = System.nanoTime();
        try {
            String registeredName = spelled(name);
            Entity entity = registeredName == null ? null : EntityList.createEntityByName(registeredName, world);
            if (entity instanceof EntityLivingBase) made = (EntityLivingBase) entity;
        } catch (Throwable unmade) {
            BROKEN.add(key);
            return null;
        } finally {
            madeNanos = System.nanoTime() - started;
        }
        if (MADE.size() > 256) MADE.clear();
        MADE.put(key, made);
        return made;
    }

    /** The registry's own spelling of a name, matched without regard to case as the list matches it. */
    private static String spelled(String name) {
        if (EntityList.stringToClassMapping.containsKey(name)) return name;
        for (String known : EntityList.stringToClassMapping.keySet()) {
            if (known != null && known.equalsIgnoreCase(name)) return known;
        }
        return null;
    }

    /** The mob, fitted into 16 by 16 standing on the bottom edge; a mob whose drawing throws is not drawn again. */
    private static void drawMob(String name, EntityLivingBase mob, int x, int y) {
        float size = Math.max(mob.height, mob.width);
        int scale = Math.max(1, Math.min(16, Math.round(15F / Math.max(0.1F, size))));
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_LIGHTING_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_TRANSFORM_BIT
                | GL11.GL_DEPTH_BUFFER_BIT);
        int depth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        try {
            GL11.glColor4f(1F, 1F, 1F, 1F);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            turned(x + 8, y + 15, scale, MobRows.turn(System.currentTimeMillis()), mob);
        } catch (Throwable hostileRenderer) {
            BROKEN.add(name.toLowerCase(Locale.ROOT));
            try {
                Tessellator.instance.draw();
            } catch (Throwable wasNotDrawing) {
                // Already idle, the ordinary case.
            }
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            while (GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH) > depth) {
                GL11.glPopMatrix();
            }
            RenderHelper.disableStandardItemLighting();
            GL11.glPopAttrib();
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }

    /**
     * The mob drawn as the inventory draws the player ({@code GuiInventory.func_147046_a}), but turned about its
     * upright axis to {@code angle} rather than towards the pointer (spec C6): body, head and look all at the one
     * angle, set back afterwards, since the mob is kept and drawn again.
     */
    private static void turned(int x, int y, int scale, float angle, EntityLivingBase mob) {
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 50F);
        GL11.glScalef(-scale, scale, scale);
        GL11.glRotatef(180F, 0F, 0F, 1F);
        float body = mob.renderYawOffset;
        float bodyBefore = mob.prevRenderYawOffset;
        float yaw = mob.rotationYaw;
        float pitch = mob.rotationPitch;
        float headBefore = mob.prevRotationYawHead;
        float head = mob.rotationYawHead;
        GL11.glRotatef(135F, 0F, 1F, 0F);
        RenderHelper.enableStandardItemLighting();
        GL11.glRotatef(-135F, 0F, 1F, 0F);
        mob.renderYawOffset = angle;
        mob.prevRenderYawOffset = angle;
        mob.rotationYaw = angle;
        mob.rotationPitch = 0F;
        mob.rotationYawHead = angle;
        mob.prevRotationYawHead = angle;
        GL11.glTranslatef(0F, mob.yOffset, 0F);
        RenderManager.instance.playerViewY = 180F;
        try {
            RenderManager.instance.renderEntityWithPosYaw(mob, 0D, 0D, 0D, 0F, 1F);
        } finally {
            mob.renderYawOffset = body;
            mob.prevRenderYawOffset = bodyBefore;
            mob.rotationYaw = yaw;
            mob.rotationPitch = pitch;
            mob.prevRotationYawHead = headBefore;
            mob.rotationYawHead = head;
            GL11.glPopMatrix();
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        }
    }

    /** The generic egg, for {@code *} in the mob list: every mob no line names (spec M3.2, ANY_MOB). */
    static void drawGenericEgg(int x, int y) {
        drawGeneric(x, y);
    }

    /** The generic egg, at full size. */
    private static void drawGeneric(int x, int y) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getTextureManager() == null) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1F, 1F, 1F, 1F);
            mc.getTextureManager()
                .bindTexture(GENERIC);
            Gui.func_146110_a(x, y, 0F, 0F, 16, 16, 16F, 16F);
        } finally {
            GL11.glPopAttrib();
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }
}
