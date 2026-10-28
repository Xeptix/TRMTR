package com.trmtgtnh.client.gui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.util.OldNames;

/**
 * The picture beside a mob list row, on both settings screens (0.9.228; Xep: "We should always try to show the mob
 * itself, and falllback to spawn egg where possible, and perhaps we make a generic spawn egg icon or something as the
 * final fallback"): the mob itself, drawn small - which needs a world to make one in, so not at the title screen - else
 * its spawn egg, else this mod's own generic egg; a name no mob answers to gets none ({@link MobRows#pictureFor}).
 *
 * <p>
 * A name is matched as the list matches it, without regard to case: a registry name, what the magic tamper writes, or
 * a short name, what the list always held, or a 1.7.10 name this version split ({@code EntityHorse}), drawn as the
 * first kind it became. Every picture is drawn inside {@link BlockIconArrayEntry#guarded}, so a mob whose renderer
 * throws is drawn once, marked broken and shown by its egg from then on.
 */
final class MobPicture {

    /** This mod's own generic spawn egg, 16 by 16. */
    static final ResourceLocation GENERIC = new ResourceLocation(Trmt.MODID, "textures/gui/mob_egg.png");

    /** What one name resolved to. */
    static final class Resolved {

        final EntityEntry entry;

        final ItemStack egg;

        Resolved(EntityEntry entry, ItemStack egg) {
            this.entry = entry;
            this.egg = egg;
        }

        boolean known() {
            return entry != null;
        }
    }

    private static final Resolved NOTHING = new Resolved(null, null);

    private static final Map<String, Resolved> RESOLVED = new HashMap<String, Resolved>();

    /** The mobs made to be drawn, by name, and the world they were made in: made again for another world. */
    private static final Map<String, EntityLivingBase> MADE = new HashMap<String, EntityLivingBase>();

    private static World madeIn;

    /** The names whose mob could not be made or drawn, which are drawn by their egg from then on. */
    private static final Set<String> BROKEN = new HashSet<String>();

    /** How long the last mob made took to make, for the settingsperf part's slowest mob (0.9.228). */
    static long madeNanos;

    private MobPicture() {}

    /** A row's name resolved to the mob it names, or a resolution that knows none. Never null. */
    static Resolved resolve(String name) {
        String key = name == null ? ""
            : name.trim()
                .toLowerCase(Locale.ROOT);
        if (key.isEmpty() || MobRows.EVERY_MOB.equals(key)) return NOTHING;
        Resolved held = RESOLVED.get(key);
        if (held != null) return held;
        // Typing a name resolves one dead name per keystroke; nothing here is costly to work out again.
        if (RESOLVED.size() > 256) RESOLVED.clear();
        held = build(key);
        RESOLVED.put(key, held);
        return held;
    }

    private static Resolved build(String key) {
        try {
            EntityEntry found = find(key);
            if (found == null) {
                List<String> became = OldNames.mobs(key);
                if (!became.isEmpty()) found = find(
                    became.get(0)
                        .toLowerCase(Locale.ROOT));
            }
            if (found == null) return NOTHING;
            ResourceLocation id = found.getRegistryName();
            ItemStack egg = null;
            if (id != null && (EntityList.ENTITY_EGGS.containsKey(id) || found.getEgg() != null)) {
                // The mob is in the egg's data at this version, rather than in its damage value.
                egg = new ItemStack(Items.SPAWN_EGG);
                ItemMonsterPlacer.applyEntityIdToItemStack(egg, id);
            }
            return new Resolved(found, egg);
        } catch (Throwable awkwardEntity) {
            return NOTHING;
        }
    }

    private static EntityEntry find(String key) {
        for (EntityEntry known : ForgeRegistries.ENTITIES) {
            if (known == null) continue;
            ResourceLocation id = known.getRegistryName();
            if (id != null && id.toString()
                .equalsIgnoreCase(key)) return known;
            if (known.getName() != null && known.getName()
                .equalsIgnoreCase(key)) return known;
        }
        return null;
    }

