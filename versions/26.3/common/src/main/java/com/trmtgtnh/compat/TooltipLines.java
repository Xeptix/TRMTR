package com.trmtgtnh.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.ChatFormatting;

import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.entity.EntityGolemOfWays;
import com.trmtgtnh.entity.GolemCombat;
import com.trmtgtnh.entity.GolemUpgrade;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.erosion.RecordReadout;
import com.trmtgtnh.erosion.Reinforcement;
import com.trmtgtnh.erosion.SinkProfile;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The lines a block tooltip shows for a worn square, a reinforced or warded block, a path light and a Golem of
 * Ways - written once, for every tooltip mod this edition speaks to.
 *
 * <p>
 * Jade and The One Probe are told exactly the same thing, so a player sees the same facts whichever of them
 * their pack runs. They get those facts from different places, and that is the only difference between them:
 * Jade's provider runs on the client, so it reads the client's own overlay and the inspection reply the
 * crosshair asked the server for; The One Probe calls its providers on the server, so it reads the server's
 * record directly and asks nothing over the wire. Both hand the figures here, and every line is worded here, so
 * the two cannot drift apart a word at a time.
 *
 * <p>
 * Nothing in this class names either tooltip mod, nor anything of the client's half of the mod, because The One
 * Probe's half of it runs on a dedicated server. Words that a player's language decides are handed to
 * {@code localise}: Jade translates them on the spot, The One Probe sends a marker its own client translates.
 * WTHIT, Jade's counterpart on Fabric, reads these lines through the same {@code WailaCompat} and {@code WailaGolem}.
 */
public final class TooltipLines {

    /** In-game seconds in a Minecraft day, which is how healing durations are expressed. */
    private static final double SECONDS_PER_DAY = 1200.0D;

    private TooltipLines() {}

    /**
     * What the server's record says about one position, as far as a tooltip reads it - the figures of an
     * inspection reply. Null wherever no reply stands, which is a position with no record, or one the client has
     * not heard back about yet.
     */
    public static final class Reply {

        final int reinforce;
        final int ward;
        final int chainLength;
        final int chainIndex;
        final boolean tellsProgress;
        final float progress;
        final int untouchedSeconds;
        final int recoverySeconds;

        public Reply(int reinforce, int ward, int chainLength, int chainIndex, boolean tellsProgress, float progress,
            int untouchedSeconds, int recoverySeconds) {
            this.reinforce = reinforce;
            this.ward = ward;
            this.chainLength = chainLength;
            this.chainIndex = chainIndex;
            this.tellsProgress = tellsProgress;
            this.progress = progress;
            this.untouchedSeconds = untouchedSeconds;
            this.recoverySeconds = recoverySeconds;
        }

        /**
         * A reply read straight off the server's readout, the same one {@code PacketInspect} sends the client, with
         * progress worked out by the same two calls the client's {@code InspectionCache} makes of what arrives.
         */
        public static Reply of(RecordReadout readout) {
            if (readout == null) return null;
            return new Reply(
                readout.reinforce,
                readout.ward,
                readout.chainLength,
                readout.chainIndex,
                RecordReadout.tellsProgress(readout.threshold),
                RecordReadout.progress(readout.wear, readout.threshold),
                readout.untouchedSeconds,
                readout.recoverySeconds);
        }
    }

