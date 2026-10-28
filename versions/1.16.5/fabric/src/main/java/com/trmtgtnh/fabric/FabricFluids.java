package com.trmtgtnh.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.fabric.mixin.BucketItemAccessor;

/**
 * What a container holds, under Fabric: whatever Fabric API's fluid transfer API says it holds, where that module is
 * loaded (0.9.228, RL13 on 1.16.5 Fabric; Xep: "Carry it to 1.16.5").
 *
 * <p>
 * The 1.7.10 edition pays a {@code fluid:} entry with any container holding at least a bucket's worth of the named
 * fluid, and Forge asks the item's fluid handler, which any item may carry. Fabric's equal is
 * {@code FluidStorage.ITEM}, in {@code fabric-transfer-api-v1}, which Fabric API 0.42 for 1.16.5 carries - so a pack's
 * cell or canister pays here as on Forge and on the later Fabric jars ({@link FabricFluidStorage}). Until 0.9.228 this
 * read a {@code BucketItem}'s field alone, on the belief that the API arrived several versions later. Older Fabric API
 * for 1.16.5 has no transfer module, and this jar takes those too, so the storage is asked only where the module is
 * loaded; a bucket with no storage, or any bucket on an older Fabric API, is read by the field it keeps its fluid in, as
 * before.
 */
public final class FabricFluids implements com.trmtgtnh.util.Fluids.Side {

    /** Whether Fabric API's transfer module is here: asked once, so the class naming it is never loaded without it. */
    private static final boolean TRANSFER = FabricLoader.getInstance()
        .isModLoaded("fabric-transfer-api-v1");

    private static volatile boolean narrownessSaid;

    /**
     * Which way the last question was answered (0.9.228): the fluid storage, the bucket's own field, or nothing - the
     * harness's fluid line says it.
     */
    private static volatile String answered = "nothing yet";

    private FabricFluids() {}

    @Override
    public String answeredBy() {
        return answered;
    }

    public static void use() {
        com.trmtgtnh.util.Fluids.use(new FabricFluids());
    }

    @Override
    public String fluidNameOf(ItemStack stack) {
        if (TRANSFER && FabricFluidStorage.has(stack)) {
            answered = "the fluid storage";
            Fluid held = FabricFluidStorage.fluidIn(stack);
            return held == null ? null : String.valueOf(Registry.FLUID.getKey(held));
        }
        Fluid held = contents(stack);
        if (held == null || held == Fluids.EMPTY) return null;
        return String.valueOf(Registry.FLUID.getKey(held));
    }

    @Override
    public int fluidAmountOf(ItemStack stack) {
        if (TRANSFER && FabricFluidStorage.has(stack)) {
            answered = "the fluid storage";
            return FabricFluidStorage.millibucketsIn(stack);
        }
        // A bucket is full or it is not a bucket of anything. There is no partial one to describe.
        return contents(stack) == null ? 0 : com.trmtgtnh.util.Fluids.BUCKET;
    }

    /**
     * One bucket's worth out of one container, and what it has become: by the fluid storage where the item has one, else
     * a bucket's crafting remainder - the rule the whole game honours when it leaves one in a crafting grid, so a mod's
     * returnable container comes back and a single-use one, which declares no remainder, does not.
     */
    @Override
    public ItemStack drained(ItemStack one) {
        if (TRANSFER && FabricFluidStorage.has(one)) return FabricFluidStorage.drained(one);
        if (contents(one) == null) return null;
        net.minecraft.world.item.Item remains = one.getItem()
            .getCraftingRemainingItem();
        return remains == null ? ItemStack.EMPTY : new ItemStack(remains);
    }

    private static Fluid contents(ItemStack stack) {
        if (!(stack.getItem() instanceof BucketItem)) {
            answered = "nothing: no fluid storage, and not a bucket";
            if (!narrownessSaid && !TRANSFER) {
                narrownessSaid = true;
                Trmt.LOG.info(
                    "Something that is not a bucket was asked what fluid it holds. This Fabric API has no fluid "
                        + "transfer module (fabric-transfer-api-v1, in Fabric API 0.42 for 1.16.5), so only buckets can "
                        + "answer, and a reinforcement entry naming a fluid is paid for with a bucket of it and not with "
                        + "a cell or a tank. This is said once.");
            }
            return null;
        }
        answered = "the bucket's own field";
        Fluid held = ((BucketItemAccessor) stack.getItem()).trmt$content();
        return held == Fluids.EMPTY ? null : held;
    }
}
