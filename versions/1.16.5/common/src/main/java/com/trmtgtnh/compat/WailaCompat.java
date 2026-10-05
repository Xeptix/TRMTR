package com.trmtgtnh.compat;

import java.util.List;

import net.minecraft.ChatFormatting;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.client.ClientErosionCache;
import com.trmtgtnh.client.InspectionCache;
import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionKey;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.erosion.Reinforcement;
import com.trmtgtnh.erosion.SinkProfile;
import com.trmtgtnh.surface.SurfaceFamily;


/**
 * Shows what this mod knows about a block in Waila's tooltip: how worn it is and how long it has left to
 * recover, and - on any block at all, worn or not - its reinforcement and spawn ward.
 *
 * <p>
 * Waila already reports the block itself correctly — a ghost hands back the block it covers, so
 * the tooltip says "Grass Block" and offers the right tool. What it cannot know is the part that
 * only exists as data: which stage the wear has reached and when it will start growing back.
 *
 * <p>
 * An earlier version asked for that through Waila's server-NBT channel, which produced an empty
 * tooltip: Waila only fetches NBT for blocks that have a tile entity, and a ghost deliberately
 * has none. So nothing here goes through Waila at all. The surface and the stage are read
 * straight off the client's own overlay — the client is already drawing them, so it necessarily
 * knows them — and what exists only in the server's record, the progress, the idle time, the recovery,
 * the reinforcement and the ward, arrives through {@link InspectionCache}, which asks about the one block
 * under the crosshair.
 *
 * <p>
 * Every reference to Waila is confined to this class, and it is only ever loaded in response to
 * Waila answering the registration message below, so a pack without Waila never touches it.
 */
/**
 * The lines this mod writes into a block tooltip, as text and nothing else.
 *
 * <p>
 * <strong>No tooltip type is named here, deliberately.</strong> The mod that draws the tooltip is
 * Jade on one loader and WTHIT on the other; they share a package and almost an interface, and each
 * loader module has only its own on the compile path. So this holds the whole of what is said and
 * each loader holds the small class that says it - which also means these lines can be read by
 * something that has neither mod, and are.
 *
 * <p>
 * The toggle is the caller's. Both tooltip mods write their own config entry for it and hand the
 * answer in; this is asked only once the answer is yes.
 */
public final class WailaCompat {

    private static final double SECONDS_PER_DAY = 1200.0D;

    private WailaCompat() {}