    /** Which picture this name draws now: the mob where one can be made here, else its egg, else the generic egg. */
    static MobRows.Picture pictureOf(String name, Resolved resolved) {
        boolean drawable = resolved.known() && Minecraft.getMinecraft().world != null
            && EntityLivingBase.class.isAssignableFrom(resolved.entry.getEntityClass())
            && !BROKEN.contains(brokenKey(name));
        return MobRows.pictureFor(resolved.known(), drawable, resolved.egg != null);
    }

    /** Draws a row's picture at x, y, sixteen pixels square; nothing for a name no mob answers to. */
    static void draw(final String name, final Resolved resolved, final int x, final int y) {
        MobRows.Picture picture = pictureOf(name, resolved);
        if (picture == MobRows.Picture.MOB) {
            final EntityLivingBase mob = made(name, resolved);
            if (mob == null) {
                BROKEN.add(brokenKey(name));
                picture = pictureOf(name, resolved);
            } else {
                BlockIconArrayEntry.guarded(brokenKey(name), new Runnable() {

                    @Override
                    public void run() {
                        float tall = Math.max(0.5F, Math.max(mob.height, mob.width));
                        int scale = Math.max(1, (int) (14F / tall));
                        turned(x + 8, y + 15, scale, MobRows.turn(System.currentTimeMillis()), mob);
                    }
                });
                if (BlockIconArrayEntry.broken(brokenKey(name))) BROKEN.add(brokenKey(name));
                GlStateManager.color(1F, 1F, 1F, 1F);
                return;
            }
        }
        if (picture == MobRows.Picture.EGG) {
            BlockIconArrayEntry.drawIcon("egg:" + name.trim(), resolved.egg, x, y);
        } else if (picture == MobRows.Picture.GENERIC) {
            BlockIconArrayEntry.guarded("generic-egg", new Runnable() {

                @Override
                public void run() {
                    GlStateManager.disableLighting();
                    GlStateManager.enableBlend();
                    GlStateManager.color(1F, 1F, 1F, 1F);
                    Minecraft.getMinecraft()
                        .getTextureManager()
                        .bindTexture(GENERIC);
                    Gui.drawModalRectWithCustomSizedTexture(x, y, 0F, 0F, 16, 16, 16F, 16F);
                }
            });
        }
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    /**
     * The mob drawn as the inventory draws the player ({@code GuiInventory.drawEntityOnScreen}), but turned about its
     * upright axis to {@code angle} rather than towards the pointer (spec C6): body, head and look all at the one
     * angle, set back afterwards, since the mob is kept and drawn again.
     */
    private static void turned(int x, int y, int scale, float angle, EntityLivingBase mob) {
        GlStateManager.enableColorMaterial();
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y, 50F);
        GlStateManager.scale((float) -scale, (float) scale, (float) scale);
        GlStateManager.rotate(180F, 0F, 0F, 1F);
        float body = mob.renderYawOffset;
        float bodyBefore = mob.prevRenderYawOffset;
        float yaw = mob.rotationYaw;
        float pitch = mob.rotationPitch;
        float headBefore = mob.prevRotationYawHead;
        float head = mob.rotationYawHead;
        GlStateManager.rotate(135F, 0F, 1F, 0F);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.rotate(-135F, 0F, 1F, 0F);
        mob.renderYawOffset = angle;
        mob.prevRenderYawOffset = angle;
        mob.rotationYaw = angle;
        mob.rotationPitch = 0F;
        mob.rotationYawHead = angle;
        mob.prevRotationYawHead = angle;
        RenderManager renders = Minecraft.getMinecraft()
            .getRenderManager();
        renders.setPlayerViewY(180F);
        renders.setRenderShadow(false);
        try {
            renders.renderEntity(mob, 0D, 0D, 0D, 0F, 1F, false);
        } finally {
            renders.setRenderShadow(true);
            mob.renderYawOffset = body;
            mob.prevRenderYawOffset = bodyBefore;
            mob.rotationYaw = yaw;
            mob.rotationPitch = pitch;
            mob.prevRotationYawHead = headBefore;
            mob.rotationYawHead = head;
            GlStateManager.popMatrix();
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        }
    }

