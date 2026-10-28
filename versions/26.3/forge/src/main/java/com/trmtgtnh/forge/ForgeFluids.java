package com.trmtgtnh.forge;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * <strong>Asked of the stack itself, not of {@code FluidUtil}</strong> (0.9.228, carried from the 1.21.1 edition's fix
 * of the same day). Forge's {@code FluidUtil.getFluidHandler(ItemStack)} answers {@code LazyOptional.empty()} for every
 * stack - its whole body in 64.1.5's, 65.1.5's and 66.0.13's jars, as in Forge 52's - and its {@code BucketItem} gives
 * no capability of its own, where Forge 47's attached a {@code FluidBucketWrapper}. So until 0.9.228 this found no fluid
 * in anything on 26.x Forge, and every {@code fluid:} entry of reinforce.materials paid nothing there (the matched
 * part's water bucket held "null 0"). The stack is asked for {@code ForgeCapabilities.FLUID_HANDLER_ITEM}, which a
 * mod's container still provides through its item's capability provider, and a bucket with none is read through
 * Forge's own {@code FluidBucketWrapper} - the reading Forge 47 gave every bucket, and the one 1.7.10's container
 * registry gives the vanilla buckets. From 26.1 the milk bucket is a plain item rather than a {@code MilkBucketItem},
 * and the wrapper reads it as Forge's milk itself, so it is wrapped by name.
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

    /** Which way the last container was read, for the harness's line: its own capability, Forge's bucket wrapper, or not at all. */
    @Override
    public String answeredBy() {
        return last;
    }

    private static volatile String last = "nothing";

    private static FluidStack contents(ItemStack stack) {
        IFluidHandlerItem handler = handlerOf(stack);
        return handler == null || handler.getTanks() < 1 ? null : handler.getFluidInTank(0);
    }

    /** The stack's own fluid capability, or Forge's bucket wrapper for a bucket that has none, or null. */
    private static IFluidHandlerItem handlerOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            last = "nothing";
            return null;
        }
        IFluidHandlerItem own = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve().orElse(null);
        if (own != null) {
            last = "the fluid handler";
            return own;
        }
        if (stack.getItem() instanceof BucketItem || stack.is(Items.MILK_BUCKET)) {
            last = "Forge's bucket wrapper";
            return new FluidBucketWrapper(stack);
        }
        last = "nothing";
        return null;
    }
}
