package com.trmtgtnh.forge;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import com.trmtgtnh.util.Fluids;

/**
 * What a container holds, under Forge, which has a capability for exactly this.
 *
 * <p>
 * Any item may carry a fluid handler, so this answers for a mod's cell, tank or canister as readily
 * as for a bucket, and the settings can name a fluid once and have it paid for from whatever the
 * pack happens to carry it in. That is the whole of what the Fabric side cannot do at this version.
 *
 * <p>
 * The first tank only. A container with several is not something reinforcement is priced in, and
 * reading the sum of them would let a half-full pair of tanks pay as a full bucket.
 */
public final class ForgeFluids implements Fluids.Side {

    private ForgeFluids() {}

    public static void use() {
        Fluids.use(new ForgeFluids());
    }

    @Override
    public String fluidNameOf(ItemStack stack) {
        FluidStack held = contents(stack);
        if (held == null || held.isEmpty()) return null;
        return String.valueOf(held.getFluid()
            .getRegistryName());
    }

    @Override
    public int fluidAmountOf(ItemStack stack) {
        FluidStack held = contents(stack);
        return held == null ? 0 : held.getAmount();
    }

    @Override
    public ItemStack drained(ItemStack one) {
        net.minecraftforge.fluids.capability.IFluidHandlerItem handler = FluidUtil.getFluidHandler(one)
            .orElse(null);
        if (handler == null) return null;
        FluidStack took = handler.drain(Fluids.BUCKET, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        if (took == null || took.isEmpty()) return null;
        return handler.getContainer();
    }

    private static FluidStack contents(ItemStack stack) {
        return FluidUtil.getFluidHandler(stack)
            .map(handler -> handler.getFluidInTank(0))
            .orElse(null);
    }
}
