package com.trmtgtnh.neoforge;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import com.trmtgtnh.util.Fluids;

/**
 * What a container holds, under NeoForge, which has a capability for exactly this. The Forge module's ForgeFluids
 * carried across: NeoForge's FluidUtil hands back the item's fluid handler as an Optional where Forge's handed back a
 * LazyOptional, and the two are asked the same way.
 *
 * <p>
 * Any item may carry a fluid handler, so this answers for a mod's cell, tank or canister as readily as for a bucket,
 * and the settings can name a fluid once and have it paid for from whatever the pack happens to carry it in. That is
 * the whole of what the Fabric side cannot do at this version.
 *
 * <p>
 * The first tank only. A container with several is not something reinforcement is priced in, and reading the sum of
 * them would let a half-full pair of tanks pay as a full bucket.
 *
 * <p>
 * <strong>From NeoForge 26.3 the fluid capability is the transfer API's</strong> (net.neoforged.neoforge.transfer, read
 * from 26.3.0.94's universal jar): {@code FluidUtil.getFluidHandler}, {@code IFluidHandlerItem} and {@code FluidAction}
 * are gone, and an item's fluids are a {@code ResourceHandler<FluidResource>} asked of an {@code ItemAccess} for the
 * stack ({@code Capabilities.Fluid.ITEM}). The same three questions are asked of it: the first tank's fluid and amount,
 * and one bucket's worth taken out of whatever it holds, inside a transaction that is committed, the container read
 * back from the access - the one slot it was handed in - as {@code getContainer()} read it.
 */
public final class NeoForgeFluids implements Fluids.Side {

    private NeoForgeFluids() {}

    public static void use() {
        Fluids.use(new NeoForgeFluids());
    }

    @Override
    public String fluidNameOf(ItemStack stack) {
        FluidResource held = contents(stack);
        if (held == null || held.isEmpty()) return null;
        return String.valueOf(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(held.getFluid()));
    }

    @Override
    public int fluidAmountOf(ItemStack stack) {
        ResourceHandler<FluidResource> handler = handlerOf(ItemAccess.forStack(stack).oneByOne());
        if (handler == null || handler.size() == 0 || handler.getResource(0)
            .isEmpty()) return 0;
        return handler.getAmountAsInt(0);
    }

    @Override
    public ItemStack drained(ItemStack one) {
        // The stack in a container of one slot, so what the handler turns it into - an empty bucket, nothing - is read
        // back from that slot once the transaction is committed, as IFluidHandlerItem.getContainer() handed it back.
        net.minecraft.world.SimpleContainer box = new net.minecraft.world.SimpleContainer(one);
        ResourceHandler<FluidResource> handler = handlerOf(
            ItemAccess.forHandlerIndex(net.neoforged.neoforge.transfer.item.VanillaContainerWrapper.of(box), 0));
        if (handler == null) return null;
        try (net.neoforged.neoforge.transfer.transaction.Transaction drain = net.neoforged.neoforge.transfer.transaction.Transaction
            .openRoot()) {
            // One bucket's worth of whatever it holds, as drain(BUCKET, EXECUTE) took it.
            net.neoforged.neoforge.transfer.resource.ResourceStack<FluidResource> took = net.neoforged.neoforge.transfer.ResourceHandlerUtil
                .extractFirst(handler, fluid -> true, Fluids.BUCKET, drain);
            if (took == null || took.isEmpty()) return null;
            drain.commit();
        }
        return box.getItem(0);
    }

    /**
     * The first tank's fluid, or null where the stack has no fluid handler or no tank. Read one container at a time, as
     * NeoForge's own FluidUtil.getFirstStackContained reads a stack (26.3), so a stack of buckets says what one holds.
     */
    private static FluidResource contents(ItemStack stack) {
        ResourceHandler<FluidResource> handler = handlerOf(ItemAccess.forStack(stack).oneByOne());
        return handler == null || handler.size() == 0 ? null : handler.getResource(0);
    }

    private static ResourceHandler<FluidResource> handlerOf(ItemAccess access) {
        return access.getCapability(Capabilities.Fluid.ITEM);
    }
}
