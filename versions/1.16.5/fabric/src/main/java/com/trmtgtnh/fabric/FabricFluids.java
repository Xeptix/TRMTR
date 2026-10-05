package com.trmtgtnh.fabric;

import net.minecraft.core.Registry;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.fabric.mixin.BucketItemAccessor;

/**
 * What a container holds, under Fabric, which at this version means a bucket and nothing else.
 *
 * <p>
 * <strong>This is a narrower answer than the Forge side gives, and the difference is the loader's
 * rather than this mod's.</strong> Forge has a fluid handler capability that any item may carry, so
 * a pack's cell or canister of concrete pays for reinforcement there. Fabric's fluid transfer API
 * arrives several versions after this one; there is no question to ask an arbitrary item here, so
 * what can be read is a bucket - any bucket, a mod's as well as vanilla's, through an accessor on
 * the field it keeps its fluid in.
 *
 * <p>
 * Said once when a fluid entry is asked about something that is not a bucket, because the symptom
 * otherwise is a payment the settings allow being refused with nothing written down anywhere.
 */
public final class FabricFluids implements com.trmtgtnh.util.Fluids.Side {

    private static volatile boolean narrownessSaid;

    private FabricFluids() {}

    public static void use() {
        com.trmtgtnh.util.Fluids.use(new FabricFluids());
    }

    @Override
    public String fluidNameOf(ItemStack stack) {
        Fluid held = contents(stack);
        if (held == null || held == Fluids.EMPTY) return null;
        return String.valueOf(Registry.FLUID.getKey(held));
    }

    @Override
    public int fluidAmountOf(ItemStack stack) {
        // A bucket is full or it is not a bucket of anything. There is no partial one to describe.
        return contents(stack) == null ? 0 : com.trmtgtnh.util.Fluids.BUCKET;
    }

    /**
     * A bucket emptied, which leaves an empty bucket.
     *
     * <p>
     * The crafting remainder rather than a hardcoded empty bucket, because that is the rule the
     * whole game already honours when it leaves one in a crafting grid - so a mod's returnable
     * container comes back and a single-use one, which declares no remainder, does not.
     */
    @Override
    public ItemStack drained(ItemStack one) {
        if (contents(one) == null) return null;
        net.minecraft.world.item.Item remains = one.getItem()
            .getCraftingRemainingItem();
        return remains == null ? ItemStack.EMPTY : new ItemStack(remains);
    }

    private static Fluid contents(ItemStack stack) {
        if (!(stack.getItem() instanceof BucketItem)) {
            if (!narrownessSaid) {
                narrownessSaid = true;
                Trmt.LOG.info(
                    "Something that is not a bucket was asked what fluid it holds. On this loader only "
                        + "buckets can answer - Fabric has no way at this version to ask an arbitrary "
                        + "container - so a reinforcement entry naming a fluid is paid for with a "
                        + "bucket of it and not with a cell or a tank. This is said once.");
            }
            return null;
        }
        Fluid held = ((BucketItemAccessor) stack.getItem()).trmt$content();
        return held == Fluids.EMPTY ? null : held;
    }
}
