package com.trmtgtnh.forge;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MilkBucketItem;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.wrappers.FluidBucketWrapper;

import com.trmtgtnh.util.Fluids;

/**
 * What a container holds, under Forge, which has a capability for exactly this.
 *
 * <p>
 * Any item may carry a fluid handler, so this answers for a mod's cell, tank or canister as readily
 * as for a bucket, and the settings can name a fluid once and have it paid for from whatever the
 * pack happens to carry it in.
 *
 * <p>
 * <strong>Asked of the stack itself, not of {@code FluidUtil}</strong> (0.9.228). Forge 52's
 * {@code FluidUtil.getFluidHandler(ItemStack)} answers {@code LazyOptional.empty()} for every stack - that is its whole
 * body in 52.1.19's jar - and its {@code BucketItem} gives no capability provider of its own, where Forge 47's attached
 * a {@code FluidBucketWrapper}. So until 0.9.228 this found no fluid in anything on 1.21.1 Forge, and every
 * {@code fluid:} entry of reinforce.materials paid nothing there (the matched part's water bucket held "null 0 mB"; the
 * released 0.9.227 jar has the same lookup). The stack is asked for {@code ForgeCapabilities.FLUID_HANDLER_ITEM}, which
 * a mod's container still provides through its item's capability provider, and a bucket with none is read through
 * Forge's own {@code FluidBucketWrapper} - the reading Forge 47 gave every bucket, and the one 1.7.10's container
 * registry gives the vanilla buckets.
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
        return String.valueOf(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(held.getFluid()));
    }

    @Override
    public int fluidAmountOf(ItemStack stack) {
        FluidStack held = contents(stack);
        return held == null ? 0 : held.getAmount();
    }

    @Override
    public ItemStack drained(ItemStack one) {
        IFluidHandlerItem handler = handlerOf(one);
        if (handler == null) return null;
        FluidStack took = handler.drain(Fluids.BUCKET, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        if (took == null || took.isEmpty()) return null;
        return handler.getContainer();
    }

    /** Which way a container was read, for the harness's line (0.9.228): its own capability, Forge's bucket wrapper, or not at all. */
    @Override
    public String readBy(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "nothing";
        if (own(stack) != null) return "the fluid handler";
        return handlerOf(stack) != null ? "Forge's bucket wrapper" : "nothing";
    }

    /**
     * Whether this Forge's item stacks carry capabilities at all (0.9.228, step 9; Xep: "Fix the code too"). Forge 52
     * took them off {@code ItemStack} and gave them back in 52.0.52, where the stack became a
     * {@code CapabilityProvider} - read from each build's patched client jar - so on 52.0.0 to 52.0.51 the call below
     * names a method the game has not got, and the first fluid read ended the game with a {@code NoSuchMethodError}.
     * Asked once, of the class itself; where they are missing a mod's container cannot offer a fluid handler either,
     * and a bucket is read through Forge's own wrapper as it is everywhere.
     */
    private static final boolean STACKS_CARRY_CAPABILITIES = net.minecraftforge.common.capabilities.ICapabilityProvider.class
        .isAssignableFrom(ItemStack.class);

    /** The stack's own fluid handler, or null - never asked where stacks carry no capabilities. */
    private static IFluidHandlerItem own(ItemStack stack) {
        if (!STACKS_CARRY_CAPABILITIES) return null;
        return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve().orElse(null);
    }

    private static FluidStack contents(ItemStack stack) {
        IFluidHandlerItem handler = handlerOf(stack);
        return handler == null || handler.getTanks() < 1 ? null : handler.getFluidInTank(0);
    }

    /** The stack's own fluid capability, or Forge's bucket wrapper for a bucket that has none, or null. */
    private static IFluidHandlerItem handlerOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        IFluidHandlerItem own = own(stack);
        if (own != null) return own;
        return stack.getItem() instanceof BucketItem || stack.getItem() instanceof MilkBucketItem ? new FluidBucketWrapper(stack) : null;
    }
}
