package com.trmtgtnh.compat;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.block.BlockGhostGrass;
import com.trmtgtnh.block.GhostBlock;
import com.trmtgtnh.client.ClientErosionCache;
import com.trmtgtnh.client.InspectionCache;
import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionKey;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.erosion.Reinforcement;
import com.trmtgtnh.erosion.SinkProfile;
import com.trmtgtnh.surface.SurfaceFamily;

import cpw.mods.fml.common.event.FMLInterModComms;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

/**
 * Shows what this mod knows about a block in Waila's tooltip: how worn it is and how long it has left to
 * recover, and - on any block at all, worn or not - its reinforcement and spawn ward.
 *
 * <p>
 * Waila already reports the block itself correctly - a ghost hands back the block it covers, so
 * the tooltip says "Grass Block" and offers the right tool. What it cannot know is the part that
 * only exists as data: which stage the wear has reached and when it will start growing back.
 *
 * <p>
 * An earlier version asked for that through Waila's server-NBT channel, which produced an empty
 * tooltip: Waila only fetches NBT for blocks that have a tile entity, and a ghost deliberately
 * has none. So nothing here goes through Waila at all. The surface and the stage are read
 * straight off the client's own overlay - the client is already drawing them, so it necessarily
 * knows them - and what exists only in the server's record, the progress, the idle time, the recovery,
 * the reinforcement and the ward, arrives through {@link InspectionCache}, which asks about the one block
 * under the crosshair.
 *
 * <p>
 * Every reference to Waila is confined to this class, and it is only ever loaded in response to
 * Waila answering the registration message below, so a pack without Waila never touches it.
 */
public class WailaCompat implements IWailaDataProvider {

    /** In-game seconds in a Minecraft day, which is how healing durations are expressed. */
    private static final double SECONDS_PER_DAY = 1200.0D;

    /** True for the provider registered against every block, which speaks only where no ghost stands. */
    private final boolean everyBlock;

    public WailaCompat() {
        this(false);
    }

    private WailaCompat(boolean everyBlock) {
        this.everyBlock = everyBlock;
    }

    /** Announces this provider. Waila calls the named method back if it is installed. */
    public static void request() {
        FMLInterModComms.sendMessage("Waila", "register", "com.trmtgtnh.compat.WailaCompat.callbackRegister");
    }

    @SuppressWarnings("unused") // called by Waila through the message above
    public static void callbackRegister(IWailaRegistrar registrar) {
        // One wear provider is named against each ghost class, BlockGhost and BlockGhostGrass here and the
        // stair ghost below, so worn stone, sand and grass each have a provider of their own whatever
        // Waila does with a class it was not given. Waila is not on this build's classpath, so whether it
        // also hands a provider the subclasses of the class it was registered against is not read from its
        // code: the in-game Waila check (step 1) settles it, and the every-block provider further down is
        // written to be right either way.
        registrar.registerBodyProvider(new WailaCompat(false), BlockGhost.class);
        registrar.registerBodyProvider(new WailaCompat(false), BlockGhostGrass.class);
        // A stair ghost is a third class rather than a variant of the first, because the
        // renderer's dispatch for a stair casts to BlockStairs - so it needs saying here too,
        // and a worn stair had no tooltip at all until it did.
        registrar.registerBodyProvider(new WailaCompat(false), com.trmtgtnh.block.BlockGhostStairs.class);
        // Reinforcement and a spawn ward can sit on any block, worn or not, and most of what gets warded is
        // ground nobody has worn, so a second provider is named against every block. InspectionReach.speaks
        // keeps the two from writing the same lines twice over a ghost, whichever way Waila matches.
        registrar.registerBodyProvider(new WailaCompat(true), net.minecraft.block.Block.class);
        // The golem answers for itself: whose it is, and why it is standing still.
        registrar.registerBodyProvider(new WailaGolem(), com.trmtgtnh.entity.EntityGolemOfWays.class);
        // Label only. The key beside it is what Waila writes to its own config, so it stays as
        // it is and a player's existing toggle survives the rename.
        registrar.addConfig(Trmt.NAME, "trmtgtnh.wear");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public List<String> getWailaBody(ItemStack stack, List currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        if (!config.getConfig("trmtgtnh.wear")) return currentTip;

        boolean ghostHere = accessor.getBlock() instanceof GhostBlock;
        if (!com.trmtgtnh.util.InspectionReach.speaks(everyBlock, ghostHere)) return currentTip;

        int x = accessor.getPosition().blockX;
        int y = accessor.getPosition().blockY;
        int z = accessor.getPosition().blockZ;

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
                currentTip.add(
                    EnumChatFormatting.AQUA + "Reinforced "
                        + EnumChatFormatting.WHITE
                        + level
                        + "/"
                        + cap
                        + (level >= cap ? EnumChatFormatting.DARK_GRAY + "  blast-proof" : "")
                        + (wears ? wearHold(level) : ""));
            }
        }

