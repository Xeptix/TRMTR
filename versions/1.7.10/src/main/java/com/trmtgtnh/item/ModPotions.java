package com.trmtgtnh.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;

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
 * Registered from the common proxy, like the three enchantments beside it and for the same reason:
 * an effect id is written into a save and travels over the wire as a raw byte, so both sides have to
 * settle on the same number or each will show the other's effect as something else entirely.
 */
public final class ModPotions {

    /** The exemption. Null when no id could be claimed, which is a state every caller must expect. */
    public static Potion LIGHTNESS;

    /** Its opposite. Null on the same terms. */
    public static Potion HEAVY;

    private ModPotions() {}

    /**
     * Claims an id for each effect. Called from {@code CommonProxy.init}.
     *
     * <p>
     * Registered whatever the feature switches say, for the reason the enchantments give one
     * paragraph over: the id belongs to the save the moment anybody drinks one, and a switch turned
     * off later must leave that effect resolvable rather than turning it into a hole. What the
     * switches gate is whether the effect does anything, which is asked live.
     */
    public static void register(int lightnessId, int heavyId) {
        if (LIGHTNESS == null) {
            // Jump Boost's icon and Slowness's, borrowed rather than drawn. A custom sheet would
            // mean our own draw call inside the inventory screen, which means client-only code in a
            // class the dedicated server loads - a crash for a picture. These two read correctly
            // enough: one is a figure springing up and the other a figure weighed down.
            LIGHTNESS = claim("lightness", "lightnessPotionId", lightnessId, false, 0xFBDC93, 2, 1);
        }
        if (HEAVY == null) {
            HEAVY = claim("heavyfoot", "heavyFootPotionId", heavyId, true, 0x4A3B2E, 1, 0);
        }
    }

    /**
     * @param key       the effect's own name, which is also the tail of its lang key and may not be
     *                  changed to suit a message
     * @param configKey the setting that pins it, which is spelled differently and has to be quoted
     *                  exactly - a log line naming a key that does not exist is worse than none
     */
    private static Potion claim(String key, String configKey, int configured, boolean bad, int color, int iconColumn,
        int iconRow) {
        int width = Potion.potionTypes.length;
        int id = configured >= 0 ? configured : firstFreeId(width);
        if (id < 0) {
            Trmt.LOG.warn(
                "No free potion id for {}; that draught will be an ornament on this pack. The array is {} wide and every slot in it is taken. Free one up, or pin potions.{} to a number you know is spare.",
                new Object[] { key, Integer.valueOf(width), configKey });
            return null;
        }
        if (id <= 0 || id >= width) {
            Trmt.LOG.warn(
                "Potion id {} for {} is outside this pack's array of {}; that draught will do nothing. Pin a free id in config.",
                new Object[] { Integer.valueOf(id), key, Integer.valueOf(width) });
            return null;
        }
        if (Potion.potionTypes[id] != null) {
            Trmt.LOG.warn(
                "Potion id {} is already {}; the {} draught will do nothing. Pin a free id in config.",
                new Object[] { Integer.valueOf(id), Potion.potionTypes[id].getName(), key });
            return null;
        }
        Potion potion = new Draught(id, bad, color).setPotionName("potion." + Trmt.MODID + "." + key);
        ((Draught) potion).icon(iconColumn, iconRow);
        Trmt.LOG.info(
            "Registered the {} effect at potion id {}, in an array {} wide",
            new Object[] { key, Integer.valueOf(id), Integer.valueOf(width) });
        return potion;
    }

