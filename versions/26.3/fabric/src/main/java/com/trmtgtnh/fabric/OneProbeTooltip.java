package com.trmtgtnh.fabric;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.compat.TooltipLines;
import com.trmtgtnh.entity.EntityGolemOfWays;
import com.trmtgtnh.erosion.ErosionEntry;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.erosion.ErosionStore;
import com.trmtgtnh.network.PacketInspect;
import com.trmtgtnh.network.TrmtNetwork;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeHitEntityData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoEntityProvider;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;
import mcjty.theoneprobe.api.TheOneProbePlugin;

/**
 * This mod's lines in The One Probe's tooltip on Fabric - the NeoForge module's {@code OneProbeTooltip}, word for word
 * in everything it asks and says (OneProbeIsTheSameOnFabricTest holds the two bodies together), and the same lines
 * {@link com.trmtgtnh.compat.JadeTooltip} gives Jade.
 *
 * <p>
 * The One Probe has a Fabric build from 26.2 (15.0.1), where it had only NeoForge's before (0.9.227; decided: supported
 * wherever a build exists). Fabric has no messages between mods, so where NeoForge's plugin answers a message, this one
 * is found by The One Probe through an entrypoint of its own name, {@code theoneprobe}, as an implementation of its
 * {@link TheOneProbePlugin} (read from theoneprobe-26.2-15.0.1-fabric's TheOneProbe and its fabric.mod.json). An
 * entrypoint is only loaded by the mod that asks for it, so a game without The One Probe never touches this class. Its
 * API is NeoForge's build's but for the fluid calls, which this never makes.
 */
public final class OneProbeTooltip implements TheOneProbePlugin, IProbeInfoProvider {

    /**
     * How a language key travels to the client: between The One Probe's two markers, which its client replaces with
     * the key translated into the reader's language - on a line sent as a component, which is the form whose text
     * it reads for them. Translated here instead it would be in the server's language, which on a dedicated server
     * is nobody's in particular.
     */
    static String marked(String key) {
        return IProbeInfo.STARTLOC + key + IProbeInfo.ENDLOC;
    }

    @Override
    public void register(ITheOneProbe probe) {
        // One provider for blocks, against every block - The One Probe asks every provider about whatever is under
        // the crosshair - and one for entities, which speaks only for the golem.
        probe.registerProvider(this);
        probe.registerEntityProvider(new Golem());
    }

    // The block provider's name is an Identifier (a ResourceLocation until 1.21.11) from The One Probe 1.20; the
    // entity provider's is still a string, which is why the golem's provider is a class of its own here where 1.16.5's
    // was this one.
    @Override
    public net.minecraft.resources.Identifier getID() {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath(Trmt.MODID, "wear");
    }

    @Override
    public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, Player player, Level world, BlockState blockState,
        IProbeHitData data) {
        BlockPos at = data.getPos();
        if (at == null || world == null || !TrmtNetwork.probeLinesFor(player)) return;
        add(probeInfo, linesAt(world, at.getX(), at.getY(), at.getZ(), TrmtNetwork.drawsWearFor(player)));
    }

    /**
     * The lines for one position, from the server's record.
     *
     * <p>
     * A worn square is drawn where the record's packed state shows one and the reader's client is drawing the
     * overlay at all - the two questions that client answers before it stands a ghost there. The second is asked of
     * that player (TrmtNetwork.drawsWearFor), as NeoForge's plugin asks it.
     */
    static List<String> linesAt(Level world, int x, int y, int z, boolean drawsWear) {
        ErosionEntry entry = ErosionStore.get()
            .getEntry(world, x, y, z);
        short state = entry == null ? ErosionState.NONE : entry.packState();
        boolean ghostHere = drawsWear && BlockGhost.shows(state);
        int glow = entry == null ? 0 : entry.getLight();
        return TooltipLines.block(
            ghostHere,
            state,
            glow,
            TooltipLines.Reply.of(PacketInspect.readout(world, x, y, z)),
            OneProbeTooltip::marked);
    }

    /** The entity provider, which speaks only for the golem, under the name both providers had on 1.16.5. */
    private static final class Golem implements IProbeInfoEntityProvider {

        @Override
        public String getID() {
            return Trmt.MODID + ":wear";
        }

        @Override
        public void addProbeEntityInfo(ProbeMode mode, IProbeInfo probeInfo, Player player, Level world, Entity entity,
            IProbeHitEntityData data) {
            if (!(entity instanceof EntityGolemOfWays) || !TrmtNetwork.probeLinesFor(player)) return;
            add(probeInfo, TooltipLines.golem((EntityGolemOfWays) entity, OneProbeTooltip::marked));
        }
    }

    /**
     * Each line as a component, because that is the form whose text The One Probe's client reads for its markers;
     * a line handed over as a plain string is made a translatable component whose key is the whole line, and the
     * markers in it are drawn as they are.
     */
    private static void add(IProbeInfo probeInfo, List<String> lines) {
        for (String line : lines) probeInfo.text(Component.literal(line));
    }
}