        // Spawn ward, on any block for the same reason, from the same inspection reply.
        if (TrmtConfig.wardEnabled && InspectionCache.has(x, y, z)) {
            int ward = InspectionCache.ward();
            if ((ward & 0x1) != 0) {
                currentTip.add(EnumChatFormatting.AQUA + "Warded" + EnumChatFormatting.WHITE + "  no hostile spawns");
            }
            if ((ward & 0x2) != 0) {
                currentTip.add(EnumChatFormatting.AQUA + "Warded" + EnumChatFormatting.WHITE + "  no passive spawns");
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
                currentTip.add(
                    EnumChatFormatting.AQUA + "Lit "
                        + EnumChatFormatting.WHITE
                        + (glow & 0xF)
                        + "/15"
                        + EnumChatFormatting.DARK_GRAY
                        + "  "
                        + net.minecraft.util.StatCollector
                            .translateToLocal("trmtgtnh.light.color." + ((glow >> 4) & 0xF)));
            }
        }

        // Every line below is drawn from wear, and only a ghost has wear to draw. Beyond this gate the provider
        // also meets leaves, plants and unworn ground, whose records are trample tallies, ground short of its
        // first gradation, or kept only for a reinforcement or a ward - and a tally's wear is the count that
        // breaks a leaf, not progress toward a gradation. So nothing below may move above it. A server from
        // 0.9.213 withholds those figures for such a record as well (RecordReadout); an older one sends them,
        // and there this gate is all that stands in the way.
        if (!ghostHere) return currentTip;

        GhostBlock ghost = (GhostBlock) accessor.getBlock();

        // Straight off the client's own record. It holds the surface, how worn the face is and
        // how far the ground has dropped, and those are three separate things: a layer count on
        // its own restarts every time the ground sinks a pixel, so it cannot say how far along a
        // block really is.
        short state = stateFromOverlay(x, y, z);
        SurfaceFamily family = state == ErosionState.NONE ? ghost.appearance() : ErosionState.familyOf(state);
        int layer = state == ErosionState.NONE ? accessor.getMetadata() : ErosionState.layerOf(state);
        int sink = SinkProfile.shown(ghost.appearance(), ErosionState.sinkOf(state));
        if (family == null || layer < 0) return currentTip;

        FamilySettings settings = TrmtConfig.family(family);
        int layers = settings == null ? layer + 1
            : Math.max(sink > 0 ? settings.layersPerDepth : settings.stages, layer + 1);

        StringBuilder worn = new StringBuilder();
        worn.append(EnumChatFormatting.GRAY)
            .append("Worn: ")
            .append(EnumChatFormatting.WHITE)
            .append(label(family))
            .append(' ')
            .append(layer + 1)
            .append('/')
            .append(layers);

        if (sink > 0) {
            worn.append(EnumChatFormatting.GRAY)
                .append(", sunk ")
                .append(sink)
                .append("/16");
        }
        currentTip.add(worn.toString());

        // Straight off the packed state, so it stands the moment the ghost is drawn rather than
        // waiting for the server's reply. A position with no overlay record cannot be pinned, so
        // there is nothing to miss by reading it here.
        if (ErosionState.frozenOf(state)) {
            currentTip
                .add(EnumChatFormatting.AQUA + "Frozen" + EnumChatFormatting.DARK_GRAY + "  no wear, no recovery");
        }

        // Everything below needs the server's copy of the record. Until the reply lands - one
        // tick, in practice - the line above already stands on its own.
        if (!InspectionCache.has(x, y, z)) return currentTip;

        // Where this sits on the whole run, pristine to fully sunk. The line above restarts its
        // count when grass gives way to earth, so on its own it cannot say how far along a block
        // really is.
        int chainLength = InspectionCache.chainLength();
        int chainIndex = InspectionCache.chainIndex();
        if (chainLength > 0 && chainIndex >= 0) {
            currentTip.add(
                EnumChatFormatting.GRAY + "Overall: "
                    + EnumChatFormatting.WHITE
                    + (chainIndex + 1)
                    + "/"
                    + chainLength);
        }

        // Asked of the reply as well as of the layers: a reply that tells no progress is for a record with
        // nothing drawn, which a ghost can outlive on this client for as long as it takes to catch up.
        if (layer + 1 < layers && InspectionCache.tellsProgress()) {
            currentTip.add(
                EnumChatFormatting.DARK_GRAY + "  toward next: " + Math.round(InspectionCache.progress() * 100f) + "%");
        }

        int recovery = InspectionCache.recoverySeconds();
        if (recovery >= 0) {
            String idle = "last step " + format(InspectionCache.untouchedSeconds()) + " ago";
            currentTip.add(
                EnumChatFormatting.DARK_GRAY + (recovery == 0 ? "  recovering now, " + idle
                    : "  recovers in " + format(recovery) + ", " + idle));
        }
        return currentTip;
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
        return EnumChatFormatting.DARK_GRAY + "  x" + shown + " to wear";
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

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return null; // the block's own getPickBlock already reports what is really there
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List<String> getWailaHead(ItemStack stack, List currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return currentTip;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List<String> getWailaTail(ItemStack stack, List currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return currentTip;
    }

    @Override
    public net.minecraft.nbt.NBTTagCompound getNBTData(net.minecraft.entity.player.EntityPlayerMP player,
        net.minecraft.tileentity.TileEntity tile, net.minecraft.nbt.NBTTagCompound tag, net.minecraft.world.World world,
        int x, int y, int z) {
        return tag; // not registered as an NBT provider; a ghost has no tile entity to hang it on
    }
}