    /**
     * Every line for one square, appended in the order they are said.
     *
     * @param everyBlock whether this is the provider named against every block rather than the one
     *                   named against a ghost, which is what decides whether it speaks at all
     * @param ghostHere  whether what is being looked at is one of this mod's own worn squares
     */
    public static void ground(List<String> into, boolean everyBlock, boolean ghostHere, int x, int y, int z) {
        if (!com.trmtgtnh.util.InspectionReach.speaks(everyBlock, ghostHere)) return;

        // Reinforcement can sit on any block, worn or not, so this line is written before the wear body's own
        // "only a ghost" gate and over unworn blocks too. The level is the server's, read through the same
        // inspection reply the wear numbers use.
        if (TrmtConfig.reinforceEnabled && InspectionCache.has(x, y, z)) {
            int level = InspectionCache.reinforce();
            if (level > 0) {
                int cap = Math.min(3, TrmtConfig.reinforceMaxLevel);
                // What the level buys against traffic, which is otherwise invisible: the wear bar simply fills
                // more slowly and nothing says why. Said only of ground that wears through gradations - a ghost,
                // or a block the server gives a run of steps - because a reinforced leaf or plant is not trampled
                // at all and brick never wears; see InspectionReach.wears.
                boolean wears = com.trmtgtnh.util.InspectionReach.wears(ghostHere, InspectionCache.chainLength() > 0);
                into.add(
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
        if (TrmtConfig.wardEnabled && InspectionCache.has(x, y, z)) {
            int ward = InspectionCache.ward();
            if ((ward & 0x1) != 0) {
                into.add(ChatFormatting.AQUA + "Warded" + ChatFormatting.WHITE + "  no hostile spawns");
            }
            if ((ward & 0x2) != 0) {
                into.add(ChatFormatting.AQUA + "Warded" + ChatFormatting.WHITE + "  no passive spawns");
            }
        }

        // The glow needs no inspection round trip: the client already knows it, because it is drawing it. Said
        // only over a ghost, because only a ghost glows. Ground healed back to pristine keeps its light for when
        // it wears again, so the client still holds a level for a block that is not lit, and "Lit" over it would
        // describe a light nobody can see.
        if (ghostHere && TrmtConfig.lightEnabled) {
            int glow = com.trmtgtnh.client.ClientLightCache.get()
                .at(x, y, z);
            if ((glow & 0xF) != 0) {
                into.add(
                    ChatFormatting.AQUA + "Lit "
                        + ChatFormatting.WHITE
                        + (glow & 0xF)
                        + "/15"
                        + ChatFormatting.DARK_GRAY
                        + "  "
                        + com.trmtgtnh.util.Translate
                            .get("trmtgtnh.light.colour." + ((glow >> 4) & 0xF)));
            }
        }

        // Every line below is drawn from wear, and only a ghost has wear to draw. Beyond this gate the provider
        // also meets leaves, plants and unworn ground, whose records are trample tallies, ground short of its
        // first gradation, or kept only for a reinforcement or a ward - and a tally's wear is the count that
        // breaks a leaf, not progress toward a gradation. So nothing below may move above it. A server from
        // 0.9.213 withholds those figures for such a record as well (RecordReadout); an older one sends them,
        // and there this gate is all that stands in the way.
        if (!ghostHere) return;

        // Straight off the client's own record. It holds the surface, how worn the face is and
        // how far the ground has dropped, and those are three separate things: a layer count on
        // its own restarts every time the ground sinks a pixel, so it cannot say how far along a
        // block really is.
        short state = stateFromOverlay(x, y, z);
        if (state == ErosionState.NONE) return;
        SurfaceFamily family = ErosionState.familyOf(state);
        int layer = ErosionState.layerOf(state);
        int sink = SinkProfile.shown(family, ErosionState.sinkOf(state));
        if (family == null || layer < 0) return;

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
        into.add(worn.toString());

        // Straight off the packed state, so it stands the moment the ghost is drawn rather than
        // waiting for the server's reply. A position with no overlay record cannot be pinned, so
        // there is nothing to miss by reading it here.
        if (ErosionState.frozenOf(state)) {
            into.add(ChatFormatting.AQUA + "Frozen" + ChatFormatting.DARK_GRAY + "  no wear, no recovery");
        }

        // Everything below needs the server's copy of the record. Until the reply lands — one
        // tick, in practice — the line above already stands on its own.
        if (!InspectionCache.has(x, y, z)) return;

        // Where this sits on the whole run, pristine to fully sunk. The line above restarts its
        // count when grass gives way to earth, so on its own it cannot say how far along a block
        // really is.
        int chainLength = InspectionCache.chainLength();
        int chainIndex = InspectionCache.chainIndex();
        if (chainLength > 0 && chainIndex >= 0) {
            into
                .add(ChatFormatting.GRAY + "Overall: " + ChatFormatting.WHITE + (chainIndex + 1) + "/" + chainLength);
        }

        // Asked of the reply as well as of the layers: a reply that tells no progress is for a record with
        // nothing drawn, which a ghost can outlive on this client for as long as it takes to catch up.
        if (layer + 1 < layers && InspectionCache.tellsProgress()) {
            into.add(
                ChatFormatting.DARK_GRAY + "  toward next: " + Math.round(InspectionCache.progress() * 100f) + "%");
        }

        int recovery = InspectionCache.recoverySeconds();
        if (recovery >= 0) {
            String idle = "last step " + format(InspectionCache.untouchedSeconds()) + " ago";
            into.add(
                ChatFormatting.DARK_GRAY + (recovery == 0 ? "  recovering now, " + idle
                    : "  recovers in " + format(recovery) + ", " + idle));
        }
        return;
    }

    /** The wear recorded at a position, or {@link ErosionState#NONE} when there is none. */
    private static short stateFromOverlay(int x, int y, int z) {
        ClientErosionCache.ChunkOverlay overlay = ClientErosionCache.get()
            .overlay(x >> 4, z >> 4);
        if (overlay == null) return ErosionState.NONE;
        return overlay.stateAt(ErosionKey.packWorld(x, y, z));
    }

    /**
     * What a reinforcement level is worth against traffic, or nothing when it is worth nothing.
     *
     * <p>
     * Read from this client's own settings, the same as every other number on this tooltip. A
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
    private static String format(int gameSeconds) {
        if (gameSeconds >= SECONDS_PER_DAY) return Math.round(gameSeconds / SECONDS_PER_DAY) + "d";
        int minutes = (int) (gameSeconds / SECONDS_PER_DAY * 24.0D * 60.0D);
        if (minutes >= 60) return (minutes / 60) + "h";
        return minutes <= 0 ? "moments" : minutes + "m";
    }

}
