package com.trmtgtnh.compat;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.compat.WailaCompat;
import com.trmtgtnh.compat.WailaGolem;
import com.trmtgtnh.entity.EntityGolemOfWays;

import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IRegistrar;
import mcp.mobius.waila.api.IWailaPlugin;
import mcp.mobius.waila.api.ITooltip;

/**
 * This mod's lines in WTHIT's block tooltip.
 *
 * <p>
 * WTHIT is Hwyla's successor on this version and keeps its package, so this is the same integration
 * the 1.12.2 edition has under a different interface. What is said lives in {@link WailaCompat} and
 * {@link WailaGolem}, which name no tooltip type at all - because the other loader's tooltip mod is
 * a different mod with a different way of announcing a plugin, and only the announcement and the
 * shape of a provider actually differ.
 *
 * <p>
 * <strong>Two block providers, as there.</strong> One named against the ghost, for everything that
 * is only true of worn ground; one against every block, because reinforcement and a spawn ward sit
 * on blocks nobody has worn. {@code InspectionReach.speaks} is what keeps the two from saying the
 * same thing twice over a ghost, whichever way the tooltip matches a class - which is the same line
 * the other edition relies on and for the same reason.
 *
 * <p>
 * Found by annotation rather than by an inter-mod message, which is what WTHIT takes. Nothing here is
 * loaded when WTHIT is absent: a class is only looked at by the thing that looks for it.
 */

public final class WthitTooltip implements IWailaPlugin {

    /**
     * The toggle Jade writes into its own config.
     *
     * <p>
     * The same string the other edition uses - {@code trmtgtnh:wear} - so a player who recognises it
     * in one recognises it in the other. Hwyla takes a label and a key; this takes a name and a
     * default, and the default is on because an integration nobody can see is an integration nobody
     * knows they have.
     */
    private static final ResourceLocation TOGGLE = new ResourceLocation(Trmt.MODID, "wear");

    @Override
    public void register(IRegistrar registrar) {
        // Something here reads the reply, so the crosshair is worth asking about. See InspectionCache.
        com.trmtgtnh.client.InspectionCache.noteReader();
        // Each in the tooltip's body, as before; from WTHIT 3 the position is the method's name.
        registrar.body(new Ground(false), BlockGhost.class);
        registrar.body(new Ground(true), Block.class);
        registrar.body(new Golem(), EntityGolemOfWays.class);
        // A worn square is named for the block it covers, as 1.7.10's Waila names it from the covered block's stack
        // (spec GF14). WTHIT's core takes the icon from the pick-block stack already, but writes the name and the mod
        // from the stand-in itself, at priority 900; these run after it and put the covered block's in their place.
        registrar.head(new Named(), BlockGhost.class);
        registrar.tail(new Named(), BlockGhost.class);
        registrar.addConfig(TOGGLE, true);
    }

    /** One of the two block providers; see the class note for why there are two. */
    private static final class Ground implements IBlockComponentProvider {

        private final boolean everyBlock;

        Ground(boolean everyBlock) {
            this.everyBlock = everyBlock;
        }

        @Override
        public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            if (!config.getBoolean(TOGGLE)) return;
            BlockPos at = accessor.getPosition();
            List<String> said = new ArrayList<String>();
            WailaCompat
                .ground(said, everyBlock, accessor.getBlock() instanceof BlockGhost, at.getX(), at.getY(), at.getZ());
            for (String line : said) {
                tooltip.addLine(Component.literal(line));
            }
        }
    }

    /** A worn square's name and mod lines, written as the block it stands in for's (spec GF14). */
    private static final class Named implements IBlockComponentProvider {

        @Override
        public void appendHead(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            net.minecraft.world.item.ItemStack covered = covered(accessor);
            if (covered.isEmpty()) return;
            tooltip.setLine(mcp.mobius.waila.api.WailaConstants.OBJECT_NAME_TAG,
                mcp.mobius.waila.api.IWailaConfig.get()
                    .getFormatter()
                    .blockName(covered.getHoverName()
                        .getString()));
        }

        @Override
        public void appendTail(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            if (!config.getBoolean(mcp.mobius.waila.api.WailaConstants.CONFIG_SHOW_MOD_NAME)) return;
            net.minecraft.world.item.ItemStack covered = covered(accessor);
            if (covered.isEmpty()) return;
            tooltip.setLine(mcp.mobius.waila.api.WailaConstants.MOD_NAME_TAG,
                mcp.mobius.waila.api.IWailaConfig.get()
                    .getFormatter()
                    .modName(mcp.mobius.waila.api.IModInfo.get(covered)
                        .getName()));
        }

        /** The covered block's item: the stand-in's pick-block, which is what 1.7.10's Waila names it from. */
        private static net.minecraft.world.item.ItemStack covered(IBlockAccessor accessor) {
            return accessor.getBlock()
                .getCloneItemStack(accessor.getLevel(), accessor.getPosition(), accessor.getBlockState());
        }
    }

    private static final class Golem implements IEntityComponentProvider {

        @Override
        public void appendBody(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
            if (!config.getBoolean(TOGGLE)) return;
            Entity looked = accessor.getEntity();
            List<String> said = new ArrayList<String>();
            WailaGolem.golem(said, looked);
            for (String line : said) {
                tooltip.addLine(Component.literal(line));
            }
        }
    }
}
