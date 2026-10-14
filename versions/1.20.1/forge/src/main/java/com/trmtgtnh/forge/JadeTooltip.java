package com.trmtgtnh.forge;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.compat.WailaCompat;
import com.trmtgtnh.compat.WailaGolem;
import com.trmtgtnh.entity.EntityGolemOfWays;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * This mod's lines in Jade's block tooltip.
 *
 * <p>
 * Jade is Hwyla's successor on this version, and from Jade 8 has its own package and its own API, so this is the
 * same integration the 1.12.2 and 1.16.5 editions have under a different interface. What is said lives in {@link WailaCompat} and
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
 * Found by annotation rather than by an inter-mod message, which is Jade's way. Nothing here is
 * loaded when Jade is absent: a class is only looked at by the thing that looks for it.
 *
 * <p>
 * <strong>One toggle, as before.</strong> From Jade 8 each provider has a name of its own and Jade gives each a toggle
 * by it, unless the provider says it is required. These three say so, and ask the one toggle this mod has always
 * written, {@code trmtgtnh:wear}, so a player still turns the whole integration off with one switch.
 */
@WailaPlugin
public final class JadeTooltip implements IWailaPlugin {

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

    // On the client's side of the registration, from Jade 8: these are drawn on the client, from what it holds.
    @Override
    public void registerClient(IWailaClientRegistration registrar) {
        // Something here reads the reply, so the crosshair is worth asking about. See InspectionCache.
        com.trmtgtnh.client.InspectionCache.noteReader();
        registrar.registerBlockComponent(new Ground(false), BlockGhost.class);
        registrar.registerBlockComponent(new Ground(true), Block.class);
        registrar.registerEntityComponent(new Golem(), EntityGolemOfWays.class);
        registrar.addConfig(TOGGLE, true);
    }

    /** One of the two block providers; see the class note for why there are two. */
    private static final class Ground implements IBlockComponentProvider {

        private final boolean everyBlock;

        Ground(boolean everyBlock) {
            this.everyBlock = everyBlock;
        }

        @Override
        public ResourceLocation getUid() {
            return new ResourceLocation(Trmt.MODID, everyBlock ? "wear_every_block" : "wear_ground");
        }

        @Override
        public boolean isRequired() {
            return true;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!config.get(TOGGLE)) return;
            BlockPos at = accessor.getPosition();
            List<String> said = new ArrayList<String>();
            WailaCompat
                .ground(said, everyBlock, accessor.getBlock() instanceof BlockGhost, at.getX(), at.getY(), at.getZ());
            for (String line : said) {
                tooltip.add(Component.literal(line));
            }
        }
    }

    /** The golem, which answers for itself: whose it is, and why it is standing still. */
    private static final class Golem implements IEntityComponentProvider {

        @Override
        public ResourceLocation getUid() {
            return new ResourceLocation(Trmt.MODID, "golem");
        }

        @Override
        public boolean isRequired() {
            return true;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (!config.get(TOGGLE)) return;
            Entity looked = accessor.getEntity();
            List<String> said = new ArrayList<String>();
            WailaGolem.golem(said, looked);
            for (String line : said) {
                tooltip.add(Component.literal(line));
            }
        }
    }
}
