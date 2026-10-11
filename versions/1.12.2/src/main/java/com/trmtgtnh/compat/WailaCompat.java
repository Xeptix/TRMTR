package com.trmtgtnh.compat;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.event.FMLInterModComms;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.client.ClientErosionCache;
import com.trmtgtnh.client.InspectionCache;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionKey;
import com.trmtgtnh.erosion.ErosionState;

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
 * Waila answering the registration message below, so a pack without Waila never touches it. The lines
 * themselves are worded in {@link TooltipLines}, which words them for The One Probe as well.
 */
public class WailaCompat implements IWailaDataProvider {

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
        FMLInterModComms.sendMessage("waila", "register", "com.trmtgtnh.compat.WailaCompat.callbackRegister");
    }

    @SuppressWarnings("unused") // called by Waila through the message above
    public static void callbackRegister(IWailaRegistrar registrar) {
        // One wear provider, named against the one ghost class. The 1.7.10 edition names three here - its plain,
        // grass and stair stand-ins - and this edition has one ghost, drawn for every family, worn stairs included,
        // through unlisted properties, which is what the texture pipeline needed. With one class there is no
        // question of whether Waila hands a provider the subclasses of the class it was given, and the every-block
        // provider further down is written to be right either way.
        registrar.registerBodyProvider(new WailaCompat(false), BlockGhost.class);
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

        boolean ghostHere = accessor.getBlock() instanceof BlockGhost;
        if (!com.trmtgtnh.util.InspectionReach.speaks(everyBlock, ghostHere)) return currentTip;

        net.minecraft.util.math.BlockPos at = accessor.getPosition();
        int x = at.getX();
        int y = at.getY();
        int z = at.getZ();

        // The client's own copies: the overlay it is drawing from, the light it is drawing, and the server's
        // reply to the crosshair's inspection of exactly this position. The lines themselves are TooltipLines',
        // which words them for The One Probe too.
        TooltipLines.Reply reply = InspectionCache.has(x, y, z) ? new TooltipLines.Reply(
            InspectionCache.reinforce(),
            InspectionCache.ward(),
            InspectionCache.chainLength(),
            InspectionCache.chainIndex(),
            InspectionCache.tellsProgress(),
            InspectionCache.progress(),
            InspectionCache.untouchedSeconds(),
            InspectionCache.recoverySeconds()) : null;
        int glow = ghostHere && TrmtConfig.lightEnabled ? com.trmtgtnh.client.ClientLightCache.get()
            .at(x, y, z) : 0;
        currentTip.addAll(
            TooltipLines.block(
                ghostHere,
                ghostHere ? stateFromOverlay(x, y, z) : ErosionState.NONE,
                glow,
                reply,
                net.minecraft.util.text.translation.I18n::translateToLocal));
        return currentTip;
    }

    /** The wear recorded at a position, or {@link ErosionState#NONE} when there is none. */
    private static short stateFromOverlay(int x, int y, int z) {
        ClientErosionCache.ChunkOverlay overlay = ClientErosionCache.get()
            .overlay(x >> 4, z >> 4);
        if (overlay == null) return ErosionState.NONE;
        return overlay.stateAt(ErosionKey.packWorld(x, y, z));
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
        net.minecraft.util.math.BlockPos pos) {
        return tag; // not registered as an NBT provider; a ghost has no tile entity to hang it on
    }
}
