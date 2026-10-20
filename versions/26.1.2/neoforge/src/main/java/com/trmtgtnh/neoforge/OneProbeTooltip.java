package com.trmtgtnh.neoforge;

import java.util.List;
import java.util.function.Function;

import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.InterModComms;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.compat.TooltipLines;
import com.trmtgtnh.config.TrmtConfig;
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

/**
 * This mod's lines in The One Probe's tooltip - the same lines {@link com.trmtgtnh.compat.JadeTooltip} gives Jade.
 *
 * <p>
 * The lines are {@link TooltipLines}', which Jade's are too, so the two say the same thing word for word. Where the
 * figures come from is the one difference, and it is The One Probe's rather than this mod's: it calls its providers
 * on the server, from the request its client sends while the crosshair rests on something, and sends the finished
 * lines back. So nothing here reads the client's half of the mod, and nothing here says it reads the inspection
 * reply - the crosshair's request to the server is Jade's alone. What the client's overlay holds is the record's own
 * packed state, and what the inspection reply carries is {@link PacketInspect#readout}, both read here straight from
 * the server's store - the source the client's copies are sent from.
 *
 * <p>
 * Every reference to The One Probe is confined to this class, and it is only loaded when The One Probe is installed
 * and answers the message in {@link #request}, so a pack without it never touches it. NeoForge only: The One Probe has
 * no Forge or Fabric release for this version. Read against the API of The One Probe 1.21_neo-12.0.8, whose provider
 * interfaces, markers and message are 1.20.1-10.0.4's; only the message's own class moved, to NeoForge's FML.
 */
public final class OneProbeTooltip implements Function<ITheOneProbe, Void>, IProbeInfoProvider {

    /**
     * How a language key travels to the client: between The One Probe's two markers, which its client replaces with
     * the key translated into the reader's language - on a line sent as a component, which is the form whose text
     * it reads for them. Translated here instead it would be in the server's language, which on a dedicated server
     * is nobody's in particular.
     */
    static String marked(String key) {
        return IProbeInfo.STARTLOC + key + IProbeInfo.ENDLOC;
    }

    /**
     * Announces this provider. The One Probe asks the supplier for a function and hands it its API if it is
     * installed. Sent while mods enqueue their messages; see TrmtNeoForge.
     */
    static void request() {
        InterModComms.sendTo("theoneprobe", "getTheOneProbe", OneProbeTooltip::new);
    }

    @Override
    public Void apply(ITheOneProbe probe) {
        // One provider for blocks, against every block - The One Probe asks every provider about whatever is under
        // the crosshair - and one for entities, which speaks only for the golem.
        probe.registerProvider(this);
        probe.registerEntityProvider(new Golem());
        return null;
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
     * that player (TrmtNetwork.drawsWearFor) since 0.9.224: until then it was this machine's settings, so on a
     * dedicated server a player who had hidden worn ground was still told of it, where Hwyla and Jade, which ask the
     * client, said nothing.
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