    /**
     * The first slot nobody has taken, counting up from twenty-four.
     *
     * <p>
     * Up rather than down, and from twenty-four rather than from nought, because index nought is the
     * sentinel meaning no effect at all and one to twenty-three are the game's own. On plain Forge
     * the array is thirty-two long and those eight are every spare slot there is; a pack that has
     * widened it has a great many more.
     *
     * <p>
     * The first pass stops short of a hundred and twenty-eight even where the array is longer, and
     * the reason is the wire rather than the array. An effect id is written as a single byte both in
     * the save and in the packet that tells a client what it is under, so a number at or above a
     * hundred and twenty-eight arrives negative unless something on that pack masks it back. Only
     * when nothing below is free is the upper half tried at all, and then it says so.
     */
    private static int firstFreeId(int width) {
        int safe = Math.min(width, 128);
        for (int id = 24; id < safe; id++) {
            if (Potion.potionTypes[id] == null) return id;
        }
        for (int id = 128; id < width; id++) {
            if (Potion.potionTypes[id] == null) {
                Trmt.LOG.warn(
                    "Every potion id below 128 is taken, so id {} is being used. That number only survives being written to a save and sent to a client on a pack that widens the byte it travels in - which this one appears to, since the array is {} wide. On a client without that, the effect will arrive as something else.",
                    Integer.valueOf(id),
                    Integer.valueOf(width));
                return id;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // What the engine asks
    // ------------------------------------------------------------------

    /**
     * Whether this mover leaves the ground alone entirely.
     *
     * <p>
     * False when the heavy draught is also on somebody, because the two are opposites and opposites
     * that meet come to nothing. Without this the light one simply won: the engine asks this first
     * and returns on a yes, so a player who drank the heavy draught while still light watched it do
     * nothing at all and had no way to tell why. Neither effect is removed - both go on counting
     * down, and whichever outlasts the other takes hold for the rest of its time.
     *
     * <p>
     * Asked of the pair rather than of the rider, so a light rider on a heavy mount is ordinary
     * ground too. The two are already read the same way round - either party being light makes the
     * crossing light, either being heavy makes it heavy - and one of them counting for both while
     * the other counted for one would be a rule nobody could hold in their head.
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

    /** Says what was claimed, for {@code /trmt status} and for anybody reading a log. */
    public static String describe() {
        return "lightness=" + idOf(
            LIGHTNESS) + " heavyFoot=" + idOf(HEAVY) + " arrayWidth=" + Potion.potionTypes.length;
    }

    private static String idOf(Potion potion) {
        return potion == null ? "unclaimed" : Integer.toString(potion.getId());
    }

    // ------------------------------------------------------------------
    // Surviving a disagreement about which number an effect is
    // ------------------------------------------------------------------

    /** Ids already complained about, so a warning is said once rather than twenty times a second. */
    private static final java.util.Set<Integer> WARNED = new java.util.HashSet<Integer>();

    /**
     * Throws away an effect this client has no potion for, before the game dereferences it.
     *
     * <p>
     * The reason this exists is worth setting down in full, because the failure it prevents is far
     * worse than it sounds and the comment beside the id settings used to describe it wrongly.
     *
     * <p>
     * An effect id is claimed independently on each machine, from whatever that machine's own
     * config says and whatever its own mod set left free. When a server applies an effect it sends
     * the raw number, and the receiving client looks it up in its own table. If the two disagree and
     * the client has a <em>different</em> potion at that index it shows the wrong effect, which is
     * what the settings used to promise. If the client has <em>nothing</em> there - which is the
     * likelier half, because on a plain pack the slots this mod hunts through are all empty - then
     * {@code PotionEffect.onUpdate} reaches {@code Potion.potionTypes[id].isReady(..)} with no null
     * check and throws inside the client's own world tick. That is not a cosmetic fault: it is a
     * crash on the tick the drink lands, and again on rejoining, until the effect times out on a
     * server the player can no longer reach. It also takes down anybody who merely walks into range,
     * because the entity tracker replays active effects to every new watcher.
     *
     * <p>
     * The guard is general rather than aimed at this mod's two effects, and has to be: the whole
     * problem is that a client cannot tell which number the server meant. Any active effect with no
     * potion behind it is already unusable and already certain to throw, so dropping it costs
     * nothing that was working. What is lost is the icon and the timer on a client whose number
     * disagrees - the effect itself is applied on the server and goes on working perfectly.
     *
     * <p>
     * Placed on {@code LivingUpdateEvent}, which Forge fires at the top of
     * {@code EntityLivingBase.onUpdate} and therefore strictly before the potion pass that would
     * throw. Client side only, since the server is the machine that chose the number.
     */
    public static void guardAgainstUnknownEffects(net.minecraft.entity.EntityLivingBase entity) {
        if (entity == null || entity.worldObj == null || !entity.worldObj.isRemote) return;
        java.util.Collection<?> active = entity.getActivePotionEffects();
        if (active == null || active.isEmpty()) return;

        java.util.List<Integer> unusable = null;
        for (Object held : active) {
            if (!(held instanceof net.minecraft.potion.PotionEffect)) continue;
            int id = ((net.minecraft.potion.PotionEffect) held).getPotionID();
            if (id > 0 && id < Potion.potionTypes.length && Potion.potionTypes[id] != null) continue;
            if (unusable == null) unusable = new java.util.ArrayList<Integer>(1);
            unusable.add(Integer.valueOf(id));
        }
        if (unusable == null) return;

        for (int i = 0; i < unusable.size(); i++) {
            Integer id = unusable.get(i);
            entity.removePotionEffectClient(id.intValue());
            if (WARNED.add(id)) {
                Trmt.LOG.warn(
                    "Dropped an effect at id {} that this client has no potion for. Something applied it - very likely a server whose own numbering differs from this client's - and leaving it in place would have thrown inside the world tick rather than merely looking wrong. This client currently holds {}. If those are this mod's draughts, pin potions.lightnessPotionId and potions.heavyFootPotionId to the same numbers on the server and on every client.",
                    id,
                    describe());
            }
        }
    }

    /** Nothing but a name, a color and a borrowed icon; all the behaviour is asked for elsewhere. */
    private static final class Draught extends Potion {

        private Draught(int id, boolean bad, int color) {
            super(id, bad, color);
        }

        private void icon(int column, int row) {
            setIconIndex(column, row);
        }
    }
}
