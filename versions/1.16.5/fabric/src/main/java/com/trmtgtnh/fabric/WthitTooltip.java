package com.trmtgtnh.fabric;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.compat.CoveredNames;
import com.trmtgtnh.compat.WailaCompat;
import com.trmtgtnh.compat.WailaGolem;
import com.trmtgtnh.entity.EntityGolemOfWays;

import mcp.mobius.waila.api.IComponentProvider;
import mcp.mobius.waila.api.IDataAccessor;
import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IRegistrar;
import mcp.mobius.waila.api.IWailaPlugin;
import mcp.mobius.waila.api.TooltipPosition;

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
        registrar.registerComponentProvider(new Ground(false), TooltipPosition.BODY, BlockGhost.class);
        registrar.registerComponentProvider(new Ground(true), TooltipPosition.BODY, Block.class);
        registrar.registerComponentProvider(new Golem(), TooltipPosition.BODY, EntityGolemOfWays.class);
        // A worn square named for the block it covers, and that block's mod (spec GF14; 0.9.227). WTHIT writes its name
        // line at priority 900 and its mod line at 1100, so the name is put right at the default and the mod after it.
        registrar.addComponent(new Named(), TooltipPosition.HEAD, BlockGhost.class);
        registrar.addComponent(new Named(), TooltipPosition.TAIL, BlockGhost.class, 1200);
        registrar.addConfig(TOGGLE, true);
    }

    /** The name and mod lines over a worn square, said for the block it covers. See CoveredNames. */
    private static final class Named implements mcp.mobius.waila.api.IBlockComponentProvider {

        @Override
        public void appendHead(List<Component> tooltip, mcp.mobius.waila.api.IBlockAccessor accessor, IPluginConfig config) {
            rename(tooltip, accessor);
        }

        @Override
        public void appendTail(List<Component> tooltip, mcp.mobius.waila.api.IBlockAccessor accessor, IPluginConfig config) {
            rename(tooltip, accessor);
        }
    }

    /** Each tagged line over a ghost with the covered block's words in place of the stand-in's. */
    @SuppressWarnings("unchecked")
    static void rename(List<Component> tooltip, mcp.mobius.waila.api.IBlockAccessor accessor) {
        if (!(tooltip instanceof mcp.mobius.waila.api.ITaggableList)) return;
        net.minecraft.world.item.ItemStack covered = accessor.getStack();
        if (covered == null || covered.isEmpty() || covered.getItem() instanceof net.minecraft.world.item.BlockItem
            && ((net.minecraft.world.item.BlockItem) covered.getItem()).getBlock() instanceof BlockGhost) return;
        mcp.mobius.waila.api.ITaggableList<Object, Component> tags = (mcp.mobius.waila.api.ITaggableList<Object, Component>) tooltip;
        String standIn = accessor.getBlock()
            .getName()
            .getString();
        for (java.util.Map.Entry<Object, Component> each : new ArrayList<java.util.Map.Entry<Object, Component>>(
            tags.getTags()
                .entrySet())) {
            if (!(each.getKey() instanceof ResourceLocation) || each.getValue() == null) continue;
            String tag = ((ResourceLocation) each.getKey()).getPath();
            String was = each.getValue()
                .getString();
            String now = was;
            if (CoveredNames.NAME_TAG.equals(tag)) {
                now = CoveredNames.renamed(was, standIn, covered.getHoverName()
                    .getString());
            } else if (CoveredNames.MOD_TAG.equals(tag)) {
                now = CoveredNames.renamed(was, Trmt.NAME, modOf(covered));
            }
            if (!now.equals(was)) tags.setTag(each.getKey(), new TextComponent(now));
        }
    }

    /** The name of the mod a stack's item comes from, as Fabric Loader knows it. */
    static String modOf(net.minecraft.world.item.ItemStack stack) {
        String id = net.minecraft.core.Registry.ITEM.getKey(stack.getItem())
            .getNamespace();
        return net.fabricmc.loader.api.FabricLoader.getInstance()
            .getModContainer(id)
            .map(container -> container.getMetadata()
                .getName())
            .orElse(id);
    }

    /** One of the two block providers; see the class note for why there are two. */
    private static final class Ground implements IComponentProvider {

        private final boolean everyBlock;

        Ground(boolean everyBlock) {
            this.everyBlock = everyBlock;
        }

        @Override
        public void appendBody(List<Component> tooltip, IDataAccessor accessor, IPluginConfig config) {
            if (!config.get(TOGGLE)) return;
            BlockPos at = accessor.getPosition();
            List<String> said = new ArrayList<String>();
            WailaCompat
                .ground(said, everyBlock, accessor.getBlock() instanceof BlockGhost, at.getX(), at.getY(), at.getZ());
            for (String line : said) {
                tooltip.add(new TextComponent(line));
            }
        }
    }

    /** The golem, which answers for itself: whose it is, and why it is standing still. */
    private static final class Golem implements IEntityComponentProvider {

        @Override
        public void appendBody(List<Component> tooltip, IEntityAccessor accessor, IPluginConfig config) {
            if (!config.get(TOGGLE)) return;
            Entity looked = accessor.getEntity();
            List<String> said = new ArrayList<String>();
            WailaGolem.golem(said, looked);
            for (String line : said) {
                tooltip.add(new TextComponent(line));
            }
        }
    }
}
