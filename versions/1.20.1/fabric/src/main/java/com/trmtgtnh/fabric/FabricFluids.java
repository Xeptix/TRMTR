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
 * What a container holds, under Fabric: whatever Fabric API's fluid transfer API says it holds (0.9.228, RL13; Xep:
 * "Read the fluid storage").
 *
 * <p>
 * The 1.7.10 edition pays a {@code fluid:} entry of {@code reinforce.materials} with any container holding at least a
 * bucket's worth of the named fluid, and Forge here asks the item's fluid handler, which any item may carry. Fabric's
 * equal is {@code FluidStorage.ITEM}, which a mod's cell, canister or tank registers its storage with, and which Fabric
 * API itself gives every bucket and the water bottle - so a pack's container pays here as it does on Forge. Until
 * 0.9.228 this read a {@code BucketItem}'s field and nothing else, on the belief that Fabric had no such API at this
 * version; it has had one, in {@code fabric-transfer-api-v1}, for every Fabric API this jar takes.
 *
 * <p>
 * What a storage holds is counted in droplets, 81 to a millibucket, and a container is read for its first fluid that
 * is not blank. A bucket that registered no storage - a mod's {@code BucketItem} Fabric API did not reach - is still
 * read by the field it keeps its fluid in, as before, and said once when it is, because that is the one path a reader
 * of the log could not otherwise tell apart.
 *
 * <p>
 * Emptying is done in a transaction on a slot of this class's own holding a copy of the one container, and kept only
 * when exactly a bucket came out: what the slot then holds is what the container became - an empty bucket, an empty
 * cell, or nothing. One shape for every version this edition's code is carried to, since {@code withInitial} is only
 * in 1.20.1's transfer API.
 */
public final class FabricFluids implements com.trmtgtnh.util.Fluids.Side {

    /** Droplets to the millibucket: Fabric's bucket of 81000 is this mod's bucket of 1000. */
    static final long DROPLETS_PER_MILLIBUCKET = FluidConstants.BUCKET / com.trmtgtnh.util.Fluids.BUCKET;

    private static volatile boolean bucketFieldSaid;

    private FabricFluids() {}

    public static void use() {
        com.trmtgtnh.util.Fluids.use(new FabricFluids());
    }

    @Override
    public String fluidNameOf(ItemStack stack) {
        FluidVariant held = heldIn(stack);
        if (held != null) return String.valueOf(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(held.getFluid()));
        Fluid bucket = storageOf(stack) == null ? bucketContents(stack) : null;
        return bucket == null ? null : String.valueOf(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(bucket));
    }

    @Override
    public int fluidAmountOf(ItemStack stack) {
        Storage<FluidVariant> storage = storageOf(stack);
        if (storage != null) {
            StorageView<FluidVariant> view = firstHeld(storage);
            return view == null ? 0 : millibuckets(view.getAmount());
        }
        // A bucket is full or it is not a bucket of anything. There is no partial one to describe.
        return bucketContents(stack) == null ? 0 : com.trmtgtnh.util.Fluids.BUCKET;
    }

    /**
     * One bucket's worth out of one container, and what it has become: the slot's stack after a committed extraction
     * of exactly a bucket, or null when the container holds no fluid or cannot give a whole bucket.
     */
    @Override
    public ItemStack drained(ItemStack one) {
        if (storageOf(one) == null) return bucketDrained(one);
        FluidVariant held = heldIn(one);
        if (held == null) return null;
        Slot slot = new Slot(one.copy());
        Storage<FluidVariant> storage = FluidStorage.ITEM.find(slot.held(), ContainerItemContext.ofSingleSlot(slot));
        if (storage == null) return null;
        try (Transaction transaction = Transaction.openOuter()) {
            long took = storage.extract(held, FluidConstants.BUCKET, transaction);
            if (took != FluidConstants.BUCKET) {
                transaction.abort();
                return null;
            }
            transaction.commit();
        }
        return slot.held();
    }

    /** Droplets in millibuckets, rounded down: a third of a bucket is 333, never a bucket. */
    static int millibuckets(long droplets) {
        long amount = droplets / DROPLETS_PER_MILLIBUCKET;
        return amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, amount);
    }

    /** The item's fluid storage, asked of the stack as it is and changing nothing, or null where it has none. */
    private static Storage<FluidVariant> storageOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return FluidStorage.ITEM.find(stack, ContainerItemContext.withConstant(stack));
    }

    /** The first fluid the item's storage holds, or null. */
    private static FluidVariant heldIn(ItemStack stack) {
        Storage<FluidVariant> storage = storageOf(stack);
        StorageView<FluidVariant> view = storage == null ? null : firstHeld(storage);
        return view == null ? null : view.getResource();
    }

    private static StorageView<FluidVariant> firstHeld(Storage<FluidVariant> storage) {
        for (StorageView<FluidVariant> view : storage.nonEmptyViews()) {
            if (!view.isResourceBlank() && view.getAmount() > 0) return view;
        }
        return null;
    }

    /**
     * A bucket with no fluid storage of its own, read by the field it was built with - the reading from before
     * 0.9.228, kept for a mod's {@code BucketItem} that Fabric API's own registration did not reach.
     */
    private static Fluid bucketContents(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof BucketItem)) return null;
        Fluid held = ((BucketItemAccessor) stack.getItem()).trmt$content();
        if (held == null || held == Fluids.EMPTY) return null;
        if (!bucketFieldSaid) {
            bucketFieldSaid = true;
            Trmt.LOG.info(
                "{} has no fluid storage of its own, so it is read as a bucket of {} by the field it was built with. "
                    + "This is said once.",
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()),
                net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(held));
        }
        return held;
    }

    /**
     * Such a bucket emptied, which leaves its crafting remainder - the rule the whole game honours when it leaves one
     * in a crafting grid, so a mod's returnable container comes back and a single-use one does not.
     */
    private static ItemStack bucketDrained(ItemStack one) {
        if (bucketContents(one) == null) return null;
        net.minecraft.world.item.Item remains = one.getItem()
            .getCraftingRemainingItem();
        return remains == null ? ItemStack.EMPTY : new ItemStack(remains);
    }

    /** Which way a container was read: by its fluid storage, by a bucket's field, or not at all. For the harness. */
    @Override
    public String readBy(ItemStack stack) {
        if (storageOf(stack) != null) return "the fluid storage";
        return bucketContents(stack) != null ? "the bucket's field" : "nothing";
    }

    /** A slot of this class's own, holding the one container being emptied, which the storage changes in place. */
    private static final class Slot extends SingleStackStorage {

        private ItemStack stack;

        Slot(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        protected ItemStack getStack() {
            return stack;
        }

        @Override
        protected void setStack(ItemStack stack) {
            this.stack = stack;
        }

        ItemStack held() {
            return stack;
        }
    }
}
