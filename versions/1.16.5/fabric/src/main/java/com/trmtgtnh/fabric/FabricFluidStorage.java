package com.trmtgtnh.fabric;

import java.util.Iterator;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/**
 * What a container holds by Fabric API's fluid transfer API, {@code fabric-transfer-api-v1} (0.9.228, RL13 on 1.16.5
 * Fabric; Xep: "Carry it to 1.16.5"). Fabric API 0.42 carries it for 1.16.5 (transfer API 1.5.0, read in its jar), so a
 * mod's cell, canister or tank pays a {@code fluid:} entry here as it does on 1.20.1 and later. Older Fabric API for
 * 1.16.5 has no such module, and this jar takes those too, so {@link FabricFluids} names this class only when the module
 * is loaded, and reads a bucket's own field otherwise.
 *
 * <p>
 * 1.5.0's shapes, not 1.20.1's: a context made {@code withInitial} (there is no {@code withConstant} yet), and a
 * storage's views read through a transaction ({@code iterable}), as that version has no {@code nonEmptyViews}. A
 * container is read for its first fluid that is not blank, in droplets, 81 to a millibucket, rounded down - so Fabric's
 * own water bottle, a third of a bucket, is 333 and pays no entry. Emptying is done in a transaction on a slot of this
 * class's own and kept only when exactly a bucket came out, as 1.20.1's reader does.
 */
final class FabricFluidStorage {

    /** Droplets to the millibucket: Fabric's bucket of 81000 is this mod's bucket of 1000. */
    static final long DROPLETS_PER_MILLIBUCKET = FluidConstants.BUCKET / com.trmtgtnh.util.Fluids.BUCKET;

    private FabricFluidStorage() {}

    /** The item's fluid storage, asked of a copy of the stack, or null where it has none. */
    static Storage<FluidVariant> storageOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return FluidStorage.ITEM.find(stack, ContainerItemContext.withInitial(stack.copy()));
    }

    /** Whether the item has a fluid storage at all. */
    static boolean has(ItemStack stack) {
        return storageOf(stack) != null;
    }

    /** The first fluid the item's storage holds, or null. */
    static Fluid fluidIn(ItemStack stack) {
        FluidVariant held = first(storageOf(stack));
        return held == null ? null : held.getFluid();
    }

    /** How much of it the storage holds, in millibuckets, rounded down. */
    static int millibucketsIn(ItemStack stack) {
        Storage<FluidVariant> storage = storageOf(stack);
        if (storage == null) return 0;
        try (Transaction transaction = Transaction.openOuter()) {
            for (StorageView<FluidVariant> view : storage.iterable(transaction)) {
                if (!view.isResourceBlank() && view.getAmount() > 0) return millibuckets(view.getAmount());
            }
        }
        return 0;
    }

    /**
     * One bucket's worth out of one container, and what it has become: the slot's stack after a committed extraction of
     * exactly a bucket, or null when the container cannot give a whole bucket.
     */
    static ItemStack drained(ItemStack one) {
        FluidVariant held = first(storageOf(one));
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

    private static FluidVariant first(Storage<FluidVariant> storage) {
        if (storage == null) return null;
        try (Transaction transaction = Transaction.openOuter()) {
            Iterator<StorageView<FluidVariant>> views = storage.iterator(transaction);
            while (views.hasNext()) {
                StorageView<FluidVariant> view = views.next();
                if (!view.isResourceBlank() && view.getAmount() > 0) return view.getResource();
            }
        }
        return null;
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
