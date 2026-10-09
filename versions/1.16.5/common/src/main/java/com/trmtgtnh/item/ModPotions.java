package com.trmtgtnh.item;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.resources.ResourceLocation;

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
 *
 * <p>
 * <b>The icons are ours here, and in both older editions they are borrowed.</b> An effect there
 * picks a column and a row out of vanilla's shared potion sheet, and this class takes Jump Boost's
 * square and Slowness's - which was right, because a sheet of our own would have meant our own draw
 * call inside the inventory screen, and that means client-only code in a class the dedicated server
 * loads. 1.13 made an effect's icon a plain texture named after the effect, so there is no index to
 * borrow, no draw call to write, and no server-side worry left. The two are drawn by
 * {@code tools/mk_potion_icons.py} and named to match the registry names below, which is how the
 * game finds them.
 */
public final class ModPotions {

    /** The exemption. */
    public static MobEffect LIGHTNESS;

    /** Its opposite. */
    public static MobEffect HEAVY;

    private ModPotions() {}

    /**
     * Registered whatever the feature switches say, for the reason the enchantments give: the effect
     * belongs to the save the moment anybody drinks one, and a switch turned off later must leave it
     * resolvable rather than turning it into a hole. What the switches gate is whether the effect does
     * anything, which is asked live.
     */
    /** What a loader module supplies: its own way of putting an effect in the registry. */
    public interface Registrar {

        void effect(ResourceLocation name, MobEffect effect);
    }

    /**
     * Makes the two effects and hands them over. Safe to call twice, because Forge calls it twice -
     * the same arrangement ModBlocks is under, and for the same reason.
     *
     * <p>
     * The name is not set here and no longer can be. Both older editions give each effect a
     * translation key of its own; here the key is derived from the registry name, so these are
     * {@code effect.trmtgtnh.lightness} and {@code effect.trmtgtnh.heavyfoot} and the lang file has
     * to say so.
     */
    public static void register(Registrar into) {
        if (LIGHTNESS == null) LIGHTNESS = new Draught(false, 0xFBDC93);
        if (HEAVY == null) HEAVY = new Draught(true, 0x4A3B2E);
        into.effect(new ResourceLocation(Trmt.MODID, "lightness"), LIGHTNESS);
        into.effect(new ResourceLocation(Trmt.MODID, "heavyfoot"), HEAVY);
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

    private static boolean affected(Entity entity, MobEffect potion) {
        if (!(entity instanceof LivingEntity)) return false;
        return ((LivingEntity) entity).hasEffect(potion);
    }

    /** Says what was registered, for {@code /trmt status} and for anybody reading a log. */
    public static String describe() {
        return "lightness=" + nameOf(LIGHTNESS) + " heavyFoot=" + nameOf(HEAVY);
    }

    private static String nameOf(MobEffect potion) {
        if (potion == null) return "unregistered";
        // Asked of the registry rather than of the effect. An effect carried its own registry name
        // in both older editions; here the registry holds that relationship and the effect does not
        // know its own name, which is why this reads backwards from the way it used to.
        ResourceLocation name = net.minecraft.core.Registry.MOB_EFFECT.getKey(potion);
        return name == null ? "unregistered" : name.toString();
    }

    /**
     * Nothing but a name, a color and a borrowed icon; all the behaviour is asked for elsewhere.
     *
     * <p>
     * The icon is not chosen here and cannot be: the game finds it by the effect's registry name.
     * What this class still says is which way the effect cuts, which was a boolean and is now one of
     * the game's own categories - the same fact, named rather than flagged.
     */
    private static final class Draught extends MobEffect {

        private Draught(boolean bad, int color) {
            super(
                bad ? net.minecraft.world.effect.MobEffectCategory.HARMFUL
                    : net.minecraft.world.effect.MobEffectCategory.BENEFICIAL,
                color);
        }
    }
}