    /**
     * The lines for the block at one position.
     *
     * @param ghostHere true when a worn square is drawn here - a ghost stands in the client's world
     * @param state     the packed wear the client's overlay holds for the position, {@link ErosionState#NONE} when
     *                  none
     * @param glow      the path light the client holds for the position, level in the low four bits and color in
     *                  the next four
     * @param reply     the inspection reply for exactly this position, or null when none stands
     * @param localise  turns a language key into what the reader shows
     */
    public static List<String> block(boolean ghostHere, short state, int glow, Reply reply,
        Function<String, String> localise) {
        List<String> tip = new ArrayList<String>();

        // Reinforcement can sit on any block, worn or not, so this line is written before the wear body's own
        // "only a ghost" gate and over unworn blocks too. The level is the server's, read through the same
        // inspection reply the wear numbers use.
        if (TrmtConfig.reinforceEnabled && reply != null) {
            int level = reply.reinforce;
            if (level > 0) {
                int cap = Math.min(3, TrmtConfig.reinforceMaxLevel);
                // What the level buys against traffic, which is otherwise invisible: the wear bar simply fills
                // more slowly and nothing says why. Said only of ground that wears through gradations - a ghost,
                // or a block the server gives a run of steps - because a reinforced leaf or plant is not trampled
                // at all and brick never wears; see InspectionReach.wears.
                boolean wears = com.trmtgtnh.util.InspectionReach.wears(ghostHere, reply.chainLength > 0);
                tip.add(
                    ChatFormatting.AQUA + "Reinforced "
                        + ChatFormatting.WHITE
                        + level
                        + "/"
                        + cap
                        + (level >= cap ? ChatFormatting.DARK_GRAY + "  blast-proof" : "")
                        + (wears ? wearHold(level) : ""));
            }
        }

        // Spawn ward, on any block for the same reason, from the same inspection reply.
        if (TrmtConfig.wardEnabled && reply != null) {
            int ward = reply.ward;
            if ((ward & 0x1) != 0) {
                tip.add(ChatFormatting.AQUA + "Warded" + ChatFormatting.WHITE + "  no hostile spawns");
            }
            if ((ward & 0x2) != 0) {
                tip.add(ChatFormatting.AQUA + "Warded" + ChatFormatting.WHITE + "  no passive spawns");
            }
        }

        // The glow needs no inspection round trip: the client already knows it, because it is drawing it. Said
        // only over a ghost, because only a ghost glows. Ground healed back to pristine keeps its light for when
        // it wears again, so the client still holds a level for a block that is not lit, and "Lit" over it would
        // describe a light nobody can see.
        if (ghostHere && TrmtConfig.lightEnabled) {
            if ((glow & 0xF) != 0) {
                tip.add(
                    ChatFormatting.AQUA + "Lit "
                        + ChatFormatting.WHITE
                        + (glow & 0xF)
                        + "/15"
                        + ChatFormatting.DARK_GRAY
                        + "  "
                        + localise.apply("trmtgtnh.light.color." + ((glow >> 4) & 0xF)));
            }
        }

        // Every line below is drawn from wear, and only a ghost has wear to draw. Beyond this gate the provider
        // also meets leaves, plants and unworn ground, whose records are trample tallies, ground short of its
        // first gradation, or kept only for a reinforcement or a ward - and a tally's wear is the count that
        // breaks a leaf, not progress toward a gradation. So nothing below may move above it. A server from
        // 0.9.213 withholds those figures for such a record as well (RecordReadout); an older one sends them,
        // and there this gate is all that stands in the way.
        if (!ghostHere) return tip;

        // Straight off the client's own record. It holds the surface, how worn the face is and
        // how far the ground has dropped, and those are three separate things: a layer count on
        // its own restarts every time the ground sinks a pixel, so it cannot say how far along a
        // block really is.
        if (state == ErosionState.NONE) return tip;
        SurfaceFamily family = ErosionState.familyOf(state);
        int layer = ErosionState.layerOf(state);
        int sink = SinkProfile.shown(family, ErosionState.sinkOf(state));
        if (family == null || layer < 0) return tip;

        FamilySettings settings = TrmtConfig.family(family);
        int layers = settings == null ? layer + 1
            : Math.max(sink > 0 ? settings.layersPerDepth : settings.stages, layer + 1);

        StringBuilder worn = new StringBuilder();
        worn.append(ChatFormatting.GRAY)
            .append("Worn: ")
            .append(ChatFormatting.WHITE)
            .append(label(family))
            .append(' ')
            .append(layer + 1)
            .append('/')
            .append(layers);

        if (sink > 0) {
            worn.append(ChatFormatting.GRAY)
                .append(", sunk ")
                .append(sink)
                .append("/16");
        }
        tip.add(worn.toString());

        // Straight off the packed state, so it stands the moment the ghost is drawn rather than
        // waiting for the server's reply. A position with no overlay record cannot be pinned, so
        // there is nothing to miss by reading it here.
        if (ErosionState.frozenOf(state)) {
            tip.add(ChatFormatting.AQUA + "Frozen" + ChatFormatting.DARK_GRAY + "  no wear, no recovery");
        }

        // Everything below needs the server's copy of the record. Until the reply lands - one
        // tick, in practice - the line above already stands on its own.
        if (reply == null) return tip;

        // Where this sits on the whole run, pristine to fully sunk. The line above restarts its
        // count when grass gives way to earth, so on its own it cannot say how far along a block
        // really is.
        int chainLength = reply.chainLength;
        int chainIndex = reply.chainIndex;
        if (chainLength > 0 && chainIndex >= 0) {
            tip.add(ChatFormatting.GRAY + "Overall: " + ChatFormatting.WHITE + (chainIndex + 1) + "/" + chainLength);
        }

        // Asked of the reply as well as of the layers: a reply that tells no progress is for a record with
        // nothing drawn, which a ghost can outlive on this client for as long as it takes to catch up.
        if (layer + 1 < layers && reply.tellsProgress) {
            tip.add(ChatFormatting.DARK_GRAY + "  toward next: " + Math.round(reply.progress * 100f) + "%");
        }

        int recovery = reply.recoverySeconds;
        if (recovery >= 0) {
            String idle = "last step " + format(reply.untouchedSeconds) + " ago";
            tip.add(
                ChatFormatting.DARK_GRAY + (recovery == 0 ? "  recovering now, " + idle
                    : "  recovers in " + format(recovery) + ", " + idle));
        }
        return tip;
    }