    /** The generic egg, for {@code *} in the mob list: every mob no line names (spec M3.2, ANY_MOB). */
    static void drawGenericEgg(final int x, final int y) {
        BlockIconArrayEntry.guarded("generic-egg", new Runnable() {

            @Override
            public void run() {
                GlStateManager.disableLighting();
                GlStateManager.enableBlend();
                GlStateManager.color(1F, 1F, 1F, 1F);
                Minecraft.getMinecraft()
                    .getTextureManager()
                    .bindTexture(GENERIC);
                Gui.drawModalRectWithCustomSizedTexture(x, y, 0F, 0F, 16, 16, 16F, 16F);
            }
        });
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    /**
     * The mobs the mob list's picker offers (spec M4): each living mob the registry holds by the short name a line
     * names it by, as the 1.7.10 edition's picker writes it - not this mod's own Golem of Ways, which never wears the
     * ground loose whatever the list says - read again only when the registry grows.
     */
    static List<String> offered() {
        int size = net.minecraftforge.fml.common.registry.ForgeRegistries.ENTITIES.getKeys()
            .size();
        if (offered != null && size == offeredFrom) return offered;
        List<String> out = new java.util.ArrayList<String>();
        try {
            for (EntityEntry entry : ForgeRegistries.ENTITIES) {
                if (entry == null || entry.getName() == null) continue;
                Class<?> type = entry.getEntityClass();
                if (type == null || !EntityLiving.class.isAssignableFrom(type)
                    || com.trmtgtnh.entity.EntityGolemOfWays.class.isAssignableFrom(type)) continue;
                if (!out.contains(entry.getName())) out.add(entry.getName());
            }
        } catch (Throwable noRegistry) {
            // Nothing to offer, which the empty picker says.
        }
        offered = out;
        offeredFrom = size;
        return out;
    }

    private static List<String> offered;

    private static int offeredFrom = -1;

    /** Every name a line can name a mob of this game by, as the server reads the list at its start. */
    static java.util.Set<String> names() {
        int size = ForgeRegistries.ENTITIES.getKeys()
            .size();
        if (names == null || size != namesFrom) {
            try {
                names = com.trmtgtnh.server.ServerEvents.registeredMobNames();
            } catch (Throwable noRegistry) {
                names = java.util.Collections.emptySet();
            }
            namesFrom = size;
        }
        return names;
    }

    private static java.util.Set<String> names;

    private static int namesFrom = -1;

    /** A mob's name as the game translates it, or the name itself. */
    static String translated(String name) {
        try {
            Resolved resolved = resolve(name);
            if (resolved.known() && resolved.entry.getName() != null) {
                String key = "entity." + resolved.entry.getName() + ".name";
                if (net.minecraft.client.resources.I18n.hasKey(key))
                    return net.minecraft.client.resources.I18n.format(key);
            }
        } catch (Throwable hostile) {
            return name;
        }
        return name;
    }

    private static String brokenKey(String name) {
        return "mob:" + (name == null ? ""
            : name.trim()
                .toLowerCase(Locale.ROOT));
    }

    /** The mob a name draws, made once in the world the screen is open over; null where it cannot be made. */
    private static EntityLivingBase made(String name, Resolved resolved) {
        World world = Minecraft.getMinecraft().world;
        if (world == null) return null;
        if (world != madeIn) {
            MADE.clear();
            madeIn = world;
        }
        String key = brokenKey(name);
        if (MADE.containsKey(key)) return MADE.get(key);
        EntityLivingBase made = null;
        long started = System.nanoTime();
        try {
            Entity entity = resolved.entry.newInstance(world);
            if (entity instanceof EntityLivingBase) made = (EntityLivingBase) entity;
            // Drawn standing still, as the game draws a mob in its own screens: no AI to turn its head between frames.
            if (made instanceof EntityLiving) ((EntityLiving) made).setNoAI(true);
        } catch (Throwable cannotBeMade) {
            made = null;
        } finally {
            madeNanos = System.nanoTime() - started;
        }
        if (MADE.size() > 64) MADE.clear();
        MADE.put(key, made);
        return made;
    }
}
