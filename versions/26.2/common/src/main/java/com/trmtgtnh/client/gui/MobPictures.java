package com.trmtgtnh.client.gui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.MobNames;

/**
 * A mob row's picture, on both settings screens (0.9.228), in the order {@link MobRows#picture} decides: the mob
 * itself, drawn small - where there is a world to make one in, because a mob is made in a world and the title screen
 * has none; else its spawn egg; else this mod's own generic egg, {@code textures/gui/mob_egg.png}. A name no mob
 * answers to gets nothing. Names no Cloth type, so the mod's own screen loads it without Cloth; the Cloth screen's
 * rows ask it too.
 *
 * <p>
 * A mob is drawn as this game draws one in its inventory - its render state handed to the frame with
 * {@code GuiGraphicsExtractor.entity}, the step {@code InventoryScreen.extractEntityInInventoryFollowsMouse} ends in -
 * which hands the frame to whatever renderer its mod registered, so a mob that cannot be made or drawn is never tried
 * again that session and falls back to its egg. Each kind is made once per world and kept, never added to it: it is a
 * picture, not a creature in the world.
 *
 * <p>
 * <strong>Turned to the clock rather than to the pointer</strong> (spec C6). Until 0.9.228 26.x's rows called the
 * follow-the-pointer helper with the pointer held at the picture's middle, so every mob faced out. That helper turns a
 * mob only as far as a pointer beside it would (the arc tangent of the pointer's distance), never a whole turn, so the
 * steps it takes are taken here with the angle the clock gives: the same render state, light, scale and point it is
 * drawn about, and the 1.7.10 edition's turn.
 */
public final class MobPictures {

    /** The generic egg: the last fallback, so a known mob without an egg still looks unlike an unknown name. */
    static final Identifier GENERIC = Identifier.fromNamespaceAndPath(Trmt.MODID, "textures/gui/mob_egg.png");

    /** Full brightness, as the inventory lights the player it draws. */
    private static final int FULL_LIGHT = 15728880;

    /** The mobs made for their pictures, by kind, for the world they were made in. */
    private static final Map<EntityType<?>, LivingEntity> MADE = new HashMap<EntityType<?>, LivingEntity>();

    /** The IDs given the mobs made for pictures: counting down from far below any the world hands out. */
    private static final java.util.concurrent.atomic.AtomicInteger PICTURE_IDS =
        new java.util.concurrent.atomic.AtomicInteger(-1_000_000);

    /** Kinds whose mob could not be made or drawn, never tried again. */
    private static final Set<EntityType<?>> BROKEN = new HashSet<EntityType<?>>();

    private static Level madeIn;

    private static List<String> registered;

    private static int registeredFrom = -1;

    /** How long the last mob made took to make, for the settingsperf part's slowest mob (0.9.228). */
    static long madeNanos;

    private MobPictures() {}

    /** Every mob name the game answers to, read again only when the registry grows. */
    static List<String> registered() {
        int size = BuiltInRegistries.ENTITY_TYPE.size();
        if (registered == null || size != registeredFrom) {
            registered = MobNames.registered();
            registeredFrom = size;
        }
        return registered;
    }

    /** The first registered mob a line names, or null where none answers: Skeleton is the skeleton. */
    static EntityType<?> typeOf(String line) {
        for (String name : ConfigLayout.mobNames(line)) {
            Identifier at = Identifier.tryParse(name);
            if (at == null) continue;
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(at)
                .orElse(null);
            if (type != null) return type;
        }
        return null;
    }

