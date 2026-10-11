package com.trmtgtnh.compat;

import java.util.List;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.event.FMLInterModComms;

import com.trmtgtnh.Tags;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.entity.EntityGolemOfWays;
import com.trmtgtnh.erosion.ErosionEntry;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.erosion.ErosionStore;
import com.trmtgtnh.network.PacketInspect;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeHitEntityData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoEntityProvider;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;

/**
 * Tells The One Probe what Hwyla is told: how worn a square is and how long it has left to recover, its
 * reinforcement and spawn ward on any block, its path light, and what a Golem of Ways is doing and why not.
 *
 * <p>
 * The lines are {@link TooltipLines}', the same ones {@link WailaCompat} and {@link WailaGolem} add, so the two
 * tooltips say the same thing word for word. Where the figures come from is the one difference, and it is The One
 * Probe's rather than this mod's: it calls its providers on the server, from the request its client sends while the
 * crosshair rests on something, and sends the finished lines back. So nothing here reads the client's half of the
 * mod and nothing here needs the inspection request Hwyla's provider waits on. What the client's overlay holds is
 * the record's own packed state, and what the inspection reply carries is {@link PacketInspect#readout}, both read
 * here straight from the server's store - the source the client's copies are sent from.
 *
 * <p>
 * Every reference to The One Probe is confined to this class, and it is only loaded when The One Probe is
 * installed and answers the message in {@link #request}, so a pack without it never touches it. Read against the
 * API of The One Probe 1.12-1.4.28.
 */
public class OneProbeCompat
    implements com.google.common.base.Function<ITheOneProbe, Void>, IProbeInfoProvider, IProbeInfoEntityProvider {

    /**
     * How a language key travels to the client: between The One Probe's two markers, which its client replaces
     * with the key translated into the reader's language. Translated here instead it would be in the server's
     * language, which on a dedicated server is nobody's in particular.
     */
    static String marked(String key) {
        return IProbeInfo.STARTLOC + key + IProbeInfo.ENDLOC;
    }

    /** Announces this provider. The One Probe makes one of this class and hands it its API if it is installed. */
    public static void request() {
        FMLInterModComms.sendFunctionMessage("theoneprobe", "getTheOneProbe", OneProbeCompat.class.getName());
    }

    @Override
    public Void apply(ITheOneProbe probe) {
        // One provider for blocks, against every block - The One Probe asks every provider about whatever is under
        // the crosshair - and one for entities, which speaks only for the golem.
        probe.registerProvider(this);
        probe.registerEntityProvider(this);
        return null;
    }

    @Override
    public String getID() {
        return Tags.MOD_ID + ":wear";
    }

    @Override
    public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, EntityPlayer player, World world,
        IBlockState blockState, IProbeHitData data) {
        BlockPos at = data.getPos();
        if (at == null || world == null) return;
        add(probeInfo, linesAt(world, at.getX(), at.getY(), at.getZ()));
    }

    /**
     * The lines for one position, from the server's record.
     *
     * <p>
     * A worn square is drawn where the record's packed state shows one and the overlay is being drawn at all -
     * the two questions the client answers before it stands a ghost there. The settings asked are this machine's:
     * on a single-player world the client's own, since the server shares them, and on a dedicated server the
     * server's, which are the ones that govern there.
     */
    static List<String> linesAt(World world, int x, int y, int z) {
        ErosionEntry entry = ErosionStore.get()
            .getEntry(world, x, y, z);
        short state = entry == null ? ErosionState.NONE : entry.packState();
        boolean ghostHere = TrmtConfig.enabled && TrmtConfig.overlayVisible() && BlockGhost.shows(state);
        int glow = entry == null ? 0 : entry.getLight();
        return TooltipLines.block(
            ghostHere,
            state,
            glow,
            TooltipLines.Reply.of(PacketInspect.readout(world, x, y, z)),
            OneProbeCompat::marked);
    }

    @Override
    public void addProbeEntityInfo(ProbeMode mode, IProbeInfo probeInfo, EntityPlayer player, World world,
        Entity entity, IProbeHitEntityData data) {
        if (!(entity instanceof EntityGolemOfWays)) return;
        add(probeInfo, TooltipLines.golem((EntityGolemOfWays) entity, OneProbeCompat::marked));
    }

    private static void add(IProbeInfo probeInfo, List<String> lines) {
        for (String line : lines) probeInfo.text(line);
    }
}
