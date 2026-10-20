package com.trmtgtnh.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.IPlantable;

import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.block.GhostInherit;

/**
 * Whether a plant may stand on a worn square is the covered block's answer - the 1.7.10 edition's
 * {@code GhostLogic.sustainsPlant} (0.9.222, spec GF15).
 *
 * <p>
 * Forge asks a block whether it holds a plant ({@code canSustainPlant}), and its own default answers by identity - is
 * this grass, dirt, sand - which a ghost never is, whatever it is drawn as. The client asks before it sends a placement
 * and sends nothing on a no, so nothing could be planted on worn ground from this client although the server, which
 * holds the real ground, would have taken it; and a plant already standing there asks again when a neighbour changes,
 * and on a no the client takes it away. Every vanilla plant that asks - a bush and everything built on one, a mushroom,
 * a cactus, a reed - asks through here.
 *
 * <p>
 * <strong>A mixin because the question names a type of Forge's</strong> ({@code IPlantable}), which the shared module
 * may not name, so the ghost there cannot declare the method the way it declares Forge's sound and slipperiness. At the
 * head of Forge's own implementation on {@code Block}, guarded on the block first, so every other block pays one
 * {@code instanceof}; and everything around the question - the covered block, a view holding it at its square, the
 * guard against the question coming back - is {@link GhostInherit#sustainsPlant}'s. Null from there means nothing is
 * recorded under the square, and Forge's own answer for the ghost stands.
 *
 * <p>
 * Fabric's plants decide this themselves rather than ask the block; that module's {@code MixinGhostSoil*} put the same
 * question to the covered block at the same four places.
 */
@Mixin(Block.class)
public abstract class MixinGhostSustainsPlant {

    // By name alone and unremapped: the name is Forge's own, which no mapping renames, and Block has the one method of
    // that name - whereas a descriptor written here in this module's names would not be remapped with it, and would
    // name classes the production game calls something else.
    @Inject(method = "canSustainPlant", at = @At("HEAD"), cancellable = true, remap = false)
    private void trmt$askTheCoveredBlock(BlockState state, BlockGetter world, BlockPos pos, Direction facing,
        IPlantable plantable, CallbackInfoReturnable<Boolean> callback) {
        Block self = (Block) (Object) this;
        if (!(self instanceof BlockGhost)) return;
        Boolean covered = GhostInherit
            .sustainsPlant(self, world, pos, (under, view) -> under.canSustainPlant(view, pos, facing, plantable));
        if (covered != null) callback.setReturnValue(covered);
    }
}
