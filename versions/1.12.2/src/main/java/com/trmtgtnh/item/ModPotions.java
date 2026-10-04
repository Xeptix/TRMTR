package com.trmtgtnh.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.trmtgtnh.Tags;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;

/**
 * The two effects a draught carries: one that stops a walker wearing ground, and its opposite.
 *
 * <p>
 * Lightness is milkucha's, and is faithful to it. In the original the tick hook returns before any
 * erosion is accumulated, so an affected walker contributes nothing to the block underfoot and
 * nothing to the three around it that a footstep normally bleeds onto - a total exemption rather
 * than a reduction, placed immediately beside the check for sneaking, which is the same class of
 * suppression arrived at by posture rather than by drinking. It covers a mount as well as its rider,
 * deliberately, so a splash thrown over a horse genuinely exempts the horse.
 *
 * <p>
 * Heavy-footedness is not in the original mod in any branch. It is the obvious counterpart and it is
 * built the obvious way: the same walker, the same block, several times the mark. Because the factor
 * multiplies the figure that is handed to the spread and to the crops underfoot as well as to the
 * block being stood on, a heavy walker widens a track rather than only deepening it. That is worth
 * saying aloud, because "more wear" does not literally say it.
 *
 * <p>
 * <b>Half of the other edition's version of this file is not here, and could not be.</b> There, an
 * effect is a slot in a fixed array: the class hunts for a free number from twenty-four up, stops
 * short of a hundred and twenty-eight because the id travels as a single byte, warns when a pack has
 * no room, and carries a guard that throws away an effect a client has no potion for - because a
 * server and a client each choose their own numbers, and a client that looks up a number it has
 * nothing at is not merely wrong but crashes inside its own world tick. None of that survives into
 * 1.12.2. An effect is a registry entry with a name, Forge settles the numbering between the two
 * machines when they connect, and the whole failure the guard existed for cannot arise. What is left
 * is the two effects and the four questions the engine asks about them.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class ModPotions {

    /** The exemption. */
    public static Potion LIGHTNESS;

    /** Its opposite. */
    public static Potion HEAVY;

    private ModPotions() {}

    /**
     * Registered whatever the feature switches say, for the reason the enchantments give: the effect
     * belongs to the save the moment anybody drinks one, and a switch turned off later must leave it
     * resolvable rather than turning it into a hole. What the switches gate is whether the effect does
     * anything, which is asked live.
     */
    @SubscribeEvent
    public static void register(RegistryEvent.Register<Potion> event) {
        LIGHTNESS = new Draught(false, 0xFBDC93, 2, 1).setPotionName("potion." + Trmt.MODID + ".lightness");
        LIGHTNESS.setRegistryName(new ResourceLocation(Trmt.MODID, "lightness"));
        HEAVY = new Draught(true, 0x4A3B2E, 1, 0).setPotionName("potion." + Trmt.MODID + ".heavyfoot");
        HEAVY.setRegistryName(new ResourceLocation(Trmt.MODID, "heavyfoot"));
        event.getRegistry()
            .register(LIGHTNESS);
        event.getRegistry()
            .register(HEAVY);
    }

    // ------------------------------------------------------------------
    // What the engine asks
    // ------------------------------------------------------------------

    /**
     * Whether this mover leaves the ground alone entirely.
     *
     * <p>
     * A rider and a mount are asked together, and the reader is either of them: a light rider on an
     * ordinary horse leaves no track, and so does an ordinary rider on a light horse. Heavy-footedness
     * is read the same way round, and cancels lightness rather than compounding with it - two draughts
     * pulling opposite ways is a walker treading ordinarily.
     */
    public static boolean treadsLightly(Entity walker, Entity vehicle) {
        return isLight(walker, vehicle) && !isHeavy(walker, vehicle);
    }

    /**
     * How much more ground this mover wears than an ordinary one. One when nobody is heavy.
     *
     * <p>
     * One as well when somebody is light, which is the other half of the cancelling above.
     *
     * <p>
     * Not compounded when a heavy rider sits on a heavy mount. Two doses is one heavy thing walking,
     * and squaring the factor there would be a quiet way for sixteen times the wear to arrive out of
     * a setting that says four.
     */
    public static float wearFactor(Entity walker, Entity vehicle) {
        if (!isHeavy(walker, vehicle) || isLight(walker, vehicle)) return 1f;
        float factor = TrmtConfig.heavyFootFactor;
        return factor <= 0f ? 1f : factor;
    }

    /**
     * Whether either party is carrying the light draught, and it is a draught that does anything.
     *
     * <p>
     * The switch is part of the question rather than a test around it. A draught somebody has
     * switched off must not cancel its opposite either - it would be a setting that turned one
     * feature off and quietly took the other with it.
     */
    private static boolean isLight(Entity walker, Entity vehicle) {
        if (LIGHTNESS == null || !TrmtConfig.potionsEnabled || !TrmtConfig.potionLightness) return false;
        return affected(walker, LIGHTNESS) || affected(vehicle, LIGHTNESS);
    }

    /** The same question of the heavy draught, and switched off the same way. */
    private static boolean isHeavy(Entity walker, Entity vehicle) {
        if (HEAVY == null || !TrmtConfig.potionsEnabled || !TrmtConfig.potionHeavyFoot) return false;
        return affected(walker, HEAVY) || affected(vehicle, HEAVY);
    }

    private static boolean affected(Entity entity, Potion potion) {
        if (!(entity instanceof EntityLivingBase)) return false;
        return ((EntityLivingBase) entity).isPotionActive(potion);
    }

    /** Says what was registered, for {@code /trmt status} and for anybody reading a log. */
    public static String describe() {
        return "lightness=" + nameOf(LIGHTNESS) + " heavyFoot=" + nameOf(HEAVY);
    }

    private static String nameOf(Potion potion) {
        return potion == null || potion.getRegistryName() == null ? "unregistered"
            : potion.getRegistryName()
                .toString();
    }

    /**
     * Nothing but a name, a colour and a borrowed icon; all the behaviour is asked for elsewhere.
     *
     * <p>
     * Jump Boost's icon and Slowness's, borrowed rather than drawn, exactly as the other edition
     * borrows them. A sheet of our own would mean our own draw call inside the inventory screen, which
     * means client-only code in a class the dedicated server loads - a crash for a picture. These two
     * read correctly enough: one is a figure springing up and the other a figure weighed down.
     */
    private static final class Draught extends Potion {

        private Draught(boolean bad, int colour, int iconColumn, int iconRow) {
            super(bad, colour);
            setIconIndex(iconColumn, iconRow);
        }
    }
}