    /** Draws the picture for one line of the mobs list, 16 by 16 with its top left at x, y. */
    public static void draw(GuiGraphicsExtractor graphics, String line, int x, int y) {
        EntityType<?> type = typeOf(line);
        LivingEntity mob = type == null ? null : mobFor(type);
        ItemStack egg = mob == null ? ConfigIcons.resolve(ConfigLayout.Icons.MOB, line) : ItemStack.EMPTY;
        switch (MobRows.picture(line, registered(), mob != null, !egg.isEmpty())) {
            case MOB:
                if (drawMob(graphics, type, mob, x, y)) return;
                // Refused as it was drawn: its egg, or the generic one, from here on.
                ItemStack fallback = ConfigIcons.resolve(ConfigLayout.Icons.MOB, line);
                if (!fallback.isEmpty()) WearIcons.drawIcon(graphics, "egg:" + line, fallback, x, y);
                else drawGeneric(graphics, x, y);
                return;
            case EGG:
                WearIcons.drawIcon(graphics, "egg:" + line, egg, x, y);
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
            // Its spawn checks set aside: a picture is never added to the world, and from 26.2 a hostile mob in a
            // peaceful world was refused, so its row drew the egg (0.9.228).
            Entity entity = type.create(level, new net.minecraft.world.entity.EntitySpawnRequest(EntitySpawnReason.LOAD, true));
            // An ID of its own, far below any the world hands out: from 26.2 drawing an entity asks its ID, and a mob
            // made for a picture had none, so every row fell back to its egg (0.9.228, found by the log).
            if (entity != null) entity.setId(PICTURE_IDS.decrementAndGet());
            if (entity instanceof LivingEntity) made = (LivingEntity) entity;
        } catch (Throwable unmade) {
            // Said once a mob (0.9.228): on 26.2 and 26.3 the list drew eggs where 26.1.2 drew the mobs, and the reason
            // went nowhere.
            Trmt.LOG.warn("The mobs list could not make a {} to draw, so it draws its egg: {}", type, unmade.toString());
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
     * The mob, fitted into 16 by 16, turning slowly to the clock's angle (spec C6); a mob whose drawing throws is not
     * drawn again.
     */
    private static boolean drawMob(GuiGraphicsExtractor graphics, EntityType<?> type, LivingEntity mob, int x, int y) {
        try {
            float size = Math.max(mob.getBbHeight(), mob.getBbWidth());
            float scale = Math.max(1F, Math.min(16F, 15F / Math.max(0.1F, size)));
            turned(graphics, x + 8, y + 8, scale, MobRows.turn(System.currentTimeMillis()), mob);
            return true;
        } catch (Throwable hostileRenderer) {
            Trmt.LOG.warn("The mobs list could not draw a {}, so it draws its egg: {}", type, hostileRenderer.toString());
            BROKEN.add(type);
            MADE.remove(type);
            return false;
        }
    }

    /**
     * The mob drawn as the inventory draws the player, but turned about its upright axis to {@code angle} rather than
     * towards the pointer (spec C6): body, head and look all at the one angle, nought facing out of the screen as there,
     * and set back afterwards, since the mob is kept and drawn again. The 1.7.10 edition's turn; the inventory's own
     * steps otherwise - the mob's render state made by its renderer at full light with no shadow or outline, handed to
     * the frame about the mob's middle, upright turned to face out - which at 26.x end in
     * {@code GuiGraphicsExtractor.entity} over the picture's square.
     */
    private static void turned(GuiGraphicsExtractor graphics, int x, int y, float scale, float angle, LivingEntity mob) {
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
            EntityRenderState state = Minecraft.getInstance()
                .getEntityRenderDispatcher()
                .getRenderer(mob)
                .createRenderState(mob, 1.0F);
            state.lightCoords = FULL_LIGHT;
            state.shadowPieces.clear();
            state.outlineColor = EntityRenderState.NO_OUTLINE;
            graphics.entity(state, scale, new Vector3f(0F, mob.getBbHeight() / 2F, 0F), upright, look, x - 8, y - 8,
                x + 8, y + 8);
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
    static void drawGenericEgg(GuiGraphicsExtractor graphics, int x, int y) {
        drawGeneric(graphics, x, y);
    }

    /** The generic egg, at full size. */
    private static void drawGeneric(GuiGraphicsExtractor graphics, int x, int y) {
        WornPathArt.picture(graphics, GENERIC, x, y, 16);
    }
}
