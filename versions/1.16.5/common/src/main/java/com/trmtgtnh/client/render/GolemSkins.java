package com.trmtgtnh.client.render;

import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceLocation;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.entity.GolemUpgrade;

/**
 * Which skin a Golem of Ways wears, and which overlay glows on it.
 *
 * <p>
 * One texture per upgrade, so fitting one changes what the thing looks like the moment it goes in -
 * and the model puts a mantle across its shoulders at the same time. Held in arrays because this is
 * asked once per golem per frame, twice over: the skin for the main pass and the glow for the layer
 * drawn after it.
 *
 * <p>
 * Its own class rather than two arrays inside the renderer, which is where the other edition keeps
 * them. The renderer there answers both questions itself because both passes are its own methods;
 * here the glow is a layer renderer and a layer is a separate object, so the two would otherwise
 * either hold a copy each or reach into one another.
 */
public final class GolemSkins {

    /**
     * How many steps the All Ways skin turns through, and how long it holds each.
     *
     * <p>
     * The count is the generator's, and the two have to agree - it writes exactly this many numbered
     * frames and this asks for them by number. Three ticks a step puts a full turn at a second and a
     * half, which reads as a color moving; at one tick it reads as a color flickering, which is a
     * different and much worse thing to have on a golem.
     */
    private static final int OMNI_FRAMES = 10;

    private static final int OMNI_TICKS = 3;

    private static final ResourceLocation[] SKINS = new ResourceLocation[GolemUpgrade.values().length];

    /** The additive overlay for each skin: black but for its eyes, its waystone and its sigil. */
    private static final ResourceLocation[] GLOWS = new ResourceLocation[GolemUpgrade.values().length];

    private static final ResourceLocation[] OMNI_SKINS = new ResourceLocation[OMNI_FRAMES];

    private static final ResourceLocation[] OMNI_GLOWS = new ResourceLocation[OMNI_FRAMES];

    /**
     * The loose version's frames, which are the bound one's with the missing-texture chequer
     * scattered over them.
     *
     * <p>
     * The same count and the same clock as the bound one, so the two turn through the wheel together
     * and the only difference between them on screen is the thing that is actually different about
     * them.
     */
    private static final ResourceLocation[] LOOSE_SKINS = new ResourceLocation[OMNI_FRAMES];

    private static final ResourceLocation[] LOOSE_GLOWS = new ResourceLocation[OMNI_FRAMES];

    static {
        for (GolemUpgrade upgrade : GolemUpgrade.values()) {
            SKINS[upgrade.ordinal()] = skin("golem_" + upgrade.key);
            GLOWS[upgrade.ordinal()] = skin("golem_glow_" + upgrade.key);
        }
        for (int frame = 0; frame < OMNI_FRAMES; frame++) {
            OMNI_SKINS[frame] = skin("golem_" + GolemUpgrade.OMNI.key + "_" + frame);
            OMNI_GLOWS[frame] = skin("golem_glow_" + GolemUpgrade.OMNI.key + "_" + frame);
            LOOSE_SKINS[frame] = skin("golem_" + GolemUpgrade.UNSTABLE.key + "_" + frame);
            LOOSE_GLOWS[frame] = skin("golem_glow_" + GolemUpgrade.UNSTABLE.key + "_" + frame);
        }
    }

    private GolemSkins() {}

    private static ResourceLocation skin(String name) {
        return new ResourceLocation(Trmt.MODID, "textures/entity/" + name + ".png");
    }

    /**
     * Which step of the turn this frame is, off the world clock rather than the golem's own age.
     *
     * <p>
     * So that two All Ways golems standing beside each other turn together. Off their own
     * {@code tickCount} they would drift by however far apart they were built, and a pair of them
     * out of phase reads as two different upgrades.
     */
    private static int frame(Entity entity) {
        if (entity == null || entity.level == null) return 0;
        return (int) ((entity.level.getGameTime() / OMNI_TICKS) % OMNI_FRAMES);
    }

    public static ResourceLocation skinFor(Entity entity, GolemUpgrade fitted) {
        if (fitted.isOmni()) return OMNI_SKINS[frame(entity)];
        if (fitted == GolemUpgrade.UNSTABLE) return LOOSE_SKINS[frame(entity)];
        return SKINS[fitted.ordinal()];
    }

    public static ResourceLocation glowFor(Entity entity, GolemUpgrade fitted) {
        if (fitted.isOmni()) return OMNI_GLOWS[frame(entity)];
        if (fitted == GolemUpgrade.UNSTABLE) return LOOSE_GLOWS[frame(entity)];
        return GLOWS[fitted.ordinal()];
    }
}