    /**
     * The lines for a Golem of Ways, read from the entity's own watched values.
     *
     * <p>
     * Watched values are the server's, sent to every client that can see the golem, so the client that Jade
     * runs on and the server The One Probe runs on read the same figures.
     */
    public static List<String> golem(EntityGolemOfWays golem, Function<String, String> localise) {
        return golem(
            golem.fittedUpgrade(),
            golem.watchedRadius(),
            golem.isFleeing(),
            golem.isChewing(),
            golem.busyKind(),
            golem.isArmed(),
            golem.watchedOrders(),
            golem.hasMendingStock(),
            golem.watchedShortOfBlocks(),
            golem.watchedSummoner(),
            golem.watchedConfigurer(),
            localise);
    }

    /** The golem's lines from its figures, apart from the entity so they can be held without a world. */
    static List<String> golem(GolemUpgrade fitted, int radius, boolean fleeing, boolean chewing, int busy,
        boolean armed, boolean orders, boolean mendingStock, boolean shortOfBlocks, String built, String told,
        Function<String, String> localise) {
        List<String> tip = new ArrayList<String>();

        if (fitted != GolemUpgrade.NONE) {
            tip.add(
                ChatFormatting.AQUA + localise.apply("trmtgtnh.golem.waila.upgrade")
                    + " "
                    + ChatFormatting.WHITE
                    + localise.apply(fitted.itemNameKey()));
        }

        tip.add(ChatFormatting.GRAY + localise.apply("trmtgtnh.golem.gui.radius") + " " + ChatFormatting.WHITE + radius);

        // What it is doing. Chewing is asked before the busy kinds because it is not one of
        // them: eating is carried by a bit of its own rather than by a stroke of work, so a
        // golem visibly taking a mouthful - arms up to its mouth, head dipping to meet them,
        // the eating sound with it - used to report that it had nothing to do for as long as
        // the mouthful lasted. The state was already on the wire and simply never asked for.
        String doing = doingKey(busy);
        if (fleeing) {
            tip.add(ChatFormatting.RED + localise.apply("trmtgtnh.golem.waila.fleeing"));
        } else if (chewing) {
            tip.add(ChatFormatting.GREEN + localise.apply("trmtgtnh.golem.waila.chewing"));
        } else if (doing != null) {
            tip.add(ChatFormatting.GREEN + localise.apply(doing));
        } else {
            tip.add(ChatFormatting.GRAY + localise.apply("trmtgtnh.golem.waila.idle"));
        }

        // And, separately, whatever is stopping it - which is not the same question and used to be
        // asked as though it were. A golem wearing ground perfectly well can still be unable to
        // mend any of it, and hanging that behind "is it busy" meant the one line that would have
        // explained the problem was the one line never shown while the problem was happening.
        if (!armed) {
            tip.add(ChatFormatting.RED + localise.apply("trmtgtnh.golem.waila.noTool"));
        }
        if (!orders) {
            tip.add(ChatFormatting.RED + localise.apply("trmtgtnh.golem.waila.noOrders"));
        }
        if (!mendingStock) {
            tip.add(ChatFormatting.RED + localise.apply("trmtgtnh.golem.waila.noBlocks"));
        } else if (shortOfBlocks) {
            tip.add(ChatFormatting.RED + localise.apply("trmtgtnh.golem.waila.wrongBlocks"));
        }

        if (built != null && !built.isEmpty()) {
            tip.add(ChatFormatting.DARK_GRAY + localise.apply("trmtgtnh.golem.gui.built") + " " + built);
        }
        if (told != null && !told.isEmpty()) {
            tip.add(ChatFormatting.DARK_GRAY + localise.apply("trmtgtnh.golem.waila.told") + " " + told);
        }
        return tip;
    }

