package com.trmtgtnh.fabric;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.fabric.mixin.BucketItemAccessor;

/**
 * What a container holds, under Fabric: asked of Fabric API's fluid transfer API, as the 1.7.10 edition asks Forge's
 * fluid container registry (0.9.228, spec RL13; Xep: "Read the fluid storage").
 *
 * <p>
 * The 1.7.10 edition pays a {@code fluid:} entry with any container holding at least a bucket's worth of the named
 * fluid ({@code ReinforceCost}), and the Forge and NeoForge jars here ask the item's fluid handler for the same. Until
 * 0.9.228 this answered only for a bucket, through an accessor on the field it keeps its fluid in, and said once that
 * Fabric had no way to ask anything else - which was wrong: Fabric API's {@code fabric-transfer-api-v1} is in every jar
 * of it this edition requires, and its item lookup ({@code FluidStorage.ITEM}) is how a mod's cell, canister or tank
 * says what it holds. So a pack's container of concrete pays here as it does on Forge.
 *
 * <p>
 * The bucket's own field is kept behind the lookup, for a mod's bucket that registered no storage (Fabric API gives
 * every vanilla bucket one anyway). Amounts are in droplets there, 81000 to a bucket, and are handed on in the
 * millibuckets the rest of the mod counts in. Each way is said once in the log as it first answers.
 */
public final class FabricFluids implements com.trmtgtnh.util.Fluids.Side {

    /** Droplets to a millibucket: Fabric's bucket is 81000 droplets, this mod's 1000. */
    static final long DROPLETS_PER_MILLIBUCKET = FluidConstants.BUCKET / com.trmtgtnh.util.Fluids.BUCKET;

    private static volatile boolean storageSaid;

    private static volatile boolean bucketSaid;

    /** Which way the last question was answered: the transfer API, the bucket's own field, or neither. */
    private static volatile String answered = "nothing yet";

    private FabricFluids() {}

    public static void use() {
        com.trmtgtnh.util.Fluids.use(new FabricFluids());
    }

    /** One fluid and how much of it, in millibuckets. */
    private static final class Held {

        final Fluid fluid;

        final int millibuckets;

        Held(Fluid fluid, int millibuckets) {
            this.fluid = fluid;
            this.millibuckets = millibuckets;
        }
    }

    @Override
    public String fluidNameOf(ItemStack stack) {
        Held held = contents(stack);
        if (held == null) return null;
        return String.valueOf(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(held.fluid));
    }

    @Override
    public int fluidAmountOf(ItemStack stack) {
        Held held = contents(stack);
        return held == null ? 0 : held.millibuckets;
    }

    /**
     * A bucket's worth taken out of one container, and what the container became - asked of the container's own
     * storage, in a transaction committed only when exactly a bucket came out.
     *
     * <p>
     * The container is held in a slot of this mod's own, a copy of the one handed in, so the storage can exchange it
     * for what it becomes (a water bucket for an empty one, a cell for an emptier cell) and that is what is handed
     * back. One shape for every version of the transfer API this edition's games carry: {@code
     * ContainerItemContext.ofSingleSlot} over a {@code SingleStackStorage}, rather than 1.20.1's {@code withInitial},
     * which the later ones do not have. Anything short of a bucket is rolled back and answers null, as a container
     * holding no fluid does.
     */
    @Override
    public ItemStack drained(ItemStack one) {
        Held held = contents(one);
        if (held == null) return null;
        final ItemStack[] slot = { one.copyWithCount(1) };
        SingleStackStorage storage = new SingleStackStorage() {

            @Override
            protected ItemStack getStack() {
                return slot[0];
            }

            @Override
            protected void setStack(ItemStack stack) {
                slot[0] = stack;
            }
        };
        Storage<FluidVariant> fluids = FluidStorage.ITEM.find(slot[0], ContainerItemContext.ofSingleSlot(storage));
        if (fluids == null) return bucketDrained(one);
        // Closed by the try, which rolls back anything not committed.
        try (Transaction taking = Transaction.openOuter()) {
            long out = fluids.extract(FluidVariant.of(held.fluid), FluidConstants.BUCKET, taking);
            if (out != FluidConstants.BUCKET) return null;
            taking.commit();
        }
        return slot[0];
    }

    @Override
    public String answeredBy() {
        return answered;
    }

    /**
     * The first fluid a container's storage shows, or the bucket's own where it has no storage; null for nothing.
     *
     * <p>
     * Asked of one item of the stack, with nothing changed: a constant context, which the storage reads and cannot
     * write back through.
     */
    private static Held contents(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ItemStack one = stack.copyWithCount(1);
        Storage<FluidVariant> fluids = FluidStorage.ITEM.find(one, ContainerItemContext.withConstant(one));
        if (fluids != null) {
            answered = "Fabric's transfer API";
            if (!storageSaid) {
                storageSaid = true;
                Trmt.LOG.info(
                    "A container was asked what fluid it holds and Fabric's transfer API answered for it, so a "
                        + "reinforcement entry naming a fluid is paid for from any container holding a bucket of it. "
                        + "This is said once.");
            }
            for (StorageView<FluidVariant> view : fluids.nonEmptyViews()) {
                if (view.isResourceBlank()) continue;
                Fluid fluid = view.getResource()
                    .getFluid();
                if (fluid == null || fluid == Fluids.EMPTY) continue;
                long millibuckets = view.getAmount() / DROPLETS_PER_MILLIBUCKET;
                return new Held(fluid, (int) Math.min(Integer.MAX_VALUE, millibuckets));
            }
            return null;
        }
        if (!(stack.getItem() instanceof BucketItem)) {
            answered = "nothing: no storage and no bucket";
            return null;
        }
        answered = "the bucket's own field";
        if (!bucketSaid) {
            bucketSaid = true;
            Trmt.LOG.info(
                "A bucket with no fluid storage of Fabric's was asked what it holds and answered from its own field. "
                    + "This is said once.");
        }
        Fluid held = ((BucketItemAccessor) stack.getItem()).trmt$content();
        return held == null || held == Fluids.EMPTY ? null : new Held(held, com.trmtgtnh.util.Fluids.BUCKET);
    }

    /**
     * A bucket with no storage emptied, which leaves its crafting remainder - the rule the whole game already honours
     * when it leaves one in a crafting grid, so a mod's returnable container comes back and a single-use one, which
     * declares no remainder, does not.
     */
    private static ItemStack bucketDrained(ItemStack one) {
        // From 1.21.2 the remainder is a stack template (26.1: ItemStackTemplate) rather than an item; made into a
        // stack it is the same one stack of that item 1.21.1 made.
        net.minecraft.world.item.ItemStackTemplate remains = one.getItem()
            .getCraftingRemainder();
        return remains == null ? ItemStack.EMPTY : remains.create();
    }
}