    /** What to call whatever it has its tool up for, or null when it has it down. */
    private static String doingKey(int busy) {
        switch (busy) {
            case GolemCombat.BUSY_WEAR:
                return "trmtgtnh.golem.waila.wearing";
            case GolemCombat.BUSY_MEND:
                return "trmtgtnh.golem.waila.mending";
            case GolemCombat.BUSY_FIGHT:
                return "trmtgtnh.golem.waila.fighting";
            default:
                return null;
        }
    }

    /**
     * What a reinforcement level is worth against traffic, or nothing when it is worth nothing.
     *
     * <p>
     * Read from this machine's own settings, the same as every other number on this tooltip. A
     * server running a different factor will disagree, and that is the existing bargain with all
     * of these lines rather than anything new here.
     */
    private static String wearHold(int level) {
        float factor = Reinforcement.wearFactor(level);
        if (factor <= 1f) return "";
        String shown = factor == Math.round(factor) ? String.valueOf(Math.round(factor))
            : String.format(java.util.Locale.ROOT, "%.1f", Float.valueOf(factor));
        return ChatFormatting.DARK_GRAY + "  x" + shown + " to wear";
    }

    /** "grass" reads better in a tooltip than "GRASS". */
    private static String label(SurfaceFamily family) {
        String key = family.key();
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    /**
     * An in-game duration at whatever scale suits it.
     *
     * <p>
     * The units step down rather than rounding to the nearest day, because both numbers here
     * are read at both ends of their range: a stage takes tens of days to grow back, and the
     * ground you are standing on was walked on seconds ago. An earlier version floored
     * everything below half a day to "1h", which made ground you had just trodden on
     * indistinguishable from ground you had left alone all morning.
     */
    static String format(int gameSeconds) {
        if (gameSeconds >= SECONDS_PER_DAY) return Math.round(gameSeconds / SECONDS_PER_DAY) + "d";
        int minutes = (int) (gameSeconds / SECONDS_PER_DAY * 24.0D * 60.0D);
        if (minutes >= 60) return (minutes / 60) + "h";
        return minutes <= 0 ? "moments" : minutes + "m";
    }
}
